-- Fase 3, aanvulling: rol van docenten ook goed als ze nog geen profielrij hebben.
--
-- Met e-mailbevestiging aan ontstaat de profielrij pas bij het eerste inloggen. Koppelt de admin
-- een docent vóór dat moment, dan werd zijn rol niet gezet. Nu:
--   * my_role() kijkt ook naar school_staff;
--   * admin_add_staff() maakt de profielrij aan als die ontbreekt.
-- Uitvoeren NA 20261025210000_fase3_scholen.sql. Veilig om opnieuw uit te voeren.

begin;

create or replace function public.my_role()
returns text
language sql
stable
security definer
set search_path = public
as $$
  select case
    when exists (select 1 from public.admins where user_id = auth.uid()) then 'admin'
    when (select role::text from public.profiles where id = auth.uid()) in ('professional', 'school_admin', 'teacher')
      then (select role::text from public.profiles where id = auth.uid())
    when exists (select 1 from public.school_staff where user_id = auth.uid() and staff_role = 'school_admin') then 'school_admin'
    when exists (select 1 from public.school_staff where user_id = auth.uid()) then 'teacher'
    else 'student'
  end;
$$;

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
  -- Profielrij aanmaken als die ontbreekt; rol meegeven, maar nooit een admin of vakman terugzetten.
  insert into public.profiles (id, role) values (v_user, p_staff_role::public.user_role)
  on conflict (id) do update set role = excluded.role
   where public.profiles.role in ('student', 'teacher', 'school_admin');
  return 'ok';
end;
$$;

revoke all on function public.my_role() from public, anon;
grant execute on function public.my_role() to authenticated;
revoke all on function public.admin_add_staff(uuid, text, text) from public, anon;
grant execute on function public.admin_add_staff(uuid, text, text) to authenticated;

commit;
