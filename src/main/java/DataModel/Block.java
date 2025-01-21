package DataModel;

public record Block(
        String blockHash,
        String blockNumber,
        long timeStamp,
        String blockSubmitter,
        long blockReward,
        int blockSize,
        int txnCount
) { }