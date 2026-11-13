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
 * SEÑAL ELEGIDA (y por qué es suficiente). [BrokerDetector.currentPackageName] se alimenta
 * EXCLUSIVAMENTE de eventos reales `typeWindowStateChanged` del sistema
 * (AutoTradeAccessibilityService.onAccessibilityEvent, L502–507): Android solo emite ese
 * evento cuando existe una ventana en primer plano. La cadena
 * `servicio de accesibilidad activo → window-event → nombre de paquete` es la única vía
 * por la que ese campo puede valer un paquete de broker, y por construcción NO puede
 * reportar un broker cuya ventana no existió recientemente. Además es la MISMA señal que
 * ya producen las lecturas de saldo/activo y el guard DEMO preexistente: no introduce una
 * segunda fuente de verdad. Se descartaron APIs de enumeración de procesos/ventanas
 * (ActivityManager.getRunningTasks/getRunningAppProcesses, UsageStats): deprecated,
 * incompletas desde API 24+ y nunca usadas en este repo.
 *
 * FAIL-CLOSED. La frescura (ventana del broker vista hace ≤ [maxAgeMs], default 120 s,
 * alineado con el horizonte de StandardDemoAccountGuard) convierte la propiedad anterior
 * en un veredicto: sin ventana de broker reciente ⇒ ausente ⇒ veto. Un falso negativo
 * (veto con broker vivo pero pantalla estable) cuesta una operación perdida; un falso
 * positivo (despacho a broker muerto) cuesta un ciclo fantasma: se prioriza lo primero.
 *
 * NO toca la cascada congelada: este sensor solo lo consume la COMPOSICIÓN Android
 * (AndroidOrderComposition / BrokerPresenceGateTransport), y ni este archivo ni los que
 * lo consumen forman parte de loops/r29c/strategy_registry_v3.json.
 */
class BrokerProcessPresenceSensor(
    /** Fuente del último paquete en primer plano reportado por accesibilidad. */
    private val packageNameSupplier: () -> String = { BrokerDetector.currentPackageName },
    /** Momento (epoch ms) de la última actualización de ese paquete. */
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

        /** Instancia de producción: lee exclusivamente de [BrokerDetector]. */
        val fromDetector = BrokerProcessPresenceSensor()
    }
}
