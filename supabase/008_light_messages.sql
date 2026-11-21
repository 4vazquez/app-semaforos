-- =====================================================================
-- Migración 008 — Mensajería ligera por semáforo (Vía Verde)
-- ---------------------------------------------------------------------
-- Idea de Alan (2026-11-21): en un semáforo, un usuario puede preguntar
-- «¿Ahí hay algo?» y los demás usuarios presentes en ese semáforo
-- responden con opciones PREDEFINIDAS (Tráfico / Choque / Mercado sobre
-- ruedas / Obra / Nada). Sin texto libre, sin feed: solo pregunta +
-- respuestas de un toque, visibles únicamente para quienes están en
-- ese semáforo (≤ 150 m, lo aplica el cliente), con apodo (sin
-- identidad real) y expiración rápida (30 min). Las respuestas NO
-- cuentan para la cuota de Colaborador ni para el ranking.
--
-- Idempotente: se puede correr varias veces sin daño.
-- =====================================================================

-- Preguntas activas por semáforo. Una sola pregunta vigente por
-- semáforo a la vez: la aplicación reutiliza la activa si existe.
create table if not exists public.light_questions (
  id uuid primary key default gen_random_uuid(),
  light_id text not null references public.traffic_lights (id) on delete cascade,
  asked_by uuid references public.users (id) on delete set null,
  nickname text not null default 'Conductor',
  asked_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '30 minutes')
);

-- Respuestas predefinidas. Una respuesta por usuario por pregunta
-- (unique): si el usuario cambia de opinión, se actualiza la fila.
create table if not exists public.light_answers (
  id uuid primary key default gen_random_uuid(),
  question_id uuid not null references public.light_questions (id) on delete cascade,
  answered_by uuid references public.users (id) on delete set null,
  option text not null check (option in ('TRAFICO', 'CHOQUE', 'MERCADO', 'OBRA', 'NADA')),
  answered_at timestamptz not null default now(),
  unique (question_id, answered_by)
);

create index if not exists idx_light_questions_light
  on public.light_questions (light_id, expires_at desc);
create index if not exists idx_light_answers_question
  on public.light_answers (question_id);

-- ---------------------------------------------------------------------
-- RLS: solo usuarios con sesión pueden leer y escribir (la mensajería
-- es un aporte: exige login, igual que sync/agregar/reportar).
-- ---------------------------------------------------------------------
alter table public.light_questions enable row level security;
alter table public.light_answers enable row level security;

drop policy if exists "light_questions_select" on public.light_questions;
create policy "light_questions_select" on public.light_questions
  for select to authenticated using (true);

drop policy if exists "light_questions_insert" on public.light_questions;
create policy "light_questions_insert" on public.light_questions
  for insert to authenticated with check (asked_by = auth.uid());

drop policy if exists "light_answers_select" on public.light_answers;
create policy "light_answers_select" on public.light_answers
  for select to authenticated using (true);

drop policy if exists "light_answers_insert" on public.light_answers;
create policy "light_answers_insert" on public.light_answers
  for insert to authenticated with check (answered_by = auth.uid());

drop policy if exists "light_answers_update" on public.light_answers;
create policy "light_answers_update" on public.light_answers
  for update to authenticated
  using (answered_by = auth.uid())
  with check (answered_by = auth.uid());
