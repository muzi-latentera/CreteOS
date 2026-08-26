package com.gamelaunch.frontend.domain.usecase

import android.content.Context
import com.gamelaunch.frontend.BuildConfig
import com.gamelaunch.frontend.util.VersionCompare
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

/** A newer published GitHub release than the installed build. */
data class AppUpdate(val versionName: String, val releaseUrl: String)

/**
 * Checks the project's GitHub "latest release" and reports it when it's newer than the installed
 * app version. No backend required — it's a plain read of the public Releases API on app launch.
 */
class CheckForUpdateUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient()

    suspend operator fun invoke(): AppUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val current = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: return@runCatching null

            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string() ?: return@runCatching null
                val json = JSONObject(body)
                // Ignore drafts / pre-releases — only ship stable versions to users.
                if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@runCatching null

                val tag = json.optString("tag_name").ifBlank { return@runCatching null }
                val latest = tag.trimStart('v', 'V')
                if (!VersionCompare.isNewer(latest, current)) return@runCatching null

                val url = json.optString("html_url").ifBlank { RELEASES_PAGE }
                AppUpdate(latest, url)
            }
        }.getOrNull()
    }

    companion object {
        private val REPO = BuildConfig.UPDATE_REPO
        private val UPSTREAM_REPO = BuildConfig.UPSTREAM_REPO
        private val LATEST_RELEASE_URL get() = "https://api.github.com/repos/$REPO/releases/latest"
        private val RELEASES_PAGE get() = "https://github.com/$REPO/releases/latest"
    }
}
