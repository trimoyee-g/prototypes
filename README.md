# Connection pooling, hands-on

Small experiments showing why connection pools exist. Java 17, no build tool.

## 1. Simulated database (`connectionPool/Main.java`)

`Database` is a fake with a 100-connection limit. 500 threads each run a 1s query:

1. **Connection per thread:** most threads fail with "Too many connections".
2. **Pool of 100 connections** (`ConnectionPool`): all 500 succeed, because threads borrow and return a fixed set of connections.

Run from the repo root:

```
javac -d out/sim connectionPool/Connection.java connectionPool/ConnectionPool.java connectionPool/Database.java connectionPool/Work.java connectionPool/Main.java
java -cp out/sim connectionPool.Main
```

## 2. Real Postgres

Needs Docker and the PostgreSQL JDBC driver.

```
mkdir lib
curl -L -o lib/postgresql.jar https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar
docker run -d --name pg -e POSTGRES_PASSWORD=pw -p 5432:5432 postgres:16
```

Postgres's default `max_connections` is 100. To change it, add `-c max_connections=N` to the `docker run` line. It needs a restart to take effect.

Compile once:

```
javac -cp lib/postgresql.jar -d out/pg connectionPool/PgLimit.java connectionPool/PgElasticPool.java
```

**`PgLimit`** opens connections and holds them until Postgres refuses one. You should see connection #100 succeed and #101 fail with `FATAL: sorry, too many clients already`.

```
java "-Duser.timezone=Asia/Kolkata" -cp "out/pg;lib/postgresql.jar" connectionPool.PgLimit
```

**`PgElasticPool`** is a pool that grows from 5 to 50 connections on demand and shrinks back after 3s idle. 500 threads share it. A monitor prints the pool size next to the number of connections Postgres sees, which stays at or below 50.

```
java "-Duser.timezone=Asia/Kolkata" -cp "out/pg;lib/postgresql.jar" connectionPool.PgElasticPool
```

Notes:
- The `-Duser.timezone` flag is needed because the JVM reports `Asia/Calcutta`, which the Postgres 16 image rejects. Use your own zone, or drop the flag if yours works.
- `;` is the Windows classpath separator. Use `:` on Linux or macOS.
- Clean up with `docker rm -f pg`.

## Limits of `PgElasticPool`

It's a teaching pool, not production code. It has no connection validation, no leak detection, and no protection against returning a connection twice. Use HikariCP for real work.
