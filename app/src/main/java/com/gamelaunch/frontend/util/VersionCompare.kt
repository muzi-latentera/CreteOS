package com.gamelaunch.frontend.util

/** Version comparison shared by the app self-update and emulator-update checks. */
object VersionCompare {

    /** Numeric dotted-version compare. Vendor/channel suffixes do not change the version core. */
    fun isNewer(latest: String, current: String): Boolean {
        val l = numericCore(latest) ?: return false
        val c = numericCore(current) ?: return false
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /**
     * Examples: `v2.1-4248` → 2.1, `2.0.1 GH` → 2.0.1, `2126.0-vanilla` → 2126.0.
     * Treating suffix digits as another version component caused stable/channel builds to be
     * compared incorrectly.
     */
    private fun numericCore(raw: String): List<Int>? {
        val core = Regex("""^[vV]?(\d+(?:\.\d+)*)""")
            .find(raw.trim())
            ?.groupValues
            ?.getOrNull(1)
            ?: return null
        return core.split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }
    }
}
