# ============================================================
#  LupaFacil - Crea todos los archivos actualizados
#  Ejecutar en PowerShell:  .\crear_archivos.ps1
# ============================================================

$base = "C:\Users\Keiner\Desktop\LupaFacil_Kotlin"

Write-Host "Creando archivos en: $base" -ForegroundColor Cyan

# --- Crear carpetas ---
$carpetas = @(
    "$base\app\src\main\java\com\keinermaster\lupafacil",
    "$base\app\src\main\res\layout",
    "$base\app\src\main\res\values",
    "$base\app\src\main\res\drawable",
    "$base\app\src\main\res\mipmap-anydpi-v26",
    "$base\gradle\wrapper",
    "$base\.github\workflows"
)
foreach ($c in $carpetas) {
    New-Item -ItemType Directory -Force -Path $c | Out-Null
}

Write-Host "Carpetas creadas." -ForegroundColor Green
Write-Host ""
Write-Host "Ahora copia los archivos que te doy en el chat." -ForegroundColor Yellow
Write-Host "O usa el script completo que te proporciono." -ForegroundColor Yellow
