package Core;

import DataModel.UserTransactionInfo;
import com.github.pwrlabs.pwrj.record.block.Block;
import com.github.pwrlabs.pwrj.record.transaction.DelegateTransaction;
import com.github.pwrlabs.pwrj.record.transaction.JoinTransaction;
import com.github.pwrlabs.pwrj.record.transaction.Transaction;
import com.github.pwrlabs.pwrj.record.transaction.WithdrawTransaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static Database.Queries.*;
import static Database.Queries.insertTxn;

public class Processor {
    private static final Logger logger = LogManager.getLogger(Processor.class);
    private static final Map<String, UserTransactionInfo> userTransactionsBuffer = new ConcurrentHashMap<>();
    private static long timeSinceLastFlush = System.currentTimeMillis();

    public static void processIncomingBlock(Block block) {
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

    public static boolean shouldFlushBuffer() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - timeSinceLastFlush > 20_000) {
            timeSinceLastFlush = currentTime;
            return true;
        }
        return false;
    }
}
