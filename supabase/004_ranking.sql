-- Semáforos MX — Supabase v1.2: ranking semanal de Colaboradores
-- ============================================================
-- Ejecutar DESPUÉS de 001_schema.sql, en el SQL Editor.
--
-- Diseño aprobado por Alan (19 nov 2026, SPEC §6.10): reconocimiento
-- semanal (no competencia formal) para que los conductores aporten
-- seguido. Ciclo de lunes 00:00 a domingo 23:59 (America/Monterrey).
-- Métrica: semáforos distintos aportados en la semana (los mismos
-- aportes que cuentan para la cuota de §6.5: syncs manuales con login
-- y GPS verificado; un semáforo cuenta una sola vez por semana).
-- Sin tope. Top 3: $300 / $200 / $100 MXN en premios.
-- Solo aparecen los usuarios con opt-in (show_in_ranking) y apodo.

-- Columnas de ranking en users ----------------------------------------
alter table public.users
  add column if not exists nickname text,
  add column if not exists show_in_ranking boolean not null default false;

-- Apodo único entre quienes lo tienen (evita confusiones en el ranking).
create unique index if not exists idx_users_nickname_unique
  on public.users (nickname) where nickname is not null;

-- Premios semanales ----------------------------------------------------
do $$ begin
  create type prize_status as enum ('PENDING', 'CONTACTED', 'DELIVERED', 'DISQUALIFIED');
exception when duplicate_object then null; end $$;

create table if not exists public.weekly_prizes (
  id uuid primary key default gen_random_uuid(),
  -- Lunes de la semana (America/Monterrey).
  week_start date not null,
  user_id uuid not null references public.users (id) on delete cascade,
  place int not null check (place between 1 and 3),
  amount_cents int not null check (amount_cents in (30000, 20000, 10000)),
  prize_description text,
  status prize_status not null default 'PENDING',
  decided_at timestamptz,
  decided_by uuid references public.users (id) on delete set null,
  notes text,
  unique (week_start, place),
  unique (week_start, user_id)
);

create index if not exists idx_prizes_week
  on public.weekly_prizes (week_start, place);

-- Ranking de la semana -------------------------------------------------
-- Devuelve el top N de la semana (por defecto, la semana en curso):
-- solo usuarios con opt-in y apodo. Desempate: mayor confianza y luego
-- el primer aporte de la semana más temprano. SECURITY DEFINER porque
-- el ranking es público para quien tenga la app, pero solo expone el
-- apodo y los conteos de quienes dieron su consentimiento.
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
    and tr.reported_at >= (p_week_start::timestamptz at time zone 'America/Monterrey')
    and tr.reported_at < ((p_week_start + 7)::timestamptz at time zone 'America/Monterrey')
  group by u.id, u.nickname, ut.trust_score
  order by distinct_lights desc, trust_score desc, first_contribution asc
  limit p_limit;
$$;

-- Cualquiera con la app puede leer el ranking (solo apodos con opt-in).
grant execute on function public.top_collaborators_week(date, int) to anon, authenticated;

-- Registrar un premio (solo Admin, vía función SECURITY DEFINER) --------
-- Crea o actualiza el premio de un lugar en una semana. Si el usuario
-- queda descalificado, el lugar queda DISQUALIFIED y no se reasigna
-- solo: el Admin decide si el siguiente lugar sube o no.
create or replace function public.award_weekly_prize(
  p_week_start date,
  p_user_id uuid,
  p_place int,
  p_amount_cents int,
  p_prize_description text default null
)
returns void
language plpgsql
security definer
as $$
begin
  if not public.is_admin() then
    raise exception 'only_admin_can_award' using errcode = 'P0004';
  end if;
  if p_place not in (1, 2, 3) then
    raise exception 'invalid_place' using errcode = 'P0005';
  end if;
  insert into public.weekly_prizes
    (week_start, user_id, place, amount_cents, prize_description, status, decided_at, decided_by)
  values
    (p_week_start, p_user_id, p_place, p_amount_cents, p_prize_description, 'PENDING', now(), auth.uid())
  on conflict (week_start, place) do update set
    user_id = excluded.user_id,
    amount_cents = excluded.amount_cents,
    prize_description = excluded.prize_description,
    status = 'PENDING',
    decided_at = now(),
    decided_by = auth.uid(),
    notes = null;
end;
$$;

-- Cambiar el estado de un premio (solo Admin): CONTACTED, DELIVERED o
-- DISQUALIFIED (con motivo en notes).
create or replace function public.set_prize_status(
  p_prize_id uuid,
  p_status prize_status,
  p_notes text default null
)
returns void
language plpgsql
security definer
as $$
begin
  if not public.is_admin() then
    raise exception 'only_admin_can_review' using errcode = 'P0004';
  end if;
  update public.weekly_prizes set
    status = p_status,
    decided_at = now(),
    decided_by = auth.uid(),
    notes = p_notes
  where id = p_prize_id;
end;
$$;

-- RLS: premios ----------------------------------------------------------
alter table public.weekly_prizes enable row level security;

-- Cada quien ve solo sus premios; el Admin ve todos. La escritura es
-- solo por las funciones de arriba (Admin); no hay políticas de
-- INSERT/UPDATE directo para nadie.
drop policy if exists prizes_select_own on public.weekly_prizes;
create policy prizes_select_own on public.weekly_prizes
  for select using (user_id = auth.uid() or public.is_admin());
