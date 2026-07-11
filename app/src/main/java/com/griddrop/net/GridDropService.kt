package com.griddrop.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.griddrop.GridDropApplication
import com.griddrop.R
import com.griddrop.Role
import com.griddrop.SERVER_PORT
import com.griddrop.util.NetUtils


class GridDropService : Service() {

    private var server: GridDropServer? = null
    private var gatewayIp: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        val role = Role.valueOf(intent?.getStringExtra(EXTRA_ROLE) ?: Role.RECEIVE.name)
        gatewayIp = intent?.getStringExtra(EXTRA_IP) ?: NetUtils.findHotspotAddressString()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                buildNotification(role),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIF_ID, buildNotification(role))
        }

        val app = application as GridDropApplication
        server = GridDropServer(this, app.repository, role).also {
            runCatching { it.start() }.onFailure { e -> Log.e(TAG, "server start failed", e) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    private fun stopEverything() {
        runCatching { server?.stop() }
        server = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(role: Role): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.channel_desc) }
            nm.createNotificationChannel(channel)
        }
        val url = gatewayIp?.let { "http://$it:$SERVER_PORT" } ?: "the hotspot address"
        val text = when (role) {
            Role.SEND -> "Sharing files at $url"
            Role.RECEIVE -> "Ready to receive at $url"
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle("GridDrop active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    override fun onTimeout(startId: Int) {
        
        stopEverything()
    }

    companion object {
        private const val TAG = "GridDropService"
        private const val CHANNEL_ID = "griddrop_transfer"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "com.griddrop.STOP"
        const val EXTRA_ROLE = "role"
        const val EXTRA_IP = "ip"

        fun start(context: Context, role: Role, ip: String?) {
            val intent = Intent(context, GridDropService::class.java).apply {
                putExtra(EXTRA_ROLE, role.name)
                putExtra(EXTRA_IP, ip)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, GridDropService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
