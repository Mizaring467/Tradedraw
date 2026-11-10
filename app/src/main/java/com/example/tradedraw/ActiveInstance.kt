package com.example.tradedraw

import java.util.concurrent.atomic.AtomicReference

/**
 * Referencia a una única instancia "activa" de un componente con ciclo de vida.
 *
 * R27-H / Loop 1: `AutoTradeAccessibilityService.instance` era un `var` compartido que se
 * asignaba en `onServiceConnected` y en `onRebind`, pero **no** se liberaba en `onUnbind`. Entre
 * `onUnbind` (que devuelve `true` pidiendo rebind) y `onRebind` el servicio está desenlazado y la
 * referencia seguía apuntando a un objeto que ya no recibe eventos del sistema. Otros 25 puntos
 * del código la usan para despachar gestos y leer el árbol de accesibilidad.
 *
 * R27-H / cierre de loops: además de correcta en secuencia, la clase tiene que ser correcta en
 * concurrencia. `onUnbind`/`onRebind`/`onDestroy` llegan en el hilo principal, pero **quien lee**
 * (`current`) lo hace desde hilos de trabajo (`HandlerThread` de captura, pool del bridge HTTP,
 * callbacks del WebSocket). Con un `@Volatile var` la publicación se veía, pero la secuencia
 * "comparar identidad y luego asignar null" era **dos operaciones**: entre la comparación y la
 * escritura otra hebra podía promover una instancia nueva y la escritura la borraba. Ese es un
 * lost update, no una simple visibilidad.
 *
 * La regla que hay que garantizar es una sola:
 *
 *   **una instancia antigua NUNCA puede liberar la referencia de una instancia más reciente.**
 *
 * Sin esa regla, la secuencia `crear(A) → crear(B) → destruir(A)` dejaría el sistema sin
 * referencia a B, que es el que está vivo.
 *
 * Por eso la comparación y la liberación son una **única operación atómica**
 * ([AtomicReference.compareAndSet]): el chequeo de identidad y la puesta a `null` ocurren en el
 * mismo paso linealizable, así que ninguna promoción concurrente puede colarse entre ambos. Se
 * compara por identidad (`===`), no por igualdad: dos servicios distintos con el mismo estado no
 * son la misma instancia.
 *
 * Clase genérica y sin dependencias de Android a propósito: así el invariante se puede demostrar
 * con pruebas JVM, que es exactamente lo que el ciclo de vida real no permite (reproducir
 * `onUnbind`/`onRebind` en el dispositivo exige desenlazar el servicio, algo fuera de alcance de
 * estas restricciones).
 *
 * @param T tipo de la instancia. Debe ser una referencia (no primitivo).
 */
internal class ActiveInstance<T : Any> {

    private val ref = AtomicReference<T?>(null)

    /** Instancia activa actual, o `null` si no hay ninguna. */
    val current: T?
        get() = ref.get()

    /**
     * Marca [service] como la instancia activa.
     *
     * Idempotente: promover la misma instancia dos veces no cambia nada. Promover una distinta
     * sustituye la anterior, que deja de ser alcanzable a través de este objeto.
     *
     * Es una publicación atómica: cualquier lector ve o la instancia anterior o la nueva, nunca un
     * estado intermedio.
     */
    fun promote(service: T) {
        ref.set(service)
    }

    /**
     * Libera la referencia **solo si** sigue siendo exactamente [service].
     *
     * Ésta es la guarda contra carreras: un `onDestroy` tardío de una instancia vieja no puede
     * borrar la instancia nueva que ya se promovió. La comparación por identidad y la liberación
     * forman una sola operación atómica (`compareAndSet`), de modo que no existe ventana entre
     * ambas.
     *
     * El bucle es obligatorio: si otra hebra cambia la referencia entre el `get()` y el
     * `compareAndSet`, el CAS falla y se vuelve a evaluar contra el valor nuevo en lugar de
     * escribir a ciegas.
     *
     * @return `true` si esta llamada efectivamente liberó la referencia.
     */
    fun release(service: T): Boolean {
        while (true) {
            val observed = ref.get()
            // Identidad, no igualdad: solo el dueño exacto puede liberar.
            if (observed !== service) return false
            if (ref.compareAndSet(observed, null)) return true
            // Otro hilo modificó la referencia entre get() y CAS: reintentar con el valor nuevo.
        }
    }
}
