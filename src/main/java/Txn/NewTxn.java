package Txn;

public record NewTxn(String hash, long blockNumber, int positionInBlock, String fromAddress, String toAddress,
                     long timestamp, long value, String txnType, long txnFee, boolean success
) {
}
