package com.example.tradedraw

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.nio.ByteBuffer

class ScreenCaptureManager(private val context: Context, private val intent: Intent) {

    private val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var width: Int = 0
    private var height: Int = 0
    private var density: Int = 0

    private val backgroundThread = HandlerThread("ScreenCaptureThread").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    var isCapturing = false
        private set
    private var onImageCapturedCallback: ((Bitmap) -> Unit)? = null
    var latestFrame: Bitmap? = null
        private set
    var totalFramesCaptured: Long = 0L
        private set

    init {
        try {
            mediaProjection = projectionManager.getMediaProjection(android.app.Activity.RESULT_OK, intent)
            if (mediaProjection != null) {
                setupVirtualDisplay()
            } else {
                Log.e("ScreenCaptureManager", "MediaProjection es nula al inicializar.")
            }
        } catch (e: Exception) {
            Log.e("ScreenCaptureManager", "Error al inicializar MediaProjection", e)
        }
    }

    private fun setupVirtualDisplay() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
            density = context.resources.displayMetrics.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            width = metrics.widthPixels
            height = metrics.heightPixels
            density = metrics.densityDpi
        }

        @SuppressLint("WrongConstant")
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "TradeDraw_ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, backgroundHandler
        )

        var lastCaptureTime = 0L
        val CAPTURE_INTERVAL_MS = 1000L

        imageReader?.setOnImageAvailableListener({ reader ->
            val image: Image? = try { reader.acquireLatestImage() } catch (e: Exception) { null }

            if (image == null) return@setOnImageAvailableListener

            try {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastCaptureTime >= CAPTURE_INTERVAL_MS) {
                    lastCaptureTime = currentTime

                    val planes = image.planes
                    val buffer: ByteBuffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)

                    val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    latestFrame = croppedBitmap
                    totalFramesCaptured++

                    if (isCapturing && onImageCapturedCallback != null) {
                        mainHandler.post {
                            onImageCapturedCallback?.invoke(croppedBitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ScreenCaptureManager", "Error procesando frame", e)
            } finally {
                image.close()
            }
        }, backgroundHandler)
    }

    fun startCapture(onImageCaptured: (Bitmap) -> Unit) {
        onImageCapturedCallback = onImageCaptured
        isCapturing = true
    }

    fun stopCapture() {
        isCapturing = false
        onImageCapturedCallback = null
    }

    fun destroy() {
        isCapturing = false
        onImageCapturedCallback = null
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
            backgroundThread.quitSafely()
        } catch (e: Exception) {
            Log.e("ScreenCaptureManager", "Error destruyendo recursos", e)
        }
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }
}
