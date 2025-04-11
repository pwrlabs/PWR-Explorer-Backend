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
import static Database.Queries.insertTxn;

public class Processor {
    private static final Logger logger = LogManager.getLogger(Processor.class);
    private static final Map<String, UserTransactionInfo> userTransactionsBuffer = new ConcurrentHashMap<>();
    private static long timeSinceLastFlush = System.currentTimeMillis();

    public static void processIncomingBlock(Block block) throws Exception {
        List<FalconTransaction> transactions = new ArrayList<>();
        List<String> txnHashes = block.getTransactionHashes();
        if(txnHashes != null || !txnHashes.isEmpty()) transactions = Main.pwrj.getTransactionsByHashes(txnHashes);

        for (FalconTransaction txn : transactions) {
            long value = 0;
            if (txn instanceof FalconTransaction.FalconTransfer payableTxn) {
                value = payableTxn.getAmount();
            } else if (txn instanceof FalconTransaction.PayableVidaDataTxn vidaDataTxn) {
                value = vidaDataTxn.getValue();
            } else if (txn instanceof FalconTransaction.TransferPWRFromVidaTxn transferFromVidaTxn) {
                value = transferFromVidaTxn.getAmount();
            }

            if (txn instanceof FalconTransaction.FalconJoinAsValidator joinTxn) {
                insertValidator(joinTxn.getSender().toLowerCase(), txn.getTimestamp());
            }

            try {
                insertTxn(txn.getTransactionHash().toLowerCase(), block.getBlockNumber(), txn.getPositionInBlock(),
                        txn.getSender().substring(2), txn.getReceiver(), txn.getTimestamp(),
                        value, txn.getType(), txn.getPaidTotalFee(), !txn.isSuccess());

                processUserTransaction(txn.getSender().toLowerCase(), txn.getTransactionHash(), txn.getTimestamp());
                processUserTransaction(txn.getReceiver().toLowerCase(), txn.getTransactionHash(), txn.getTimestamp());

            } catch (Exception e) {
                logger.error("Error inserting transaction: {} {}", txn.getTransactionHash(), e.getLocalizedMessage());
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
