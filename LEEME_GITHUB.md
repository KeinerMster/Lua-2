# LupaFacil (Kotlin) — Compilar APK con GitHub

Esta carpeta está lista para subir a GitHub y que **GitHub Actions compile el APK por ti**.
No necesitas instalar Android Studio, ni Java, ni el SDK. Solo subir y esperar.

---

## 🚀 Pasos (5 minutos)

### 1. Crea un repositorio en GitHub
- Ve a https://github.com/new
- Nombre: `LupaFacil` (o el que quieras)
- Público o privado (da igual)
- NO marques "Add README"
- Crea el repo

### 2. Sube esta carpeta
**Opción A — desde la web:**
1. En tu repo vacío, pulsa **"uploading an existing file"**
2. Arrastra **TODO el contenido** de esta carpeta (incluida la carpeta `.github`)
3. Commit

> ⚠️ La carpeta `.github/workflows/build-apk.yml` DEBE subirse.
> Si no puedes subirla arrastrando, créala a mano:
> **"Add file" → "Create new file"** → escribe `.github/workflows/build-apk.yml`
> → pega el contenido del archivo → Commit.

**Opción B — con Git:**
```bash
cd LupaFacil_Kotlin
git init
git add .
git commit -m "LupaFacil Kotlin listo para compilar"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/LupaFacil.git
git push -u origin main
```

### 3. Espera a que compile
- Ve a la pestaña **Actions**
- Verás "Build Android APK" ejecutándose (tarda ~5-10 min, mucho menos que Kivy)
- Cuando termine con ✅ verde, entra en el run
- Abajo, en **Artifacts**, descarga **`LupaFacil-debug-apk`**
- Descomprime el .zip → dentro está el **`.apk`**

### 4. Instala en el Android
- Pasa el `.apk` al teléfono/tablet
- Activa "Instalar apps de origen desconocido"
- Instala y abre

---

## ✅ Por qué esta versión SÍ funciona

| Antes (Python/Kivy) | Ahora (Kotlin nativo) |
|---|---|
| Buildozer frágil, fallaba por licencias SDK | Gradle estándar, sin problemas de licencias |
| Solo compilaba arm64-v8a | APK universal (todas las arquitecturas) |
| `config.py` con `\n` literales → crash | Código Kotlin compilado, sin errores de sintaxis |
| ML Kit mal integrado → crash | ML Kit como dependencia Gradle oficial |
| Cámara con Kivy (inestable) | CameraX (estándar de Google) |
| ~40 min de build | ~5-10 min de build |

---

## 📱 Compatibilidad

- **Android 7.0 (API 24) en adelante** — cubre prácticamente todos los dispositivos
- **Todas las arquitecturas** (32 y 64 bits) en un solo APK
- Cámara (CameraX), voz (TTS nativo), vibración, OCR (ML Kit offline)

---

## 🐛 Si algo falla

**El build falla en GitHub Actions:**
- Abre el run fallido y mira el paso en rojo
- Copia el error y pásamelo

**El APK instala pero se cierra:**
- Pásame el error de `adb logcat` o una descripción

**La cámara sale negra:**
- Ajustes → Apps → LupaFacil → Permisos → Cámara → Permitir

---

## 📂 Estructura

```
LupaFacil_Kotlin/
├── .github/workflows/build-apk.yml   ← GitHub compila aquí
├── build.gradle / settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/keinermaster/lupafacil/
        │   ├── MainActivity.kt
        │   ├── ColorModes.kt
        │   ├── TtsEngine.kt
        │   ├── Prefs.kt
        │   └── Vibration.kt
        └── res/
            ├── layout/
            ├── values/
            ├── drawable/
            └── mipmap-anydpi-v26/
```

---

**Creada con ♥ K3iiner — para Dilcia Mercedes**
