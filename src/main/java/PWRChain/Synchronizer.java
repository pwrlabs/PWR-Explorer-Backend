package PWRChain;

import DailyActivity.Stats;
import Txn.NewTxn;
import Txn.Txns;
import Validator.Validators;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import com.github.pwrlabs.pwrj.record.block.Block;
import com.github.pwrlabs.pwrj.record.transaction.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*;
import static Core.Sql.Queries.*;

public class Synchronizer {
    private static final Logger logger = LogManager.getLogger(Synchronizer.class);

    public static void sync(PWRJ pwrj) {
        new Thread(() -> {
            long blockToCheck = getLastBlockNumber();
            if (blockToCheck == 0) blockToCheck = 1;

            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.error("Thread sleep interrupted", e);
                }

                try {
                    long latestBlockNumber = pwrj.getLatestBlockNumber();
                    while (blockToCheck <= latestBlockNumber) {
//           nfo             logger.info("Checking block: {}", blockToCheck);

                        try {
                            Block block = pwrj.getBlockByNumberExcludingDataAndExtraData(blockToCheck);
                            logger.info("Scanned block: {} ",block);
                            logger.info("Block hash: {}", block.getHash());

                            try {
                                insertBlock(block.getNumber(), block.getHash(), block.getSubmitter().substring(2),
                                        block.getTimestamp(), block.getTransactionCount(), block.getReward(), block.getSize(),block.processedWithoutCriticalErrors()
                                );
                            } catch (Exception e) {
                                logger.error("Error inserting block: {}", blockToCheck, e);
                            }

                            for (Transaction txn : block.getTransactions()) {
                                long value = txn.getValue();
                                byte[] data = null;

                                if (txn instanceof VmDataTransaction) {
                                    VmDataTransaction vmDataTxn = (VmDataTransaction) txn;
//                                    data = Hex.decode(vmDataTxn.getData());

                                } else if (txn instanceof DelegateTransaction) {
                                    DelegateTransaction delegateTxn = (DelegateTransaction) txn;
                                    updateInitialDelegations(delegateTxn.getReceiver(), delegateTxn.getSender(), delegateTxn.getValue());
                                } else if (txn instanceof WithdrawTransaction) {
                                    WithdrawTransaction withdrawTxn = (WithdrawTransaction) txn;
                                    updateInitialDelegations(withdrawTxn.getReceiver(), withdrawTxn.getSender(), withdrawTxn.getValue());
                                } else if (txn instanceof JoinTransaction) {
                                    JoinTransaction joinTxn = (JoinTransaction) txn;
                                    Validators.add(joinTxn.getSender(), block.getTimestamp());
                                }

                                try {
                                    long blockNumber = (long) block.getNumber();
                                    List<Validators> validatorList = new ArrayList<>();

                                    NewTxn newTxn = new NewTxn(txn.getHash(), (int) block.getNumber(), txn.getPositionInTheBlock(), txn.getSender().substring(2),
                                            txn.getReceiver(), txn.getTimestamp(), txn.getValue(), txn.getType(), txn.getFee(), !txn.hasError());
                                    Txns.add(newTxn);
                                    Stats.getInstance().processBlock(txn.getSender(), block.getTransactionCount(), block.getReward(), block.getTimestamp(), block.getSize());

                                    insertTxn(txn.getHash(), block.getNumber(), txn.getPositionInTheBlock(),
                                            txn.getSender().substring(2), txn.getReceiver(), block.getTimestamp(),
                                            value, txn.getType(), txn.getFee(), !txn.hasError());
                                } catch (Exception e) {
                                    logger.error("Error inserting transaction: {}", txn.getHash(), e);
                                }
                            }

                        } catch (Exception e) {
                            logger.error("Error processing block: {}", blockToCheck, e);
                        }

                        ++blockToCheck;
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            logger.error("Thread sleep interrupted", e);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error getting latest block number", e);
                }
            }
        }).start();
    }
}
