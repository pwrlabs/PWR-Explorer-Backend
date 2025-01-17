package Database;

import DataModel.Block;
import Utils.Settings;
import DataModel.NewTxn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static Database.Constants.Constants.*;
import static Database.DatabaseConnection.getConnection;

public class Queries {
    private static final Logger logger = LogManager.getLogger(Queries.class);
    private static final int NUMBER_OF_SHARDS = 1;

    //#region Insert functions
    public static void insertTxn(
            String hash, long blockNumber, int positionInBlock, String fromAddress, String toAddress,
            long timestamp, long value, String txnType, long txnFee, Boolean success
    ) {
        String tableName = getTransactionsTableName("0");
        String sql = "INSERT INTO " + tableName + " (" +
                HASH + ", " +
                BLOCK_NUMBER + ", " +
                POSITION_IN_BLOCK + ", " +
                FROM_ADDRESS + ", " +
                TO_ADDRESS + ", " +
                TIMESTAMP + ", " +
                VALUE + ", " +
                TXN_TYPE + ", " +
                TXN_FEE + ", " +
                SUCCESS +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try {
            executeUpdate(sql, hash, blockNumber, positionInBlock,
                    fromAddress.toLowerCase(), toAddress.toLowerCase(), timestamp,
                    value, txnType, txnFee, success);
        } catch (Exception e) {
            logger.error("Failed to insert txn {}:  ", hash, e);
        }
    }

    public static void insertBlock(
            long blockNumber, String blockHash, String feeRecipient, long timestamp, int transactionsCount,
            long blockReward, int size, boolean success
    ) {
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
                SUCCESS +
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
            logger.error("Failed to insert block {}: {} ", blockNumber, e.getLocalizedMessage());
        }
    }

    public static void insertValidator(String address, long joiningTime) {
        String sql = "INSERT INTO \"Validator\" (" +
                ADDRESS + ", " +
                JOINING_TIME + ", " +
                LIFETIME_REWARDS + ", " +
                SUBMITTED_BLOCKS + ", " +
                BLOCKS_SUBMITTED +
                ") VALUES (?,?,0,0,0)";

        try {
            executeUpdate(sql, address, joiningTime);
        } catch (Exception e) {
            logger.error("Failed to insert Validator: ", e);
        }
    }

    public static void upsertUserHistory(String address, String txnHash, long txnTimestamp, int incrementCount) {
        String sql = "INSERT INTO \"UsersHistory\" (" +
                "\"address\", " +
                "\"transaction_count\", " +
                "\"first_txn_timestamp\", " +
                "\"first_txn_hash\", " +
                "\"last_txn_timestamp\", " +
                "\"last_txn_hash\"" +
                ") VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (\"address\") DO UPDATE SET " +
                "\"transaction_count\" = \"UsersHistory\".\"transaction_count\" + ?, " +
                "\"last_txn_timestamp\" = ?, " +
                "\"last_txn_hash\" = ?";

        try {
            executeUpdate(sql, address, incrementCount, txnTimestamp, txnHash, txnTimestamp, txnHash, incrementCount, txnTimestamp, txnHash);
        } catch (SQLException e) {
            logger.error("Error upserting user history for address {}: {}", address, e.getLocalizedMessage());
            throw new RuntimeException("Failed to upsert user history", e);
        }
    }
    //#endregion


    //#region Update functions
    public static void updateInitialDelegations(String userAddress, String validatorAddress, long initialDelegation) {
        String sql = "INSERT INTO \"InitialDelegation\" (" + USER_ADDRESS + ", " + VALIDATOR_ADDRESS + ", " + INITIAL_DELEGATION + ") " +
                "VALUES (?, ?, ?) " +
                "ON CONFLICT (" + USER_ADDRESS + ", " + VALIDATOR_ADDRESS + ") DO UPDATE SET " + INITIAL_DELEGATION + " = ?;";

        try {
            executeUpdate(sql, userAddress, validatorAddress, initialDelegation, initialDelegation);
        } catch (Exception e) {
            logger.error("Failed to update initial delegation for user {}: {}", userAddress, e.getLocalizedMessage());
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

        try {
            executeUpdate(sql, blockHash, feeRecipient, timestamp, transactionsCount, blockReward, size, success, blockNumber);
        } catch (Exception e) {
            logger.error("Failed to update block {}: {}", blockNumber, e.getLocalizedMessage());
        }
    }
    //#endregion


    //#region Get functions
    public static Block getDbBlock(long blockNumber) {
        String sql = "SELECT * FROM \"Block\" WHERE " + BLOCK_NUMBER + " = ?;";
        Block block = null;

        try (QueryResult result = executeQuery(sql, blockNumber)) {
            ResultSet rs = result.ResultSet();
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
        } catch (Exception e) {
            logger.error("Failed to get block {} from db: {}", blockNumber, e.getLocalizedMessage());
        }
        return block;
    }

    public static long getLastBlockNumber() {
        String sql = "SELECT MAX(" + BLOCK_NUMBER + ") FROM \"Block\";";

        try (QueryResult result = executeQuery(sql)) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            logger.error("Failed to get last block number: {}", e.getLocalizedMessage());
        }
        return 0;
    }

    public static long getLatestBlockNumberForFeeRecipient(String feeRecipient) {
        String sql = "SELECT " + TIMESTAMP + " FROM \"Block\" " +
                "WHERE LOWER(" + FEE_RECIPIENT + ") = ? " +
                "ORDER BY " + BLOCK_NUMBER + " DESC " +
                "LIMIT 1";

        try (QueryResult result = executeQuery(sql, feeRecipient)) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                return rs.getLong(TIMESTAMP);
            }
        } catch (SQLException e) {
            logger.error("Error getting latest block number for fee recipient {}: {}", feeRecipient, e.getMessage());
        }
        return -1;
    }

    public static double getAverageTps(int numberOfBlocks, long lastBlockNumber) {
        String sql = "SELECT " + TRANSACTIONS_COUNT + ", " + TIMESTAMP + " FROM \"Block\" " +
                "WHERE " + BLOCK_NUMBER + " > ? " +
                "ORDER BY " + BLOCK_NUMBER + " DESC " +
                "LIMIT ?";

        long totalTxns = 0;
        Long firstTimestamp = null;
        Long lastTimestamp = null;

        try (QueryResult result = executeQuery(sql, lastBlockNumber - numberOfBlocks, numberOfBlocks)) {
            ResultSet rs = result.ResultSet();
            while (rs.next()) {
                totalTxns += rs.getInt(TRANSACTIONS_COUNT);
                long timestamp = rs.getLong(TIMESTAMP);

                if (firstTimestamp == null) {
                    firstTimestamp = timestamp;
                }
                lastTimestamp = timestamp;
            }

            if (firstTimestamp == null || firstTimestamp.equals(lastTimestamp)) {
                return 0;
            }

            // Calculate time difference in seconds
            double timeSpanSeconds = (firstTimestamp - lastTimestamp) / 1000.0;

            // Calculate TPS
            return BigDecimal.valueOf(totalTxns).divide(BigDecimal.valueOf(timeSpanSeconds), 1, BigDecimal.ROUND_HALF_UP)
                    .doubleValue();
        } catch (Exception e) {
            logger.error("Failed to calculate TPS: {}", e.getLocalizedMessage());
            return 0;
        }
    }

    public static NewTxn getDbTxn(String hash) {
        NewTxn txn = null;
        String tableName = getTransactionsTableName(hash);

        String sql = "SELECT * FROM " + tableName + " WHERE " + HASH + " = ?;";

        try (QueryResult result = executeQuery(sql, hash)) {

            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                txn = populateNewTxnObject(rs);
            }
        } catch (Exception e) {
            logger.error("An error occurred while retrieving txn details: {}", e.getLocalizedMessage());
        }
        return txn;
    }

    public static String getBlockHash(long blockNumber) {
        String blockHash = null;
        String sql = "SELECT " + BLOCK_HASH + " FROM \"Block\" WHERE " + BLOCK_NUMBER + " = ?;";

        try (QueryResult result = executeQuery(sql, blockNumber)) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                blockHash = rs.getString(BLOCK_HASH);
            }
        } catch (Exception e) {
            logger.error("Failed to get hash of block {}: {}", blockNumber, e.getLocalizedMessage());
        }

        return blockHash;
    }

    public static List<NewTxn> getTransactions(int pageSize, int page) {
        List<NewTxn> txns = new ArrayList<>();
        String tableName = getTransactionsTableName("0");

        if (pageSize * page > 100_000) {
            page = (int) Math.ceil((double) 100_000 / pageSize);
        }

        String sql = "SELECT * " +
                "FROM " + tableName +
                " WHERE id > ? " +
                "ORDER BY id DESC " +
                "LIMIT ?;";

        try (QueryResult result = executeQuery(sql, page, pageSize)) {
            ResultSet rs = result.ResultSet();
            while (rs.next()) {
                NewTxn txn = populateNewTxnObject(rs);
                txns.add(txn);
            }
        } catch (Exception e) {
            logger.error("Failed to get txns: {}", e.getLocalizedMessage());
        }

        return txns;
    }

    public static List<NewTxn> getLastXTransactions(int x) {
        List<NewTxn> txns = new ArrayList<>();
        String tableName = getTransactionsTableName("0");

        // Query to get transactions from the highest block number
        String sql = "SELECT * FROM " + tableName +
                " ORDER BY " + TIMESTAMP + " DESC " +
                " LIMIT ?;";

        try (QueryResult result = executeQuery(sql, x)) {
            ResultSet rs = result.ResultSet();
            while (rs.next()) {
                NewTxn txn = populateNewTxnObject(rs);
                txns.add(txn);
            }
        } catch (Exception e) {
            logger.error("Failed to get past {} txns: ", x, e);
        }

        return txns;
    }

    public static List<Block> getLastXBlocks(int x) {
        List<Block> blocks = new ArrayList<>();
        String sql = "SELECT * FROM \"Block\" " +
                "ORDER BY " + BLOCK_NUMBER + " DESC " +
                "LIMIT ?;";

        try (QueryResult result = executeQuery(sql, x)) {
            ResultSet rs = result.ResultSet();
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
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        return blocks;
    }

    public static List<Block> getLastXBlocks(int pageSize, int page) {
        List<Block> blocks = new ArrayList<>();
        String sql = "SELECT * FROM \"Block\" " +
                "ORDER BY " + BLOCK_NUMBER + " DESC " +
                "LIMIT ? OFFSET ?";

        if (pageSize * page > 100_000) {
            page = (int) Math.ceil((double) 100_000 / pageSize);
        }

        try (QueryResult result = executeQuery(sql, pageSize, page)) {
            ResultSet rs = result.ResultSet();
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

        } catch (Exception e) {
            logger.error("Error retrieving blocks: {}", e.getLocalizedMessage());
        }
        return blocks;
    }

    public static List<NewTxn> getBlockTxns(String blockNumberString) {
        long blockNumber = Long.parseLong(blockNumberString);
        List<NewTxn> txns = new ArrayList<>();
        String tableName = getTransactionsTableName("0");
        String sql = "SELECT * FROM " + tableName + " WHERE " + BLOCK_NUMBER + " = ? " +
                "ORDER BY " + POSITION_IN_BLOCK + " ASC;";

        try (QueryResult result = executeQuery(sql, blockNumber)) {
            ResultSet rs = result.ResultSet();
            while (rs.next()) {
                NewTxn txn = populateNewTxnObject(rs);
                txns.add(txn);
            }
        } catch (Exception e) {
            logger.error("Failed to get block {} txns: {}", blockNumber, e.getLocalizedMessage());
        }
        return txns;
    }

    public static long getValidatorJoiningTime(String address) {
        String sql = "SELECT " + JOINING_TIME + " FROM Validator WHERE " + ADDRESS + " = ?";
        try (QueryResult result = executeQuery(sql, address)) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve validator {} joining time: {}", address, e.getLocalizedMessage());
        }
        return 0;
    }

    public static List<NewTxn> getUserTxns(String address, int page, int pageSize) {
        List<NewTxn> txns = new ArrayList<>();

        if (pageSize * page > 100_000) {
            page = (int) Math.ceil((double) 100_000 / pageSize);
        }

        String tableName = getTransactionsTableName("0");

        String sql = "SELECT * FROM " + tableName + " WHERE " + FROM_ADDRESS + " = ? OR " + TO_ADDRESS + " = ? " +
                "ORDER BY " + TIMESTAMP + " DESC " +
                "LIMIT ? OFFSET ?;";

        try (QueryResult result = executeQuery(sql, address, address, pageSize, (page - 1) * pageSize)) {
            ResultSet rs = result.ResultSet();
            while (rs.next()) {
                NewTxn txn = populateNewTxnObject(rs);
                txns.add(txn);
            }
        } catch (Exception e) {
            logger.error("Failed to get txns for user {}: {}", address, e.getLocalizedMessage());
        }

        return txns;
    }

    public static double getAverageTransactionFeePercentageChange() {
        double percentageChange = 0.0;
        String tableName = getTransactionsTableName("0");

        String sql = "SELECT " +
                "CASE WHEN COUNT(*) FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) = 0 THEN 0 " +
                "ELSE ((AVG(" + TXN_FEE + ") FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) / " +
                "AVG(" + TXN_FEE + ") FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '48 hours')) * 1000 AND " +
                TIMESTAMP + " < EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) - 1) * 100) " +
                "END AS percentage_change " +
                "FROM " + tableName + ";";

        try (QueryResult result = executeQuery(sql)) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                percentageChange = rs.getDouble("percentage_change");
                logger.info("Average Transaction Fee Percentage Change: {}", percentageChange);
            } else {
                logger.info("No data found for Average Transaction Fee Percentage Change");
            }
        } catch (Exception e) {
            logger.error("Error while calculating Average Transaction Fee Percentage Change: {}", e.getLocalizedMessage());
        }

        return percentageChange;
    }

    public static double getTotalTransactionFeesPercentageChange() {
        double percentageChange = 0.0;
        String tableName = getTransactionsTableName("0");

        String sql = "SELECT " +
                "CASE WHEN COUNT(*) FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) = 0 THEN 0 " +
                "ELSE ((SUM(" + TXN_FEE + ") FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) / " +
                "SUM(" + TXN_FEE + ") FILTER (WHERE \"" + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '48 hours')) * 1000 AND \"" + TIMESTAMP + "\" < EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) - 1) * 100) " +
                "END AS percentage_change " +
                "FROM " + tableName + ";";

        try (QueryResult result = executeQuery(sql)) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                percentageChange = rs.getDouble("percentage_change");
                logger.info("Total Transaction Fees Percentage Change: {}", percentageChange);
            } else {
                logger.info("No data found for Total Transaction Fees Percentage Change");
            }

        } catch (Exception e) {
            logger.error("Error while calculating Total Transaction Fees Percentage Change: {}", e.getLocalizedMessage());
        }

        return percentageChange;
    }

    public static BigInteger getAverageTransactionFeePast24Hours() {
        BigInteger averageFee = BigInteger.ZERO;
        String tableName = getTransactionsTableName("0");

        String sql = "SELECT AVG(" + TXN_FEE + ") AS average_fee FROM " + tableName +
                " WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000;";

        try (QueryResult result = executeQuery(sql)) {
            ResultSet rs = result.ResultSet();
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
        } catch (Exception e) {
            logger.error("Error while calculating Average Transaction Fee Past 24 Hours: {}", e.getLocalizedMessage());
        }

        return averageFee;
    }

    public static BigInteger getTotalTransactionFeesPast24Hours() {
        BigInteger totalFees = BigInteger.ZERO;
        String tableName = getTransactionsTableName("0");

        String sql = "SELECT SUM(" + TXN_FEE + ") AS total_fees FROM " + tableName +
                " WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000;";

        try (QueryResult result = executeQuery(sql)) {
            ResultSet rs = result.ResultSet();
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
        } catch (Exception e) {
            logger.error("Error while calculating Total Transaction Fees Past 24 Hours: {}", e.getLocalizedMessage());
        }

        return totalFees;
    }

    public static int getTransactionCountPast24Hours() {
        int transactionCount = 0;
        String tableName = getTransactionsTableName("0");

        String sql = "SELECT COUNT(*) AS transaction_count FROM " + tableName +
                " WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000;";

        try (QueryResult result = executeQuery(sql)) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                transactionCount = rs.getInt("transaction_count");
                logger.info("Transaction Count Past 24 Hours: {}", transactionCount);
            } else {
                logger.info("No data found for Transaction Count Past 24 Hours");
            }
        } catch (Exception e) {
            logger.error("Error while calculating Transaction Count Past 24 Hours: {}", e.getLocalizedMessage());
        }

        return transactionCount;
    }

    public static double getTransactionCountPercentageChangeComparedToPreviousDay() {
        double percentageChange = 0.0;
        String tableName = getTransactionsTableName("0");

        String sql = "SELECT CASE " +
                "WHEN COUNT(*) FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '48 hours')) * 1000 AND " + TIMESTAMP + " < EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) = 0 THEN 0 " +
                "ELSE (COUNT(*) FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) * 1.0 / " +
                "COUNT(*) FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '48 hours')) * 1000 AND " + TIMESTAMP + " < EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) - 1) * 100 " +
                "END AS percentage_change " +
                "FROM " + tableName + ";";

        try (QueryResult result = executeQuery(sql)) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                percentageChange = rs.getDouble("percentage_change");
                logger.info("Transaction Count Percentage Change Compared to Previous Day: {}", percentageChange);
            } else {
                logger.info("No data found for Transaction Count Percentage Change Compared to Previous Day");
            }
        } catch (Exception e) {
            logger.error("Error while calculating Transaction Count Percentage Change Compared to Previous Day: {}", e.getLocalizedMessage());
        }

        return percentageChange;
    }

    public static JSONObject get24HourBlockStats() {
        final long MILLIS_PER_DAY = 86400000; // 24 * 60 * 60 * 1000
        long currentTimeMillis = System.currentTimeMillis();

        String sql = "SELECT COALESCE(SUM(" + SIZE + "), 0) as total_size, " +
                "COALESCE(SUM(" + BLOCK_REWARD + "), 0) as total_rewards, " +
                "COUNT(*) as block_count, " +
                "COALESCE(AVG(" + SIZE + "), 0) as avg_size, " +
                "COALESCE(SUM(" + TRANSACTIONS_COUNT + "), 0) as total_txns " +
                "FROM \"Block\" WHERE " + TIMESTAMP + " >= ? AND " + TIMESTAMP + " < ?";

        try (QueryResult result = executeQuery(sql, currentTimeMillis - MILLIS_PER_DAY, currentTimeMillis)) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                int blockCount = rs.getInt("block_count");
                int avgBlockSize = (int) rs.getDouble("avg_size");
                long totalRewards = rs.getLong("total_rewards");
                long totalTxns = rs.getLong("total_txns");
                double networkUtilization = blockCount > 0
                        ? BigDecimal.valueOf(((double) avgBlockSize / (double) Settings.getBlockSizeLimit()) * 100)
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
        } catch (SQLException e) {
            logger.error("Error getting 24h block stats: {}", e.getLocalizedMessage());
        }

        return new JSONObject()
                .put("blocksCount", 0)
                .put("averageBlockSize", 0)
                .put("totalRewards", 0)
                .put("totalTransactions", 0)
                .put("networkUtilization", 0.0);
    }

    public static BigInteger getTotalFees() {
        String tableName = getTransactionsTableName("0");
        // First get the min and max day timestamps from the table
        String boundsQuery = "SELECT " +
                "MIN(timestamp / 86400000) as min_day, " +
                "MAX(timestamp / 86400000) as max_day " +
                "FROM " + tableName;

        try (Connection conn = DatabaseConnection.getConnection()) {
            long startDay, endDay;

            // Get the full range of days
            try (PreparedStatement stmt = conn.prepareStatement(boundsQuery);
                 ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return BigInteger.ZERO;
                startDay = rs.getLong("min_day");
                endDay = rs.getLong("max_day");
            }

            // Define partition size (adjust based on your data distribution)
            int PARTITION_SIZE = 30;

            // Calculate number of partitions needed
            int numPartitions = (int) Math.ceil((endDay - startDay + 1) / (double) PARTITION_SIZE);

            // Sum across all partitions
            return (BigInteger) IntStream.range(0, numPartitions)
                    .parallel()
                    .mapToObj(partition -> {
                        long partitionStart = startDay + (partition * PARTITION_SIZE);
                        long partitionEnd = Math.min(partitionStart + PARTITION_SIZE, endDay);

                        String sql = "SELECT COALESCE(SUM(txn_fee), 0) " +
                                "FROM " + tableName + " " +
                                "WHERE timestamp / 86400000 BETWEEN ? AND ?";

                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            stmt.setLong(1, partitionStart);
                            stmt.setLong(2, partitionEnd);

                            try (ResultSet rs = stmt.executeQuery()) {
                                return rs.next() ? rs.getLong(1) : 0L;
                            }
                        } catch (SQLException e) {
                            logger.error("Error summing fees for partition {}: {}", partition, e.getLocalizedMessage());
                            return BigInteger.ZERO;
                        }
                    })
                    .reduce(BigInteger.ZERO, (a, b) -> ((BigInteger) a).add((BigInteger) b)); // Added explicit casting

        } catch (SQLException e) {
            logger.error("Error getting timestamp bounds for shard: {}", e.getLocalizedMessage());
            return BigInteger.ZERO;
        }
    }

    public static int getTotalTransactionCount() {
        int totalCount = 0;
        String tableName = getTransactionsTableName("0");
        String sql = "SELECT COUNT(*) AS total_count FROM " + tableName;

        try (QueryResult result = executeQuery(sql)) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                totalCount = rs.getInt("total_count");
            }
        } catch (Exception e) {
            logger.error("Failed to get txns count: {}", e.getLocalizedMessage());
        }

        return totalCount;
    }

    public static int getTotalTxnCount(String address) {
        String sql = "SELECT transaction_count FROM \"UsersHistory\" WHERE address = ?";

        try (QueryResult result = executeQuery(sql, "0x" + address.toLowerCase())) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            logger.error("Error getting results from parallel queries", e);
        }

        return 0;
    }

    public static Pair<NewTxn, NewTxn> getFirstAndLastTransactionsByAddress(String address) {
        NewTxn firstTxn = null;
        NewTxn lastTxn = null;

        String sql = "SELECT first_txn_timestamp, first_txn_hash, " +
                "last_txn_timestamp, last_txn_hash " +
                "FROM \"UsersHistory\" WHERE address = ?";

        try (QueryResult result = executeQuery(sql, "0x" + address.toLowerCase())) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                // Populate first transaction
                firstTxn = new NewTxn(
                        rs.getString("first_txn_hash"),
                        0,
                        0,
                        "",
                        "",
                        rs.getLong("first_txn_timestamp"),
                        0,
                        "",
                        0,
                        false
                );

                // Populate last transaction
                lastTxn = new NewTxn(
                        rs.getString("last_txn_hash"),
                        0,
                        0,
                        "",
                        "",
                        rs.getLong("last_txn_timestamp"),
                        0,
                        "",
                        0,
                        false
                );
            }
        } catch (Exception e) {
            logger.error("Error querying transactions for address {}: {}", address, e.getMessage());
        }

        return new Pair<>(firstTxn, lastTxn);
    }

    public static Map<Long, Integer> getFourteenDaysTxn() {
        final int DAYS_TO_FETCH = 14;
        final long MILLIS_PER_DAY = 86400000; // 24 * 60 * 60 * 1000
        String tableName = getTransactionsTableName("0");

        TreeMap<Long, Integer> txns = new TreeMap<>(Collections.reverseOrder());

        // Calculate current day's start timestamp
        long currentTimeMillis = System.currentTimeMillis();
        long currentDayStart = (currentTimeMillis / MILLIS_PER_DAY) * MILLIS_PER_DAY;
        long startTimeMillis = currentDayStart - ((DAYS_TO_FETCH - 1) * MILLIS_PER_DAY);

        String sql = "SELECT (" + TIMESTAMP + " / " + MILLIS_PER_DAY + ") * " + MILLIS_PER_DAY + " as day_start, " +
                "COUNT(*) as count " +
                "FROM " + tableName + " " +
                "WHERE " + TIMESTAMP + " >= ? AND " + TIMESTAMP + " < ? " +
                "GROUP BY (" + TIMESTAMP + " / " + MILLIS_PER_DAY + ") * " + MILLIS_PER_DAY + " " +
                "ORDER BY day_start DESC";

        try (QueryResult result = executeQuery(sql, startTimeMillis, currentDayStart + MILLIS_PER_DAY)) {
            ResultSet rs = result.ResultSet();

            // Initialize map with zeros for all days at day start boundaries
            for (int i = 0; i < DAYS_TO_FETCH; i++) {
                long dayStartMillis = startTimeMillis + (i * MILLIS_PER_DAY);
                txns.put(dayStartMillis, 0);
            }

            while (rs.next()) {
                long dayStartMillis = rs.getLong("day_start");
                int count = rs.getInt("count");
                txns.put(dayStartMillis, count);
            }
        } catch (SQLException e) {
            logger.error("Error fetching {} days transactions: {}", DAYS_TO_FETCH, e.getLocalizedMessage());
        }

        return txns;
    }

    public static JSONArray getBlocksCreated(String address, int pageSize, int page) {
        String sql = "SELECT " + BLOCK_NUMBER + ", " + TIMESTAMP + ", " + SUCCESS + ", " + BLOCK_REWARD + ", " + TRANSACTIONS_COUNT + " " +
                "FROM \"Block\" WHERE LOWER(" + FEE_RECIPIENT + ") = ? " +
                "ORDER BY " + TIMESTAMP + " DESC " +
                "LIMIT ? OFFSET ?";

        if (pageSize * page > 100_000) {
            page = (int) Math.ceil((double) 100_000 / pageSize);
        }

        JSONArray blocks = new JSONArray();
        try (QueryResult result = executeQuery(sql, address, pageSize, page)) {
            ResultSet rs = result.ResultSet();
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
        } catch (SQLException e) {
            logger.error("Failed to fetch created blocks {}", e.getLocalizedMessage());
        }
        return blocks;
    }

    public static int getBlocksSubmitted(String address) {
        String sql = "SELECT COUNT(" + BLOCK_HASH + ") FROM \"Block\" WHERE LOWER(" + FEE_RECIPIENT + ") = ?";

        try (QueryResult result = executeQuery(sql, address)) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            logger.error("Failed to fetch submitted blocks {}", e.getLocalizedMessage());
            return 0;
        }
    }

    public static boolean isNewUser(String address) {
        System.out.println("Checking address " + address);
        String sql = "SELECT COUNT(*) FROM \"UsersHistory\" WHERE \"address\" = ?";
        try (QueryResult result = executeQuery(sql, "0x" + address)) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        } catch (Exception e) {
            logger.error("Error checking if user exists: {} {}", address, e.getMessage());
        }
        return false;
    }
    //#endregion


    // These functions to be deleted after integrating the users history table
    public static Pair<NewTxn, NewTxn> getFirstAndLastTransactionssByAddress(String address) {
        CompletableFuture<NewTxn> firstTxnFuture = CompletableFuture.supplyAsync(() -> {
            NewTxn firstTxn = null;
            String tableName = getTransactionsTableName("0");

            String sql = "(" +
                    "SELECT * FROM " + tableName + " WHERE " + FROM_ADDRESS + " = ? " +
                    "ORDER BY " + TIMESTAMP + " ASC LIMIT 1" +
                    ") UNION ALL (" +
                    "SELECT * FROM " + tableName + " WHERE " + TO_ADDRESS + " = ? " +
                    "ORDER BY " + TIMESTAMP + " ASC LIMIT 1" +
                    ") ORDER BY " + TIMESTAMP + " ASC LIMIT 1";

            try (Connection conn = getConnection();
                 PreparedStatement preparedStatement = conn.prepareStatement(sql)) {

                preparedStatement.setString(1, address);
                preparedStatement.setString(2, address);

                try (ResultSet rs = preparedStatement.executeQuery()) {
                    if (rs.next()) {
                        firstTxn = populateNewTxnObject(rs);
                    }
                }
            } catch (Exception e) {
                logger.error("Error querying first transaction for address {}: {}", address, e.getMessage());
            }
            return firstTxn;
        });

        CompletableFuture<NewTxn> lastTxnFuture = CompletableFuture.supplyAsync(() -> {
            NewTxn lastTxn = null;
            String tableName = getTransactionsTableName("0");

            String sql = "(" +
                    "SELECT * FROM " + tableName + " WHERE " + FROM_ADDRESS + " = ? " +
                    "ORDER BY " + TIMESTAMP + " DESC LIMIT 1" +
                    ") UNION ALL (" +
                    "SELECT * FROM " + tableName + " WHERE " + TO_ADDRESS + " = ? " +
                    "ORDER BY " + TIMESTAMP + " DESC LIMIT 1" +
                    ") ORDER BY " + TIMESTAMP + " DESC LIMIT 1";

            try (Connection conn = getConnection();
                 PreparedStatement preparedStatement = conn.prepareStatement(sql)) {

                preparedStatement.setString(1, address);
                preparedStatement.setString(2, address);

                try (ResultSet rs = preparedStatement.executeQuery()) {
                    if (rs.next()) {
                        lastTxn = populateNewTxnObject(rs);
                    }
                }
            } catch (Exception e) {
                logger.error("Error querying last transaction for address {}: {}", address, e.getMessage());
            }
            return lastTxn;
        });

        try {
            // Wait for both futures to complete and combine results
            NewTxn firstTxn = firstTxnFuture.get();
            NewTxn lastTxn = lastTxnFuture.get();
            return new Pair<>(firstTxn, lastTxn);
        } catch (Exception e) {
            logger.error("Error while querying transactions in parallel for address {}: {}", address, e.getMessage());
        }

        return new Pair<>(null, null);
    }
    public static void populateUsersHistoryTable() {
        logger.info("Starting to populate UsersHistory table...");

        for (int shardIndex = 0; shardIndex < NUMBER_OF_SHARDS; shardIndex++) {
            String tableName = "\"Transactions_Shard_" + shardIndex + "\"";

            String addressesSql = "SELECT DISTINCT address FROM ( " +
                    "SELECT from_address as address FROM " + tableName +
                    " UNION SELECT to_address as address FROM " + tableName +
                    " WHERE to_address IS NOT NULL) AS all_addresses";

            Set<String> addresseSet = new HashSet<>();

            try (QueryResult result = executeQuery(addressesSql)) {
                ResultSet rs = result.ResultSet();
                while (rs.next()) {
                    addresseSet.add(rs.getString("address"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            for (String address : addresseSet) {
                try {
                    String selectFromQuery = "SELECT COUNT(*) AS count FROM " + tableName + " WHERE " + FROM_ADDRESS + " = ?";
                    String selectToQuery = "SELECT COUNT(*) AS count FROM " + tableName + " WHERE " + TO_ADDRESS + " = ?";

                    String formattedAddress = address;
                    if (!address.startsWith("0x")) {
                        try {
                            new BigInteger(address);  // If it's a number, use as is
                        } catch (NumberFormatException e) {
                            formattedAddress = "0x" + address;  // Not a number, add prefix
                        }
                    }

                    CompletableFuture<Integer> fromCountFuture = CompletableFuture.supplyAsync(() -> {
                        try (QueryResult result = executeQuery(selectFromQuery, address)) {
                            ResultSet rs = result.ResultSet();
                            return rs.next() ? rs.getInt("count") : 0;
                        } catch (SQLException e) {
                            logger.error("Failed to get sender address txns count: {}", e.getLocalizedMessage());
                            return 0;
                        }
                    });

                    CompletableFuture<Integer> toCountFuture = CompletableFuture.supplyAsync(() -> {
                        try (QueryResult result = executeQuery(selectToQuery, address)) {
                            ResultSet rs = result.ResultSet();
                            return rs.next() ? rs.getInt("count") : 0;
                        } catch (SQLException e) {
                            logger.error("Failed to get receiver address txns count: {}", e.getLocalizedMessage());
                            return 0;
                        }
                    });

                    Pair<NewTxn, NewTxn> firstLastTxns = getFirstAndLastTransactionssByAddress(address);

                    int totalCount = fromCountFuture.get() + toCountFuture.get();

                    String insertSql = "INSERT INTO \"UsersHistory\"(" +
                            "address, " +
                            "transaction_count, " +
                            "first_txn_timestamp, " +
                            "first_txn_hash, " +
                            "last_txn_timestamp," +
                            "last_txn_hash) VALUES(?,?,?,?,?,?)";
                    executeUpdate(
                            insertSql, formattedAddress.toLowerCase(), totalCount, firstLastTxns.first.timestamp(), firstLastTxns.first.hash(),
                            firstLastTxns.second.timestamp(), firstLastTxns.second.hash()
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            logger.info("Successfully flushed");
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

    private static String getTransactionsTableName(String hash) {
        int shardIndex = Math.abs(hash.hashCode()) % NUMBER_OF_SHARDS;
        return "\"Transactions_Shard_" + shardIndex + "\"";
    }

    public static class Pair<T, U> {
        public final T first;
        public final U second;

        public Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }
    }
    //#endregion


    //#region sql executors
    private record QueryResult(
            Connection connection,
            PreparedStatement statement,
            ResultSet ResultSet
    ) implements AutoCloseable {

        @Override
        public void close() throws SQLException {
            if (ResultSet != null) ResultSet.close();
            if (statement != null) statement.close();
            if (connection != null) connection.close();
        }
    }

    private static QueryResult executeQuery(String sql, Object... params) throws SQLException {
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

    private static void executeUpdate(String sql, Object... params) throws SQLException {
        try (Connection connection = getConnection()) {
            PreparedStatement stmt = connection.prepareStatement(sql);
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            stmt.executeUpdate();
        }
    }
    //#endregion

}