package Database.Repository.SqlExecutors;

import DataModel.QueryResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static Database.DatabaseConnection.getConnection;

public class JdbcExecutors implements SqlExecutors {
    @Override
    public QueryResult executeQuery(String sql, Object... params) throws SQLException {
        Connection connection = getConnection();
        try {
            PreparedStatement stmt = connection.prepareStatement(sql);
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            ResultSet rs = stmt.executeQuery();
            return new QueryResult(connection, stmt, rs);
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
    }

    @Override
    public void executeUpdate(String sql, Object... params) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            stmt.executeUpdate();
        }
    }
}
