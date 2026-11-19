-- Semáforos MX — Supabase v1.2: verificación de Colaborador (híbrido)
-- ============================================================
-- Ejecutar DESPUÉS de 001_schema.sql, en el SQL Editor.
--
-- Regla aprobada por Alan (18 nov 2026, SPEC §6.9):
-- - El beneficio sin anuncios es AUTOMÁTICO por cuota (20 semáforos
--   distintos al mes, §6.5): no necesita rol ni verificación.
-- - El rol COLLABORATOR es el SELLO de conductor verificado: el usuario
--   sube una foto de su licencia de chofer (tipo C/D en Nuevo León) y
--   el Admin la revisa. Al aprobarla, el rol pasa a COLLABORATOR.
--
-- La foto de la licencia es un dato sensible (LFPDPPP): vive en un
-- bucket PRIVADO (solo el dueño y el Admin pueden verla), se revisa
-- una sola vez y SE BORRA tras la verificación. Solo queda guardado el
-- estado «verificado», la fecha y el tipo de licencia, nunca la imagen.

-- Bucket privado para las fotos de licencia --------------------------
insert into storage.buckets (id, name, public)
values ('license-photos', 'license-photos', false)
on conflict (id) do nothing;

-- Solicitudes de verificación ----------------------------------------
do $$ begin
  create type verification_status as enum ('PENDING', 'APPROVED', 'REJECTED');
exception when duplicate_object then null; end $$;

create table if not exists public.verification_requests (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.users (id) on delete cascade,
  -- Tipo de licencia de chofer ('C', 'D' en Nuevo León; otras categorías
  -- de otros estados cuando el piloto crezca).
  license_type text not null,
  -- Ruta en el bucket license-photos: '{user_id}/{archivo}'. Se pone
  -- en NULL al borrar la foto tras la revisión.
  photo_path text,
  status verification_status not null default 'PENDING',
  submitted_at timestamptz not null default now(),
  reviewed_at timestamptz,
  reviewed_by uuid references public.users (id) on delete set null,
  rejection_reason text
);

-- Una sola solicitud pendiente por usuario (puede reintentarlo tras
-- un rechazo, una vez resuelta la anterior).
create unique index if not exists idx_verification_one_pending
  on public.verification_requests (user_id)
  where status = 'PENDING';

create index if not exists idx_verification_status
  on public.verification_requests (status, submitted_at);

-- Resolución (solo Admin, vía función SECURITY DEFINER): aprueba o
-- rechaza la solicitud; al aprobar, el rol pasa a COLLABORATOR (salvo
-- que ya sea ADMIN). La foto se borra por separado desde Storage
-- (panel de Supabase o API con service key) y photo_path queda en NULL.
create or replace function public.resolve_verification(
  p_request_id uuid,
  p_approved boolean,
  p_reason text default null
)
returns void
language plpgsql
security definer
as $$
declare
  v_user_id uuid;
  v_status verification_status;
begin
  if not public.is_admin() then
    raise exception 'only_admin_can_review' using errcode = 'P0004';
  end if;
  select user_id, status into v_user_id, v_status
  from public.verification_requests
  where id = p_request_id;
  if v_user_id is null then
    raise exception 'request_not_found' using errcode = 'P0005';
  end if;
  if v_status <> 'PENDING' then
    raise exception 'request_already_resolved' using errcode = 'P0006';
  end if;
  update public.verification_requests set
    status = case when p_approved then 'APPROVED'::verification_status
                  else 'REJECTED'::verification_status end,
    reviewed_at = now(),
    reviewed_by = auth.uid(),
    rejection_reason = case when p_approved then null else p_reason end,
    photo_path = null
  where id = p_request_id;
  if p_approved then
    update public.users set role = 'COLLABORATOR'
    where id = v_user_id and role <> 'ADMIN';
  end if;
end;
$$;

-- RLS: solicitudes ----------------------------------------------------
alter table public.verification_requests enable row level security;

drop policy if exists verif_insert_own on public.verification_requests;
create policy verif_insert_own on public.verification_requests
  for insert with check (
    auth.uid() is not null
    and user_id = auth.uid()
    and status = 'PENDING'
  );
drop policy if exists verif_select_own on public.verification_requests;
create policy verif_select_own on public.verification_requests
  for select using (user_id = auth.uid() or public.is_admin());
-- La resolución la hace solo la función resolve_verification (Admin);
-- no hay política de UPDATE directo para nadie.

-- RLS: fotos en Storage ------------------------------------------------
-- Ruta obligatoria: '{user_id}/{archivo}'. El dueño solo sube dentro
-- de su carpeta; solo el Admin (y el dueño) pueden ver o borrar.
drop policy if exists license_upload_own on storage.objects;
create policy license_upload_own on storage.objects
  for insert with check (
    bucket_id = 'license-photos'
    and (storage.foldername(name))[1] = auth.uid()::text
  );
drop policy if exists license_select_own_admin on storage.objects;
create policy license_select_own_admin on storage.objects
  for select using (
    bucket_id = 'license-photos'
    and ((storage.foldername(name))[1] = auth.uid()::text
         or public.is_admin())
  );
drop policy if exists license_delete_admin on storage.objects;
create policy license_delete_admin on storage.objects
  for delete using (
    bucket_id = 'license-photos'
    and public.is_admin()
  );
