package com.gamelaunch.frontend.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gamelaunch.frontend.domain.model.EmulatorMapping
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.EmulatorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmulatorLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emulatorRepository: EmulatorRepository,
    private val packageManagerHelper: PackageManagerHelper
) {
    suspend fun launch(game: Game): Result<Unit> {
        // Android game: romPath is "package:<pkg>" — launch the app directly.
        if (game.romPath.startsWith("package:")) {
            val pkg = game.romPath.removePrefix("package:")
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return Result.failure(Exception("App not installed: $pkg"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return tryStartActivity(intent)
        }

        var mapping = emulatorRepository.getMappingForPlatform(game.platformId)
            ?: return Result.failure(NoEmulatorConfiguredException(game.platformId))

        // If the saved package is no longer installed (e.g. stale DB after package name fix),
        // run auto-detect once to update the mapping before trying to launch.
        if (!packageManagerHelper.isPackageInstalled(mapping.packageName)) {
            emulatorRepository.autoDetectAndAssign()
            mapping = emulatorRepository.getMappingForPlatform(game.platformId)
                ?: return Result.failure(NoEmulatorConfiguredException(game.platformId))
        }

        return if (mapping.isRetroArch) {
            launchRetroArch(game, mapping)
        } else {
            launchStandalone(game, mapping)
        }
    }

    private fun launchRetroArch(game: Game, mapping: EmulatorMapping): Result<Unit> {
        val pkg = mapping.packageName
        // RetroArch's content-loading activity. Launching MainMenuActivity (the package's
        // default launch intent) only opens the menu; RetroActivityFuture with ROM/LIBRETRO/
        // CONFIGFILE extras is what actually boots a game directly.
        // Android RetroArch core files carry an "_android" suffix (e.g.
        // nestopia_libretro_android.so), so the canonical core name from PlatformDefinitions
        // must be adapted before building the path.
        val corePath = mapping.retroArchCore?.let { name ->
            val androidName = if (name.endsWith("_android.so")) name
                              else name.removeSuffix(".so") + "_android.so"
            "/data/user/0/$pkg/cores/$androidName"
        }
        val configFile = "/storage/emulated/0/Android/data/$pkg/files/retroarch.cfg"
        val intent = Intent().apply {
            setClassName(pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            putExtra("ROM", game.romPath)
            corePath?.let { putExtra("LIBRETRO", it) }
            putExtra("CONFIGFILE", configFile)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // Fall back to the plain launch intent (opens the menu) if the content activity
        // can't be started for some reason — better than a hard failure.
        return tryStartActivity(intent).recoverCatching {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: throw it
            launch.putExtra("ROM", game.romPath)
            corePath?.let { c -> launch.putExtra("LIBRETRO", c) }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        }
    }

    private fun launchStandalone(game: Game, mapping: EmulatorMapping): Result<Unit> {
        val pkg  = mapping.packageName
        val spec = launchSpecs[pkg]
        val file = File(game.romPath)

        // Preferred path: each known emulator has a verified launch recipe (explicit activity +
        // either a ROM path extra or a file:// data URI).
        if (spec != null) {
            val intent = Intent(spec.action).apply {
                setClassName(pkg, spec.activity)
                if (spec.romExtraKey != null) {
                    // ROM handed over as a plain path string — no Uri, nothing to expose.
                    putExtra(spec.romExtraKey, game.romPath)
                } else {
                    setDataAndType(Uri.fromFile(file), spec.mimeType)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                mapping.intentExtras.forEach { (k, v) -> putExtra(k, v) }
            }
            // If the hard-coded activity name is wrong for this build, fall back to a generic
            // VIEW intent, and finally to just opening the emulator's own game list.
            return tryStartActivity(intent)
                .recoverCatching { context.startActivity(genericViewIntent(pkg, file, mapping)) }
                .recoverCatching { context.startActivity(openAppIntent(pkg) ?: throw it) }
        }

        // Unknown emulator: generic VIEW by package, else just open the emulator.
        return tryStartActivity(genericViewIntent(pkg, file, mapping))
            .recoverCatching { context.startActivity(openAppIntent(pkg) ?: throw it) }
    }

    /** Last-resort: open the emulator's own UI (its game list) when it can't be booted directly. */
    private fun openAppIntent(pkg: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun genericViewIntent(pkg: String, file: File, mapping: EmulatorMapping): Intent =
        Intent(mapping.launchAction ?: Intent.ACTION_VIEW, Uri.fromFile(file)).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            mapping.intentExtras.forEach { (k, v) -> putExtra(k, v) }
        }

    private fun tryStartActivity(intent: Intent): Result<Unit> = runCatching {
        context.startActivity(intent)
    }

    /** How to hand a ROM to a specific standalone emulator (all verified on a Retroid Pocket 4). */
    private data class LaunchSpec(
        val activity: String,            // fully-qualified activity to launch explicitly
        val romExtraKey: String? = null, // pass ROM path via this String extra; else as file:// data
        val action: String = Intent.ACTION_VIEW,
        val mimeType: String? = null
    )

    private val launchSpecs: Map<String, LaunchSpec> = mapOf(
        // PS1 — DuckStation reads the ROM from a "bootPath" extra, not VIEW data.
        "com.github.stenzek.duckstation" to
            LaunchSpec("com.github.stenzek.duckstation.EmulationActivity",
                       romExtraKey = "bootPath", action = Intent.ACTION_MAIN),
        // PS2 — NetherSX2 / AetherSX2 share DuckStation's launch convention (same author).
        "xyz.aethersx2.android" to
            LaunchSpec("xyz.aethersx2.android.EmulationActivity",
                       romExtraKey = "bootPath", action = Intent.ACTION_MAIN),
        "xyz.trizle.nethersx2" to
            LaunchSpec("xyz.aethersx2.android.EmulationActivity",
                       romExtraKey = "bootPath", action = Intent.ACTION_MAIN),
        "net.play.ptmk.ps2" to
            LaunchSpec("xyz.aethersx2.android.EmulationActivity",
                       romExtraKey = "bootPath", action = Intent.ACTION_MAIN),
        // GameCube / Wii — Dolphin boots a game when MainActivity gets an "AutoStartFile" path
        // extra. It must NOT be an ACTION_VIEW intent (its MainActivity rejects VIEW), otherwise
        // it just opens the game-list menu and sits on a loading screen.
        "org.dolphinemu.dolphinemu" to
            LaunchSpec("org.dolphinemu.dolphinemu.ui.main.MainActivity",
                       romExtraKey = "AutoStartFile", action = Intent.ACTION_MAIN),
        // PSP — PPSSPP reads getData().
        "org.ppsspp.ppsspp"     to LaunchSpec("org.ppsspp.ppsspp.PpssppActivity"),
        "org.ppsspp.ppssppgold" to LaunchSpec("org.ppsspp.ppsspp.PpssppActivity"),
        // NDS — melonDS's EmulatorActivity crashes (ConcurrentModificationException) when launched
        // cold from outside, and the warm-then-launch workaround is blocked by Android's
        // background-activity-start policy. Open its ROM list instead so it never crashes; the
        // user taps the game there. (DraStic or a RetroArch DS core give true direct-boot.)
        "me.magnum.melonds" to LaunchSpec("me.magnum.melonds.ui.romlist.RomListActivity"),
        // N64 — Mupen64Plus FZ splash screen forwards to GameActivity.
        "org.mupen64plusae.v3.fzurita"     to LaunchSpec("paulscode.android.mupen64plusae.SplashActivity"),
        "org.mupen64plusae.v3.fzurita.pro" to LaunchSpec("paulscode.android.mupen64plusae.SplashActivity"),
        // Dreamcast — Redream only accepts a file:// scheme.
        "io.recompiled.redream" to LaunchSpec("io.recompiled.redream.MainActivity"),
        // Saturn — Yaba Sanshiro game activity reads getData().
        "org.devmiyax.yabasanshioro2"     to LaunchSpec("org.uoyabause.android.Yabause"),
        "org.devmiyax.yabasanshioro2.pro" to LaunchSpec("org.uoyabause.android.Yabause"),
        // 3DS — Citra (MMJ) reads the ROM from a "GamePath" extra.
        "org.citra.emu" to LaunchSpec("org.citra.emu.ui.EmulationActivity",
                                      romExtraKey = "GamePath", action = Intent.ACTION_MAIN),
        // Switch — Yuzu-derived emulators expose an EmulationActivity that reads getData().
        "dev.eden.eden_emulator"  to LaunchSpec("org.yuzu.yuzu_emu.activities.EmulationActivity"),
        "dev.eden.emulator"       to LaunchSpec("org.yuzu.yuzu_emu.activities.EmulationActivity"),
        "org.yuzu.yuzu_emu"       to LaunchSpec("org.yuzu.yuzu_emu.activities.EmulationActivity"),
        "org.sudachi.sudachi_emu" to LaunchSpec("org.sudachi.sudachi_emu.activities.EmulationActivity"),
    )
}

class NoEmulatorConfiguredException(platformId: String) :
    Exception("No emulator configured for platform: $platformId")
