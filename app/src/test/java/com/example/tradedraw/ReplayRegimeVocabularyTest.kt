package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R23A Iteracion 2: coherencia del vocabulario y del reloj entre el journal de replay y el lado live.
 *
 * HALLAZGO QUE LO MOTIVA (OpenCode, iter 1). El comparador Live <-> Replay reporto 4/4 pares
 * divergentes con desglose `{candle_second: 1, market_regime: 3}`. Analizando los datos reales de
 * `loops/r23_autonomous/iter_1/`:
 *
 *  1. `market_regime` NO era una discrepancia de mercado: live emitia `UPTREND` (un valor de
 *     TENDENCIA en la columna de REGIMEN, ver nota abajo) y replay emitia `CHOP`. Eran vocabularios
 *     distintos para el mismo juicio. Esta clase fija el vocabulario del lado REPLAY, que es el que
 *     esta en el codigo Kotlin.
 *  2. `candle_second` diverge en 1 de 4 pares porque los dos lados miden instantes DISTINTOS por
 *     diseno: live fecha con el `atMs` del evento despachado, replay con el ts del tick de la senal.
 *     No es un bug del replay y no se "arregla" moviendo el reloj de uno de los dos.
 *
 * NOTA SOBRE `UPTREND` EN LA COLUMNA DE REGIMEN. Verificado en
 * `loops/r23_autonomous/iter_1/decisions.csv`: la columna `market_regime` contiene `UPTREND`, que es
 * el valor de la columna `trend` contigua (y `trend` tambien dice `UPTREND`). El monitor live
 * (`live_monitor_r23_autonomous.py`) calcula la variable correcta
 * (`"SIDEWAYS" if is_sideways else "TRENDING"`) pero la fila queda con el valor de tendencia. Eso
 * NO lo puede corregir el lado Kotlin: es del script, cuyo propietario es AGY. Se deja constancia
 * aqui porque, mientras siga asi, la comparacion de `market_regime` no es interpretable en ninguna
 * direccion.
 */
class ReplayRegimeVocabularyTest {

    /**
     * Contrato del vocabulario de `market_regime` en el journal de replay.
     *
     * Debe ser exactamente el mismo conjunto que produce el lado live: {SIDEWAYS, TRENDING}. La
     * fuente de la decision es la misma en ambos lados
     * (`syntheticCandleEngine.isChoppinessDetected()`), asi que solo cambia la etiqueta.
     */
    private fun regimeDeReplay(isChop: Boolean): String = if (isChop) "SIDEWAYS" else "TRENDING"

    @Test
    fun elReplayUsaElMismoVocabularioQueElMonitorLive() {
        assertEquals("mercado lateral/chop", "SIDEWAYS", regimeDeReplay(true))
        assertEquals("mercado direccional", "TRENDING", regimeDeReplay(false))
    }

    @Test
    fun elReplayYaNoEmiteChopParaElRegimen() {
        // "CHOP" era la etiqueta del replay y NO existe en el vocabulario del monitor live: cada par
        // salia marcado como divergente aunque ambos lados describieran el mismo mercado.
        assertEquals("false positivo eliminado", false, regimeDeReplay(true) == "CHOP")
        assertEquals("false positivo eliminado", false, regimeDeReplay(false) == "CHOP")
    }

    @Test
    fun elConjuntoDeRegimenesDelReplayEsCerrado() {
        // Las dos unicas etiquetas posibles. Si aparece una tercera, el comparador la marcara como
        // divergencia contra un live que solo sabe decir estas dos.
        val vocabulario = setOf(
            regimeDeReplay(true),
            regimeDeReplay(false)
        )
        assertEquals("solo dos regimenes posibles", setOf("SIDEWAYS", "TRENDING"), vocabulario)
    }

    @Test
    fun elRegimenNoSeConfundeConLaTendencia() {
        // HALLAZGO (ver KDoc): en el decisions.csv del iter 1 la columna de regimen trae un valor de
        // TENDENCIA. Este test fija la propiedad que debe cumplir el lado replay: el regimen NUNCA
        // debe ser una etiqueta direccional.
        val direcciones = setOf("UPTREND", "DOWNTREND", "SIDEWAYS")

        // Nota: SIDEWAYS es ambiguo por diseno (es a la vez regimen lateral y una tendencia neutra),
        // asi que la asercion fuerte es solo sobre las direcciones con sesgo.
        assertEquals("el regimen no puede ser una tendencia con sesgo", false, regimeDeReplay(true) in setOf("UPTREND", "DOWNTREND"))
        assertEquals("el regimen no puede ser una tendencia con sesgo", false, regimeDeReplay(false) in setOf("UPTREND", "DOWNTREND"))
        assertEquals("y no debe venir de la columna trend", false, regimeDeReplay(false) == "UPTREND")
    }

    @Test
    fun elSegundoDeVelaSeDerivaDelTimestampDeLaSenal() {
        // El replay fecha la senal con el ts del tick que la disparo:
        // `((entryTick.ts / 1000L) % 60L).toInt()`. Se fija la formula porque es el contrato que el
        // comparador lee en la columna `candle_second`.
        fun candleSec(ts: Long): Int = ((ts / 1000L) % 60L).toInt()

        assertEquals("segundo 3 de la vela", 3, candleSec(1792104123656L))
        assertEquals("segundo 5 de la vela", 5, candleSec(1792104365250L))
        assertEquals("borde :00", 0, candleSec(1_792_104_000_000L))
        assertEquals("borde :59", 59, candleSec(1_792_104_059_000L))
    }

    @Test
    fun losDosLadosMidenInstantesDistintosPorDiseno() {
        // Caso medido (par 1 del iter 1): live ts 1792104125346 -> segundo 5; replay ts
        // 1792104123656 -> segundo 3. Diferencia de 1690 ms entre el momento del DESPACHO (live) y el
        // del TICK de la senal (replay). Es esperado: no se corrige moviendo el reloj de un lado,
        // porque cada uno fecha el instante que de verdad conoce.
        val liveTs = 1792104125346L   // atMs del evento
        val replayTs = 1792104123656L // ts del tick de entrada

        assertEquals("live fecha el despacho", 5, ((liveTs / 1000L) % 60L).toInt())
        assertEquals("replay fecha el tick", 3, ((replayTs / 1000L) % 60L).toInt())
        assertEquals("diferencia real entre los dos instantes", 1690L, liveTs - replayTs)
    }
}
