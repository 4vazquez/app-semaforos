# Semáforos MX — Especificación del producto (MVP)

> **Semáforos MX — El widget que despierta a los distraídos**
> *«Si no ves el verde, el verde te avisa.»*
>
> **Paquete Android:** `com.alan.semaforosmx`
> **Idea original:** Alan Vázquez · **Borrador inicial:** noviembre de 2025
> (prototipo web con asistencia de GPT-5) ·
> **Repositorio del borrador:** https://github.com/4vazquez/app-semaforos
> **Fecha de este documento:** 18 de noviembre de 2026 · **Versión:** 1.1 (MVP)
>
> **Cambios en v1.1 (18 nov 2026):** botones de corrección de mapa
> («Aquí hay un semáforo» y «Ya no existe»), paquete anti-abuso completo
> (§6.7), tres niveles de monetización (§9), pantalla de aceptación de
> Términos y Condiciones v1.1 (§4.6, §10) y detección pasiva conectada al
> widget (§7).

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
  de agregación y confianza (mediana ponderada por confianza del usuario +
  rechazo de atípicos).
- **Correcciones de mapa (tres acciones, misma lógica de confirmación):**
  «Sync verde» (tiempos), «Aquí hay un semáforo» (agrega un semáforo
  faltante en la ubicación GPS y registra el sync) y «Ya no existe»
  (reporta un semáforo retirado; se oculta con 3 confirmaciones).
- **Paquete anti-abuso** (§6.7): verificación GPS (≤ 100 m, fix fresco y
  preciso), detección de ubicación simulada, rechazo de saltos imposibles,
  cruce con el sensor de movimiento, una cuenta por dispositivo, reputación
  (trust) por usuario, semáforos nuevos UNVERIFIED (grises, sin alertas) y
  tope de 5 semáforos nuevos por usuario por día.
- Detección pasiva de arranque (acelerómetro/GPS) conectada al widget: el
  arranque solo cuenta como reporte dentro de la ventana de verde ± 15 s.
- Monetización en tres niveles: Gratis (con banner), Colaborador (gratis y
  sin anuncios con 20+ actualizaciones en semáforos distintos al mes) y
  Plus (suscripción de pago, solo el flag/stub en el MVP; se lanza después).
- Términos y Condiciones v1.1: pantalla de aceptación en la primera
  apertura («Acepto» obligatorio) y relectura desde Ajustes. El texto es
  un borrador pendiente de revisión legal antes del lanzamiento público.
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
   cambio a verde y la base de datos se corrige sola. Y el mapa se
   autocompleta: si falta un semáforo, el conductor presiona **«Aquí hay
   un semáforo»** (lo agrega en su ubicación GPS y registra el sync); si
   un semáforo ya no existe, lo reporta con **«Ya no existe»**. Ningún
   cambio de mapa se da por hecho con un solo reporte: todo se confirma
   con varios conductores independientes (§6.7).

**Mercado inicial:** Monterrey, Nuevo León, México. Después: nacional, luego
internacional.

## 3. Alcance del MVP (lo que incluye esta versión)

| # | Función | Estado |
|---|---------|--------|
| 1 | Widget flotante (overlay) **arrastrable** con control manual de opacidad (slider) y borde LED con cuenta regresiva y parpadeo al verde | ✅ Implementado |
| 2 | Mapa de semáforos de Monterrey (OpenStreetMap, sin llave de pago) con los **1 579 semáforos reales** del GeoJSON como base semilla; marcadores rojos (verificados) y grises (sin verificar) | ✅ Implementado |
| 3 | Botones grandes de un toque: «Sync verde» (corregir tiempos) y «Aquí hay un semáforo» (agregar faltante + sync) | ✅ Implementado |
| 4 | «Ya no existe»: reportar un semáforo retirado (detalle del marcador y botón en la app); se oculta con 3 confirmaciones | ✅ Implementado |
| 5 | Detección pasiva de arranque (acelerómetro/GPS), conectada al widget; solo cuenta dentro de la ventana de verde ± 15 s | ✅ Implementada (umbrales por calibrar con pruebas de campo, ver §7) |
| 6 | Base de datos colaborativa de tiempos + agregación con confianza (mediana ponderada por trust) | ✅ Modelo + agregación implementados; cliente de backend en stub |
| 7 | Paquete anti-abuso: verificación GPS, anti-mock, anti-salto, una cuenta por dispositivo, trust, UNVERIFIED, tope diario | ✅ Implementado (cliente; la aplicación en servidor es v1, ver §6.7) |
| 8 | Monetización en tres niveles: Gratis / Colaborador / Plus (stub) | ✅ Esqueleto (AdMob con ID de prueba; Plus sin flujo de pago) |
| 9 | Términos y Condiciones v1.1: aceptación en primera apertura + relectura en Ajustes | ✅ Implementado (texto = borrador, pendiente de revisión legal) |
| 10 | Menú inferior dentro de la app (Mapa / Mis aportes / Alertas / Ajustes) + pantalla «Mis aportes» (progreso X/20, estado Colaborador, trust, historial) + widget por proximidad en segundo plano con dos modos (completo para Colaborador/Plus, solo LED para Gratis) | ✅ Implementado (v0.2.0) |

**Fuera del MVP:** iOS, detección del semáforo por cámara con IA, voz
(«verde»), rutas/navegación, cuentas de usuario con contraseña, pagos
(Plus se integra en v1+), flujo de eliminación de cuenta en la app (v1).

## 4. Flujos de usuario

### 4.1 Primera ejecución (onboarding)
1. **Términos y Condiciones (obligatorio):** resumen en lenguaje sencillo
   de las cláusulas clave + texto completo (v1.1). El usuario debe
   presionar **«Acepto»** para usar la app; «No acepto» cierra la app.
   La aceptación se guarda localmente (versión + fecha/hora). Si los
   términos cambian de versión, hay que aceptarlos de nuevo.
2. Pantalla de bienvenida: qué hace la app y el aviso de seguridad
   («mira la calle, no la pantalla»).
3. Permisos, en orden: ubicación (siempre, para detectar semáforos),
   superposición sobre otras apps (para el widget flotante), notificaciones.
4. Se muestra el mapa de Monterrey con los semáforos conocidos.
5. El usuario puede activar el widget flotante desde la pestaña
   **Alertas** del menú inferior.

### 4.2 Conductor detenido en un semáforo (flujo principal)
1. El servicio en segundo plano detecta por GPS que hay un semáforo
   verificado cerca (≤ 150 m). El widget flotante APARECE en un borde de
   la pantalla: barra vertical completa si el usuario es Colaborador/Plus
   (botones + opacidad) o píldora solo-LED si es usuario normal. Si no
   hay semáforo a ≤ 200 m, el widget se oculta (histéresis 150/200 m
   para no parpadear en el límite). El widget nunca se muestra mientras
   la app está abierta: dentro de la app está el menú.
2. El borde LED muestra rojo/ámbar y la cuenta regresiva estimada hasta
   el verde.
3. A 3 segundos del verde estimado, el borde parpadea en verde y suena una
   vibración corta (opcional, configurable).
4. Al llegar el verde, el borde queda verde fijo 2 segundos y el widget se
   oculta al alejarte del cruce.
5. Si el tiempo no coincidió, el conductor Colaborador presiona
   **«Sync verde»** en el momento real del cambio (o cualquier usuario
   lo hace desde la pestaña Mapa de la app). Ese reporte corrige la base
   de datos.

### 4.3 Flujo de Sync verde
1. El botón «Sync verde» está siempre visible en el widget y en la pantalla
   principal: grande (mínimo 96 dp), de un toque, sin confirmaciones.
2. Al presionarlo, la app obtiene un fix GPS fresco y verifica que el
   usuario está en el semáforo más cercano (≤ 100 m, fix < 30 s, precisión
   ≤ 50 m, sin ubicación simulada). Si no pasa la verificación, el reporte
   se rechaza con un mensaje amable (§6.7).
3. Se registra: semáforo más cercano, hora exacta, precisión del GPS y si
   el reporte fue manual.
4. El reporte se envía al backend (o se encola si no hay red) y cuenta
   para la cuota Colaborador **solo si fue aceptado**. Los syncs de tiempo
   son ilimitados (el dato fresco es el feed en vivo), pero la cuota
   cuenta **semáforos distintos al mes** (§6.5).
5. Al llegar a 20 semáforos distintos en el mes, el usuario es Colaborador:
   sin anuncios gratis hasta fin de mes.

### 4.4 Agregar un semáforo nuevo («Aquí hay un semáforo»)
El conductor está detenido en un semáforo que la app no conoce:
1. Presiona el botón naranja **«Aquí hay un semáforo»** (igual de grande y
   de un toque que «Sync verde»), en el widget o en la pantalla principal.
2. La app obtiene un fix GPS fresco y preciso (obligatorio: nada de
   ubicaciones en caché) y crea el semáforo **en esa ubicación**, en estado
   **UNVERIFIED** (marcador gris, sin alertas), **y** registra el sync de
   verde de ese momento (el conductor lo presiona al cambiar a verde).
3. **Deduplicación:** si ya hay un semáforo a menos de 30 m, no se crea
   un duplicado: el reporte se convierte en un sync sobre el existente
   (y lo confirma si estaba UNVERIFIED).
4. **Confirmación:** el semáforo pasa a VERIFIED (marcador rojo, con
   alertas) cuando **3 conductores distintos** reportan tiempo en él.
5. **Anti-spam:** un usuario solo puede reportar cada semáforo **una vez**
   (para siempre). Si lo intenta de nuevo, ve: «Este semáforo ya se envió
   a revisión, ¡manda otro!».
6. **Tope diario:** máximo **5 semáforos nuevos por usuario por día**.
7. Alternativa sin sync: toque prolongado en el mapa → «Agregar semáforo
   aquí» (mismo estado UNVERIFIED, mismo tope diario, sin reporte de
   tiempo; la ubicación es el punto tocado, no el GPS).

### 4.5 Reportar un semáforo que ya no existe («Ya no existe»)
La app muestra un semáforo que fue retirado en la vida real:
1. El conductor toca el marcador en el mapa (detalle del semáforo) o el
   botón pequeño **«¿Ya no existe?»** en la pantalla principal.
2. La app verifica el GPS contra ese semáforo (≤ 100 m, fix fresco y
   preciso, sin ubicación simulada): hay que estar ahí para reportarlo.
3. **Anti-spam:** un usuario solo puede reportar cada semáforo **una vez**.
   Si ya lo reportó, el botón aparece deshabilitado y ve el mensaje
   «Este semáforo ya se envió a revisión, ¡manda otro!».
4. Los reportes se acumulan por semáforo. Al llegar a **3 conductores
   distintos**, el semáforo se **oculta** del mapa (no se borra: queda en
   la base para auditoría y restauración). El detalle muestra cuántos
   conductores lo han reportado («2 de 3»).
5. Un retiro reportado cuenta para la cuota Colaborador como una
   actualización en ese semáforo (distinto).

### 4.6 Términos y Condiciones
- **Primera apertura:** pantalla de aceptación (ver §4.1, paso 1).
- **Relectura:** Ajustes → «Términos y Condiciones» reabre el texto
  completo (modo solo lectura, con botón «Cerrar»).
- El texto es el archivo `TERMINOS_Y_CONDICIONES.md` del repo (v1.1),
  copiado verbatim a `assets/terminos_y_condiciones.md` para que la app
  lo muestre. ⚠️ **Es un borrador: debe revisarlo un abogado antes del
  lanzamiento público** (ver §10).

## 5. Pantallas

El menú inferior (Mapa / Mis aportes / Alertas / Ajustes) existe SOLO
dentro de la app abierta. Cuando la app está cerrada no hay menú en
pantalla: el widget flotante trabaja en segundo plano y aparece solo
cuando hay un semáforo cerca.

| Pantalla | Contenido |
|----------|-----------|
| **Mapa** (principal) | Mapa de Monterrey, marcadores rojos (verificados) y grises (sin verificar), botones «Sync verde» y «Aquí hay un semáforo», botón «¿Ya no existe?», contador de actualizaciones del mes (semáforos distintos), banner de anuncios (solo Gratis) |
| **Mis aportes** | Progreso del mes: semáforos distintos aportados, X/20, con barra · Estado Colaborador (activo o no, y hasta cuándo: fin de mes) · Nivel de confianza (puntaje + nivel + conteos) · Historial de aportes (syncs, agregados, retiros, del más reciente al más antiguo) |
| **Alertas** | Activar/desactivar el widget en segundo plano · Tu modo de widget (completo o solo LED, según tu nivel, con la explicación de cómo desbloquear el completo) · Vista previa del LED (así se ve la alerta a 3 s del verde) |
| **Detalle de semáforo** (diálogo) | Nombre del cruce, estado (verificado / sin verificar), reportes de retiro acumulados («2 de 3»), acción «Reportar que ya no existe» |
| **Widget flotante** | Barra vertical compacta (120 dp de ancho, un tercio de la pantalla como máximo, en el borde derecho, arrastrable) o píldora solo-LED (160 × 72 dp, arriba al centro). FULL (Colaborador/Plus): borde LED, cuenta regresiva, botones «Sync verde» y «Aquí hay un semáforo», slider de opacidad. LED_ONLY (Gratis): solo el borde LED con la cuenta regresiva. Aparece a ≤ 150 m de un semáforo verificado, se oculta a > 200 m, y nunca se muestra con la app abierta |
| **Ajustes** | Vibración al avisar, Términos y Condiciones (reabre el texto), Acerca de (qué datos se recogen + atribución OSM) |
| **Términos y Condiciones** | Resumen en lenguaje sencillo + texto completo v1.1; en primera apertura, botones «Acepto» / «No acepto»; desde Ajustes, solo lectura |
| **Onboarding** | Bienvenida, permisos, aviso de seguridad (después de aceptar términos) |

## 6. Modelo de datos

### 6.1 Semáforo (`traffic_lights`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | UUID | Identificador |
| `latitude` / `longitude` | double | Ubicación (6 decimales ≈ 11 cm) |
| `name` | String? | Nombre opcional (cruce: «Av. Lincoln / Ruiz Cortines») |
| `city` | String | «Monterrey» (permite crecer a otras ciudades) |
| `status` | enum | `VERIFIED` (en el mapa, con alertas) / `UNVERIFIED` (gris, sin alertas, pendiente de 3 confirmaciones) / `REMOVED` (oculto, 3 reportes de retiro) |
| `reported_by` | UUID? | Usuario anónimo que lo reportó (solo UNVERIFIED; sirve para el anti-spam) |
| `created_by` | UUID? | Usuario que lo agregó (opcional) |
| `created_at` | timestamp | Creación |

La base semilla (GeoJSON de OSM) entra como `VERIFIED`. Todo semáforo
agregado por un usuario empieza `UNVERIFIED`.

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
4. **Mediana ponderada por confianza:** cada reporte pesa según la
   confianza (trust) del usuario que lo envió (§6.7): un conductor con
   historial confirmado pesa más que un usuario nuevo (0.5 neutral, 1.0
   máximo). Sin pesos, se comporta como la mediana clásica.
5. **Confianza** (0–1), combinación de:
   - Cantidad de muestras: `min(1, muestra_efectiva / 20)` — la muestra
     efectiva es la suma de los pesos (20 reportes de peso completo =
     confianza plena por volumen).
   - Dispersión: `1 - min(1, desviación_mediana_absoluta / 10 s)` —
     reportes agrupados = alta confianza.
   - Frescura: los reportes decaen con el tiempo (vida media 14 días).
   - `confianza = 0.5 × volumen + 0.3 × dispersión + 0.2 × frescura`
6. Umbrales de uso: confianza ≥ 0.6 → el widget muestra cuenta regresiva;
   0.3–0.6 → muestra «tiempo aproximado»; < 0.3 → no predice, solo invita
   a hacer sync. **Los semáforos UNVERIFIED nunca sirven alertas**,
   tengan los reportes que tengan, hasta ser verificados.

La agregación además devuelve qué reportes fueron aceptados y cuáles
rechazados como atípicos, para que el repositorio ajuste la confianza de
cada usuario **una sola vez por reporte** (si no, el widget recalcularía
la confianza cada segundo con la misma evidencia).

El código de referencia está en `TimingAggregator.kt` y es idéntico en el
cliente (para cálculo local) y en el backend (para el cálculo oficial).

### 6.5 Contribución del usuario (`user_contributions`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `user_id` | UUID | Usuario (anónimo, ligado al dispositivo; ver §6.7) |
| `month` | String | «2026-11» |
| `light_ids` | text[] | Semáforos distintos a los que aportó en el mes |
| `update_count` | int | `light_ids` distintos (derivado). Con 20+ el usuario es Colaborador (sin anuncios, gratis) |

**La cuota cuenta semáforos distintos, no reportes:** cada actualización
(sync verde, semáforo agregado o retiro reportado) suma 1 al contador de
ese semáforo, pero repetir el mismo cruce no suma de nuevo. Así no se puede
farmear el Colaborador en un solo semáforo. Los reportes pasivos
(detección de arranque) NO cuentan para la cuota: el programa premia la
contribución activa.

### 6.6 Reporte de retiro (`removal_reports`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | UUID | Identificador |
| `light_id` | UUID | Semáforo reportado como retirado |
| `reported_at` | timestamp | Momento del reporte |
| `user_id` | UUID? | Usuario anónimo (uno por semáforo, para siempre) |

Un semáforo se oculta (`status = REMOVED`) al acumular reportes de **3
usuarios distintos**. Nunca se borra: queda para auditoría y restauración.

### 6.7 Anti-abuso

El mapa y los tiempos los construye la comunidad, así que la app asume que
alguien intentará farmear recompensas o ensuciar los datos. Reglas
aprobadas por Alan (todas implementadas en el cliente del MVP; **en v1 se
replican en el servidor con triggers/funciones, porque un tope solo en el
cliente se salta con una app modificada**):

| # | Regla | Implementación |
|---|-------|----------------|
| 1 | **Verificación GPS:** un sync o reporte solo cuenta si el GPS confirma que el usuario está a **≤ 100 m** del semáforo, con un fix **fresco (< 30 s)** y **preciso (≤ 50 m)** | `GpsVerification.check()` antes de aceptar; «Aquí hay un semáforo» crea el semáforo EN el fix actual, así que la proximidad es inherente pero la frescura/precisión se exige igual |
| 2 | **Ubicación simulada (mock):** se rechazan los reportes con fix marcado como mock por Android | `Location.isFromMockProvider`, en la verificación y en la obtención del fix |
| 3 | **Salto imposible:** dos reportes del mismo usuario que implican moverse a más de **180 km/h** → el segundo se rechaza | El repositorio guarda el último reporte por usuario (semáforo + momento) y calcula la velocidad implícita |
| 4 | **Cruce con el sensor de movimiento:** si el coche está detenido (velocidad GPS < 1 km/h) pero el GPS salta más de **100 m en menos de 30 s**, el fix no es fiable y el reporte se rechaza | El widget compara sus dos últimos fixes con el estado de `MovementDetector` |
| 5 | **Una cuenta por dispositivo:** el ID de usuario se deriva del ANDROID_ID con hash SHA-256 (el ID crudo nunca sale del teléfono). Reinstalar recupera el mismo ID. Los reportes del mismo dispositivo nunca cuentan como confirmaciones independientes | `UserIdentity` |
| 6 | **Reputación (trust):** cada usuario tiene una confianza 0.1–1.0 que empieza neutral (0.5). Los reportes confirmados por la multitud suben (+0.03); los rechazados como atípicos bajan (−0.05). La agregación pondera por confianza (§6.4). El mínimo es 0.1, no 0: cualquiera puede recuperarse | `UserTrust` + `TimingAggregator` (mediana ponderada) + ajuste en el repositorio |
| 7 | **Semáforos nuevos UNVERIFIED:** todo semáforo agregado por un usuario empieza gris y **sin alertas** hasta que 3 conductores distintos lo confirmen | `LightStatus.UNVERIFIED`; el widget solo sirve alertas de VERIFIED |
| 8 | **Tope diario:** máximo **5 semáforos nuevos por usuario por día** (botón y toque prolongado comparten el contador) | `MapCorrection.MAX_NEW_LIGHTS_PER_DAY` + `canSubmitNewLight()` |
| 9 | **Un reporte de corrección por semáforo por usuario:** agregar o reportar retirado el mismo semáforo solo se puede una vez, para siempre. Al reintentarlo: «Este semáforo ya se envió a revisión, ¡manda otro!» | `reportedBy` en el semáforo + `removal_reports` por usuario |
| 10 | **Cuota por semáforos distintos:** el Colaborador requiere 20 semáforos distintos al mes, no 20 reportes (§6.5). Los syncs de tiempo siguen siendo ilimitados | `user_contributions.light_ids` |

Los mensajes de rechazo son genéricos a propósito («Reporte no registrado:
la ubicación no es fiable»): no se le explica al abusador exactamente qué
falló. Los mensajes de verificación GPS (demasiado lejos, ubicación vieja
o imprecisa) sí son específicos, porque son problemas honestos del
conductor.

### 6.8 Confianza del usuario (`user_trust`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `user_id` | UUID | Usuario anónimo |
| `trust_score` | float | 0.1–1.0, empieza en 0.5 (neutral) |
| `confirmed_count` | int | Reportes confirmados por la multitud |
| `rejected_count` | int | Reportes rechazados como atípicos |

En el MVP la confianza vive en el repositorio en memoria; en v1 es una
tabla de Supabase que la función de agregación consulta para ponderar.

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

**Estado en el MVP:** `MovementDetector` está conectado al widget flotante:
registra acelerómetro + GPS, detecta el arranque y genera el reporte
pasivo solo si cae en la ventana de verde ± 15 s (con deduplicación: un
reporte pasivo por semáforo cada 60 s como máximo). Los reportes pasivos
NO cuentan para la cuota Colaborador (el programa premia la contribución
activa). La calibración fina de umbrales requiere pruebas de campo y
queda para v1.

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

Tres niveles:

1. **Gratis:** app completa, con banner de anuncios (AdMob) en la parte
   inferior de la pantalla del mapa. Nunca dentro del widget flotante
   (distraería al conductor). En segundo plano el widget es **solo LED**:
   el borde luminoso con la cuenta regresiva, sin botones ni opacidad.
2. **Colaborador (GRATIS, sin banners, nunca se cobra):** el usuario que
   aporta **20+ actualizaciones en semáforos distintos en un mes** (syncs
   verdes, semáforos agregados o retiros reportados) usa la app sin
   anuncios gratis. Su pago son los datos que aporta. Este es el programa
   para comunidades de conductores Uber/DiDi: 1 000 conductores activos
   = cobertura de toda Monterrey. El conteo es mensual y se reinicia el
   día 1. Además, su widget en segundo plano es el **completo**: botones
   «Sync verde» y «Aquí hay un semáforo» + slider de opacidad. El modo
   se re-evalúa cada 30 s y tras cada aporte: al llegar a los 20, el
   widget pasa a completo sin reiniciar nada.
3. **Plus (suscripción de pago, se lanza DESPUÉS con buena base de
   usuarios):** para usuarios regulares que no contribuyen — sin anuncios
   + extras (alertas personalizadas, estadísticas de manejo). **En el MVP
   no hay flujo de pago:** solo existe el flag `AdsManager.isPlusUser()`
   (stub, siempre false) al que la integración de Google Play Billing se
   conectará en v1+.

**Futuro (fuera del MVP):** la base de datos de tiempos de semáforos
agregada y anonimizada es el activo de mayor valor (ciudades, logística,
apps de mapas). Los banners son el ingreso inicial; la data es el negocio
a largo plazo. Los términos (§6 Ter) ya contemplan el uso de datos
agregados y anonimizados, incluso para ofrecer información agregada a
terceros, sin identificar a usuarios individuales.

## 10. Privacidad y legal

- **Sin cuentas obligatorias:** el usuario es un ID anónimo ligado al
  dispositivo (hash SHA-256 del ANDROID_ID; el ID crudo nunca sale del
  teléfono). No se pide nombre, correo ni teléfono. Una cuenta por
  dispositivo (ver §6.7, regla 5).
- **Ubicación:** se usa solo para detectar cercanía a semáforos y para
  validar reportes. Los reportes guardan el semáforo (no la ubicación
  exacta del usuario) y la precisión del GPS.
- **Agregación:** los tiempos publicados son medianas ponderadas de muchos
  reportes; ningún reporte individual es identificable en los datos
  agregados.
- **Retención:** los reportes crudos se conservan 90 días; los agregados,
  indefinidamente.
- **Contenido del usuario:** al enviar reportes, el usuario otorga a
  Semáforos MX una licencia para usarlos de forma agregada y anonimizada
  como parte del servicio (Términos §6 Ter).
- **Términos y Condiciones v1.1** (`TERMINOS_Y_CONDICIONES.md`, copiado a
  `assets/terminos_y_condiciones.md`): el conductor es el único
  responsable; la app es una ayuda informativa, no sustituye la atención
  ni el semáforo real; sin responsabilidad por accidentes, daños o
  multas; los datos colaborativos pueden ser inexactos; los mapas son
  OpenStreetMap (ODbL, no son nuestros); ley mexicana, tribunales de
  Monterrey. Aceptación obligatoria en la primera apertura (§4.6).
  ⚠️ **El texto es un borrador y DEBE revisarlo un abogado antes del
  lanzamiento público** (en México no puede excluirse la responsabilidad
  por dolo o negligencia grave, y PROFECO puede declarar abusivas
  ciertas cláusulas en contratos de adhesión).
- **Pendiente para el lanzamiento público:** Aviso de Privacidad completo
  en la app y en la ficha de Play Store (Ley Federal de Protección de
  Datos Personales, México); flujo de eliminación de cuenta y datos desde
  la app (los términos §7 ya prometen que el usuario puede solicitarlo).

## 11. Seguridad vial (no negociable)

- El widget **nunca muestra contenido que invite a mirar la pantalla**:
  solo color y cuenta regresiva. Sin texto largo, sin imágenes, sin
  anuncios en el widget.
- La alerta es **visual periférica** (borde de pantalla) + vibración
  opcional. El conductor debe mirar la calle; la app solo le avisa que
  levante la vista.
- El botón «Sync verde» es de un toque y grande; no exige precisión ni
  lectura.
- **No interactuar con la app mientras el vehículo está en movimiento:**
  los términos (§3) lo prohíben y la app está diseñada para consultarse
  de un vistazo cuando el vehículo está detenido.
- El onboarding y la ficha de la tienda dicen explícitamente que la app
  no justifica usar el teléfono al manejar.

## 12. Roadmap por fases

Las fases siguen las que Alan definió en el README del borrador
(Beta Monterrey → Nacional → Premium sin anuncios).

### MVP (esta entrega)
Widget flotante arrastrable con opacidad manual y borde LED · Mapa de
Monterrey con los 1 579 semáforos reales del GeoJSON (rojos/grises) ·
Botones Sync verde y «Aquí hay un semáforo» · «Ya no existe» con 3
confirmaciones · Agregación con confianza (mediana ponderada por trust) ·
Paquete anti-abuso (cliente) · Detección pasiva conectada · Monetización
en tres niveles (Plus como stub) · Términos v1.1 con aceptación obligatoria.

### v1 — Beta Monterrey (primer lanzamiento público)
Backend Supabase real (tablas + PostGIS + función de agregación, con el
GeoJSON importado a `traffic_lights`) · **Anti-abuso aplicado en el
servidor** (triggers/funciones: tope diario, saltos, transiciones de
estado, trust) · Detección pasiva calibrada con pruebas de campo ·
Onboarding completo con permisos · AdMob con ID real · Revisión legal de
los términos + Aviso de Privacidad + eliminación de cuenta en la app ·
Publicación en Play Store (cuenta de desarrollador de Alan) · **Al
publicar en Play, el actualizador propio se apaga y la actualización la
maneja Play con la In-App Updates API (flexible/inmediata)** — ver §13.

### v2 — Crecimiento
Alertas por voz («verde») · Detección por cámara con IA (opcional, solo
con el teléfono en soporte) · Estadísticas del conductor (tiempo ahorrado)
· Programa formal con comunidades de Uber/DiDi · Ciudades adicionales de
México (CDMX, Guadalajara) · **Android Auto (idea de Alan):** que el
contorno de la pantalla del coche parpadee el color del semáforo 2–3
segundos antes de que cambie a verde, para que el conductor se ponga
alerta. Restricción de la plataforma: Android Auto NO permite overlays
arbitrarios ni dibujar sobre el borde de la pantalla — el sistema es dueño
del marco y Google restringe la Car App Library a plantillas aprobadas.
El enfoque factible en v2 es una app de Android Auto en categoría
navegación/conducción con superficie de dibujo propia (SurfaceRenderer de
la plantilla de navegación), donde el borde luminoso se dibuja DENTRO del
área de la app: mismo efecto visual para el conductor (borde parpadeando
2–3 s antes del verde), sujeto a la revisión de Google para apps de
conducción. No es alcance del MVP ni de v1.

### v3 — Nacional / Premium
Cobertura nacional · **Plus** (suscripción de pago, si aún no se lanzó en
v1/v2): sin anuncios, personalización de alertas y estadísticas de
eficiencia en conducción (según el README del borrador) · Localización
(portugués, inglés) · API de datos agregados para ciudades y logística ·
Modo pasajero (sin widget de conducción).

## 13. Actualización automática (sideload)

El usuario nunca tiene que descargar e instalar el APK a mano: la app se
actualiza sola con un toque (idea de Alan, v0.3.0).

### 13.1 Cómo funciona

- **Fuente:** los releases de GitHub del repo
  (https://github.com/4vazquez/app-semaforos). Al arrancar, la app
  consulta `GET /repos/4vazquez/app-semaforos/releases/latest` y compara
  el tag del release (formato obligatorio `vX.Y.Z`, igual que el
  `versionName`) con la versión instalada. Si el tag es mayor, hay
  actualización.
- **Frecuencia:** la red se consulta como máximo una vez al día (la hora
  del último chequeo persiste). Sin red o con error de la API, falla en
  silencio: no hay diálogo ni crash.
- **Aviso:** diálogo en la app — «Hay una nueva versión (vX.Y.Z)» con las
  notas del release — y botones «Actualizar» y «Más tarde». «Más tarde»
  silencia esa versión 24 h; una versión distinta vuelve a avisar.
- **Un toque:** «Actualizar» descarga el APK del asset del release (con
  barra de progreso) y lanza el instalador del sistema. Android pide la
  confirmación final; la app nunca instala sola. El archivo se verifica
  (no vacío) antes de instalar y los parciales se borran.
- **Permiso de instalar:** en Android 8+, instalar desde fuera de Play
  requiere el permiso `REQUEST_INSTALL_PACKAGES`, que el usuario concede
  por app en Ajustes («Permitir desde esta fuente»). Si falta, la app lo
  explica y abre los Ajustes; al volver, la descarga se reanuda sola.

### 13.2 La regla de Google Play (no negociable)

**Google Play PROHÍBE que una app distribuida en Play se actualice desde
fuera de Play.** Por eso el actualizador está detrás del flag
`BuildConfig.SELF_UPDATE_ENABLED`: `true` en el build debug/sideload,
`false` en el build release/Play (ver `app/build.gradle.kts`). Al
publicar en Play, la actualización la maneja Play con la **In-App
Updates API** (modo flexible: descarga en segundo plano y aplica al
reiniciar; modo inmediato: pantalla completa que bloquea hasta actualizar).
Implementar la In-App Updates API es un pendiente de v1, en el momento de
la publicación en Play; no se implementa ahora porque el piloto se
distribuye por APK directo.

### 13.3 Publicar una nueva versión

1. Subir `versionCode` y `versionName` en `app/build.gradle.kts`.
2. Compilar el APK debug y verificarlo (`./gradlew assembleDebug`).
3. Push a GitHub y crear un release con el tag `vX.Y.Z` (igual que el
   `versionName`) adjuntando el `app-debug.apk` como asset.
4. Los usuarios con la app instalada reciben el aviso al abrirla (como
   máximo una vez al día) y actualizan con un toque.
