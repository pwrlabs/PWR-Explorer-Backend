package Database;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static Database.Constants.Constants.*;
import static Database.DatabaseConnection.getConnection;

public class DatabaseInitialization {
    private static final Logger logger = LogManager.getLogger(DatabaseInitialization.class);
    private static final int NUMBER_OF_SHARDS = 2;

    public static void initialize() {
        // Initialize the "Block" table first
        initializeTable(initializeBlock);

        // Initialize the transactions tables
        for (int i = 0; i < NUMBER_OF_SHARDS; i++) {
            initializeTransactionsTable(i);
        }

        // Initialize the remaining tables
        for (String sql : initializationQueue) {
            initializeTable(sql);
        }
    }

    private static final String initializeBlock = "CREATE TABLE IF NOT EXISTS \"Block\" (" +
            BLOCK_NUMBER + " BIGINT PRIMARY KEY, " +
            BLOCK_HASH + " VARCHAR(256), " +
            FEE_RECIPIENT + " TEXT, " +
            TIMESTAMP + " BIGINT, " +
            TRANSACTIONS_COUNT + " INTEGER, " +
            BLOCK_REWARD + " BIGINT, " +
            SIZE + " INTEGER, " +
            SUCCESS + " BOOLEAN); " +
            "CREATE INDEX IF NOT EXISTS idx_block_number ON \"Block\" (" + BLOCK_NUMBER + "); " +
            "CREATE INDEX IF NOT EXISTS idx_fee_recipient_lower ON \"Block\" (LOWER(" + FEE_RECIPIENT + "))";

    private static final String initializeInitialDelegation = "CREATE TABLE IF NOT EXISTS \"InitialDelegation\" (" +
            USER_ADDRESS + " VARCHAR(256), " +
            VALIDATOR_ADDRESS + " VARCHAR(256), " +
            INITIAL_DELEGATION + " BIGINT, " +
            "PRIMARY KEY (" + USER_ADDRESS + ", " + VALIDATOR_ADDRESS + "))";

    private static final String initializeValidator = "CREATE TABLE IF NOT EXISTS \"Validator\" (" +
            ADDRESS + " VARCHAR(62) PRIMARY KEY, " +
            JOINING_TIME + " BIGINT, " +
            LIFETIME_REWARDS + " BIGINT, " +
            SUBMITTED_BLOCKS + " INTEGER, " +
            BLOCKS_SUBMITTED + " BIGINT)";

    private static final String initializeUsersHistory = "CREATE TABLE IF NOT EXISTS \"UsersHistory\" (" +
            ADDRESS + " VARCHAR(256) PRIMARY KEY, " +
            TRANSACTIONS_COUNT + " BIGINT DEFAULT 0, " +
            FIRST_TXN_TIMESTAMP + " BIGINT, " +
            FIRST_TXN_HASH + " VARCHAR(256), " +
            LAST_TXN_TIMESTAMP + " BIGINT, " +
            LAST_TXN_HASH + " VARCHAR(256))";


    final static String[] initializationQueue = new String[]{
            initializeInitialDelegation, initializeValidator, initializeUsersHistory
    };

    private static void initializeTable(String sql) {
        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.executeUpdate();
            logger.info("Table initialized!");
        } catch (Exception e) {
            logger.error("Unable to initialize table: ", e);
        }
    }

    private static void initializeTransactionsTable(int shardIndex) {
        String tableName = "\"Transactions_Shard_" + shardIndex + "\"";
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                HASH + " VARCHAR(256), " +
                BLOCK_NUMBER + " BIGINT, " +
                POSITION_IN_BLOCK + " INTEGER, " +
                FROM_ADDRESS + " VARCHAR(256), " +
                TO_ADDRESS + " VARCHAR(256), " +
                TIMESTAMP + " BIGINT, " +
                VALUE + " BIGINT, " +
                TXN_TYPE + " VARCHAR(256), " +
                TXN_FEE + " BIGINT, " +
                SUCCESS + " BOOLEAN, " +
                "PRIMARY KEY (" + HASH + ", " + BLOCK_NUMBER + "), " +
                "FOREIGN KEY (" + BLOCK_NUMBER + ") REFERENCES \"Block\" (" + BLOCK_NUMBER + ") " +
                "); " +
                "CREATE INDEX IF NOT EXISTS idx_from_address_" + shardIndex + " ON " + tableName + " (" + FROM_ADDRESS + "); " +
                "CREATE INDEX IF NOT EXISTS idx_to_address_" + shardIndex + " ON " + tableName + " (" + TO_ADDRESS + "); " +
                "CREATE INDEX IF NOT EXISTS idx_block_number_" + shardIndex + " ON " + tableName + " (" + BLOCK_NUMBER + "); " +
                "CREATE INDEX IF NOT EXISTS idx_timestamp_" + shardIndex + " ON " + tableName + " (" + TIMESTAMP + " DESC); " +
                "CREATE INDEX IF NOT EXISTS idx_timestamp_grouped_" + shardIndex + " ON " + tableName + " ((" + TIMESTAMP + " / 86400000)); " +
                "CREATE INDEX IF NOT EXISTS idx_block_timestamp_" + shardIndex + " ON " + tableName + " (" + BLOCK_NUMBER + ", " + TIMESTAMP + " DESC);";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.executeUpdate();
            logger.info("Transactions table for shard {} initialized!", shardIndex);
        } catch (Exception e) {
            logger.error("Unable to initialize transactions table for shard {}\nReason: {}", shardIndex, e.getMessage());
        }
    }

    private static void dropTables() {
        try (Connection connection = getConnection()) {
            // Drop tables with foreign key constraints first (transactions tables that reference Block)
            for (int i = 0; i < NUMBER_OF_SHARDS; i++) {
                dropTable(connection, "\"Transactions_Shard_" + i + "\"");
            }
            // Then drop the remaining tables
            dropTable(connection, "\"Block\"");
            dropTable(connection, "\"InitialDelegation\"");
            dropTable(connection, "\"Validator\"");
            dropTable(connection, "\"UsersHistory\"");
        } catch (Exception e) {
            logger.error("Error dropping tables: ", e);
        }
    }

    private static void dropTable(Connection connection, String tableName) throws SQLException {
        String sql = "DROP TABLE IF EXISTS " + tableName + " CASCADE";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

}
