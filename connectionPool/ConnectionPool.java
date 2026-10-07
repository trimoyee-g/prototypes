package connectionPool;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ConnectionPool {
    Database database;
    final BlockingQueue<Connection> idle;
    final int capacity;

    ConnectionPool(Database database) throws Exception{
        capacity = database.getMaxConnections();
        idle = new ArrayBlockingQueue<>(capacity);
        for(int i = 0; i < capacity; i++){
            idle.add(database.connect());
        }
    }

    Connection borrowConnection() throws Exception {
        return idle.take();
    }

    void returnConnection(Connection connection) {
        idle.add(connection);
    }

}
