package com.gamelaunch.frontend.pocket.providers

enum class ProviderId(val displayName: String) {
    GAME_NATIVE("GameNative"),
    GAME_HUB_LITE("GameHub Lite"),
    WIN_NATIVE("WinNative"),
    WINLATOR("Winlator CMod"),
    MOONLIGHT("Moonlight"),
    GEFORCE_NOW("GeForce NOW"),
    ANDROID_SHORTCUT("Android Shortcut"),
    EMULATOR("Emulator");

    /** Cloud video decoding is cheap enough for Eco; every other provider runs on this device. */
    val runsLocally: Boolean
        get() = this != MOONLIGHT && this != GEFORCE_NOW
}
