package Block;

import java.util.ArrayList;
import java.util.List;

import Txn.Txn;
import com.github.pwrlabs.dbm.DBM;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;

import static Core.Sql.Queries.getBlockTxns;

public class Block {
    private final Logger logger = LogManager.getLogger(Block.class);
    private String blockNumber;
    private final long timeStamp;
    private byte[] blockSubmitter;
    private long blockReward;
    private int blockSize;
    private int txnCount;

    private final Txn[] txns;

    public Block(String blockNumber, long timeStamp, byte[] blockSubmitter, long blockReward, int blockSize, int txnCount) {
        this.blockNumber = blockNumber;
        this.timeStamp = timeStamp;
        this.blockSubmitter = blockSubmitter;
        this.blockReward = blockReward;
        this.blockSize = blockSize;
        this.txnCount = txnCount;

        txns = new Txn[txnCount];
        logger.info("CREATED ARRAY OF LENGTH: {}, ACTUAL LENGTH: {}", txnCount, txns.length);
        Txn[] tempTxns = getBlockTxns(blockNumber);

        for(int i = 0; i < tempTxns.length; i++) {
            txns[i] = tempTxns[i];
        }

        logger.info("LENGTH AFTER PROCESSING: {}", txns.length);

        Blocks.add(this);
    }

    public void addTxn(Txn txn, int positionInTheBlock) {
        logger.info("TXNS OF BLOCK: {} HAS A SIZE OF : {} WHEN CALLED", blockNumber, txns.length);
        txns[positionInTheBlock] = txn;
        logger.info("New txn added to block: {}  at position: {}", blockNumber, positionInTheBlock);
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

    public byte[] getBlockSubmitter() {
        return blockSubmitter;
    }

    public void setBlockSubmitter(byte[] blockSubmitter) {
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

    public Txn[] getTxns() {
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
