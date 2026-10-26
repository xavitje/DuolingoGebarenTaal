-- Pincodes zichtbaar voor docenten, feedbackformulier voor docenten, en demovideo's door vakmensen
-- met beoordeling door een tweede vakman.
--
-- Na deze migratie: edge function `review-demo-video` deployen (zie BEHEER.md).
-- Veilig om meerdere keren uit te voeren.

begin;

-- 1. Pincodes altijd inzienbaar voor de docent ---------------------------------------------------
-- Keuze van de eigenaar: een pincode van een leerling is geen gevoelig gegeven (alleen toegang tot
-- een leerapp, geen beelden). Inloggen controleert nog steeds de hash; de leesbare pincode is er
-- alleen voor de docent (via class_overview, alleen voor staff van die school).
alter table public.student_secrets add column if not exists pin_plain text;

create or replace function public.set_student_secret(p_user_id uuid, p_password text, p_pin text)
returns void
language sql
volatile
security definer
set search_path = public, extensions
as $$
  insert into public.student_secrets (user_id, login_password, pin_hash, pin_plain)
  values (p_user_id, p_password, extensions.crypt(p_pin, extensions.gen_salt('bf')), p_pin)
  on conflict (user_id) do update
    set pin_hash = excluded.pin_hash, pin_plain = excluded.pin_plain, failed_attempts = 0, locked_until = null,
        login_password = coalesce(nullif(excluded.login_password, ''), public.student_secrets.login_password);
$$;

drop function if exists public.class_overview(uuid);
create function public.class_overview(p_class_id uuid)
returns table (
  user_id uuid,
  first_name text,
  completed_lessons int,
  total_xp int,
  current_streak int,
  last_practice_date date,
  camera_allowed boolean,
  pin text
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
           cs.camera_allowed,
           ss.pin_plain
      from public.class_students cs
      left join public.profiles p on p.id = cs.user_id
      left join public.student_secrets ss on ss.user_id = cs.user_id
     where cs.class_id = p_class_id
     order by lower(cs.first_name);
end;
$$;
revoke all on function public.class_overview(uuid) from public, anon;
grant execute on function public.class_overview(uuid) to authenticated;

-- 2. Feedback van docenten ----------------------------------------------------------------------
create table if not exists public.teacher_feedback (
  id              uuid primary key default gen_random_uuid(),
  school_id       uuid references public.schools(id) on delete cascade,
  user_id         uuid references auth.users(id) on delete set null,
  created_at      timestamptz not null default now(),
  usage           text check (usage in ('niet', 'soms', 'wekelijks', 'vaker')),
  pupils_enjoy    int check (pupils_enjoy between 1 and 5),
  recognition     int check (recognition between 1 and 5),
  ease_of_use     int check (ease_of_use between 1 and 5),
  recommend       int check (recommend between 0 and 10),
  improve         text check (char_length(improve) <= 2000),
  best            text check (char_length(best) <= 2000),
  may_quote       boolean not null default false
);
alter table public.teacher_feedback enable row level security;  -- geen policies: alleen via functies

create or replace function public.submit_teacher_feedback(
  p_usage text, p_pupils_enjoy int, p_recognition int, p_ease_of_use int, p_recommend int,
  p_improve text, p_best text, p_may_quote boolean
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_school uuid;
begin
  select school_id into v_school from public.school_staff where user_id = auth.uid() limit 1;
  if v_school is null then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  insert into public.teacher_feedback
    (school_id, user_id, usage, pupils_enjoy, recognition, ease_of_use, recommend, improve, best, may_quote)
  values (v_school, auth.uid(), p_usage, p_pupils_enjoy, p_recognition, p_ease_of_use, p_recommend,
          nullif(trim(p_improve), ''), nullif(trim(p_best), ''), coalesce(p_may_quote, false));
end;
$$;

-- Vraag om feedback: 14 dagen na koppeling aan de school, daarna elke 4 weken.
create or replace function public.feedback_due()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.school_staff st
     where st.user_id = auth.uid()
       and st.created_at <= now() - interval '14 days'
  ) and not exists (
    select 1 from public.teacher_feedback f
     where f.user_id = auth.uid()
       and f.created_at > now() - interval '28 days'
  );
$$;

create or replace function public.admin_list_feedback(p_school_id uuid)
returns table (
  created_at timestamptz, teacher_email text, usage text, pupils_enjoy int, recognition int,
  ease_of_use int, recommend int, improve text, best text, may_quote boolean
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
    select f.created_at, u.email::text, f.usage, f.pupils_enjoy, f.recognition, f.ease_of_use,
           f.recommend, f.improve, f.best, f.may_quote
      from public.teacher_feedback f
      left join auth.users u on u.id = f.user_id
     where f.school_id = p_school_id
     order by f.created_at desc;
end;
$$;

revoke all on function public.submit_teacher_feedback(text, int, int, int, int, text, text, boolean) from public, anon;
revoke all on function public.feedback_due() from public, anon;
revoke all on function public.admin_list_feedback(uuid) from public, anon;
grant execute on function public.submit_teacher_feedback(text, int, int, int, int, text, text, boolean) to authenticated;
grant execute on function public.feedback_due() to authenticated;
grant execute on function public.admin_list_feedback(uuid) to authenticated;

-- 3. Demovideo's: opnemen door een vakman, beoordelen door een andere vakman ----------------------
-- Opnames komen in de privé-bucket 'demo-review'. Pas na goedkeuring kopieert de edge function
-- `review-demo-video` de video naar de openbare bucket 'demo-videos' en zet hem bij het gebaar.
insert into storage.buckets (id, name, public) values ('demo-review', 'demo-review', false)
  on conflict (id) do nothing;
insert into storage.buckets (id, name, public) values ('demo-videos', 'demo-videos', true)
  on conflict (id) do nothing;

drop policy if exists "contributors upload demo for review" on storage.objects;
create policy "contributors upload demo for review"
  on storage.objects for insert to authenticated
  with check (bucket_id = 'demo-review' and public.can_contribute());

drop policy if exists "contributors view demos for review" on storage.objects;
create policy "contributors view demos for review"
  on storage.objects for select to authenticated
  using (bucket_id = 'demo-review' and public.can_contribute());

create table if not exists public.demo_videos (
  id            uuid primary key default gen_random_uuid(),
  gesture_id    text not null,
  sign_language text not null default 'ngt',
  storage_path  text not null,
  recorded_by   uuid references auth.users(id) on delete set null,
  status        text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
  reviewed_by   uuid references auth.users(id) on delete set null,
  reviewed_at   timestamptz,
  review_note   text,
  created_at    timestamptz not null default now()
);
alter table public.demo_videos enable row level security;

drop policy if exists "contributors see demo videos" on public.demo_videos;
create policy "contributors see demo videos"
  on public.demo_videos for select to authenticated
  using (public.can_contribute());

drop policy if exists "contributors submit demo videos" on public.demo_videos;
create policy "contributors submit demo videos"
  on public.demo_videos for insert to authenticated
  with check (public.can_contribute() and recorded_by = auth.uid() and status = 'pending'
              and reviewed_by is null and reviewed_at is null);
-- Geen update/delete-policy: beoordelen gaat alleen via de edge function.

-- Content-checklist uitgebreid met wachtende demovideo's.
drop function if exists public.content_status(text);
create function public.content_status(p_sign_language text default 'ngt')
returns table (gesture_id text, name text, unit_id text, has_demo_video boolean, pending int, approved int, demo_pending int)
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
             where s.gesture_id = g.id and s.sign_language = g.sign_language and s.status = 'approved'),
           (select count(*)::int from public.demo_videos d
             where d.gesture_id = g.id and d.sign_language = g.sign_language and d.status = 'pending')
      from public.gestures g
     where g.sign_language = p_sign_language
     order by g.unit_id, g.order_index, g.id;
end;
$$;
revoke all on function public.content_status(text) from public, anon;
grant execute on function public.content_status(text) to authenticated;

commit;
