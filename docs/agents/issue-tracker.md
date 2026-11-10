# Issue tracker: GitHub

Los issues y specs de este repositorio viven como GitHub issues en
`Mizaring467/Tradedraw`. Usa la CLI `gh` para todas las operaciones.

## Convenciones

- **Crear un issue**: `gh issue create --title "..." --body "..."`. Usa heredoc para cuerpos multilínea.
- **Leer un issue**: `gh issue view <number> --json number,title,body,labels,comments`.
- **Listar issues**: `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'` con los filtros `--label` y `--state` apropiados.
- **Hacer un issue sub-issue de un padre**: `gh issue create --parent <parent> ...`, o después `gh issue edit <parent> --add-sub-issue <child>` (`gh` 2.94+). En versiones anteriores: `gh api --method POST repos/<owner>/<repo>/issues/<parent>/sub_issues -F sub_issue_id=<child-db-id>` (id de base de datos, ver **Bloqueos** abajo). Sin sub-issues, pon `Part of #<parent>` al inicio del cuerpo del hijo.
- **Bloqueos**: las **dependencias nativas** de GitHub, la representación canónica y visible en la UI. Añade una arista con `gh issue create --blocked-by <n>,<n> ...` o `gh issue edit <child> --add-blocked-by <blocker>` (`gh` 2.94+). En versiones anteriores: `gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`, donde `<blocker-db-id>` es el **id de base de datos** numérico del bloqueador (`gh api repos/<owner>/<repo>/issues/<n> --jq .id`, _no_ el `#number` ni el `node_id`). Donde las dependencias no estén disponibles, usa una línea `Blocked by: #<n>, #<n>` al inicio del cuerpo del hijo. Un ticket está desbloqueado cuando todos sus bloqueadores están cerrados.
- **Contar bloqueadores abiertos**: `gh api repos/<owner>/<repo>/issues/<n> --jq .issue_dependencies_summary.blocked_by` (solo bloqueadores abiertos; 0 significa que se puede empezar). `gh issue view <n> --json blockedBy` también lista bloqueadores cerrados, así que filtra: `--jq '[.blockedBy.nodes[] | select(.state == "OPEN")] | length'`.
- **Comentar un issue**: `gh issue comment <number> --body "..."`
- **Aplicar / quitar labels**: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **Cerrar**: publica la explicación con `gh issue comment <number> --body "..."`, luego `gh issue close <number>`. En un issue ya cerrado, `gh issue close --comment` descarta el comentario y aun así devuelve exit 0.

El repo se infiere de `git remote -v`; `gh` lo hace automáticamente dentro de un clon.

Registra issues solo en el repositorio del usuario. En un fork, `gh` puede apuntar por defecto al padre original, así que antes del primer `gh issue create` de una sesión comprueba el destino con `gh repo view --json nameWithOwner`.

## Pull requests como superficie de triage

**PRs como superficie de petición: no.** _(Poner en `yes` si este repositorio trata PRs externos como peticiones de funcionalidad; `/triage` lee este flag.)_

Cuando esté en `yes`, los PRs pasan por los mismos labels y estados que los issues, usando los equivalentes de `gh pr`:

- **Leer un PR**: `gh pr view <number>`, luego `gh pr view <number> --comments` (sin TTY, `--comments` imprime solo los comentarios, no el cuerpo), y `gh pr diff <number>` para el diff.
- **Listar PRs externos para triage**: `gh api --paginate 'repos/{owner}/{repo}/pulls?state=open' --jq '.[] | select(.author_association | IN("OWNER","MEMBER","COLLABORATOR") | not) | {number, title, author: .user.login, author_association, labels: [.labels[].name]}'`.
- **Comentar / etiquetar / cerrar**: `gh pr comment`, `gh pr edit --add-label`/`--remove-label`, `gh pr close`.

GitHub comparte un único espacio de numeración entre issues y PRs, así que un `#42` suelto puede ser cualquiera de los dos: resuélvelo con `gh pr view 42` y recurre a `gh issue view 42` si falla.

## Cuando una skill dice "publica en el issue tracker"

Crea un GitHub issue.

## Cuando una skill dice "obtén el ticket relevante"

Léelo como se describe en **Leer un issue** arriba.

## Política del proyecto

Este repositorio trabaja por rondas de validación (`R21`, `R22`, ... `R27`) con
preregistro obligatorio y veredictos formales documentados en `loops/`.

- Toda petición de trabajo experimental debe declarar su ronda y su preregistro.
- Ningún issue puede cerrarse como "rentable" o "con edge": el criterio es
  siempre un protocolo congelado y una muestra declarada de antemano.
- Los hallazgos de instrumentación no son evidencia de rendimiento.
