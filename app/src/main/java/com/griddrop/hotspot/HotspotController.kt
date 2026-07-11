package com.griddrop.hotspot

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.LocalOnlyHotspotCallback
import android.net.wifi.WifiManager.LocalOnlyHotspotReservation
import android.os.Build
import android.util.Log
import android.util.SparseIntArray
import com.griddrop.util.Diag
import com.griddrop.util.NetUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


class HotspotController(context: Context) {

    private val appContext = context.applicationContext
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var reservation: LocalOnlyHotspotReservation? = null

    
    val canForceBand5: Boolean get() = Build.VERSION.SDK_INT >= 36

    
    private var wantHotspot = false

    private val _status = MutableStateFlow<HotspotStatus>(HotspotStatus.Idle)
    val status: StateFlow<HotspotStatus> = _status.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            _status.value = HotspotStatus.Unsupported
            return
        }
        
        
        if (_status.value is HotspotStatus.Starting) {
            Log.d(TAG, "start() ignored — a request is already pending")
            return
        }
        closeReservation()
        wantHotspot = true
        Diag.setBandMode("Auto (device picks)")
        _status.value = HotspotStatus.Starting
        try {
            wifi.startLocalOnlyHotspot(callback, null)
        } catch (t: Throwable) {
            
            Log.e(TAG, "startLocalOnlyHotspot failed", t)
            Diag.log("Hotspot start threw: ${t.javaClass.simpleName}: ${t.message}")
            _status.value = HotspotStatus.Failed(LocalOnlyHotspotCallback.ERROR_GENERIC)
        }
    }

    
    @SuppressLint("MissingPermission")
    fun startForced5GHz(): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        if (_status.value is HotspotStatus.Starting) return true
        closeReservation()
        wantHotspot = true
        Diag.setBandMode("Forced 5 GHz")
        _status.value = HotspotStatus.Starting
        return try {
            
            
            val channels = SparseIntArray().apply { put(SoftApConfiguration.BAND_5GHZ, 0) }
            val config = SoftApConfiguration.Builder().setChannels(channels).build()
            wifi.startLocalOnlyHotspotWithConfiguration(config, appContext.mainExecutor, callback)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "startLocalOnlyHotspotWithConfiguration failed", t)
            Diag.log("Forced 5 GHz start threw: ${t.javaClass.simpleName}: ${t.message}")
            _status.value = HotspotStatus.Failed(LocalOnlyHotspotCallback.ERROR_GENERIC)
            false
        }
    }

    fun stop() {
        wantHotspot = false
        closeReservation()
        when (_status.value) {
            is HotspotStatus.Running, is HotspotStatus.Starting -> _status.value = HotspotStatus.Idle
            else -> {}
        }
    }

    private fun closeReservation() {
        reservation?.let {
            try {
                it.close()
            } catch (t: Throwable) {
                Log.w(TAG, "reservation.close() failed", t)
            }
        }
        reservation = null
    }

    private val callback = object : LocalOnlyHotspotCallback() {
        override fun onStarted(res: LocalOnlyHotspotReservation) {
            
            
            if (!wantHotspot) {
                Log.d(TAG, "hotspot started but no longer wanted — closing it")
                Diag.log("Hotspot came up but was no longer wanted; closed it")
                try { res.close() } catch (t: Throwable) { Log.w(TAG, "close() failed", t) }
                return
            }
            reservation = res
            val info = res.toHotspotInfo()
            Diag.log("Hotspot up: SSID=\"${info.ssid}\" gateway=${info.gatewayIp}")
            _status.value = HotspotStatus.Running(info)
        }

        override fun onStopped() {
            reservation = null
            Diag.log("Hotspot stopped")
            if (_status.value is HotspotStatus.Running) _status.value = HotspotStatus.Idle
        }

        override fun onFailed(reason: Int) {
            reservation = null
            Diag.log("Hotspot failed (reason code $reason)")
            if (wantHotspot) _status.value = HotspotStatus.Failed(reason)
        }
    }

    private fun LocalOnlyHotspotReservation.toHotspotInfo(): HotspotInfo {
        var ssid = ""
        var pass: String? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            
            softApConfiguration?.let { cfg ->
                ssid = cfg.ssid ?: ""
                pass = cfg.passphrase
            }
        } else {
            @Suppress("DEPRECATION")
            wifiConfiguration?.let { wc ->
                ssid = wc.SSID?.trim('"') ?: ""
                pass = wc.preSharedKey?.trim('"')
            }
        }

        val gateway = NetUtils.findHotspotAddressString() ?: "192.168.49.1"
        return HotspotInfo(ssid = ssid, passphrase = pass, gatewayIp = gateway)
    }

    private companion object {
        const val TAG = "HotspotController"
    }
}
