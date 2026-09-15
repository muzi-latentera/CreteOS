package com.gamelaunch.frontend.pocket.providers.impl

/**
 * Seed data for PS Cloud games available via Asobi (com.asobi.droid).
 *
 * conceptId = candidate PSN concept ID. Catalogue presence and direct-launch behavior are
 * verified separately on the target device.
 * platform  = target generation (ps5/ps4/ps3) — informational only
 *
 * IDs are a small, reviewable candidate set; do not treat them as verified until the actual
 * Asobi app accepts the launch extra and starts the selected title.
 */
object ASOBISeedData {

    data class Game(
        val title: String,
        val conceptId: String,
        val platform: String = "ps5",
    )

    val GAMES: List<Game> = listOf(
        // ── Verified PS5 / cross-gen ───────────────────────────────────────────
        Game("Ghost of Yōtei",                        "10003923", "ps5"),
        Game("Silent Hill 2",                         "10005529", "ps5"),
        Game("Until Dawn",                            "10007897", "ps5"),
        Game("Abiotic Factor",                        "10012483", "ps5"),
        Game("Blue Prince",                           "10012002", "ps5"),
        Game("Warhammer 40,000: Space Marine 2",      "10003330", "ps5"),
        Game("Lords of the Fallen",                   "10002848", "ps5"),
        Game("Banishers: Ghosts of New Eden",         "10006098", "ps5"),
        Game("Pacific Drive",                         "10006288", "ps5"),
        Game("Darkest Dungeon II",                    "10009597", "ps5"),
        Game("Enotria: The Last Song",                "10008826", "ps5"),
        Game("Skate Story",                           "10014385", "ps5"),
        Game("Prince of Persia: The Lost Crown",      "10005985", "ps5"),
        Game("Session: Skate Sim",                    "10004240", "ps5"),
        Game("Thymesia",                              "10003778", "ps5"),

        // ── Cross-gen PS4+PS5 ──────────────────────────────────────────────────
        Game("Assassin's Creed Mirage",                "10004865", "ps5"),
        Game("Avatar: Frontiers of Pandora™",         "235179",   "ps5"),
        Game("Resident Evil Village",                 "235235",   "ps5"),
        Game("Yakuza: Like a Dragon",                 "235238",   "ps5"),
        Game("Immortals Fenyx Rising",                "234423",   "ps4"),
        Game("FINAL FANTASY VII REMAKE INTERGRADE",   "228744",   "ps5"),
        Game("Persona 5 Royal",                       "10002771", "ps4"),
        Game("Vampyr",                                "205200",   "ps4"),

        // ── PS4 only ───────────────────────────────────────────────────────────
        Game("Bloodborne™",                           "200520",   "ps4"),
        Game("Deus Ex: Mankind Divided",              "200943",   "ps4"),
        Game("The Long Dark",                         "229391",   "ps4"),
        Game("Celeste",                               "231477",   "ps4"),
        Game("Steep",                                 "221765",   "ps4"),
        Game("Shadow of the Colossus",                "225857",   "ps4"),
        Game("The Last Guardian",                     "204826",   "ps4"),
    )
}
