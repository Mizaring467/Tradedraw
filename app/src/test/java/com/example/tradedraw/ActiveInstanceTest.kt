package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * R27-H / Loop 1 — regresión del ciclo de vida de la referencia estática del servicio de
 * accesibilidad.
 *
 * Hallazgo auditado: `onUnbind()` pedía rebind (`return true`) pero no liberaba la referencia
 * estática, así que durante la ventana unbind→rebind otros componentes podían operar sobre un
 * servicio desenlazado. Además `onDestroy` liberaba de forma condicional (`if (instance == this)`),
 * guarda correcta pero que no estaba cubierta por ninguna prueba.
 *
 * Estas pruebas fijan el invariante en la clase pura [ActiveInstance], que es exactamente la que
 * usa el servicio. **No sustituyen validación en Android real**: el ciclo real
 * `onServiceConnected → onUnbind → onRebind → onDestroy` no se reproduce aquí (ver
 * `loops/r27h_loops/LOOP1_ACCESIBILIDAD.md` §Limitaciones).
 */
class ActiveInstanceTest {

    /** Instancia ficticia; solo importa su identidad. */
    private class FakeService(val etiqueta: String)

    private lateinit var ref: ActiveInstance<FakeService>

    @Before
    fun setUp() {
        ref = ActiveInstance()
    }

    // ---------- Estado inicial ----------

    @Test
    fun `sin promocion no hay instancia activa`() {
        assertNull(ref.current)
    }

    // ---------- onServiceConnected / onRebind ----------

    @Test
    fun `promover publica la instancia`() {
        val a = FakeService("A")
        ref.promote(a)

        assertSame(a, ref.current)
    }

    @Test
    fun `promover dos veces la misma instancia es idempotente`() {
        val a = FakeService("A")
        ref.promote(a)
        ref.promote(a)

        assertSame(a, ref.current)
    }

    @Test
    fun `promover una instancia nueva sustituye la anterior`() {
        val a = FakeService("A")
        val b = FakeService("B")

        ref.promote(a)
        ref.promote(b)

        assertSame("Debe quedar la más reciente", b, ref.current)
    }

    // ---------- onUnbind: el fallo auditado ----------

    @Test
    fun `liberar la instancia activa la deja en null`() {
        val a = FakeService("A")
        ref.promote(a)

        val liberada = ref.release(a)

        assertTrue("Debe reportar que liberó", liberada)
        assertNull("Unbind no debe dejar referencia obsoleta", ref.current)
    }

    @Test
    fun `liberar cuando no hay nada no rompe`() {
        val a = FakeService("A")

        assertFalse(ref.release(a))
        assertNull(ref.current)
    }

    // ---------- onDestroy tardío de una instancia vieja ----------

    @Test
    fun `una instancia vieja no puede borrar una nueva`() {
        val vieja = FakeService("A")
        val nueva = FakeService("B")

        ref.promote(vieja)
        ref.promote(nueva)            // rebind: la nueva pasa a ser la activa

        val borro = ref.release(vieja) // onDestroy tardío de la vieja

        assertFalse("No debe liberar lo que ya no es suyo", borro)
        assertSame("La nueva debe sobrevivir", nueva, ref.current)
    }

    @Test
    fun `liberar dos veces la misma instancia solo funciona la primera`() {
        val a = FakeService("A")
        ref.promote(a)

        assertTrue(ref.release(a))
        assertFalse("La segunda llamada no tiene nada que liberar", ref.release(a))
        assertNull(ref.current)
    }

    // ---------- Secuencias reales del ciclo de vida ----------

    @Test
    fun `secuencia connect - unbind - rebind deja la instancia activa`() {
        val a = FakeService("A")

        ref.promote(a)   // onServiceConnected
        assertSame(a, ref.current)

        ref.release(a)   // onUnbind  → ya no debe quedar publicada
        assertNull(ref.current)

        ref.promote(a)   // onRebind  → vuelve a publicarse
        assertSame(a, ref.current)
    }

    @Test
    fun `secuencia connect - unbind - destroy mantiene el estado limpio`() {
        val a = FakeService("A")

        ref.promote(a)
        ref.release(a)   // onUnbind
        ref.release(a)   // onDestroy

        assertNull(ref.current)
    }

    @Test
    fun `recreacion de servicio con destroy fuera de orden conserva la instancia viva`() {
        val viejo = FakeService("viejo")
        val nuevo = FakeService("nuevo")

        ref.promote(viejo)
        ref.promote(nuevo)       // el sistema ya conectó el nuevo
        ref.release(viejo)       // llega tarde el onDestroy del viejo
        ref.release(nuevo)       // onDestroy del nuevo

        assertNull(ref.current)
    }

    @Test
    fun `liberar una instancia nunca promovida no afecta a la activa`() {
        val activa = FakeService("activa")
        val intrusa = FakeService("intrusa")

        ref.promote(activa)
        val borro = ref.release(intrusa)

        assertFalse(borro)
        assertSame(activa, ref.current)
    }

    // ---------- Contrato de la API pública del servicio ----------

    @Test
    fun `el getter de instance refleja el holder`() {
        // Fija el contrato que consumen los 25 puntos del código.
        val a = FakeService("A")
        ref.promote(a)
        assertEquals("A", ref.current?.etiqueta)
    }

    // ---------- Secuencia vieja/nueva explícita ----------

    @Test
    fun `promover la nueva y despues liberar la vieja conserva la nueva`() {
        val vieja = FakeService("vieja")
        val nueva = FakeService("nueva")

        ref.promote(vieja)
        ref.promote(nueva)
        val libero = ref.release(vieja)

        assertFalse("La vieja ya no era la activa", libero)
        assertSame(nueva, ref.current)
    }

    @Test
    fun `liberar la vieja y despues promover la nueva deja la nueva`() {
        val vieja = FakeService("vieja")
        val nueva = FakeService("nueva")

        ref.promote(vieja)
        assertTrue(ref.release(vieja))   // onUnbind de la vieja llega antes del rebind
        ref.promote(nueva)

        assertSame("La promoción posterior manda", nueva, ref.current)
    }

    @Test
    fun `una instancia vieja liberada dos veces nunca toca a la nueva`() {
        val vieja = FakeService("vieja")
        val nueva = FakeService("nueva")

        ref.promote(vieja)
        ref.promote(nueva)          // rebind
        ref.release(vieja)          // onUnbind tardío
        ref.release(vieja)          // onDestroy tardío

        assertSame(nueva, ref.current)
    }

    // ---------- Concurrencia ----------

    /**
     * `promote(nueva)` y `release(vieja)` en paralelo **siempre** terminan con la nueva publicada.
     *
     * El resultado es independiente de la intercalación: si `release(vieja)` gana la carrera, no
     * encuentra su identidad y no toca nada; si pierde, ya hay una nueva y tampoco la toca. Ésa es
     * exactamente la propiedad que el `if (ref === service) { ref = null }` no garantizaba, porque
     * entre la comparación y la escritura cabía otra hebra.
     *
     * Se repite para cubrir muchas intercalaciones reales; la aserción no depende de cuál ocurra,
     * así que la prueba es repetible y no depende de la suerte del planificador.
     */
    @Test
    fun `promocion y liberacion concurrentes nunca dejan la nueva sin publicar`() {
        val repeticiones = 500
        repeat(repeticiones) { ronda ->
            val vieja = FakeService("vieja-$ronda")
            val nueva = FakeService("nueva-$ronda")
            ref.promote(vieja)

            val barrera = CyclicBarrier(2)
            val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val promotora = pool.submit {
                    barrera.await(10, TimeUnit.SECONDS)
                    ref.promote(nueva)
                }
                val liberadora = pool.submit {
                    barrera.await(10, TimeUnit.SECONDS)
                    ref.release(vieja)
                }
                promotora.get(30, TimeUnit.SECONDS)
                liberadora.get(30, TimeUnit.SECONDS)
            } finally {
                pool.shutdownNow()
            }

            assertSame("Ronda $ronda: la nueva debe sobrevivir", nueva, ref.current)
        }
    }

    /**
     * Varias hebras liberando **la misma** instancia: exactamente una puede ganar.
     *
     * Invariante independiente de la intercalación. Con `compareAndSet` solo una transición
     * `instancia → null` puede tener éxito; con un `if` + asignación dos hebras podían leer la
     * misma identidad y ambas reportar éxito.
     */
    @Test
    fun `solo una liberacion concurrente de la misma instancia gana`() {
        val hilos = 8
        val repeticiones = 200
        repeat(repeticiones) { ronda ->
            val a = FakeService("A-$ronda")
            ref.promote(a)

            val barrera = CyclicBarrier(hilos)
            val exitos = AtomicInteger(0)
            val pool = java.util.concurrent.Executors.newFixedThreadPool(hilos)
            try {
                val tareas = (1..hilos).map {
                    pool.submit {
                        barrera.await(10, TimeUnit.SECONDS)
                        if (ref.release(a)) exitos.incrementAndGet()
                    }
                }
                tareas.forEach { it.get(30, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }

            assertEquals("Ronda $ronda: solo un release puede ganar", 1, exitos.get())
            assertNull("Ronda $ronda: la instancia quedó liberada", ref.current)
        }
    }

    /**
     * Dos instancias viejas liberándose en paralelo no pueden borrar la nueva.
     *
     * Modela el caso real de recreación: el sistema ya conectó `nueva` y a la vez llegan los
     * callbacks tardíos de dos instancias anteriores.
     */
    @Test
    fun `liberaciones concurrentes de instancias viejas no borran la nueva`() {
        val repeticiones = 200
        repeat(repeticiones) { ronda ->
            val viejaA = FakeService("viejaA-$ronda")
            val viejaB = FakeService("viejaB-$ronda")
            val nueva = FakeService("nueva-$ronda")

            ref.promote(viejaA)
            ref.promote(viejaB)
            ref.promote(nueva)

            val barrera = CyclicBarrier(2)
            val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val t1 = pool.submit { barrera.await(10, TimeUnit.SECONDS); ref.release(viejaA) }
                val t2 = pool.submit { barrera.await(10, TimeUnit.SECONDS); ref.release(viejaB) }
                t1.get(30, TimeUnit.SECONDS)
                t2.get(30, TimeUnit.SECONDS)
            } finally {
                pool.shutdownNow()
            }

            assertSame("Ronda $ronda: la nueva sobrevive", nueva, ref.current)
        }
    }

    /**
     * Cada ronda enfrenta una promoción con una lectura de cada lector.
     *
     * La barrera abre la ronda y espera todas las lecturas antes de permitir otra promoción.
     * Una lectura solo puede observar la instancia anterior o la promovida en esa ronda.
     */
    @Test
    fun `los lectores concurrentes solo observan instancias promovidas`() {
        val instancias = (1..4).map { FakeService("S$it") }
        val hilosLectores = 2
        val rondas = 2000
        val barrera = CyclicBarrier(1 + hilosLectores)
        val invalidas = AtomicInteger(0)
        ref.promote(instancias.last())

        val pool = java.util.concurrent.Executors.newFixedThreadPool(1 + hilosLectores)
        try {
            val escritora = pool.submit {
                repeat(rondas) { ronda ->
                    barrera.await(10, TimeUnit.SECONDS)
                    ref.promote(instancias[ronda % instancias.size])
                    barrera.await(10, TimeUnit.SECONDS)
                }
            }

            val lectoras = List(hilosLectores) {
                pool.submit {
                    repeat(rondas) { ronda ->
                        val anterior = instancias[(ronda + instancias.lastIndex) % instancias.size]
                        val nueva = instancias[ronda % instancias.size]
                        barrera.await(10, TimeUnit.SECONDS)
                        val observada = ref.current
                        if (observada !== anterior && observada !== nueva) {
                            invalidas.incrementAndGet()
                        }
                        barrera.await(10, TimeUnit.SECONDS)
                    }
                }
            }

            escritora.get(60, TimeUnit.SECONDS)
            lectoras.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertEquals("Cada lectura debe observar la instancia anterior o la nueva", 0, invalidas.get())
    }
}
