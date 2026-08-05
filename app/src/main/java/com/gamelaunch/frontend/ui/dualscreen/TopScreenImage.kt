package com.gamelaunch.frontend.ui.dualscreen

/** Which media the dual-screen top panel shows for the selected game (in game view). */
enum class TopScreenImage(val label: String) {
    /** The game's wheel/logo art (default) — reads as branded title art. */
    MARQUEE("Marquee"),
    /** An in-game screenshot. */
    SCREENSHOT("Screenshot"),
    /** A composited "mix" image (box + screenshot + logo), à la ES-DE / ScreenScraper. */
    MIXIMAGE("Miximage");

    companion object {
        val DEFAULT = MARQUEE
        fun fromName(name: String): TopScreenImage =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
