package Core;

import DataModel.UserTransactionInfo;
import Database.Queries;
import Main.Main;
import com.github.pwrlabs.pwrj.entities.Block;
import com.github.pwrlabs.pwrj.entities.FalconTransaction;
import io.pwrlabs.util.encoders.BiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static Database.Queries.*;

public class Processor {
    private static final Logger logger = LoggerFactory.getLogger(Processor.class);
    private static final Map<String, UserTransactionInfo> userTransactionsBuffer = new ConcurrentHashMap<>();
    private static long timeSinceLastFlush = System.currentTimeMillis();

    public static void processIncomingBlocks(List<Long> blockNumbers) throws Exception {
        long totalStart = System.currentTimeMillis();
        List<FalconTransaction> allTxns = new ArrayList<>();
        List<Block> blocks = new ArrayList<>();

        logger.info("Started processing {} blocks", blockNumbers.size());

        for (long blockNumber : blockNumbers) {
            long blockStart = System.currentTimeMillis();
            long fetchStart = System.currentTimeMillis();

            try {
                BiResult<Block, List<FalconTransaction>> blockAndTransactions = Main.pwrj.getBlockAndTransactions(blockNumber);
                Block block = blockAndTransactions.getFirst();
                List<FalconTransaction> transactions = blockAndTransactions.getSecond();

                long fetchDuration = System.currentTimeMillis() - fetchStart;
                logger.info("Fetched transactions for block {} in {} ms", block.getBlockNumber(), fetchDuration);

                logger.info("Getting {} transactions by hashes for block {}", transactions.size(), block.getBlockNumber());

                long processStart = System.currentTimeMillis();
                for (FalconTransaction txn : transactions) {
                    if (txn instanceof FalconTransaction.FalconJoinAsValidator joinTxn) {
                        insertValidator(joinTxn.getSender().toLowerCase(), txn.getTimestamp());
                    }

                    processUserTransaction(txn.getSender().toLowerCase(), txn.getTransactionHash(), txn.getTimestamp());
                    processUserTransaction(txn.getReceiver().toLowerCase(), txn.getTransactionHash(), txn.getTimestamp());
                }
                long processDuration = System.currentTimeMillis() - processStart;
                logger.info("Processed {} transactions for block {} in {} ms", transactions.size(), block.getBlockNumber(), processDuration);

                blocks.add(block);
                allTxns.addAll(transactions);
                long blockDuration = System.currentTimeMillis() - blockStart;
                logger.info("Finished processing block {} in {} ms", block.getBlockNumber(), blockDuration);

            } catch (Exception e) {
                logger.error("Failed to fetch block {} and txns", blockNumber);
            }
        }

        logger.info("Finished processing all blocks in {} ms", System.currentTimeMillis() - totalStart);

        Queries.updateLastStoredBlock(blockNumbers.getLast());
        logger.info("Updated latest block number");

        if (!allTxns.isEmpty() || !blocks.isEmpty()) {
            long batchInsertStart = System.currentTimeMillis();
            insertBlockData(blocks);
            batchInsertTxns(allTxns);
            long batchInsertDuration = System.currentTimeMillis() - batchInsertStart;

            logger.info(
                    "Inserted {} blocks and {} transactions for blocks {} -> {} in {} ms",
                    blocks.size(),
                    allTxns.size(),
                    blocks.getFirst().getBlockNumber(),
                    blocks.getLast().getBlockNumber(),
                    batchInsertDuration
            );
        }

        logger.info("Total time for processIncomingBlocks: {} ms", System.currentTimeMillis() - totalStart);
    }

    private static void processUserTransaction(String address, String txnHash, long timestamp) {
        userTransactionsBuffer.compute(address, (key, existingInfo) -> {
            if (existingInfo == null) {
                // If not in buffer, check database
                boolean newUser = isNewUser(address.substring(2).toLowerCase());
                return new UserTransactionInfo(txnHash, timestamp, newUser);
            } else {
                return existingInfo.updateTransaction(txnHash, timestamp);
            }
        });
    }

    public static void flushTransactionBuffer() {
        if (userTransactionsBuffer.isEmpty()) {
            return;
        }
        Map<String, UserTransactionInfo> batchToProcess = new HashMap<>(userTransactionsBuffer);
        userTransactionsBuffer.clear();

        try {
            for (Map.Entry<String, UserTransactionInfo> entry : batchToProcess.entrySet()) {
                UserTransactionInfo info = entry.getValue();

                if (info.isNewUser()) {
                    upsertUserHistory(
                            entry.getKey(),
                            info.firstTxnHash(),
                            info.firstTxnTimestamp(),
                            info.count()
                    );
                } else {
                    upsertUserHistory(
                            entry.getKey(),
                            info.lastTxnHash(),
                            info.lastTxnTimestamp(),
                            info.count()
                    );
                }
            }
        } catch (Exception e) {
            logger.error("Error flushing transaction buffer: {}", e.getLocalizedMessage());
            userTransactionsBuffer.putAll(batchToProcess);
        }
    }

    public static boolean shouldFlushBuffer() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - timeSinceLastFlush > 20_000) {
            timeSinceLastFlush = currentTime;
            return true;
        }
        return false;
    }

    private static void insertBlockData(List<Block> blocks) {
        try {
            insertBlock(blocks);
            Queries.incrementSubmittedBlocksCount(blocks);
            logger.info("Incremented submitted blocks count");
        } catch (Exception e) {
            logger.error("Error inserting from block {} -> {}", blocks.getFirst().getBlockNumber(), blocks.getLast().getBlockNumber(), e);
        }
    }
}
