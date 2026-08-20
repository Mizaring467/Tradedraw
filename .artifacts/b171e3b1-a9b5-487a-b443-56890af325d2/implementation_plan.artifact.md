# Plan de Implementación: Interacción Avanzada y Submenús Inteligentes

Este plan detalla las correcciones críticas para lograr una manipulación de objetos fluida y un despliegue de menús adaptativo según la posición de la pantalla.

## Proposed Changes

### 1. Manipulación Pro de Objetos (`CustomDrawingView.kt`)
- **[MODIFICAR]** `onTouchEvent`:
    - Priorizar la selección de figuras sobre el dibujo nuevo.
    - Implementar `DragMode`: START/END para nodos de control y BODY para desplazamiento total.
    - Bloquear la creación de nuevas figuras si se está interactuando con una existente.
- **[MODIFICAR]** `deleteSelectedOrLast`: Asegurar que el botón de borrado de la barra principal priorice el objeto seleccionado (`selectedShape`) antes de actuar sobre el historial.

### 2. Despliegue Inteligente de Submenús (`OverlayService.kt`)
- **[NUEVO]** Lógica de posicionamiento adaptativo:
    - Obtener el ancho de pantalla mediante `DisplayMetrics`.
    - Detectar si la burbuja está en la mitad derecha o izquierda.
    - Cambiar la orientación del `HorizontalScrollView` del submenú para que aparezca a la izquierda de la barra si esta está pegada al borde derecho.
- **[MODIFICAR]** Estructura de inflado de submenús para soportar la inversión de posición.

### 3. Ajustes de Layout (`overlay_menu.xml`)
- **[MODIFICAR]** Estructura para permitir que el contenedor de submenús pueda posicionarse a ambos lados del menú principal.

## Plan de Verificación

### Automated Tests
- Compilar para verificar la correcta obtención de métricas de pantalla.

### Manual Verification
1.  **Edición de Nodos**: Dibujar una línea de tendencia, tocar un extremo y arrastrarlo. Solo ese punto debe moverse.
2.  **Desplazamiento**: Tocar el centro de un Fibonacci y arrastrar. Toda la figura debe desplazarse en bloque.
3.  **Submenú Adaptativo**: Mover la burbuja al borde derecho de la pantalla y abrir "Líneas". El submenú debe desplegarse hacia la izquierda (hacia adentro de la pantalla).
4.  **Borrado**: Seleccionar un rectángulo y pulsar el botón de borrar de la barra lateral. Solo debe desaparecer ese rectángulo.
