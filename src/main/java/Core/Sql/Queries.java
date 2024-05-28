package Core.Sql;

import Block.Block;
import Core.DatabaseConnection;
import DailyActivity.DailyActivity;
import Main.Hex;
import Main.Settings;
import Txn.Txn;
import Block.Block;
import User.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.*;

import static Core.Constants.Constants.*;
import static Core.DatabaseConnection.getConnection;
//import static Core.DatabaseConnection.getPool;

public class Queries {
    private static final Logger logger = LogManager.getLogger(Queries.class);
    private static final int NUMBER_OF_SHARDS = 1 ;

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

            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }
    public static void insertBlock(long blockNumber, String blockHash, byte[] feeRecipient, long timestamp, int transactionsCount, long blockReward, int size, boolean success) {
        String blockNumberStr = "" + blockNumber;
        new Block(blockNumberStr, timestamp, feeRecipient, blockReward, size, 0);
        String sql = "INSERT INTO \"Block\" ("+
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
            preparedStatement.setBytes(3, feeRecipient);
            preparedStatement.setLong(4, timestamp);
            preparedStatement.setInt(5, transactionsCount);
            preparedStatement.setLong(6, blockReward);
            preparedStatement.setInt(7, size);
            preparedStatement.setBoolean(8, success);  // Set the success parameter

            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }
    public static void insertTxn(String hash, long blockNumber, int size, int positionInBlock, String fromAddress, String toAddress, long timestamp, long value, long txnFee, byte[] txnData, String txnType, long amountUsdValue, long feeUsdValue, Boolean success, String errorMessage,  String extraData, long nonce, long actionFee, boolean paid, String feePayer) {
        String tableName = getTransactionsTableName("0");
        logger.info("table name {}", tableName);

        String sql = "INSERT INTO " + tableName + " (" +
                HASH +", " +
                BLOCK_NUMBER +", " +
                SIZE +", " +
                POSITION_IN_BLOCK +", " +
                FROM_ADDRESS +", " +
                TO_ADDRESS +", " +
                TIMESTAMP +", " +
                VALUE +", " +
                TXN_FEE +", " +
                TXN_DATA +", " +
                TXN_TYPE +", " +
                AMOUNT_USD_VALUE +", " +
                FEE_USD_VALUE +", " +
                SUCCESS +", " +
                ERROR_MESSAGE +", " +
                EXTRA_DATA +", " +
                NONCE +", " +
                ACTION_FEE +", " +
                PAID +", " +
                FEEPAYER +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?);";

        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, hash);
            preparedStatement.setLong(2, blockNumber);
            preparedStatement.setInt(3, size);
            preparedStatement.setInt(4, positionInBlock);
            preparedStatement.setString(5, fromAddress);
            preparedStatement.setString(6, toAddress);
            preparedStatement.setLong(7, timestamp);
            preparedStatement.setLong(8, value);
            preparedStatement.setLong(9, txnFee);
            preparedStatement.setBytes(10, txnData);
            preparedStatement.setString(11, txnType);
            preparedStatement.setLong(12, amountUsdValue);
            preparedStatement.setLong(13, feeUsdValue);
            preparedStatement.setObject(14, success);
            preparedStatement.setString(15, errorMessage);
            preparedStatement.setObject(14, extraData);  // Set JSON data
            preparedStatement.setLong(15, nonce);
            preparedStatement.setLong(16, actionFee);
            preparedStatement.setBoolean(17, paid);
            preparedStatement.setString(18, feePayer);

            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }
    public static void insertVM(long id, byte[] firstSentTxn, byte[] lastSentTxn, byte[][] transactionHashes, int transactionsCount, long totalRevenue) {
        String sql = "INSERT INTO \"VM\" (" +
                ID + ", " +
                FIRST_SENT_TXN + ", " +
                LAST_SENT_TXN + ", " +
                TRANSACTION_HASHES + ", " +
                TRANSACTIONS_COUNT + ", " +
                TOTAL_REVENUE +
                ") VALUES (?, ?, ?, ?, ?, ?);";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setLong(1, id);
            preparedStatement.setBytes(2, firstSentTxn);
            preparedStatement.setBytes(3, lastSentTxn);
            preparedStatement.setArray(4, conn.createArrayOf("BYTEA", transactionHashes));
            preparedStatement.setInt(5, transactionsCount);
            preparedStatement.setLong(6, totalRevenue);

            logger.info("QUERY: {}", preparedStatement.toString());

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

            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }


    //>>>>>>>UPDATE<<<<<<<<
    public static void updateInitialDelegations(String userAddress, String validatorAddress, long initialDelegation) {
        String sql = "INSERT INTO \"InitialDelegation\" ("+USER_ADDRESS+", "+VALIDATOR_ADDRESS+", "+INITIAL_DELEGATION+") " +
                "VALUES (?, ?, ?) " +
                "ON CONFLICT ("+USER_ADDRESS+", "+VALIDATOR_ADDRESS+") DO UPDATE SET "+INITIAL_DELEGATION+" = ?;";

        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, userAddress);
            preparedStatement.setString(2, validatorAddress);
            preparedStatement.setLong(3, initialDelegation);
            preparedStatement.setLong(4, initialDelegation);

            logger.info("QUERY: {}", preparedStatement);

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

            logger.info("QUERY: {}", preparedStatement.toString());

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
    public static void updateVM(long id, byte[] firstSentTxn, byte[] lastSentTxn, byte[][] transactionHashes, int transactionsCount, long totalRevenue) {
        String sql = "UPDATE \"VM\" SET " +
                FIRST_SENT_TXN + " = ?, " +
                LAST_SENT_TXN + " = ?, " +
                TRANSACTION_HASHES + " = ?, " +
                TRANSACTIONS_COUNT + " = ?, " +
                TOTAL_REVENUE + " = ? " +
                "WHERE " + ID + " = ?;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setBytes(1, firstSentTxn);
            preparedStatement.setBytes(2, lastSentTxn);
            preparedStatement.setArray(3, conn.createArrayOf("BYTEA", transactionHashes));
            preparedStatement.setInt(4, transactionsCount);
            preparedStatement.setLong(5, totalRevenue);
            preparedStatement.setLong(6, id);

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
        String sql = "SELECT "+INITIAL_DELEGATION+" FROM \"InitialDelegation\" WHERE "+USER_ADDRESS+" = ?;";
        JSONObject jsonObject = new JSONObject();
        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)){
            preparedStatement.setString(1, userAddress);

            logger.info("QUERY: {}", preparedStatement.toString());

            try (ResultSet rs =  preparedStatement.executeQuery()) {
                while(rs.next()) {
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
        String sql = "SELECT * FROM \"User\" WHERE "+ADDRESS+" = ?;";
        User user = null;
        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);

            logger.info("QUERY: {}", preparedStatement.toString());

            try(ResultSet rs = preparedStatement.executeQuery()) {
                if(rs.next()) {
                    byte[] firstSentTxn = rs.getBytes(FIRST_SENT_TXN);
                    byte[] lastSentTxn = rs.getBytes(LAST_SENT_TXN);
                    byte[][] transactionHashes = (byte[][]) rs.getArray(TRANSACTION_HASHES).getArray();
                    int transactionsCount = rs.getInt(TRANSACTIONS_COUNT);
                    user = new User(address, firstSentTxn, lastSentTxn, transactionHashes, transactionsCount);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
        return user;
    }
    public static boolean dbUserExists(String address) {
        String sql = "SELECT EXISTS (SELECT 1 FROM \"User\" WHERE "+ADDRESS+" = ?);";
        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);
            try(ResultSet rs = preparedStatement.executeQuery()) {
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
        String sql = "SELECT * FROM \"Block\" WHERE "+BLOCK_NUMBER+" = ?;";
        Block block = null;
        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setLong(1, blockNumber);

            logger.info("QUERY: {}", preparedStatement.toString());

            try(ResultSet rs = preparedStatement.executeQuery()) {
                if(rs.next()) {
                    String blockHash = rs.getString(BLOCK_HASH);
                    byte[] feeRecipient = rs.getBytes(FEE_RECIPIENT);
                    long timestamp = rs.getLong(TIMESTAMP);
                    int transactionsCount = rs.getInt(TRANSACTIONS_COUNT);
                    long blockReward = rs.getLong(BLOCK_REWARD);
                    int size = rs.getInt(BLOCK_SIZE);
                    String blockNumberString = ""+blockNumber;
                    block = new Block(blockNumberString, timestamp,feeRecipient, blockReward, size,transactionsCount);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        }
        return block;
    }
    public static long getLastBlockNumber() {
        String sql = "SELECT MAX("+BLOCK_NUMBER+") FROM \"Block\";";

        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql);
            ResultSet rs = preparedStatement.executeQuery()) {
            if(rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        }
        return 0;
    }
    public static Txn getDbTxn(String hash) {
        Txn txn = null;
        String tableName = getTransactionsTableName(hash);

        String sql = "SELECT * FROM " + tableName + " WHERE " + HASH + " = ?;";

        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, hash);

            try(ResultSet rs = preparedStatement.executeQuery()) {
                if(rs.next()) {
                    txn = populateTxnObject(rs);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
        return txn;
    }
    public static List<Txn> getTransactions(int limit, int offset) {
        List<Txn> txns = new ArrayList<>();
        String tableName = getTransactionsTableName("0");
        String sql = "SELECT * " +
                "FROM " + tableName +
                " ORDER BY " + BLOCK_NUMBER + " DESC, " + POSITION_IN_BLOCK + " DESC " +
                "LIMIT ? OFFSET ?;";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setInt(1, limit);
            preparedStatement.setInt(2, offset);

            logger.info("QUERY: {}", preparedStatement.toString());

            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    Txn txn = populateTxnObject(rs);
                    txns.add(txn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }

        return txns;
    }
    public static List<Txn> getLastXTransactions(int x) {
        List<Txn> txns = new ArrayList<>();
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
                        " WHERE " + BLOCK_NUMBER + " = ? " +
                        " ORDER BY " + TIMESTAMP + " DESC " +
                        " LIMIT ?;";

                try (PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
                    preparedStatement.setInt(1, maxBlockNumber);
                    preparedStatement.setInt(2, x);

                    logger.info("QUERY: {}", preparedStatement.toString());

                    try (ResultSet rs = preparedStatement.executeQuery()) {
                        while (rs.next()) {
                            Txn txn = populateTxnObject(rs);
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

            logger.info("QUERY: {}", preparedStatement.toString());

            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    long blockNumber = rs.getLong(BLOCK_NUMBER);
                    String blockHash = rs.getString(BLOCK_HASH);
                    byte[] feeRecipient = rs.getBytes(FEE_RECIPIENT);
                    long timestamp = rs.getLong(TIMESTAMP);
                    int transactionsCount = rs.getInt(TRANSACTIONS_COUNT);
                    long blockReward = rs.getLong(BLOCK_REWARD);
                    int size = rs.getInt(BLOCK_SIZE);
                    String blockNumberString = "" + blockNumber;
                    Block block = new Block(blockNumberString, timestamp, feeRecipient, blockReward, size, transactionsCount);
                    blocks.add(block);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        }

        return blocks;
    }
    public static List<Txn> getBlockTxns(String blockNumberString) {
        long blockNumber = Long.parseLong(blockNumberString);
        List<Txn> txns = new ArrayList<>();
        String tableName = getTransactionsTableName("0");
        String sql = "SELECT * FROM "+tableName+" WHERE " + BLOCK_NUMBER + " = ? " +
                "ORDER BY " + POSITION_IN_BLOCK + " ASC;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setLong(1, blockNumber);

            logger.info("QUERY: {}", preparedStatement.toString());

            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    Txn txn = populateTxnObject(rs);
                    txns.add(txn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
        return txns;
    }
    public static List<Txn> getUserTxns(String address, int page, int pageSize) {
        List<Txn> txns = new ArrayList<>();

        String tableName = getTransactionsTableName("0");

        String sql = "SELECT * FROM " + tableName + " WHERE " + FROM_ADDRESS + " = ? OR " + TO_ADDRESS + " = ? " +
                "ORDER BY " + TIMESTAMP + " ASC " +
                "LIMIT ? OFFSET ?;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);
            preparedStatement.setString(2, address);
            preparedStatement.setInt(3, pageSize);
            preparedStatement.setInt(4, (page - 1) * pageSize);

            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    Txn txn = populateTxnObject(rs);
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
            logger.info("QUERY: {}", preparedStatement.toString());

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
            logger.info("QUERY: {}", preparedStatement.toString());

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
            logger.info("QUERY: {}", preparedStatement.toString());

            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    BigDecimal averageFeeDecimal = rs.getBigDecimal("average_fee");
                    if (averageFeeDecimal != null) {
                        averageFee = averageFeeDecimal.toBigInteger();
                        logger.info("Average Transaction Fee Past 24 Hours: {}", averageFee);
                    } else {
                        logger.info("No data found for Average Transaction Fee Past 24 Hours");
                    }
                } else {
                    logger.info("No data found for Average Transaction Fee Past 24 Hours");
                }
            }
        } catch (Exception e) {
            logger.error("Error while calculating Average Transaction Fee Past 24 Hours: {}", e.toString());
            e.printStackTrace();
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
            logger.info("QUERY: {}", preparedStatement.toString());

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
            logger.info("QUERY: {}", preparedStatement.toString());

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
            logger.info("QUERY: {}", preparedStatement.toString());

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
    //============================helpers=============================================

//        public static List<DailyActivity> getDailyActivity() {
//            String sql = "SELECT " +
//                    "    DATE_TRUNC('day', TO_TIMESTAMP(\"timestamp\" / 1000)) AS day, " +
//                    "    COUNT(*) AS transaction_count, " +
//                    "    SUM(\"value\") AS total_value, " +
//                    "    SUM(\"txn_fee\") AS total_fee, " +
//                    "    SUM(\"amount_usd_value\") AS total_amount_usd, " +
//                    "    SUM(\"fee_usd_value\") AS total_fee_usd " +
//                    "FROM " +
//                    "    ( " +
//                    "        SELECT * FROM \"Transactions_Shard_0\" " +
//                    "        UNION ALL " +
//                    "        SELECT * FROM \"Transactions_Shard_1\" " +
//                    "        UNION ALL " +
//                    "        SELECT * FROM \"Transactions_Shard_2\" " +
//                    "        UNION ALL " +
//                    "        SELECT * FROM \"Transactions_Shard_3\" " +
//                    "        UNION ALL " +
//                    "        SELECT * FROM \"Transactions_Shard_4\" " +
//                    "    ) AS transactions " +
//                    "GROUP BY " +
//                    "    day " +
//                    "ORDER BY " +
//                    "    day;";
//
//            List<DailyActivity> dailyActivities = new ArrayList<>();
//
//            try (Connection conn = getConnection();
//                 PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
//
//                logger.info("QUERY: {}", preparedStatement.toString());
//
//                try (ResultSet rs = preparedStatement.executeQuery()) {
//                    while (rs.next()) {
//                        Date day = rs.getDate("day");
//                        int transactionCount = rs.getInt("transaction_count");
//                        long totalValue = rs.getLong("total_value");
//                        long totalFee = rs.getLong("total_fee");
//                        long totalAmountUsd = rs.getLong("total_amount_usd");
//                        long totalFeeUsd = rs.getLong("total_fee_usd");
//
//                        DailyActivity dailyActivity = new DailyActivity(day, transactionCount, totalValue, totalFee, totalAmountUsd, totalFeeUsd);
//                        dailyActivities.add(dailyActivity);
//                    }
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//                logger.error(e.toString());
//            }
//
//            return dailyActivities;
//        }


    //============================HELPERS=============================================
    private static Txn populateTxnObject(ResultSet rs) throws SQLException {
        return new Txn(
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
                "0",
                rs.getBoolean(SUCCESS),
                rs.getString(ERROR_MESSAGE)
        );
    }
    private static String getTransactionsTableName(String hash) {
        int shardIndex = Math.abs(hash.hashCode()) % NUMBER_OF_SHARDS;
        return "\"Transactions_Shard_" + shardIndex + "\"";
    }
}