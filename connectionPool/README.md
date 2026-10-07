1. Start Postgres with no override (the default config):

   ```
   docker rm -f pg
   docker run -d --name pg -e POSTGRES_PASSWORD=pw -p 5432:5432 postgres:16 -c max_connections=100
   ```

2. Confirm the limit is Postgres's own default and not something you set:

   ```
   docker exec pg psql -U postgres -Atc "select name, setting, source from pg_settings where name in ('max_connections','superuser_reserved_connections')"
   ```

   In the `source` column, `default` means nobody set it. You should see `max_connections | 100 | default` and `superuser_reserved_connections | 3 | default`.

3. Run the experiment:

   ```
   java "-Duser.timezone=Asia/Kolkata" -cp "out/pg;lib/postgresql.jar" connectionPool.PgLimit
   ```

   ```
   java "-Duser.timezone=Asia/Kolkata" -cp "out/pg;lib/postgresql.jar" connectionPool.PgElasticPool
   ```

   It prints each connection as it opens. The last lines should show connection #100 opening and #101 failing with `too many clients already`.

4. Clean up when you're done:

   ```
   docker rm -f pg
   ```
