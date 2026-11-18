# Semáforos MX — Especificación del producto (MVP)

> **Semáforos MX — El widget que despierta a los distraídos**
> *«Si no ves el verde, el verde te avisa.»*
>
> **Paquete Android:** `com.alan.semaforosmx`
> **Idea original:** Alan Vázquez · **Borrador inicial:** noviembre de 2025
> (prototipo web con asistencia de GPT-5) ·
> **Repositorio del borrador:** https://github.com/4vazquez/app-semaforos
> **Fecha de este documento:** 18 de noviembre de 2026 · **Versión:** 1.0 (MVP)

---

## 0. Estado del proyecto: qué ya existía vs qué es nuevo

Este MVP **no parte de cero**: Alan ya tenía un borrador (noviembre de 2025)
en https://github.com/4vazquez/app-semaforos. La app Android de este
documento se construye sobre ese borrador.

### Ya existía en el borrador
- **Nombre, concepto y eslogan**: «Semáforos MX — El widget que despierta a
  los distraídos». «Si no ves el verde, el verde te avisa.»
- **Prototipo web** (`index.html`, `script.js`, `style.css`): mapa Leaflet +
  OpenStreetMap centrado en Monterrey (25.6866, −100.3161, zoom 12), widget
  flotante arrastrable con slider de opacidad (0.2–1) y un simulador de
  cambio de luces (Rojo → Verde → Amarillo cada 5 s). La UX de la app
  Android espeja este prototipo: widget arrastrable, opacidad manual,
  marcadores rojos en el mapa.
- **Base de semáforos real**: `export (1).geojson` — **1 579 semáforos del
  área metropolitana de Monterrey** (lat 25.57–25.83, lon −100.50 a −100.00),
  exportados de OpenStreetMap vía overpass-turbo el 2025-11-16. Es la base
  semilla del mapa de la app (ver §2 y §6). Datos © OpenStreetMap
  contributors, licencia ODbL.
- **Fases definidas en el README del borrador**: Beta Monterrey → Nacional →
  Premium sin anuncios (ver §12).

### Nuevo en este MVP (app Android)
- App Android nativa (Kotlin + Jetpack Compose) con el widget flotante real
  (overlay `SYSTEM_ALERT_WINDOW`), arrastrable, con borde LED y cuenta
  regresiva basada en datos.
- Botón «Sync verde» y base de datos colaborativa de tiempos con algoritmo
  de agregación y confianza (mediana + rechazo de atípicos).
- Detección pasiva de arranque (acelerómetro/GPS) — diseño + stub.
- Monetización: banner AdMob + modo sin anuncios para conductores con 20+
  syncs al mes (programa Uber/DiDi).
- Importación del GeoJSON como base semilla dentro de la app
  (`assets/semaforos_mty.geojson`).

### Pendiente conocido del borrador
- El `script.js` del prototipo web tiene el código de inicialización del
  mapa duplicado y el `fetch` apunta a `semaforos.geojson`, pero el archivo
  en el repo se llama `export (1).geojson`. No se corrigió el borrador (no
  se toca el repo de Alan); la app Android usa el nombre real del archivo.

---

## 1. El problema

En un semáforo en rojo, muchos conductores bajan la vista al teléfono (redes
sociales, mensajes, música). Cuando el semáforo cambia a verde, tardan
segundos en reaccionar. Esos segundos no son gratis:

- **Por conductor:** 1–3 segundos perdidos por semáforo.
- **Por trayecto:** 15–20 semáforos entre casa y trabajo → 15–60 segundos
  por viaje, por conductor.
- **Por ciudad:** miles de conductores × decenas de semáforos = horas de
  tráfico agregado todos los días. En ingeniería de tráfico esto se conoce
  como *tiempo perdido de arranque* (*start-up lost time*): el retraso del
  primer vehículo de la fila se propaga a todos los que van detrás.

La app **no alienta el uso del teléfono al manejar**. Es un asistente de
conducción: avisa con una señal visual en el borde de su widget flotante
cuando el semáforo está por cambiar a verde, para que el conductor levante
la vista a tiempo. El mensaje de la app es siempre: *mira la calle, no la
pantalla*.

## 2. La solución

Una app Android que:

1. **Sabe dónde están los semáforos**: mapa de Monterrey con los **1 579
   semáforos reales** del GeoJSON de OpenStreetMap (base semilla del
   borrador de Alan), más los que agregue la comunidad.
2. **Conoce los tiempos de cada semáforo** por hora del día (base de datos
   colaborativa: los propios conductores corrigen los tiempos).
3. **Avisa antes del verde**: un widget flotante **arrastrable** con borde
   luminoso (estilo LED) en su contorno hace cuenta regresiva y parpadea
   cuando el semáforo está por cambiar a verde o acaba de cambiar. El
   conductor lo mueve a donde no le estorbe, como en el prototipo web.
4. **Aprende de la comunidad**: si el tiempo está desincronizado, el
   conductor presiona el botón **«Sync verde»** en el momento exacto del
   cambio a verde y la base de datos se corrige sola.

**Mercado inicial:** Monterrey, Nuevo León, México. Después: nacional, luego
internacional.

## 3. Alcance del MVP (lo que incluye esta versión)

| # | Función | Estado |
|---|---------|--------|
| 1 | Widget flotante (overlay) **arrastrable** con control manual de opacidad (slider) y borde LED con cuenta regresiva y parpadeo al verde | ✅ Implementado |
| 2 | Mapa de semáforos de Monterrey (OpenStreetMap, sin llave de pago) con los **1 579 semáforos reales** del GeoJSON como base semilla, marcadores rojos | ✅ Implementado |
| 3 | Botón «Sync verde» grande, de un toque, para corregir tiempos | ✅ Implementado |
| 4 | Detección pasiva de arranque (acelerómetro/GPS) | ⚠️ Diseñado, stub funcional (ver §7) |
| 5 | Base de datos colaborativa de tiempos + agregación con confianza | ✅ Modelo + agregación implementados; cliente de backend en stub |
| 6 | Monetización: descarga gratis, banner de anuncios, modo sin anuncios para conductores con 20+ syncs al mes | ✅ Esqueleto (AdMob con ID de prueba) |

**Fuera del MVP:** iOS, detección del semáforo por cámara con IA, voz
(«verde»), rutas/navegación, cuentas de usuario con contraseña, pagos.

## 4. Flujos de usuario

### 4.1 Primera ejecución (onboarding)
1. Pantalla de bienvenida: qué hace la app y el aviso de seguridad
   («mira la calle, no la pantalla»).
2. Permisos, en orden: ubicación (siempre, para detectar semáforos),
   superposición sobre otras apps (para el widget flotante), notificaciones.
3. Se muestra el mapa de Monterrey con los semáforos conocidos.
4. El usuario puede activar el widget flotante desde la pantalla principal.

### 4.2 Conductor detenido en un semáforo (flujo principal)
1. El GPS detecta que el usuario está detenido cerca de un semáforo
   registrado (< 30 m, velocidad ≈ 0).
2. El widget flotante aparece con el borde LED en rojo/ámbar y la cuenta
   regresiva estimada hasta el verde.
3. A 3 segundos del verde estimado, el borde parpadea en verde y suena una
   vibración corta (opcional, configurable).
4. Al llegar el verde, el borde queda verde fijo 2 segundos y el widget se
   minimiza.
5. Si el tiempo no coincidió, el conductor presiona **«Sync verde»** en el
   momento real del cambio. Ese reporte corrige la base de datos.

### 4.3 Flujo de Sync verde
1. El botón «Sync verde» está siempre visible en el widget y en la pantalla
   principal: grande (mínimo 96 dp), de un toque, sin confirmaciones.
2. Al presionarlo, la app registra: semáforo más cercano, hora exacta,
   precisión del GPS y si el reporte fue manual.
3. El reporte se envía al backend (o se encola si no hay red) y se refleja
   en el contador mensual del usuario.
4. Al llegar a 20 syncs en el mes, se desbloquea el modo sin anuncios
   hasta fin de mes.

### 4.4 Agregar un semáforo nuevo
1. En el mapa, toque prolongado sobre una intersección → «Agregar semáforo».
2. Se crea con confianza baja (0) hasta que acumule reportes.

## 5. Pantallas

| Pantalla | Contenido |
|----------|-----------|
| **Mapa** (principal) | Mapa de Monterrey, marcadores de semáforos (color según confianza), botón «Sync verde», botón para activar/desactivar el widget, contador de syncs del mes |
| **Widget flotante** | Borde LED, cuenta regresiva, botón «Sync verde», slider de opacidad |
| **Ajustes** | Opacidad por defecto, vibración, sonido, radio de detección, modo sin anuncios (estado), acerca de |
| **Onboarding** | Bienvenida, permisos, aviso de seguridad |

## 6. Modelo de datos

### 6.1 Semáforo (`traffic_lights`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | UUID | Identificador |
| `latitude` / `longitude` | double | Ubicación (6 decimales ≈ 11 cm) |
| `name` | String? | Nombre opcional (cruce: «Av. Lincoln / Ruiz Cortines») |
| `city` | String | «Monterrey» (permite crecer a otras ciudades) |
| `created_by` | UUID? | Usuario que lo agregó (opcional) |
| `created_at` | timestamp | Creación |

### 6.2 Reporte de tiempo (`timing_reports`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | UUID | Identificador |
| `light_id` | UUID | Semáforo |
| `reported_at` | timestamp | Momento exacto del verde (UTC) |
| `hour_slot` | int | Franja horaria 0–23 (los tiempos varían por hora) |
| `day_type` | enum | `WEEKDAY` / `SATURDAY` / `SUNDAY_HOLIDAY` |
| `source` | enum | `MANUAL_SYNC` (botón) / `PASSIVE` (sensor) |
| `gps_accuracy_m` | float | Precisión del GPS al reportar |
| `user_id` | UUID? | Anónimo por defecto (ver §10) |

### 6.3 Tiempos agregados (`light_timings`, calculado)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `light_id` + `hour_slot` + `day_type` | clave | Un registro por semáforo/franja/día |
| `green_offset_seconds` | int | Segundos desde el inicio del minuto en que suele llegar el verde (mediana) |
| `cycle_seconds` | int? | Duración estimada del ciclo, si se infiere |
| `sample_count` | int | Número de reportes usados |
| `confidence` | float 0–1 | Confianza (ver §6.4) |
| `updated_at` | timestamp | Última actualización |

### 6.4 Algoritmo de agregación y confianza

Los semáforos no cambian a la misma hora exacta todos los días: la mayoría
tiene ciclos fijos por franja horaria, pero con variaciones. Por eso el
sistema trabaja con **medianas por franja horaria**, no con promedios simples.

**Agregación (por semáforo + franja horaria + tipo de día):**
1. Tomar los reportes de los últimos 30 días.
2. Convertir cada reporte a «segundos dentro del ciclo» (módulo del ciclo
   estimado; si no hay ciclo estimado, segundos dentro de la hora).
3. **Rechazo de atípicos:** descartar valores fuera de ±1.5 × IQR
   (rango intercuartílico) respecto a la mediana. Un conductor que presionó
   tarde o temprano no contamina el dato.
4. **Mediana** de los valores restantes = tiempo estimado del verde.
5. **Confianza** (0–1), combinación de:
   - Cantidad de muestras: `min(1, n / 20)` — 20 reportes = confianza plena
     por volumen.
   - Dispersión: `1 - min(1, desviación_mediana_absoluta / 10 s)` —
     reportes agrupados = alta confianza.
   - Frescura: los reportes decaen con el tiempo (vida media 14 días).
   - `confianza = 0.5 × volumen + 0.3 × dispersión + 0.2 × frescura`
6. Umbrales de uso: confianza ≥ 0.6 → el widget muestra cuenta regresiva;
   0.3–0.6 → muestra «tiempo aproximado»; < 0.3 → no predice, solo invita
   a hacer sync.

El código de referencia está en `TimingAggregator.kt` y es idéntico en el
cliente (para cálculo local) y en el backend (para el cálculo oficial).

### 6.5 Contribución del usuario (`user_contributions`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `user_id` | UUID | Usuario (anónimo, generado en el dispositivo) |
| `month` | String | «2026-11» |
| `sync_count` | int | Syncs manuales en el mes |
| `ad_free_until` | date? | Fin del modo sin anuncios si `sync_count ≥ 20` |

## 7. Detección pasiva de arranque (diseño)

**Objetivo:** detectar que el coche arrancó sin que el conductor presione
nada, para generar reportes de tiempo automáticamente.

**Señales:**
- **Acelerómetro:** patrón de arranque (aceleración sostenida > 0.5 m/s²
  durante > 1.5 s después de estar detenido).
- **GPS:** velocidad pasa de < 1 km/h a > 10 km/h en menos de 5 segundos.
- **Contexto:** solo se considera si el usuario estaba detenido < 30 m de
  un semáforo registrado y el semáforo estaba en rojo estimado.

**Regla:** un reporte pasivo solo se genera si el arranque ocurre dentro de
la ventana de verde estimado ± 15 s. Si el conductor arranca fuera de esa
ventana (p. ej., dio vuelta en rojo o el semáforo estaba en verde desde
antes), no se reporta nada: un falso positivo es peor que un reporte
perdido.

**Estado en el MVP:** la clase `MovementDetector` existe como stub
funcional: expone la API (`start()`, `stop()`, callback
`onVehicleStarted`) y la lógica de validación por ventana, pero el
procesamiento real de sensores se activa en v1 (requiere pruebas de campo
para calibrar umbrales).

## 8. Stack tecnológico y justificación

| Capa | Elección | Por qué |
|------|----------|---------|
| Lenguaje/UI | **Kotlin + Jetpack Compose** (Material 3) | Estándar actual de Android; Compose acelera la UI del widget y el mapa |
| SDK | compileSdk 34, targetSdk 34, minSdk 26 (Android 8.0) | Cubre ~95% de dispositivos activos; minSdk 26 permite las APIs modernas de overlay y notificaciones |
| Mapa | **OpenStreetMap vía osmdroid** | **Decisión tomada (viene del borrador de Alan): OpenStreetMap, sin Google Maps ni llave de pago.** El prototipo web ya usaba Leaflet + OSM; en Android el equivalente es osmdroid: ligero, está en Maven Central, funciona con mosaicos de OSM y se integra a Compose con `AndroidView`. Google Maps quedó descartado: exige una cuenta con facturación activa incluso en capa gratuita. La base semilla de 1 579 semáforos es el GeoJSON de OSM del borrador (`assets/semaforos_mty.geojson`, licencia ODbL, con atribución en el mapa y en «Acerca de») |
| Backend | **Supabase** (Postgres + REST) | Capa gratuita generosa (500 MB de BD, 50 000 usuarios/mes). Postgres con PostGIS permite consultas geoespaciales («semáforos en un radio de 500 m») sin hacks; Firestore necesitaría GeoFirestore y tiene más lock-in. La API REST de Supabase no exige un SDK pesado: el cliente es Retrofit + OkHttp. La agregación puede correr como función de Postgres en v1 |
| Red | Retrofit 2 + OkHttp + Moshi | Estándar, ligero, sin sorpresas |
| Anuncios | AdMob (banner, ID de prueba en el MVP) | El estándar de monetización en Android; el esqueleto está listo para cambiar el ID de prueba por el real |
| Permisos | Ubicación, `SYSTEM_ALERT_WINDOW`, notificaciones | Los tres son imprescindibles para el flujo principal |

**Decisión explícita: sin cuentas en la nube en el MVP.** No se crea ningún
proyecto de Firebase/Supabase ni cuenta de AdMob en esta entrega: el código
trae un cliente de backend con implementación en memoria (funciona sin
servidor) y el ID de anuncio es el de prueba oficial de Google. Activar el
backend real es cambiar una línea (ver `README.md`).

## 9. Monetización

- **Descarga y uso: gratis.** Sin suscripción de pago en el MVP.
- **Banner de anuncios (AdMob):** banner en la parte inferior de la
  pantalla del mapa. Nunca dentro del widget flotante (distraería).
- **Programa de conductores (Uber/DiDi):** el conductor que aporte **20 o
  más syncs manuales en un mes** obtiene el mes sin anuncios. El conteo es
  mensual y se reinicia el día 1. Esto convierte a los conductores
  profesionales (que recorren toda la ciudad a diario) en la fuente
  principal de datos: 1 000 conductores activos = cobertura de toda
  Monterrey.
- **Futuro (fuera del MVP):** la base de datos de tiempos de semáforos
  agregada y anonimizada es el activo de mayor valor (ciudades, logística,
  apps de mapas). Los banners son el ingreso inicial; la data es el
  negocio a largo plazo.

## 10. Privacidad

- **Sin cuentas obligatorias:** el usuario es un UUID anónimo generado en
  el dispositivo. No se pide nombre, correo ni teléfono.
- **Ubicación:** se usa solo para detectar cercanía a semáforos y para
  registrar reportes. Los reportes guardan el semáforo (no la ubicación
  exacta del usuario) y la precisión del GPS.
- **Agregación:** los tiempos publicados son medianas de muchos reportes;
  ningún reporte individual es identificable en los datos agregados.
- **Retención:** los reportes crudos se conservan 90 días; los agregados,
  indefinidamente.
- **Transparencia:** la pantalla «Acerca de» explica qué datos se recogen
  y por qué. Cumplir con la Ley Federal de Protección de Datos Personales
  (México) antes del lanzamiento público: aviso de privacidad en la app y
  en la ficha de Play Store.

## 11. Seguridad vial (no negociable)

- El widget **nunca muestra contenido que invite a mirar la pantalla**:
  solo color y cuenta regresiva. Sin texto largo, sin imágenes, sin
  anuncios en el widget.
- La alerta es **visual periférica** (borde de pantalla) + vibración
  opcional. El conductor debe mirar la calle; la app solo le avisa que
  levante la vista.
- El botón «Sync verde» es de un toque y grande; no exige precisión ni
  lectura.
- El onboarding y la ficha de la tienda dicen explícitamente que la app
  no justifica usar el teléfono al manejar.

## 12. Roadmap por fases

Las fases siguen las que Alan definió en el README del borrador
(Beta Monterrey → Nacional → Premium sin anuncios).

### MVP (esta entrega)
Widget flotante arrastrable con opacidad manual y borde LED · Mapa de
Monterrey con los 1 579 semáforos reales del GeoJSON · Botón Sync verde ·
Agregación con confianza (local) · Esqueleto de anuncios + modo sin
anuncios por 20 syncs · Stub de detección pasiva.

### v1 — Beta Monterrey (primer lanzamiento público)
Backend Supabase real (tablas + PostGIS + función de agregación, con el
GeoJSON importado a `traffic_lights`) · Detección pasiva calibrada con
pruebas de campo · Onboarding completo con permisos · AdMob con ID real ·
Publicación en Play Store (cuenta de desarrollador de Alan).

### v2 — Crecimiento
Alertas por voz («verde») · Detección por cámara con IA (opcional, solo
con el teléfono en soporte) · Estadísticas del conductor (tiempo ahorrado)
· Programa formal con comunidades de Uber/DiDi · Ciudades adicionales de
México (CDMX, Guadalajara).

### v3 — Nacional / Premium
Cobertura nacional · **Modo Premium**: sin anuncios, personalización de
alertas y estadísticas de eficiencia en conducción (según el README del
borrador) · Localización (portugués, inglés) · API de datos agregados para
ciudades y logística · Modo pasajero (sin widget de conducción).
