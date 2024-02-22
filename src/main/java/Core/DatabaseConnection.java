package Core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    public static Connection getConnection() throws SQLException {
        String url = "jdbc:postgresql://localhost:5432/pwrexplorer?characterEncoding=UTF-8";
        String user = "postgres";
        String password = "bXgzfYVU49ki";
        return DriverManager.getConnection(url, user, password);
    }
}
