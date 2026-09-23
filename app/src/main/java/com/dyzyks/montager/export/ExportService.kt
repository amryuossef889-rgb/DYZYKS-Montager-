package com.dyzyks.montager.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dyzyks.montager.MainActivity
import com.dyzyks.montager.media.TimelineRenderPlan
import com.dyzyks.montager.model.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class ExportService : Service() {

    companion object {
        private const val TAG = "ExportService"
        private const val NOTIFICATION_ID = 8801
        private const val CHANNEL_ID = "export_channel"

        const val ACTION_START_EXPORT = "com.dyzyks.montager.START_EXPORT"
        const val ACTION_CANCEL_EXPORT = "com.dyzyks.montager.CANCEL_EXPORT"

        var activePlan: TimelineRenderPlan? = null
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var exportJob: Job? = null
    private var isCancelledFlag = false

    private val _progressState = MutableStateFlow(0.0f)
    val progressState: StateFlow<Float> = _progressState.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): ExportService = this@ExportService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_EXPORT -> {
                cancelExport()
            }
            ACTION_START_EXPORT -> {
                val plan = activePlan
                if (plan != null) {
                    startExport(plan)
                }
            }
        }
        return START_NOT_STICKY
    }

    fun startExport(plan: TimelineRenderPlan) {
        if (_isExporting.value) return
        _isExporting.value = true
        isCancelledFlag = false
        _progressState.value = 0.0f

        startForeground(NOTIFICATION_ID, buildNotification(0))

        exportJob = serviceScope.launch {
            val outputDir = File(cacheDir, "exports").apply { mkdirs() }
            val tempFile = File(outputDir, "export_${System.currentTimeMillis()}.mp4")

            val config = ExportConfig(
                width = plan.project.width,
                height = plan.project.height,
                fps = plan.project.fps,
                outputFile = tempFile
            )

            val engine = ExportEngine(
                renderPlan = plan,
                config = config,
                onProgress = { prog ->
                    _progressState.value = prog
                    updateNotification((prog * 100).toInt())
                },
                isCancelled = { isCancelledFlag }
            )

            val success = engine.export()

            if (success && !isCancelledFlag) {
                // Insert into MediaStore Movies/DYZYKS (Rule #8)
                val uri = ExportMediaStoreHelper.insertIntoMediaStore(
                    context = applicationContext,
                    sourceFile = tempFile,
                    title = plan.project.name
                )
                Log.i(TAG, "Export saved to MediaStore: $uri")
                showCompletionNotification(success = true)
            } else {
                Log.w(TAG, "Export failed or cancelled (isCancelled=$isCancelledFlag)")
                showCompletionNotification(success = false)
            }

            _isExporting.value = false
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    fun cancelExport() {
        isCancelledFlag = true
        exportJob?.cancel()
        _isExporting.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Montage Export",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of DYZYKS Montager video exports"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(progressPercent: Int): android.app.Notification {
        val cancelIntent = Intent(this, ExportService::class.java).apply {
            action = ACTION_CANCEL_EXPORT
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Exporting Montage...")
            .setContentText("$progressPercent% complete")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setProgress(100, progressPercent, false)
            .setOngoing(true)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun updateNotification(progressPercent: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(progressPercent))
    }

    private fun showCompletionNotification(success: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (success) "Export Complete!" else "Export Cancelled")
            .setContentText(if (success) "Saved to Movies/DYZYKS" else "Export was not completed.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID + 1, notif)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
