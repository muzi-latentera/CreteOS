package com.gamelaunch.frontend.data.sync

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the embedded Syncthing daemon: builds the process that runs the bundled `libsyncthing.so`,
 * and talks to it over its local REST API. The daemon never shows its own web UI — eOr drives it.
 *
 * On first launch the daemon generates `config.xml` (with its own random API key) and binds its GUI
 * to `127.0.0.1:8384`. We read that generated API key from config.xml rather than trying to inject
 * one — Syncthing 1.x ignores STGUIAPIKEY when a config already exists.
 */
@Singleton
class SyncthingController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val guiAddress = "127.0.0.1:$GUI_PORT"

    private val home: File get() = File(context.filesDir, "syncthing").apply { mkdirs() }
    private val configFile: File get() = File(home, "config.xml")
    private val binary: File get() = File(context.applicationInfo.nativeLibraryDir, "libsyncthing.so")

    private val client = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    /** True if the bundled daemon exists for this device's ABI. */
    fun isSupported(): Boolean = binary.exists()

    /** Build (but don't start) the daemon process — the foreground service owns its lifecycle. */
    fun buildProcess(): Process {
        val pb = ProcessBuilder(
            binary.absolutePath,
            "-home", home.absolutePath,
            "-no-browser",
            "-no-restart",
            "-logflags", "0"
        )
        pb.environment().apply {
            put("HOME", home.absolutePath)
            put("STNORESTART", "1")   // we manage the process; don't let it fork-restart
            put("STNOUPGRADE", "1")   // never self-upgrade a bundled binary
            put("STGUIADDRESS", guiAddress)
            put("TMPDIR", context.cacheDir.absolutePath)
        }
        pb.redirectErrorStream(true)
        return pb.start()
    }

    /** Poll the REST API until it answers, then return this device's Syncthing ID (`myID`). */
    suspend fun awaitDeviceId(timeoutMs: Long = 25_000): String? = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val key = readApiKey()
            if (key != null) {
                val id = getJson("/rest/system/status", key)?.optString("myID")?.takeIf { it.isNotBlank() }
                if (id != null) return@withContext id
            }
            delay(500)
        }
        null
    }

    /** The daemon's API key, parsed from the config.xml it generates on first launch. */
    private fun readApiKey(): String? {
        if (!configFile.exists()) return null
        return runCatching {
            APIKEY_RE.find(configFile.readText())?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun getJson(path: String, apiKey: String): JSONObject? = runCatching {
        val request = Request.Builder()
            .url("http://$guiAddress$path")
            .header("X-API-Key", apiKey)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string()?.let { JSONObject(it) }
        }
    }.onFailure { Log.d(TAG, "REST $path not ready: ${it.message}") }.getOrNull()

    /** A local folder to sync (one Syncthing "folder"), with a stable ID shared across devices. */
    data class SyncFolder(val id: String, val label: String, val path: String)

    /**
     * Register the given save folders with the daemon (idempotent upsert). Each is a sendreceive
     * folder; the local device is added automatically, peers are attached in [addPeer].
     */
    suspend fun configureFolders(folders: List<SyncFolder>): Boolean = withContext(Dispatchers.IO) {
        val key = readApiKey() ?: return@withContext false
        folders.all { folder ->
            val body = JSONObject().apply {
                put("id", folder.id)
                put("label", folder.label)
                put("path", folder.path)
                put("type", "sendreceive")
                put("fsWatcherEnabled", true)
                put("rescanIntervalS", 3600)
            }
            putJson("/rest/config/folders/${folder.id}", key, body)
        }
    }

    /** Add a peer device (from a scanned/pasted ID) as an introducer and share all eOr folders with it. */
    suspend fun addPeer(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        val key = readApiKey() ?: return@withContext false
        val id = deviceId.trim().uppercase()
        if (id.isBlank()) return@withContext false
        val device = JSONObject().apply {
            put("deviceID", id)
            put("name", "eOr device")
            put("introducer", true)
            put("autoAcceptFolders", true)
            put("addresses", org.json.JSONArray(listOf("dynamic")))
        }
        if (!putJson("/rest/config/devices/$id", key, device)) return@withContext false
        // Share every eOr-managed folder with the new peer.
        shareEorFoldersWith(id, key)
    }

    private fun shareEorFoldersWith(deviceId: String, key: String): Boolean = runCatching {
        val req = Request.Builder().url("http://$guiAddress/rest/config/folders").header("X-API-Key", key).build()
        val arrText = client.newCall(req).execute().use { if (!it.isSuccessful) return false else it.body?.string() } ?: return false
        val arr = org.json.JSONArray(arrText)
        for (i in 0 until arr.length()) {
            val f = arr.getJSONObject(i)
            val fid = f.optString("id")
            if (!fid.startsWith(EOR_FOLDER_PREFIX)) continue
            val devices = f.optJSONArray("devices") ?: org.json.JSONArray()
            if ((0 until devices.length()).any { devices.getJSONObject(it).optString("deviceID") == deviceId }) continue
            devices.put(JSONObject().put("deviceID", deviceId))
            f.put("devices", devices)
            putJson("/rest/config/folders/$fid", key, f)
        }
        true
    }.getOrElse { Log.d(TAG, "shareEorFoldersWith failed: ${it.message}"); false }

    private fun putJson(path: String, apiKey: String, body: JSONObject): Boolean = runCatching {
        val request = Request.Builder()
            .url("http://$guiAddress$path")
            .header("X-API-Key", apiKey)
            .put(body.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { it.isSuccessful }
    }.getOrElse { Log.d(TAG, "PUT $path failed: ${it.message}"); false }

    companion object {
        const val EOR_FOLDER_PREFIX = "eor-"
        private const val TAG = "SyncthingController"
        private const val GUI_PORT = 8384
        private val APIKEY_RE = Regex("<apikey>(.*?)</apikey>")
        private val JSON = "application/json".toMediaType()
    }
}
