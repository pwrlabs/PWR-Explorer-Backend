package PWRChain;

import Block.Block;
import Main.Hex;
import Main.Settings;
import Txn.Txn;
import User.User;
import User.Users;
import Validator.Validators;
import com.github.pwrlabs.pwrj.Transaction.*;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static Core.Sql.Queries.*;

public class Synchronizer {
    private static final Logger logger = LogManager.getLogger(Synchronizer.class);

    public static void sync() {
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
                    long latestBlockNumber = PWRJ.getLatestBlockNumber();
                    while (blockToCheck <= latestBlockNumber) {
                        logger.info("Checking block: {}", blockToCheck);
                        com.github.pwrlabs.pwrj.Block.Block block = PWRJ.getBlockByNumber(blockToCheck);

                        insertBlock(block.getNumber(), block.getHash(), Hex.decode(block.getSubmitter().substring(2)),
                                block.getTimestamp(), block.getTransactionCount(), block.getReward(), block.getSize());

                        for (Transaction txn : block.getTransactions()) {
                            long value = txn.getValue();
                            byte[] data = null;

                            if (txn instanceof VmDataTxn) {
                                VmDataTxn vmDataTxn = (VmDataTxn) txn;
                                data = Hex.decode(vmDataTxn.getData());

                            } else if (txn instanceof DelegateTxn) {
                                DelegateTxn delegateTxn = (DelegateTxn) txn;
                                updateInitialDelegations(delegateTxn.getSender(),delegateTxn.getValidator(),delegateTxn.getAmount());
//                                User user = Users.getUserCreateIfMissing(delegateTxn.getSender());
//                                user.addDelegation(delegateTxn.getTo(), delegateTxn.getAmount());
                            } else if (txn instanceof WithdrawTxn) {
                                WithdrawTxn withdrawTxn = (WithdrawTxn) txn;
                                User user = Users.getUserCreateIfMissing(withdrawTxn.getSender());
                                user.checkDelegation(withdrawTxn.getTo(),withdrawTxn.getValidator());
                            } else if (txn instanceof JoinTxn) {
                                JoinTxn joinTxn = (JoinTxn) txn;
                                Validators.add(joinTxn.getSender(), block.getTimestamp());
                            }

                            try {
                                insertTxn(txn.getHash(), block.getNumber(), txn.getSize(), txn.getPositionInTheBlock(),
                                       txn.getSender().substring(2), txn.getTo(), block.getTimestamp(),
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