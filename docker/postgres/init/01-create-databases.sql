SELECT 'CREATE DATABASE fincore_ledger OWNER fincore'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'fincore_ledger')
\gexec

SELECT 'CREATE ROLE fincore_keycloak LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD ''fincore_keycloak_local'''
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'fincore_keycloak')
\gexec

SELECT 'CREATE DATABASE fincore_keycloak OWNER fincore_keycloak'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'fincore_keycloak')
\gexec

-- Upgrade older local volumes without reassigning shared objects owned by fincore.
\connect fincore_keycloak
BEGIN;

SELECT 'ALTER DATABASE fincore_keycloak OWNER TO fincore_keycloak'
FROM pg_database
WHERE datname = current_database() AND datdba = 'fincore'::regrole
\gexec

SELECT format('ALTER %s public.%I OWNER TO fincore_keycloak',
              CASE relkind WHEN 'S' THEN 'SEQUENCE' ELSE 'TABLE' END, relname)
FROM pg_class
WHERE relnamespace = 'public'::regnamespace
  AND relowner = 'fincore'::regrole
  AND relkind IN ('r', 'p', 'S')
ORDER BY (relkind = 'S'), relname
\gexec

COMMIT;
