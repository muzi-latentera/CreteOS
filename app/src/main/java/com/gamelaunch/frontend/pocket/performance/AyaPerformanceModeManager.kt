package com.gamelaunch.frontend.pocket.performance

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.ayaneo.gamewindow.AyaAidlInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The hardware performance modes exposed by AYANEO System Settings on the Pocket FIT. */
enum class AyaPerformanceMode(val vendorValue: Int) {
    ECO(0),
    BALANCE(1),
    STREAMING(2),
    GAMING(3),
    MAX(4)
}

/**
 * Small client for AYANEO GameWindow's exported performance service.
 *
 * This deliberately uses the same public binder command as AYANEO System Settings instead of
 * writing vendor configuration files or requiring root. Requests are ignored on non-AYANEO
 * devices, so the normal CreteOS launch path is unchanged elsewhere.
 */
@Singleton
class AyaPerformanceModeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var service: AyaAidlInterface? = null
    private var binding = false
    private var desiredMode: AyaPerformanceMode? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            binding = false
            service = AyaAidlInterface.Stub.asInterface(binder)
            desiredMode?.let(::sendMode)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            binding = false
            service = null
        }

        override fun onBindingDied(name: ComponentName) {
            binding = false
            service = null
        }

        override fun onNullBinding(name: ComponentName) {
            binding = false
            service = null
            Log.w(TAG, "AYANEO performance service returned a null binder")
        }
    }

    fun useEcoMode() = setMode(AyaPerformanceMode.ECO)

    fun useGamingMode() = setMode(AyaPerformanceMode.GAMING)

    fun setMode(mode: AyaPerformanceMode) {
        if (!isAyaDevice()) return
        mainHandler.post {
            desiredMode = mode
            if (service != null) {
                sendMode(mode)
            } else if (!binding) {
                bind()
            }
        }
    }

    private fun bind() {
        val intent = Intent().setComponent(SERVICE_COMPONENT)
        binding = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.onFailure {
            Log.w(TAG, "Unable to bind AYANEO performance service", it)
        }.getOrDefault(false)

        if (!binding) Log.w(TAG, "AYANEO performance service is unavailable")
    }

    private fun sendMode(mode: AyaPerformanceMode) {
        val remote = service ?: return
        runCatching {
            remote.send("$CLIENT_ID:$MESSAGE_TYPE:$SET_MODE_COMMAND:${mode.vendorValue}")
        }.onSuccess {
            Log.i(TAG, "Applied AYANEO ${mode.name} mode")
        }.onFailure {
            service = null
            binding = false
            Log.w(TAG, "Failed to apply AYANEO ${mode.name} mode", it)
        }
    }

    private fun isAyaDevice(): Boolean =
        Build.MANUFACTURER.contains("AYANEO", ignoreCase = true) ||
            Build.BRAND.contains("AYANEO", ignoreCase = true)

    private companion object {
        const val TAG = "AyaPerformanceMode"
        const val CLIENT_ID = "creteos"
        const val MESSAGE_TYPE = "msg_type_performance"
        const val SET_MODE_COMMAND = "com_set_performance_mode"
        val SERVICE_COMPONENT = ComponentName(
            "com.ayaneo.gamewindow",
            "com.ayaneo.gamewindow.utils.aidl.AyaAidlService"
        )
    }
}
