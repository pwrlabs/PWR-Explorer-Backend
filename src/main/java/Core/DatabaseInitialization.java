package Core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static Core.DatabaseConnection.getConnection;

public class DatabaseInitialization {

    private static final Logger logger = LogManager.getLogger(DatabaseInitialization.class);
    private static final int NUMBER_OF_SHARDS = 2;
    private static final String initializeBlock = "CREATE TABLE IF NOT EXISTS \"Block\" (\n" +
            "    \"block_number\" BIGINT PRIMARY KEY,\n" +
            "    \"block_hash\" VARCHAR(256),\n" +
            "    \"fee_recipient\" TEXT,\n" +
            "    \"timestamp\" BIGINT,\n" +
            "    \"transactions_count\" INTEGER,\n" +
            "    \"block_reward\" BIGINT,\n" +
            "    \"size\" INTEGER,\n" +
            "    \"success\" BOOLEAN\n" +
            ");\n" +
            "CREATE INDEX IF NOT EXISTS \"idx_block_number\" ON \"Block\" (\"block_number\");";

    private static final String initializeUser = "CREATE TABLE IF NOT EXISTS \"User\" (\n" +
            "    \"address\" VARCHAR(256) PRIMARY KEY,\n" +
            "    \"first_sent_txn\" BYTEA,\n" +
            "    \"last_sent_txn\" BYTEA,\n" +
            "    \"transaction_hashes\" BYTEA[]\n" +
            ");";

    private static final String initializeVM = "CREATE TABLE IF NOT EXISTS \"VM\" (\n" +
            "    \"id\" BIGINT PRIMARY KEY,\n" +
            "    \"first_sent_txn_hash\" VARCHAR(256),\n" +
            "    \"last_sent_txn_hash\" VARCHAR(256)\n" +
            ");";

    private static final String initializeInitialDelegation = "CREATE TABLE IF NOT EXISTS \"InitialDelegation\" (\n" +
            "    \"user_address\" VARCHAR(256),\n" +
            "    \"validator_address\" VARCHAR(256),\n" +
            "    \"initial_delegation\" BIGINT,\n" +
            "    PRIMARY KEY (\"user_address\", \"validator_address\")\n" +
            ");";

    private static final String initializeValidator = "CREATE TABLE IF NOT EXISTS \"Validator\" (\n" +
            "    \"address\" VARCHAR(62) PRIMARY KEY,\n" +
            "    \"joining_time\" BIGINT,\n" +
            "    \"lifetime_rewards\" BIGINT,\n" +
            "    \"submitted_blocks\" INTEGER,\n" +
            "    \"blocks_submitted\" BIGINT\n" +
            ");";

    final static String[] initializationQueue = new String[]{
            initializeUser, initializeVM, initializeInitialDelegation, initializeValidator
    };

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
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (\n" +
                "    \"hash\" VARCHAR(256),\n" +
                "    \"block_number\" BIGINT,\n" +
                "    \"position_in_block\" INTEGER,\n" +
                "    \"from_address\" VARCHAR(256),\n" +
                "    \"to_address\" VARCHAR(256),\n" +
                "    \"timestamp\" BIGINT,\n" +
                "    \"value\" BIGINT,\n" +
                "    \"txn_type\" VARCHAR(256),\n" +
                "    \"txn_fee\" BIGINT,\n" +
                "    \"success\" BOOLEAN,\n" +
                "    PRIMARY KEY (\"hash\", \"block_number\"),\n" +
                "    FOREIGN KEY (\"block_number\") REFERENCES \"Block\"(\"block_number\")\n" +
                ");\n" +
                "CREATE INDEX IF NOT EXISTS \"idx_from_address_" + shardIndex + "\" ON " + tableName + " (\"from_address\");\n" +
                "CREATE INDEX IF NOT EXISTS \"idx_to_address_" + shardIndex + "\" ON " + tableName + " (\"to_address\");\n" +
                "CREATE INDEX IF NOT EXISTS \"idx_block_number_" + shardIndex + "\" ON " + tableName + " (\"block_number\");\n" +
                "CREATE INDEX IF NOT EXISTS \"idx_timestamp_" + shardIndex + "\" ON " + tableName + " (\"timestamp\" DESC);\n" +
                "CREATE INDEX IF NOT EXISTS \"idx_timestamp_grouped_" + shardIndex + "\" ON " + tableName + " ((\"timestamp\" / 86400000));\n" +
                "CREATE INDEX IF NOT EXISTS \"idx_block_timestamp_" + shardIndex + "\" ON " + tableName + " (\"block_number\", \"timestamp\" DESC);";
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
            dropTable(connection, "\"User\"");
            dropTable(connection, "\"VM\"");
            dropTable(connection, "\"InitialDelegation\"");
            dropTable(connection, "\"Validator\"");
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
