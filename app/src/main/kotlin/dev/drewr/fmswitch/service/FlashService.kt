// Ported from FM-SWITCH App/Standard/Install/InstallingView.swift.
package dev.drewr.fmswitch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import dev.drewr.fmswitch.FmSwitchApplication
import dev.drewr.fmswitch.R

class FlashService : Service() {
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val name = intent?.getStringExtra(EXTRA_NAME).orEmpty()
        createNotificationChannel()
        acquireWakeLock()
        startInForeground(name)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground(name: String) {
        val notification = notification(name)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: SecurityException) {
            reportFailure(error)
        } catch (error: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                error is android.app.ForegroundServiceStartNotAllowedException
            ) {
                reportForegroundServiceStartNotAllowed(error)
            } else {
                throw error
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun reportForegroundServiceStartNotAllowed(error: android.app.ForegroundServiceStartNotAllowedException) =
        reportFailure(error)

    private fun reportFailure(error: Exception) {
        (application as FmSwitchApplication).appModel.value?.device?.appendLog(
            "Couldn't start the service; keep this screen open. ${error.message.orEmpty()}",
        )
        stopSelf()
    }

    private fun notification(name: String): Notification =
        Notification
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.flash_service_title, name))
            .setContentText(getString(R.string.flash_service_text))
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.flash_service_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "$packageName:flash")
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
    }

    companion object {
        private const val CHANNEL_ID = "flash-install"
        private const val EXTRA_NAME = "name"
        private const val NOTIFICATION_ID = 13
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 20 * 60 * 1000L
        private var wakeLock: android.os.PowerManager.WakeLock? = null

        fun start(
            context: Context,
            name: String,
        ) {
            try {
                context.startForegroundService(
                    Intent(context, FlashService::class.java).putExtra(EXTRA_NAME, name),
                )
            } catch (error: SecurityException) {
                reportStartFailure(context, error)
            } catch (error: IllegalStateException) {
                reportStartFailure(context, error)
            } catch (error: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    error is android.app.ForegroundServiceStartNotAllowedException
                ) {
                    reportStartFailure(context, error)
                } else {
                    throw error
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FlashService::class.java))
        }

        private fun reportStartFailure(
            context: Context,
            error: Exception,
        ) {
            (context.applicationContext as? FmSwitchApplication)?.appModel?.value?.device?.appendLog(
                "Couldn't start the service; keep this screen open. ${error.message.orEmpty()}",
            )
        }
    }
}
