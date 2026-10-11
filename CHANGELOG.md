# Changelog

Todos los cambios importantes de este proyecto se documentan en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es/1.0.0/),
y este proyecto sigue [Versionado Semántico](https://semver.org/lang/es/).

---

## [1.4.2] — 2026-10-11

### Corrección de compilación
- `const val` fuera del `companion object` (no permitido en Kotlin): ahora es `val` normal.

## [1.4.1] — 2026-10-11

### Borde rojo elegante
- La lupa (círculo y barra) ahora tiene un anillo rojo fino y elegante (2.5 dp) en vez del borde amarillo grueso. Mini, refinado y visible sobre cualquier contenido.

## [1.4.0] — 2026-10-11

### Lupa más limpia + barra de lectura
- **Botones auto-ocultables**: la lupa ahora se ve como *solo el círculo*, sin botones alrededor. Al tocarla aparecen los 5 botones y se esconden solos tras 4 segundos sin interactuar. Más espacio para leer, mismas funciones.
- **Barra de lectura** (segunda opción): franja horizontal redondeada para leer renglón por renglón. Se elige en la pantalla principal ("Círculo / Barra de lectura"), se aplica en caliente, y sus botones aparecen en fila debajo al tocarla. En modo barra, los botones de tamaño se desactivan (tamaño fijo ideal).

## [1.3.2] — 2026-10-11

### Lupa más compacta para leer
- La lupa era demasiado grande (hasta 250 dp de diámetro + botones: casi toda la pantalla). Nueva escala compacta pensada para leer: pequeña 110 dp, mediana 150 dp, grande 200 dp de diámetro, y la **pequeña es el nuevo valor por defecto**.
- Los valores antiguos se descartan automáticamente (nueva clave de ajuste).

## [1.3.1] — 2026-10-11

### Mejoras de la lupa flotante (probada en teléfono real)
- **Botones más compactos**: los 5 botones alrededor de la lupa pasaron de 48 dp a 36 dp de diámetro para no tapar el contenido ampliado.
- **Menos parpadeo**: la ocultación temporal para capturar la pantalla bajó de 70 ms a 50 ms (~3 fotogramas), apenas perceptible.
- **Arrastre suavizado**: la ventana ahora "persigue" al dedo con un filtro exponencial en vez de saltar con cada evento táctil — estabiliza la lupa con manos temblorosas y el movimiento se siente más fluido; al soltar, se asienta exactamente donde quedó el dedo.

## [1.3.0] — 2026-10-11

### Cambio principal: solo lupa de PANTALLA (sin cámara)
- La app ahora abre directamente en la pantalla de la **lupa flotante**, que amplía la **pantalla del teléfono** sobre cualquier app (WhatsApp, navegador…), con zoom (−/+, pinza) y arrastre libre.
- **Eliminada la cámara**: se quitaron MainActivity, EasyModeActivity, CameraController, ImageDescriber, FeedbackDisplay y ColorModes, sus layouts, el permiso CAMERA y las dependencias CameraX y ML Kit (APK más liviano).
- El botón "Volver" ahora es "Ayuda"; el tutorial y la ayuda se reescribieron para la lupa de pantalla; el tutorial aparece la primera vez.

## [1.2.7] — 2026-10-11

### Corregido
- **Error de compilación**: `import kotlin.math.minOf` no existe (minOf vive en `kotlin.comparisons`, ya importado por defecto). Se usa sin importación, como en el resto del proyecto.

## [1.2.6] — 2026-10-11

### Corregido (validación profesional)
- **Arranque duplicado del servicio**: si se tocaba ACTIVAR dos veces, se duplicaba el overlay y se fugaba el hilo de captura. Ahora se ignora el segundo arranque.
- **Rendimiento en dibujo**: el texto del indicador de aumento se formateaba y medía en cada frame (15/s); ahora se precalcula. La matriz de mapeo reutiliza un array en vez de asignar uno por frame.

## [1.2.5] — 2026-10-11

### Corregido (lupa flotante sobre otras apps)
- **Parpadeo al leer**: la lente se ocultaba un instante cada 1,5 s para refrescar la foto. Ahora refresca cada 4 s cuando está quieta (al interactuar se actualiza al momento igual que antes): mucho más cómoda para leer textos.
- **Botones de tamaño y aumento sin efecto**: si la lupa ya estaba activa, cambiar el tamaño o el aumento desde la pantalla de control no hacía nada visible. Ahora se aplican al momento, sin reiniciar la lupa.
- **Aumento inicial**: ahora es 3.0x (antes 2.5x), mejor para leer letras pequeñas desde el primer uso.

## [1.2.4] — 2026-10-11

### Corregido (lupa de cámara)
- **Botón de centrar sin efecto**: la mira (⌖) no hacía nada en la lupa embebida. Ahora devuelve la lupa al centro de la pantalla.
- **Lupa cortada al cambiar tamaño**: si se agrandaba con doble toque cerca del borde, los botones quedaban cortados. Ahora la posición se recorta sola.
- **Batería**: la lupa ya no se refresca cuando la app está en segundo plano.

## [1.2.3] — 2026-10-11

### Añadido (lupa de cámara)
- **Arrastre de la lupa**: ahora se puede mover el círculo por la pantalla con el dedo y amplía lo que haya debajo de donde la pongas (antes estaba fija en el centro). La lupa nunca se sale de la pantalla ni los botones quedan cortados.

### Corregido
- **Tolerancia de toque**: estaba en píxeles fijos (28 px), demasiado estricta en pantallas densas para manos mayores. Ahora se adapta a la densidad.
- **Rendimiento**: si el frame no cambió, no se recrea el shader ni se redibuja (menos trabajo en el hilo principal).
- **Robustez**: si la vista de la lupa flotante no existe, el ciclo de captura se reprograma en vez de detenerse.

## [1.2.2] — 2026-10-11

### Corregido (lupa de cámara)
- **Zona ampliada equivocada**: la lupa embebida asumía que el frame de la cámara era 1:1 con la pantalla y ampliaba una zona distinta a la que se veía debajo del círculo. Ahora calcula el mapeo real según el escalado del PreviewView (FIT_CENTER).
- **Imagen de lado**: `ImageProxy.toBitmap()` no aplica la rotación del sensor; en vertical la lupa y el OCR veían la imagen girada 90°. Ahora se rota cada frame según `rotationDegrees`.
- **Zoom perdido al abrir**: pedir zoom antes de vincular la cámara no tenía efecto, así que el zoom guardado se perdía al abrir la app. Ahora queda pendiente y se aplica al terminar el bind.
- **Botón ✕ sin efecto**: en la lupa embebida no hacía nada. Ahora apaga la lupa.
- **Medición de la vista**: `onMeasure` ignoraba las medidas del padre y el layout quedaba indefinido. Ahora las respeta.
- **Rendimiento**: la conversión de frames se limita a ~15 fps (antes 30 fps a resolución completa), el teléfono se calienta menos y dura más la batería.

### Corregido (lupa flotante)
- **Guardado del zoom**: durante la pinza se escribía a disco decenas de veces por segundo. Ahora se guarda con retardo de 800 ms, una sola vez.
- **Botón ✕ reutilizable**: el seguro anti-doble-toque quedaba activo para siempre; ahora se restablece tras cerrarse.

### Limpieza
- Eliminado `ScreenLensService.kt` (código muerto: nunca se declaraba ni se iniciaba).

## [1.2.1] — 2026-10-11

### Corregido (lupa flotante)
- **Arrastre**: usaba coordenadas relativas a la ventana (que se mueve con el dedo), por eso iba lento y tembloroso. Ahora usa posición absoluta en pantalla y la lupa vuelve con el dedo si se pasa del borde.
- **Botones cortados**: el margen de la ventana (38 dp) era menor que lo que ocupan los botones (60 dp); quedaban recortados y medio inalcanzables. Ahora 54 dp con botones de 48 dp (mínimo recomendado por Android).
- **Iconos**: "⌖" y "❄" dependían de la fuente del teléfono y salían como un cuadrado vacío. Ahora todos los iconos se dibujan (−, +, ✕, pausa/reanudar, mira).
- **Tamaño "Grande"**: 200 dp de radio no cabía en un móvil (400 dp de diámetro). Tamaños nuevos 70/95/125 dp y recorte automático al 90 % del ancho de la pantalla.
- **Cambiar tamaño**: la lupa se mantiene centrada donde estaba (antes crecía hacia abajo/derecha).
- **Parpadeo y "lupa dentro de la lupa"**: no se captura mientras se arrastra (se usa la última foto completa); se refresca al soltar, al tocar la app de debajo y cada 1,5 s si está quieta. La foto solo se usa si llegó DESPUÉS de ocultar la lupa.
- **Cola de imágenes**: se guarda siempre el frame más reciente (antes la cola podía llenarse y devolver una imagen vieja con la propia lupa).
- **Zona ampliada desplazada**: el bitmap es 1:1 con la pantalla; se quitó la escala por rowPadding.
- **Congelado accidental**: un toque suelto ya no congela; se pausa con el botón azul II.
- Ventana sin desplazamiento por notch (NO_LIMITS + cutout SHORT_EDGES).
- Aviso en pantalla y por voz: en Android 14+ elegir "Toda la pantalla" al dar el permiso de captura.

## [1.2.0] — 2026-09-16

### Añadido
- **Lupa flotante** (overlay) que funciona sobre cualquier app
- **Lupa de fotos**: amplía imágenes de la galería
- **Tamaño de lupa ajustable** (pequeña / mediana / grande)
- **Modo Fácil**: solo 3 botones gigantes
- **Tutorial** de bienvenida en 3 pasos
- **Pantalla de ayuda** con guía de cada botón
- **Descripción de imágenes** por voz (colores, brillo, contraste)
- **Feedback visual grande** al tocar cada botón
- Interruptores para controlar qué se lee en voz alta

### Cambiado
- Migración completa de Python/Kivy a **Kotlin nativo**
- Rediseño de la interfaz con botones más grandes y claros
- Mejora del rendimiento: hardware layer, objetos reutilizados, caché

### Corregido
- Lupa mostraba zona equivocada (mapeo de coordenadas)
- Zoom de lupa no funcionaba (escalado por resolución)
- Zoom de cámara fallaba fuera del rango real
- Fugas de memoria en la cámara
- TTS fallaba en silencio si no inicializaba

---

## [1.1.0] — 2026-09-15

### Añadido
- 6 modos de color de alto contraste
- OCR con Google ML Kit (offline)
- Lectura en voz alta (TTS nativo)
- Vibración como confirmación táctil
- Ajustes persistentes

### Corregido
- Crash al arrancar (config.py con `\n` literales)
- ML Kit no incluido en el APK
- Permiso CAMERA faltante

---

## [1.0.0] — 2026-09-14

### Añadido
- Versión inicial en Python/Kivy
- Cámara en vivo con zoom
- Lupa flotante básica
- OCR con Tesseract
- Texto a voz

---

[1.2.0]: https://github.com/KeinerMaster/LupaFacil-V2/releases/tag/v1.2.0
[1.1.0]: https://github.com/KeinerMaster/LupaFacil-V2/releases/tag/v1.1.0
[1.0.0]: https://github.com/KeinerMaster/LupaFacil-V2/releases/tag/v1.0.0
