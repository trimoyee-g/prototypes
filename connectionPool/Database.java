package connectionPool;

import java.util.concurrent.atomic.AtomicInteger;

public class Database {

    final AtomicInteger numberOfOpenConnections = new AtomicInteger();
    final private int maxConnections = 100;

    Connection connect() throws Exception{
        if(numberOfOpenConnections.incrementAndGet() > maxConnections){
            numberOfOpenConnections.decrementAndGet();
            throw new Exception("Too many connections");
        }
        Thread.sleep(200);
        return new Connection(this);
    }

    void terminateConnection() {
        numberOfOpenConnections.decrementAndGet();
    }

    int getMaxConnections() {
        return maxConnections;
    }
}
