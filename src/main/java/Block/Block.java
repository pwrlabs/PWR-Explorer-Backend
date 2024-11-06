package Block;


import Txn.NewTxn;
import Txn.Txns;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static Core.Sql.Queries.getBlockTxns;

public class Block {
    private final Logger logger = LogManager.getLogger(Block.class);
    private String blockNumber;
    private final long timeStamp;
    private String  blockSubmitter;
    private long blockReward;
    private int blockSize;
    private int txnCount;

    private final NewTxn[] txns;

    public Block(String blockNumber, long timeStamp, String blockSubmitter, long blockReward, int blockSize, int txnCount) {
        this.blockNumber = blockNumber;
        this.timeStamp = timeStamp;
        this.blockSubmitter = blockSubmitter;
        this.blockReward = blockReward;
        this.blockSize = blockSize;
        this.txnCount = txnCount;

        txns = new NewTxn[txnCount];
        List<NewTxn> tempTxns = getBlockTxns(blockNumber);

        for(int i = 0; i < tempTxns.size(); i++) {
            txns[i] = tempTxns.get(i);
        }

        Blocks.add(this);
    }

    public void addTxn(NewTxn txn, int positionInTheBlock) {
        txns[positionInTheBlock] = txn;
    }

    public String getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(String blockNumber) {
        this.blockNumber = blockNumber;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public String getBlockSubmitter() {
        return blockSubmitter;
    }

    public void setBlockSubmitter(String blockSubmitter) {
        this.blockSubmitter = blockSubmitter;
    }

    public long getBlockReward() {
        return blockReward;
    }

    public void setBlockReward(long blockReward) {
        this.blockReward = blockReward;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
    }

    public int getTxnCount() {
        return txnCount;
    }

    public void setTxnCount(int txnCount) {
        this.txnCount = txnCount;
    }

    public NewTxn[] getTxns() {
        return txns;
    }

    //    public Logger getLogger() {
//        return logger;
//    }
//
//    public String getBlockNumber() {
//        return blockNumber;
//    }
//
//    public void setBlockNumber(String blockNumber) {
//        this.blockNumber = blockNumber;
//    }
//
//    public void setBlockSubmitter(byte[] blockSubmitter) {
//        this.blockSubmitter = blockSubmitter;
//    }
//
//    public void setBlockReward(long blockReward) {
//        this.blockReward = blockReward;
//    }
//
//    public void setBlockSize(int blockSize) {
//        this.blockSize = blockSize;
//    }
//
//    public int getTxnCount() {
//        return txnCount;
//    }
//
//    public void setTxnCount(int txnCount) {
//        this.txnCount = txnCount;
//    }
//
//    public long getTimeStamp() {
//        return timeStamp;
//    }

//    public byte[] getBlockSubmitter() {
//        return load("blockSubmitter");
//    }
//    public byte[] getBlockSubmitter() {
//        return blockSubmitter;
//    }
//    public long getBlockReward() {
//        return loadLong("blockReward");
//    }
//    public long getBlockReward() {
//        return blockReward;
//    }

//    public int getBlockSize() {
//        return loadInt("blockSize");
//    }

//    public int getBlockSize() {
//        return blockSize;
//    }
//    public int getTxnsCount() {
//        if(txns == null) return loadInt("txnCount");
//        else return txns.length;
//    }
//    public Txn[] getTxns() {
//        return txns;
//    }

}
