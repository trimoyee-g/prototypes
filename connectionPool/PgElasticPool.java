package connectionPool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// Elastic pool: starts with MIN connections, grows on demand up to MAX, closes idle extras after IDLE_MS.
public class PgElasticPool {
    static final String URL = "jdbc:postgresql://localhost:5432/postgres?user=postgres&password=pw";
    static final int MIN = 5, MAX = 50, IDLE_MS = 3000, THREADS = 500;

    static class Pool {
        record Idle(Connection conn, long since) {}
        final BlockingDeque<Idle> idle = new LinkedBlockingDeque<>(); // borrow/return at head, reap at tail
        int total;                                      // idle + in use, guarded by 'this'

        Pool() throws Exception {
            for (int i = 0; i < MIN; i++) { idle.addFirst(new Idle(open(), now())); total++; }
            ScheduledExecutorService reaper = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
            reaper.scheduleAtFixedRate(this::shrink, 500, 500, TimeUnit.MILLISECONDS);
        }

        static Connection open() throws Exception { return DriverManager.getConnection(URL); }
        static long now() { return System.currentTimeMillis(); }

        Connection borrow() throws Exception {
            Idle i = idle.pollFirst();                  // 1. reuse
            if (i == null) {
                boolean grow;
                synchronized (this) { grow = total < MAX; if (grow) total++; }  // reserve a slot before connecting
                if (grow) {                             // 2. grow
                    try { return open(); }
                    catch (Exception e) { synchronized (this) { total--; } throw e; }
                }
                i = idle.pollFirst(30, TimeUnit.SECONDS);   // 3. at MAX: wait for a return
                if (i == null) throw new Exception("pool timeout");
            }
            return i.conn();
        }

        void giveBack(Connection c) { idle.addFirst(new Idle(c, now())); }

        synchronized void shrink() {
            Idle last;
            while (total > MIN && (last = idle.peekLast()) != null && now() - last.since() > IDLE_MS) {
                if (!idle.removeLastOccurrence(last)) break;    // a borrower took it first
                try { last.conn().close(); } catch (Exception ignored) {}
                total--;
            }
        }

        synchronized int size() { return total; }
    }

    static int serverConnections() throws Exception {
        try (Connection c = DriverManager.getConnection(URL); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("select count(*) from pg_stat_activity where backend_type='client backend'")) {
            r.next(); return r.getInt(1) - 1;           // minus this probe
        }
    }

    public static void main(String[] args) throws Exception {
        Pool pool = new Pool();
        AtomicInteger ok = new AtomicInteger(), fail = new AtomicInteger();
        long t0 = System.currentTimeMillis();

        // Print client-side pool size vs what Postgres actually sees, once a second.
        Thread monitor = new Thread(() -> {
            try {
                while (true) {
                    System.out.printf("t=%2ds  pool=%2d  postgres=%2d  done=%d%n",
                        (System.currentTimeMillis() - t0) / 1000, pool.size(), serverConnections(), ok.get() + fail.get());
                    Thread.sleep(1000);
                }
            } catch (Exception ignored) {}
        });
        monitor.setDaemon(true);
        monitor.start();

        ExecutorService ex = Executors.newFixedThreadPool(THREADS);
        for (int i = 0; i < THREADS; i++) {
            ex.submit(() -> {
                try {
                    Connection c = pool.borrow();
                    try (Statement s = c.createStatement()) { s.execute("select pg_sleep(1)"); }
                    finally { pool.giveBack(c); }
                    ok.incrementAndGet();
                } catch (Exception e) { fail.incrementAndGet(); System.out.println(e.getMessage()); }
            });
        }
        ex.shutdown();
        ex.awaitTermination(5, TimeUnit.MINUTES);
        System.out.printf("burst finished: success=%d failed=%d time=%dms%n", ok.get(), fail.get(), System.currentTimeMillis() - t0);

        Thread.sleep(IDLE_MS + 4000);                   // idle period: watch pool shrink back to MIN
    }
}
