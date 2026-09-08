# RTK - Rust Token Killer (Google Antigravity)

**Propósito**: Proxy CLI optimizado para reducir el consumo de tokens en comandos de terminal.

## Regla Obligatoria

Anteponer SIEMPRE el prefijo `rtk` al ejecutar comandos de consola para minimizar el consumo de cuota e input tokens.

Ejemplos:

```bash
rtk git status
rtk git diff
rtk ./gradlew test
rtk cargo test
rtk ls src/
rtk grep "patron" src/
rtk find "*.kt" .
rtk docker ps
rtk gh pr list
```

## Comandos de Control y Métricas

```bash
rtk gain              # Mostrar resumen de tokens y costos ahorrados
rtk gain --history    # Historial de comandos ejecutados con su porcentaje de ahorro
rtk discover          # Detectar comandos ejecutados que pudieron haberse ahorrado
rtk proxy <cmd>       # Ejecutar sin filtrado (solo para depuración cuando sea indispensable)
```

## Justificación

RTK filtra, deduplica y comprime la salida de comandos antes de que ingrese a la ventana de contexto de Antigravity, recortando entre un 60% y un 90% de los tokens de consola en operaciones cotidianas.
