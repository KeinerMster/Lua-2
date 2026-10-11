# Guía de contribución

¡Gracias por tu interés en mejorar **LupaFacil**! Este documento explica cómo
contribuir de forma efectiva.

---

## 🐛 Reportar un bug

Antes de abrir un issue, comprueba que:

1. No exista ya un issue similar
2. Estés usando la última versión
3. Hayas probado en un dispositivo real

Al abrir el issue, incluye:

- **Descripción clara** del problema
- **Pasos para reproducirlo**
- **Comportamiento esperado** vs. **comportamiento actual**
- **Dispositivo y versión de Android**
- **Capturas o logs** si es posible

---

## 💡 Sugerir una mejora

Abre un issue con la etiqueta `enhancement` y describe:

- **Qué problema resuelve** la mejora
- **Cómo la imaginas** (con detalle)
- **Por qué sería útil** para el usuario objetivo

---

## 🔧 Enviar código

### 1. Prepara el entorno

```bash
# Clona el repositorio
git clone https://github.com/KeinerMaster/LupaFacil-V2.git
cd LupaFacil-V2

# Abre en Android Studio (recomendado)
# O compila desde la línea de comandos:
./gradlew assembleDebug
```

### 2. Crea una rama

```bash
git checkout -b feature/nombre-descriptivo
# o
git checkout -b fix/nombre-del-bug
```

### 3. Sigue el estilo del código

- **Kotlin idiomático**: usa `val` en vez de `var` cuando sea posible
- **Nombres claros**: `zoomFactor` mejor que `zf`
- **Comentarios en español**: el proyecto está pensado para hispanohablantes
- **Sin código muerto**: elimina imports y variables sin usar
- **Manejo de errores**: usa `try/catch` en operaciones que puedan fallar

### 4. Prueba tus cambios

- Compila sin errores: `./gradlew assembleDebug`
- Prueba en un dispositivo real (no solo emulador)
- Verifica que la accesibilidad sigue funcionando

### 5. Haz commit

Usa mensajes claros y en español:

```bash
git commit -m "Añade lupa de fotos con zoom ajustable"
git commit -m "Corrige crash al abrir la cámara en Android 14"
git commit -m "Mejora el contraste del modo ámbar"
```

### 6. Sube y abre un Pull Request

```bash
git push origin feature/nombre-descriptivo
```

Luego abre un **Pull Request** en GitHub describiendo:

- **Qué cambia**
- **Por qué**
- **Cómo probarlo**

---

## 📐 Principios del proyecto

Este proyecto tiene un objetivo claro: **ser útil para personas mayores y con
baja visión**. Al contribuir, ten en cuenta:

### ✅ Hazlo simple
- Menos botones es mejor
- Lenguaje claro, sin tecnicismos
- Cada acción debe tener feedback visual y/o sonoro

### ✅ Prioriza la accesibilidad
- Botones grandes (mínimo 92dp)
- Alto contraste
- Icono + palabra, nunca solo un símbolo
- Confirmación por voz de las acciones

### ✅ Cuida el rendimiento
- No bloquees el hilo principal
- Libera recursos en `onDestroy`
- Reutiliza objetos en bucles de dibujo

### ❌ Evita
- Añadir funciones que compliquen la interfaz
- Dependencias innecesarias
- Código sin comentarios en zonas complejas

---

## 🧪 Áreas que necesitan ayuda

- [ ] **Tests unitarios** (no hay ninguno todavía)
- [ ] **Tests instrumentados** (UI)
- [ ] **Traducción** a otros idiomas
- [ ] **Capturas de pantalla** para el README
- [ ] **Iconos** más elaborados
- [ ] **Modo oscuro/claro** conmutable

---

## 📄 Licencia

Al contribuir, aceptas que tu código se publique bajo la licencia **MIT** del
proyecto.

---

<div align="center">

**¡Gracias por ayudar a que más personas puedan ver mejor!** ❤️

</div>
