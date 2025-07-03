package Core;

import DataModel.UserTransactionInfo;
import Main.Main;
import Utils.Settings;
import com.github.pwrlabs.pwrj.entities.Block;
import com.github.pwrlabs.pwrj.entities.FalconTransaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static Database.Queries.*;

public class Processor {
    private static final Logger logger = LogManager.getLogger(Processor.class);
    private static final Map<String, UserTransactionInfo> userTransactionsBuffer = new ConcurrentHashMap<>();
    private static long timeSinceLastFlush = System.currentTimeMillis();

    public static void processIncomingBlocks(List<Block> blocks) throws Exception {
        long totalStart = System.currentTimeMillis();
        List<FalconTransaction> allTxns = new ArrayList<>();

        logger.info("Started processing {} blocks", blocks.size());

        for (Block block : blocks) {
            long blockStart = System.currentTimeMillis();
            long fetchStart = System.currentTimeMillis();
            List<FalconTransaction> transactions = Main.pwrj.getBlockAndTransactions(block.getBlockNumber()).getSecond();
            long fetchDuration = System.currentTimeMillis() - fetchStart;
            logger.info("Fetched transactions for block {} in {} ms", block.getBlockNumber(), fetchDuration);

            if (transactions != null && !transactions.isEmpty()) {
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

                allTxns.addAll(transactions);
            }
            long blockDuration = System.currentTimeMillis() - blockStart;
            logger.info("Finished processing block {} in {} ms", block.getBlockNumber(), blockDuration);
        }

        logger.info("Finished processing all blocks in {} ms", System.currentTimeMillis() - totalStart);

        if (!allTxns.isEmpty()) {
            long batchInsertStart = System.currentTimeMillis();
            batchInsertTxns(allTxns);
            long batchInsertDuration = System.currentTimeMillis() - batchInsertStart;

            logger.info(
                    "Inserted {} transactions for blocks {} -> {} in {} ms",
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
}
