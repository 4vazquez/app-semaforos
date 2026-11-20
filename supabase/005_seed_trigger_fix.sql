-- Parche para bases ya migradas con el 001 original (antes de este fix).
-- Corrige enforce_new_light_rules(): las inserciones de semilla/admin
-- (reported_by nulo, p. ej. 002_seed_lights.sql corrido desde el SQL
-- Editor) ya no se degradan a UNVERIFIED ni chocan con el dedup de 30 m.
-- Los envíos de usuarios siempre traen reported_by = auth.uid() (lo exige
-- la política RLS lights_insert_login), así que el bypass nunca aplica
-- a un alta de usuario.
-- Instalaciones nuevas: este archivo es innecesario (001 ya trae el fix).
-- Orden en la base actual: correr este archivo, luego 002, 003 y 004.

create or replace function public.enforce_new_light_rules()
returns trigger
language plpgsql
as $$
declare
  v_today_count int;
  v_near text;
begin
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
