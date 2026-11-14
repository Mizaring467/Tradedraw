#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
R24 TRACK B — TESTS DETERMINISTAS DE INFRAESTRUCTURA DEL REPLAY v2 (B1..B4).

QUE CONGELAN. Las cuatro piezas que el barrido W05-W11 necesitaba y no tenia:

  B1  Estado ADAPTIVE_BLOCK con la MISMA memoria del Live: carga desde
      `adaptive_learning_state.json` con las reglas de purga de `loadState` (50% / TTL 24 h), arranque
      con memoria real, y ausencia de look-ahead (una firma no participa antes de su liquidacion).
  B2  Settlement y cooldown: `lastTradeResolutionTime` se fija con el instante en que el Live
      resolvio, no con `entrada + 60 s`; sin ledger el cooldown NO se modela.
  B3  Compuerta V1: mismo `actionConflict` del Live, mismo `detail` que el motor emite, y en el
      mismo punto del pipeline.
  B4  Orden de guards identico al de `executeHeadlessTrade`, y determinismo byte a byte.

COMO SE EJECUTAN. Stdlib pura, sin dispositivo, sin red, sin Gradle:

    python -m unittest scripts.validation.test_replay_v2_guards -v
    python scripts/validation/test_replay_v2_guards.py

Ningun test toca un umbral, un peso, un veto ni una politica: todos fijan INFRAESTRUCTURA. Si un
test de aqui falla tras tocar estrategia, el cambio de estrategia es el que esta mal.
"""

import csv
import io
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import replay_v2_tick as rv  # noqa: E402
from compare_live_replay_v2 import clasificar_tipo  # noqa: E402


# ---------------------------------------------------------------------------
# Utilidades de construccion (sin reloj de pared, sin azar)
# ---------------------------------------------------------------------------

MINUTO = (1_000_000_000 // 60_000) * 60_000     # inicio de minuto exacto


def ts_en_segundo(seg: int, minuto_offset: int = 0) -> int:
    """Timestamp en el segundo pedido del minuto indicado. Verifica el % 60 antes de devolverlo."""
    ts = MINUTO + minuto_offset * 60_000 + seg * 1000
    assert (ts // 1000) % 60 == seg, f"segundo mal construido: {(ts // 1000) % 60} != {seg}"
    return ts


def tick(ts: int, price: float = 100.0, vel: float = 0.0, bull: bool = False,
         bear: bool = False, ric: str = "Z-CRY/IDX") -> dict:
    return {"ts": ts, "ric": ric, "price": price, "velocity": vel, "bull": bull, "bear": bear}


def firma(action="BUY", trend="SIDEWAYS", ds=0.30, dr=0.70, vel=0.0, bull=False, bear=False,
          sideways=True, tight=False, strategy="AUTO_ADAPTIVE", sec=1, ts=0) -> dict:
    """Firma con el MISMO esquema que `_firma` del replay, mas `ts` de visibilidad."""
    f = rv._firma(action, trend, ds, dr, vel, bull, bear, sideways, tight, strategy, sec)
    f["ts"] = ts
    return f


def firma_de_perdida(**kw) -> dict:
    """
    Firma de perdida IDENTICA a la que construye `procesar_tick_v2` para el tick de los tests de
    bloqueo. Con similitud 1.0 (>= 0.99) y contexto estricto, el anti-patron tiene que disparar.
    """
    base = dict(action="BUY", trend="SIDEWAYS", ds=0.30, dr=0.70, vel=0.0, bull=False,
                bear=False, sideways=True, tight=False, strategy="AUTO_ADAPTIVE", sec=4)
    base.update(kw)
    return firma(**base)


def fila_para(estado, ts, trend="SIDEWAYS", ds=0.30, dr=0.70, rsi=50.0, accion="BUY",
              bull=False, bear=False, sr_status=None, action_plan=None):
    return rv.procesar_tick_v2(
        tick(ts, vel=0.0, bull=bull, bear=bear), estado, trend, ds, dr, rsi, accion=accion,
        sr_status=sr_status, action_plan=action_plan,
    )


# ---------------------------------------------------------------------------
# B1 — estado ADAPTIVE_BLOCK
# ---------------------------------------------------------------------------


class TestB1MemoriaInicial(unittest.TestCase):
    def test_sin_memoria_no_hay_adaptive_block(self):
        estado = rv.MaquinaEstados()
        fila = fila_para(estado, ts_en_segundo(4))
        self.assertEqual(fila["kind"], "ORDER_STATE", fila["detail"])

    def test_con_memoria_real_bloquea(self):
        estado = rv.MaquinaEstados(memoria_inicial=[firma_de_perdida(ts=ts_en_segundo(1, -1))])
        fila = fila_para(estado, ts_en_segundo(4))
        self.assertEqual(fila["kind"], "VETO")
        self.assertTrue(fila["detail"].startswith("ADAPTIVE_BLOCK"), fila["detail"])

    def test_detail_clasifica_como_adaptive_block_en_el_comparador(self):
        """Contrato con el instrumento: el comparador v2 debe leer el tipo `ADAPTIVE_BLOCK`."""
        estado = rv.MaquinaEstados(memoria_inicial=[firma_de_perdida(ts=ts_en_segundo(1, -1))])
        fila = fila_para(estado, ts_en_segundo(4))
        self.assertEqual(clasificar_tipo(fila), "ADAPTIVE_BLOCK")

    def test_memoria_inicial_no_se_muta(self):
        original = [firma_de_perdida(ts=ts_en_segundo(1, -1))]
        copia = [dict(f) for f in original]
        estado = rv.MaquinaEstados(memoria_inicial=original)
        fila_para(estado, ts_en_segundo(4))
        self.assertEqual(original, copia, "el estado no puede mutar la lista del llamador")


class TestB1CargaDesdeEstadoReal(unittest.TestCase):
    """`cargar_memoria_adaptativa` replica la purga de `AdaptiveLearningEngine.loadState`."""

    def _state(self, firmas):
        d = tempfile.mkdtemp(prefix="r24b1_")
        ruta = os.path.join(d, "adaptive_learning_state.json")
        with open(ruta, "w", encoding="utf-8") as f:
            json.dump({"lossSignatures": firmas}, f)
        return ruta

    def _sig(self, ts, ds=0.30, dr=0.70, action="BUY", trend="SIDEWAYS", sec=4):
        return {
            "action": action, "trend": trend, "distSup": ds, "distRes": dr,
            "nearSup": False, "nearRes": False, "tickVelNorm": 0.0,
            "bullishImp": False, "bearishImp": False, "doji": False, "tight": False,
            "sideways": True, "strat": "AUTO_ADAPTIVE", "sec": sec, "ts": ts,
            "continuation": False, "reversion": False,
        }

    def test_carga_y_mapea_campos(self):
        inicio = ts_en_segundo(0)
        ruta = self._state([self._sig(inicio - 60_000)])
        firmas, stats = rv.cargar_memoria_adaptativa(ruta, inicio)
        self.assertEqual(len(firmas), 1)
        self.assertEqual(stats["cargadas"], 1)
        f = firmas[0]
        self.assertEqual((f["action"], f["trend"]), ("BUY", "SIDEWAYS"))
        self.assertAlmostEqual(f["ds"], 0.30, places=6)
        self.assertAlmostEqual(f["dr"], 0.70, places=6)
        self.assertEqual(f["candle_second"], 4)

    def test_purga_firmas_genericas_en_el_50(self):
        inicio = ts_en_segundo(0)
        ruta = self._state([self._sig(inicio - 60_000, ds=0.50, dr=0.50),
                            self._sig(inicio - 60_000, ds=0.505, dr=0.499)])
        firmas, stats = rv.cargar_memoria_adaptativa(ruta, inicio)
        self.assertEqual(len(firmas), 0)
        self.assertEqual(stats["purgadas_50"], 2)

    def test_purga_del_50_exige_AMBOS_ratios(self):
        """
        `loadState` purga con `&&`: hace falta que ds Y dr esten en el 50%. Una firma con solo un
        ratio en el 50% describe un contexto real y se conserva. Transcribir `&&` como `||` borraria
        firmas validas en silencio, asi que el test fija la conjuncion.

        (El borde numerico exacto no es fijable con floats: 0.52-0.50 es 0.020000000000000018, de modo
        que `<` y `<=` dan el mismo veredicto. Se fija la conjuncion, que si discrimina.)
        """
        inicio = ts_en_segundo(0)
        solo_sup = self._state([self._sig(inicio - 60_000, ds=0.50, dr=0.90)])
        firmas, stats = rv.cargar_memoria_adaptativa(solo_sup, inicio)
        self.assertEqual(len(firmas), 1, "ds en el 50% con dr lejos NO es firma generica")
        self.assertEqual(stats["purgadas_50"], 0)

        solo_res = self._state([self._sig(inicio - 60_000, ds=0.90, dr=0.50)])
        firmas2, _ = rv.cargar_memoria_adaptativa(solo_res, inicio)
        self.assertEqual(len(firmas2), 1, "dr en el 50% con ds lejos NO es firma generica")

    def test_borde_inferior_de_la_purga_del_50(self):
        """Justo dentro (±0.019) purga; justo fuera (0.52) conserva."""
        inicio = ts_en_segundo(0)
        dentro = self._state([self._sig(inicio - 60_000, ds=0.519, dr=0.481)])
        firmas, stats = rv.cargar_memoria_adaptativa(dentro, inicio)
        self.assertEqual(len(firmas), 0)
        self.assertEqual(stats["purgadas_50"], 1)
        fuera = self._state([self._sig(inicio - 60_000, ds=0.52, dr=0.52)])
        firmas2, _ = rv.cargar_memoria_adaptativa(fuera, inicio)
        self.assertEqual(len(firmas2), 1)

    def test_ttl_de_24h_se_mide_contra_el_inicio_de_ventana(self):
        inicio = ts_en_segundo(0)
        justo = inicio - rv.MEMORIA_TTL_MS                     # exactamente 24 h: NO caduca
        pasado = inicio - rv.MEMORIA_TTL_MS - 1000             # 24 h + 1 s: caduca
        ruta = self._state([self._sig(justo), self._sig(pasado)])
        firmas, stats = rv.cargar_memoria_adaptativa(ruta, inicio)
        self.assertEqual(len(firmas), 1)
        self.assertEqual(stats["purgadas_ttl"], 1)

    def test_ttl_es_determinista_no_usa_reloj_de_pared(self):
        """Mismo JSON + mismo inicio de ventana -> mismo resultado en dos corridas."""
        inicio = ts_en_segundo(0)
        ruta = self._state([self._sig(inicio - 5_000)])
        a, _ = rv.cargar_memoria_adaptativa(ruta, inicio)
        b, _ = rv.cargar_memoria_adaptativa(ruta, inicio)
        self.assertEqual(a, b)


class TestB1SinLookAhead(unittest.TestCase):
    def test_firma_futura_no_entra_en_la_decision(self):
        """Una firma liquidada DESPUES del tick no puede vetarlo."""
        bloqueado = ts_en_segundo(4)
        futura = firma_de_perdida(ts=bloqueado + 1)
        estado = rv.MaquinaEstados(memoria_inicial=[futura])
        fila = fila_para(estado, bloqueado)
        self.assertEqual(fila["kind"], "ORDER_STATE", fila["detail"])
        # Y un tick posterior a su liquidacion SI la ve (misma firma, mismo contexto).
        fila2 = fila_para(estado, bloqueado + 1)
        self.assertTrue(fila2["detail"].startswith("ADAPTIVE_BLOCK"), fila2["detail"])

    def test_memoria_visible_respeta_el_orden_de_visibilidad(self):
        t0 = ts_en_segundo(0)
        estado = rv.MaquinaEstados(memoria_inicial=[firma(ts=t0), firma(ts=t0 + 1000),
                                                   firma(ts=t0 + 2000)])
        self.assertEqual(len(estado.memoria_visible(t0 - 1)), 0)
        self.assertEqual(len(estado.memoria_visible(t0)), 1)
        self.assertEqual(len(estado.memoria_visible(t0 + 1500)), 2)
        self.assertEqual(len(estado.memoria_visible(t0 + 9999)), 3)

    def test_memoria_visible_recorta_a_20(self):
        t0 = ts_en_segundo(0)
        estado = rv.MaquinaEstados(memoria_inicial=[firma(ts=t0 - i) for i in range(30)])
        self.assertEqual(len(estado.memoria_visible(t0)), 20)

    def test_carga_descarta_firmas_posteriores_al_inicio_de_ventana(self):
        inicio = ts_en_segundo(0)
        d = tempfile.mkdtemp(prefix="r24b1_")
        ruta = os.path.join(d, "state.json")
        sig = {
            "action": "BUY", "trend": "SIDEWAYS", "distSup": 0.3, "distRes": 0.7,
            "tickVelNorm": 0.0, "sideways": True, "strat": "AUTO_ADAPTIVE", "sec": 1,
            "ts": inicio + 60_000,
        }
        with open(ruta, "w", encoding="utf-8") as f:
            json.dump({"lossSignatures": [sig]}, f)
        firmas, stats = rv.cargar_memoria_adaptativa(ruta, inicio)
        self.assertEqual(firmas, [])
        self.assertEqual(stats["purgadas_futuro"], 1)

    def test_la_memoria_crece_al_liquidar_no_al_entrar(self):
        """En vivo `recordTradeOutcome` corre en la liquidacion; el replay hace lo mismo."""
        t_entrada = ts_en_segundo(4)
        estado = rv.MaquinaEstados(settlements=[
            {"ts": t_entrada + 60_000, "result": "LOSS", "action": "BUY", "entry_ts": 0}
        ])
        fila = fila_para(estado, t_entrada)
        self.assertEqual(fila["kind"], "ORDER_STATE")
        # Justo despues de entrar la memoria sigue vacia: la perdida aun no existe.
        self.assertEqual(estado.memoria_visible(t_entrada + 1), [])
        estado.avanzar(t_entrada + 60_000)
        self.assertEqual(len(estado.memoria_perdidas), 1)
        self.assertEqual(estado.memoria_perdidas[0]["ts"], t_entrada + 60_000,
                         "la firma debe nacer con el ts de LIQUIDACION, no el de entrada")

    def test_una_liquidacion_sin_resultado_no_alimenta_la_memoria(self):
        """`live_samples` da el instante pero no el desenlace: no se inventa la perdida."""
        t0 = ts_en_segundo(4)
        estado = rv.MaquinaEstados(settlements=[
            {"ts": t0 + 1000, "result": "UNKNOWN", "action": "", "entry_ts": 0}
        ])
        fila_para(estado, t0)
        estado.avanzar(t0 + 5000)
        self.assertEqual(estado.memoria_perdidas, [])
        self.assertEqual(estado.racha_perdidas, 0, "sin desenlace no se suma una perdida")

    def test_una_liquidacion_sin_resultado_SI_mueve_el_reloj_de_cooldown(self):
        """
        El reloj es del settlement, no del resultado: el monitor ve desaparecer la orden pendiente y
        el motor fija `lastTradeResolutionTime` en ese mismo bloque.
        """
        t_res = ts_en_segundo(10, 1)
        estado = rv.MaquinaEstados(settlements=[
            {"ts": t_res, "result": "UNKNOWN", "action": "", "entry_ts": 0}
        ])
        estado.avanzar(t_res)
        self.assertEqual(estado.ultima_resolucion_ms, t_res)
        self.assertTrue(estado.cooldown_modelado)
        self.assertEqual(estado.motivo_cooldown(t_res + 1), "COOLDOWN 1ms")

    def test_tie_conserva_la_racha(self):
        """`recordTradeVoid` no toca `currentLossStreak`: un empate no borra la racha."""
        t = ts_en_segundo(0)
        estado = rv.MaquinaEstados(submode="CONSERVATIVE", settlements=[
            {"ts": t, "result": "LOSS", "action": "BUY", "entry_ts": 0},
            {"ts": t + 50_000, "result": "TIE", "action": "BUY", "entry_ts": 0},
        ])
        estado.aplicar_settlements(t + 50_000)
        self.assertEqual(estado.racha_perdidas, 1)
        self.assertEqual(estado.ultima_resolucion_ms, t + 50_000)


# ---------------------------------------------------------------------------
# B2 — settlement y cooldown
# ---------------------------------------------------------------------------


class TestB2SettlementCooldown(unittest.TestCase):
    def test_cooldown_arranca_en_el_instante_de_liquidacion(self):
        t_res = ts_en_segundo(10)
        estado = rv.MaquinaEstados(settlements=[
            {"ts": t_res, "result": "WIN", "action": "BUY", "entry_ts": 0}
        ])
        # Sin haber aplicado aun, no hay cooldown.
        self.assertIsNone(estado.motivo_cooldown(t_res - 1))
        estado.avanzar(t_res)
        self.assertEqual(estado.ultima_resolucion_ms, t_res)
        fila = fila_para(estado, ts_en_segundo(4, 1))   # 1 min despues: fuera del cooldown YOLO
        self.assertFalse((fila["detail"] or "").startswith("COOLDOWN"))

    def test_cooldown_yolo_bloquea_dentro_de_8s_y_libera_en_el_borde(self):
        """Borde exacto del guard: `< 8000` bloquea, `>= 8000` libera."""
        t_res = ts_en_segundo(0)
        estado = rv.MaquinaEstados(settlements=[
            {"ts": t_res, "result": "WIN", "action": "BUY", "entry_ts": 0}
        ])
        estado.avanzar(t_res)
        self.assertEqual(estado.motivo_cooldown(t_res + rv.YOLO_COOLDOWN_MS - 1),
                         f"COOLDOWN {rv.YOLO_COOLDOWN_MS - 1}ms")
        self.assertIsNone(estado.motivo_cooldown(t_res + rv.YOLO_COOLDOWN_MS))

    def test_sin_ledger_el_cooldown_no_se_reconstruye(self):
        """
        El defecto que B2 elimina: fijar `lastTradeResolutionTime = entrada + 60 s`. Sin ledger, el
        estado cierra la operacion vencida pero NO mueve el reloj de cooldown.
        """
        t0 = ts_en_segundo(4)
        estado = rv.MaquinaEstados()
        fila_para(estado, t0)
        estado.avanzar(t0 + rv.TRADE_DURATION_MS)
        self.assertFalse(estado.cooldown_modelado)
        self.assertEqual(estado.ultima_resolucion_ms, 0)
        self.assertIsNone(estado.motivo_cooldown(t0 + rv.TRADE_DURATION_MS + 1))
        self.assertEqual(estado.contadores.get("vencidas_sin_settlement"), 1)

    def test_settlement_cierra_la_operacion_abierta_y_libera_el_risk_limit(self):
        t0 = ts_en_segundo(4)
        estado = rv.MaquinaEstados(settlements=[
            {"ts": t0 + 55_000, "result": "WIN", "action": "BUY", "entry_ts": 0}
        ])
        fila_para(estado, t0)
        self.assertTrue((fila_para(estado, t0 + 1000)["detail"] or "").startswith("RISK_LIMIT"))
        estado.avanzar(t0 + 55_000)
        self.assertEqual(estado.trades_abiertos, [])

    def test_settlement_por_entry_ts_cierra_la_operacion_correcta(self):
        t0 = ts_en_segundo(4)
        t1 = ts_en_segundo(4, 5)
        estado = rv.MaquinaEstados(settlements=[
            {"ts": t1 + 60_000, "result": "WIN", "action": "BUY", "entry_ts": t1}
        ])
        estado.registrar_entrada(t0, firma_de_perdida())
        estado.registrar_entrada(t1, firma_de_perdida())
        estado.aplicar_settlements(t1 + 60_000)
        self.assertEqual([t["entrada_ms"] for t in estado.trades_abiertos], [t0],
                         "debe cerrar la operacion que el ledger identifica, no la mas vieja")

    def test_racha_de_perdidas_reconstruida_del_ledger(self):
        """`currentLossStreak` gobierna el cooldown de 35 s: se deriva del mismo ledger."""
        t = ts_en_segundo(0)
        estado = rv.MaquinaEstados(submode="CONSERVATIVE", settlements=[
            {"ts": t, "result": "LOSS", "action": "BUY", "entry_ts": 0},
            {"ts": t + 100_000, "result": "LOSS", "action": "BUY", "entry_ts": 0},
        ])
        estado.aplicar_settlements(t)
        self.assertEqual(estado.racha_perdidas, 1)
        self.assertEqual(estado.cooldown_ms(t), rv.CONSERVATIVE_COOLDOWN_MS)
        estado.aplicar_settlements(t + 100_000)
        self.assertEqual(estado.racha_perdidas, 2)

    def test_win_resetea_la_racha(self):
        t = ts_en_segundo(0)
        estado = rv.MaquinaEstados(submode="CONSERVATIVE", settlements=[
            {"ts": t, "result": "LOSS", "action": "BUY", "entry_ts": 0},
            {"ts": t + 100_000, "result": "WIN", "action": "BUY", "entry_ts": 0},
        ])
        estado.aplicar_settlements(t + 100_000)
        self.assertEqual(estado.racha_perdidas, 0)
        self.assertEqual(estado.cooldown_ms(t + 100_000), rv.DEFAULT_COOLDOWN_MS)

    def test_settlements_no_se_aplican_dos_veces(self):
        t = ts_en_segundo(0)
        estado = rv.MaquinaEstados(settlements=[
            {"ts": t, "result": "LOSS", "action": "BUY", "entry_ts": 0}
        ])
        estado.avanzar(t)
        estado.avanzar(t + 1000)
        estado.avanzar(t + 2000)
        self.assertEqual(estado.racha_perdidas, 1)
        self.assertEqual(estado.contadores["settlements_aplicados"], 1)

    def test_settlements_desde_muestras_ignora_orden_no_procesada(self):
        muestras = [
            {"ts": 1000, "risk": {"hasPendingTrade": False, "orderState": "NINGUNA"}},
            {"ts": 2000, "risk": {"hasPendingTrade": True, "orderState": "CONFIRMADA"}},
            {"ts": 3000, "risk": {"hasPendingTrade": False, "orderState": "NINGUNA"}},
            {"ts": 4000, "risk": {"hasPendingTrade": True, "orderState": "ENVIADA"}},
            {"ts": 5000, "risk": {"hasPendingTrade": False, "orderState": "NO_PROCESADA"}},
        ]
        liquidadas = rv.settlements_desde_muestras(muestras)
        self.assertEqual([s["ts"] for s in liquidadas], [3000])
        self.assertEqual(liquidadas[0]["result"], "UNKNOWN")

    def test_cargar_settlements_solo_acepta_resultados_conocidos(self):
        d = tempfile.mkdtemp(prefix="r24b2_")
        ruta = os.path.join(d, "settlements.csv")
        with open(ruta, "w", encoding="utf-8", newline="") as f:
            f.write("ts,result,action,entry_ts\n")
            f.write("1000,LOSS,BUY,900\n")
            f.write("2000,won,BUY,\n")
            f.write("3000,WIN,SELL,\n")
        s = rv.cargar_settlements(ruta)
        self.assertEqual([x["ts"] for x in s], [1000, 3000])
        self.assertEqual(s[0]["entry_ts"], 900)
        self.assertEqual(s[1]["entry_ts"], 0)


class TestB2JournalDelMotor(unittest.TestCase):
    """`settlements_desde_journal` lee el journal REAL: su `timestamp` ES la liquidacion."""

    def _journal(self, filas, nombre="trade_journal_live.csv"):
        d = tempfile.mkdtemp(prefix="r24jw_")
        ruta = os.path.join(d, nombre)
        with open(ruta, "w", encoding="utf-8", newline="") as f:
            f.write("timestamp,iso_date,strategy,submode,action,confidence,price_y,stake,"
                    "base_balance,result,settled_balance,duration_sec,reason" + chr(10))
            for ts, res, act, dur in filas:
                f.write(f"{ts},2026-10-19T00:00:00,AUTO_ADAPTIVE,YOLO,{act},0.85,641.87,1.00,"
                        f"1000.00,{res},999.00,{dur},\"motivo\"" + chr(10))
        return ruta

    def test_timestamp_del_journal_es_la_liquidacion_y_entry_ts_lo_descuenta(self):
        ruta = self._journal([(1792386601598, "LOSS", "BUY", 60)])
        s = rv.settlements_desde_journal([ruta])
        self.assertEqual(len(s), 1)
        self.assertEqual(s[0]["ts"], 1792386601598)
        self.assertEqual(s[0]["entry_ts"], 1792386601598 - 60_000)
        self.assertEqual(s[0]["result"], "LOSS")

    def test_descarta_resultados_desconocidos(self):
        ruta = self._journal([(1000, "PENDIENTE", "BUY", 60), (2000, "WIN", "BUY", 60)])
        self.assertEqual([x["ts"] for x in rv.settlements_desde_journal([ruta])], [2000])

    def test_ignora_journal_de_replay_para_no_ser_circular(self):
        ruta = self._journal([(1000, "LOSS", "BUY", 60)], nombre="replay_journal.csv")
        self.assertEqual(rv.settlements_desde_journal([ruta]), [])

    def test_mezcla_de_dos_journals_ordenada_por_ts(self):
        a = self._journal([(3000, "WIN", "BUY", 60)], nombre="j_a.csv")
        b = self._journal([(1000, "LOSS", "SELL", 60)], nombre="j_b.csv")
        self.assertEqual([x["ts"] for x in rv.settlements_desde_journal([a, b])], [1000, 3000])


# ---------------------------------------------------------------------------
# B3 — compuerta V1
# ---------------------------------------------------------------------------

SR_PUT = "\U0001F534 Toque Directo en Resistencia [PUT \u00d3ptimo]"
SR_CALL = "\U0001F7E2 Toque Directo en Soporte [CALL \u00d3ptimo]"
SR_NEUTRAL = "\u26aa Centro del Canal S/R (Neutral)"
SR_VACIO = "\u2014"
PLAN_CALL_ABAJO = ("\U0001F3AF Plan: Cerca de Soporte (22%). "
                   "Esperando gatillo CALL en la ventana :01s - :05s.")
PLAN_PUT_ARRIBA = ("\U0001F3AF Plan: Buscar retroceso leve a resistencia para ejecutar PUT "
                   "en la ventana :01s - :05s.")
PLAN_CALL_ARRIBA = ("\U0001F3AF Plan: Buscar retroceso leve a soporte para ejecutar CALL "
                    "en la ventana :01s - :05s.")


class TestB3CompuertaV1(unittest.TestCase):
    def test_conflicto_real_del_dispositivo_dispara(self):
        """Caso literal de W11/R11: S/R pide PUT, plan busca CALL."""
        self.assertTrue(rv.accion_conflictiva(SR_PUT, PLAN_CALL_ABAJO))

    def test_direccion_opuesta_inversa_tambien_dispara(self):
        self.assertTrue(rv.accion_conflictiva(SR_CALL, PLAN_PUT_ARRIBA))

    def test_coincidencia_no_dispara(self):
        self.assertFalse(rv.accion_conflictiva(SR_CALL, PLAN_CALL_ARRIBA))
        self.assertFalse(rv.accion_conflictiva(SR_PUT, PLAN_PUT_ARRIBA))

    def test_neutral_y_guion_no_disparan(self):
        self.assertFalse(rv.accion_conflictiva(SR_NEUTRAL, PLAN_CALL_ABAJO))
        self.assertFalse(rv.accion_conflictiva(SR_NEUTRAL, PLAN_PUT_ARRIBA))
        self.assertFalse(rv.accion_conflictiva(SR_VACIO, PLAN_PUT_ARRIBA))

    def test_plan_que_nombra_ambas_direcciones_no_dispara(self):
        """`!planWantsCall` / `!planWantsPut`: si el plan menciona las dos, no hay contradiccion."""
        self.assertFalse(rv.accion_conflictiva(SR_PUT, "Plan: CALL o PUT, esperando ruptura"))
        self.assertFalse(rv.accion_conflictiva(SR_CALL, "Plan: PUT o CALL, esperando ruptura"))

    def test_detail_v1_replica_el_formato_del_motor(self):
        d = rv.motivo_veto_v1(SR_PUT, PLAN_CALL_ABAJO)
        self.assertEqual(d, f'V1 SR="{SR_PUT}" PLAN="{PLAN_CALL_ABAJO}"')

    def test_detail_v1_clasifica_como_v1_en_el_comparador(self):
        estado = rv.MaquinaEstados()
        fila = fila_para(estado, ts_en_segundo(4), sr_status=SR_PUT, action_plan=PLAN_CALL_ABAJO)
        self.assertEqual(fila["kind"], "VETO")
        self.assertEqual(clasificar_tipo(fila), "V1")

    def test_sin_contexto_sr_v1_no_se_evalua(self):
        """No se inventan textos: sin evidencia S/R la fila es entrada, no veto."""
        estado = rv.MaquinaEstados()
        fila = fila_para(estado, ts_en_segundo(4))
        self.assertEqual(fila["kind"], "ORDER_STATE")

    def test_sr_status_desde_distancias_replica_los_cortes(self):
        self.assertEqual(rv.sr_status_desde_distancias(0.15, 0.90, True), SR_CALL)      # borde
        self.assertEqual(rv.sr_status_desde_distancias(0.151, 0.90, True),
                         "\U0001F7E2 Cerca de Soporte (15%)")
        self.assertEqual(rv.sr_status_desde_distancias(0.30, 0.90, True),
                         "\U0001F7E2 Cerca de Soporte (30%)")                            # borde
        self.assertEqual(rv.sr_status_desde_distancias(0.31, 0.90, True), SR_NEUTRAL)
        self.assertEqual(rv.sr_status_desde_distancias(0.90, 0.10, True), SR_PUT)
        self.assertEqual(rv.sr_status_desde_distancias(0.0, 0.0, False), SR_VACIO)

    def test_plan_replica_las_ramas_direccionales(self):
        self.assertIn("CALL", rv.action_plan_desde_estado("UPTREND", 0.9, 0.9, "", 0, False, "YOLO"))
        self.assertIn("PUT", rv.action_plan_desde_estado("DOWNTREND", 0.9, 0.9, "", 0, False, "YOLO"))
        self.assertIn("CALL", rv.action_plan_desde_estado("SIDEWAYS", 0.20, 0.9, "", 0, False, "YOLO"))
        self.assertIn("PUT", rv.action_plan_desde_estado("SIDEWAYS", 0.9, 0.20, "", 0, False, "YOLO"))

    def test_plan_anti_chop_solo_fuera_de_yolo(self):
        """En YOLO la rama anti-chop no existe: el plan cae a la direccional."""
        yolo = rv.action_plan_desde_estado("SIDEWAYS", 0.20, 0.90, "", 0, True, "YOLO")
        self.assertNotIn("Anti-Chop", yolo)
        cons = rv.action_plan_desde_estado("SIDEWAYS", 0.20, 0.90, "", 0, True, "CONSERVATIVE")
        self.assertIn("Anti-Chop", cons)

    def test_plan_con_orden_abierta_nombra_la_direccion(self):
        self.assertIn("CALL", rv.action_plan_desde_estado("UPTREND", 0.9, 0.9, "BUY", 42, False, "YOLO"))
        self.assertIn("PUT", rv.action_plan_desde_estado("UPTREND", 0.9, 0.9, "SELL", 42, False, "YOLO"))


# ---------------------------------------------------------------------------
# B4 — orden de guards
# ---------------------------------------------------------------------------


class TestB4OrdenDeGuards(unittest.TestCase):
    """Cada caso aisla DOS guards consecutivos y afirma cual gana, que es lo que el barrido midio."""

    def test_cooldown_gana_al_veto_de_agotamiento(self):
        t_res = ts_en_segundo(0)
        estado = rv.MaquinaEstados(settlements=[
            {"ts": t_res, "result": "WIN", "action": "BUY", "entry_ts": 0}
        ])
        estado.avanzar(t_res)
        # callPower 92 en UPTREND dispararia el veto de agotamiento: el cooldown va primero.
        fila = fila_para(estado, t_res + 1000, trend="UPTREND", ds=0.10, dr=0.90, rsi=50.0,
                         bull=True)
        self.assertTrue(fila["detail"].startswith("COOLDOWN"), fila["detail"])

    def test_agotamiento_gana_al_adaptive_block(self):
        ts = ts_en_segundo(4)
        estado = rv.MaquinaEstados(memoria_inicial=[firma_de_perdida(
            action="BUY", trend="UPTREND", ds=0.10, dr=0.90, bull=True, sideways=False,
            ts=ts_en_segundo(1, -1))])
        fila = fila_para(estado, ts, trend="UPTREND", ds=0.10, dr=0.90, rsi=50.0, bull=True)
        self.assertTrue(fila["detail"].startswith("VETO_BUY_EXHAUSTION_CLIMAX"), fila["detail"])

    def test_adaptive_block_gana_a_la_operacion_abierta(self):
        """
        El defecto central del barrido: 23 casos donde el Live veto por anti-patron y el replay dijo
        RISK_LIMIT porque evaluaba primero la operacion abierta.
        """
        t0 = ts_en_segundo(4)
        estado = rv.MaquinaEstados(memoria_inicial=[firma_de_perdida(ts=ts_en_segundo(1, -1))])
        fila_para(estado, t0)                       # abre operacion
        fila = fila_para(estado, t0 + 1000)         # mismo contexto, con orden abierta
        self.assertTrue(fila["detail"].startswith("ADAPTIVE_BLOCK"), fila["detail"])

    def test_operacion_abierta_gana_a_v1(self):
        t0 = ts_en_segundo(4)
        estado = rv.MaquinaEstados()
        fila_para(estado, t0)
        fila = fila_para(estado, t0 + 1000, sr_status=SR_PUT, action_plan=PLAN_CALL_ABAJO)
        self.assertTrue(fila["detail"].startswith("RISK_LIMIT"), fila["detail"])

    def test_v1_gana_a_la_entrada(self):
        estado = rv.MaquinaEstados()
        fila = fila_para(estado, ts_en_segundo(4), sr_status=SR_PUT, action_plan=PLAN_CALL_ABAJO)
        self.assertEqual(fila["kind"], "VETO")
        self.assertEqual(estado.trades_abiertos, [], "un veto no puede registrar entrada")

    def test_fuera_de_ventana_gana_a_todos(self):
        estado = rv.MaquinaEstados(memoria_inicial=[firma_de_perdida(ts=ts_en_segundo(1, -1))])
        fila = fila_para(estado, ts_en_segundo(30), sr_status=SR_PUT, action_plan=PLAN_CALL_ABAJO)
        self.assertEqual(fila["kind"], "SKIP")
        self.assertEqual(fila["detail"], "FUERA_VENTANA_SNIPER")

    def test_cadena_completa_deja_pasar_cuando_no_hay_guard(self):
        estado = rv.MaquinaEstados(settlements=[
            {"ts": ts_en_segundo(0, -1), "result": "WIN", "action": "BUY", "entry_ts": 0}
        ])
        fila = fila_para(estado, ts_en_segundo(4), sr_status=SR_CALL, action_plan=PLAN_CALL_ARRIBA)
        self.assertEqual(fila["kind"], "ORDER_STATE")
        self.assertEqual(fila["veto_reason"], "NONE")
        self.assertEqual(estado.contadores["entradas"], 1)


# ---------------------------------------------------------------------------
# Determinismo de la corrida completa
# ---------------------------------------------------------------------------


class TestDeterminismo(unittest.TestCase):
    def _ticks(self):
        base = MINUTO
        out = []
        for i in range(240):             # 4 minutos, tick cada segundo
            ts = base + i * 1000
            out.append(tick(ts, price=100.0 + (i % 7) * 0.0001,
                            vel=0.00002 if i % 3 == 0 else -0.00002,
                            bull=(i % 3 == 0), bear=(i % 3 == 1)))
        return out

    def _correr(self):
        d = tempfile.mkdtemp(prefix="r24det_")
        mem = [firma_de_perdida(ts=MINUTO - 60_000)]
        setl = [{"ts": MINUTO + 120_000, "result": "LOSS", "action": "BUY", "entry_ts": 0}]
        ruta = rv.ejecutar_replay(self._ticks(), d, submode="YOLO", muestras=None,
                                  memoria_inicial=mem, settlements=setl)
        with open(ruta, "rb") as f:
            datos = f.read()
        with open(os.path.join(d, "replay_v2_stats.json"), encoding="utf-8") as f:
            stats = json.load(f)
        return datos, stats

    def test_dos_corridas_identicas_byte_a_byte(self):
        a, sa = self._correr()
        b, sb = self._correr()
        self.assertEqual(a, b)
        self.assertEqual(sa, sb)

    def test_sidecar_declara_la_infraestructura_activa(self):
        _, stats = self._correr()
        self.assertEqual(stats["memoria_inicial_firmas"], 1)
        self.assertTrue(stats["cooldown_modelado"])
        self.assertFalse(stats["v1_evaluado"], "sin contexto S/R el sidecar debe declararlo")
        self.assertEqual(stats["ticks"], 240)

    def test_esquema_de_salida_estable(self):
        datos, _ = self._correr()
        texto = datos.decode("utf-8")
        cabecera = texto.splitlines()[0].split(",")
        self.assertEqual(cabecera, rv.ESQUEMA_DECISIONS)


if __name__ == "__main__":
    unittest.main(verbosity=2)
