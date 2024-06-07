package Core.DataModel;

public class BlockModel {
    private long blockNumber;
    private String blockHash;
    private byte[] feeRecipient;
    private long timestamp;
    private int transactionsCount;
    private long blockReward;
    private int size;

    public BlockModel(long blockNumber, String blockHash, byte[] feeRecipient, long timestamp,
                      int transactionsCount, long blockReward, int size) {
        this.blockNumber = blockNumber;
        this.blockHash = blockHash;
        this.feeRecipient = feeRecipient;
        this.timestamp = timestamp;
        this.transactionsCount = transactionsCount;
        this.blockReward = blockReward;
        this.size = size;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public byte[] getFeeRecipient() {
        return feeRecipient;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getTransactionsCount() {
        return transactionsCount;
    }

    public long getBlockReward() {
        return blockReward;
    }

    public int getSize() {
        return size;
    }
}