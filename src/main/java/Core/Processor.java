package Core;

import DataModel.UserTransactionInfo;
import com.github.pwrlabs.pwrj.entities.FalconTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static Database.Queries.*;

public class Processor {
    private static final Logger logger = LoggerFactory.getLogger(Processor.class);
    private static final Map<String, UserTransactionInfo> userTransactionsBuffer = new ConcurrentHashMap<>();
    private static long timeSinceLastFlush = System.currentTimeMillis();
    private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 20, 1, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());

    public static void processTxns(long block, List<FalconTransaction> txnsToProcess) throws Exception {
        long totalStart = System.currentTimeMillis();
        long txnsCount = txnsToProcess.size();

        executor.execute(() -> {
            logger.info("Started processing {} transactions for block {}", txnsCount, block);
            for (FalconTransaction txn : txnsToProcess) {
                if (txn instanceof FalconTransaction.FalconJoinAsValidator joinTxn) {
                    insertValidator(joinTxn.getSender().toLowerCase(), txn.getTimestamp());
                }

                processUserTransaction(txn.getSender().toLowerCase(), txn.getTransactionHash(), txn.getTimestamp());
                processUserTransaction(txn.getReceiver().toLowerCase(), txn.getTransactionHash(), txn.getTimestamp());
            }
            logger.info("Finished processing all transactions in {} ms", System.currentTimeMillis() - totalStart);

            if (!txnsToProcess.isEmpty()) {
                long batchInsertStart = System.currentTimeMillis();
                batchInsertTxns(txnsToProcess);
                long batchInsertDuration = System.currentTimeMillis() - batchInsertStart;

                logger.info(
                        "Inserted {} transactions for block {} in {} ms",
                        txnsCount,
                        block,
                        batchInsertDuration
                );
            }

            logger.info("Total time for processing {} txns in block {} is: {} ms", txnsCount, block, System.currentTimeMillis() - totalStart);
        });
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

        logger.info("Flushing user txns buffer");
        long start = System.currentTimeMillis();

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
            logger.info("Finished flushing user txns buffer took: {} ms", System.currentTimeMillis() - start);
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
