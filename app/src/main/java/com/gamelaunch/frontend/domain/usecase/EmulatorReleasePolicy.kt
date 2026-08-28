package com.gamelaunch.frontend.domain.usecase

/** Guardrails for release feeds whose labels are not safe to compare to Android APK versions. */
object EmulatorReleasePolicy {
    private val unsupportedVersionSchemes = setOf(
        // Android reports 0.2.0-12 while the CI repository uses unrelated tags such as 4074.
        "org.vita3k.emulator",
    )

    private val developmentMarkers = Regex(
        pattern = """\b(dev(?:elopment)?|nightly|canary|alpha|beta|preview|release candidate|rc)\b""",
        option = RegexOption.IGNORE_CASE,
    )

    fun canComparePackage(packageName: String): Boolean =
        packageName !in unsupportedVersionSchemes

    fun isStableRelease(tag: String, name: String): Boolean =
        !developmentMarkers.containsMatchIn("$tag $name")
}
