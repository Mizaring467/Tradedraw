package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R23J Fase A — Test de EQUIVALENCIA de `calcularPoderesDeMercado`.
 *
 * QUE PROTEGE. En R23H el bloque de calculo de `callPower`/`putPower` se extrajo de
 * `buildAnalysisFromSyntheticEngine` (privada, necesita `MarketTick`) a la funcion pura
 * `calcularPoderesDeMercado`, para que el arnes de replay pudiera reproducir el mismo `callPower`
 * sin duplicar la formula. Fue un refactor mecanico presentado como "sin cambiar una sola
 * constante", pero esa afirmacion se sostenia sobre un test de valores dorados con 5 casos
 * escritos a mano. Cinco casos no prueban equivalencia: prueban cinco puntos.
 *
 * ESTE TEST. Copia la formula ORIGINAL tal como estaba en `HEAD:aac1df3^` (antes de la extraccion)
 * como oraculo independiente, y la compara contra `calcularPoderesDeMercado` sobre una rejilla
 * exhaustiva del dominio:
 *
 *   TrendDirection x distSup 0.05..0.95 (paso 0.05) x distRes 0.05..0.95 (paso 0.05)
 *   x rsi 15..85 (paso 5, mas los bordes exactos de cada umbral)
 *   x isBullishImpulse T/F x isBearishImpulse T/F
 *
 * Cualquier divergencia en cualquier combinacion hace fallar el test nombrando la coordenada exacta.
 *
 * POR QUE EL ORACULO NO SE "REUTILIZA". No llama a `calcularPoderesDeMercado`: si lo hiciera,
 * compararia la funcion consigo misma y pasaria siempre. Es una copia literal del codigo previo,
 * con nombres distintos y sin ningun atajo, para que una edicion futura de la funcion (o del
 * oraculo) se delate.
 *
 * LOS BORDES IMPORTAN. Los umbrales de la formula son `<= 0.25f`, `<= 0.20f`, `rsi in 40.0..68.0` y
 * `rsi in 32.0..60.0`. Un cambio de `<=` a `<` solo se detecta si la rejilla toca el valor exacto,
 * asi que se añaden explicitamente ademas del barrido regular.
 */
class PoderesDeMercadoEquivalenciaTest {

    /**
     * ORACULO: copia literal del bloque original de `buildAnalysisFromSyntheticEngine` en
     * `HEAD:aac1df3^`. No delegar en la implementacion bajo prueba.
     */
    private fun oraculo(
        trend: TrendDirection,
        distSup: Float,
        distRes: Float,
        rsi: Double,
        isBullishImpulse: Boolean,
        isBearishImpulse: Boolean
    ): Pair<Int, Int> {
        var callPower = 50
        var putPower = 50
        when (trend) {
            TrendDirection.UPTREND -> {
                callPower = 68
                putPower = 32
                if (distSup <= 0.25f) callPower += 10
                if (rsi in 40.0..68.0) callPower += 8
                if (isBullishImpulse) callPower += 6
            }
            TrendDirection.DOWNTREND -> {
                callPower = 32
                putPower = 68
                if (distRes <= 0.25f) putPower += 10
                if (rsi in 32.0..60.0) putPower += 8
                if (isBearishImpulse) putPower += 6
            }
            TrendDirection.SIDEWAYS -> {
                if (distSup <= 0.20f) { callPower = 62; putPower = 38 }
                else if (distRes <= 0.20f) { callPower = 38; putPower = 62 }
            }
        }
        callPower = callPower.coerceIn(15, 95)
        putPower = putPower.coerceIn(15, 95)
        if (callPower >= putPower) putPower = 100 - callPower else callPower = 100 - putPower
        return callPower to putPower
    }

    /** 0.05, 0.10, ... 0.95 construidos como k/20f para que los bordes sean exactos en binario. */
    private val distancias: List<Float> = (1..19).map { it / 20f }

    /** Barrido regular 15..85 paso 5, mas los bordes exactos de cada umbral de RSI. */
    private val rsis: List<Double> = buildList {
        var v = 15.0
        while (v <= 85.0) { add(v); v += 5.0 }
        addAll(listOf(22.0, 32.0, 39.999, 40.0, 40.001, 60.0, 67.999, 68.0, 68.001, 78.0))
    }

    private val impulsos: List<Pair<Boolean, Boolean>> = listOf(
        true to true, true to false, false to true, false to false
    )

    @Test
    fun equivalenciaExhaustivaSobreTodoElDominio() {
        var combinaciones = 0
        val divergencias = mutableListOf<String>()

        for (trend in TrendDirection.values()) {
            for (ds in distancias) {
                for (dr in distancias) {
                    for (rsi in rsis) {
                        for ((bull, bear) in impulsos) {
                            combinaciones++
                            val esperado = oraculo(trend, ds, dr, rsi, bull, bear)
                            val obtenido = calcularPoderesDeMercado(trend, ds, dr, rsi, bull, bear)
                            if (esperado != obtenido) {
                                // Se acumulan hasta 10 y se corta: un fallo sistemico genera miles de
                                // lineas y oculta la causa. Con 10 muestras basta para diagnosticar.
                                if (divergencias.size < 10) {
                                    divergencias.add(
                                        "trend=$trend ds=$ds dr=$dr rsi=$rsi bull=$bull bear=$bear " +
                                            "oraculo=$esperado funcion=$obtenido"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (divergencias.isNotEmpty()) {
            throw AssertionError(
                "calcularPoderesDeMercado divergio del oraculo de HEAD en al menos " +
                    "${divergencias.size} combinaciones (se listan hasta 10):\n" +
                    divergencias.joinToString("\n")
            )
        }

        // Guarda contra una rejilla accidentalmente vacia: si alguien rompe los rangos, el test
        // pasaria sin comparar nada. 3 tendencias x 19 x 19 x 25 rsi x 4 impulsos = 108.300.
        assertEquals(
            "La rejilla debe cubrir el dominio completo (3x19x19x25x4)",
            3 * 19 * 19 * rsis.size * 4,
            combinaciones
        )
        println("Equivalencia verificada en $combinaciones combinaciones.")
    }

    /**
     * Los cuatro bordes que un `<=` mal transcrito romperia, comprobados uno a uno. Redundante con el
     * barrido, pero deja el fallo senalado en el nombre del test en vez de en una coordenada
     * perdida entre 108.300 iteraciones.
     */
    @Test
    fun losBordesDeLosUmbralesCoinciden() {
        // distSup == 0.25f inclusive en UPTREND
        assertEquals(
            oraculo(TrendDirection.UPTREND, 0.25f, 0.9f, 90.0, false, false),
            calcularPoderesDeMercado(TrendDirection.UPTREND, 0.25f, 0.9f, 90.0, false, false)
        )
        // distRes == 0.25f inclusive en DOWNTREND
        assertEquals(
            oraculo(TrendDirection.DOWNTREND, 0.9f, 0.25f, 10.0, false, false),
            calcularPoderesDeMercado(TrendDirection.DOWNTREND, 0.9f, 0.25f, 10.0, false, false)
        )
        // distSup == 0.20f inclusive en SIDEWAYS (y el `else if` de distRes)
        assertEquals(
            oraculo(TrendDirection.SIDEWAYS, 0.20f, 0.9f, 50.0, false, false),
            calcularPoderesDeMercado(TrendDirection.SIDEWAYS, 0.20f, 0.9f, 50.0, false, false)
        )
        assertEquals(
            oraculo(TrendDirection.SIDEWAYS, 0.9f, 0.20f, 50.0, false, false),
            calcularPoderesDeMercado(TrendDirection.SIDEWAYS, 0.9f, 0.20f, 50.0, false, false)
        )
        // rsi == 40.0 y 68.0 inclusive en UPTREND; 32.0 y 60.0 inclusive en DOWNTREND
        for (rsi in listOf(40.0, 68.0)) {
            assertEquals(
                "Borde RSI $rsi en UPTREND",
                oraculo(TrendDirection.UPTREND, 0.9f, 0.9f, rsi, false, false),
                calcularPoderesDeMercado(TrendDirection.UPTREND, 0.9f, 0.9f, rsi, false, false)
            )
        }
        for (rsi in listOf(32.0, 60.0)) {
            assertEquals(
                "Borde RSI $rsi en DOWNTREND",
                oraculo(TrendDirection.DOWNTREND, 0.9f, 0.9f, rsi, false, false),
                calcularPoderesDeMercado(TrendDirection.DOWNTREND, 0.9f, 0.9f, rsi, false, false)
            )
        }
    }
}
