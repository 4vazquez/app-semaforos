# 🚦 Semáforos MX — El widget que despierta a los distraídos

## Versión piloto: Monterrey, Nuevo León 🇲🇽

### 💡 Idea principal
Semáforos MX nace de una realidad cotidiana:
la gente se distrae en el celular mientras el semáforo ya cambió a verde.
Este widget flotante busca evitar ese micro-caos urbano detectando cuándo el alto cambia y notificando al conductor con una alerta visual o sonora — sin necesidad de tocar el teléfono.

### ⚙️ ¿Cómo funciona?
- Se ejecuta como un widget flotante o overlay sobre cualquier app (WhatsApp, TikTok, Instagram, etc.).
- Usa datos de ubicación para detectar la luz del semáforo.
- Cuando la luz cambia a verde, el widget vibra, emite un sonido o muestra una alerta sutil.
- Opcionalmente, recopila datos anónimos sobre los tiempos de cambio de semáforos para futuras optimizaciones de tráfico.

### 🎯 Objetivo
- Reducir retrasos en los cruces urbanos.
- Disminuir el estrés y los pitos innecesarios en el tráfico.
- Promover el uso responsable del celular al volante.

### 🚧 Próximas fases
- **Versión Beta:** solo para Monterrey.
- **Versión Nacional:** incluir reconocimiento de semáforos en todo México.
- **Modo Premium:** sin anuncios, personalización de alertas, y estadísticas de eficiencia en conducción.

"Si no ves el verde, el verde te avisa." 💚

### 📂 Estructura del proyecto
- `index.html`, `script.js`, `style.css` — prototipo web original (Leaflet + OpenStreetMap).
- `export (1).geojson` — 1,579 semáforos del área metropolitana de Monterrey (OpenStreetMap, 2025-11-16).
- `android/` — app Android nativa (Kotlin + Jetpack Compose, MVP en desarrollo).
- `SPEC.md` — especificación del producto (MVP): alcance, flujos, modelo de datos, monetización y roadmap.
- `TERMINOS_Y_CONDICIONES.md` — términos de uso y exención de responsabilidad (borrador, pendiente de revisión legal antes del lanzamiento).

### 🧠 Créditos
- Idea original: Alan Vázquez
- Asistencia técnica y desarrollo conceptual: GPT-5
- Desarrollo del MVP Android: Jarvis (Muse Spark)
