-- Fase 2: rollen en rechten.
-- Vijf rollen (student, teacher, school_admin, professional, admin). Alleen professional en admin
-- mogen gebaren en video's indienen. Leerlingen kunnen hun eigen rol niet wijzigen.
-- Veilig om opnieuw uit te voeren (idempotent). Alles in één transactie: faalt er iets, dan
-- verandert er niets.

begin;

-- 1. Rollen-type en kolom ------------------------------------------------------------------
do $$ begin
  create type public.user_role as enum ('student', 'teacher', 'school_admin', 'professional', 'admin');
exception when duplicate_object then null;
end $$;

alter table public.profiles
  add column if not exists role public.user_role not null default 'student';

-- Bestaande admins (tabel public.admins) krijgen de rol admin.
update public.profiles p
   set role = 'admin'
  from public.admins a
 where a.user_id = p.id and p.role <> 'admin';

-- 2. Rol beschermen ------------------------------------------------------------------------
-- De policy "Gebruiker ziet alleen eigen profiel" (ALL) laat gebruikers hun eigen rij schrijven.
-- Deze trigger zorgt dat de rol daarbij nooit verandert, behalve door een admin of vanuit de
-- SQL-editor / server (rol postgres/service_role).
create or replace function public.protect_profile_role()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if current_user in ('authenticated', 'anon') and not public.is_admin() then
    if tg_op = 'INSERT' then
      new.role := 'student';
    elsif new.role is distinct from old.role then
      new.role := old.role;
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists profiles_protect_role on public.profiles;
create trigger profiles_protect_role
  before insert or update on public.profiles
  for each row execute function public.protect_profile_role();

-- 3. Functies voor de app ------------------------------------------------------------------
-- Eigen rol als tekst. De admins-tabel blijft leidend voor admin.
create or replace function public.my_role()
returns text
language sql
stable
security definer
set search_path = public
as $$
  select case
    when exists (select 1 from public.admins where user_id = auth.uid()) then 'admin'
    else coalesce((select role::text from public.profiles where id = auth.uid()), 'student')
  end;
$$;

create or replace function public.can_contribute()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.my_role() in ('professional', 'admin');
$$;

revoke all on function public.my_role() from public, anon;
revoke all on function public.can_contribute() from public, anon;
grant execute on function public.my_role() to authenticated;
grant execute on function public.can_contribute() to authenticated;

-- 4. Bijdragen alleen voor vakmensen en admins ------------------------------------------------
drop policy if exists "anyone can submit a pending sample" on public.gesture_samples;
drop policy if exists "contributors can submit a pending sample" on public.gesture_samples;
create policy "contributors can submit a pending sample"
  on public.gesture_samples
  for insert
  to authenticated
  with check (status = 'pending' and public.can_contribute());

drop policy if exists "anyone can upload a review video" on storage.objects;
drop policy if exists "contributors can upload a review video" on storage.objects;
create policy "contributors can upload a review video"
  on storage.objects
  for insert
  to authenticated
  with check (bucket_id = 'gesture-videos' and public.can_contribute());

commit;

-- Controle (los uitvoeren na de migratie; zou je eigen account als 'admin' moeten tonen):
--   select p.id, u.email, p.role from public.profiles p join auth.users u on u.id = p.id order by p.role;
--
-- Een vakman aanwijzen (e-mailadres invullen):
--   update public.profiles set role = 'professional'
--    where id = (select id from auth.users where email = 'naam@voorbeeld.nl');
