package connectionPool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// Opens connections to a real Postgres and keeps them open until it refuses one.
public class PgLimit {
    static final String URL = "jdbc:postgresql://localhost:5432/postgres?user=postgres&password=pw";

    public static void main(String[] args) throws Exception {
        List<Connection> open = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(URL); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("show max_connections")) {
            r.next();
            System.out.println("server max_connections = " + r.getString(1));
        }
        try {
            while (true) {
                open.add(DriverManager.getConnection(URL));
                System.out.println("opened connection #" + open.size());
            }
        } catch (Exception e) {
            System.out.println("FAILED on connection #" + (open.size() + 1) + ": " + e.getMessage());
        }
        for (Connection c : open) c.close();
    }
}
