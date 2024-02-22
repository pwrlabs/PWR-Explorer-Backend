package PWRChain;

import Block.Blocks;
import Block.Block;
import Main.Hex;
import Main.Settings;
import Txn.Txn;
import User.User;
import User.Users;
import Validator.Validators;
import com.github.pwrlabs.dbm.SDBM;
import com.github.pwrlabs.pwrj.Transaction.*;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static Core.Sql.Queries.insertBlock;
import static Core.Sql.Queries.insertTxn;

public class Synchronizer {
    private static final Logger logger = LogManager.getLogger(Synchronizer.class);
    public static void sync() {

        new Thread() {
            public void run() {
                Blocks.updateLatestBlockNumber();
                long blockToCheck = Blocks.getLatestBlockNumber();
//                if(blockToCheck == 0) {
//                    Blocks.updateLatestBlockNumber();
//                }
                //                            PWRJ.getOwnerOfVm(vmId);  TODO: use this to fetch vm owner

                if(blockToCheck == 0) blockToCheck = 1;
                while(true) {
                    try { Thread.sleep(1000); } catch (Exception e) { e.printStackTrace(); }
                    try {
                        long latestBlockNumber = PWRJ.getLatestBlockNumber();
                        while(blockToCheck <= latestBlockNumber) {
                            logger.info("Checking block: {}", blockToCheck);
                            com.github.pwrlabs.pwrj.Block.Block block = PWRJ.getBlockByNumber(blockToCheck);

                            new Block(block.getNumber() + "", block.getTimestamp(), Hex.decode(block.getSubmitter().substring(2)), block.getReward(), block.getSize(), block.getTransactionCount());
                            insertBlock(block.getNumber() + "", block.getTimestamp(), Hex.decode(block.getSubmitter().substring(2)), block.getReward(), block.getSize(), block.getTransactionCount()); //TODO: check if this should be placed within Block to ensure caching of variables
                            for(Transaction txn: block.getTransactions()) {
                                long value = txn.getValue();
                                byte[] data = null;

                                if (txn instanceof VmDataTxn) {
                                    VmDataTxn vmDataTxn = (VmDataTxn) txn;
                                    data = Hex.decode(vmDataTxn.getData());
                                } else if (txn instanceof DelegateTxn) {
                                    DelegateTxn delegateTxn = (DelegateTxn) txn;

                                    User u = Users.getUser(delegateTxn.getSender());
                                    u.addDelegation(delegateTxn.getTo(), delegateTxn.getAmount());
                                } else if (txn instanceof WithdrawTxn) {
                                    WithdrawTxn withdrawTxn = (WithdrawTxn) txn;

                                    User u = Users.getUser(withdrawTxn.getSender());
                                    u.checkDelegation(withdrawTxn.getTo());
                                } else if (txn instanceof JoinTxn) {
                                    JoinTxn joinTxn = (JoinTxn) txn;

                                    Validators.add(joinTxn.getSender(), block.getTimestamp());  //TODO: reroute this to DB
                                }

                                try {
                                    new Txn(txn.getHash(), txn.getSize(), txn.getPositionInTheBlock(), block.getNumber(), Hex.decode(txn.getSender().substring(2)), txn.getTo(), value, txn.getFee(), data, txn.getType(), txn.getNonce()+""); //TODO: perhaps pass this as a parameter?
                                    insertTxn(txn.getHash(), txn.getSize(), txn.getPositionInTheBlock(), block.getNumber(), Hex.decode(txn.getSender().substring(2)), txn.getTo(), value, txn.getFee(), data, txn.getType(), txn.getNonce()+"");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            ++blockToCheck;
//                            SDBM.store("blockToCheck", ++blockToCheck); //TODO does this need to be here?
                            Thread.sleep(10);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

}
