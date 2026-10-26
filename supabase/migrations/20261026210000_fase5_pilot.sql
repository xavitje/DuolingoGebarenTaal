-- Fase 5: pilot en content
--
-- 1. Camera per leerling uit te zetten door de docent (bv. als ouders geen camera willen).
-- 2. Pilotmetingen: anonieme tellingen per gebaar per school per dag (pogingen en gelukt), en per
--    leerling alleen op welke dagen er geoefend is (voor "actieve leerlingen per week").
-- 3. Content-overzicht voor vakmensen: per gebaar demovideo ja/nee en het aantal opnames.
-- 4. Crashrapporten ouder dan 12 maanden worden mee opgeruimd door de dagelijkse taak.
--
-- Veilig om meerdere keren uit te voeren.

begin;

-- 1. Camera per leerling ----------------------------------------------------------------------
alter table public.class_students add column if not exists camera_allowed boolean not null default true;

drop function if exists public.class_overview(uuid);
drop function if exists public.my_school();
drop function if exists public.content_status(text);

create function public.class_overview(p_class_id uuid)
returns table (
  user_id uuid,
  first_name text,
  completed_lessons int,
  total_xp int,
  current_streak int,
  last_practice_date date,
  camera_allowed boolean
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
           p.last_practice_date,
           cs.camera_allowed
      from public.class_students cs
      left join public.profiles p on p.id = cs.user_id
     where cs.class_id = p_class_id
     order by lower(cs.first_name);
end;
$$;

create function public.my_school()
returns table (
  school_id uuid,
  school_name text,
  class_name text,
  staff_role text,
  license_end date,
  license_active boolean,
  is_student boolean,
  camera_allowed boolean
)
language sql
stable
security definer
set search_path = public
as $$
  select s.id, s.name, c.name, null::text, s.license_end, public.license_active(s.id), true, cs.camera_allowed
    from public.class_students cs
    join public.classes c on c.id = cs.class_id
    join public.schools s on s.id = c.school_id
   where cs.user_id = auth.uid()
  union all
  select s.id, s.name, null::text, st.staff_role, s.license_end, public.license_active(s.id), false, true
    from public.school_staff st
    join public.schools s on s.id = st.school_id
   where st.user_id = auth.uid();
$$;

create or replace function public.set_student_camera(p_class_id uuid, p_user_id uuid, p_allowed boolean)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not exists (select 1 from public.classes c where c.id = p_class_id and public.is_school_staff(c.school_id)) then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  update public.class_students set camera_allowed = p_allowed
   where class_id = p_class_id and user_id = p_user_id;
end;
$$;

-- 2. Pilotmetingen ----------------------------------------------------------------------------
create table if not exists public.practice_days (
  user_id uuid not null references auth.users(id) on delete cascade,
  day     date not null default current_date,
  primary key (user_id, day)
);
alter table public.practice_days enable row level security;  -- geen policies: alleen via functies

create table if not exists public.gesture_attempt_stats (
  id         bigserial primary key,
  school_id  uuid references public.schools(id) on delete cascade,  -- null = gebruiker zonder school
  gesture_id text not null,
  day        date not null default current_date,
  attempts   int not null default 0,
  successes  int not null default 0
);
create unique index if not exists gesture_attempt_stats_key
  on public.gesture_attempt_stats (coalesce(school_id, '00000000-0000-0000-0000-000000000000'::uuid), gesture_id, day);
alter table public.gesture_attempt_stats enable row level security;  -- geen policies: alleen via functies

-- Door de app aangeroepen na elke oefening met de camera (gelukt of opgegeven).
create or replace function public.log_practice_attempt(p_gesture_id text, p_success boolean)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_school uuid;
begin
  if auth.uid() is null then return; end if;
  select c.school_id into v_school
    from public.class_students cs join public.classes c on c.id = cs.class_id
   where cs.user_id = auth.uid()
   limit 1;

  insert into public.gesture_attempt_stats as g (school_id, gesture_id, day, attempts, successes)
  values (v_school, left(p_gesture_id, 64), current_date, 1, case when p_success then 1 else 0 end)
  on conflict (coalesce(school_id, '00000000-0000-0000-0000-000000000000'::uuid), gesture_id, day)
  do update set attempts = g.attempts + 1, successes = g.successes + excluded.successes;

  insert into public.practice_days (user_id, day) values (auth.uid(), current_date)
  on conflict do nothing;
end;
$$;

-- Voor de zelfcheck (zonder camera): telt alleen als oefendag, niet als herkenningspoging.
create or replace function public.mark_practice_day()
returns void
language sql
security definer
set search_path = public
as $$
  insert into public.practice_days (user_id, day)
  select auth.uid(), current_date where auth.uid() is not null
  on conflict do nothing;
$$;

-- Per week: actieve leerlingen, totaal leerlingen, pogingen en gelukt. Voor admin en de school zelf.
create or replace function public.school_pilot_stats(p_school_id uuid, p_weeks int default 12)
returns table (
  week_start date,
  active_students int,
  total_students int,
  attempts int,
  successes int
)
language plpgsql
stable
security definer
set search_path = public
as $$
#variable_conflict use_column
begin
  if not (public.is_admin() or public.is_school_staff(p_school_id)) then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  return query
    with weeks as (
      select (date_trunc('week', current_date) - (n || ' weeks')::interval)::date as week_start
        from generate_series(0, greatest(p_weeks, 1) - 1) as n
    ), students as (
      select cs.user_id from public.class_students cs
        join public.classes c on c.id = cs.class_id
       where c.school_id = p_school_id
    )
    select w.week_start,
           (select count(distinct pd.user_id)::int from public.practice_days pd
              where pd.user_id in (select user_id from students)
                and pd.day >= w.week_start and pd.day < w.week_start + 7),
           (select count(*)::int from students),
           coalesce((select sum(g.attempts)::int from public.gesture_attempt_stats g
              where g.school_id = p_school_id and g.day >= w.week_start and g.day < w.week_start + 7), 0),
           coalesce((select sum(g.successes)::int from public.gesture_attempt_stats g
              where g.school_id = p_school_id and g.day >= w.week_start and g.day < w.week_start + 7), 0)
      from weeks w
     order by w.week_start desc;
end;
$$;

-- Welke gebaren worden het vaakst niet herkend? (alle scholen, of één school). Alleen admin.
create or replace function public.admin_gesture_stats(p_school_id uuid default null, p_days int default 56)
returns table (gesture_id text, attempts int, successes int)
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
    select g.gesture_id, sum(g.attempts)::int, sum(g.successes)::int
      from public.gesture_attempt_stats g
     where g.day >= current_date - p_days
       and (p_school_id is null or g.school_id = p_school_id)
     group by g.gesture_id
     order by (sum(g.successes)::float / greatest(sum(g.attempts), 1)) asc, sum(g.attempts) desc;
end;
$$;

-- 3. Content-overzicht voor vakmensen -------------------------------------------------------------
create function public.content_status(p_sign_language text default 'ngt')
returns table (gesture_id text, name text, unit_id text, has_demo_video boolean, pending int, approved int)
language plpgsql
stable
security definer
set search_path = public
as $$
#variable_conflict use_column
begin
  if not public.can_contribute() then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  return query
    select g.id::text,
           g.name::text,
           g.unit_id::text,
           coalesce(g.demo_video_url, '') <> '',
           (select count(*)::int from public.gesture_samples s
             where s.gesture_id = g.id and s.sign_language = g.sign_language and s.status = 'pending'),
           (select count(*)::int from public.gesture_samples s
             where s.gesture_id = g.id and s.sign_language = g.sign_language and s.status = 'approved')
      from public.gestures g
     where g.sign_language = p_sign_language
     order by g.unit_id, g.order_index, g.id;
end;
$$;

-- 4. Crashrapporten 12 maanden bewaren, mee in de dagelijkse taak ---------------------------------
create or replace function public.purge_expired_schools(p_grace_days int default 90)
returns int
language plpgsql
security definer
set search_path = public
as $$
declare
  s record;
  total int := 0;
begin
  for s in
    select id from public.schools
     where license_end < current_date - p_grace_days
       and data_purged_at is null
  loop
    total := total + public.purge_school_data(s.id);
  end loop;

  if to_regclass('public.crash_reports') is not null then
    delete from public.crash_reports where created_at < now() - interval '12 months';
  end if;
  return total;
end;
$$;

revoke all on function public.class_overview(uuid) from public, anon;
revoke all on function public.my_school() from public, anon;
revoke all on function public.set_student_camera(uuid, uuid, boolean) from public, anon;
revoke all on function public.log_practice_attempt(text, boolean) from public, anon;
revoke all on function public.mark_practice_day() from public, anon;
grant execute on function public.mark_practice_day() to authenticated;
revoke all on function public.school_pilot_stats(uuid, int) from public, anon;
revoke all on function public.admin_gesture_stats(uuid, int) from public, anon;
revoke all on function public.content_status(text) from public, anon;
revoke all on function public.purge_expired_schools(int) from public, anon, authenticated;
grant execute on function public.class_overview(uuid) to authenticated;
grant execute on function public.my_school() to authenticated;
grant execute on function public.set_student_camera(uuid, uuid, boolean) to authenticated;
grant execute on function public.log_practice_attempt(text, boolean) to authenticated;
grant execute on function public.school_pilot_stats(uuid, int) to authenticated;
grant execute on function public.admin_gesture_stats(uuid, int) to authenticated;
grant execute on function public.content_status(text) to authenticated;

commit;
