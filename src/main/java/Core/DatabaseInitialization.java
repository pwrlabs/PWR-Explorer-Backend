package Core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static Core.DatabaseConnection.getConnection;

public class DatabaseInitialization {

    private static final Logger logger = LogManager.getLogger(DatabaseInitialization.class);

    private static final String initializeUser = "CREATE TABLE IF NOT EXISTS \"User\" (\n" + //TODO: remove delegator count
            "    \"address\" VARCHAR PRIMARY KEY,\n" +
            "    \"initial_delegations\" JSON,\n" +
            "    \"delegator_count\" INT\n" +
            ");";

    private static final String initializeBlock = "CREATE TABLE IF NOT EXISTS \"Block\" (\n" +
            "    \"block_number\" VARCHAR PRIMARY KEY,\n" +
            "    \"timestamp\" BIGINT,\n" +
            "    \"block_submitter\" VARCHAR,\n" +
            "    \"block_reward\" BIGINT,\n" +
            "    \"block_size\" INT,\n" +
            "    \"txn_count\" INT\n" +
            ");";
    private static final String initializeTxn = "CREATE TABLE IF NOT EXISTS \"Txn\" (\n" +
            "    \"hash\" VARCHAR PRIMARY KEY,\n" +
            "    \"size\" INT,\n" +
            "    \"position_in_block\" INT,\n" +
            "    \"block_number\" VARCHAR REFERENCES \"Block\"(\"block_number\"),\n" +
            "    \"txn_from_address\" VARCHAR REFERENCES \"User\"(\"address\"),\n" +
            "    \"txn_to\" VARCHAR,\n" +
            "    \"txn_to_address\" VARCHAR REFERENCES \"User\"(\"address\"),\n" +
            "    \"value\" BIGINT,\n" +
            "    \"txn_fee\" BIGINT,\n" +
            "    \"txn_data\" VARCHAR,\n" +
            "    \"txn_type\" VARCHAR,\n" +
            "    \"nonce_or_validation\" VARCHAR,\n" +
            "    \"amount_usd_value\" NUMERIC, -- BigDecimal is typically represented as NUMERIC in PostgreSQL\n" +
            "    \"fee_usd_value\" NUMERIC\n" +
            ");";


    final static String[] initializationQueue = new String[] {
        initializeUser, initializeBlock, initializeTxn
    };

    public static void initialize() {
        for(int i = 0; i < initializationQueue.length; i++) {
            String sql = initializationQueue[i];
            try(Connection conn = getConnection();
                PreparedStatement preparedStatement  = conn.prepareStatement(sql)) {
                preparedStatement.executeUpdate();
                logger.info("Table {} initialized!", i);
            } catch(Exception e) {
                e.printStackTrace();
                logger.error("Unable to initialize table {}\n Reason: {}", i, e.getMessage());
            }
        }
    }

}
