-- Fase 3: scholen, klassen, leerlingen en licenties.
--
-- Model
--   schools         een school met licentie (einddatum) en maximaal aantal leerlingplekken
--   school_staff    docenten en schoolbeheerders van een school
--   classes         klassen; elke klas heeft een klascode van 6 tekens
--   class_students  leerlingen in een klas (alleen voornaam; geen e-mail)
--   student_secrets inloggegevens van leerlingen (gehashte pin, intern wachtwoord).
--                   Geen enkele client kan deze tabel lezen; alleen de edge functions.
--
-- Leerlingen loggen in met klascode + naam kiezen + 4-cijferige pin (edge function
-- student-login). Docenten maken leerlingen aan via de edge function school-students.
--
-- Veilig om opnieuw uit te voeren. Alles in één transactie.

begin;

create extension if not exists pgcrypto with schema extensions;

-- 1. Tabellen -------------------------------------------------------------------------------

create table if not exists public.schools (
  id            uuid primary key default gen_random_uuid(),
  name          text not null,
  brin          text,
  contact_name  text,
  contact_email text,
  max_students  int  not null default 30 check (max_students >= 0),
  license_start date not null default current_date,
  license_end   date not null default (current_date + interval '1 year')::date,
  created_at    timestamptz not null default now()
);

create table if not exists public.school_staff (
  school_id  uuid not null references public.schools(id) on delete cascade,
  user_id    uuid not null references auth.users(id) on delete cascade,
  staff_role text not null check (staff_role in ('teacher', 'school_admin')),
  created_at timestamptz not null default now(),
  primary key (school_id, user_id)
);

-- Klascode: 6 tekens zonder verwarrende tekens (geen 0/O, 1/I/L).
create or replace function public.generate_class_code()
returns text
language plpgsql
volatile
set search_path = public
as $$
declare
  alphabet constant text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
  result text := '';
begin
  for i in 1..6 loop
    result := result || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
  end loop;
  return result;
end;
$$;

create table if not exists public.classes (
  id         uuid primary key default gen_random_uuid(),
  school_id  uuid not null references public.schools(id) on delete cascade,
  name       text not null,
  code       text not null unique default public.generate_class_code(),
  created_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now()
);

create table if not exists public.class_students (
  class_id   uuid not null references public.classes(id) on delete cascade,
  user_id    uuid not null references auth.users(id) on delete cascade,
  first_name text not null,
  created_at timestamptz not null default now(),
  primary key (class_id, user_id)
);
-- Een leerling zit in precies één klas.
create unique index if not exists class_students_user_unique on public.class_students(user_id);

create table if not exists public.student_secrets (
  user_id         uuid primary key references auth.users(id) on delete cascade,
  login_password  text not null,
  pin_hash        text not null,
  failed_attempts int  not null default 0,
  locked_until    timestamptz
);

alter table public.schools         enable row level security;
alter table public.school_staff    enable row level security;
alter table public.classes         enable row level security;
alter table public.class_students  enable row level security;
alter table public.student_secrets enable row level security;
-- student_secrets krijgt bewust GEEN policies: alleen de service role (edge functions) kan erbij.

-- 2. Hulpfuncties ---------------------------------------------------------------------------

-- Is de ingelogde gebruiker docent/schoolbeheerder van deze school (of admin)?
create or replace function public.is_school_staff(p_school_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.is_admin()
      or exists (select 1 from public.school_staff
                  where school_id = p_school_id and user_id = auth.uid());
$$;

create or replace function public.license_active(p_school_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (select 1 from public.schools
                  where id = p_school_id and current_date between license_start and license_end);
$$;

create or replace function public.school_seats_used(p_school_id uuid)
returns int
language sql
stable
security definer
set search_path = public
as $$
  select count(*)::int
    from public.class_students cs join public.classes c on c.id = cs.class_id
   where c.school_id = p_school_id;
$$;

-- Hulpfuncties die RLS omzeilen (security definer), zodat de policies hieronder niet naar
-- elkaars tabellen verwijzen. Dat zou "infinite recursion detected in policy" geven.
create or replace function public.class_school(p_class_id uuid)
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select school_id from public.classes where id = p_class_id;
$$;

create or replace function public.is_class_student(p_class_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (select 1 from public.class_students where class_id = p_class_id and user_id = auth.uid());
$$;

create or replace function public.is_school_student(p_school_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (select 1 from public.class_students cs join public.classes c on c.id = cs.class_id
                  where cs.user_id = auth.uid() and c.school_id = p_school_id);
$$;

-- 3. Rechten (RLS) --------------------------------------------------------------------------

drop policy if exists "schools: staff, students and admins read" on public.schools;
create policy "schools: staff, students and admins read" on public.schools
  for select to authenticated
  using (public.is_school_staff(id) or public.is_school_student(id));

drop policy if exists "schools: admins write" on public.schools;
create policy "schools: admins write" on public.schools
  for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

drop policy if exists "school_staff: same school or admin reads" on public.school_staff;
create policy "school_staff: same school or admin reads" on public.school_staff
  for select to authenticated
  using (user_id = auth.uid() or public.is_school_staff(school_id));

drop policy if exists "school_staff: admins write" on public.school_staff;
create policy "school_staff: admins write" on public.school_staff
  for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

drop policy if exists "classes: staff and own students read" on public.classes;
create policy "classes: staff and own students read" on public.classes
  for select to authenticated
  using (public.is_school_staff(school_id) or public.is_class_student(id));

drop policy if exists "classes: staff create" on public.classes;
create policy "classes: staff create" on public.classes
  for insert to authenticated
  with check (public.is_school_staff(school_id));

drop policy if exists "classes: staff update" on public.classes;
create policy "classes: staff update" on public.classes
  for update to authenticated
  using (public.is_school_staff(school_id)) with check (public.is_school_staff(school_id));

drop policy if exists "classes: staff delete" on public.classes;
create policy "classes: staff delete" on public.classes
  for delete to authenticated
  using (public.is_school_staff(school_id));

drop policy if exists "class_students: staff and self read" on public.class_students;
create policy "class_students: staff and self read" on public.class_students
  for select to authenticated
  using (user_id = auth.uid() or public.is_school_staff(public.class_school(class_id)));
-- Schrijven in class_students gaat alleen via de edge function school-students (service role).

-- 4. Functies voor de app -------------------------------------------------------------------

-- Namenlijst van een klas voor het inlogscherm. Alleen voornamen; alleen bij geldige licentie.
create or replace function public.class_roster(p_code text)
returns table (user_id uuid, first_name text)
language sql
stable
security definer
set search_path = public
as $$
  select cs.user_id, cs.first_name
    from public.classes c
    join public.class_students cs on cs.class_id = c.id
   where c.code = upper(trim(p_code))
     and public.license_active(c.school_id)
   order by lower(cs.first_name);
$$;

-- Pin controleren (alleen voor de edge function). Geeft het interne wachtwoord terug bij een
-- juiste pin, anders null. Na 5 foute pogingen 15 minuten geblokkeerd.
create or replace function public.verify_student_pin(p_code text, p_user_id uuid, p_pin text)
returns text
language plpgsql
volatile
security definer
set search_path = public, extensions
as $$
declare
  s public.student_secrets%rowtype;
begin
  if not exists (
    select 1 from public.classes c join public.class_students cs on cs.class_id = c.id
     where c.code = upper(trim(p_code)) and cs.user_id = p_user_id and public.license_active(c.school_id)
  ) then
    return null;
  end if;

  select * into s from public.student_secrets where user_id = p_user_id for update;
  if not found then
    return null;
  end if;
  if s.locked_until is not null and s.locked_until > now() then
    raise exception 'locked' using errcode = 'P0001';
  end if;

  if extensions.crypt(p_pin, s.pin_hash) = s.pin_hash then
    update public.student_secrets set failed_attempts = 0, locked_until = null where user_id = p_user_id;
    return s.login_password;
  end if;

  update public.student_secrets
     set failed_attempts = s.failed_attempts + 1,
         locked_until = case when s.failed_attempts + 1 >= 5 then now() + interval '15 minutes' else null end
   where user_id = p_user_id;
  return null;
end;
$$;

-- Pin opslaan (alleen voor de edge function).
create or replace function public.set_student_secret(p_user_id uuid, p_password text, p_pin text)
returns void
language sql
volatile
security definer
set search_path = public, extensions
as $$
  insert into public.student_secrets (user_id, login_password, pin_hash)
  values (p_user_id, p_password, extensions.crypt(p_pin, extensions.gen_salt('bf')))
  on conflict (user_id) do update
    set pin_hash = excluded.pin_hash, failed_attempts = 0, locked_until = null,
        login_password = coalesce(nullif(excluded.login_password, ''), public.student_secrets.login_password);
$$;

-- Voortgang per leerling voor het docentoverzicht.
create or replace function public.class_overview(p_class_id uuid)
returns table (
  user_id uuid,
  first_name text,
  completed_lessons int,
  total_xp int,
  current_streak int,
  last_practice_date date
)
language plpgsql
stable
security definer
set search_path = public
as $$
#variable_conflict use_column
begin
  if not exists (select 1 from public.classes c where c.id = p_class_id and public.is_school_staff(c.school_id)) then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  return query
    select cs.user_id,
           cs.first_name,
           (select count(distinct up.gesture_id)::int from public.user_progress up where up.user_id = cs.user_id),
           coalesce(p.total_xp, 0),
           coalesce(p.current_streak, 0),
           p.last_practice_date
      from public.class_students cs
      left join public.profiles p on p.id = cs.user_id
     where cs.class_id = p_class_id
     order by lower(cs.first_name);
end;
$$;

-- Bij welke school/klas hoort de ingelogde gebruiker, en is de licentie geldig?
create or replace function public.my_school()
returns table (
  school_id uuid,
  school_name text,
  class_name text,
  staff_role text,
  license_end date,
  license_active boolean,
  is_student boolean
)
language sql
stable
security definer
set search_path = public
as $$
  select s.id, s.name, c.name, null::text, s.license_end, public.license_active(s.id), true
    from public.class_students cs
    join public.classes c on c.id = cs.class_id
    join public.schools s on s.id = c.school_id
   where cs.user_id = auth.uid()
  union all
  select s.id, s.name, null::text, st.staff_role, s.license_end, public.license_active(s.id), false
    from public.school_staff st
    join public.schools s on s.id = st.school_id
   where st.user_id = auth.uid();
$$;

-- Beheer: scholen met gebruikte plekken en aantal docenten.
create or replace function public.admin_list_schools()
returns table (
  id uuid, name text, brin text, contact_name text, contact_email text,
  max_students int, license_start date, license_end date,
  seats_used int, staff_count int, license_active boolean
)
language plpgsql
stable
security definer
set search_path = public
as $$
#variable_conflict use_column
begin
  if not public.is_admin() then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  return query
    select s.id, s.name, s.brin, s.contact_name, s.contact_email, s.max_students,
           s.license_start, s.license_end, public.school_seats_used(s.id),
           (select count(*)::int from public.school_staff st where st.school_id = s.id),
           public.license_active(s.id)
      from public.schools s
     order by s.name;
end;
$$;

-- Beheer: docenten/schoolbeheerders van een school, met e-mail.
create or replace function public.admin_list_staff(p_school_id uuid)
returns table (user_id uuid, email text, staff_role text)
language plpgsql
stable
security definer
set search_path = public, auth
as $$
#variable_conflict use_column
begin
  if not public.is_admin() then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  return query
    select st.user_id, u.email::text, st.staff_role
      from public.school_staff st join auth.users u on u.id = st.user_id
     where st.school_id = p_school_id
     order by u.email;
end;
$$;

-- Beheer: docent of schoolbeheerder koppelen via e-mail. Het account moet al bestaan.
-- Geeft 'ok' of 'not_found' terug.
create or replace function public.admin_add_staff(p_school_id uuid, p_email text, p_staff_role text)
returns text
language plpgsql
volatile
security definer
set search_path = public, auth
as $$
declare
  v_user uuid;
begin
  if not public.is_admin() then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  if p_staff_role not in ('teacher', 'school_admin') then
    raise exception 'invalid role';
  end if;
  select id into v_user from auth.users where lower(email) = lower(trim(p_email));
  if v_user is null then
    return 'not_found';
  end if;
  insert into public.school_staff (school_id, user_id, staff_role)
  values (p_school_id, v_user, p_staff_role)
  on conflict (school_id, user_id) do update set staff_role = excluded.staff_role;
  -- Rol meegeven, maar nooit een admin of vakman "terugzetten".
  update public.profiles set role = p_staff_role::public.user_role
   where id = v_user and role in ('student', 'teacher', 'school_admin');
  return 'ok';
end;
$$;

create or replace function public.admin_remove_staff(p_school_id uuid, p_user_id uuid)
returns void
language plpgsql
volatile
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  delete from public.school_staff where school_id = p_school_id and user_id = p_user_id;
  update public.profiles set role = 'student'
   where id = p_user_id and role in ('teacher', 'school_admin')
     and not exists (select 1 from public.school_staff where user_id = p_user_id);
end;
$$;

-- 5. Wie mag wat aanroepen ------------------------------------------------------------------

revoke all on function public.class_roster(text) from public;
grant execute on function public.class_roster(text) to anon, authenticated;

revoke all on function public.verify_student_pin(text, uuid, text) from public, anon, authenticated;
revoke all on function public.set_student_secret(uuid, text, text) from public, anon, authenticated;
grant execute on function public.verify_student_pin(text, uuid, text) to service_role;
grant execute on function public.set_student_secret(uuid, text, text) to service_role;

revoke all on function public.class_overview(uuid) from public, anon;
revoke all on function public.my_school() from public, anon;
revoke all on function public.admin_list_schools() from public, anon;
revoke all on function public.admin_list_staff(uuid) from public, anon;
revoke all on function public.admin_add_staff(uuid, text, text) from public, anon;
revoke all on function public.admin_remove_staff(uuid, uuid) from public, anon;
revoke all on function public.is_school_staff(uuid) from public, anon;
grant execute on function public.class_overview(uuid) to authenticated;
grant execute on function public.my_school() to authenticated;
grant execute on function public.admin_list_schools() to authenticated;
grant execute on function public.admin_list_staff(uuid) to authenticated;
grant execute on function public.admin_add_staff(uuid, text, text) to authenticated;
grant execute on function public.admin_remove_staff(uuid, uuid) to authenticated;
grant execute on function public.is_school_staff(uuid) to authenticated, service_role;
revoke all on function public.class_school(uuid) from public, anon;
revoke all on function public.is_class_student(uuid) from public, anon;
revoke all on function public.is_school_student(uuid) from public, anon;
grant execute on function public.class_school(uuid) to authenticated, service_role;
grant execute on function public.is_class_student(uuid) to authenticated;
grant execute on function public.is_school_student(uuid) to authenticated;
grant execute on function public.school_seats_used(uuid) to authenticated, service_role;
grant execute on function public.license_active(uuid) to authenticated, service_role;

commit;
