package Core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnection {
    private static HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/pwrexplorer?characterEncoding=UTF-8");
        config.setUsername("postgres");
        config.setPassword("bXgzfYVU49ki");
        config.setMaximumPoolSize(10);
        // Set other configuration properties as needed

        dataSource = new HikariDataSource(config);
    }

    public static HikariDataSource getPool() {
        return dataSource;
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}