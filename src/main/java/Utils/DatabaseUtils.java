package Utils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import DTOs.BlockDTO;
import DTOs.TransactionDTO;
import org.json.JSONArray;
import org.json.JSONObject;


public class DatabaseUtils {

    private static final String jdbcUrl = "jdbc:mysql://localhost:3306/pwrchain";
    private static final String jdbcUser = "root";
    private static final String jdbcPassword = "#####";
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

        return transactionDTO;
    }


    public static TransactionDTO fetchTransactionFromTransactionHash(String txnHash){
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
            String sql = "SELECT  t.* FROM transaction t where t.transaction_hash = ? " ;


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
            blockJson.put("blockNumebr",blockDTO.getBlockNumber());
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

    // Add other utility methods for database operations as needed
}
