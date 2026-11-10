# TradeDraw · R29-A — MISIÓN

**Fase:** R29-A · **Rama de referencia:** `r10-night` · **Inicio:** 2026-11-09T07:00Z
**Identidad:** Quantitative Research Platform.

## Objetivo
Dejar TradeDraw auditado, documentado y preparado para iniciar modularización
arquitectónica controlada en R29-B. No se implementa R29-B aquí.

## Reglas innegociables (resumen operativo)
1–2. No modificar estrategias, parámetros estratégicos ni resultados históricos.
3–5. No activar AUTONOMOUS, no conectar simulación con broker, sin dinero real.
6. No sustituir mecanismos de ejecución existentes sin autorización.
7. No eliminar archivos, directorios, datos, informes ni pruebas.
8. Prohibidos `git reset --hard`, `git clean`, `git checkout -- .` y equivalentes.
9. No sobrescribir trabajo existente sin inspeccionarlo primero.
10. No hacer commits automáticos.
11–12. No inventar resultados; una compilación exitosa no prueba corrección arquitectónica.
13. No modificar archivos fuera del proyecto.
14. No crear módulos vacíos para aparentar progreso.
15. Si una modificación puede alterar comportamiento del sistema: detenerla y documentar el riesgo.

Principio: **preservar primero, comprender después, modificar solo con justificación verificable.**

## rondas
R0 preparación → R1 auditoría git → R2 inventario técnico → R3 aislamiento →
R4 arquitectura objetivo → R5 documentación central → R6 plan migración R29-B →
R7 verificación independiente → R8 cierre (FINAL_REPORT.md).

## Recuperación de sesión
Leer `STATE.md` y verificar el repositorio antes de continuar. No asumir que una
ronda terminó solo porque existe su archivo de informe.
