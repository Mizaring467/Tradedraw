# Triage Labels

Las skills hablan en términos de cinco roles canónicos de triage. Este archivo
mapea esos roles a las cadenas de label realmente usadas en el tracker de este
repositorio.

| Label en mattpocock/skills | Label en nuestro tracker | Significado                                     |
| -------------------------- | ------------------------ | ----------------------------------------------- |
| `needs-triage`             | `needs-triage`           | El maintainer debe evaluar este issue           |
| `needs-info`               | `needs-info`             | Esperando información del reportante            |
| `ready-for-agent`          | `ready-for-agent`        | Completamente especificado, listo para un agente AFK |
| `ready-for-human`          | `ready-for-human`        | Requiere implementación humana                  |
| `wontfix`                  | `wontfix`                | No se va a abordar                              |

Cuando una skill menciona un rol (por ejemplo "aplica el label de listo para
agente AFK"), usa la cadena de label correspondiente de esta tabla.

Edita la columna "Label en nuestro tracker" para ajustarla al vocabulario que
realmente uses.

## Notas específicas de este repositorio

- `wontfix` ya existía en GitHub con el color `#ffffff`; los otros cuatro se
  crearon para esta configuración.
- Un issue marcado `ready-for-agent` en este proyecto debe poder resolverse
  **sin** acceso al dispositivo físico. Todo lo que exija calibración visual,
  toques en Binomo o verificación de accesibilidad va en `ready-for-human`.
