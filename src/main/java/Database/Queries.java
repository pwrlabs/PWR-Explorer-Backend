package Database;

import DataModel.Block;
import Utils.Settings;
import DataModel.NewTxn;
import com.github.pwrlabs.pwrj.entities.FalconTransaction;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static Database.Constants.Constants.*;
import static Database.DatabaseConnection.getConnection;

public class Queries {
    private static final Logger logger = LoggerFactory.getLogger(Queries.class);
    private static final int NUMBER_OF_SHARDS = 1;

    public static void batchInsertTxns(List<FalconTransaction> txns) {
        String tableName = getTransactionsTableName("0");  // or dynamic if needed
        String sql = "INSERT INTO " + tableName + " (" + HASH + ", " + BLOCK_NUMBER + ", " + POSITION_IN_BLOCK + ", " + FROM_ADDRESS + ", " + TO_ADDRESS + ", " + TIMESTAMP + ", " + VALUE + ", " + TXN_TYPE + ", " + TXN_FEE + ", " + SUCCESS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            logger.info("Retrieved connection and started inserting txns");

            conn.setAutoCommit(false);

            for (FalconTransaction txn : txns) {
                long value = 0;
                if (txn instanceof FalconTransaction.FalconTransfer t) {
                    value = t.getAmount();
                } else if (txn instanceof FalconTransaction.PayableVidaDataTxn t) {
                    value = t.getValue();
                } else if (txn instanceof FalconTransaction.TransferPWRFromVidaTxn t) {
                    value = t.getAmount();
                } else if (txn instanceof FalconTransaction.FalconDelegate t) {
                    value = t.getPwrAmount();
                }

                String from = txn.getSender().startsWith("0x") ? txn.getSender().substring(2) : txn.getSender();
                String to = txn.getReceiver() != null ? txn.getReceiver() : "";

                pstmt.setString(1, txn.getTransactionHash().toLowerCase());
                pstmt.setLong(2, txn.getBlockNumber());
                pstmt.setInt(3, txn.getPositionInBlock());
                pstmt.setString(4, from.toLowerCase());
                pstmt.setString(5, to.toLowerCase());
                pstmt.setLong(6, txn.getTimestamp());
                pstmt.setLong(7, value);
                pstmt.setString(8, txn.getType());
                pstmt.setLong(9, txn.getPaidTotalFee());
                pstmt.setBoolean(10, txn.isSuccess());

                pstmt.addBatch();
            }

            pstmt.executeBatch();
            conn.commit();

            logger.info("Successfully inserted txns");

        } catch (Exception e) {
            throw new RuntimeException("Batch insert transactions failed: " + e.getMessage(), e);
        }

        String updateSql = "UPDATE \"TxnsCount\" SET " + TXNS_COUNT + " = " + TXNS_COUNT + " + ? WHERE " + ID + " = 1";
        try {
            executeUpdate(updateSql, txns.size());
            logger.info("Successfully updated txns count");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void insertValidator(String address, long joiningTime) {
        address = address.toLowerCase();
        if (address.startsWith("0x")) {
            address = address.substring(2);
        }
        String sql = "INSERT INTO \"Validator\" (" + ADDRESS + ", " + JOINING_TIME + ", " + LIFETIME_REWARDS + ", " + SUBMITTED_BLOCKS_COUNT + ", " + LATEST_BLOCK_NUMBER + ") VALUES (?,?,0,0,0)";

        try {
            executeUpdate(sql, address, joiningTime);
        } catch (Exception e) {
            logger.error("Failed to insert Validator: ", e);
        }
    }

    public static void upsertUserHistory(String address, String txnHash, long txnTimestamp, int incrementCount) {
        String sql = "INSERT INTO \"UsersHistory\" (" + ADDRESS + ", " + TRANSACTIONS_COUNT + ", " + FIRST_TXN_TIMESTAMP + ", " + FIRST_TXN_HASH + ", " + LAST_TXN_TIMESTAMP + ", " + LAST_TXN_HASH + ") VALUES (?, ?, ?, ?, ?, ?) " + "ON CONFLICT (" + ADDRESS + ") DO UPDATE SET " + TRANSACTIONS_COUNT + " = \"UsersHistory\"." + TRANSACTIONS_COUNT + " + ?, " + LAST_TXN_TIMESTAMP + " = ?, " + LAST_TXN_HASH + " = ?";

        try {
            executeUpdate(sql, address, incrementCount, txnTimestamp, txnHash, txnTimestamp, txnHash, incrementCount, txnTimestamp, txnHash);
        } catch (SQLException e) {
            logger.error("Error upserting user history for address {}: {}", address, e.getLocalizedMessage());
            throw new RuntimeException("Failed to upsert user history", e);
        }
    }

    public static double getAverageTps(int numberOfBlocks, long lastBlockNumber) {
        String sql = "SELECT " + TRANSACTIONS_COUNT + ", " + TIMESTAMP + " FROM \"Block\" " + "WHERE " + BLOCK_NUMBER + " > ? " + "ORDER BY " + BLOCK_NUMBER + " DESC " + "LIMIT ?";

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
            return BigDecimal.valueOf(totalTxns).divide(BigDecimal.valueOf(timeSpanSeconds), 1, BigDecimal.ROUND_HALF_UP).doubleValue();
        } catch (Exception e) {
            logger.error("Failed to calculate TPS: {}", e.getLocalizedMessage());
            return 0;
        }
    }

    public static NewTxn getDbTxn(String hash) {
        NewTxn txn = null;
        String tableName = getTransactionsTableName(hash);

        if (hash != null && hash.startsWith("0x")) {
            hash = hash.substring(2);
        }

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

    public static List<NewTxn> getTransactions(int pageSize, int page) {
        List<NewTxn> txns = new ArrayList<>();
        String tableName = getTransactionsTableName("0");

        pageSize = Math.min(Math.max(1, pageSize), 1000);
        page = Math.max(1, page);

        if (pageSize * page > 100_000) {
            page = (int) Math.ceil((double) 100_000 / pageSize);
        }

        int offset = Math.max(0, (page - 1) * pageSize);

        String sql = "SELECT * " + "FROM " + tableName + " ORDER BY block_number DESC, position_in_block ASC " + "LIMIT ? OFFSET ?;";

        try (QueryResult result = executeQuery(sql, pageSize, offset)) {
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
        String sql = "SELECT * FROM " + tableName + " ORDER BY " + TIMESTAMP + " DESC " + " LIMIT ?;";

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
        if (address.startsWith("0x")) {
            address = address.substring(2);
        }
        address = address.toLowerCase();

        List<NewTxn> txns = new ArrayList<>();

        if (pageSize * page > 100_000) {
            page = (int) Math.ceil((double) 100_000 / pageSize);
        }

        String tableName = getTransactionsTableName("0");

        String sql = "SELECT * FROM " + tableName + " WHERE " + FROM_ADDRESS + " = ? OR " + TO_ADDRESS + " = ? " + "ORDER BY " + TIMESTAMP + " DESC " + "LIMIT ? OFFSET ?;";

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

        String sql = "SELECT " + "CASE WHEN COUNT(*) FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) = 0 THEN 0 " + "ELSE ((AVG(" + TXN_FEE + ") FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) / " + "AVG(" + TXN_FEE + ") FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '48 hours')) * 1000 AND " + TIMESTAMP + " < EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) - 1) * 100) " + "END AS percentage_change " + "FROM " + tableName + ";";

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

        String sql = "SELECT " + "CASE WHEN COUNT(*) FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) = 0 THEN 0 " + "ELSE ((SUM(" + TXN_FEE + ") FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) / " + "SUM(" + TXN_FEE + ") FILTER (WHERE \"" + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '48 hours')) * 1000 AND \"" + TIMESTAMP + "\" < EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) - 1) * 100) " + "END AS percentage_change " + "FROM " + tableName + ";";

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

        String sql = "SELECT AVG(" + TXN_FEE + ") AS average_fee FROM " + tableName + " WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000;";

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

        String sql = "SELECT SUM(" + TXN_FEE + ") AS total_fees FROM " + tableName + " WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000;";

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

        String sql = "SELECT COUNT(*) AS transaction_count FROM " + tableName + " WHERE \"" + TIMESTAMP + "\" >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000;";

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

        String sql = "SELECT CASE " + "WHEN COUNT(*) FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '48 hours')) * 1000 AND " + TIMESTAMP + " < EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) = 0 THEN 0 " + "ELSE (COUNT(*) FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) * 1.0 / " + "COUNT(*) FILTER (WHERE " + TIMESTAMP + " >= EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '48 hours')) * 1000 AND " + TIMESTAMP + " < EXTRACT(EPOCH FROM (NOW() AT TIME ZONE 'UTC' - INTERVAL '24 hours')) * 1000) - 1) * 100 " + "END AS percentage_change " + "FROM " + tableName + ";";

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

    public static BigInteger getTotalFees() {
        String tableName = getTransactionsTableName("0");
        // First get the min and max day timestamps from the table
        String boundsQuery = "SELECT " + "MIN(timestamp / 86400000) as min_day, " + "MAX(timestamp / 86400000) as max_day " + "FROM " + tableName;

        try (Connection conn = DatabaseConnection.getConnection()) {
            long startDay, endDay;

            // Get the full range of days
            try (PreparedStatement stmt = conn.prepareStatement(boundsQuery); ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return BigInteger.ZERO;
                startDay = rs.getLong("min_day");
                endDay = rs.getLong("max_day");
            }

            // Define partition size (adjust based on your data distribution)
            int PARTITION_SIZE = 30;

            // Calculate number of partitions needed
            int numPartitions = (int) Math.ceil((endDay - startDay + 1) / (double) PARTITION_SIZE);

            // Sum across all partitions
            return (BigInteger) IntStream.range(0, numPartitions).parallel().mapToObj(partition -> {
                long partitionStart = startDay + (partition * PARTITION_SIZE);
                long partitionEnd = Math.min(partitionStart + PARTITION_SIZE, endDay);

                String sql = "SELECT COALESCE(SUM(txn_fee), 0) " + "FROM " + tableName + " " + "WHERE timestamp / 86400000 BETWEEN ? AND ?";

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
            }).reduce(BigInteger.ZERO, (a, b) -> ((BigInteger) a).add((BigInteger) b)); // Added explicit casting

        } catch (SQLException e) {
            logger.error("Error getting timestamp bounds for shard: {}", e.getLocalizedMessage());
            return BigInteger.ZERO;
        }
    }

    public static long getTotalTransactionCount() {
        long totalCount = 0;
        String sql = "SELECT " + TXNS_COUNT + " AS total_count FROM \"TxnsCount\"";

        try (QueryResult result = executeQuery(sql)) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                totalCount = rs.getLong("total_count");
            }
        } catch (Exception e) {
            logger.error("Failed to get txns count: {}", e.getLocalizedMessage());
        }

        return totalCount;
    }

    public static Pair<NewTxn, NewTxn> getFirstAndLastTransactionsByAddress(String address) {
        if (address.startsWith("0x")) {
            address = address.substring(2);
        }
        address = address.toLowerCase();
        NewTxn firstTxn = null;
        NewTxn lastTxn = null;

        String sql = "SELECT * FROM \"UsersHistory\" WHERE address = ?";

        try (QueryResult result = executeQuery(sql, address)) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                // Populate first transaction
                firstTxn = new NewTxn(rs.getString(FIRST_TXN_HASH), 0, 0, "", "", rs.getLong(FIRST_TXN_TIMESTAMP), 0, "", 0, false);

                // Populate last transaction
                lastTxn = new NewTxn(rs.getString(LAST_TXN_HASH), 0, 0, "", "", rs.getLong(LAST_TXN_TIMESTAMP), 0, "", 0, false);
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

        String sql = "SELECT (" + TIMESTAMP + " / " + MILLIS_PER_DAY + ") * " + MILLIS_PER_DAY + " as day_start, " + "COUNT(*) as count " + "FROM " + tableName + " " + "WHERE " + TIMESTAMP + " >= ? AND " + TIMESTAMP + " < ? " + "GROUP BY (" + TIMESTAMP + " / " + MILLIS_PER_DAY + ") * " + MILLIS_PER_DAY + " " + "ORDER BY day_start DESC";

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

    public static boolean isNewUser(String address) {
        address = address.toLowerCase();
        if (address.startsWith("0x")) {
            address = address.substring(2);
        }
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

    /* TODO: For future devs working on the explorer if db is being reset consider adding a new table
        which will hold the total txns count and on each block maybe or a specific interval we update the count since now
        to get the txns count we are counting them from the txns table which is not scalable but for now we are caching them
        which works fine and smoothly.
   */
    public static int getTotalTxnCountOld(String address) {
        address = address.toLowerCase();
        if (address.startsWith("0x")) {
            address = address.substring(2);
        }
        int totalCount = 0;

        String tableName = getTransactionsTableName("0");

        String sql = "SELECT COUNT(*) AS total_count FROM " + tableName + " WHERE " + FROM_ADDRESS + " = ? OR " + TO_ADDRESS + " = ?;";

        try (Connection conn = getConnection(); PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);
            preparedStatement.setString(2, address);

            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    totalCount = rs.getInt("total_count");
                }
            }
        } catch (Exception e) {
            logger.error(e.toString());
        }

        return totalCount;
    }

    public static NewTxn populateNewTxnObject(ResultSet rs) throws SQLException {
        return new NewTxn(rs.getString(HASH), rs.getLong(BLOCK_NUMBER), rs.getInt(POSITION_IN_BLOCK), rs.getString(FROM_ADDRESS), rs.getString(TO_ADDRESS), rs.getLong(TIMESTAMP), rs.getLong(VALUE), rs.getString(TXN_TYPE), rs.getLong(TXN_FEE), rs.getBoolean(SUCCESS));
    }

    public static String getTransactionsTableName(String hash) {
        int shardIndex = Math.abs(hash.hashCode()) % NUMBER_OF_SHARDS;
        return "\"Transactions_Shard_" + shardIndex + "\"";
    }

    public static void initializeValidators(com.github.pwrlabs.pwrj.entities.Block block) {
        String sql = "INSERT INTO \"Validator\" VALUES(?, ?, ?, ?, ?)";
        Object[] defaultValues = {block.getTimestamp(), 0, 0, 0};

        try {
            executeUpdate(sql, "f5fe6ae4ba7aa68c1ab340652d243b899859075b", block.getTimestamp(), 0, 0, 0);
            executeUpdate(sql, "8796f287962c5de43b564f62d67314b7980738fc", block.getTimestamp(), 0, 0, 0);
        } catch (Exception e) {
            logger.error("An error occurred while initializing validators: ", e);
        }
    }

    public static long getLifetimeRewards(String address) {
        address = address.toLowerCase();
        if (address.startsWith("0x")) {
            address = address.substring(2);
        }
        String sql = "SELECT " + LIFETIME_REWARDS + " FROM \"Validator\" WHERE " + ADDRESS + " = ?";

        try (QueryResult result = executeQuery(sql, address)) {
            ResultSet rs = result.ResultSet();
            if (rs.next()) {
                return rs.getLong(LIFETIME_REWARDS);
            }
        } catch (Exception e) {
            logger.error("Failed to get lifetime rewards for validator {}: {}", address, e.getLocalizedMessage());
        }
        return 0;
    }


    ///* Utils *///
    public record Pair<T, U>(T first, U second) {
    }

    private record QueryResult(Connection connection, PreparedStatement statement,
                               ResultSet ResultSet) implements AutoCloseable {

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
}