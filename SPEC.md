# Vía Verde — Especificación del producto (MVP)

> **Vía Verde — El widget que despierta a los distraídos**
> *«Si no ves el verde, Vía Verde te avisa.»*
>
> **Paquete Android:** `com.alan.semaforosmx`
> **Idea original:** Alan Vázquez · **Borrador inicial:** noviembre de 2025
> (prototipo web con asistencia de GPT-5) ·
> **Repositorio del borrador:** https://github.com/4vazquez/app-semaforos
> **Fecha de este documento:** 19 de noviembre de 2026 · **Versión:** 1.4
>
> **Cambios en v1.4 (19 nov 2026):** app v1.1.1 — sello de verificación
> junto al nombre (§6.9): palomita azul «Propietario» para el Admin
> (solo Alan) y palomita verde «Colaborador verificado» para los
> conductores con licencia de chofer verificada; el usuario común no
> muestra sello. Solo presentación: los permisos reales se hacen
> cumplir en Supabase con RLS. Ranking semanal de Colaboradores
> diseñado (§6.10, SQL `supabase/004_ranking.sql`): top 3 con premios
> de $300/$200/$100 por semana, opt-in con apodo; la UI llega en
> v1.2. App v1.1.2 — LED en degradado: el borde luminoso del widget es
> intenso en la orilla y se difumina hacia el centro (línea nítida +
> banda de luz que se apaga hacia adentro). App v1.1.5 — renombrada a
> «Vía Verde» en toda la app y los documentos (Términos v1.7, Aviso
> v1.2, con la cláusula de reducción de daño en Términos §3 y en el
> resumen de la pantalla «Acepto»); el paquete interno
> com.alan.semaforosmx y el correo adminsemaforosmx@gmail.com no
> cambian). App v1.1.6 — confirmación visible en el widget al reportar
> («Aquí hay un semáforo» y «Sync verde» muestran aviso; antes solo
> parpadeaba el LED). App v1.1.3 — Términos v1.4
> y Aviso de Privacidad v1.1: reglas del ranking semanal (§7 Ter),
> verificación de licencia (dato sensible, se borra tras revisarla),
> términos de la suscripción Plus y notificación de brechas (LFPDPPP
> art. 20). Siguen pendientes de revisión de un abogado antes del
> lanzamiento público. App v1.1.4 — Aviso de Privacidad visible en la
> app (desde la pantalla «Acepto» y desde Ajustes), Términos v1.5
> (tope de responsabilidad con piso de $5,000 MXN en vez de cero,
> impugnación de descalificaciones del ranking en 15 días) y
> consentimiento expreso documentado para la foto de la licencia
> (§6.9, LFPDPPP art. 9). App v1.1.0 — login con Google
> (Credential Manager / Google ID) y backend Supabase implementados:
> sesión cifrada, roles `USER` / `COLLABORATOR` / `ADMIN` en la tabla
> `users`, puertas en la app según el rol (§4.7, §6.9), esquema SQL con
> RLS y triggers anti-abuso en `supabase/001_schema.sql`, seed de
> 1 579 semáforos. Pendiente: crear el proyecto Supabase y el cliente
> OAuth de Google (ver `supabase/README.md`); sin esa configuración la
> app sigue en modo local anónimo.
>
> **Cambios en v1.3 (19 nov 2026):** app v1.0.0 — logo nuevo y renovación
> visual (esquema «semáforo» claro/oscuro), botón «Mi ubicación» en el
> mapa. Los roles y el login con Google y el backend Supabase seguían
> pendientes de implementación: la app v1.0.0 sigue siendo anónima y
> local, sin cuentas.
>
> **Cambios en v1.2 (18 nov 2026):** roles y permisos (Usuario común /
> Colaborador / Admin, §6.9), login híbrido con Google (§4.7),
> `user_id` ligado al login en v1 (§6.5, §6.7, §6.8), Términos y
> Condiciones v1.3 y Aviso de Privacidad v1.0 (§10). Los roles y el login
> son trabajo de backend en v1: la app v0.3.1 sigue siendo anónima y
> local, sin cuentas.
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
- **Nombre, concepto y eslogan**: «Vía Verde — El widget que despierta a
  los distraídos». «Si no ves el verde, Vía Verde te avisa.»
- **Filosofía de reducción de daño**: no fomentamos mirar el teléfono al
  conducir; es un hábito que ya existe en los semáforos. Vía Verde
  minimiza ese daño con alertas breves y periféricas, para que el
  conductor pase menos segundos en la pantalla y más tiempo viendo la
  calle. (También en Términos §3.)
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
- Términos y Condiciones v1.5: pantalla de aceptación en la primera
  apertura («Acepto» obligatorio) y relectura desde Ajustes. El texto es
  un borrador pendiente de revisión legal antes del lanzamiento público.
  La v1.3 añade cuentas, roles y login con Google (§7 Bis) y el Aviso de
  Privacidad v1.0 (`AVISO_DE_PRIVACIDAD.md`).
- **Roles y login híbrido (documentados en v1.2 del SPEC, trabajo de
  backend en v1, NO en la app v0.3.1):** Usuario común sin login (mapa +
  alerta), Colaborador con login de Google (aporta y ve solo sus
  aportes) y Admin (solo Alan, privilegios totales, panel aparte). Ver
  §4.7 y §6.9. La app v0.3.1 sigue siendo anónima y local. El rol se
  llama oficialmente **Colaborador** (nunca «socio» en texto visible al
  usuario o legal: «socio» implica derechos sobre utilidades, que el
  programa no otorga — ver Términos §7 Bis).
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
| 9 | Términos y Condiciones v1.5: aceptación en primera apertura + relectura en Ajustes | ✅ Implementado (texto = borrador, pendiente de revisión legal) |
| 10 | Menú inferior dentro de la app (Mapa / Mis aportes / Alertas / Ajustes) + pantalla «Mis aportes» (progreso X/20, estado Colaborador, trust, historial) + widget por proximidad en segundo plano con dos modos (completo para Colaborador/Plus, solo LED para Gratis) | ✅ Implementado (v0.2.0) |

**Fuera del MVP:** iOS, detección del semáforo por cámara con IA, voz
(«verde»), rutas/navegación, cuentas de usuario con contraseña, pagos
(Plus se integra en v1+), flujo de eliminación de cuenta en la app (v1).

## 4. Flujos de usuario

### 4.1 Primera ejecución (onboarding)
1. **Términos y Condiciones (obligatorio):** resumen en lenguaje sencillo
   de las cláusulas clave + texto completo (v1.4). El usuario debe
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
- El texto es el archivo `TERMINOS_Y_CONDICIONES.md` del repo (v1.4),
  copiado verbatim a `assets/terminos_y_condiciones.md` para que la app
  lo muestre. ⚠️ **Es un borrador: debe revisarlo un abogado antes del
  lanzamiento público** (ver §10).
- El §12 nombra el correo de contacto del equipo
  (adminsemaforosmx@gmail.com), publicado también en Ajustes → Contacto.

### 4.7 Login híbrido con Google (v1, aprobado por Alan el 18 nov 2026)

El uso básico no requiere cuenta; aportar sí. Así no se espanta al
usuario común y a la vez se construye la base de usuarios que aportan,
con su rol, su confianza y sus aportes (§6.9).

- **Sin login:** ver el mapa y recibir la alerta del verde. Nada más.
  También puede hacer Sync verde desde la pestaña Mapa de la app; ese
  sync alimenta la agregación de tiempos pero, al no haber cuenta, no
  cuenta para la cuota Colaborador ni aparece en «Mis aportes».
- **Con login (Google, SSO):** obligatorio para hacer sync desde el
  widget, agregar o reportar semáforos, ser Colaborador y tener Plus.
- **Qué recibe la app:** con Google Sign-In NO se ve la contraseña del
  usuario. Llega: nombre, correo, foto (si tiene) e identificador de
  Google. Eso basta para la base de usuarios (`users`, ver §6.9).
- **Una cuenta por login:** en v1 el `user_id` de Supabase se liga al
  login de Google, no al ANDROID_ID anónimo del MVP (§6.7, regla 5).
  Reinstalar con el mismo Google recupera la misma cuenta, sus aportes
  y su confianza. Un mismo login de Google puede usarse en varios
  dispositivos; lo que sigue prohibido es tener varias cuentas para
  manipular los datos (Términos §8).
- **Eliminación de cuenta:** el usuario puede borrar su cuenta y sus
  datos personales desde la app (Ajustes → Eliminar cuenta), como exige
  Play. Al borrarla se borran nombre/correo/foto/ID de Google; sus
  reportes previos quedan solo como datos agregados y anonimizados (no
  identificables).
- **Migración desde el MVP:** los reportes anónimos del MVP (ID ligado
  al dispositivo) se archivan sin identidad o se migran como datos
  agregados; no se pueden ligar retroactivamente a una cuenta de Google.

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
| **Ajustes** | Vibración al avisar, Términos y Condiciones (reabre el texto), Contacto (correo del equipo para copiar o escribir con asunto precargado), Acerca de (qué datos se recogen + atribución OSM) |
| **Términos y Condiciones** | Resumen en lenguaje sencillo + texto completo v1.4; en primera apertura, botones «Acepto» / «No acepto»; desde Ajustes, solo lectura |
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
| `reported_by` | UUID? | Usuario que lo reportó (con login en v1; anónimo ligado al dispositivo en el MVP; solo UNVERIFIED; sirve para el anti-spam) |
| `created_by` | UUID? | Usuario que lo agregó (opcional; con login en v1) |
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
| `user_id` | UUID? | Usuario con login en v1; nulo para syncs sin login desde el Mapa y para reportes del MVP anónimo (ver §6.9 y §10) |

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
| `user_id` | UUID | Usuario con login de Google en v1 (ver §4.7 y §6.9); en el MVP era el ID anónimo ligado al dispositivo (ver §6.7) |
| `month` | String | «2026-11» |
| `light_ids` | text[] | Semáforos distintos a los que aportó en el mes |
| `update_count` | int | `light_ids` distintos (derivado). Con 20+ el usuario es Colaborador (sin anuncios, gratis) |

**La cuota cuenta semáforos distintos, no reportes:** cada actualización
(sync verde, semáforo agregado o retiro reportado) suma 1 al contador de
ese semáforo, pero repetir el mismo cruce no suma de nuevo. Así no se puede
farmear el Colaborador en un solo semáforo. Los reportes pasivos
(detección de arranque) NO cuentan para la cuota: el programa premia la
contribución activa. Tampoco cuentan los syncs sin login desde el Mapa
(§4.7): sin cuenta no hay cuota que acreditar.

### 6.6 Reporte de retiro (`removal_reports`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | UUID | Identificador |
| `light_id` | UUID | Semáforo reportado como retirado |
| `reported_at` | timestamp | Momento del reporte |
| `user_id` | UUID? | Usuario con login en v1 (uno por semáforo, para siempre); anónimo ligado al dispositivo en el MVP |

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

**Cambio en v1 (regla 5):** la identidad deja de ser el hash del
ANDROID_ID y pasa a ser el login de Google (§4.7, §6.9). La regla pasa a
ser **una cuenta por login de Google**: un mismo login puede usarse en
varios dispositivos, y lo que sigue prohibido es crear varias cuentas
para manipular los datos. Los reportes anónimos del MVP se archivan sin
identidad y no se ligan retroactivamente a ninguna cuenta.

### 6.8 Confianza del usuario (`user_trust`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `user_id` | UUID | Usuario con login de Google en v1; ID anónimo ligado al dispositivo en el MVP |
| `trust_score` | float | 0.1–1.0, empieza en 0.5 (neutral) |
| `confirmed_count` | int | Reportes confirmados por la multitud |
| `rejected_count` | int | Reportes rechazados como atípicos |

En el MVP la confianza vive en el repositorio en memoria; en v1 es una
tabla de Supabase que la función de agregación consulta para ponderar.
El `user_id` es la clave foránea a `users` (§6.9).

### 6.9 Roles y permisos (v1, aprobado por Alan el 18 nov 2026)

Tres roles, con privilegios mínimos (cada rol solo ve y hace lo que
ocupa). **El rol de aporte se llama oficialmente «Colaborador»** en
todo texto visible al usuario y legal; «socio» no se usa ahí porque en
español implica un socio de negocio con derechos sobre utilidades, que
el programa no otorga (ver Términos §7 Bis).

| Capacidad | Usuario común (sin login) | Colaborador (login Google) | Admin (Alan, solo él) |
|---|---|---|---|
| Ver mapa y recibir alerta del verde | ✅ sí | ✅ sí | ✅ sí |
| Hacer Sync verde | ✅ sí, desde la app (Mapa) | ✅ sí, también desde el widget | ✅ sí |
| Agregar semáforo («Aquí hay un semáforo») | ❌ no | ✅ sí | ✅ sí |
| Reportar «Ya no existe» | ❌ no | ✅ sí | ✅ sí |
| Ver sus propios aportes y progreso (X/20, confianza) | ❌ no | ✅ solo los suyos | ✅ los suyos y los de todos |
| Widget completo (botones + opacidad) | ❌ solo LED | ✅ al llegar a 20 distintos/mes | ✅ sí |
| Aprobar / rechazar semáforos nuevos | ❌ no | ❌ no | ✅ solo admin |
| Ocultar / restaurar semáforos | ❌ no | ❌ no | ✅ solo admin |
| Ver reportes, confianza de usuarios y métricas globales | ❌ no | ❌ no | ✅ solo admin |
| Moderar / suspender cuentas | ❌ no | ❌ no | ✅ solo admin |
| Ver ingresos, datos de otros usuarios o datos globales | ❌ no | ❌ no | ✅ solo admin |

**El Colaborador NO es empleado, socio, asociado, agente ni accionista
de Vía Verde.** Su participación es voluntaria, sin horario,
exclusividad ni subordinación; el ÚNICO beneficio es el uso sin
anuncios mientras cumpla las condiciones del programa (20 semáforos
distintos al mes), sin valor en efectivo, sin participación en ingresos
o utilidades, sin equity e intransferible. El programa y sus
condiciones pueden modificarse o terminarse, con aviso dentro de la app
(Términos §7 Bis).

**Regla de oro (v1, servidor):** la separación se hace cumplir en
Supabase (Row Level Security + funciones), no solo escondiendo botones
en la app. En el MVP todo vive local y anónimo, así que hoy cualquiera
podría brincárselo; por eso los roles son trabajo de backend en v1, no
del MVP. El panel de admin es **aparte de la app pública** (no va dentro
del APK que descargan los usuarios).

**Tabla `users` (v1, nueva):**

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | UUID | `user_id` de Supabase (clave de `user_contributions`, `user_trust`, reportes) |
| `google_id` | String | Identificador de Google (SSO); la contraseña de Google nunca se recibe ni se guarda |
| `email` | String | Correo de la cuenta de Google |
| `display_name` | String? | Nombre de la cuenta de Google |
| `photo_url` | String? | Foto de perfil de Google (si la tiene) |
| `role` | enum | `USER` (común, con login) / `COLLABORATOR` / `ADMIN` (solo Alan) |
| `nickname` | String? | Apodo para el ranking semanal (§6.10); único, solo si el usuario quiere aparecer |
| `show_in_ranking` | boolean | `true` solo si el usuario activó aparecer en el ranking (opt-in, por defecto `false`) |
| `created_at` | timestamp | Creación de la cuenta |

Un usuario común que inicia sesión con Google pero aún no aporta es
`USER`; al aportar sigue siendo `USER` con aportes registrados, y el
estado Colaborador (sin anuncios) se deriva de la cuota mensual de §6.5,
no es un rol distinto en la base de datos. El rol `COLLABORATOR` marca
a los conductores del programa (p. ej. comunidades Uber/DiDi) que
aportan de forma regular.

**Cómo se gana el rol COLLABORATOR (híbrido, aprobado por Alan el
18 nov 2026):**
1. **Beneficio sin anuncios (automático):** cualquier usuario con login
   que aporte en 20 semáforos distintos en el mes es Colaborador sin
   anuncios (§6.5). No necesita rol ni verificación.
2. **Sello de conductor verificado (rol COLLABORATOR):** el usuario
   sube una foto de su licencia de chofer (tipo C/D en Nuevo León, la
   categoría del piloto; otras categorías de otros estados después) y
   el Admin la revisa. Al aprobarla, el rol pasa a `COLLABORATOR`.
   La foto es un dato sensible: se guarda en un bucket privado de
   Supabase Storage (solo el dueño y el Admin pueden verla), se revisa
   una sola vez y se borra tras la verificación; solo queda guardado el
   estado «verificado», la fecha y el tipo de licencia, nunca la imagen.
   Al subir la foto, el usuario otorga su **consentimiento expreso**
   marcando la casilla «Autorizo el tratamiento de la foto de mi
   licencia para verificar mi condición de conductor» (LFPDPPP art. 9:
   los datos sensibles exigen consentimiento expreso); la app guarda la
   fecha y hora de ese consentimiento (`consent_at`). Sin la casilla
   marcada, la foto no se envía.
   Un rechazo no bloquea el uso de la app: el usuario sigue aportando y
   puede ganar el sin anuncios por cuota, y puede volver a solicitar
   la verificación.

### 6.10 Ranking semanal de Colaboradores (diseño aprobado por Alan el 19 nov 2026)

Los conductores se aburren de su día a día: el ranking les da un motivo
para aportar seguido, como **reconocimiento**, no como competencia
formal («sin definir como competencia»).

- **Ciclo semanal:** de lunes 00:00 a domingo 23:59 (America/Monterrey).
- **Métrica:** semáforos distintos aportados en la semana. Cuentan los
  mismos aportes que cuentan para la cuota de §6.5: syncs manuales con
  login y GPS verificado (§6.7: en el lugar, fix fresco, sin mock
  location). Un semáforo cuenta una sola vez por semana aunque se
  sincronice varias veces. **Sin tope:** más aportes, más puntos.
- **La cuota mensual de 20 distintos (§6.5) es independiente:** es lo
  que conserva el beneficio sin anuncios (Colaborador). El ranking no
  la sustituye ni la cambia. (Ojo con el nombre: el beneficio que se
  conserva con 20/mes es el sin anuncios de Colaborador, NO el Plus —
  Plus es la suscripción de pago que llega después, §9.)
- **Top 3 y premios:** 1er lugar $300 MXN, 2do $200, 3er $100, en
  premios (boletos de cine, recargas u otros de valor equivalente, a
  elegir por el Admin). Total **$600/semana (~$2,400/mes)** como
  inversión del proyecto, pagada por Alan en el piloto.
- **Empates:** desempata por mayor confianza (§6.8) y luego por el
  primer aporte de la semana más temprano.
- **Visibilidad opt-in:** solo aparecen los usuarios que activaron
  «aparecer en el ranking» y eligieron un apodo. Sin opt-in no aparece
  nadie. Lo único visible es el apodo; nunca el nombre real ni el
  correo.
- **Cierre y entrega:** el ranking cierra el domingo a las 23:59; el
  Admin revisa a los 3 ganadores y marca cada premio como entregado. La
  entrega es manual al principio (Alan contacta al ganador). Todo queda
  registrado en `weekly_prizes`. Si hay menos de 3 participantes con
  aportes verificados, solo se premian los lugares con ganador real; si
  nadie aporta en la semana, no hay premios esa semana.
- **Anti-trampa:** cuentan solo los aportes verificados (§6.7). El
  Admin puede descalificar a un usuario de la semana (y del programa si
  reincide) si detecta farming; la descalificación queda registrada con
  motivo, y el usuario puede impugnarla por correo dentro de los 15 días
  siguientes (Términos §7 Ter).
- **Legal:** es un reconocimiento por mérito (quien más aporta, gana),
  no un sorteo: no hay azar. Las reglas van en los Términos (pendiente
  de revisión del abogado, como el resto del documento).
- **En la app:** Mis aportes muestra el top 3 de la semana en curso y
  tu lugar (si estás en el top 10 y tienes opt-in). La UI es trabajo
  de v1.2 (necesita el backend vivo).
- **Sostenibilidad:** $600/semana lo paga Alan en el piloto; a futuro,
  patrocinios o ajuste del monto según usuarios activos. El programa
  puede modificarse o terminarse con aviso en la app.

**Tabla `weekly_prizes` (v1, nueva):**

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | UUID | PK |
| `week_start` | date | Lunes de la semana |
| `user_id` | UUID | Ganador |
| `place` | int | 1, 2 o 3 |
| `amount_cents` | int | 30000 / 20000 / 10000 |
| `prize_description` | text | Qué se entregó (p. ej. «2 boletos de cine») |
| `status` | enum | `PENDING` / `CONTACTED` / `DELIVERED` / `DISQUALIFIED` |
| `decided_at` | timestamp | Cuándo se resolvió |
| `decided_by` | UUID | Admin que lo resolvió |
| `notes` | text | Motivo de descalificación u otras notas |

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

- **Cuentas (v1):** el uso básico (mapa + alerta del verde) no requiere
  cuenta. Para aportar (sync desde el widget, agregar o reportar
  semáforos), ser Colaborador o tener Plus, el usuario inicia sesión con
  su cuenta de Google (§4.7). Con Google Sign-In se reciben nombre,
  correo, foto (si la tiene) e identificador de Google; **la contraseña
  de Google nunca se recibe ni se guarda**. En el MVP el usuario es un
  ID anónimo ligado al dispositivo (hash SHA-256 del ANDROID_ID; el ID
  crudo nunca sale del teléfono); en v1 ese ID se sustituye por el login
  de Google (ver §6.7, regla 5, y §6.9).
- **Ubicación:** se usa solo para detectar cercanía a semáforos y para
  validar reportes. Los reportes guardan el semáforo (no la ubicación
  exacta del usuario) y la precisión del GPS.
- **Agregación:** los tiempos publicados son medianas ponderadas de muchos
  reportes; ningún reporte individual es identificable en los datos
  agregados.
- **Retención:** los reportes crudos se conservan 90 días; los agregados,
  indefinidamente.
- **Contenido del usuario:** al enviar reportes, el usuario otorga a
  Vía Verde una licencia para usarlos de forma agregada y anonimizada
  como parte del servicio (Términos §6 Ter).
- **Finalidades del uso de datos (v1.4):** los datos de la cuenta se
  tratan solo para las finalidades listadas en el Aviso de Privacidad:
  operar la App, mejorar los tiempos, prevenir fraude y abuso, avisar de
  cambios, estadísticas agregadas y anonimizadas y soporte. Los datos
  personales identificables **no se venden ni se comparten sin
  consentimiento expreso aparte**. No existe una cláusula de uso «a
  conveniencia»: la LFPDPPP exige finalidades específicas.
- **Términos y Condiciones v1.5** (`TERMINOS_Y_CONDICIONES.md`, copiado a
  `assets/terminos_y_condiciones.md`): el conductor es el único
  responsable; la app es una ayuda informativa, no sustituye la atención
  ni el semáforo real; sin responsabilidad por accidentes, daños o
  multas; los datos colaborativos pueden ser inexactos; los mapas son
  OpenStreetMap (ODbL, no son nuestros); cuentas, roles y login con
  Google (§7 Bis: el Colaborador no es empleado ni socio y su único
  beneficio es el uso sin anuncios); ley mexicana, tribunales de
  Monterrey. Aceptación obligatoria en la primera apertura (§4.6).
  ⚠️ **El texto es un borrador y DEBE revisarlo un abogado antes del
  lanzamiento público** (en México no puede excluirse la responsabilidad
  por dolo o negligencia grave, y PROFECO puede declarar abusivas
  ciertas cláusulas en contratos de adhesión).
- **Aviso de Privacidad v1.0** (`AVISO_DE_PRIVACIDAD.md`, 18 nov 2026):
  responsable y contacto (adminsemaforosmx@gmail.com), datos recabados,
  finalidades, transferencias (solo procesadores del servicio: Google
  Sign-In, Supabase, Play/AdMob), retención de 90 días, derechos ARCO y
  eliminación de cuenta. ⚠️ **También es un borrador pendiente de
  revisión legal**; falta integrarlo dentro de la app y en la ficha de
  Play Store antes del lanzamiento público.
- **Pendiente para el lanzamiento público:** revisión legal de Términos
  v1.7 + Aviso de Privacidad v1.2; Aviso de Privacidad en la ficha de
  Play Store (ya está visible dentro de la app desde v1.1.4, en la
  pantalla «Acepto» y en Ajustes); flujo de eliminación de cuenta y
  datos desde la app (los términos §7 y §7 Bis ya prometen que el
  usuario puede solicitarlo).

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
en tres niveles (Plus como stub) · Términos v1.5 con aceptación obligatoria.

### v1 — Beta Monterrey (primer lanzamiento público)
Backend Supabase real (tablas + PostGIS + función de agregación, con el
GeoJSON importado a `traffic_lights`) · **Anti-abuso aplicado en el
servidor** (triggers/funciones: tope diario, saltos, transiciones de
estado, trust) · **Roles y permisos + login híbrido con Google (§4.7,
§6.9):** tabla `users`, RLS por rol, panel de admin aparte (solo Alan),
eliminación de cuenta en la app · **Ranking semanal de Colaboradores
(§6.10):** top 3 con premios de $300/$200/$100 por semana, opt-in con
apodo, entrega manual por el Admin · Detección pasiva calibrada con pruebas de campo ·
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

### Rechazado (no volver a proponer)
**Feed de incidentes estilo Bipper** (reportes de una línea tipo «aguas,
choque en Cuauhtémoc y Colón»): rechazado por Alan el 19 nov 2026 —
la app debe mantenerse limpia y cumplir su función (semáforos), sin
abarcar de más («el que mucho abarca, poco aprieta»). Waze ya cubre
los reportes de incidentes; el feed diluiría el producto.

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
