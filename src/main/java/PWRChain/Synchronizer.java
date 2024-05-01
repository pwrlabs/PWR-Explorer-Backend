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
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    long latestBlockNumber = pwrj.getLatestBlockNumber();
                    while (blockToCheck <= latestBlockNumber) {
                        logger.info("Checking block: {}", blockToCheck);
                        Block block = pwrj.getBlockByNumber(blockToCheck);

                        insertBlock(block.getNumber(), block.getHash(), Hex.decode(block.getSubmitter().substring(2)),
                                block.getTimestamp(), block.getTransactionCount(), block.getReward(), block.getSize());

                        for (Transaction txn : block.getTransactions()) {
                            long value = txn.getValue();
                            byte[] data = null;

                            if (txn instanceof VmDataTransaction) {
                                VmDataTransaction vmDataTxn = (VmDataTransaction) txn;
                                data = Hex.decode(vmDataTxn.getData());

                            } else if (txn instanceof DelegateTransaction) {
                                DelegateTransaction delegateTxn = (DelegateTransaction) txn;
                                updateInitialDelegations(delegateTxn.getReceiver(),delegateTxn.getSender(),delegateTxn.getValue());
//                                User user = Users.getUserCreateIfMissing(delegateTxn.getSender());
//                                user.addDelegation(delegateTxn.getTo(), delegateTxn.getAmount());
                            } else if (txn instanceof WithdrawTransaction) {
                                WithdrawTransaction withdrawTxn = (WithdrawTransaction) txn;
                                updateInitialDelegations(withdrawTxn.getReceiver(),withdrawTxn.getSender(),withdrawTxn.getValue());
//                                User user = Users.getUserCreateIfMissing(withdrawTxn.getSender());
//                                user.checkDelegation(pwrj, withdrawTxn.getReceiver(),withdrawTxn.getValidator());
                            } else if (txn instanceof JoinTransaction) {
                                JoinTransaction joinTxn = (JoinTransaction) txn;
                                Validators.add(joinTxn.getSender(), block.getTimestamp());
                            }

                            try {
                                insertTxn(txn.getHash(), block.getNumber(), txn.getSize(), txn.getPositionInTheBlock(),
                                       txn.getSender().substring(2), txn.getReceiver(), block.getTimestamp(),
                                        value, txn.getFee(), data, txn.getType(), 0, 0);
//                                User user = Users.getUserCreateIfMissing(txn.getSender());
//                                user.addTxn(new Txn(txn.getHash(), txn.getSize(), txn.getPositionInTheBlock(),
//                                        block.getNumber(), Hex.decode(txn.getSender().substring(2)), txn.getTo(),
//                                        block.getTimestamp(), value, txn.getFee(), data, txn.getType(), txn.getNonce() + ""));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        ++blockToCheck;
                        Thread.sleep(10);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}