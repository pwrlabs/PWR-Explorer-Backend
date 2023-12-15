package Utils;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import DTOs.BlockDTO;
import DTOs.TransactionDTO;
import org.json.JSONArray;
import org.json.JSONObject;


public class DatabaseUtils {

    private static final String jdbcUrl = "jdbc:mysql://localhost:3306/pwrchain";
    private static final String jdbcUser = "root";
    private static final String jdbcPassword = "hamza123";



    private static long txnCountPast24Hours = 0;
    private static double txnCountPercentageChangeComparedToPreviousDay = 0;

    private static long totalTxnFeesPast24Hours = 0;
    private static double totalTxnFeesPercentageChangeComparedToPreviousDay = 0;

    private static long averageTxnFeePast24Hours = 0;
    private static double averageTxnFeePercentageChangeComparedToPreviousDay = 0;

    public static BlockDTO fetchBlockDetails(long blockNumber) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
            String sql = "SELECT b.*, t.* FROM blocks b " +
                    "LEFT JOIN transactions t ON b.block_number = t.block_id " +
                    "WHERE b.block_number = ?";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, blockNumber);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return createBlockDTOFromResultSet(resultSet);
                    } else {
                        return null;
                    }
                }
            }
        }
    }

    public static List<BlockDTO> fetchLatestBlocks() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
            String sql = "select * from blocks order by timestamp desc limit 5;";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return latestBlocksFromResultSet(resultSet);
                    } else {
                        return null;
                    }
                }
            }
        }
    }

    public static List<TransactionDTO> fetchLatestTransactions() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
            String sql = "select * from transactions order by entryTime desc limit 5";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return fetchLatestTransactionsFromResultSet(resultSet);
                    } else {
                        return null;
                    }
                }
            }
        }
    }
    private static BlockDTO createBlockDTOFromResultSet(ResultSet resultSet) throws SQLException {
        BlockDTO blockDTO = new BlockDTO();
        blockDTO.setTransactionCount(resultSet.getInt("transaction_count"));
        blockDTO.setBlockSize(resultSet.getInt("block_size"));
        blockDTO.setBlockNumber(resultSet.getLong("block_number"));
        blockDTO.setBlockReward(resultSet.getLong("block_reward"));
        blockDTO.setTimestamp(resultSet.getLong("timestamp"));
        blockDTO.setBlockHash(resultSet.getString("block_hash"));
        blockDTO.setSubmitter(resultSet.getString("submitter"));
        blockDTO.setSuccess(resultSet.getBoolean("success"));


            List<TransactionDTO> transactions = new ArrayList<>();
            do {
                TransactionDTO transactionDTO = createTransactionDTOFromResultSet(resultSet);
                transactions.add(transactionDTO);
            } while (resultSet.next());

            blockDTO.setTransactions(transactions);


        return blockDTO;
    }

    private static List<BlockDTO> latestBlocksFromResultSet(ResultSet resultSet) throws SQLException {
        List<BlockDTO> latestBlockList =new ArrayList<>();
        while (resultSet.next()){
            BlockDTO blockDTO = new BlockDTO();
            blockDTO.setTransactionCount(resultSet.getInt("transaction_count"));
            blockDTO.setBlockSize(resultSet.getInt("block_size"));
            blockDTO.setBlockNumber(resultSet.getLong("block_number"));
            blockDTO.setBlockReward(resultSet.getLong("block_reward"));
            blockDTO.setTimestamp(resultSet.getLong("timestamp"));
            blockDTO.setBlockHash(resultSet.getString("block_hash"));
            blockDTO.setSubmitter(resultSet.getString("submitter"));
            blockDTO.setSuccess(resultSet.getBoolean("success"));
            latestBlockList.add(blockDTO);
        }


        return latestBlockList;
    }

    private static List<TransactionDTO> fetchLatestTransactionsFromResultSet(ResultSet resultSet) throws SQLException {
        List<TransactionDTO> latestBlockList =new ArrayList<>();
        while (resultSet.next()){
            TransactionDTO transactionDTO = createTransactionDTOFromResultSet(resultSet);
            if(transactionDTO.getSize() > 0)
             latestBlockList.add(transactionDTO);
        }
        return latestBlockList;
    }

    private static TransactionDTO createTransactionDTOFromResultSet(ResultSet resultSet) throws SQLException {
        TransactionDTO transactionDTO = new TransactionDTO();
        transactionDTO.setPositionInTheBlock(resultSet.getInt("position_in_block"));
        transactionDTO.setNonceOrValidationHash(resultSet.getString("nonce_or_validation_hash"));
        transactionDTO.setSize(resultSet.getInt("size"));
        transactionDTO.setRawTxn(resultSet.getString("raw_txn"));
        transactionDTO.setTxnFee(resultSet.getLong("txn_fee"));
        transactionDTO.setFrom(resultSet.getString("from_address"));
        transactionDTO.setTo(resultSet.getString("to_address"));
        transactionDTO.setType(resultSet.getString("type"));
        transactionDTO.setHash(resultSet.getString("transaction_hash"));
        transactionDTO.setValue(resultSet.getLong("value"));
        transactionDTO.setAmountUsdValue((resultSet.getLong("amount_usd_value")));
        transactionDTO.setFeeUsdValue((resultSet.getLong("fee_usd_value")));
        transactionDTO.setBlockNumber((resultSet.getLong("block_id")));
        transactionDTO.setTimeStamp((resultSet.getTimestamp("entryTime")));
        return transactionDTO;
    }


    public static TransactionDTO fetchTransactionFromTransactionHash(String txnHash){
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
            String sql = "SELECT  t.* FROM transactions t where t.transaction_hash = ? " ;


            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, txnHash);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        TransactionDTO transactionDTO = new TransactionDTO();
                        transactionDTO.setPositionInTheBlock(resultSet.getInt("position_in_block"));
                        transactionDTO.setNonceOrValidationHash(resultSet.getString("nonce_or_validation_hash"));
                        transactionDTO.setSize(resultSet.getInt("size"));
                        transactionDTO.setRawTxn(resultSet.getString("raw_txn"));
                        transactionDTO.setTxnFee(resultSet.getLong("txn_fee"));
                        transactionDTO.setFrom(resultSet.getString("from_address"));
                        transactionDTO.setTo(resultSet.getString("to_address"));
                        transactionDTO.setType(resultSet.getString("type"));
                        transactionDTO.setHash(resultSet.getString("transaction_hash"));
                        transactionDTO.setValue(resultSet.getLong("value"));
                        transactionDTO.setAmountUsdValue((resultSet.getLong("amount_usd_value")));
                        transactionDTO.setFeeUsdValue((resultSet.getLong("fee_usd_value")));
                        transactionDTO.setBlockNumber((resultSet.getLong("block_id")));
                        transactionDTO.setTimeStamp((resultSet.getTimestamp("entryTime")));
                        return transactionDTO;
                    } else {
                        return null;
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static JSONObject convertBlockDTOToJson(BlockDTO blockDTO) {
            JSONObject blockJson = new JSONObject();
            blockJson.put("transactionCount", blockDTO.getTransactionCount());
            blockJson.put("blockHash", blockDTO.getBlockHash());
            blockJson.put("blockReward",blockDTO.getBlockReward());
            blockJson.put("blockSubmitter",blockDTO.getSubmitter());
            blockJson.put("blockSize",blockDTO.getBlockSize());
            blockJson.put("timestamp",blockDTO.getTimestamp());
            blockJson.put("success",blockDTO.isSuccess());
            blockJson.put("blockNumber",blockDTO.getBlockNumber());
            // Add other fields as needed

            // Convert TransactionDTOs to JsonArray
            JSONArray transactionsJson = new JSONArray();
            if(blockDTO.getTransactions() != null) {
                for (TransactionDTO transactionDTO : blockDTO.getTransactions()) {
                    transactionsJson.put(convertTransactionDTOToJson(transactionDTO));
                }
                blockJson.put("transactions", transactionsJson);
            }

            return blockJson;
        }

        public static JSONObject convertTransactionDTOToJson(TransactionDTO transactionDTO) {
            JSONObject transactionJson = new JSONObject();
            transactionJson.put("positionInTheBlock", transactionDTO.getPositionInTheBlock());
            transactionJson.put("nonceOrValidationHash", transactionDTO.getNonceOrValidationHash());
            transactionJson.put("size", transactionDTO.getSize());
            transactionJson.put("rawTxn", transactionDTO.getRawTxn());
            transactionJson.put("fee", transactionDTO.getTxnFee());
            transactionJson.put("from", transactionDTO.getFrom());
            transactionJson.put("to", transactionDTO.getTo());
            transactionJson.put("txnFee", transactionDTO.getTxnFee());
            transactionJson.put("type", transactionDTO.getType());
            transactionJson.put("value", transactionDTO.getValue());
            transactionJson.put("hash", transactionDTO.getHash());
            transactionJson.put("amountUsdValue", transactionDTO.getAmountUsdValue());
            transactionJson.put("feeUsdValue", transactionDTO.getFeeUsdValue());
            transactionJson.put("blockNumber", transactionDTO.getBlockNumber());
            return transactionJson;
        }

    public static int getTotalPagesWrtBlocks(int count) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
            String sql = "SELECT COUNT(*) FROM blocks";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        int totalBlocks = resultSet.getInt(1);
                        return (int) Math.ceil((double) totalBlocks / count);
                    } else {
                        return 0;
                    }
                }
            }
        }
    }

    public static List<BlockDTO> fetchLatestBlocks(int count, int page) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
            int offset = (page - 1) * count;
            String sql = "SELECT * FROM blocks ORDER BY timestamp DESC LIMIT ? OFFSET ?";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, count);
                statement.setInt(2, offset);

                try (ResultSet resultSet = statement.executeQuery()) {
                    List<BlockDTO> latestBlockList = new ArrayList<>();
                    while (resultSet.next()) {
                        BlockDTO blockDTO = createBlockDTOFromResultSet(resultSet);
                        latestBlockList.add(blockDTO);
                    }
                    return latestBlockList;
                }
            }
        }
    }

    public static long getLatestBlockNumber() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
            String sql = "SELECT MAX(block_number) FROM blocks";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getLong(1);
                    } else {
                        return 0;
                    }
                }
            }
        }
    }

    public static void updateTxn24HourStats() throws SQLException {
        long txnCountPast24Hours = 0;
        long totalTxnFeesPast24Hours = 0;
        long averageTxnFeePast24Hours = 0;

        long txnCountThe24HoursBefore = 0;
        long totalTxnFeesThe24HoursBefore = 0;
        long averageTxnFeeThe24HoursBefore = 0;

        long timeNow = Instant.now().getEpochSecond();
        long blockNumberToCheck = getLatestBlockNumber();
        while(true) {
            BlockDTO block = fetchBlockDetails(blockNumberToCheck--);
            if(block.getTimestamp() < timeNow - 24 * 60 * 60) break;

            txnCountPast24Hours += block.getTransactionCount();
            for(TransactionDTO txn : block.getTransactions()) {
                try { totalTxnFeesPast24Hours += txn.getTxnFee(); } catch (Exception e) {
                    // Handle exception
                }
            }
        }
        if(txnCountPast24Hours == 0) averageTxnFeePast24Hours = 0;
        else averageTxnFeePast24Hours = totalTxnFeesPast24Hours / txnCountPast24Hours;

        // Calculate the stats of the 24 hours before the current 24 hours
        while(true) {
            BlockDTO block = fetchBlockDetails(blockNumberToCheck--);
            if(block == null) break;
            if(block.getTimestamp() < timeNow - 24 * 60 * 60 * 2) break;

            txnCountThe24HoursBefore += block.getTransactionCount();
            for(TransactionDTO txn : block.getTransactions()) {
                try { totalTxnFeesThe24HoursBefore += txn.getTxnFee(); } catch (Exception e) {
                    // Handle exception
                }
            }
        }
        if(txnCountThe24HoursBefore == 0) averageTxnFeeThe24HoursBefore = 0;
        else averageTxnFeeThe24HoursBefore = totalTxnFeesThe24HoursBefore / txnCountThe24HoursBefore;

        // Calculate the percentage change
        if(txnCountThe24HoursBefore == 0) {
            txnCountPercentageChangeComparedToPreviousDay = 0;
        } else {
            txnCountPercentageChangeComparedToPreviousDay = BigDecimal.valueOf(
                    (txnCountPast24Hours - txnCountThe24HoursBefore) / (double) txnCountThe24HoursBefore * 100
            ).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
        }

        if(totalTxnFeesThe24HoursBefore == 0) {
            totalTxnFeesPercentageChangeComparedToPreviousDay = 0;
        } else {
            totalTxnFeesPercentageChangeComparedToPreviousDay = BigDecimal.valueOf(
                    (totalTxnFeesPast24Hours - totalTxnFeesThe24HoursBefore) / (double) totalTxnFeesThe24HoursBefore * 100
            ).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
        }

        if(averageTxnFeeThe24HoursBefore == 0) {
            averageTxnFeePercentageChangeComparedToPreviousDay = 0;
        } else {
            averageTxnFeePercentageChangeComparedToPreviousDay = BigDecimal.valueOf(
                    (averageTxnFeePast24Hours - averageTxnFeeThe24HoursBefore) / (double) averageTxnFeeThe24HoursBefore * 100
            ).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
        }

        // Update the static variables
//        txnCountPast24Hours = txnCountPast24Hours;
//        totalTxnFeesPast24Hours = totalTxnFeesPast24Hours;
//        averageTxnFeePast24Hours = averageTxnFeePast24Hours;
    }

    public static List<TransactionDTO> fetchLatestTransactionsOfUser(String address) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
            String sql = "select * from transactions where from_address = ? order by entryTime desc ;";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, address);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return fetchLatestTransactionsFromResultSet(resultSet);
                    } else {
                        return null;
                    }
                }
            }
        }
    }


    public static long getTxnCountPast24Hours() {
        return txnCountPast24Hours;
    }

    public static void setTxnCountPast24Hours(long txnCountPast24Hours) {
        DatabaseUtils.txnCountPast24Hours = txnCountPast24Hours;
    }

    public static double getTxnCountPercentageChangeComparedToPreviousDay() {
        return txnCountPercentageChangeComparedToPreviousDay;
    }

    public static void setTxnCountPercentageChangeComparedToPreviousDay(double txnCountPercentageChangeComparedToPreviousDay) {
        DatabaseUtils.txnCountPercentageChangeComparedToPreviousDay = txnCountPercentageChangeComparedToPreviousDay;
    }

    public static long getTotalTxnFeesPast24Hours() {
        return totalTxnFeesPast24Hours;
    }

    public static void setTotalTxnFeesPast24Hours(long totalTxnFeesPast24Hours) {
        DatabaseUtils.totalTxnFeesPast24Hours = totalTxnFeesPast24Hours;
    }

    public static double getTotalTxnFeesPercentageChangeComparedToPreviousDay() {
        return totalTxnFeesPercentageChangeComparedToPreviousDay;
    }

    public static void setTotalTxnFeesPercentageChangeComparedToPreviousDay(double totalTxnFeesPercentageChangeComparedToPreviousDay) {
        DatabaseUtils.totalTxnFeesPercentageChangeComparedToPreviousDay = totalTxnFeesPercentageChangeComparedToPreviousDay;
    }

    public static long getAverageTxnFeePast24Hours() {
        return averageTxnFeePast24Hours;
    }

    public static void setAverageTxnFeePast24Hours(long averageTxnFeePast24Hours) {
        DatabaseUtils.averageTxnFeePast24Hours = averageTxnFeePast24Hours;
    }

    public static double getAverageTxnFeePercentageChangeComparedToPreviousDay() {
        return averageTxnFeePercentageChangeComparedToPreviousDay;
    }

    public static void setAverageTxnFeePercentageChangeComparedToPreviousDay(double averageTxnFeePercentageChangeComparedToPreviousDay) {
        DatabaseUtils.averageTxnFeePercentageChangeComparedToPreviousDay = averageTxnFeePercentageChangeComparedToPreviousDay;
    }

//    public static List<TransactionDTO> getAllJoinTransaction(String address){
//
//    }

    // Add other utility methods for database operations as needed
}
