package Database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnection {
    private static HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setPassword(Config.getDatabasePassword());

        config.setJdbcUrl("jdbc:postgresql://localhost:5432/" + Config.getDatabaseName() + "?characterEncoding=UTF-8");
        config.setUsername(Config.getDatabaseUserName());
        config.setMaximumPoolSize(10);

        dataSource = new HikariDataSource(config);
    }


    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

}