# Domain Docs

Cómo deben consumir las skills de ingeniería la documentación de dominio de
este repositorio al explorar el código.

## Antes de explorar, lee

- **`GLOSSARY.md`** en la raíz del repositorio, o
- **`GLOSSARY-MAP.md`** en la raíz si existe: apunta a un `GLOSSARY.md` por
  contexto. Lee cada uno que sea relevante para el tema.
- **`docs/adr/`**: lee los ADRs que afecten al área en la que vas a trabajar.

Si alguno de estos archivos no existe, **continúa en silencio**. No señales su
ausencia; no propongas crearlos de entrada. La skill `/domain-modeling` los
crea de forma perezosa cuando los términos o las decisiones realmente se
resuelven.

## Estructura de archivos

Repositorio de contexto único (la mayoría de repos):

```
/
├── GLOSSARY.md
├── docs/adr/
│   ├── 0001-....md
│   └── 0002-....md
└── app/
```

Este repositorio es de **contexto único**: un solo módulo Gradle (`:app`) y un
único paquete (`com.example.tradedraw`).

## Usa el vocabulario del glosario

Cuando tu salida nombre un concepto de dominio (en el título de un issue, una
propuesta de refactor, una hipótesis o el nombre de un test), usa el término
tal como se define en `GLOSSARY.md`. No derives a sinónimos que el glosario
evite explícitamente.

Si el concepto que necesitas aún no está en el glosario, eso es una señal: o
estás inventando lenguaje que el proyecto no usa (reconsidéralo) o hay un
hueco real (anótalo para `/domain-modeling`).

## Señala conflictos con ADRs

Si tu salida contradice un ADR existente, sácalo explícitamente en vez de
sobrescribirlo en silencio:

> _Contradice ADR-0007 (...), pero merece reabrirse porque…_

## Fuentes de dominio ya existentes en este repositorio

Antes de inventar terminología, consulta lo que ya está versionado:

- `AGENTS.md` — arquitectura por capas (Overlay/UI, Screen Vision, Trading &
  Strategy, Risk & Calibration, Remote AI & Diagnostics) y "gotchas" conocidos.
- `docs/ARCHITECTURE.md` — arquitectura técnica.
- `docs/QUANTITATIVE_BINARY_STRATEGIES_MASTER_TRADERS.md` — definiciones
  formales de las estrategias, umbrales y filtros.
- `.agents/skills/master_traders_skill/SKILL.md` — gobernanza cuantitativa
  obligatoria (breakeven, matriz de confluencia, Quarter-Kelly).
- `workflows/PREREGISTRO_R*.md` — hipótesis falsables congeladas antes de medir.

## Regla de vocabulario más importante del proyecto

Este proyecto distingue con rigor cuatro cosas que suenan parecidas. No las
mezcles en ningún título, hipótesis ni criterio de aceptación:

1. Correctitud de la app y de sus contadores.
2. Integridad de datos, temporalidad y ejecución simulada.
3. Calidad estadística de las señales.
4. Rentabilidad fuera de muestra.

Que el código compile o que un control de instrumentación pase **no** es
evidencia de (3) ni de (4).
