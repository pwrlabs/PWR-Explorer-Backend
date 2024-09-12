package Core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
//        config.setJdbcUrl("jdbc:postgresql://localhost:5432/explorer?characterEncoding=UTF-8");
//        config.setUsername("postgres");
//        config.setPassword("Kriko2004");
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/pwrexplorer?characterEncoding=UTF-8");
        config.setUsername("postgres");
        config.setPassword("bXgzfYVU49ki");
        config.setMaximumPoolSize(10);

        dataSource = new HikariDataSource(config);
    }

    public static HikariDataSource getPool() {
        return dataSource;
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }


//        private static final String url = "jdbc:postgresql://localhost:5432/pwrexplorer?characterEncoding=UTF-8";
//        private static final String user = "postgres";
//        private static final String password = "bXgzfYVU49ki";
//
//        public static Connection getConnection() throws SQLException {
//            return DriverManager.getConnection(url, user, password);
//        }

}