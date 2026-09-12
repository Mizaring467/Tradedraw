package com.example.tradedraw

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
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

/**
 * Gestor de captura de pantalla continuo y optimizado con Modo Híbrido:
 * - Fast ROI (100ms / ~10 FPS): Sub-región activa derecha para tracking ultra rápido de punta de vela y tick de precio.
 * - Full Frame (1000ms / ~1 FPS): Fotograma completo para análisis global de soportes, resistencias y patrones técnicos.
 * - Compatibilidad total con Landscape (horizontal) y Portrait (vertical).
 */
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

    @Volatile
    private var isRecreatingDisplay = false

    // Configuración y temporización del Modo Híbrido
    var isHybridModeEnabled: Boolean = true
    var fastRoiIntervalMs: Long = 100L
    var fullFrameIntervalMs: Long = 1000L

    private var lastFastRoiCaptureTime: Long = 0L
    private var lastFullFrameCaptureTime: Long = 0L

    // Buffer directo reutilizable para extracción de ROI sin allocs excesivos en GC
    private var roiDirectBuffer: ByteBuffer? = null
    private var lastAllocatedRoiSize: Int = 0

    // Rect personalizado opcional de ROI
    var customRoiRect: Rect? = null

    var isCapturing = false
        private set

    // Callbacks
    private var onImageCapturedCallback: ((Bitmap) -> Unit)? = null
    private var onFastRoiCapturedCallback: ((Bitmap, Rect) -> Unit)? = null

    // Estados públicos de los últimos frames capturados
    var latestFrame: Bitmap? = null
        private set
    var latestRoiBitmap: Bitmap? = null
        private set
    var latestRoiRect: Rect = Rect()
        private set

    var totalFramesCaptured: Long = 0L
        private set
    var totalFullFramesCaptured: Long = 0L
        private set
    var totalRoiFramesCaptured: Long = 0L
        private set

    init {
        try {
            mediaProjection = projectionManager.getMediaProjection(android.app.Activity.RESULT_OK, intent)
            if (mediaProjection != null) {
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        super.onStop()
                        Log.d("ScreenCaptureManager", "MediaProjection onStop recibido")
                        destroy()
                    }
                }, backgroundHandler)

                setupVirtualDisplay()
            } else {
                Log.e("ScreenCaptureManager", "MediaProjection es nula al inicializar.")
            }
        } catch (e: Exception) {
            Log.e("ScreenCaptureManager", "Error al inicializar MediaProjection", e)
        }
    }

    /**
     * Calcula la Región de Interés (ROI) de la zona activa derecha del gráfico según orientación.
     */
    fun getActiveRoiRect(w: Int = width, h: Int = height): Rect {
        customRoiRect?.let { custom ->
            val left = custom.left.coerceIn(0, (w - 1).coerceAtLeast(0))
            val right = custom.right.coerceIn(left + 1, w.coerceAtLeast(1))
            val top = custom.top.coerceIn(0, (h - 1).coerceAtLeast(0))
            val bottom = custom.bottom.coerceIn(top + 1, h.coerceAtLeast(1))
            return Rect(left, top, right, bottom)
        }

        if (w <= 0 || h <= 0) return Rect(0, 0, 0, 0)

        val isLandscape = w > h
        val roiLeft: Int
        val roiRight: Int
        val roiTop: Int
        val roiBottom: Int

        if (isLandscape) {
            // Horizontal / Landscape (ej. 2400x1080 / 2712x1220):
            // - Zona activa derecha de velas: X 48% a 76% (antes del panel de botones a la derecha)
            // - Rango vertical de velas: Y 18% a 78% (excluye barra superior de pestañas y herramientas inferiores)
            roiLeft = (w * 0.48f).toInt().coerceIn(0, w - 1)
            roiRight = (w * 0.76f).toInt().coerceIn(roiLeft + 1, w)
            roiTop = (h * 0.18f).toInt().coerceIn(0, h - 1)
            roiBottom = (h * 0.78f).toInt().coerceIn(roiTop + 1, h)
        } else {
            // Vertical / Portrait (ej. 1080x2400 / 1220x2712):
            // - Zona activa derecha de velas: X 45% a 80% (últimas velas y formación activa)
            // - Rango vertical de velas: Y 18% a 74%
            roiLeft = (w * 0.45f).toInt().coerceIn(0, w - 1)
            roiRight = (w * 0.80f).toInt().coerceIn(roiLeft + 1, w)
            roiTop = (h * 0.18f).toInt().coerceIn(0, h - 1)
            roiBottom = (h * 0.74f).toInt().coerceIn(roiTop + 1, h)
        }

        return Rect(roiLeft, roiTop, roiRight, roiBottom)
    }

    /**
     * Reconfigura los parámetros del Modo Híbrido.
     */
    fun setHybridMode(enabled: Boolean, fastIntervalMs: Long = 100L, fullIntervalMs: Long = 1000L) {
        this.isHybridModeEnabled = enabled
        this.fastRoiIntervalMs = fastIntervalMs
        this.fullFrameIntervalMs = fullIntervalMs
    }

    fun setFastRoiCallback(callback: ((Bitmap, Rect) -> Unit)?) {
        this.onFastRoiCapturedCallback = callback
    }

    fun refreshVirtualDisplay() {
        backgroundHandler.post {
            isRecreatingDisplay = true
            try {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val metrics = DisplayMetrics()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val bounds = windowManager.currentWindowMetrics.bounds
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

                val rotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    try { context.display?.rotation } catch (e: Exception) { null } ?: windowManager.defaultDisplay.rotation
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay.rotation
                }
                val isLandscapeDisplay = rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_270
                val configOrientation = context.resources.configuration.orientation
                val isLandscapeConfig = (configOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) || isLandscapeDisplay

                if (isLandscapeConfig && width < height) {
                    val temp = width
                    width = height
                    height = temp
                } else if (!isLandscapeConfig && width > height) {
                    val temp = width
                    width = height
                    height = temp
                }

                // Invalidar buffer directo de ROI al rotar/cambiar resolución
                roiDirectBuffer = null
                lastAllocatedRoiSize = 0

                // En Android 14 no se puede invocar MediaProjection#createVirtualDisplay múltiples veces.
                // Usamos resize() y setSurface() con un nuevo ImageReader.
                imageReader?.close()
                @SuppressLint("WrongConstant")
                val newReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
                imageReader = newReader

                if (virtualDisplay != null) {
                    virtualDisplay?.resize(width, height, density)
                    virtualDisplay?.setSurface(newReader.surface)
                } else {
                    val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                    virtualDisplay = mediaProjection?.createVirtualDisplay(
                        "TradeDraw_ScreenCapture",
                        width, height, density,
                        flags,
                        newReader.surface, null, backgroundHandler
                    )
                }

                newReader.setOnImageAvailableListener({ reader ->
                    handleImageFromReader(reader)
                }, backgroundHandler)

                Log.d("ScreenCaptureManager", "VirtualDisplay redimensionado exitosamente ($width x $height)")
            } catch (e: Exception) {
                Log.e("ScreenCaptureManager", "Error refrescando VirtualDisplay", e)
            } finally {
                isRecreatingDisplay = false
            }
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

        if (width <= 0 || height <= 0) {
            width = 1080
            height = 1920
            density = 320
        }

        val rotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try { context.display?.rotation } catch (e: Exception) { null } ?: windowManager.defaultDisplay.rotation
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        val isLandscapeDisplay = rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_270
        val configOrientation = context.resources.configuration.orientation
        val isLandscapeConfig = (configOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) || isLandscapeDisplay

        if (isLandscapeConfig && width < height) {
            val temp = width
            width = height
            height = temp
        } else if (!isLandscapeConfig && width > height) {
            val temp = width
            width = height
            height = temp
        }

        @SuppressLint("WrongConstant")
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)

        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "TradeDraw_ScreenCapture",
            width, height, density,
            flags,
            imageReader?.surface, null, backgroundHandler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            handleImageFromReader(reader)
        }, backgroundHandler)
    }

    private fun handleImageFromReader(reader: ImageReader) {
        val image = try { reader.acquireLatestImage() } catch (e: Exception) { null } ?: return
        try {
            if (isRecreatingDisplay) return

            val targetW = width
            val targetH = height
            // Descartar frames con dimensiones discrepantes durante rotación
            if (image.width != targetW || image.height != targetH) return

            val currentTime = System.currentTimeMillis()
            val isFullFrameDue = (currentTime - lastFullFrameCaptureTime) >= fullFrameIntervalMs
            val isFastRoiDue = isHybridModeEnabled && (currentTime - lastFastRoiCaptureTime) >= fastRoiIntervalMs

            // Si aún no toca ningún intervalo de muestreo, descartar inmediatamente
            if (!isFullFrameDue && !isFastRoiDue) return

            val planes = image.planes
            if (planes.isEmpty()) return

            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride

            // 1. Procesamiento FULL FRAME (1000ms): análisis global del gráfico
            if (isFullFrameDue) {
                lastFullFrameCaptureTime = currentTime
                lastFastRoiCaptureTime = currentTime // Sincronizar para no duplicar ROI en el mismo ciclo

                val rowPadding = rowStride - pixelStride * targetW
                val fullRawBitmap = Bitmap.createBitmap(targetW + rowPadding / pixelStride, targetH, Bitmap.Config.ARGB_8888)
                fullRawBitmap.copyPixelsFromBuffer(buffer)

                val croppedBitmap = Bitmap.createBitmap(fullRawBitmap, 0, 0, targetW, targetH)
                latestFrame = croppedBitmap
                totalFullFramesCaptured++
                totalFramesCaptured++

                // Extraer también el ROI del frame completo para mantener latestRoiBitmap al día
                val roiRect = getActiveRoiRect(targetW, targetH)
                if (roiRect.width() > 0 && roiRect.height() > 0) {
                    val roiBmp = Bitmap.createBitmap(croppedBitmap, roiRect.left, roiRect.top, roiRect.width(), roiRect.height())
                    latestRoiBitmap = roiBmp
                    latestRoiRect = roiRect

                    if (isCapturing && onFastRoiCapturedCallback != null) {
                        mainHandler.post {
                            onFastRoiCapturedCallback?.invoke(roiBmp, roiRect)
                        }
                    }
                }

                if (isCapturing && onImageCapturedCallback != null) {
                    mainHandler.post {
                        onImageCapturedCallback?.invoke(croppedBitmap)
                    }
                }
            }
            // 2. Procesamiento FAST ROI (100ms): sub-región activa derecha para punta de vela / tick rápido
            else if (isFastRoiDue) {
                lastFastRoiCaptureTime = currentTime

                val roiRect = getActiveRoiRect(targetW, targetH)
                val roiW = roiRect.width()
                val roiH = roiRect.height()

                if (roiW > 0 && roiH > 0 && pixelStride == 4) {
                    val requiredSize = roiW * roiH * 4
                    val roiBuf: ByteBuffer
                    if (roiDirectBuffer == null || lastAllocatedRoiSize != requiredSize) {
                        val newBuf = ByteBuffer.allocateDirect(requiredSize)
                        roiDirectBuffer = newBuf
                        lastAllocatedRoiSize = requiredSize
                        roiBuf = newBuf
                    } else {
                        roiBuf = roiDirectBuffer!!
                        roiBuf.clear()
                    }

                    val rowBytesToCopy = roiW * 4
                    val roiLeft = roiRect.left
                    val roiTop = roiRect.top
                    val roiBottom = roiRect.bottom

                    for (row in roiTop until roiBottom) {
                        val rowOffset = row * rowStride + roiLeft * pixelStride
                        buffer.position(rowOffset)
                        val oldLimit = buffer.limit()
                        buffer.limit((rowOffset + rowBytesToCopy).coerceAtMost(oldLimit))
                        roiBuf.put(buffer)
                        buffer.limit(oldLimit)
                    }

                    roiBuf.rewind()
                    val roiBitmap = Bitmap.createBitmap(roiW, roiH, Bitmap.Config.ARGB_8888)
                    roiBitmap.copyPixelsFromBuffer(roiBuf)

                    latestRoiBitmap = roiBitmap
                    latestRoiRect = roiRect
                    totalRoiFramesCaptured++
                    totalFramesCaptured++

                    if (isCapturing && onFastRoiCapturedCallback != null) {
                        mainHandler.post {
                            onFastRoiCapturedCallback?.invoke(roiBitmap, roiRect)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenCaptureManager", "Error procesando frame", e)
        } finally {
            try { image.close() } catch (e: Exception) {}
        }
    }

    /**
     * Inicia captura estándar (Full Frame).
     */
    fun startCapture(onImageCaptured: (Bitmap) -> Unit) {
        onImageCapturedCallback = onImageCaptured
        isCapturing = true
        latestFrame?.let { frame ->
            mainHandler.post { onImageCaptured(frame) }
        }
    }

    /**
     * Inicia captura en Modo Híbrido con callbacks dedicados para Full Frame (1000ms) y Fast ROI (100ms).
     */
    fun startHybridCapture(
        onFullFrame: (Bitmap) -> Unit,
        onFastRoi: ((Bitmap, Rect) -> Unit)? = null
    ) {
        this.onImageCapturedCallback = onFullFrame
        this.onFastRoiCapturedCallback = onFastRoi
        this.isCapturing = true

        latestFrame?.let { frame ->
            mainHandler.post { onFullFrame(frame) }
        }
        if (onFastRoi != null) {
            latestRoiBitmap?.let { roiBmp ->
                mainHandler.post { onFastRoi(roiBmp, latestRoiRect) }
            }
        }
    }

    fun stopCapture() {
        isCapturing = false
        onImageCapturedCallback = null
        onFastRoiCapturedCallback = null
    }

    fun destroy() {
        isCapturing = false
        onImageCapturedCallback = null
        onFastRoiCapturedCallback = null
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
        roiDirectBuffer = null
    }
}

