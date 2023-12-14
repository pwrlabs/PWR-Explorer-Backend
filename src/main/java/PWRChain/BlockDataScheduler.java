
package PWRChain;

import Main.Settings;
import com.github.pwrlabs.pwrj.Transaction.Transaction;
import com.github.pwrlabs.pwrj.protocol.PWRJ;

import java.math.BigDecimal;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class BlockDataScheduler {

    private static final String rpcNodeUrl = "your_rpc_node_url";
    private static int latestBlockNumber = 0;
    private static final int THREAD_POOL_SIZE = 20; // Adjust the pool size as needed

    private static final String jdbcUrl = "jdbc:mysql://localhost:3306/pwrchain";
    private static final String jdbcUser = "root";
    private static final String jdbcPassword = "####";

    public static void scheduler() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        // Schedule the task to run every 1 minute
        scheduler.scheduleAtFixedRate(BlockDataScheduler::fetchBlockData, 0, 5, TimeUnit.SECONDS);
    }

    private static void fetchBlockData() {
        try {
            // Fetch blocks as before
            List<com.github.pwrlabs.pwrj.Block.Block> blocks = fetchBlocksConcurrently();

            // Use an ExecutorService for concurrent database insertion
            ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

            // Insert blocks and transactions concurrently
            for (com.github.pwrlabs.pwrj.Block.Block block : blocks) {
                executorService.submit(() -> {
                    try {
                        insertBlockAndTransactions(block);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                });
            }

            // Shutdown the executor service
            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        } catch (Exception e) {
            // Handle exceptions appropriately
            e.printStackTrace();
        }
    }

    private static List<com.github.pwrlabs.pwrj.Block.Block> fetchBlocksConcurrently() {
        try {
            ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            List<Future<com.github.pwrlabs.pwrj.Block.Block>> futures = new ArrayList<>();

            // Start fetching data for the next few blocks in parallel
            for (int i = 0; i < THREAD_POOL_SIZE; i++) {
                int blockNumber = latestBlockNumber + 1;
                Future<com.github.pwrlabs.pwrj.Block.Block> future = executorService.submit(() -> PWRJ.getBlockByNumber(blockNumber));
                futures.add(future);
                latestBlockNumber++; // Update latestBlockNumber for the next iteration
            }

            // Collect the results in a list
            List<com.github.pwrlabs.pwrj.Block.Block> blocks = new ArrayList<>();
            for (Future<com.github.pwrlabs.pwrj.Block.Block> future : futures) {
                com.github.pwrlabs.pwrj.Block.Block block = future.get();
                blocks.add(block);
                System.out.println("Fetched block data for block number: " + block.getNumber());
            }

            // Shutdown the executor service
            executorService.shutdown();

            return blocks;

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>(); // Return an empty list in case of an error
        }
    }

    private static void insertBlockAndTransactions(com.github.pwrlabs.pwrj.Block.Block block) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
            // Insert the block into the 'blocks' table
            long blockId = insertBlock(connection, block);

            // Insert transactions into the 'transactions' table
            for (com.github.pwrlabs.pwrj.Transaction.Transaction transaction : block.getTransactions()) {
                insertTransaction(connection, blockId, transaction);
            }
        }
    }

    private static long insertBlock(Connection connection, com.github.pwrlabs.pwrj.Block.Block block) throws SQLException {
        String sql = "INSERT INTO blocks (block_number, transaction_count, block_size, block_reward, timestamp, block_hash, submitter, success) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, block.getNumber());
            statement.setLong(2, block.getTransactionCount());
            statement.setLong(3, block.getSize());
            statement.setLong(4, block.getReward());
            statement.setLong(5, block.getTimestamp());
            statement.setString(6, block.getHash());
            statement.setString(7, block.getSubmitter());
            statement.setBoolean(8, block.isSuccess());

            int affectedRows = statement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Inserting block failed, no rows affected.");
            }
            else
             return block.getNumber();
        }
    }

    private static void insertTransaction(Connection connection, long blockId, com.github.pwrlabs.pwrj.Transaction.Transaction transaction) throws SQLException {
        String sql = "INSERT INTO transactions (block_id, position_in_block, nonce_or_validation_hash, size, raw_txn, txn_fee, from_address, to_address, type, value, transaction_hash,amount_usd_value,fee_usd_value) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?)";

        // calculating the dynamic values
        long feeUsdValue = 0L;
        long amountUsdValue = 0L;
        if(transaction.getValue() > 0) {
            double usdValue = (transaction.getValue() * Settings.getPrice());
            BigDecimal result = BigDecimal.valueOf(usdValue).divide(BigDecimal.valueOf((long) Math.pow(10, 11)));
            DecimalFormat df = new DecimalFormat("#.0000");
            BigDecimal formattedResult = new BigDecimal(df.format(result));

            amountUsdValue = formattedResult.longValue();
        }

        //fee USD Value
        double usdFeeValue = (transaction.getFee() * Settings.getPrice());
        BigDecimal feeResult = BigDecimal.valueOf(usdFeeValue).divide(BigDecimal.valueOf((long) Math.pow(10, 11)));
        DecimalFormat feeDf = new DecimalFormat("#.00000000");
        BigDecimal formattedFeeResult = new BigDecimal(feeDf.format(feeResult));
        feeUsdValue = formattedFeeResult.longValue();



        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, blockId);
            statement.setLong(2, transaction.getPositionInTheBlock());
            statement.setString(3, transaction.getNonceOrValidationHash());
            statement.setLong(4, transaction.getSize());
            statement.setString(5, transaction.getHash());
            statement.setLong(6,  transaction.getFee());
            statement.setString(7, transaction.getFrom());
            statement.setString(8, transaction.getTo());
            statement.setString(9, transaction.getType());
            statement.setLong(10, transaction.getValue());
            statement.setString(11, transaction.getHash());
            statement.setLong(12, amountUsdValue);
            statement.setLong(13, feeUsdValue);
            statement.executeUpdate();
        }
    }
}
