package Database.Constants.Repository.SqlExecutors;

import DataModel.QueryResult;

import java.sql.SQLException;

public interface SqlExecutors {
    QueryResult executeQuery(String sql, Object... params) throws SQLException;

    void executeUpdate(String sql, Object... params) throws SQLException;
}
