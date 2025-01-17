package DataModel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Block {
    private final String blockHash;
    private final String blockNumber;
    private final long timeStamp;
    private final String blockSubmitter;
    private final long blockReward;
    private final int blockSize;
    private final int txnCount;


    public Block(String blockHash, String blockNumber, long timeStamp, String blockSubmitter,
                 long blockReward, int blockSize, int txnCount) {
        this.blockHash = blockHash;
        this.blockNumber = blockNumber;
        this.timeStamp = timeStamp;
        this.blockSubmitter = blockSubmitter;
        this.blockReward = blockReward;
        this.blockSize = blockSize;
        this.txnCount = txnCount;
    }

    // Getters and setters
    public String getBlockHash() { return blockHash; }

    public String getBlockNumber() {
        return blockNumber;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public String getBlockSubmitter() {
        return blockSubmitter;
    }

    public long getBlockReward() {
        return blockReward;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public int getTxnCount() {
        return txnCount;
    }

    @Override
    public String toString() {
        return "DataModel.Block{" +
                "blockNumber='" + blockNumber + '\'' +
                ", timeStamp=" + timeStamp +
                ", blockSubmitter='" + blockSubmitter + '\'' +
                ", blockReward=" + blockReward +
                ", blockSize=" + blockSize +
                ", txnCount=" + txnCount +
                '}';
    }
}