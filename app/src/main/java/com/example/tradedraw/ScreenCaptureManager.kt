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

    private val handler = Handler(Looper.getMainLooper())
    private var isCapturing = false

    fun startCapture(onImageCaptured: (Bitmap) -> Unit) {
        if (isCapturing) return

        mediaProjection = projectionManager.getMediaProjection(android.app.Activity.RESULT_OK, intent)
        if (mediaProjection == null) {
            Log.e("ScreenCaptureManager", "MediaProjection is null")
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi

        // Creamos el ImageReader. RGBA_8888 funciona mejor para MediaProjection.
        @SuppressLint("WrongConstant")
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        isCapturing = true

        var lastCaptureTime = 0L
        val CAPTURE_INTERVAL_MS = 1000L // Capturar y convertir 1 frame por segundo para no agotar la memoria

        imageReader?.setOnImageAvailableListener({ reader ->
            if (!isCapturing) {
                // Hay que consumir la imagen para que no se tranque la cola
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }

            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image != null) {
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

                        // Extraer solo la imagen real (sin el padding extra de la memoria)
                        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)

                        onImageCaptured(croppedBitmap)
                    }
                }
            } catch (e: Exception) {
                Log.e("ScreenCaptureManager", "Error capturando pantalla", e)
            } finally {
                image?.close()
            }
        }, handler)
    }

    fun stopCapture() {
        isCapturing = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()

        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }
}
