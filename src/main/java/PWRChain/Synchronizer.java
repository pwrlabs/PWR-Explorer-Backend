package PWRChain;

import Main.Hex;
import Main.Settings;
import Txn.Txn;

import User.User;
import User.Users;
import Validator.Validators;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import com.github.pwrlabs.pwrj.record.block.Block;
import com.github.pwrlabs.pwrj.record.transaction.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLOutput;

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
                        logger.info("Checking block: {}", blockToCheck);

                        try {
                            Block block = pwrj.getBlockByNumber(blockToCheck);
                            logger.info("Block hash: {}", block.getHash());

                            try {
                                insertBlock(block.getNumber(), block.getHash(), Hex.decode(block.getSubmitter().substring(2)),
                                        block.getTimestamp(), block.getTransactionCount(), block.getReward(), block.getSize(),block.processedWithoutCriticalErrors());
                            } catch (Exception e) {
                                logger.error("Error inserting block: {}", blockToCheck, e);
                            }

                            for (Transaction txn : block.getTransactions()) {
                                long value = txn.getValue();
                                byte[] data = null;

                                if (txn instanceof VmDataTransaction) {
                                    VmDataTransaction vmDataTxn = (VmDataTransaction) txn;
                                    data = Hex.decode(vmDataTxn.getData());

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
                                    new Txn(txn.getHash(), txn.getSize(), txn.getPositionInTheBlock(), block.getNumber(), txn.getSender().substring(2),
                                            txn.getReceiver(), txn.getTimestamp(), value, txn.getFee(), data, txn.getType(), txn.getNonce() + "", true, "No message for now");
                                    insertTxn(txn.getHash(), block.getNumber(), txn.getSize(), txn.getPositionInTheBlock(),
                                            txn.getSender().substring(2), txn.getReceiver(), block.getTimestamp(),
                                            value, txn.getFee(), data, txn.getType(), 0, 0, !txn.hasError(), txn.getErrorMessage(), String.valueOf(txn.toJSON()),txn.getNonce(),0,false,null);
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
