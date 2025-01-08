package Core.Sql;

import Block.Block;
import Core.DataModel.TxnModel;
import Core.DatabaseConnection;
import Main.Settings;
import Txn.Txns;
import Txn.NewTxn;
import User.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static Core.Constants.Constants.*;
import static Core.DatabaseConnection.getConnection;

public class Queries {
    private static final Logger logger = LogManager.getLogger(Queries.class);
    private static final int NUMBER_OF_SHARDS = 1;

    //>>>>>>>INSERT<<<<<<<<
    public static void insertUser(String address, byte[] firstSentTxn, byte[] lastSentTxn, byte[][] transactionHashes) {
        String sql = "INSERT INTO \"User\" (" +
                "address, " +
                "first_sent_txn, " +
                "last_sent_txn, " +
                "transaction_hashes" +
                ") VALUES (?, ?, ?, ?);";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);
            preparedStatement.setBytes(2, firstSentTxn);
            preparedStatement.setBytes(3, lastSentTxn);
            preparedStatement.setArray(4, conn.createArrayOf("BYTEA", transactionHashes));

//            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to insert user {} due to an internal error: ", address, e);
        }
    }

    public static void insertTxn(
            String hash, long blockNumber, int positionInBlock, String fromAddress, String toAddress,
            long timestamp, long value, String txnType, long txnFee, Boolean success
    ) {
        String tableName = getTransactionsTableName("0");
//        logger.info("table name {}", tableName);
        String sql = "INSERT INTO " + tableName + " (" +
                "\"hash\", " +
                "\"block_number\", " +
                "\"position_in_block\", " +
                "\"from_address\", " +
                "\"to_address\", " +
                "\"timestamp\", " +
                "\"value\", " +
                "\"txn_type\", " +
                "\"txn_fee\", " +
                "\"success\"" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, hash);
            preparedStatement.setLong(2, blockNumber);
            preparedStatement.setInt(3, positionInBlock);
            preparedStatement.setString(4, fromAddress.toLowerCase());
            preparedStatement.setString(5, toAddress.toLowerCase());
            preparedStatement.setLong(6, timestamp);
            preparedStatement.setLong(7, value);
            preparedStatement.setString(8, txnType);
            preparedStatement.setLong(9, txnFee);
            preparedStatement.setBoolean(10, success);
            preparedStatement.executeUpdate();
        } catch (Exception e) {
            logger.error("Error inserting transaction: ", e);
        }
    }

    public static void insertBlock(long blockNumber, String blockHash, String feeRecipient, long timestamp, int transactionsCount, long blockReward, int size, boolean success) {
        String blockNumberStr = "" + blockNumber;
        new Block(blockHash, blockNumberStr, timestamp, feeRecipient, blockReward, size, transactionsCount);
        String sql = "INSERT INTO \"Block\" (" +
                BLOCK_NUMBER + ", " +
                BLOCK_HASH + ", " +
                FEE_RECIPIENT + ", " +
                TIMESTAMP + ", " +
                TRANSACTIONS_COUNT + ", " +
                BLOCK_REWARD + ", " +
                BLOCK_SIZE + ", " +
                "success" +  // Add success column
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?);";  // Add one more placeholder for success

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setLong(1, blockNumber);
            preparedStatement.setString(2, blockHash);
            preparedStatement.setString(3, feeRecipient);
            preparedStatement.setLong(4, timestamp);
            preparedStatement.setInt(5, transactionsCount);
            preparedStatement.setLong(6, blockReward);
            preparedStatement.setInt(7, size);
            preparedStatement.setBoolean(8, success);  // Set the success parameter

//            logger.info("QUERY: {}", preparedStatement.toString());
//            logger.info("Inserted Block {} Successfully", blockNumber);

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }

    public static void insertValidator(String address, long joiningTime) {
        String sql = "INSERT INTO \"Validator\" (address, joining_time, lifetime_rewards, submitted_blocks, blocks_submitted) VALUES (?,?,0,0,0)";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, address);
            stmt.setLong(2, joiningTime);
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to insert Validator: ", e);
        }
    }

    public static void insertVM(long id, String firstSentTxn, String lastSentTxn) {
        String sql = "INSERT INTO \"VM\" (" + ID + ", " + FIRST_SENT_TXN_HASH + ", " + LAST_SENT_TXN_HASH + ") VALUES (?, ?, ?);";
        try (Connection conn = getConnection(); PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setLong(1, id);
            preparedStatement.setString(2, firstSentTxn);
            preparedStatement.setString(3, lastSentTxn);
//            logger.info("QUERY: {}", preparedStatement.toString());
            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }

    public static void insertInitialDelegation(String userAddress, String validatorAddress, long initialDelegation) {
        String sql = "INSERT INTO \"InitialDelegation\" (" +
                USER_ADDRESS + ", " +
                VALIDATOR_ADDRESS + ", " +
                INITIAL_DELEGATION +
                ") VALUES (?, ?, ?);";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, userAddress);
            preparedStatement.setString(2, validatorAddress);
            preparedStatement.setLong(3, initialDelegation);

//            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }

    public static long getLatestBlockNumberForFeeRecipient(String feeRecipient) {
        String sql = "SELECT timestamp FROM \"Block\" " +
                "WHERE LOWER(fee_recipient) = ? " +
                "ORDER BY block_number DESC " +
                "LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {

            preparedStatement.setString(1, feeRecipient);

            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("timestamp");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting latest block number for fee recipient {}: {}", feeRecipient, e.getMessage());
        }

        return -1; // Return -1 if no block found or in case of error
    }

    //>>>>>>>UPDATE<<<<<<<<
    public static void updateInitialDelegations(String userAddress, String validatorAddress, long initialDelegation) {
        String sql = "INSERT INTO \"InitialDelegation\" (" + USER_ADDRESS + ", " + VALIDATOR_ADDRESS + ", " + INITIAL_DELEGATION + ") " +
                "VALUES (?, ?, ?) " +
                "ON CONFLICT (" + USER_ADDRESS + ", " + VALIDATOR_ADDRESS + ") DO UPDATE SET " + INITIAL_DELEGATION + " = ?;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, userAddress);
            preparedStatement.setString(2, validatorAddress);
            preparedStatement.setLong(3, initialDelegation);
            preparedStatement.setLong(4, initialDelegation);

//            logger.info("QUERY: {}", preparedStatement);

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }

    public static void updateBlock(long blockNumber, String blockHash, byte[] feeRecipient, long timestamp, int transactionsCount, long blockReward, int size, boolean success) {
        String sql = "UPDATE \"Block\" SET " +
                BLOCK_HASH + " = ?, " +
                FEE_RECIPIENT + " = ?, " +
                TIMESTAMP + " = ?, " +
                TRANSACTIONS_COUNT + " = ?, " +
                BLOCK_REWARD + " = ?, " +
                SIZE + " = ?, " +
                SUCCESS + " = ? " +
                "WHERE " + BLOCK_NUMBER + " = ?;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, blockHash);
            preparedStatement.setBytes(2, feeRecipient);
            preparedStatement.setLong(3, timestamp);
            preparedStatement.setInt(4, transactionsCount);
            preparedStatement.setLong(5, blockReward);
            preparedStatement.setInt(6, size);
            preparedStatement.setBoolean(7, success);
            preparedStatement.setLong(8, blockNumber);

//            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }

    public static void updateUser(String address, byte[] firstSentTxn, byte[] lastSentTxn, byte[][] transactionHashes) {
        String sql = "UPDATE \"User\" SET " +
                FIRST_SENT_TXN + " = ?, " +
                LAST_SENT_TXN + " = ?, " +
                TRANSACTION_HASHES + " = ? " +
                "WHERE " + ADDRESS + " = ?;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setBytes(1, firstSentTxn);
            preparedStatement.setBytes(2, lastSentTxn);
            preparedStatement.setArray(3, conn.createArrayOf("BYTEA", transactionHashes));
            preparedStatement.setString(4, address);

            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }

    public static void updateVMLastTxn(long id, String lastSentTxn) {
        String sql = "UPDATE \"VM\" SET " +
                LAST_SENT_TXN_HASH + " = ? " +
                "WHERE " + ID + " = ?;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, lastSentTxn);
            preparedStatement.setLong(2, id);

            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }

    public static void updateInitialDelegation(String userAddress, String validatorAddress, long initialDelegation) {
        String sql = "UPDATE \"InitialDelegation\" SET " +
                INITIAL_DELEGATION + " = ? " +
                "WHERE " + USER_ADDRESS + " = ? AND " + VALIDATOR_ADDRESS + " = ?;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setLong(1, initialDelegation);
            preparedStatement.setString(2, userAddress);
            preparedStatement.setString(3, validatorAddress);

            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }

    public static void updateValidator(String address, long lifetimeRewards, int submittedBlocks, Object[] blocksSubmitted) {
        String sql = "UPDATE \"Validator\" SET " +
                LIFETIME_REWARDS + " = ?, " +
                SUBMITTED_BLOCKS + " = ?, " +
                BLOCKS_SUBMITTED + " = ? " +
                "WHERE " + ADDRESS + " = ?;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setLong(1, lifetimeRewards);
            preparedStatement.setInt(2, submittedBlocks);
            preparedStatement.setArray(3, conn.createArrayOf("BIGINT", blocksSubmitted));
            preparedStatement.setString(4, address);

            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }

    public static void updateUserInDatabase(String address, byte[] firstSentTxn, byte[] lastSentTxn, byte[][] transactionHashes, int transactionsCount) {
        String sql = "UPDATE \"User\" SET " +
                "\"first_sent_txn\" = ?, " +
                "\"last_sent_txn\" = ?, " +
                "\"transaction_hashes\" = ?, " +
                "\"transactions_count\" = ? " +
                "WHERE \"address\" = ?";
        Connection conn = null;
        try {
            conn = getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        try (PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setBytes(1, firstSentTxn);
            preparedStatement.setBytes(2, lastSentTxn);
            preparedStatement.setArray(3, conn.createArrayOf("BYTEA", transactionHashes));
            preparedStatement.setInt(4, transactionsCount);
            preparedStatement.setString(5, address);

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            // Handle the exception appropriately (e.g., log, throw, etc.)
        }
    }


    //>>>>>>>GET<<<<<<<<
    public static JSONObject getInitialDelegations(String userAddress) {
        String sql = "SELECT " + INITIAL_DELEGATION + " FROM \"InitialDelegation\" WHERE " + USER_ADDRESS + " = ?;";
        JSONObject jsonObject = new JSONObject();
        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, userAddress);

//            logger.info("QUERY: {}", preparedStatement.toString());

            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    long initialDelegation = rs.getLong(INITIAL_DELEGATION);
                    jsonObject.put(rs.getString(VALIDATOR_ADDRESS), initialDelegation);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
        return jsonObject;
    }

    public static User getDbUser(String address) {
        String sql = "SELECT * FROM \"User\" WHERE " + ADDRESS + " = ?;";
        User user = null;
        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);

            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    byte[] firstSentTxn = rs.getBytes(FIRST_SENT_TXN);
                    byte[] lastSentTxn = rs.getBytes(LAST_SENT_TXN);
                    byte[][] transactionHashes = (byte[][]) rs.getArray(TRANSACTION_HASHES).getArray();
                    int transactionsCount = rs.getInt(TRANSACTIONS_COUNT);
                    user = new User(address, firstSentTxn, lastSentTxn, transactionHashes, transactionsCount);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get user from db: ", e);
        }
        return user;
    }

    public static boolean dbUserExists(String address) {
        String sql = "SELECT EXISTS (SELECT 1 FROM \"User\" WHERE " + ADDRESS + " = ?);";
        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);
            try (ResultSet rs = preparedStatement.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
        return false;
    }

    public static Block getDbBlock(long blockNumber) {
        String sql = "SELECT * FROM \"Block\" WHERE " + BLOCK_NUMBER + " = ?;";
        Block block = null;
        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setLong(1, blockNumber);

            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    String feeRecipient = rs.getString(FEE_RECIPIENT);
                    long timestamp = rs.getLong(TIMESTAMP);
                    int transactionsCount = rs.getInt(TRANSACTIONS_COUNT);
                    long blockReward = rs.getLong(BLOCK_REWARD);
                    int size = rs.getInt(BLOCK_SIZE);
                    String blockHash = rs.getString(BLOCK_HASH);
                    String blockNumberString = "" + blockNumber;
                    block = new Block(blockHash, blockNumberString, timestamp, feeRecipient, blockReward, size, transactionsCount);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get block {} from db: ", blockNumber, e);
        }
        return block;
    }

    public static long getLastBlockNumber() {
        String sql = "SELECT MAX(" + BLOCK_NUMBER + ") FROM \"Block\";";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql);
             ResultSet rs = preparedStatement.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return 0;
    }

    public static double getAverageTps(int numberOfBlocks, long lastBlockNumber) {
        String sql = "SELECT transactions_count, timestamp FROM \"Block\" " +
                "WHERE block_number > ? " +
                "ORDER BY block_number DESC " +
                "LIMIT ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, lastBlockNumber - numberOfBlocks);
            stmt.setInt(2, numberOfBlocks);

            long totalTxns = 0;
            Long firstTimestamp = null;
            Long lastTimestamp = null;

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    totalTxns += rs.getInt("transactions_count");
                    long timestamp = rs.getLong("timestamp");

                    if (firstTimestamp == null) {
                        firstTimestamp = timestamp;
                    }
                    lastTimestamp = timestamp;
                }
            }

            if (firstTimestamp == null || lastTimestamp == null || firstTimestamp.equals(lastTimestamp)) {
                return 0;
            }

            // Calculate time difference in seconds
            double timeSpanSeconds = (firstTimestamp - lastTimestamp) / 1000.0;

            // Calculate TPS
            return BigDecimal.valueOf(totalTxns)
                    .divide(BigDecimal.valueOf(timeSpanSeconds), 1, BigDecimal.ROUND_HALF_UP)
                    .doubleValue();
        } catch (Exception e) {
            logger.error("Failed to calculate TPS: ", e);
            return 0;
        }
    }

    public static NewTxn getDbTxn(String hash) {
        NewTxn txn = null;
        String tableName = getTransactionsTableName(hash);

        String sql = "SELECT * FROM " + tableName + " WHERE " + HASH + " = ?;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, hash);

            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    txn = populateNewTxnObject(rs);
                }
            }
        } catch (Exception e) {
            logger.error("An error occurred while retrieving txn details: ", e);
        }
        return txn;
    }

    public static String getBlockHash(long blockNumber) {
        String blockHash = null;
        String sql = "SELECT \"block_hash\" FROM \"Block\" WHERE \"block_number\" = ?;";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setLong(1, blockNumber);

//            logger.info("QUERY: {}", preparedStatement.toString());

            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    blockHash = rs.getString("block_hash");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }

        return blockHash;
    }

    public static List<NewTxn> getTransactions(int limit, int offset) {
        List<NewTxn> txns = new ArrayList<>();
        String tableName = getTransactionsTableName("0");
        String sql = "SELECT * " +
                "FROM " + tableName +
                " ORDER BY " + BLOCK_NUMBER + " DESC, " + POSITION_IN_BLOCK + " DESC " +
                "LIMIT ? OFFSET ?;";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setInt(1, limit);
            preparedStatement.setInt(2, offset);

            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    NewTxn txn = populateNewTxnObject(rs);
                    txns.add(txn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }

        return txns;
    }

    public static List<NewTxn> getLastXTransactions(int x) {
        List<NewTxn> txns = new ArrayList<>();
        String tableName = getTransactionsTableName("0");

        // Query to get the highest block number
        String getMaxBlockNumberSql = "SELECT MAX(" + BLOCK_NUMBER + ") AS max_block_number FROM " + tableName;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rsMax = stmt.executeQuery(getMaxBlockNumberSql)) {

            if (rsMax.next()) {
                int maxBlockNumber = rsMax.getInt("max_block_number");

                // Query to get transactions from the highest block number
                String sql = "SELECT * FROM " + tableName +
                        " ORDER BY " + TIMESTAMP + " DESC " +
                        " LIMIT ?;";

                try (PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
                    preparedStatement.setInt(1, x);

                    try (ResultSet rs = preparedStatement.executeQuery()) {
                        while (rs.next()) {
                            NewTxn txn = populateNewTxnObject(rs);
                            txns.add(txn);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }

        return txns;
    }

    public static List<Block> getLastXBlocks(int x) {
        List<Block> blocks = new ArrayList<>();
        String sql = "SELECT * FROM \"Block\" " +
                "ORDER BY \"block_number\" DESC " +
                "LIMIT ?;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setInt(1, x);

//            logger.info("QUERY: {}", preparedStatement.toString());

            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    long blockNumber = rs.getLong(BLOCK_NUMBER);
                    String blockHash = rs.getString(BLOCK_HASH);
                    String feeRecipient = rs.getString(FEE_RECIPIENT);
                    long timestamp = rs.getLong(TIMESTAMP);
                    int transactionsCount = rs.getInt(TRANSACTIONS_COUNT);
                    long blockReward = rs.getLong(BLOCK_REWARD);
                    int size = rs.getInt(BLOCK_SIZE);
                    String blockNumberString = "" + blockNumber;

                    Block block = new Block(blockHash, blockNumberString, timestamp, feeRecipient, blockReward, size, transactionsCount);
                    blocks.add(block);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        return blocks;
    }

    public static List<Block> getLastXBlocks(int limit, int offset) {
        List<Block> blocks = new ArrayList<>();
        String sql = "SELECT * FROM \"Block\" " +
                "ORDER BY \"block_number\" DESC " +
                "LIMIT ? OFFSET ?;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setInt(1, limit);
            preparedStatement.setInt(2, offset);

//            logger.info("QUERY: {}", preparedStatement);

            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    long blockNumber = rs.getLong(BLOCK_NUMBER);
                    String blockHash = rs.getString(BLOCK_HASH);
                    String feeRecipient = rs.getString(FEE_RECIPIENT);
                    long timestamp = rs.getLong(TIMESTAMP);
                    int transactionsCount = rs.getInt(TRANSACTIONS_COUNT);
                    long blockReward = rs.getLong(BLOCK_REWARD);
                    int size = rs.getInt(BLOCK_SIZE);

                    Block block = new Block(
                            blockHash,
                            String.valueOf(blockNumber),
                            timestamp,
                            feeRecipient,
                            blockReward,
                            size,
                            transactionsCount
                    );
                    blocks.add(block);
                }
            }
        } catch (Exception e) {
            logger.error("Error retrieving blocks: {}", e.getMessage(), e);
        }

        return blocks;
    }

    public static List<NewTxn> getBlockTxns(String blockNumberString) {
        long blockNumber = Long.parseLong(blockNumberString);
        List<NewTxn> txns = new ArrayList<>();
        String tableName = getTransactionsTableName("0");
        String sql = "SELECT * FROM " + tableName + " WHERE " + BLOCK_NUMBER + " = ? " +
                "ORDER BY " + POSITION_IN_BLOCK + " ASC;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setLong(1, blockNumber);

//            logger.info("QUERY: {}", preparedStatement.toString());

            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    NewTxn txn = populateNewTxnObject(rs);
                    txns.add(txn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
        return txns;
    }

    public static long getValidatorJoiningTime(String address) {
        String sql = "SELECT joining_time FROM Validator WHERE address = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, address);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception e) {
            logger.error("An error occurred while retrieving validator joining time: ", e);
        }
        return 0;
    }

    public static List<NewTxn> getTransactionsByAddressAndBlockRange(String address, long startBlockNumber, long endBlockNumber) {
        List<NewTxn> txns = new ArrayList<>();

        String tableName = getTransactionsTableName("0");

        String sql = "SELECT * FROM " + tableName +
                " WHERE (" + FROM_ADDRESS + " = ? OR " + TO_ADDRESS + " = ?)" +
                " AND " + BLOCK_NUMBER + " BETWEEN ? AND ?" +
                " ORDER BY " + BLOCK_NUMBER + " ASC;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);
            preparedStatement.setString(2, address);
            preparedStatement.setLong(3, startBlockNumber);
            preparedStatement.setLong(4, endBlockNumber);

            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    NewTxn txn = populateNewTxnObject(rs);
                    txns.add(txn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }

        return txns;
    }

    public static List<NewTxn> getTxnsByAddressAndBlockRangeSortedByPrefix(String address, long startBlock, long endBlock, byte[] bytePrefix) {
        List<NewTxn> txns = new ArrayList<>();

        String tableName = getTransactionsTableName("0");

        String sql = "SELECT * FROM " + tableName +
                " WHERE (" + FROM_ADDRESS + " = ? OR " + TO_ADDRESS + " = ?)" +
                " AND " + BLOCK_NUMBER + " BETWEEN ? AND ?" +
                " AND " + HASH + " LIKE ?" +
                " ORDER BY " + HASH + " ASC;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);
            preparedStatement.setString(2, address);
            preparedStatement.setLong(3, startBlock);
            preparedStatement.setLong(4, endBlock);
            preparedStatement.setString(5, bytePrefixToLikePattern(bytePrefix));

            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    NewTxn txn = populateNewTxnObject(rs);
                    txns.add(txn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }

        return txns;
    }

    private static String bytePrefixToLikePattern(byte[] bytePrefix) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytePrefix) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString() + "%";
    }

    public static List<NewTxn> getUserTxns(String address, int page, int pageSize) {
        List<NewTxn> txns = new ArrayList<>();

        String tableName = getTransactionsTableName("0");

        String sql = "SELECT * FROM " + tableName + " WHERE " + FROM_ADDRESS + " = ? OR " + TO_ADDRESS + " = ? " +
                "ORDER BY " + TIMESTAMP + " DESC " +
                "LIMIT ? OFFSET ?;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);
            preparedStatement.setString(2, address);
            preparedStatement.setInt(3, pageSize);
            preparedStatement.setInt(4, (page - 1) * pageSize);

            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    NewTxn txn = populateNewTxnObject(rs);
                    txns.add(txn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }

        return txns;
    }

    public static double getAverageTransactionFeePercentageChange() {
        double percentageChange = 0.0;
        String tableName = getTransactionsTableName("0");

        String sql = "SELECT " +
                "CASE WHEN COUNT(*) FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) = 0 THEN 0 " +
                "ELSE ((AVG(\"" + TXN_FEE + "\") FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) / " +
                "AVG(\"" + TXN_FEE + "\") FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '48 hours')) * 1000 AND \"" + TIMESTAMP + "\" < EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) - 1) * 100) " +
                "END AS percentage_change " +
                "FROM " + tableName + ";";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
//            logger.info("QUERY: {}", preparedStatement.toString());

            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    percentageChange = rs.getDouble("percentage_change");
                    logger.info("Average Transaction Fee Percentage Change: {}", percentageChange);
                } else {
                    logger.info("No data found for Average Transaction Fee Percentage Change");
                }
            }
        } catch (Exception e) {
            logger.error("Error while calculating Average Transaction Fee Percentage Change: {}", e.toString());
            e.printStackTrace();
        }

        return percentageChange;
    }

    public static double getTotalTransactionFeesPercentageChange() {
        double percentageChange = 0.0;
        String tableName = getTransactionsTableName("0");

        String sql = "SELECT " +
                "CASE WHEN COUNT(*) FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) = 0 THEN 0 " +
                "ELSE ((SUM(\"" + TXN_FEE + "\") FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) / " +
                "SUM(\"" + TXN_FEE + "\") FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '48 hours')) * 1000 AND \"" + TIMESTAMP + "\" < EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) - 1) * 100) " +
                "END AS percentage_change " +
                "FROM " + tableName + ";";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
//            logger.info("QUERY: {}", preparedStatement.toString());

            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    percentageChange = rs.getDouble("percentage_change");
                    logger.info("Total Transaction Fees Percentage Change: {}", percentageChange);
                } else {
                    logger.info("No data found for Total Transaction Fees Percentage Change");
                }
            }
        } catch (Exception e) {
            logger.error("Error while calculating Total Transaction Fees Percentage Change: {}", e.toString());
            e.printStackTrace();
        }

        return percentageChange;
    }

    public static BigInteger getAverageTransactionFeePast24Hours() {
        BigInteger averageFee = BigInteger.ZERO;
        String tableName = getTransactionsTableName("0");

        String sql = "SELECT AVG(\"" + TXN_FEE + "\") AS average_fee FROM " + tableName +
                " WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {

            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    BigDecimal averageFeeDecimal = rs.getBigDecimal("average_fee");
                    if (averageFeeDecimal != null) {
                        averageFee = averageFeeDecimal.toBigInteger();
                        logger.info("Average Transaction Fee Past 24 Hours: {}", averageFee);
                    } else {
                        logger.info("Average fee decimal is null");
                    }
                } else {
                    logger.info("No data found for Average Transaction Fee Past 24 Hours. Result set was empty");
                }
            }
        } catch (Exception e) {
            logger.error("Error while calculating Average Transaction Fee Past 24 Hours: ", e);
        }

        return averageFee;
    }

    public static BigInteger getTotalTransactionFeesPast24Hours() {
        BigInteger totalFees = BigInteger.ZERO;
        String tableName = getTransactionsTableName("0");

        String sql = "SELECT SUM(\"" + TXN_FEE + "\") AS total_fees FROM " + tableName +
                " WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
//            logger.info("QUERY: {}", preparedStatement.toString());

            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    BigDecimal totalFeesDecimal = rs.getBigDecimal("total_fees");
                    if (totalFeesDecimal != null) {
                        totalFees = totalFeesDecimal.toBigInteger();
                        logger.info("Total Transaction Fees Past 24 Hours: {}", totalFees);
                    } else {
                        logger.info("No data found for Total Transaction Fees Past 24 Hours");
                    }
                } else {
                    logger.info("No data found for Total Transaction Fees Past 24 Hours");
                }
            }
        } catch (Exception e) {
            logger.error("Error while calculating Total Transaction Fees Past 24 Hours: {}", e.toString());
            e.printStackTrace();
        }

        return totalFees;
    }

    public static int getTransactionCountPast24Hours() {
        int transactionCount = 0;
        String tableName = getTransactionsTableName("0");

        String sql = "SELECT COUNT(*) AS transaction_count FROM " + tableName +
                " WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
//            logger.info("QUERY: {}", preparedStatement.toString());

            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    transactionCount = rs.getInt("transaction_count");
                    logger.info("Transaction Count Past 24 Hours: {}", transactionCount);
                } else {
                    logger.info("No data found for Transaction Count Past 24 Hours");
                }
            }
        } catch (Exception e) {
            logger.error("Error while calculating Transaction Count Past 24 Hours: {}", e.toString());
            e.printStackTrace();
        }

        return transactionCount;
    }

    public static double getTransactionCountPercentageChangeComparedToPreviousDay() {
        double percentageChange = 0.0;
        String tableName = getTransactionsTableName("0");

        String sql = "SELECT CASE " +
                "WHEN COUNT(*) FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '48 hours')) * 1000 AND \"" + TIMESTAMP + "\" < EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) = 0 THEN 0 " +
                "ELSE (COUNT(*) FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) * 1.0 / " +
                "COUNT(*) FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '48 hours')) * 1000 AND \"" + TIMESTAMP + "\" < EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) - 1) * 100 " +
                "END AS percentage_change " +
                "FROM " + tableName + ";";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
//            logger.info("QUERY: {}", preparedStatement.toString());

            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    percentageChange = rs.getDouble("percentage_change");
                    logger.info("Transaction Count Percentage Change Compared to Previous Day: {}", percentageChange);
                } else {
                    logger.info("No data found for Transaction Count Percentage Change Compared to Previous Day");
                }
            }
        } catch (Exception e) {
            logger.error("Error while calculating Transaction Count Percentage Change Compared to Previous Day: {}", e.toString());
            e.printStackTrace();
        }

        return percentageChange;
    }

    public static JSONObject get24HourBlockStats() {
        final long MILLIS_PER_DAY = 86400000; // 24 * 60 * 60 * 1000
        long currentTimeMillis = System.currentTimeMillis();

        String sql = "SELECT COALESCE(SUM(\"size\"), 0) as total_size, " +
                "COALESCE(SUM(\"block_reward\"), 0) as total_rewards, " +
                "COUNT(*) as block_count, " +
                "COALESCE(AVG(\"size\"), 0) as avg_size, " +
                "COALESCE(SUM(\"transactions_count\"), 0) as total_txns " +
                "FROM \"Block\" WHERE \"timestamp\" >= ? AND \"timestamp\" < ?";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setLong(1, currentTimeMillis - MILLIS_PER_DAY);
            stmt.setLong(2, currentTimeMillis);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int blockCount = rs.getInt("block_count");
                    int avgBlockSize = (int) rs.getDouble("avg_size");
                    long totalRewards = rs.getLong("total_rewards");
                    long totalTxns = rs.getLong("total_txns");
                    double networkUtilization = blockCount > 0
                            ? BigDecimal.valueOf(((double)avgBlockSize / (double) Settings.getBlockSizeLimit()) * 100)
                            .setScale(2, BigDecimal.ROUND_HALF_UP)
                            .doubleValue()
                            : 0.0;

                    return new JSONObject()
                            .put("blocksCount", blockCount)
                            .put("averageBlockSize", avgBlockSize)
                            .put("totalRewards", totalRewards)
                            .put("totalTransactions", totalTxns)
                            .put("networkUtilization", networkUtilization);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting 24h block stats: ", e);
        }

        return new JSONObject()
                .put("blocksCount", 0)
                .put("averageBlockSize", 0)
                .put("totalRewards", 0)
                .put("totalTransactions", 0)
                .put("networkUtilization", 0.0);
    }


    public static int getTotalTransactionCount() {
        int totalCount = 0;
        String tableName = getTransactionsTableName("0");
        String sql = "SELECT COUNT(*) AS total_count FROM " + tableName;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql);
             ResultSet rs = preparedStatement.executeQuery()) {
            if (rs.next()) {
                totalCount = rs.getInt("total_count");
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }

        return totalCount;
    }

    public static int getTotalTxnCount(String address) {
        int totalCount = 0;

        String tableName = getTransactionsTableName("0");

        String sql = "SELECT COUNT(*) AS total_count FROM " + tableName +
                " WHERE " + FROM_ADDRESS + " = ? OR " + TO_ADDRESS + " = ?;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);
            preparedStatement.setString(2, address);

            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    totalCount = rs.getInt("total_count");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }

        return totalCount;
    }

    public static Pair<NewTxn, NewTxn> getFirstAndLastTransactionsByAddress(String address) {
        NewTxn firstTxn = null;
        NewTxn lastTxn = null;

        String tableName = getTransactionsTableName("0");

        String sql = "SELECT * FROM " + tableName +
                " WHERE (" + FROM_ADDRESS + " = ? OR " + TO_ADDRESS + " = ?)" +
                " ORDER BY " + TIMESTAMP + " ASC" +
                " LIMIT 1";

        String sql2 = "SELECT * FROM " + tableName +
                " WHERE (" + FROM_ADDRESS + " = ? OR " + TO_ADDRESS + " = ?)" +
                " ORDER BY " + TIMESTAMP + " DESC" +
                " LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql);
             PreparedStatement preparedStatement2 = conn.prepareStatement(sql2)) {

            preparedStatement.setString(1, address);
            preparedStatement.setString(2, address);
            preparedStatement2.setString(1, address);
            preparedStatement2.setString(2, address);

            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    firstTxn = populateNewTxnObject(rs);
                }
            }

            try (ResultSet rs = preparedStatement2.executeQuery()) {
                if (rs.next()) {
                    lastTxn = populateNewTxnObject(rs);
                }
            }
        } catch (Exception e) {
            logger.error("Error querying first and last transactions for address {}: {}", address, e.getMessage());
        }

        return new Pair<>(firstTxn, lastTxn);
    }

    public static Map<Long, Integer> getFourteenDaysTxn() {
        final int DAYS_TO_FETCH = 14;
        final long MILLIS_PER_DAY = 86400000; // 24 * 60 * 60 * 1000

        TreeMap<Long, Integer> txns = new TreeMap<>(Collections.reverseOrder());

        // Calculate current day's start timestamp
        long currentTimeMillis = System.currentTimeMillis();
        long currentDayStart = (currentTimeMillis / MILLIS_PER_DAY) * MILLIS_PER_DAY;
        long startTimeMillis = currentDayStart - ((DAYS_TO_FETCH - 1) * MILLIS_PER_DAY);

        String sql = "SELECT (\"timestamp\" / " + MILLIS_PER_DAY + ") * " + MILLIS_PER_DAY + " as day_start, " +
                "COUNT(*) as count " +
                "FROM \"Transactions_Shard_0\" " +
                "WHERE \"timestamp\" >= ? AND \"timestamp\" < ? " +
                "GROUP BY (\"timestamp\" / " + MILLIS_PER_DAY + ") * " + MILLIS_PER_DAY + " " +
                "ORDER BY day_start DESC";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setLong(1, startTimeMillis);
            stmt.setLong(2, currentDayStart + MILLIS_PER_DAY); // Include full current day

            // Initialize map with zeros for all days at day start boundaries
            for (int i = 0; i < DAYS_TO_FETCH; i++) {
                long dayStartMillis = startTimeMillis + (i * MILLIS_PER_DAY);
                txns.put(dayStartMillis, 0);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long dayStartMillis = rs.getLong("day_start");
                    int count = rs.getInt("count");
                    txns.put(dayStartMillis, count);
                }
            }

        } catch (SQLException e) {
            logger.error("Error fetching {} days transactions: {}", DAYS_TO_FETCH, e.getMessage());
        }

        return txns;
    }

    public static JSONArray getBlocksCreated(String address, int limit, int offset) {
        String sql = "SELECT block_number, timestamp, success, block_reward, transactions_count " +
                "FROM \"Block\" WHERE LOWER(fee_recipient) = ? " +
                "ORDER BY timestamp DESC " +
                "LIMIT ? OFFSET ?";

        JSONArray blocks = new JSONArray();
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, address);
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JSONObject block = new JSONObject();
                    long blockNumber = rs.getLong(BLOCK_NUMBER);
                    long timestamp = rs.getLong(TIMESTAMP);
                    int transactionsCount = rs.getInt(TRANSACTIONS_COUNT);
                    long blockReward = rs.getLong(BLOCK_REWARD);
                    boolean success = rs.getBoolean(SUCCESS);

                    block.put("blockHeight", blockNumber);
                    block.put("timeStamp", timestamp);
                    block.put("txnsCount", transactionsCount);
                    block.put("blockReward", blockReward);
                    block.put("blockSubmitter", "0x" + address);
                    block.put("error", !success);

                    blocks.put(block);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to fetch created blocks {}", e.getLocalizedMessage());
        }
        return blocks;
    }

    public static int getBlocksSubmitted(String address) {
        String sql = "SELECT COUNT(block_hash) FROM \"Block\" WHERE LOWER(fee_recipient) = ?";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, address);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            logger.error("Failed to fetch submitted blocks {}", e.getLocalizedMessage());
            return 0;
        }
    }

    public static class Pair<T, U> {
        public final T first;
        public final U second;

        public Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }
    }

    //#region Helpers
    private static NewTxn populateNewTxnObject(ResultSet rs) throws SQLException {
        return new NewTxn(
                rs.getString(HASH),
                rs.getLong(BLOCK_NUMBER),
                rs.getInt(POSITION_IN_BLOCK),
                rs.getString(FROM_ADDRESS),
                rs.getString(TO_ADDRESS),
                rs.getLong(TIMESTAMP),
                rs.getLong(VALUE),
                rs.getString(TXN_TYPE),
                rs.getLong(TXN_FEE),
                rs.getBoolean(SUCCESS)
        );
    }

    private static TxnModel populateTxnModel(ResultSet rs) throws SQLException {
        return new TxnModel(
                rs.getString(HASH),
                rs.getInt(SIZE),
                rs.getInt(POSITION_IN_BLOCK),
                rs.getLong(BLOCK_NUMBER),
                rs.getString(FROM_ADDRESS),
                rs.getString(TO_ADDRESS),
                rs.getLong(TIMESTAMP),
                rs.getLong(VALUE),
                rs.getLong(TXN_FEE),
                rs.getBytes(TXN_DATA),
                rs.getString(TXN_TYPE),
                rs.getBoolean(SUCCESS),
                rs.getString(ERROR_MESSAGE),
                (JSONObject) rs.getObject(EXTRA_DATA),
                rs.getLong(NONCE),
                rs.getLong(ACTION_FEE),
                rs.getBoolean(PAID),
                rs.getString(FEEPAYER)
        );
    }

    private static String getTransactionsTableName(String hash) {
        int shardIndex = Math.abs(hash.hashCode()) % NUMBER_OF_SHARDS;
        return "\"Transactions_Shard_" + shardIndex + "\"";
    }
    //#endregion
}