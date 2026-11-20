-- 006_gps_validation.sql
-- Validación de GPS del lado servidor para timing_reports.
-- Cierra el hueco de que la verificación GPS era 100% del cliente:
-- ahora el servidor guarda las coordenadas del reporte y rechaza
-- syncs hechos lejos del semáforo, con precisión grosera o con
-- saltos imposibles (teletransporte). Requiere 001 aplicado.
-- Orden: después de 001 (y de 005 si la base corrió el 001 original).

alter table public.timing_reports
  add column if not exists latitude double precision,
  add column if not exists longitude double precision;

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
  -- caza teletransportes, no conducción normal).
  if new.user_id is not null then
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

drop trigger if exists validate_report_gps on public.timing_reports;
create trigger validate_report_gps
  before insert on public.timing_reports
  for each row execute function public.validate_report_gps();
