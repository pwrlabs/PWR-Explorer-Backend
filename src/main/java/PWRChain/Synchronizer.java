package PWRChain;

import DailyActivity.Stats;
import Txn.NewTxn;
import Txn.Txns;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import com.github.pwrlabs.pwrj.record.block.Block;
import com.github.pwrlabs.pwrj.record.transaction.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static Core.Sql.Queries.*;

public class Synchronizer {
    private static final Logger logger = LogManager.getLogger(Synchronizer.class);

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
                                insertBlock(block.getNumber(), block.getHash(), block.getSubmitter().substring(2),
                                        block.getTimestamp(), block.getTransactionCount(), block.getReward(), block.getSize(),block.processedWithoutCriticalErrors()
                                );
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
                                    Stats.getInstance().processBlock(txn.getSender(), block.getTransactionCount(), block.getReward(), block.getTimestamp(), block.getSize());
                                    insertTxn(txn.getHash(), block.getNumber(), txn.getPositionInTheBlock(),
                                            txn.getSender().substring(2), txn.getReceiver(), txn.getTimestamp(),
                                            txn.getValue(), txn.getType(), txn.getFee(), !txn.hasError());


                                } catch (Exception e) {
                                    logger.error("Error inserting transaction: {}", txn.getHash(), e);
                                }
                            }

                        } catch (Exception e) {
                            logger.error("Error processing block: {} {}", blockToCheck, e);
                        }

                        ++blockToCheck;

                        long processingTime = System.currentTimeMillis() - startTime;
                        long sleepTime = Math.max(0, 10 - processingTime);

                        if (sleepTime > 0) {
                            try {
                                Thread.sleep(sleepTime);
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
}
