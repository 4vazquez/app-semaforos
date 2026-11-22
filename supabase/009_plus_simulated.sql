-- 009_plus_simulated.sql
-- Plus fake GPS (SPEC §9, decisión de Alan 2026-11-21): los reportes
-- hechos con ubicación simulada se guardan MARCADOS (simulated=true)
-- pero NO cuentan para la cuota Colaborador (20/mes), ni para el
-- ranking semanal, ni alimentan el modelo de tiempos, ni confirman
-- semáforos nuevos. Es uso personal del suscriptor, no contribución.
-- Idempotente. Ejecutar DESPUÉS de 001, 006 y 007, en el SQL Editor.

alter table public.timing_reports
  add column if not exists simulated boolean not null default false;

-- 1) Validación GPS (006): los reportes simulados conservan las
--    revisiones de precisión y distancia (el usuario simula estar EN
--    el semáforo), pero se exime el chequeo de salto imposible
--    (teletransportarse es el punto del fake GPS personal).
create or replace function public.validate_report_gps()
returns trigger
language plpgsql
as $$
declare
  v_light_geo geography;
  v_point geography;
  v_last_at timestamptz;
  v_last_point geography;
  v_speed_mps double precision;
begin
  -- El ADMIN (Alan) conserva su privilegio de prueba con ubicación
  -- simulada también del lado servidor.
  if public.is_admin() then
    return new;
  end if;
  -- Sin coordenadas (versiones viejas de la app) no se valida:
  -- compatibilidad hacia atrás. La app nueva siempre envía coordenadas.
  if new.latitude is null or new.longitude is null then
    return new;
  end if;
  -- Precisión: la app exige ≤ 50 m; el servidor solo rechaza
  -- imprecisiones groseras (> 100 m) para no falsos positivos.
  if new.gps_accuracy_m is not null and new.gps_accuracy_m > 100 then
    raise exception 'gps_inaccurate' using errcode = 'P0004';
  end if;
  -- Distancia al semáforo: la app exige ≤ 100 m; el servidor usa 150 m
  -- de tolerancia (error GPS + error de coordenada OSM) y caza el
  -- fraude «desde el sillón» (cientos de metros o kilómetros).
  select geo into v_light_geo
  from public.traffic_lights where id = new.light_id;
  if v_light_geo is not null then
    v_point := st_setsrid(st_makepoint(new.longitude, new.latitude), 4326)::geography;
    if not st_dwithin(v_light_geo, v_point, 150) then
      raise exception 'gps_too_far' using errcode = 'P0004';
    end if;
  end if;
  -- Salto imposible: velocidad implícita desde el último reporte del
  -- mismo usuario con coordenadas. Umbral 250 km/h (generoso: solo
  -- caza teletransportes, no conducción normal). Los reportes
  -- simulados (Plus fake GPS) se eximen: teletransportarse es su uso.
  if new.user_id is not null and not new.simulated then
    select reported_at,
           st_setsrid(st_makepoint(longitude, latitude), 4326)::geography
      into v_last_at, v_last_point
    from public.timing_reports
    where user_id = new.user_id
      and latitude is not null and longitude is not null
    order by reported_at desc
    limit 1;
    if v_last_at is not null then
      v_speed_mps := st_distance(
          v_last_point,
          st_setsrid(st_makepoint(new.longitude, new.latitude), 4326)::geography
        ) / greatest(extract(epoch from (new.reported_at - v_last_at)), 1);
      if v_speed_mps > 250.0 / 3.6 then
        raise exception 'impossible_jump' using errcode = 'P0004';
      end if;
    end if;
  end if;
  return new;
end;
$$;

-- 2) after_timing_report (001): un reporte simulado se guarda marcado
--    pero no acredita cuota, no confirma semáforos UNVERIFIED y no
--    recalcula los tiempos.
create or replace function public.after_timing_report()
returns trigger
language plpgsql
security definer
as $$
declare
  v_confirmers int;
  v_light_status light_status;
begin
  -- Reportes simulados (Plus fake GPS, SPEC §9): uso personal; no
  -- cuentan para la cuota, no confirman semáforos y no alimentan
  -- el modelo de tiempos.
  if new.simulated then
    return new;
  end if;
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

-- 3) refresh_light_timing (007): los reportes simulados quedan fuera
--    de la agregación y del ajuste de confianza (defensa en
--    profundidad: aunque alguien lo llamara, no los cuenta).
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
    and simulated = false
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
    and simulated = false
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
    and simulated = false
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
      and simulated = false
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

-- 4) top_collaborators_week (004): los reportes simulados no cuentan
--    para el ranking semanal.
create or replace function public.top_collaborators_week(
  p_week_start date default (date_trunc('week', now() at time zone 'America/Monterrey'))::date,
  p_limit int default 10
)
returns table (
  user_id uuid,
  nickname text,
  distinct_lights bigint,
  trust_score real,
  first_contribution timestamptz
)
language sql
stable
security definer
as $$
  select
    u.id,
    u.nickname,
    count(distinct tr.light_id) as distinct_lights,
    coalesce(ut.trust_score, 0.5) as trust_score,
    min(tr.reported_at) as first_contribution
  from public.users u
  join public.timing_reports tr on tr.user_id = u.id
  left join public.user_trust ut on ut.user_id = u.id
  where u.show_in_ranking = true
    and u.nickname is not null
    and tr.source = 'MANUAL_SYNC'
    and tr.simulated = false
    and tr.reported_at >= (p_week_start::timestamptz at time zone 'America/Monterrey')
    and tr.reported_at < ((p_week_start + 7)::timestamptz at time zone 'America/Monterrey')
  group by u.id, u.nickname, ut.trust_score
  order by distinct_lights desc, trust_score desc, first_contribution asc
  limit p_limit;
$$;
