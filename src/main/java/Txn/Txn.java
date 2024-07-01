package Txn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Txn {
    private String hash;
    private long blockNumber;
    private int size;
    private int positionInBlock;
    private String fromAddress;
    private String toAddress;
    private long timestamp;
    private long value;
    private long txnFee;
    private String txnType;
    private boolean success;
    private String errorMessage;
    private long nonce;
    private long actionFee;
    private boolean paid;
    private String feePayer;

    private static final Logger logger = LogManager.getLogger(Txn.class);

    public Txn(String hash, long blockNumber, int size, int positionInBlock, String fromAddress, String toAddress,
               long timestamp, long value, long txnFee, String txnType, boolean success, String errorMessage,
               long nonce, long actionFee, boolean paid, String feePayer) {
        this.hash = hash;
        this.blockNumber = blockNumber;
        this.size = size;
        this.positionInBlock = positionInBlock;
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
        this.timestamp = timestamp;
        this.value = value;
        this.txnFee = txnFee;
        this.txnType = txnType;
        this.success = success;
        this.errorMessage = errorMessage;
        this.nonce = nonce;
        this.actionFee = actionFee;
        this.paid = paid;
        this.feePayer = feePayer;
        Txns.add(this);
    }

    // Getters
    public String getHash() { return hash; }
    public long getBlockNumber() { return blockNumber; }
    public int getSize() { return size; }
    public int getPositionInBlock() { return positionInBlock; }
    public String getFromAddress() { return fromAddress; }
    public String getToAddress() { return toAddress; }
    public long getTimestamp() { return timestamp; }
    public long getValue() { return value; }
    public long getTxnFee() { return txnFee; }
    public String getTxnType() { return txnType; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public long getNonce() { return nonce; }
    public long getActionFee() { return actionFee; }
    public boolean isPaid() { return paid; }
    public String getFeePayer() { return feePayer; }

    // You might want to add setters if needed

    @Override
    public String toString() {
        return "Txn{" +
                "hash='" + hash + '\'' +
                ", blockNumber=" + blockNumber +
                ", size=" + size +
                ", positionInBlock=" + positionInBlock +
                ", fromAddress='" + fromAddress + '\'' +
                ", toAddress='" + toAddress + '\'' +
                ", timestamp=" + timestamp +
                ", value=" + value +
                ", txnFee=" + txnFee +
                ", txnType='" + txnType + '\'' +
                ", success=" + success +
                ", errorMessage='" + errorMessage + '\'' +
                ", nonce=" + nonce +
                ", actionFee=" + actionFee +
                ", paid=" + paid +
                ", feePayer='" + feePayer + '\'' +
                '}';
    }
}