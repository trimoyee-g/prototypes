package connectionPool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    static void run(String name, Work work) throws Exception{
        AtomicInteger ok = new AtomicInteger(), fail = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(500);
        long t = System.currentTimeMillis();
        for(int i=0; i<500; i++){
            executorService.submit(() -> {
                try{
                    go.await();
                    work.run();
                    ok.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                }
            });
        }
        go.countDown();
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);
        System.out.printf("%s -> success=%d failed=%d time=%dms%n", name, ok.get(), fail.get(), System.currentTimeMillis() - t);
    }

    public static void main(String[] args) throws Exception{
        Database db1 = new Database();
        run("1. connection per thread", () -> {
            Connection c = db1.connect();
            try { c.query(); } finally { c.close(); }
        });

        Database db2 = new Database();
        ConnectionPool pool = new ConnectionPool(db2);
        run("2. pool of 10          ", () -> {
            Connection c = pool.borrowConnection();
            try { c.query(); } finally { pool.returnConnection(c); }
        });
        System.out.println("max DB connections used with pool: 10 (limit " + db2.getMaxConnections() + ")");
    }
}
