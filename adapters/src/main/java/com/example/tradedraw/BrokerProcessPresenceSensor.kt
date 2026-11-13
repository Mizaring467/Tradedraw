package com.example.tradedraw

/**
 * Ronda 2 (E→F) — sensor de PRESENCIA del proceso/ventana del broker.
 *
 * HUECO QUE CIERRA. La cascada congelada (ExecutionPolicy, 13 vetos) y los guards del
 * gateway validan el FEED (WS nativo de TradeDraw, corre en ESTE proceso) y la cuenta
 * DEMO, pero nada valida que la app broker que recibirá el tap SIGA viva. Repro real
 * 2026-11-13 04:30–04:35 UTC: motor AUTONOMOUS con `com.marketly.trading` muerto (arrancó
 * recién ~05:05) → a las 04:34:01 se inyectó un gesto GESTURE a nada → ciclo fantasma
 * «ORDEN NO PROCESADA (Diff=0.0 → saldo inalterado tras 75s)».
 *
 * SEÑAL (dual, por orden de autoridad — revisión de la ronda de verificación):
 *
 * 1. VIVA, en el instante del despacho: [activeWindowPackageSupplier] (producción:
 *    `AutoTradeAccessibilityService.instance?.rootInActiveWindow?.packageName`, la MISMA
 *    API que ya usa `readCurrentBalance`, AutoTradeAccessibilityService.kt:695). Si hay
 *    ventana activa legible: paquete de broker ⇒ PRESENTE; paquete no-broker ⇒ AUSENTE.
 *    ESTA señal es la autoridad porque describe la ventana que recibiría el tap AHORA.
 *    Es imprescindible: el servicio solo suscribe `typeWindowStateChanged`
 *    (accessibility_service_config.xml:4) y [BrokerDetector.lastPackageChangeAtMs] solo se
 *    estampa en CAMBIO de paquete — un broker estable en primer plano (la noche normal)
 *    no genera cambios de paquete, así que un sensor de solo-eventos vetaría órdenes
 *    válidas tras 120 s de pantalla quieta. (Defecto corregido aquí; ver checkpoint.)
 * 2. STREAM de eventos de ventana (fallback SOLO si la señal viva no está disponible:
 *    servicio no instanciado, sin ventana activa legible): [packageNameSupplier] +
 *    [lastChangedAtSupplier] + frescura ≤ [maxAgeMs]. Se mantiene fail-closed: sin señal
 *    viva y sin evento de ventana fresco ⇒ ausente.
 *
 * POR QUÉ EL STREAM SIGUE SIENDO SUFICIENTE COMO FALLBACK: Android solo emite
 * `typeWindowStateChanged` cuando existe una ventana en primer plano; la cadena
 * `servicio activo → window-event → paquete` no puede reportar un broker cuya ventana no
 * existió recientemente. Es además la MISMA fuente que ya producen las lecturas de
 * saldo/activo y el guard DEMO preexistente (una sola fuente de verdad). Se descartaron
 * APIs de enumeración de procesos/ventanas (ActivityManager.getRunningTasks,
 * getRunningAppProcesses, UsageStats): deprecated, incompletas desde API 24+ y nunca usadas
 * en este repo.
 *
 * FAIL-CLOSED. Un falso negativo (veto con broker vivo) cuesta una operación perdida; un
 * falso positivo (despacho a broker muerto) cuesta un ciclo fantasma: se prioriza lo
 * primero. El caso «broker relanzado en background sin ventana» sigue vetando (correcto:
 * el tap no tiene UI de broker que procesarlo).
 *
 * NOTA FORENSE 04:34:01 (ver checkpoint): el guard DEMO preexistente ya exigía una
 * observación de ventana de broker fresca (≤120 s) para autorizar el gesto — por eso el
 * gesto de las 04:34 es en sí evidencia de que una ventana de broker fue vista poco antes.
 * El hueco real era de INSTANTE (ventana viva al momento del tap, no «vista hace poco»);
 * este sensor lo cierra con la señal viva (1).
 *
 * NO toca la cascada congelada: este sensor solo lo consume la COMPOSICIÓN Android
 * (AndroidOrderComposition / BrokerPresenceGateTransport), y ni este archivo ni los que
 * lo consumen forman parte de loops/r29c/strategy_registry_v3.json.
 */
class BrokerProcessPresenceSensor(
    /** Ventana activa leída EN EL INSTANTE del despacho (null/blank = señal viva no disponible). */
    private val activeWindowPackageSupplier: () -> String? = DEFAULT_ACTIVE_WINDOW_SUPPLIER,
    /** Fuente del último paquete en primer plano reportado por accesibilidad (fallback). */
    private val packageNameSupplier: () -> String = { BrokerDetector.currentPackageName },
    /** Momento (epoch ms) de la última actualización de ese paquete (fallback). */
    private val lastChangedAtSupplier: () -> Long = { BrokerDetector.lastPackageChangeAtMs },
    /** Clasificador paquete→broker; UNKNOWN ⇒ la ventana no es de un broker soportado. */
    private val brokerClassifier: (String) -> Broker = { BrokerDetector.currentBrokerFor(it) },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS
) {

    /** Veredicto de presencia con el contexto mínimo para telemetría. */
    data class PresenceSnapshot(
        val pkg: String,
        val ageMs: Long,
        val fresh: Boolean,
        val reason: String
    )

    fun isBrokerWindowPresent(): Boolean = snapshot().fresh

    fun snapshot(): PresenceSnapshot {
        // Autoridad 1: la ventana activa EN ESTE INSTANTE decide (presente o ausente).
        // Solo si esa lectura no está disponible se cae al stream de eventos (fail-closed).
        val livePkg = try { activeWindowPackageSupplier() } catch (_: Throwable) { null }
        if (!livePkg.isNullOrBlank()) {
            return if (brokerClassifier(livePkg) != Broker.UNKNOWN) {
                PresenceSnapshot(livePkg, 0L, true, REASON_WINDOW_ACTIVE)
            } else {
                PresenceSnapshot(livePkg, 0L, false, "FOREGROUND_NOT_BROKER")
            }
        }
        // Autoridad 2 (fallback): stream de eventos de ventana con frescura.
        val now = clock()
        val pkg = packageNameSupplier()
        if (pkg.isBlank()) {
            return PresenceSnapshot(pkg, Long.MAX_VALUE, false, "NO_WINDOW_EVENT_YET")
        }
        if (brokerClassifier(pkg) == Broker.UNKNOWN) {
            return PresenceSnapshot(pkg, Long.MAX_VALUE, false, "FOREGROUND_NOT_BROKER")
        }
        val age = now - lastChangedAtSupplier()
        if (age !in 0..maxAgeMs) {
            return PresenceSnapshot(pkg, age, false, "WINDOW_EVENT_STALE")
        }
        return PresenceSnapshot(pkg, age, true, "BROKER_WINDOW_FRESH")
    }

    companion object {
        /** Mismo horizonte que la frescura DEMO de [StandardDemoAccountGuard] (120 s). */
        const val DEFAULT_MAX_AGE_MS = 120_000L

        /** Veredicto de presencia por ventana activa leída en el instante del despacho. */
        const val REASON_WINDOW_ACTIVE = "BROKER_WINDOW_ACTIVE"

        /**
         * Señal viva de producción: ventana activa del servicio de accesibilidad.
         * `null` en JVM tests (sin instancia) y ante cualquier fallo del binder —
         * el sensor entonces cae al stream de eventos, que sigue siendo fail-closed.
         */
        private val DEFAULT_ACTIVE_WINDOW_SUPPLIER: () -> String? = {
            try {
                AutoTradeAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString()
            } catch (_: Throwable) {
                null
            }
        }

        /** Instancia de producción: lee exclusivamente de [BrokerDetector] + ventana viva. */
        val fromDetector = BrokerProcessPresenceSensor()
    }
}
