-- Alleen-lezen: wijzigt niets. Geeft één tekstcel terug met de huidige rechten (policies),
-- functies, kolommen en opslag-buckets die fase 2 (rollen) raakt.
select string_agg(regel, E'\n' order by regel) as overzicht from (
  select 'policy | ' || schemaname || '.' || tablename || ' | ' || policyname || ' | ' || cmd
         || ' | using: ' || coalesce(qual, '-') || ' | check: ' || coalesce(with_check, '-') as regel
  from pg_policies
  where (schemaname = 'public' and tablename in ('profiles', 'gesture_samples', 'admins', 'gestures'))
     or (schemaname = 'storage' and tablename = 'objects')
  union all
  select 'function | ' || p.proname || '(' || pg_get_function_identity_arguments(p.oid) || ') | '
         || replace(pg_get_functiondef(p.oid), E'\n', ' ')
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname in ('is_admin', 'am_i_admin', 'handle_new_user')
  union all
  select 'column | ' || table_name || '.' || column_name || ' | ' || data_type || ' | default: ' || coalesce(column_default, '-')
  from information_schema.columns
  where table_schema = 'public' and table_name in ('profiles', 'admins', 'gesture_samples')
  union all
  select 'trigger | ' || event_object_table || ' | ' || trigger_name || ' | ' || action_statement
  from information_schema.triggers where event_object_schema = 'public' and event_object_table in ('profiles', 'gesture_samples')
  union all
  select 'bucket | ' || id || ' | public: ' || public::text from storage.buckets
) x;
