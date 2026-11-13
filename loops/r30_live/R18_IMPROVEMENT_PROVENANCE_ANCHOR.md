# R18-IMPROVEMENT — ANCLA DE PROVENANCE (verbatim, preservado 21:10Z)

**Procedencia:** informe del worker e99c1a6e (ronda R18 improvement), entregado 20:56:14Z,
retransmitido por el prep 076b98ad a las 20:56:52Z (relay completo, sin truncar).
Anclado a disco por el coordinador 8fff4996 a las 21:10:20Z ante riesgo de muerte de
infraestructura por batería (<25% proyectado ~21:18-21:22Z). El transcript original vive en
brain/e99c1a6e-*/.system_generated/logs/transcript.jsonl (puede no sobrevivir a un kill de MIUI).

## Verbatim del hallazgo central (cifra corregida que cita el A-J)

"### LEAD 3 — Momento/mechanismo de corrección: ✓ SLEW CONFIRMADO Y FORTALECIDO
- Mi escaneo estructural de los 308,071 eventos (awk, ambos sentidos):
  - Saltos hacia atrás <−0.02 s: 25 (el prior dijo "6 de exactamente −1.000 s" — FALSO, ver §2).
  - Máximo salto atrás: −0.941162 s (línea 249138, stamp battery_level).
  - Huecos forward >30 s: exactamente 1: +30.141307 s (línea 297635, 14:34:52.947159→14:35:23.088466,
    par netstats — doze/screen-off, no reloj). El "+31s" del prior verificado.
  - Pares consecutivos delta-0: 27,775 (latching de stamp) pero la corrida máxima de stamp
    idéntico <10 líneas ⇒ sin ocultamiento estructural.
- VERIFICADO: TIME_SET=0, timezone_changed=0, am_broadcast_intent=0 en todo el dump events.
- VERIFICADO: densidad en la ventana de corrección 12:47:58→13:10:30 local: buckets de 60 s con
  94–1537 eventos (dip solo en 13:10=94, al cierre). Sin hueco.
- Conclusión (más fuerte que el prior): prueba estructural — con cobertura densa de 13h y ningún
  hueco >30.14 s, ningún paso de ±25.5 min pudo ocurrir dentro de la cobertura. El veredicto
  slew-no-step es ahora conclusión estructural, no solo inferencia. PENDIENTE: identidad del
  agente (kernel/OEM MIUI-HyperOS, invisible a time_detector y logcat userspace)."

## Cifras a citar en el informe A-J
- Saltos hacia atrás <−0.02s: 25 (NO "6 de −1.000s" — ese detalle del prior quedó refutado)
- Máximo salto atrás: −0.941162s (línea 249138 del trueframe dump)
- Único hueco forward >30s: +30.141307s (línea 297635, par netstats doze — no reloj)
- TIME_SET / timezone_changed / am_broadcast_intent: 0 / 0 / 0 en 308,071 eventos
- Fuente de datos: events_buffer_dump_20261113T195222Z_trueframe.log (60,082,525 B,
  sha256 183ffa3588ef1adec4d792da8eefe90d92b390d11b3863b0975213d3ca74a280, untracked fuera del git)

## Otros hallazgos del mismo informe (para A-J)
- Custodia 166MB verificada on-disk conforme a decisión del coordinador (ítem cerrado).
- Perennización 14410 sha a7aa79aa403bc0ba verificada on-disk.
- Batería baseline −13.9%/h en marco true (96%→67%, 17:22→19:52Z); aceleración en vivo documentada aparte.
- HIPÓTESIS (no veredicto): compresión de cadencia netstats 12:18→13:10 local como firma del slew;
  podría ser parcialmente doze — usar como curva de onset candidata, no como conclusión.
