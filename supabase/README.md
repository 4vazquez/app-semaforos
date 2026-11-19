# Supabase — backend de Vía Verde (v1.1)

Todo lo necesario para levantar el backend. La app funciona sin él
(modo local en memoria), pero el login, los roles y los datos
compartidos entre usuarios necesitan este proyecto.

## 1. Crear el proyecto

1. Entra a [supabase.com](https://supabase.com) con la cuenta de Google
   del negocio (`adminsemaforosmx@gmail.com`) → **New project**.
2. Nombre: `semaforos-mx`. Región: la más cercana (US East).
3. Guarda la **Database password** en un lugar seguro (la pedirá el
   panel para algunas acciones).
4. En **Project Settings → API** copia:
   - **Project URL** (`https://xxxxx.supabase.co`)
   - **anon public key** (`eyJhbGciOi...`)

## 2. Crear las tablas y las reglas

En el **SQL Editor** del proyecto, ejecuta en orden:

1. `001_schema.sql` (tablas, funciones de agregación, triggers y RLS).
2. Genera el seed y ejecútalo:
   ```bash
   python3 supabase/seed_lights.py
   ```
   Eso crea `002_seed_lights.sql` (1 579 semáforos de Monterrey,
   VERIFIED). Pégalo en el SQL Editor y ejecútalo.

## 3. Activar el login con Google

1. En Supabase: **Authentication → Providers → Google → Enable**.
   Te pedirá un **Client ID** y **Client Secret** de Google.
2. En [Google Cloud Console](https://console.cloud.google.com)
   (mismo Google del negocio):
   - Crea un proyecto (o usa el existente) → **APIs & Services →
     Credentials → Create Credentials → OAuth client ID**.
   - Tipo: **Android**. Package: `com.alan.semaforosmx`.
   - SHA-1 del certificado de firma: `7bec49c7488b0703e7a39d4280573d8f2c3ee983`
     (SHA-256: `3cd243397c13e21c7b8114bfc47ed7ed1f32c81e00350e7cb493d07dbbcd2b64`).
   - Además crea un cliente tipo **Web application**: ese Client ID es
     el `serverClientId` que usa la app para el inicio de sesión
     (Credential Manager / Google ID).
3. Pega el Client ID y Secret del cliente **Web** en el proveedor
   Google de Supabase.
4. En Supabase, **Authentication → URL Configuration**, deja el
   **Site URL** como el de tu dominio cuando lo tengas (por ahora el
   valor por defecto está bien para el login desde la app).

## 4. Conectar la app

En `android/local.properties` (nunca se sube a git):

```properties
SUPABASE_URL=https://xxxxx.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOi...
GOOGLE_SERVER_CLIENT_ID=xxxxx.apps.googleusercontent.com
```

Recompila y la app usa Supabase en vez del modo local. Sin estos
valores, la app sigue funcionando en modo local y el botón de login
explica que el inicio de sesión aún no está configurado.

## 5. Hacerte Admin

Inicia sesión en la app una vez con tu Google, y luego en el SQL Editor:

```sql
update public.users set role = 'ADMIN' where email = '<tu correo>';
```

Solo el Admin puede cambiar roles, aprobar semáforos y ver datos
globales. El panel de Admin es aparte de la app pública (v1.1 no lo
incluye todavía; mientras tanto, el panel de Supabase hace ese papel).

## 6. Borrar tu cuenta (lo que hace la app)

Ajustes → Eliminar cuenta llama a `delete_my_account()`: anonimiza
tus reportes (quedan como datos agregados sin identidad) y borra tu
fila de `users`, tus aportes y tu confianza. La fila de `auth.users`
se borra desde **Authentication → Users** en el panel.

## Archivos

| Archivo | Qué es |
|---|---|
| `001_schema.sql` | Tablas, agregación, triggers anti-abuso, RLS, RPCs |
| `003_verification.sql` | Verificación de Colaborador: solicitudes + bucket privado de licencias + resolución por Admin |
| `004_ranking.sql` | Ranking semanal: apodo + opt-in, RPC top_collaborators_week, tabla weekly_prizes + registro de premios por Admin |
| `seed_lights.py` | Genera `002_seed_lights.sql` desde el GeoJSON de OSM |
| `002_seed_lights.sql` | Generado (no editar a mano; se regenera) |

Ejecuta `001_schema.sql`, luego `003_verification.sql` y `004_ranking.sql` (en ese orden). El seed (`002_seed_lights.sql`) puede correrse en cualquier momento después de `001_schema.sql`.
