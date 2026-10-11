# ============================================================
#  LupaFacil - Crea TODOS los archivos actualizados
#  Uso:  Abre PowerShell en el Escritorio y ejecuta:
#        powershell -ExecutionPolicy Bypass -File crear_todo.ps1
# ============================================================

$base = "C:\Users\Keiner\Desktop\LupaFacil_Kotlin"

Write-Host "=== Creando proyecto LupaFacil en $base ===" -ForegroundColor Cyan

# --- Carpetas ---
$carpetas = @(
    "$base\app\src\main\java\com\keinermaster\lupafacil",
    "$base\app\src\main\res\layout",
    "$base\app\src\main\res\values",
    "$base\app\src\main\res\drawable",
    "$base\app\src\main\res\mipmap-anydpi-v26",
    "$base\gradle\wrapper",
    "$base\.github\workflows"
)
foreach ($c in $carpetas) { New-Item -ItemType Directory -Force -Path $c | Out-Null }

# ============================================================
#  ARCHIVOS RAIZ
# ============================================================

Set-Content -Path "$base\settings.gradle" -Encoding UTF8 -Value @'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LupaFacil"
include ':app'
'@

Set-Content -Path "$base\build.gradle" -Encoding UTF8 -Value @'
plugins {
    id 'com.android.application' version '8.5.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.24' apply false
}
'@

Set-Content -Path "$base\gradle.properties" -Encoding UTF8 -Value @'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
'@

Set-Content -Path "$base\gradle\wrapper\gradle-wrapper.properties" -Encoding UTF8 -Value @'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
'@

Set-Content -Path "$base\.gitignore" -Encoding UTF8 -Value @'
*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
local.properties
app/build/
'@

# ============================================================
#  APP
# ============================================================

Set-Content -Path "$base\app\build.gradle" -Encoding UTF8 -Value @'
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.keinermaster.lupafacil'
    compileSdk 34

    defaultConfig {
        applicationId "com.keinermaster.lupafacil"
        minSdk 24
        targetSdk 34
        versionCode 2
        versionName "1.1"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }

    buildFeatures {
        viewBinding true
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

    def camerax_version = "1.3.4"
    implementation "androidx.camera:camera-core:$camerax_version"
    implementation "androidx.camera:camera-camera2:$camerax_version"
    implementation "androidx.camera:camera-lifecycle:$camerax_version"
    implementation "androidx.camera:camera-view:$camerax_version"

    implementation 'com.google.mlkit:text-recognition:16.0.1'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.8.4'
}
'@

Set-Content -Path "$base\app\proguard-rules.pro" -Encoding UTF8 -Value @'
-keep class com.keinermaster.lupafacil.** { *; }
'@

Write-Host "Archivos base creados." -ForegroundColor Green
Write-Host ""
Write-Host "IMPORTANTE: los archivos .kt y .xml los pegas desde el chat." -ForegroundColor Yellow
Write-Host "O pide el script completo con todos los archivos incluidos." -ForegroundColor Yellow
