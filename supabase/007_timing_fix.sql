-- 007_timing_fix.sql — corrige refresh_light_timing (bug detectado en la
-- prueba de humo de 006, 2026-11-20): la consulta de frescura usaba
-- percentile_cont(0.5) WITHIN GROUP (ORDER BY reported_at), y
-- percentile_cont no acepta timestamptz como clave de orden
-- (ERROR 42883: function percentile_cont(numeric, timestamp with time zone)
-- does not exist). Como after_timing_report llama a refresh_light_timing,
-- TODO insert en timing_reports fallaba. La corrección ordena por
-- extract(epoch from reported_at)::double precision y calcula la frescura
-- contra extract(epoch from now()).
-- Idempotente: create or replace. Ejecutar después de 001 (y de 006).
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
