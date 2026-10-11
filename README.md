<div align="center">

# 🔍 LupaFacil

### Una ayuda visual para ver, leer y escuchar con mayor facilidad

[![Build Android APK](https://github.com/KeinerMaster/LupaFacil-V2/actions/workflows/build-apk.yml/badge.svg)](https://github.com/KeinerMaster/LupaFacil-V2/actions/workflows/build-apk.yml)
![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue)

**Diseñada especialmente para Dilcia Mercedes** · **Creada con ♥ por K3iiner**

</div>

---

## 📖 Descripción

**LupaFacil** es una aplicación Android nativa que convierte tu teléfono en una
**lupa digital accesible**, pensada para personas mayores y con baja visión.

Apunta la cámara a cualquier texto u objeto, amplíalo hasta 15 veces, cambia a un
modo de alto contraste y deja que la app **te lo lea en voz alta**.

> **Nota:** Esta es la versión migrada de Python/Kivy a **Kotlin nativo**.
> Es mucho más estable, ligera y funciona en **todos los dispositivos Android**
> (32 y 64 bits).

---

## ✨ Características

### 🔍 Dos tipos de lupa

| Lupa | Descripción |
|------|-------------|
| **Cámara** | Lupa en vivo sobre lo que enfoca la cámara |
| **Flotante** | Lupa que flota **sobre cualquier app** (WhatsApp, navegador…) |

### 🎨 Accesibilidad

- **6 modos de color** de alto contraste: normal, invertido, blanco y negro,
  ámbar, amarillo y verde sobre negro
- **Zoom de 1x a 15x** con botones grandes
- **Lectura en voz alta** (TTS nativo en español)
- **Descripción de imágenes** por voz (colores, brillo, contraste)
- **Vibración** como confirmación táctil
- **Modo Fácil**: solo 3 botones gigantes
- **Tutorial** de bienvenida en 3 pasos
- **Ayuda** integrada con guía de cada botón

### ⚙️ Personalización

- Brillo y contraste ajustables
- Velocidad de voz configurable
- Tamaño de texto ajustable
- Tamaño de la lupa (pequeña / mediana / grande)
- Aumento de la lupa (1.5x a 8x)
- **Ajustes persistentes**: se recuerdan entre sesiones

---

## 📱 Capturas

> *Próximamente*

---

## 🚀 Compilar el APK

### Opción 1 — GitHub Actions (recomendada)

**No necesitas instalar nada.** El repositorio incluye un workflow que compila
el APK automáticamente.

1. Haz **fork** o sube el proyecto a tu repositorio de GitHub
2. Ve a la pestaña **Actions**
3. Espera a que termine el job **"Build Android APK"** (~5-10 min)
4. Descarga el artefacto **`LupaFacil-debug-apk`**
5. Descomprime el `.zip` → dentro está el **`.apk`**
6. Instálalo en tu Android (activa "orígenes desconocidos")

### Opción 2 — Android Studio

```bash
# 1. Clona el repositorio
git clone https://github.com/KeinerMaster/LupaFacil-V2.git
cd LupaFacil-V2

# 2. Abre el proyecto en Android Studio
# 3. Build → Build Bundle(s) / APK(s) → Build APK(s)
```

### Opción 3 — Línea de comandos

```bash
# Requiere JDK 17 y Android SDK
./gradlew assembleDebug

# El APK estará en:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📂 Estructura del proyecto

```
LupaFacil/
├── .github/
│   └── workflows/
│       └── build-apk.yml              # CI: compila el APK automáticamente
├── app/
│   ├── build.gradle                   # Dependencias y configuración
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/keinermaster/lupafacil/
│       │   ├── MainActivity.kt            # Pantalla principal
│       │   ├── CameraController.kt        # Controlador de cámara (CameraX)
│       │   ├── FloatingLensView.kt        # Vista de la lupa circular
│       │   ├── FloatingLensService.kt     # Servicio de lupa flotante (overlay)
│       │   ├── FloatingLensActivity.kt    # Configuración de lupa flotante
│       │   ├── PhotoLensActivity.kt       # Lupa sobre fotos
│       │   ├── ScreenLensActivity.kt      # Lupa de pantalla
│       │   ├── EasyModeActivity.kt        # Modo Fácil (3 botones)
│       │   ├── TutorialActivity.kt        # Tutorial de bienvenida
│       │   ├── HelpActivity.kt            # Pantalla de ayuda
│       │   ├── ColorModes.kt              # 6 modos de color
│       │   ├── TtsEngine.kt               # Motor de voz
│       │   ├── ImageDescriber.kt          # Descripción de imágenes
│       │   ├── FeedbackDisplay.kt         # Mensajes grandes temporales
│       │   ├── Prefs.kt                   # Preferencias persistentes
│       │   └── Vibration.kt               # Vibración
│       └── res/
│           ├── layout/                    # 8 layouts
│           ├── values/                    # strings, colors, themes
│           ├── drawable/                  # Iconos vectoriales
│           └── mipmap-anydpi-v26/         # Iconos adaptativos
├── build.gradle
├── settings.gradle
├── gradle.properties
└── gradle/wrapper/
```

---

## 🛠️ Tecnología

| Componente | Versión |
|------------|---------|
| **Lenguaje** | Kotlin 1.9.24 |
| **Android Gradle Plugin** | 8.5.2 |
| **Gradle** | 8.9 |
| **Java** | 17 |
| **compileSdk / targetSdk** | 34 |
| **minSdk** | 24 (Android 7.0) |

### Librerías principales

- **CameraX 1.3.4** — cámara moderna y estable
- **ML Kit Text Recognition 16.0.1** — OCR offline
- **Material 3** — componentes de UI
- **Coroutines 1.8.1** — programación asíncrona
- **Lifecycle 2.8.4** — gestión del ciclo de vida

---

## 📋 Requisitos

- **Android 7.0 (API 24)** o superior
- **Cámara** (opcional, para la lupa de cámara)
- **~50 MB** de espacio libre

### Permisos

| Permiso | Para qué |
|---------|----------|
| `CAMERA` | Lupa de cámara |
| `VIBRATE` | Confirmación táctil |
| `SYSTEM_ALERT_WINDOW` | Lupa flotante sobre otras apps |
| `FOREGROUND_SERVICE` | Servicio de lupa flotante |
| `POST_NOTIFICATIONS` | Notificación del servicio |

---

## ♿ Accesibilidad

Este proyecto sigue las pautas de accesibilidad para personas mayores:

- ✅ Objetivos táctiles de **92dp** (el doble del mínimo estándar de 48dp)
- ✅ **Icono + palabra** en cada botón (nunca solo un símbolo)
- ✅ **Confirmación por voz** de cada acción
- ✅ **Texto grande** (28-30sp) en toda la interfaz
- ✅ **Alto contraste** en los 6 modos de color
- ✅ **Alternativa a cada gesto** (zoom por botones *y* por pellizco)
- ✅ **Feedback visual inmediato** al tocar cualquier botón

---

## 🤝 Contribuir

Las contribuciones son bienvenidas. Si encuentras un bug o tienes una idea:

1. Abre un **Issue** describiendo el problema o la mejora
2. Haz un **Fork** del proyecto
3. Crea una rama (`git checkout -b feature/nueva-funcion`)
4. Haz commit de tus cambios (`git commit -m 'Añade nueva función'`)
5. Sube la rama (`git push origin feature/nueva-funcion`)
6. Abre un **Pull Request**

---

## 📄 Licencia

Este proyecto está bajo la licencia **MIT**. Consulta el archivo `LICENSE` para más detalles.

---

## 🙏 Agradecimientos

- **Dilcia Mercedes** — inspiración y razón de ser de este proyecto
- **Google** — por CameraX y ML Kit
- **Comunidad Android** — por las herramientas open source

---

<div align="center">

**Creada con ♥ por K3iiner — para Dilcia Mercedes**

*Porque ver mejor no debería ser un privilegio.*

</div>
