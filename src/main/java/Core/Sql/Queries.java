package Core.Sql;

import Block.Block;
import DailyActivity.DailyActivity;
import Main.Hex;
import Main.Settings;
import Txn.Txn;
import User.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;

import static Core.Constants.Constants.*;
import static Core.DatabaseConnection.getConnection;

public class Queries {
    private static final Logger logger = LogManager.getLogger(Queries.class);
    private static final int NUMBER_OF_SHARDS = 1;

    public static void insertUser(String address, byte[] firstSentTxn, byte[] lastSentTxn, byte[][] transactionHashes, int transactionsCount) {
        String sql = "INSERT INTO \"User\" ("+ADDRESS+", "+FIRST_SENT_TXN+", "+LAST_SENT_TXN+", "+TRANSACTION_HASHES+", "+TRANSACTIONS_COUNT+") VALUES (?, ?, ?, ?, ?);";
        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);
            preparedStatement.setBytes(2, firstSentTxn);
            preparedStatement.setBytes(3, lastSentTxn);
            preparedStatement.setArray(4, conn.createArrayOf("BYTEA", transactionHashes));
            preparedStatement.setInt(5, transactionsCount);

            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }

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

    public static void insertBlock(long blockNumber, String blockHash, byte[] feeRecipient, long timestamp, int transactionsCount, long blockReward, int size) {
        String sql = "INSERT INTO \"Block\" ("+
                BLOCK_NUMBER+", "+
                BLOCK_HASH+", "+
                FEE_RECIPIENT+", "+
                TIMESTAMP+", "+
                TRANSACTIONS_COUNT+", "+
                BLOCK_REWARD+", "+
                BLOCK_SIZE+
                ") VALUES (?, ?, ?, ?, ?, ?, ?);";

        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setLong(1, blockNumber);
            preparedStatement.setString(2, blockHash);
            preparedStatement.setBytes(3, feeRecipient);
            preparedStatement.setLong(4, timestamp);
            preparedStatement.setInt(5, transactionsCount);
            preparedStatement.setLong(6, blockReward);
            preparedStatement.setInt(7, size);

            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        }catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
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

    public static void insertTxn(String hash, long blockNumber, int size, int positionInBlock, String fromAddress, String toAddress, long timestamp, long value, long txnFee, byte[] txnData, String txnType, long amountUsdValue, long feeUsdValue) {
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
                FEE_USD_VALUE +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

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

            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
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

    public static List<Txn> getLastXTransactions(int x) {
        List<Txn> txns = new ArrayList<>();
        String tableName = getTransactionsTableName("0");
        String sql = "SELECT * FROM " + tableName +
                " ORDER BY " + BLOCK_NUMBER + " DESC, " + POSITION_IN_BLOCK + " DESC " +
                "LIMIT ?;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setInt(1, x);

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

        // Reverse the order of transactions to have the latest first
        Collections.reverse(txns);

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

        // Reverse the order of blocks to have the latest first
        Collections.reverse(blocks);

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
    public static List<Txn> getUserTxns(String address) {
        List<Txn> txns = new ArrayList<>();
        String tableName = getTransactionsTableName("0");
        String sql = "SELECT * FROM "+tableName +" WHERE " + FROM_ADDRESS + " = ? OR " + TO_ADDRESS + " = ? " +
                "ORDER BY " + TIMESTAMP + " ASC;";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);
            preparedStatement.setString(2, address);
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

    public static double getAverageTransactionFeePercentageChange() {
        double percentageChange = 0.0;
        String tableName = getTransactionsTableName("0");
        String sql = "SELECT " +
                "(AVG(\"" + TXN_FEE + "\") FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() - INTERVAL '24 hours')) * 1000) / " +
                "AVG(\"" + TXN_FEE + "\") FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() - INTERVAL '48 hours')) * 1000 AND " +
                "\"" + TIMESTAMP + "\" < EXTRACT(EPOCH FROM (NOW() - INTERVAL '24 hours')) * 1000) - 1) * 100 AS percentage_change " +
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
                "(SUM(\"" + TXN_FEE + "\") FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() - INTERVAL '24 hours')) * 1000) / " +
                "SUM(\"" + TXN_FEE + "\") FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() - INTERVAL '48 hours')) * 1000 AND " +
                "\"" + TIMESTAMP + "\" < EXTRACT(EPOCH FROM (NOW() - INTERVAL '24 hours')) * 1000) - 1) * 100 AS percentage_change " +
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
                " WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() - INTERVAL '24 hours')) * 1000;";

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
                " WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() - INTERVAL '24 hours')) * 1000;";

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
                " WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() - INTERVAL '24 hours')) * 1000;";

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
                "WHEN COUNT(*) FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() - INTERVAL '48 hours')) * 1000 AND " +
                "\"" + TIMESTAMP + "\" < EXTRACT(EPOCH FROM (NOW() - INTERVAL '24 hours')) * 1000) = 0 THEN 0 " +
                "ELSE (COUNT(*) FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() - INTERVAL '24 hours')) * 1000) * 1.0 / " +
                "COUNT(*) FILTER (WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() - INTERVAL '48 hours')) * 1000 AND " +
                "\"" + TIMESTAMP + "\" < EXTRACT(EPOCH FROM (NOW() - INTERVAL '24 hours')) * 1000) - 1) * 100 " +
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


    //============================helpers=============================================
    private static Txn populateTxnObject(ResultSet rs) throws SQLException {
        return new Txn(
                rs.getString(HASH),
                rs.getInt(SIZE),
                rs.getInt(POSITION_IN_BLOCK),
                rs.getLong(BLOCK_NUMBER),
                rs.getBytes(FROM_ADDRESS),
                rs.getString(TO_ADDRESS),
                rs.getLong(TIMESTAMP),
                rs.getLong(VALUE),
                rs.getLong(TXN_FEE),
                rs.getBytes(TXN_DATA),
                rs.getString(TXN_TYPE),
                "0x0"
        );
    }

    private static String getTransactionsTableName(String hash) {
        int shardIndex = Math.abs(hash.hashCode()) % NUMBER_OF_SHARDS;
        return "\"Transactions_Shard_" + shardIndex + "\"";
    }
}