# Semáforos MX — MVP

**El widget que despierta a los distraídos.**
*«Si no ves el verde, el verde te avisa.»*

Asistente de conducción que avisa cuando el semáforo está por cambiar a
verde, para no perder segundos en cada cruce. Empieza en Monterrey, México.

**Idea original:** Alan Vázquez.
**Borrador inicial (nov 2025):** https://github.com/4vazquez/app-semaforos
— prototipo web (Leaflet + OpenStreetMap) y GeoJSON con 1 579 semáforos
reales de Monterrey. Esta app Android se construye sobre ese borrador
(ver `SPEC.md` §0).

La especificación completa del producto está en [`SPEC.md`](SPEC.md).

## Qué incluye el MVP

- **Menú inferior dentro de la app** (Mapa / Mis aportes / Alertas /
  Ajustes): el menú solo existe con la app abierta. Cuando está cerrada,
  el widget trabaja en segundo plano.
- **Widget flotante compacto** (barra vertical de 120 dp o píldora de
  160 × 72 dp, como máximo un tercio del ancho, en un borde, arrastrable)
  que aparece solo cuando hay un semáforo cerca (≤ 150 m) y se oculta al
  alejarte (> 200 m). **Dos modos:** completo para Colaborador/Plus
  (botones «Sync verde» y «Aquí hay un semáforo» + slider de opacidad)
  y solo LED para usuarios normales (borde luminoso + cuenta regresiva).
- **Pantalla «Mis aportes»**: progreso del mes (X/20 semáforos distintos,
  con barra), estado Colaborador (activo o no, y hasta cuándo), nivel de
  confianza e historial de aportes.
- **Pantalla «Alertas»**: activar/desactivar el widget en segundo plano,
  tu modo de widget y vista previa del LED.
- **Mapa de semáforos de Monterrey** con OpenStreetMap (osmdroid, sin llave
  de API ni facturación) y los **1 579 semáforos reales** del GeoJSON como
  base semilla, con marcadores rojos (verificados) y grises (sin verificar).
  Toque prolongado para agregar un semáforo; toque en un marcador para ver
  su detalle (con «Ya no existe»).
- **Botones «Sync verde» y «Aquí hay un semáforo»** grandes, de un toque:
  corrigen los tiempos y agregan semáforos faltantes (en la ubicación GPS,
  con sync incluido). **«Ya no existe»** reporta semáforos retirados: se
  ocultan con 3 confirmaciones de conductores distintos.
- **Agregación con confianza**: mediana ponderada por la confianza del
  usuario, rechazo de atípicos por IQR y puntaje de confianza 0–1
  (ver `TimingAggregator.kt`).
- **Paquete anti-abuso**: verificación GPS (≤ 100 m, fix fresco y preciso),
  detección de ubicación simulada, rechazo de saltos imposibles, cruce con
  el sensor de movimiento, una cuenta por dispositivo, reputación (trust)
  por usuario, semáforos nuevos sin verificar (grises, sin alertas) y tope
  de 5 semáforos nuevos por usuario por día (ver `SPEC.md` §6.7).
- **Monetización en tres niveles**: Gratis (con banner AdMob, ID de
  prueba) · Colaborador (gratis y sin anuncios con 20+ actualizaciones en
  semáforos distintos al mes; programa para conductores Uber/DiDi) ·
  Plus (suscripción de pago, solo el flag/stub; se lanza después).
- **Términos y Condiciones v1.3**: pantalla de aceptación obligatoria en la
  primera apertura + relectura desde Ajustes. Incluye cuentas, roles y
  login con Google (§7 Bis) y el Aviso de Privacidad v1.0
  (`AVISO_DE_PRIVACIDAD.md`). ⚠️ Ambos textos son borradores pendientes
  de revisión legal antes del lanzamiento público. (La app v0.3.3 ya
  muestra los Términos v1.3; al actualizar vuelve a mostrar la pantalla
  «Acepto» una vez.)
- **Detección pasiva de arranque** (acelerómetro/GPS) conectada al widget:
  el arranque solo cuenta como reporte dentro de la ventana de verde ± 15 s;
  calibración de umbrales en v1.
- **Actualización automática** (v0.3.0): la app revisa los releases de
  GitHub al arrancar (máximo una vez al día), avisa «Hay una nueva versión»
  con las notas del release y actualiza con un toque (descarga + instalador
  del sistema, sin borrar la app ni perder datos). Solo en el build
  sideload: en el build de Play está desactivada (Play prohíbe
  auto-actualizarse desde fuera; ahí la maneja Play con la In-App Updates
  API). Ver `SPEC.md` §13 y «Publicar una nueva versión» abajo.
- **Contacto** (v0.3.1): Ajustes → Contacto muestra el correo del equipo
  (adminsemaforosmx@gmail.com) para copiarlo o escribir por correo con el
  asunto precargado; también está nombrado en los Términos §12. Será el
  correo de contacto de la ficha en Google Play.
- **Widget estable** (v0.3.2): corrige el cierre del servicio del widget
  al arrancar (orden de `SavedStateRegistry` en `FloatingWidgetService.onCreate`:
  attach → restore → `ON_CREATE`); la ubicación en segundo plano ahora se
  pide por separado de la de primer plano (regla de Android 11+, con
  explicación en español) y el estado del widget persiste al reiniciar la
  app. Esta versión ya muestra los Términos v1.3, así que vuelve a pedir
  «Acepto» una vez al actualizar.
- **Modo prueba** (v0.3.3, solo Admin): simula estar junto a un semáforo
  para probar el widget, el LED y la cuenta regresiva sin manejar. Se
  desbloquea tocando 7 veces la versión de la app en Ajustes (estilo
  «opciones de desarrollador» de Android): sin login todavía, es la única
  forma de distinguir al Admin. En modo prueba la ubicación y los tiempos
  son simulados (ciclo de 30 s: espera → alerta → verde), el widget lleva
  la marca «PRUEBA», los botones no guardan nada y la detección pasiva
  queda desactivada. En v1, con login de Google, se limitará al rol Admin.
- **Logo nuevo + renovación visual + botón «Mi ubicación»** (v1.0.0):
  icono de launcher rediseñado (semáforo moderno, verde encendido con
  halo sobre fondo verde oscuro), esquema de color «semáforo» en claro y
  oscuro (el modo oscuro ahora sí se activa con el sistema), tarjetas
  pulidas en Mis aportes, Alertas y Ajustes, icono de notificación propio
  (silueta de semáforo), y botón flotante «Mi ubicación» en el mapa
  (estilo Google Maps: centra el mapa en tu GPS con animación y muestra
  el punto azul de tu posición). ⚠️ **El login con Google y el backend
  Supabase NO están en esta versión**: su implementación quedó pendiente
  (ver «Roles y login» abajo) — la app v1.0.0 sigue siendo anónima y
  local, igual que v0.3.3 en ese aspecto.
- **Login con Google + backend Supabase** (v1.1.0): inicio de sesión con
  Google (Credential Manager / Google ID, sin SDK de Supabase: la app
  canjea el ID token contra la API REST de GoTrue con el Retrofit que
  ya traía), sesión cifrada en el teléfono, roles `USER` / `COLLABORATOR`
  / `ADMIN` leídos de la tabla `users`, y puertas en la app según el
  rol (sin login: mapa + alerta + Sync desde el Mapa; con login: sync
  desde el widget, agregar y reportar semáforos, ver los aportes propios;
  Admin: Modo prueba sin desbloquear nada). El esquema completo vive en
  `supabase/001_schema.sql` (7 tablas, agregación en SQL, triggers
  anti-abuso, RLS por rol, RPC `lights_near` con PostGIS y borrado de
  cuenta) con seed de 1 579 semáforos (`seed_lights.py`). La app sin
  configurar sigue funcionando en modo local anónimo; para activar el
  backend, ver `supabase/README.md` (crear el proyecto, correr el SQL,
  activar el proveedor Google y poner URL + claves en
  `local.properties`). ⚠️ El SQL no se ha ejecutado todavía contra un
  proyecto real de Supabase: la primera ejecución en el SQL Editor es
  la prueba de fuego.
- **Sello de verificación** (v1.1.1): palomita azul «Propietario» junto
  al nombre del Admin (solo Alan) y palomita verde «Colaborador
  verificado» para los conductores con licencia de chofer verificada,
  visibles en Ajustes (tarjeta de Cuenta) y en Mis aportes. El usuario
  común no muestra sello. Es solo presentación: los permisos reales se
  hacen cumplir en Supabase con RLS.

## Roles y login con Google (v1.1: implementado; falta crear el proyecto)

Aprobado por Alan el 18 nov 2026 (ver `SPEC.md` §4.7 y §6.9,
`TERMINOS_Y_CONDICIONES.md` §7 Bis y `AVISO_DE_PRIVACIDAD.md`).
Implementado en v1.1.0 (noche del 18–19 nov 2026): el código de login y
el esquema Supabase están en el repo. Para activarlo faltan los pasos
de cuenta de Alan: crear el proyecto Supabase y correr el SQL del
esquema, crear el cliente OAuth de Google (Android + Web) y poner el
client ID en `local.properties` (ver `supabase/README.md`).

- **Usuario común (sin login):** mapa + alerta del verde (+ Sync verde
  desde la pestaña Mapa, que alimenta la agregación pero no la cuota).
- **Colaborador (login con Google):** sync desde el widget, agregar y
  reportar semáforos, ver solo sus propios aportes. El rol se llama
  oficialmente «Colaborador», nunca «socio»: el Colaborador **no** es
  empleado, socio, asociado, agente ni accionista, y su único beneficio
  es el uso sin anuncios mientras cumpla las condiciones del programa
  (sin valor en efectivo, sin participación en ingresos o utilidades,
  intransferible).
- **Admin (solo Alan):** privilegios totales — aprobar/rechazar
  semáforos, ver reportes y métricas, moderar cuentas — en un panel
  aparte, fuera del APK público.
- **Login híbrido (SSO con Google):** sin cuenta para uso básico; con
  cuenta de Google para aportar, ser Colaborador o tener Plus. Google
  entrega nombre, correo, foto e ID; la contraseña de Google nunca se
  recibe ni se guarda. La separación de permisos se hace cumplir en el
  servidor (Supabase RLS + funciones), no solo escondiendo botones.

## Requisitos para compilar

- **JDK 17** (requerido por Android Gradle Plugin 8.5).
- **Android SDK**: platform `android-34` + build-tools `34.0.0`.
  Define `ANDROID_HOME` o crea `android/local.properties` con:
  `sdk.dir=/ruta/a/tu/Android/Sdk`.
- **Gradle 8.7** (se descarga solo con el wrapper).

## Cómo compilar

```bash
cd android
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
Instálalo en el teléfono con:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Pruebas unitarias (agregación, ponderación por confianza, correcciones de
mapa, anti-abuso y matemática GPS):

```bash
./gradlew testDebugUnitTest
```

**Estado verificado (19 nov 2026):** el APK compila (`app-debug.apk`,
24.6 MB, `com.alan.semaforosmx` v1.1.1) y las **98 pruebas unitarias
pasan** (95 de v1.1.0 + 3 nuevas del sello de verificación), corridas
con `run_tests_sandbox.sh` (JUnitCore directo: el ejecutor de pruebas
de Gradle se cae en este sandbox porque sus workers se comunican por
sockets TCP y Java no puede abrirlos aquí). Nota del entorno: en este sandbox los procesos Java no pueden
abrir sockets TCP (ni siquiera loopback), así que Gradle no puede usar su
daemon ni descargar dependencias; la compilación aquí se hizo con un repo
Maven local pre-descargado (`~/maven-repo`, vía `~/maven_resolve.py`) y
Gradle en proceso (`~/gradle-offline-build.sh`, `--offline`, sin daemon).
En una máquina normal, `./gradlew assembleDebug` funciona directo. Por el
mismo bloqueo de TCP, la tarea `testDebugUnitTest` de Gradle no corre en
este sandbox (sus workers forkados necesitan loopback); las pruebas se
ejecutaron directamente con `java org.junit.runner.JUnitCore` sobre las
clases compiladas.

## Firma de los builds (keystore estable)

Todos los builds se firman con un único keystore del proyecto en
`keystore/semaforosmx-release.keystore` (alias `semaforosmx`), configurado
en `android/app/build.gradle.kts` a través de `keystore/keystore.properties`.
El keystore y el properties **NO están en git** (ver `.gitignore`): en un
clon fresco el build usa la firma debug por defecto, con un aviso.

⚠️ **Haz una copia de seguridad de `keystore/` antes del lanzamiento en
Play**: si se pierde la llave, Android y Play tratan la app como una app
distinta y habría que publicar una ficha nueva; los usuarios tendrían que
desinstalar y volver a instalar.

⚠️ **v0.3.2 cambió de firma**: v0.3.1 y anteriores se firmaron con
certificados debug distintos por máquina, así que quien tenga v0.3.1 o
anterior debe **desinstalar la app una vez** antes de instalar v0.3.2
(«No se instaló la app debido a un conflicto con un paquete» si se intenta
actualizar encima). A partir de v0.3.2 las actualizaciones encima vuelven
a funcionar con normalidad.

## Cómo probar el MVP

1. Instala el APK y abre la app.
2. Lee y acepta los Términos y Condiciones (botón «Acepto»).
3. Otorga ubicación, permiso de superposición (para el widget) y
   notificaciones.
4. El mapa muestra los 1 579 semáforos reales de Monterrey (círculos rojos).
5. Activa el widget flotante desde la pestaña **Alertas** del menú
   inferior y arrástralo a donde no te estorbe. El widget aparece solo
   cuando hay un semáforo cerca; mientras la app está abierta usas el
   menú.
6. Acércate a un semáforo registrado (o usa un emulador con ubicación
   simulada) y presiona **Sync verde** varias veces para ver cómo el
   contador sube y la confianza del semáforo mejora. Prueba también
   **«Aquí hay un semáforo»** en un cruce sin semáforo registrado: aparece
   un marcador gris que necesita 3 confirmaciones para volverse rojo.
7. Revisa tu progreso en la pestaña **Mis aportes**: semáforos distintos
   del mes (X/20), estado Colaborador, nivel de confianza e historial.

## Modo prueba (solo Admin, v0.3.3)

Para probar el widget sin manejar:

1. En **Ajustes**, toca 7 veces la **Versión de la app**: se activan las
   Opciones de desarrollador (un aviso lo confirma). El desbloqueo queda
   guardado; no hay que repetirlo.
2. Activa **Modo prueba**: la app simula que estás parado a ~30 m del
   semáforo verificado más cercano a tu última ubicación real (o de
   Av. Lincoln / Ruiz Cortines si no hay ninguno a menos de 2 km).
3. Activa el widget en **Alertas** y cierra la app con el botón Home: el
   widget aparece con la marca **PRUEBA** y el LED cicla cada 30 s entre
   espera (cuenta regresiva), alerta y verde.
4. Para volver a lo real, apaga Modo prueba: el servicio vuelve al GPS de
   inmediato.

Seguridad: en modo prueba los botones del widget están desactivados, los
syncs no se guardan (el servicio los rechaza además de la UI), la
detección pasiva no reporta y nada de lo simulado toca el repositorio
real. La simulación es interna de la app, no una ubicación simulada del
sistema, así que no dispara la detección anti-abuso. En v1, con login de
Google y roles, el modo prueba se limitará al rol Admin (Alan) y el
desbloqueo de 7 toques desaparecerá.

## Publicar una nueva versión

La app se actualiza sola desde los releases de GitHub (ver `SPEC.md`
§13). Para publicar:

1. Sube `versionCode` y `versionName` en `android/app/build.gradle.kts`
   (el tag del release debe ser `vX.Y.Z`, igual que el `versionName`).
2. Compila y verifica: `./gradlew assembleDebug` + pruebas
   (`./gradlew testDebugUnitTest`, o JUnitCore directo en este sandbox).
3. Haz push a GitHub y crea un release con el tag `vX.Y.Z` adjuntando el
   `app-debug.apk` como asset (por ejemplo con
   `gh release create vX.Y.Z app-debug.apk`).
4. Los usuarios reciben el aviso «Hay una nueva versión» al abrir la app
   (como máximo una vez al día) y actualizan con un toque. «Más tarde»
   silencia esa versión 24 h.

⚠️ El actualizador propio solo vive en el build debug/sideload. Cuando la
app se publique en Google Play, el flag `SELF_UPDATE_ENABLED` es `false`
en release y la actualización la maneja Play (In-App Updates API, por
implementar en v1).

## Activar el backend real (v1)

El MVP usa un repositorio en memoria (los datos se pierden al cerrar la
app). Para activar Supabase:

1. Crea el proyecto en [supabase.com](https://supabase.com) y las tablas
   del `SPEC.md` §6 (`traffic_lights`, `timing_reports`, `light_timings`,
   `user_contributions`, `removal_reports`, `user_trust`, `users` — esta
   última con Google ID y rol, ver `SPEC.md` §6.9).
2. Importa el GeoJSON (`android/app/src/main/assets/semaforos_mty.geojson`)
   a `traffic_lights` (el `id` de cada semáforo es el ID del nodo de OSM).
3. Pon la URL y la clave anónima en `android/local.properties`:
   `SUPABASE_URL=...` y `SUPABASE_ANON_KEY=...`.
4. En `data/repository/RepositoryProvider.kt`, cambia `USE_SUPABASE` a
   `true`.

## Anuncios reales (v1)

En `ads/AdsManager.kt`, cambia `USE_REAL_ADS` a `true` y pon el ID de
unidad real de AdMob. También cambia el ID de aplicación en
`AndroidManifest.xml` (meta-data `APPLICATION_ID`).

## Estructura del proyecto

```
android/
├── app/src/main/assets/semaforos_mty.geojson   # 1 579 semáforos (OSM, ODbL)
├── app/src/main/assets/terminos_y_condiciones.md # Términos v1.3 (verbatim)
├── app/src/main/java/com/alan/semaforosmx/
│   ├── MainActivity.kt            # Puerta de términos + menú inferior
│   ├── SemaforosMxApp.kt          # Inicialización (OSM + AdMob + seed)
│   ├── data/GeoJsonLightsLoader.kt # Parsea el GeoJSON semilla
│   ├── data/UserIdentity.kt       # ID anónimo ligado al dispositivo
│   ├── data/legal/                # Aceptación de Términos y Condiciones
│   ├── data/model/Models.kt       # Semáforo, reporte, timing, trust, anti-abuso
│   ├── data/aggregation/          # Mediana ponderada + IQR + confianza
│   ├── data/repository/           # Interfaz, en memoria, Supabase (stub)
│   ├── ui/map/                    # Mapa OpenStreetMap (rojos/grises + detalle)
│   ├── ui/sync/                   # Botones Sync verde y Aquí hay semáforo
│   ├── ui/aportes/                # Mis aportes (progreso, Colaborador, historial)
│   ├── ui/alertas/                # Alertas (control del widget + vista previa LED)
│   ├── ui/terms/                  # Pantalla de Términos (aceptación/relectura)
│   ├── ui/contact/                # Correo de contacto + mailto (Ajustes → Contacto)
│   ├── ui/settings/               # Ajustes (vibración, términos, contacto, acerca de)
│   ├── widget/                    # Servicio overlay + borde LED + modos FULL/LED_ONLY
│   ├── sensing/                   # Verificación GPS + detección pasiva
│   ├── update/                    # Actualizador propio (GitHub Releases + instalador)
│   └── ads/                       # Banner AdMob + niveles Gratis/Colaborador/Plus
└── app/src/test/...               # Pruebas: agregador, trust, mapa, anti-abuso, GPS, modos, versiones
```

## Notas

- El MVP se publicó en el repositorio de Alan
  (https://github.com/4vazquez/app-semaforos, commit `1fe0cb3`, 18 nov 2026)
  por el agente principal. Este agente de construcción trabaja solo en
  local y no sube nada a GitHub.
- No se creó ninguna cuenta en la nube ni se publicó nada en Play Store.
- Datos de semáforos: © OpenStreetMap contributors, licencia ODbL.
- La app no alienta el uso del teléfono al manejar: el widget solo muestra
  color y un número; el mensaje de la app es *mira la calle, no la pantalla*.
