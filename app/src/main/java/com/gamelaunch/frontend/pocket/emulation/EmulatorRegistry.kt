package com.gamelaunch.frontend.pocket.emulation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Central registry of supported emulators and their launch contracts.
 *
 * All launch contracts are marked with // ASSUMED — verify against installed APK comments.
 * Package names should be verified with:
 *   adb shell dumpsys package <pkg> | grep -A2 'MAIN'
 */
object EmulatorRegistry {

    /**
     * All known emulator definitions.
     * Order matters for UI display — more commonly used emulators appear first.
     */
    val DEFINITIONS: List<EmulatorDefinition> = listOf(
        // ============================================================
        // RETROARCH — Multi-system frontend
        // STATUS: ASSUMED — verify: adb shell dumpsys package com.retroarch.aarch64 | grep -A2 'MAIN'
        // ============================================================
        EmulatorDefinition(
            id = "RETROARCH", // Legacy multi-system (NES/SNES/GB/GBC/Genesis/N64 etc) — NOT used for GBA (use PIZZABOY_GBA instead; mGBA core crashes on 8 Elite)
            displayName = "RetroArch",
            systems = setOf(
                EmulatorSystem.NES,
                EmulatorSystem.SNES,
                EmulatorSystem.GB,
                EmulatorSystem.GBC,
                EmulatorSystem.N64,
                EmulatorSystem.DREAMCAST,
                EmulatorSystem.SATURN
                // GBA → Pizza Boy A, NDS → melonDS, PS1 → DuckStation, PSP → PPSSPP
            ),
            packageCandidates = listOf(
                "com.retroarch.aarch64",  // 64-bit preferred
                "com.retroarch"           // 32-bit fallback
            ),
            launchActivity = ".browser.mainmenu.MainMenuActivity", // VERIFIED from dumpsys (was RetroActivityFuture)
            launchAction = null,
            romIntentKey = "ROM", // ASSUMED — verify against installed APK
            romIntentType = RomIntentType.FILE_PATH,
            requiresSafUriGrant = false,
            supportsCustomDriver = false,
            experimental = false,
            exportMethod = "Export savestate via RetroArch menu → Save State",
            extraKeys = mapOf(
                // "LIBRETRO" to "/path/to/core.so" — must be set by caller based on system
            ),
            notes = "Requires second extra 'LIBRETRO' with core path. Core selection logic not in this definition."
        ),

        // ============================================================
        // PIZZA BOY A — Game Boy Advance
        // STATUS: VERIFIED from Pizza Boy A 2.3.3 package manifest
        // ============================================================
        EmulatorDefinition(
            id = "PIZZABOY_GBA",
            displayName = "Pizza Boy A",
            systems = setOf(EmulatorSystem.GBA),
            packageCandidates = listOf("it.dbtecno.pizzaboygba"),
            launchActivity = ".MainActivity",
            launchAction = "android.intent.action.VIEW",
            romIntentKey = null,
            romIntentType = RomIntentType.CONTENT_URI,
            requiresSafUriGrant = true,
            supportsCustomDriver = false,
            experimental = false,
            exportMethod = "Export saves from Pizza Boy A's settings.",
            notes = "Pass a FileProvider content URI as Intent.data with a temporary read grant."
        ),

        // ============================================================
        // DOLPHIN — GameCube / Wii
        // STATUS: VERIFIED from ES-DE Android definitions + APK manifest dumpsys
        // Dolphin 2603a uses: TvMainActivity, MAIN + LEANBACK_LAUNCHER.
        // Its StartupHandler prioritizes a content URI in Intent.data for scoped storage.
        // ============================================================
        EmulatorDefinition(
            id = "DOLPHIN",
            displayName = "Dolphin",
            systems = setOf(
                EmulatorSystem.GAMECUBE,
                EmulatorSystem.WII
            ),
            packageCandidates = listOf(
                "org.dolphinemu.dolphinemu"
            ),
            launchActivity = ".ui.main.TvMainActivity", // VERIFIED: ES-DE uses TvMainActivity not AppLinkActivity
            launchAction = "android.intent.action.MAIN", // VERIFIED
            launchCategory = "android.intent.category.LEANBACK_LAUNCHER", // VERIFIED
            // AutoStartFile is Dolphin's legacy raw-path fallback. A content URI must be placed in
            // Intent.data so Android can grant it to Dolphin and StartupHandler selects it first.
            romIntentKey = null,
            romIntentType = RomIntentType.CONTENT_URI,
            requiresSafUriGrant = true,
            supportsCustomDriver = false,
            experimental = false,
            exportMethod = "Memory card files in Dolphin/GC/<region>/ and Dolphin/Wii/",
            notes = "Pass a FileProvider content URI as Intent.data with a temporary read grant."
        ),

        // ============================================================
        // DUCKSTATION — PlayStation 1
        // STATUS: ASSUMED — verify: adb shell dumpsys package com.github.stenzek.duckstation | grep -A2 'MAIN'
        // ============================================================
        EmulatorDefinition(
            id = "DUCKSTATION",
            displayName = "DuckStation",
            systems = setOf(EmulatorSystem.PS1),
            packageCandidates = listOf(
                "com.github.stenzek.duckstation"
            ),
            launchActivity = null, // Uses MAIN launcher
            launchAction = null,
            romIntentKey = "boot_path", // ASSUMED — verify against installed APK
            romIntentType = RomIntentType.FILE_PATH,
            requiresSafUriGrant = false,
            supportsCustomDriver = false,
            experimental = false,
            exportMethod = "Memory cards in DuckStation/memcards/",
            notes = ""
        ),

        // ============================================================
        // PPSSPP — PlayStation Portable
        // STATUS: VERIFIED from PPSSPP 1.20.4 manifest: PpssppActivity accepts VIEW content URIs.
        // ============================================================
        EmulatorDefinition(
            id = "PPSSPP",
            displayName = "PPSSPP",
            systems = setOf(EmulatorSystem.PSP),
            packageCandidates = listOf(
                "org.ppsspp.ppsspp",      // Free version
                "org.ppsspp.ppssppgold"   // Gold version
            ),
            launchActivity = ".PpssppActivity",
            launchAction = "android.intent.action.VIEW",
            romIntentKey = null, // Uses Intent data URI
            romIntentType = RomIntentType.CONTENT_URI,
            requiresSafUriGrant = true,
            supportsCustomDriver = false,
            experimental = false,
            exportMethod = "Save data in PSP/SAVEDATA/",
            notes = "Pass a FileProvider content URI as Intent.data with a temporary read grant."
        ),

        // ============================================================
        // NETHERSX2 — PlayStation 2
        // STATUS: VERIFIED from NetherSX2 2.1-4248 APK: exported EmulationActivity reads
        // the ROM path from the String extra named "bootPath".
        // ============================================================
        EmulatorDefinition(
            id = "NETHERSX2",
            displayName = "NetherSX2",
            systems = setOf(EmulatorSystem.PS2),
            packageCandidates = listOf(
                "xyz.aethersx2.android"
            ),
            launchActivity = ".EmulationActivity",
            launchAction = "android.intent.action.VIEW",
            romIntentKey = "bootPath",
            romIntentType = RomIntentType.CONTENT_URI,
            requiresSafUriGrant = true,
            supportsCustomDriver = false,
            experimental = false,
            exportMethod = "Memory cards in NetherSX2/memcards/",
            notes = "Launch the exported EmulationActivity with a granted content URI String in bootPath."
        ),

        // ============================================================
        // EDEN — Nintendo Switch
        // STATUS: VERIFIED from dumpsys: EmulationActivity has VIEW + content:// scheme filter
        // ============================================================
        EmulatorDefinition(
            id = "EDEN",
            displayName = "Eden",
            systems = setOf(EmulatorSystem.SWITCH),
            packageCandidates = listOf(
                "dev.eden.eden_emulator"
            ),
            launchActivity = "org.yuzu.yuzu_emu.activities.EmulationActivity",
            launchAction = "android.intent.action.VIEW",
            romIntentKey = null,
            romIntentType = RomIntentType.CONTENT_URI,
            requiresSafUriGrant = true,
            supportsCustomDriver = true,
            experimental = false,
            exportMethod = "Save data in Eden/nand/user/save/",
            notes = "VIEW + content:// URI in Intent.data, same pattern as Azahar/Cemu."
        ),

        // ============================================================
        // CEMU_ANDROID — Nintendo Wii U
        // STATUS: VERIFIED from Cemu 0.5.2 manifest: EmulationActivity accepts VIEW content URIs.
        // NOTE: This is the SapphireRhodonite Android fork, not upstream desktop Cemu
        // ============================================================
        EmulatorDefinition(
            id = "CEMU_ANDROID",
            displayName = "Cemu",
            systems = setOf(EmulatorSystem.WIIU),
            packageCandidates = listOf(
                "info.cemu.cemu"
            ),
            launchActivity = ".emulation.EmulationActivity",
            launchAction = "android.intent.action.VIEW",
            romIntentKey = null,
            romIntentType = RomIntentType.CONTENT_URI,
            requiresSafUriGrant = true,
            supportsCustomDriver = false,
            experimental = false,
            exportMethod = "Save data in Cemu/mlc01/usr/save/",
            notes = "SapphireRhodonite Android fork; pass a granted content URI in Intent.data."
        ),

        // ============================================================
        // MELONDS — Nintendo DS
        // STATUS: VERIFIED from melonDS 2.0.1 manifest and frontend integration docs
        // ============================================================
        EmulatorDefinition(
            id = "MELONDS",
            displayName = "melonDS",
            systems = setOf(EmulatorSystem.NDS),
            packageCandidates = listOf(
                "me.magnum.melonds"
            ),
            launchActivity = ".ui.emulator.EmulatorActivity",
            launchAction = "me.magnum.melonds.LAUNCH_ROM",
            romIntentKey = null,
            romIntentType = RomIntentType.CONTENT_URI,
            requiresSafUriGrant = true,
            supportsCustomDriver = false,
            experimental = false,
            exportMethod = "Save files alongside ROMs or in melonDS/saves/",
            notes = "Pass a content URI as Intent.data with a temporary read grant."
        ),

        // ============================================================
        // AZAHAR — Nintendo 3DS
        // STATUS: VERIFIED from Azahar 2126.0 manifest: EmulationActivity accepts a VIEW
        // content URI with application/octet-stream.
        // ============================================================
        EmulatorDefinition(
            id = "AZAHAR",
            displayName = "Azahar",
            systems = setOf(EmulatorSystem.N3DS),
            packageCandidates = listOf(
                "org.azahar_emu.azahar",  // Azahar fork
                "org.citra_emu.citra"     // Legacy Citra fallback
            ),
            launchActivity = "org.citra.citra_emu.activities.EmulationActivity",
            launchAction = "android.intent.action.VIEW",
            romIntentKey = null,
            romIntentType = RomIntentType.CONTENT_URI,
            requiresSafUriGrant = true,
            supportsCustomDriver = false,
            experimental = false,
            exportMethod = "Save data in Azahar/sdmc/ or Citra/sdmc/",
            notes = "Pass a FileProvider content URI as Intent.data with a temporary read grant."
        ),

        // ============================================================
        // VITA3K — PlayStation Vita (EXPERIMENTAL)
        // STATUS: ASSUMED — verify: adb shell dumpsys package org.vita3k.emulator | grep -A2 'MAIN'
        // ============================================================
        EmulatorDefinition(
            id = "VITA3K",
            displayName = "Vita3K",
            systems = setOf(EmulatorSystem.PSVITA),
            packageCandidates = listOf(
                "org.vita3k.emulator"
            ),
            launchActivity = null, // ASSUMED — verify against installed APK
            launchAction = null,
            romIntentKey = null, // ASSUMED — verify against installed APK
            romIntentType = RomIntentType.CONTENT_URI,
            requiresSafUriGrant = false,
            supportsCustomDriver = false,
            experimental = true,
            exportMethod = "Save data in Vita3K/ux0/user/00/savedata/",
            notes = "EXPERIMENTAL — Android port stability varies"
        ),

        // ============================================================
        // PS3NATIVE — PlayStation 3 (EXPERIMENTAL)
        // STATUS: VERIFIED — com.ps3native.standard (v0.2.1 standard variant) | grep -A2 'MAIN'
        // ============================================================
        EmulatorDefinition(
            id = "PS3NATIVE",
            displayName = "PS3 Native",
            systems = setOf(EmulatorSystem.PS3),
            packageCandidates = listOf(
                "com.ps3native.standard"
            ),
            launchActivity = null, // ASSUMED — verify against installed APK
            launchAction = null,
            romIntentKey = null, // ASSUMED — verify against installed APK
            romIntentType = RomIntentType.FILE_PATH,
            requiresSafUriGrant = false,
            supportsCustomDriver = false,
            experimental = true,
            exportMethod = "Unknown — experimental emulator",
            notes = "EXPERIMENTAL — PS3 emulation on Android is very early stage"
        )
    )

    /**
     * Find all emulator definitions that support a given system.
     */
    fun forSystem(system: EmulatorSystem): List<EmulatorDefinition> =
        DEFINITIONS.filter { it.supportsSystem(system) }

    /**
     * Find all emulator definitions that have at least one package installed.
     */
    fun findInstalled(context: Context): List<EmulatorDefinition> {
        val pm = context.packageManager
        return DEFINITIONS.filter { def ->
            def.packageCandidates.any { pkg ->
                isPackageInstalled(pm, pkg)
            }
        }
    }

    /**
     * Find the installed package for an emulator definition.
     * Returns the first installed package from the candidates, or null if none installed.
     */
    fun findInstalledPackage(context: Context, def: EmulatorDefinition): String? {
        val pm = context.packageManager
        return def.packageCandidates.firstOrNull { pkg ->
            isPackageInstalled(pm, pkg)
        }
    }

    /**
     * Build a launch Intent for the given emulator and ROM path.
     * Returns null if no package is installed or intent cannot be built.
     *
     * Note: For RetroArch, caller must add the "LIBRETRO" extra with the core path.
     */
    fun buildLaunchIntent(
        def: EmulatorDefinition,
        romPath: String,
        context: Context
    ): Intent? {
        val installedPackage = findInstalledPackage(context, def) ?: return null

        val intent = Intent().apply {
            // Set package
            setPackage(installedPackage)

            // Set action
            action = def.launchAction ?: Intent.ACTION_MAIN

            // Set component if specific activity is defined
            if (def.launchActivity != null) {
                val activityClass = if (def.launchActivity.startsWith('.')) {
                    "$installedPackage${def.launchActivity}"
                } else {
                    def.launchActivity
                }
                setClassName(installedPackage, activityClass)
            }

            // Add category
            val category = def.launchCategory ?: if (action == Intent.ACTION_MAIN) Intent.CATEGORY_LAUNCHER else Intent.CATEGORY_DEFAULT
            addCategory(category)

            // Handle ROM path based on intent type
            when (def.romIntentType) {
                RomIntentType.FILE_PATH -> {
                    if (def.romIntentKey != null) {
                        putExtra(def.romIntentKey, romPath)
                    }
                }

                RomIntentType.CONTENT_URI -> {
                    val file = File(romPath)
                    val uri = try {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("EmulatorRegistry", "FileProvider failed, using file URI: ${e.message}")
                        Uri.fromFile(file)
                    }

                    if (def.romIntentKey != null) {
                        // Emulator contracts such as NetherSX2 call getStringExtra() and then
                        // pass this value to their native URI-aware file layer. Keep the URI in
                        // Intent.data too so Android applies FLAG_GRANT_READ_URI_PERMISSION to it.
                        putExtra(def.romIntentKey, uri.toString())
                        setDataAndType(uri, getMimeType(romPath))
                    } else {
                        // Set as intent data
                        setDataAndType(uri, getMimeType(romPath))
                    }

                    if (def.requiresSafUriGrant) {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }

                RomIntentType.DATA_URI -> {
                    val file = File(romPath)
                    data = Uri.fromFile(file)
                }

                RomIntentType.FILE_ARRAY_LIST -> {
                    if (def.romIntentKey != null) {
                        putStringArrayListExtra(def.romIntentKey, arrayListOf(romPath))
                    }
                }
            }

            // Add any extra keys
            def.extraKeys.forEach { (key, value) ->
                putExtra(key, value)
            }

            // Start as new task
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return intent
    }

    /**
     * Find an emulator definition by ID.
     */
    fun findById(id: String): EmulatorDefinition? =
        DEFINITIONS.find { it.id.equals(id, ignoreCase = true) }

    /**
     * Get all non-experimental emulators.
     */
    fun stableDefinitions(): List<EmulatorDefinition> =
        DEFINITIONS.filter { !it.experimental }

    /**
     * Get all experimental emulators.
     */
    fun experimentalDefinitions(): List<EmulatorDefinition> =
        DEFINITIONS.filter { it.experimental }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getMimeType(path: String): String {
        val extension = path.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "iso", "bin", "img" -> "application/octet-stream"
            "chd" -> "application/x-chd"
            "cue" -> "application/x-cue"
            "nsp", "xci" -> "application/x-nintendo-switch-rom"
            "3ds", "cia" -> "application/x-nintendo-3ds-rom"
            "nds" -> "application/x-nintendo-ds-rom"
            "vpk" -> "application/x-playstation-vita-pkg"
            else -> "application/octet-stream"
        }
    }
}
