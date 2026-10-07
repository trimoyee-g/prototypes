package connectionPool;

public class Connection {
    final Database database;

    Connection(Database database){
        this.database = database;
    }

    void query() throws Exception {
        Thread.sleep(1000);
    }

    void close() {
        database.terminateConnection();
    }
}
