# AVISO DE PRIVACIDAD — VÍA VERDE

> **Versión:** 1.3 — 20 de noviembre de 2026.
> Responsable de contacto: adminsemaforosmx@gmail.com
>
> ⚠️ **NOTA IMPORTANTE:** Este documento es un borrador elaborado como
> punto de partida. Debe ser revisado y ajustado por un abogado antes
> del lanzamiento público de la aplicación.

---

## 1. Responsable del tratamiento

El responsable del tratamiento es **Alan Vazquez**, persona física con
domicilio en Monterrey, Nuevo León, México, operando el proyecto
Vía Verde en fase piloto/beta. (El domicilio completo para
notificaciones y la transición a persona moral quedan sujetos a
revisión legal antes del lanzamiento público.) Contacto para
privacidad, derechos ARCO y eliminación de cuenta:
**adminsemaforosmx@gmail.com**.

## 2. Datos personales que recabamos

**Solo si el usuario inicia sesión con Google** (obligatorio para aportar
reportes, ser Colaborador o tener Plus; el uso básico —ver el mapa y
recibir la alerta del verde— no requiere cuenta):

- Nombre, correo electrónico, foto de perfil (si la tiene) e
  identificador de Google. **No recabamos ni almacenamos su contraseña
  de Google.**

**De todos los usuarios, para operar la App:**

- Ubicación del dispositivo (GPS preciso), solo para detectar
  semáforos cercanos y validar que un reporte se hizo estando en el
  cruce (≤ 100 m, fix fresco y preciso). Esto incluye la ubicación
  en segundo plano cuando el widget está activo, para detectar
  semáforos cercanos sin abrir la App; el usuario puede desactivarla
  desde los permisos del sistema. Los reportes guardan las
  coordenadas del fix GPS usado en la validación (para verificación
  antifraude del lado servidor) junto con su precisión.
- Identificador del dispositivo (hash, no el ID crudo) y datos de uso
  de la App (reportes de tiempos, semáforos agregados o reportados como
  retirados, progreso de Colaborador, nivel de confianza).
- Si el usuario nos escribe por correo, los datos que incluya en su
  mensaje.

**Solo si el usuario lo solicita (verificación de conductor):**

- Foto de su licencia de conducir de chofer vigente. Es un **dato
  personal sensible**: se guarda en un repositorio privado al que solo
  accede el Administrador, se revisa una sola vez y **se elimina en
  cuanto se resuelve la solicitud** (en ningún caso se conserva más de
  30 días). Solo quedan guardados el estado «verificado», la fecha y el
  tipo de licencia, nunca la imagen.

**Solo si el usuario lo activa (ranking semanal):**

- Apodo elegido por el usuario y su conteo de aportes de la semana, que
  se publican en el ranking dentro de la App. Sin esta activación, el
  usuario no aparece en el ranking. El apodo es lo único visible; nunca
  se publican el nombre real ni el correo.

**No recabamos** datos de salud, religión, opiniones políticas, etc.,
ni datos bancarios dentro de la App. Los pagos de Plus (en su caso) los
procesa Google Play, no la App.

## 3. Finalidades del tratamiento

**Finalidades primarias (necesarias para el servicio):**

1. Operar la App: mostrar el mapa de semáforos y las alertas del cambio
   a verde.
2. Mejorar los tiempos de los semáforos con los reportes de la
   comunidad.
3. Prevenir fraude y abuso: verificación GPS, detección de ubicación
   simulada, límites anti-spam y ponderación por confianza.
4. Avisar al usuario de cambios en el servicio y en los Términos y
   Condiciones.
5. Atender solicitudes de soporte y contacto.
6. Verificar la condición de conductor del usuario que solicita el
   sello «Colaborador verificado», revisando la foto de su licencia de
   chofer (ver §2 y §5).
7. Operar el ranking semanal de Colaboradores: contar los aportes de
   la semana y, solo con el consentimiento del usuario, publicar su
   apodo y su conteo en el ranking dentro de la App.

**Finalidades secundarias:**

8. Estadísticas agregadas y anonimizadas para mejorar el servicio. Los
    datos agregados no identifican a ningún usuario individual.

Si el usuario no desea que sus datos se usen para la finalidad
secundaria, puede manifestarlo por correo a adminsemaforosmx@gmail.com;
esto no afecta el uso del servicio.

## 4. Transferencias y encargados

**Sus datos personales identificables no se venden ni se comparten con
terceros sin su consentimiento expreso aparte.** Solo los procesan los
proveedores necesarios para operar el servicio, bajo sus propios avisos
de privacidad:

- **Google Sign-In** (inicio de sesión con Google).
- **Supabase** (base de datos y backend del servicio). La
  infraestructura corre en servidores de Amazon Web Services en
  Virginia, Estados Unidos (región US East): al usar el servicio,
  sus datos se transfieren a EE.UU. bajo las medidas de seguridad
  del proveedor. Las fotos de licencias se guardan en un repositorio
  privado de Supabase al que solo accede el Administrador.
- **Google Play / AdMob** (pagos de suscripciones y anuncios, en su caso).

Los tiempos publicados de los semáforos son medianas ponderadas de
muchos reportes; ningún reporte individual es identificable en los datos
agregados.

## 5. Retención

- Los reportes crudos (incluidas las coordenadas GPS de validación)
  se conservan **90 días**; los tiempos agregados, indefinidamente
  (son datos anonimizados, sin identidad).
- La foto de la licencia de conducir se elimina en cuanto se resuelve
  la solicitud de verificación y en ningún caso se conserva más de
  **30 días**. Solo quedan guardados el estado «verificado», la fecha y
  el tipo de licencia.
- El apodo del ranking se conserva mientras el usuario mantenga activa
  la opción «aparecer en el ranking»; al desactivarla, el apodo deja de
  publicarse.
- Los datos de la cuenta se conservan mientras la cuenta esté activa y
  se eliminan al borrarla (ver §7).

## 5 Bis. Seguridad y brechas

Aplicamos medidas de seguridad administrativas, técnicas y físicas
razonables para proteger los datos personales, incluyendo acceso
restringido a los datos sensibles y cifrado en tránsito. Ningún sistema
es totalmente seguro: en caso de una vulneración de seguridad que
afecte de forma significativa los derechos de los usuarios, lo
notificaremos a los afectados sin dilación indebida, conforme al
artículo 20 de la LFPDPPP.

## 6. Derechos ARCO y revocación del consentimiento

El usuario puede ejercer sus derechos de **Acceso, Rectificación,
Cancelación y Oposición (ARCO)**, así como revocar su consentimiento,
por correo a **adminsemaforosmx@gmail.com** o desde la App. Responderemos
en los plazos que marca la Ley Federal de Protección de Datos Personales
en Posesión de los Particulares (LFPDPPP).

## 7. Eliminación de cuenta

El usuario puede borrar su cuenta y sus datos personales desde la App
(Ajustes → Eliminar cuenta) o por correo a adminsemaforosmx@gmail.com.
Al borrarla se eliminan nombre, correo, foto e identificador de Google.
Sus reportes previos quedan únicamente como datos agregados y
anonimizados, sin forma de identificarlo.

## 8. Menores de edad

La App está dirigida a personas mayores de 18 años con licencia de
conducir vigente. No recabamos deliberadamente datos de menores de edad.

## 9. Cambios a este Aviso

Cualquier cambio se notificará dentro de la App y entrará en vigor a
partir de su publicación. El uso continuado de la App después de los
cambios implica su aceptación.

---

*Versión 1.3 — 20 de noviembre de 2026. Documento pendiente de revisión
legal antes del lanzamiento público.*
