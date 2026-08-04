package com.gamelaunch.frontend.data.network

import android.util.Base64

/**
 * Decodes the ScreenScraper *developer* credentials that are obfuscated into BuildConfig at build
 * time (see `obfuscateSecret` in app/build.gradle.kts). The XOR [KEY] must stay identical to the
 * build script's `ssObfKey`.
 *
 * This is deliberately light **obfuscation, not encryption**: it stops a casual `strings` grab of
 * the APK, but anyone reading the (open-source) code can reverse it. Embedding a usable secret in a
 * distributed client is inherently recoverable — the only theft-proof design is a server-side proxy
 * that holds the credentials so the app never ships them.
 */
object Secrets {
    private const val KEY = "e0r-ss-obf-2026"

    fun reveal(obfuscated: String): String {
        if (obfuscated.isBlank()) return ""
        return runCatching {
            val data = Base64.decode(obfuscated, Base64.NO_WRAP)
            val k = KEY.toByteArray(Charsets.UTF_8)
            String(ByteArray(data.size) { i -> (data[i].toInt() xor k[i % k.size].toInt()).toByte() }, Charsets.UTF_8)
        }.getOrDefault("")
    }
}
