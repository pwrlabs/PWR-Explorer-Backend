package Database.Constants.Repository.Blocks;

import DataModel.Block;
import DataModel.NewTxn;
import DataModel.QueryResult;
import Database.Constants.Repository.SqlExecutors.JdbcExecutors;
import Database.Constants.Repository.SqlExecutors.SqlExecutors;
import Database.Queries;
import Utils.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static Database.Constants.Constants.*;
import static Database.Constants.Constants.SUCCESS;
import static Database.DatabaseConnection.getConnection;
import static Database.Queries.getTransactionsTableName;
import static Database.Queries.populateNewTxnObject;

public class BlocksRepoImpl implements BlocksRepo {
    private static final Logger logger = LoggerFactory.getLogger(BlocksRepoImpl.class);
    private static final SqlExecutors sqlExecutors = new JdbcExecutors();

    @Override
    public void insertBlock(com.github.pwrlabs.pwrj.entities.Block block) {
        String sql = "INSERT INTO \"Block\" (" + BLOCK_NUMBER + ", " + BLOCK_HASH + ", " + FEE_RECIPIENT + ", " + TIMESTAMP + ", " + TRANSACTIONS_COUNT + ", " + BLOCK_REWARD + ", " + BLOCK_SIZE + ", " + SUCCESS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            logger.info("Retrieved connection and started inserting block {}", block.getBlockNumber());
            sqlExecutors.executeUpdate(sql, block.getBlockNumber(), block.getBlockHash().toLowerCase(), block.getProposer().toLowerCase(), block.getTimestamp(), block.getTransactionCount(), block.getBlockReward(), block.getBlockSize(), block.isProcessedWithoutCriticalErrors());
            logger.info("Successfully inserted block {}", block.getBlockNumber());
        } catch (Exception e) {
            logger.error("Failed to insert block {}: ", block.getBlockNumber(), e);
        }
    }

    @Override
    public Block getDbBlock(long blockNumber) {
        String sql = "SELECT * FROM \"Block\" WHERE " + BLOCK_NUMBER + " = ?;";
        Block block = null;

        try (QueryResult result = sqlExecutors.executeQuery(sql, blockNumber)) {
            ResultSet rs = result.resultSet();
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

    @Override
    public long getLastStoredBlock() {
        String sql = "SELECT " + BLOCK_NUMBER + " FROM \"LastBlock\";";

        try (QueryResult result = sqlExecutors.executeQuery(sql)) {
            ResultSet rs = result.resultSet();
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            logger.error("Failed to get last stored block number: {}", e.getLocalizedMessage());
        }
        return 0;
    }

    @Override
    public void updateLastStoredBlock(long blockNumber) {
        String sql = "UPDATE \"LastBlock\" SET " + BLOCK_NUMBER + " = ?";

        try {
            sqlExecutors.executeUpdate(sql, blockNumber);
        } catch (Exception e) {
            logger.error("Failed to update last stored block number: {}", e.getLocalizedMessage());
        }
    }

    @Override
    public long getLatestBlockNumberForFeeRecipient(String feeRecipient) {
        String sql = "SELECT " + TIMESTAMP + " FROM \"Block\" " + "WHERE LOWER(" + FEE_RECIPIENT + ") = ? " + "ORDER BY " + BLOCK_NUMBER + " DESC " + "LIMIT 1";

        try (QueryResult result = sqlExecutors.executeQuery(sql, feeRecipient)) {
            ResultSet rs = result.resultSet();
            if (rs.next()) {
                return rs.getLong(TIMESTAMP);
            }
        } catch (SQLException e) {
            logger.error("Error getting latest block number for fee recipient {}: {}", feeRecipient, e.getMessage());
        }
        return -1;
    }

    @Override
    public String getBlockHash(long blockNumber) {
        String blockHash = null;
        String sql = "SELECT " + BLOCK_HASH + " FROM \"Block\" WHERE " + BLOCK_NUMBER + " = ?;";

        try (QueryResult result = sqlExecutors.executeQuery(sql, blockNumber)) {
            ResultSet rs = result.resultSet();
            if (rs.next()) {
                blockHash = rs.getString(BLOCK_HASH);
            }
        } catch (Exception e) {
            logger.error("Failed to get hash of block {}: {}", blockNumber, e.getLocalizedMessage());
        }

        return blockHash;
    }

    @Override
    public  List<Block> getLastXBlocks(int x) {
        List<Block> blocks = new ArrayList<>();
        String sql = "SELECT * FROM \"Block\" " + "ORDER BY " + BLOCK_NUMBER + " DESC " + "LIMIT ?;";

        try (QueryResult result = sqlExecutors.executeQuery(sql, x)) {
            ResultSet rs = result.resultSet();
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

    @Override
    public  List<Block> getLastXBlocks(int pageSize, int page) {
        List<Block> blocks = new ArrayList<>();
        String sql = "SELECT * FROM \"Block\" " + "ORDER BY " + BLOCK_NUMBER + " DESC " + "LIMIT ? OFFSET ?";

        if (pageSize * page > 100_000) {
            page = (int) Math.ceil((double) 100_000 / pageSize);
        }

        try (QueryResult result = sqlExecutors.executeQuery(sql, pageSize, page)) {
            ResultSet rs = result.resultSet();
            while (rs.next()) {
                long blockNumber = rs.getLong(BLOCK_NUMBER);
                String blockHash = rs.getString(BLOCK_HASH);
                String feeRecipient = rs.getString(FEE_RECIPIENT);
                long timestamp = rs.getLong(TIMESTAMP);
                int transactionsCount = rs.getInt(TRANSACTIONS_COUNT);
                long blockReward = rs.getLong(BLOCK_REWARD);
                int size = rs.getInt(BLOCK_SIZE);

                Block block = new Block(blockHash, String.valueOf(blockNumber), timestamp, feeRecipient, blockReward, size, transactionsCount);
                blocks.add(block);
            }

        } catch (Exception e) {
            logger.error("Error retrieving blocks: {}", e.getLocalizedMessage());
        }
        return blocks;
    }

    @Override
    public  List<NewTxn> getBlockTxns(String blockNumberString) {
        long blockNumber = Long.parseLong(blockNumberString);
        List<NewTxn> txns = new ArrayList<>();
        String tableName = getTransactionsTableName("0");
        String sql = "SELECT * FROM " + tableName + " WHERE " + BLOCK_NUMBER + " = ? " + "ORDER BY " + POSITION_IN_BLOCK + " ASC;";

        try (QueryResult result = sqlExecutors.executeQuery(sql, blockNumber)) {
            ResultSet rs = result.resultSet();
            while (rs.next()) {
                NewTxn txn = populateNewTxnObject(rs);
                txns.add(txn);
            }
        } catch (Exception e) {
            logger.error("Failed to get block {} txns: {}", blockNumber, e.getLocalizedMessage());
        }
        return txns;
    }

    @Override
    public  JSONObject get24HourBlockStats() {
        final long MILLIS_PER_DAY = 86400000; // 24 * 60 * 60 * 1000
        long currentTimeMillis = System.currentTimeMillis();

        String sql = "SELECT COALESCE(SUM(" + SIZE + "), 0) as total_size, " + "COALESCE(SUM(" + BLOCK_REWARD + "), 0) as total_rewards, " + "COUNT(*) as block_count, " + "COALESCE(AVG(" + SIZE + "), 0) as avg_size, " + "COALESCE(SUM(" + TRANSACTIONS_COUNT + "), 0) as total_txns " + "FROM \"Block\" WHERE " + TIMESTAMP + " >= ? AND " + TIMESTAMP + " < ?";

        try (QueryResult result = sqlExecutors.executeQuery(sql, currentTimeMillis - MILLIS_PER_DAY, currentTimeMillis)) {
            ResultSet rs = result.resultSet();
            if (rs.next()) {
                int blockCount = rs.getInt("block_count");
                int avgBlockSize = (int) rs.getDouble("avg_size");
                long totalRewards = rs.getLong("total_rewards");
                long totalTxns = rs.getLong("total_txns");
                double networkUtilization = blockCount > 0 ? BigDecimal.valueOf(((double) avgBlockSize / (double) Settings.getBlockSizeLimit()) * 100).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue() : 0.0;

                return new JSONObject().put("blocksCount", blockCount).put("averageBlockSize", avgBlockSize).put("totalRewards", totalRewards).put("totalTransactions", totalTxns).put("networkUtilization", networkUtilization);
            }
        } catch (SQLException e) {
            logger.error("Error getting 24h block stats: {}", e.getLocalizedMessage());
        }

        return new JSONObject().put("blocksCount", 0).put("averageBlockSize", 0).put("totalRewards", 0).put("totalTransactions", 0).put("networkUtilization", 0.0);
    }

    @Override
    public JSONArray getBlocksCreated(String address, int pageSize, int page) {
        address = address.toLowerCase();
        if (address.startsWith("0x")) {
            address = address.substring(2);
        }
        String sql = "SELECT " + BLOCK_NUMBER + ", " + TIMESTAMP + ", " + SUCCESS + ", " + BLOCK_REWARD + ", " + TRANSACTIONS_COUNT + " " + "FROM \"Block\" WHERE LOWER(" + FEE_RECIPIENT + ") = ? " + "ORDER BY " + TIMESTAMP + " DESC " + "LIMIT ? OFFSET ?";

        if (pageSize * page > 100_000) {
            page = (int) Math.ceil((double) 100_000 / pageSize);
        }

        JSONArray blocks = new JSONArray();
        try (QueryResult result = sqlExecutors.executeQuery(sql, address, pageSize, page)) {
            ResultSet rs = result.resultSet();
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

    @Override
    public int getBlocksSubmitted(String address) {
        address = address.toLowerCase();
        if (address.startsWith("0x")) {
            address = address.substring(2);
        }
        String sql = "SELECT " + SUBMITTED_BLOCKS_COUNT + " FROM \"Validator\" WHERE LOWER(" + ADDRESS + ") = ?";

        try (QueryResult result = sqlExecutors.executeQuery(sql, address)) {
            ResultSet rs = result.resultSet();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            logger.error("Failed to fetch submitted blocks {}", e.getLocalizedMessage());
            return 0;
        }
    }

    @Override
    public void incrementSubmittedBlocksCount(com.github.pwrlabs.pwrj.entities.Block block) {
        String sql = "UPDATE \"Validator\" SET submitted_blocks_count = submitted_blocks_count + 1 WHERE address = ?;";

        try {
            String address = block.getProposer().toLowerCase();
            if (address.startsWith("0x")) {
                address = address.substring(2);
            }

            sqlExecutors.executeUpdate(sql, address);
        } catch (Exception e) {
            logger.error("Failed to increment submitted block count: {}", e.getMessage());
        }
    }

}
