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
            id = "RETROARCH",
            displayName = "RetroArch",
            systems = setOf(
                EmulatorSystem.NES,
                EmulatorSystem.SNES,
                EmulatorSystem.GB,
                EmulatorSystem.GBC,
                EmulatorSystem.GBA,
                EmulatorSystem.N64,
                EmulatorSystem.DREAMCAST,
                EmulatorSystem.SATURN,
                EmulatorSystem.PS1,
                EmulatorSystem.PSP,
                EmulatorSystem.NDS
            ),
            packageCandidates = listOf(
                "com.retroarch.aarch64",  // 64-bit preferred
                "com.retroarch"           // 32-bit fallback
            ),
            launchActivity = ".browser.retroactivity.RetroActivityFuture", // ASSUMED — verify against installed APK
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
        // DOLPHIN — GameCube / Wii
        // STATUS: ASSUMED — verify: adb shell dumpsys package org.dolphinemu.dolphinemu | grep -A2 'MAIN'
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
            launchActivity = ".activities.EmulationActivity", // ASSUMED — verify against installed APK
            launchAction = null,
            romIntentKey = "filePaths", // ASSUMED — verify against installed APK
            romIntentType = RomIntentType.FILE_ARRAY_LIST,
            requiresSafUriGrant = false,
            supportsCustomDriver = false,
            experimental = false,
            exportMethod = "Memory card files in Dolphin/GC/<region>/ and Dolphin/Wii/",
            notes = "Uses ArrayList<String> for filePaths extra"
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
        // STATUS: ASSUMED — verify: adb shell dumpsys package org.ppsspp.ppsspp | grep -A2 'MAIN'
        // ============================================================
        EmulatorDefinition(
            id = "PPSSPP",
            displayName = "PPSSPP",
            systems = setOf(EmulatorSystem.PSP),
            packageCandidates = listOf(
                "org.ppsspp.ppsspp",      // Free version
                "org.ppsspp.ppssppgold"   // Gold version
            ),
            launchActivity = ".PpssppActivity", // ASSUMED — verify against installed APK
            launchAction = null,
            romIntentKey = null, // Uses Intent data URI
            romIntentType = RomIntentType.DATA_URI,
            requiresSafUriGrant = false,
            supportsCustomDriver = false,
            experimental = false,
            exportMethod = "Save data in PSP/SAVEDATA/",
            notes = "ROM path passed via setData(Uri)"
        ),

        // ============================================================
        // NETHERSX2 — PlayStation 2
        // STATUS: ASSUMED — verify: adb shell dumpsys package xyz.trixarian.nethersx2 | grep -A2 'MAIN'
        // ============================================================
        EmulatorDefinition(
            id = "NETHERSX2",
            displayName = "NetherSX2",
            systems = setOf(EmulatorSystem.PS2),
            packageCandidates = listOf(
                "xyz.trixarian.nethersx2"
            ),
            launchActivity = null,
            launchAction = "xyz.trixarian.nethersx2.OPEN", // ASSUMED — verify against installed APK
            romIntentKey = null, // Uses Intent data
            romIntentType = RomIntentType.CONTENT_URI,
            requiresSafUriGrant = true,
            supportsCustomDriver = false,
            experimental = false,
            exportMethod = "Memory cards in NetherSX2/memcards/",
            notes = "Requires SAF URI permission grant"
        ),

        // ============================================================
        // EDEN — Nintendo Switch
        // STATUS: ASSUMED — verify: adb shell dumpsys package | grep eden
        // NOTE: Package name unconfirmed — verify with installed APK
        // ============================================================
        EmulatorDefinition(
            id = "EDEN",
            displayName = "Eden",
            systems = setOf(EmulatorSystem.SWITCH),
            packageCandidates = listOf(
                "dev.eden_emu.eden",  // TBD — primary guess
                "org.eden_emu.eden"   // TBD — fallback guess
            ),
            launchActivity = null, // ASSUMED — verify against installed APK
            launchAction = null,
            romIntentKey = null, // ASSUMED — verify against installed APK
            romIntentType = RomIntentType.FILE_PATH,
            requiresSafUriGrant = false,
            supportsCustomDriver = true,
            experimental = false,
            exportMethod = "Save data in Eden/nand/user/save/",
            notes = "Package unconfirmed — verify with: adb shell dumpsys package | grep eden"
        ),

        // ============================================================
        // CEMU_ANDROID — Nintendo Wii U
        // STATUS: ASSUMED — verify: adb shell dumpsys package info.cemu.Cemu | grep -A2 'MAIN'
        // NOTE: This is the SapphireRhodonite Android fork, not upstream desktop Cemu
        // ============================================================
        EmulatorDefinition(
            id = "CEMU_ANDROID",
            displayName = "Cemu",
            systems = setOf(EmulatorSystem.WIIU),
            packageCandidates = listOf(
                "info.cemu.Cemu"
            ),
            launchActivity = null, // ASSUMED — verify against installed APK
            launchAction = null,
            romIntentKey = null, // ASSUMED — verify against installed APK
            romIntentType = RomIntentType.FILE_PATH,
            requiresSafUriGrant = false,
            supportsCustomDriver = false,
            experimental = false,
            exportMethod = "Save data in Cemu/mlc01/usr/save/",
            notes = "SapphireRhodonite Android fork, not upstream desktop Cemu"
        ),

        // ============================================================
        // MELONDS — Nintendo DS
        // STATUS: ASSUMED — verify: adb shell dumpsys package me.magnum.melonds | grep -A2 'MAIN'
        // ============================================================
        EmulatorDefinition(
            id = "MELONDS",
            displayName = "melonDS",
            systems = setOf(EmulatorSystem.NDS),
            packageCandidates = listOf(
                "me.magnum.melonds"
            ),
            launchActivity = null, // ASSUMED — verify against installed APK
            launchAction = null,
            romIntentKey = null, // ASSUMED — verify against installed APK
            romIntentType = RomIntentType.CONTENT_URI,
            requiresSafUriGrant = true,
            supportsCustomDriver = false,
            experimental = false,
            exportMethod = "Save files alongside ROMs or in melonDS/saves/",
            notes = "Requires SAF URI permission grant"
        ),

        // ============================================================
        // AZAHAR — Nintendo 3DS
        // STATUS: ASSUMED — verify: adb shell dumpsys package org.azahar_emu.azahar | grep -A2 'MAIN'
        // ============================================================
        EmulatorDefinition(
            id = "AZAHAR",
            displayName = "Azahar",
            systems = setOf(EmulatorSystem.N3DS),
            packageCandidates = listOf(
                "org.azahar_emu.azahar",  // Azahar fork
                "org.citra_emu.citra"     // Legacy Citra fallback
            ),
            launchActivity = ".activities.EmulationActivity", // ASSUMED — verify against installed APK
            launchAction = null,
            romIntentKey = null, // ASSUMED — verify against installed APK
            romIntentType = RomIntentType.FILE_PATH,
            requiresSafUriGrant = false,
            supportsCustomDriver = false,
            experimental = false,
            exportMethod = "Save data in Azahar/sdmc/ or Citra/sdmc/",
            notes = ""
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
        // STATUS: ASSUMED — verify: adb shell dumpsys package com.ps3native.emulator | grep -A2 'MAIN'
        // ============================================================
        EmulatorDefinition(
            id = "PS3NATIVE",
            displayName = "PS3 Native",
            systems = setOf(EmulatorSystem.PS3),
            packageCandidates = listOf(
                "com.ps3native.emulator"
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
            if (def.launchAction != null) {
                action = def.launchAction
            } else {
                action = Intent.ACTION_MAIN
            }

            // Set component if specific activity is defined
            if (def.launchActivity != null) {
                setClassName(installedPackage, "$installedPackage${def.launchActivity}")
            }

            // Add category for MAIN action
            if (action == Intent.ACTION_MAIN) {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

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
                        Uri.fromFile(file)
                    }
                    setDataAndType(uri, getMimeType(romPath))
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

            // Add any extra keys (caller should handle dynamic ones like LIBRETRO)
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
