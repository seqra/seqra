CREATE OR REPLACE FUNCTION CREATE_CONSTRAINT(
    t_name text, c_name text, constraint_sql text
)
returns void AS
$$
BEGIN
    IF NOT EXISTS (
        SELECT con.conname, rel.relname FROM pg_catalog.pg_constraint con
            INNER JOIN pg_catalog.pg_class rel ON rel.oid = con.conrelid
            INNER JOIN pg_catalog.pg_namespace nsp ON nsp.oid = connamespace
        WHERE rel.relname = t_name and conname = c_name
    ) THEN
        execute constraint_sql;
    END IF;
END;
$$ language 'plpgsql'