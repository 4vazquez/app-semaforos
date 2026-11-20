-- Semáforos MX — Supabase v1.1: esquema + RLS + funciones
-- ============================================================
-- Ejecutar en el SQL Editor del proyecto de Supabase, en orden:
--   1. Este archivo (001_schema.sql)
--   2. El seed de semáforos (seed_lights.py genera 002_seed_lights.sql
--      desde el GeoJSON de OpenStreetMap)
--
-- Corresponde a SPEC.md §6 (modelo de datos) y §6.9 (roles). Los nombres
-- de tablas y columnas coinciden con el cliente REST de la app
-- (SupabaseTimingRepository.kt): no renombrar sin actualizar la app.
--
-- Roles (SPEC §6.9): USER (común con login), COLLABORATOR (conductores
-- del programa, p. ej. Uber/DiDi) y ADMIN (solo Alan). El estado
-- Colaborador "sin anuncios" NO es un rol: se deriva de la cuota
-- mensual de 20 semáforos distintos (§6.5).
--
-- Regla de oro: la separación de roles se hace cumplir AQUÍ (RLS +
-- funciones SECURITY DEFINER), no solo escondiendo botones en la app.

-- Extensiones -------------------------------------------------------
create extension if not exists "pgcrypto";   -- gen_random_uuid()
create extension if not exists "postgis";    -- ST_DWithin / geografía

-- Tipos -------------------------------------------------------------
do $$ begin
  create type light_status as enum ('VERIFIED', 'UNVERIFIED', 'REMOVED');
exception when duplicate_object then null; end $$;

do $$ begin
  create type day_type as enum ('WEEKDAY', 'SATURDAY', 'SUNDAY_HOLIDAY');
exception when duplicate_object then null; end $$;

do $$ begin
  create type report_source as enum ('MANUAL_SYNC', 'PASSIVE');
exception when duplicate_object then null; end $$;

do $$ begin
  create type user_role as enum ('USER', 'COLLABORATOR', 'ADMIN');
exception when duplicate_object then null; end $$;

-- Tablas ------------------------------------------------------------
-- users: una fila por cuenta de Google. id = auth.users.id (Supabase
-- Auth). Se crea sola con el trigger handle_new_user() al registrarse.
create table if not exists public.users (
  id uuid primary key references auth.users (id) on delete cascade,
  google_id text,
  email text not null,
  display_name text,
  photo_url text,
  role user_role not null default 'USER',
  created_at timestamptz not null default now()
);

-- traffic_lights: la base semilla (OSM) entra VERIFIED; todo lo que
-- agrega un usuario empieza UNVERIFIED (lo fuerza el trigger).
create table if not exists public.traffic_lights (
  -- TEXT (no UUID): los ids semilla son los IDs de nodo de OSM
  -- ("node/289739626"), estables entre exportaciones; los agregados
  -- por usuarios usan UUIDs generados.
  id text primary key default gen_random_uuid()::text,
  latitude double precision not null,
  longitude double precision not null,
  name text,
  city text not null default 'Monterrey',
  status light_status not null default 'UNVERIFIED',
  reported_by uuid references public.users (id) on delete set null,
  created_by uuid references public.users (id) on delete set null,
  created_at timestamptz not null default now(),
  -- Columna geográfica para el radio de proximidad (índice GiST).
  geo geography(point, 4326)
    generated always as (st_setsrid(st_makepoint(longitude, latitude), 4326)::geography) stored
);

create table if not exists public.timing_reports (
  id uuid primary key default gen_random_uuid(),
  light_id text not null references public.traffic_lights (id) on delete cascade,
  reported_at timestamptz not null,
  hour_slot int not null check (hour_slot between 0 and 23),
  day_type day_type not null,
  source report_source not null,
  gps_accuracy_m real not null,
  -- Nulo para syncs sin login desde el Mapa (§4.7): alimentan la
  -- agregación pero no cuentan para ninguna cuota ni aparecen en aportes.
  user_id uuid references public.users (id) on delete set null,
  -- El ajuste de confianza se aplica una sola vez por reporte (§6.4).
  trust_applied boolean not null default false
);

create table if not exists public.light_timings (
  light_id text not null references public.traffic_lights (id) on delete cascade,
  hour_slot int not null check (hour_slot between 0 and 23),
  day_type day_type not null,
  green_offset_seconds int not null,
  cycle_seconds int,
  sample_count int not null default 0,
  confidence real not null default 0 check (confidence between 0 and 1),
  updated_at timestamptz not null default now(),
  primary key (light_id, hour_slot, day_type)
);

create table if not exists public.user_contributions (
  user_id uuid not null references public.users (id) on delete cascade,
  month text not null,  -- '2026-11'
  light_ids text[] not null default '{}',
  primary key (user_id, month)
);

create table if not exists public.removal_reports (
  id uuid primary key default gen_random_uuid(),
  light_id text not null references public.traffic_lights (id) on delete cascade,
  reported_at timestamptz not null default now(),
  user_id uuid references public.users (id) on delete set null,
  -- Un reporte de corrección por semáforo por usuario, para siempre (§6.7 r9).
  unique (light_id, user_id)
);

create table if not exists public.user_trust (
  user_id uuid primary key references public.users (id) on delete cascade,
  trust_score real not null default 0.5 check (trust_score between 0.1 and 1.0),
  confirmed_count int not null default 0,
  rejected_count int not null default 0
);

-- Índices -----------------------------------------------------------
create index if not exists idx_lights_geo on public.traffic_lights using gist (geo);
create index if not exists idx_lights_city_status on public.traffic_lights (city, status);
create index if not exists idx_lights_reported_by on public.traffic_lights (reported_by, created_at);
create index if not exists idx_reports_light_time on public.timing_reports (light_id, hour_slot, day_type, reported_at desc);
create index if not exists idx_reports_user on public.timing_reports (user_id, reported_at desc);
create index if not exists idx_removals_light on public.removal_reports (light_id);

-- Funciones auxiliares ------------------------------------------------
-- ¿El usuario autenticado es admin? SECURITY DEFINER para leer users
-- sin recursión de RLS (las políticas llaman a esta función).
create or replace function public.is_admin()
returns boolean
language sql
security definer
stable
as $$
  select exists (
    select 1 from public.users
    where id = auth.uid() and role = 'ADMIN'
  );
$$;

-- Recalcula light_timings para un (semáforo, franja, día) con los
-- reportes de los últimos 30 días (SPEC §6.4, versión SQL):
-- mediana del offset (segundos dentro del minuto), confianza por
-- volumen + dispersión + frescura. También aplica el ajuste de trust
-- una sola vez por reporte (trust_applied).
create or replace function public.refresh_light_timing(
  p_light_id text, p_hour int, p_day day_type
)
returns void
language plpgsql
security definer
as $$
declare
  v_median double precision;
  v_mad double precision;
  v_count int;
  v_volume real;
  v_spread real;
  v_fresh real;
  v_conf real;
  r record;
  v_diff double precision;
begin
  select
    percentile_cont(0.5) within group (
      order by (extract(epoch from reported_at)::bigint % 60)),
    count(*)
  into v_median, v_count
  from public.timing_reports
  where light_id = p_light_id
    and hour_slot = p_hour
    and day_type = p_day
    and reported_at >= now() - interval '30 days';

  if v_count is null or v_count = 0 then
    delete from public.light_timings
    where light_id = p_light_id and hour_slot = p_hour and day_type = p_day;
    return;
  end if;

  -- Desviación mediana absoluta (dispersión), en segundos.
  select percentile_cont(0.5) within group (
      order by abs((extract(epoch from reported_at)::bigint % 60) - v_median))
  into v_mad
  from public.timing_reports
  where light_id = p_light_id
    and hour_slot = p_hour
    and day_type = p_day
    and reported_at >= now() - interval '30 days';

  v_volume := least(1.0, v_count::real / 20.0);
  v_spread := 1.0 - least(1.0, coalesce(v_mad, 10.0)::real / 10.0);
  -- Frescura: vida media 14 días sobre la edad mediana de los reportes.
  -- (percentile_cont no acepta timestamptz como clave de orden: se ordena
  -- por epoch en segundos y la frescura se calcula contra now() en epoch.)
  select power(0.5::double precision,
      (extract(epoch from now())
       - percentile_cont(0.5) within group
         (order by extract(epoch from reported_at)::double precision))
      / (14 * 86400))
  into v_fresh
  from public.timing_reports
  where light_id = p_light_id
    and hour_slot = p_hour
    and day_type = p_day
    and reported_at >= now() - interval '30 days';
  v_conf := 0.5 * v_volume + 0.3 * v_spread + 0.2 * coalesce(v_fresh, 0.0);

  insert into public.light_timings
    (light_id, hour_slot, day_type, green_offset_seconds, cycle_seconds,
     sample_count, confidence, updated_at)
  values (p_light_id, p_hour, p_day, v_median::int, null, v_count,
          v_conf, now())
  on conflict (light_id, hour_slot, day_type) do update set
    green_offset_seconds = excluded.green_offset_seconds,
    sample_count = excluded.sample_count,
    confidence = excluded.confidence,
    updated_at = now();

  -- Ajuste de confianza, una sola vez por reporte: dentro de ±10 s de
  -- la mediana confirma (+0.03); fuera de ±20 s discrepa (-0.05).
  for r in
    select id, user_id,
           (extract(epoch from reported_at)::bigint % 60) as offs
    from public.timing_reports
    where light_id = p_light_id
      and hour_slot = p_hour
      and day_type = p_day
      and trust_applied = false
      and user_id is not null
  loop
    v_diff := abs(r.offs - v_median);
    -- Distancia circular dentro del minuto (55 s de 5 s = 10 s, no 50).
    if v_diff > 30 then v_diff := 60 - v_diff; end if;
    if v_diff <= 10 then
      update public.user_trust set
        trust_score = least(1.0, trust_score + 0.03),
        confirmed_count = confirmed_count + 1
      where user_id = r.user_id;
    elsif v_diff >= 20 then
      update public.user_trust set
        trust_score = greatest(0.1, trust_score - 0.05),
        rejected_count = rejected_count + 1
      where user_id = r.user_id;
    end if;
    update public.timing_reports set trust_applied = true where id = r.id;
  end loop;
end;
$$;

-- Semáforos cerca (RPC PostGIS): la app llama a esta función en vez de
-- traer toda la ciudad. RLS aplica: solo se devuelven no-REMOVED.
create or replace function public.lights_near(
  p_lat double precision, p_lon double precision, p_radius_m double precision
)
returns setof public.traffic_lights
language sql
stable
as $$
  select *
  from public.traffic_lights
  where status <> 'REMOVED'
    and st_dwithin(
      geo,
      st_setsrid(st_makepoint(p_lon, p_lat), 4326)::geography,
      p_radius_m
    )
  order by geo <-> st_setsrid(st_makepoint(p_lon, p_lat), 4326)::geography;
$$;

-- Borrado de cuenta (Play lo exige): anonimiza los reportes del usuario
-- (quedan como datos agregados sin identidad), borra sus aportes,
-- confianza y fila de users. La fila de auth.users la borra Alan desde
-- el panel de Supabase (o con la service key); aquí no se puede borrar
-- con el rol authenticated.
create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
as $$
begin
  update public.timing_reports set user_id = null where user_id = auth.uid();
  update public.removal_reports set user_id = null where user_id = auth.uid();
  update public.traffic_lights set reported_by = null where reported_by = auth.uid();
  update public.traffic_lights set created_by = null where created_by = auth.uid();
  delete from public.user_contributions where user_id = auth.uid();
  delete from public.user_trust where user_id = auth.uid();
  delete from public.users where id = auth.uid();
end;
$$;

-- Triggers ------------------------------------------------------------
-- Al registrarse en Supabase Auth (Google): crear la fila en users
-- (rol USER por defecto; Alan se pone ADMIN con un UPDATE manual, ver
-- supabase/README.md) y la confianza neutral 0.5.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
as $$
begin
  insert into public.users (id, google_id, email, display_name, photo_url, role)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'sub', new.id::text),
    new.email,
    coalesce(new.raw_user_meta_data ->> 'full_name',
             new.raw_user_meta_data ->> 'name'),
    coalesce(new.raw_user_meta_data ->> 'avatar_url',
             new.raw_user_meta_data ->> 'picture'),
    'USER'
  )
  on conflict (id) do nothing;
  insert into public.user_trust (user_id) values (new.id)
  on conflict (user_id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Reglas al agregar un semáforo (servidor, no solo cliente — §6.7):
-- siempre UNVERIFIED salvo admin; tope 5 nuevos por usuario por día;
-- si ya existe uno a menos de 30 m, se rechaza (el cliente hace sync
-- sobre el existente en vez de duplicar).
create or replace function public.enforce_new_light_rules()
returns trigger
language plpgsql
as $$
declare
  v_today_count int;
  v_near text;
begin
  -- Inserciones de semilla/admin (reported_by nulo, p. ej. el seed OSM
  -- corrido desde el SQL Editor) pasan sin las reglas anti-abuso de
  -- usuario: conservan su status VERIFIED y no se les aplica el dedup
  -- de 30 m. Los envíos de usuarios siempre traen reported_by = auth.uid()
  -- (lo exige la política RLS lights_insert_login), así que este bypass
  -- nunca aplica a un alta de usuario.
  if new.reported_by is null then
    return new;
  end if;
  if new.status <> 'UNVERIFIED' and not public.is_admin() then
    new.status := 'UNVERIFIED';
  end if;
  if new.reported_by is not null then
    select count(*) into v_today_count
    from public.traffic_lights
    where reported_by = new.reported_by
      and created_at >= date_trunc('day', now());
    if v_today_count >= 5 then
      raise exception 'daily_cap_reached' using errcode = 'P0001';
    end if;
  end if;
  select id into v_near
  from public.traffic_lights
  where status <> 'REMOVED'
    and st_dwithin(
      geo,
      st_setsrid(st_makepoint(new.longitude, new.latitude), 4326)::geography,
      30)
  limit 1;
  if v_near is not null then
    raise exception 'duplicate_light:%', v_near using errcode = 'P0002';
  end if;
  return new;
end;
$$;

drop trigger if exists enforce_new_light_rules on public.traffic_lights;
create trigger enforce_new_light_rules
  before insert on public.traffic_lights
  for each row execute function public.enforce_new_light_rules();

-- Al insertar un reporte de tiempo: acreditar la cuota (solo syncs
-- MANUAL con login; los pasivos y anónimos no cuentan), confirmar
-- semáforos UNVERIFIED con 3 conductores distintos, y refrescar la
-- agregación de esa (luz, franja, día).
create or replace function public.after_timing_report()
returns trigger
language plpgsql
security definer
as $$
declare
  v_confirmers int;
  v_light_status light_status;
begin
  if new.user_id is not null and new.source = 'MANUAL_SYNC' then
    insert into public.user_contributions (user_id, month, light_ids)
    values (new.user_id, to_char(new.reported_at, 'YYYY-MM'),
            array[new.light_id::text])
    on conflict (user_id, month) do update set
      light_ids = array(
        select distinct x from unnest(
          public.user_contributions.light_ids || array[new.light_id::text]
        ) as x
      );
  end if;

  select status into v_light_status
  from public.traffic_lights where id = new.light_id;
  if v_light_status = 'UNVERIFIED' then
    select count(distinct user_id) into v_confirmers
    from public.timing_reports
    where light_id = new.light_id and user_id is not null;
    -- El que lo reportó también cuenta como primer confirmador.
    if v_confirmers >= 2 then
      update public.traffic_lights set status = 'VERIFIED'
      where id = new.light_id;
    end if;
  end if;

  perform public.refresh_light_timing(new.light_id, new.hour_slot, new.day_type);
  return new;
end;
$$;

drop trigger if exists after_timing_report on public.timing_reports;
create trigger after_timing_report
  after insert on public.timing_reports
  for each row execute function public.after_timing_report();

-- Al reportar un retiro: con 3 usuarios distintos el semáforo se oculta.
create or replace function public.after_removal_report()
returns trigger
language plpgsql
security definer
as $$
declare
  v_count int;
begin
  select count(distinct user_id) into v_count
  from public.removal_reports where light_id = new.light_id;
  if v_count >= 3 then
    update public.traffic_lights set status = 'REMOVED'
    where id = new.light_id;
  end if;
  return new;
end;
$$;

drop trigger if exists after_removal_report on public.removal_reports;
create trigger after_removal_report
  after insert on public.removal_reports
  for each row execute function public.after_removal_report();

-- Solo un admin puede cambiar el rol de un usuario.
create or replace function public.guard_role_change()
returns trigger
language plpgsql
as $$
begin
  if old.role is distinct from new.role and not public.is_admin() then
    raise exception 'only_admin_can_change_role' using errcode = 'P0003';
  end if;
  return new;
end;
$$;

drop trigger if exists guard_role_change on public.users;
create trigger guard_role_change
  before update on public.users
  for each row execute function public.guard_role_change();

-- Row Level Security --------------------------------------------------
alter table public.users enable row level security;
alter table public.traffic_lights enable row level security;
alter table public.timing_reports enable row level security;
alter table public.light_timings enable row level security;
alter table public.user_contributions enable row level security;
alter table public.removal_reports enable row level security;
alter table public.user_trust enable row level security;

-- users: cada quien ve y edita solo su fila (salvo el rol, ver trigger);
-- el admin ve todo. La fila la crea el trigger, no el cliente.
drop policy if exists users_select_own on public.users;
create policy users_select_own on public.users
  for select using (id = auth.uid() or public.is_admin());
drop policy if exists users_update_own on public.users;
create policy users_update_own on public.users
  for update using (id = auth.uid() or public.is_admin())
  with check (id = auth.uid() or public.is_admin());
drop policy if exists users_delete_own on public.users;
create policy users_delete_own on public.users
  for delete using (id = auth.uid() or public.is_admin());

-- traffic_lights: el mapa es público (todos ven los no retirados,
-- con o sin login). Insertar exige login y nace UNVERIFIED; solo el
-- admin modifica (aprobar/ocultar/restaurar) o ve los REMOVED.
drop policy if exists lights_select_public on public.traffic_lights;
create policy lights_select_public on public.traffic_lights
  for select using (status <> 'REMOVED' or public.is_admin());
drop policy if exists lights_insert_login on public.traffic_lights;
create policy lights_insert_login on public.traffic_lights
  for insert with check (
    auth.uid() is not null
    and reported_by = auth.uid()
    and status = 'UNVERIFIED'
  );
drop policy if exists lights_update_admin on public.traffic_lights;
create policy lights_update_admin on public.traffic_lights
  for update using (public.is_admin()) with check (public.is_admin());

-- timing_reports: insertar con login (user_id propio) o anónimo desde
-- el Mapa (user_id nulo, §4.7). Cada quien ve solo sus reportes.
drop policy if exists reports_insert on public.timing_reports;
create policy reports_insert on public.timing_reports
  for insert with check (
    (auth.uid() is not null and user_id = auth.uid())
    or (auth.uid() is null and user_id is null)
  );
drop policy if exists reports_select_own on public.timing_reports;
create policy reports_select_own on public.timing_reports
  for select using (user_id = auth.uid() or public.is_admin());

-- light_timings: lectura pública (los tiempos agregados no identifican
-- a nadie). Nadie escribe directo: solo refresh_light_timing().
drop policy if exists timings_select_public on public.light_timings;
create policy timings_select_public on public.light_timings
  for select using (true);

-- user_contributions: solo el dueño (y el admin). La escritura la hace
-- el trigger after_timing_report; el cliente no escribe directo.
drop policy if exists contrib_select_own on public.user_contributions;
create policy contrib_select_own on public.user_contributions
  for select using (user_id = auth.uid() or public.is_admin());

-- removal_reports: insertar con login y user_id propio; ver los propios.
drop policy if exists removals_insert_own on public.removal_reports;
create policy removals_insert_own on public.removal_reports
  for insert with check (auth.uid() is not null and user_id = auth.uid());
drop policy if exists removals_select_own on public.removal_reports;
create policy removals_select_own on public.removal_reports
  for select using (user_id = auth.uid() or public.is_admin());

-- user_trust: lectura propia (y admin). Escritura solo por triggers.
drop policy if exists trust_select_own on public.user_trust;
create policy trust_select_own on public.user_trust
  for select using (user_id = auth.uid() or public.is_admin());

-- Fin. Siguiente paso: seed de semáforos (seed_lights.py) y, tras el
-- primer login de Alan, hacerlo ADMIN:
--   update public.users set role = 'ADMIN' where email = '<su correo>';
