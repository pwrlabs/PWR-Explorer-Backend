package PWRChain;

import Block.Block;
import Block.Blocks;
import Main.Hex;
import Main.Settings;
import Main.Main;
import Txn.Txn;
import User.User;
import User.Users;
import Validator.Validators;
import com.github.pwrlabs.dbm.SDBM;
import com.github.pwrlabs.pwrj.entities.FalconTransaction;

import java.util.List;

public class Synchronizer {
    public static void sync() {

        new Thread() {
            public void run() {
                long blockToCheck = Blocks.getLatestBlockNumber();
                if(blockToCheck == 0) blockToCheck = 1;
                while(true) {
                    try { Thread.sleep(1000); } catch (Exception e) { e.printStackTrace(); }
                    try {
                        long latestBlockNumber = Main.pwrj.getLatestBlockNumber();
                        while(blockToCheck <= latestBlockNumber) {
                            System.out.println("Checking block: " + blockToCheck);
                            com.github.pwrlabs.pwrj.entities.Block block = Main.pwrj.getBlockByNumber(blockToCheck);

                            byte[] proposer = Hex.decode(block.getProposer().startsWith("0x") ? block.getProposer().substring(2) : block.getProposer());
                            new Block(block.getBlockNumber() + "", block.getTimeStamp(), proposer, block.getBlockReward(), block.getBlockSize(), block.getTransactionCount());

                            List<FalconTransaction> transactions = Main.pwrj.getTransactionsByHashes(block.getTransactionHashes());
                            for (FalconTransaction txn: transactions) {
                                long value = 0;
                                byte[] data = null;

                                if(txn instanceof FalconTransaction.PayableVidaDataTxn) {
                                    FalconTransaction.PayableVidaDataTxn vmDataTxn = (FalconTransaction.PayableVidaDataTxn) txn;
                                    data = vmDataTxn.getData();
                                    value = vmDataTxn.getValue();
                                } else if (txn instanceof FalconTransaction.FalconJoinAsValidator) {
                                    FalconTransaction.FalconJoinAsValidator joinTxn = (FalconTransaction.FalconJoinAsValidator) txn;

                                    Validators.add(joinTxn.getSender(), block.getTimeStamp());
                                }

                                try {
                                    byte[] sender = Hex.decode(txn.getSender().startsWith("0x") ? txn.getSender().substring(2) : txn.getSender());
                                    new Txn(txn.getTransactionHash(), txn.getSize(), txn.getPositionInBlock(), txn.getBlockNumber(), sender, txn.getReceiver(), value, txn.getPaidTotalFee(), data, txn.getType(), txn.getNonce() + "");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                            }

                            SDBM.store("blockToCheck", ++blockToCheck);
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
