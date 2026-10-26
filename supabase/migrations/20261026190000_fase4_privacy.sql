-- Fase 4: privacy en compliance voor scholen
--
-- 1. Alle persoonsgegevens van een gebruiker verdwijnen mee als het account wordt verwijderd
--    (een docent die een leerling verwijdert, of het opschonen na het einde van een licentie).
-- 2. Na het einde van een licentie worden de leerlinggegevens van die school na een vaste termijn
--    (90 dagen) automatisch gewist. Die termijn moet overeenkomen met de privacybijsluiter.
-- 3. Bestaande crashrapporten worden anoniem gemaakt; pushtokens van leerlingen worden gewist.
--
-- Veilig om meerdere keren uit te voeren.

begin;

-- 1. Cascade bij verwijderen account -----------------------------------------------------------
-- Zet elke foreign key van een public-tabel naar auth.users om naar ON DELETE CASCADE.
-- Uitzonderingen, waar de rij moet blijven bestaan als het account verdwijnt (verwijzing wordt leeg):
--   gesture_samples (goedgekeurde gebaren blijven bruikbaar), classes, crash_reports.
do $$
declare
  fk record;
  keep_rows text[] := array['gesture_samples', 'classes', 'crash_reports'];
  rule text;
begin
  for fk in
    select c.conname, rel.relname as tbl, a.attname as col, c.confdeltype
      from pg_constraint c
      join pg_class rel on rel.oid = c.conrelid
      join pg_namespace n on n.oid = rel.relnamespace
      join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
     where c.contype = 'f'
       and n.nspname = 'public'
       and c.confrelid = 'auth.users'::regclass
       and array_length(c.conkey, 1) = 1
  loop
    rule := case when fk.tbl = any(keep_rows) then 'set null' else 'cascade' end;
    if (rule = 'cascade' and fk.confdeltype <> 'c') or (rule = 'set null' and fk.confdeltype not in ('n', 'c')) then
      execute format('alter table public.%I drop constraint %I', fk.tbl, fk.conname);
      execute format('alter table public.%I add constraint %I foreign key (%I) references auth.users(id) on delete %s',
                     fk.tbl, fk.conname, fk.col, rule);
      raise notice 'FK %.% -> auth.users nu on delete %', fk.tbl, fk.col, rule;
    end if;
  end loop;
end $$;

-- 2. Bewaartermijn na einde licentie ----------------------------------------------------------
alter table public.schools add column if not exists data_purged_at timestamptz;

-- Wist alle leerlingaccounts van één school, met voortgang, badges, pincodes en klaslidmaatschap
-- (via de cascades hierboven). Klassen worden ook verwijderd. Docenten en de schoolrij zelf
-- blijven bestaan (nodig voor facturatie en een eventuele verlenging); de docentkoppelingen gaan
-- wel weg, zodat docenten na het opschonen niets meer van de school zien.
create or replace function public.purge_school_data(p_school_id uuid)
returns int
language plpgsql
security definer
set search_path = public
as $$
declare
  removed int;
begin
  with students as (
    select cs.user_id
      from public.class_students cs
      join public.classes c on c.id = cs.class_id
     where c.school_id = p_school_id
  ), deleted as (
    delete from auth.users u
     using students s
     where u.id = s.user_id
       and u.email like '%@students.signcoach.invalid'
    returning u.id
  )
  select count(*)::int into removed from deleted;

  delete from public.classes where school_id = p_school_id;
  delete from public.school_staff where school_id = p_school_id;
  update public.schools set data_purged_at = now() where id = p_school_id;
  return removed;
end;
$$;

-- Voor de dagelijkse taak: alle scholen waarvan de licentie meer dan 90 dagen verlopen is.
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
  return total;
end;
$$;

-- Admin-knop in de app: direct opschonen (bv. als een school opzegt en om verwijdering vraagt).
create or replace function public.admin_purge_school(p_school_id uuid)
returns int
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  return public.purge_school_data(p_school_id);
end;
$$;

revoke all on function public.purge_school_data(uuid) from public, anon, authenticated;
revoke all on function public.purge_expired_schools(int) from public, anon, authenticated;
revoke all on function public.admin_purge_school(uuid) from public, anon;
grant execute on function public.admin_purge_school(uuid) to authenticated;

-- 3. Opruimen van wat er al staat ------------------------------------------------------------
do $$
begin
  if to_regclass('public.crash_reports') is not null then
    update public.crash_reports set user_id = null where user_id is not null;
  end if;
  if to_regclass('public.device_push_tokens') is not null then
    delete from public.device_push_tokens t
     using auth.users u
     where u.id = t.user_id and u.email like '%@students.signcoach.invalid';
  end if;
end $$;

commit;

-- 4. Dagelijkse taak (los uitvoeren) ------------------------------------------------------------
-- Zet eerst pg_cron aan: Database -> Extensions -> pg_cron. Voer daarna dit uit:
--
--   select cron.schedule('purge-expired-schools', '17 3 * * *', $$select public.purge_expired_schools()$$);
--
-- Controle:  select * from cron.job;
