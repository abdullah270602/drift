package com.abdullahnaseem.drift

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.abdullahnaseem.drift.Engine as Rotator

/**
 * Drift as a live wallpaper. Its only job beyond drawing a still photo: hear the screen turn off
 * (which ordinary apps can't without a foreground service) and move "each wake" collections on,
 * so a new photo is already waiting when the phone wakes. It draws only when the photo changes.
 */
class DriftWallpaper : WallpaperService() {

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val pending = goAsync()
            Thread {
                try { Rotator.run(applicationContext, wake = true) } catch (t: Throwable) { Log.e("Drift", "Wake run failed", t) }
                finally { pending.finish() }
            }.start()
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        super.onDestroy()
    }

    override fun onCreateEngine(): WallpaperService.Engine = Painter()

    private inner class Painter : WallpaperService.Engine() {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private var bitmap: Bitmap? = null
        private var key: String? = null
        private val redraw: () -> Unit = { draw() }

        /** A lock-only engine shows the lock photo; home (or a shared engine) shows the home photo. */
        private val surface get() =
            if (Build.VERSION.SDK_INT >= 34 && wallpaperFlags == WallpaperManager.FLAG_LOCK) Surface.LOCK else Surface.HOME

        override fun onCreate(holder: SurfaceHolder) {
            Rotator.listeners += redraw
            // Becoming the wallpaper may make a surface due (e.g. the first one), so catch up once.
            if (!isPreview) Thread { runCatching { Rotator.run(applicationContext) } }.start()
        }

        override fun onDestroy() {
            Rotator.listeners -= redraw
            bitmap?.recycle(); bitmap = null
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = draw()
        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) = draw()
        override fun onWallpaperFlagsChanged(which: Int) = draw()
        override fun onVisibilityChanged(visible: Boolean) { if (visible) draw() }

        override fun onComputeColors(): WallpaperColors? = bitmap?.let { WallpaperColors.fromBitmap(it) }

        @Synchronized
        private fun draw() {
            val frame = surfaceHolder.surfaceFrame
            if (frame.isEmpty) return
            val photo = Store.load(applicationContext).shown[surface]?.photo
            val k = "$photo|${frame.width()}x${frame.height()}"
            if (k != key) {
                val next = photo?.let { runCatching { Rotator.decode(applicationContext, Uri.parse(it), frame.width(), frame.height()) }.getOrNull() }
                bitmap?.recycle()
                bitmap = next; key = k
                notifyColorsChanged() // lets Material You follow the photo
            }
            val canvas = runCatching { surfaceHolder.lockHardwareCanvas() }.getOrNull() ?: return
            try {
                canvas.drawColor(Color.BLACK)
                bitmap?.let { canvas.drawBitmap(it, null, Rect(0, 0, frame.width(), frame.height()), paint) }
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
