package Core;

import DataModel.UserTransactionInfo;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import com.github.pwrlabs.pwrj.record.block.Block;
import com.github.pwrlabs.pwrj.record.transaction.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static Database.Queries.*;

public class Synchronizer {
    private static final Logger logger = LogManager.getLogger(Synchronizer.class);
    private static final Map<String, UserTransactionInfo> userTransactionsBuffer = new ConcurrentHashMap<>();
    private static long elapsedTime = 0;
    private static int blocks = 1;

    public static void sync(PWRJ pwrj) {
        new Thread(() -> {
            long blockToCheck = getLastBlockNumber();
            if (blockToCheck == 0) blockToCheck = 1;

            while (true) {
                try {
                    long latestBlockNumber = pwrj.getLatestBlockNumber();
                    logger.info("Latest block number {}", latestBlockNumber);
                    while (blockToCheck <= latestBlockNumber) {
                        long startTime = System.currentTimeMillis();
                        try {
                            Block block = pwrj.getBlockByNumberExcludingDataAndExtraData(blockToCheck);
                            try {
                                insertBlock(block.getNumber(), block.getHash().toLowerCase(), block.getSubmitter().substring(2),
                                        block.getTimestamp(), block.getTransactionCount(), block.getReward(), block.getSize(), block.processedWithoutCriticalErrors()
                                );
                                blocks++;
                                if (blocks % 10 == 0) {
                                    logger.info("Scanned block: {}", block.getNumber());
                                    blocks = 0;
                                }
                            } catch (Exception e) {
                                logger.error("Error inserting block: {}", blockToCheck, e);
                            }

                            for (Transaction txn : block.getTransactions()) {
                                if (txn instanceof DelegateTransaction delegateTxn) {
                                    updateInitialDelegations(delegateTxn.getReceiver(), delegateTxn.getSender(), delegateTxn.getValue());
                                } else if (txn instanceof WithdrawTransaction withdrawTxn) {
                                    updateInitialDelegations(withdrawTxn.getReceiver(), withdrawTxn.getSender(), withdrawTxn.getValue());
                                } else if (txn instanceof JoinTransaction joinTxn) {
                                    insertValidator(joinTxn.getSender().toLowerCase(), txn.getTimestamp());
                                }

                                try {
                                    insertTxn(txn.getHash().toLowerCase(), block.getNumber(), txn.getPositionInTheBlock(),
                                            txn.getSender().substring(2), txn.getReceiver(), txn.getTimestamp(),
                                            txn.getValue(), txn.getType(), txn.getFee(), !txn.hasError());

                                    processUserTransaction(txn.getSender().toLowerCase(), txn.getHash(), txn.getTimestamp());
                                    processUserTransaction(txn.getReceiver().toLowerCase(), txn.getHash(), txn.getTimestamp());

                                } catch (Exception e) {
                                    logger.error("Error inserting transaction: {} {}", txn.getHash(), e.getLocalizedMessage());
                                }
                            }

                        } catch (Exception e) {
                            logger.error("Error processing block: {} {}", blockToCheck, e);
                        }

                        ++blockToCheck;

                        long processingTime = System.currentTimeMillis() - startTime;
                        long sleepTime = Math.max(0, 10 - processingTime);

                        elapsedTime += processingTime + sleepTime;

                        if (sleepTime > 0) {
                            try {
                                if (elapsedTime > 30_000) {
                                    flushTransactionBuffer();
                                    elapsedTime = 0;
                                }
                                Thread.sleep(sleepTime);
                            } catch (InterruptedException e) {
                                logger.error("Thread sleep interrupted", e);
                            }
                        } else {
                            try {
                                if (elapsedTime > 30_000) {
                                    flushTransactionBuffer();
                                    elapsedTime = 0;
                                }
                                Thread.sleep(0);
                            } catch (InterruptedException e) {
                                logger.error("Thread sleep interrupted", e);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error getting latest block number", e);
                }
            }
        }).start();
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

    private static void flushTransactionBuffer() {
        if (userTransactionsBuffer.isEmpty()) {
            return;
        }

        logger.info("Flushing transaction buffer with {} users", userTransactionsBuffer.size());

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
            logger.info("Successfully flushed transaction buffer");
        } catch (Exception e) {
            logger.error("Error flushing transaction buffer: {}", e.getLocalizedMessage());
            userTransactionsBuffer.putAll(batchToProcess);
        }
    }

}
