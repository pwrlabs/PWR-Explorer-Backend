package Core.DataModel;

import org.json.JSONObject;



public class TxnModel {
    private String hash;
    private int size;
    private int positionInBlock;
    private long blockNumber;
    private String fromAddress;
    private String toAddress;
    private long timestamp;
    private long value;
    private long txnFee;
    private byte[] txnData;
    private String txnType;
    private boolean success;
    private String errorMessage;
    private JSONObject extraData;
    private long nonce;
    private long actionFee;
    private boolean paid;
    private String feePayer;

    public TxnModel(String hash, int size, int positionInBlock, long blockNumber, String fromAddress, String toAddress,
               long timestamp, long value, long txnFee, byte[] txnData, String txnType, boolean success,
               String errorMessage, JSONObject extraData, long nonce, long actionFee, boolean paid, String feePayer) {
        this.hash = hash;
        this.size = size;
        this.positionInBlock = positionInBlock;
        this.blockNumber = blockNumber;
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
        this.timestamp = timestamp;
        this.value = value;
        this.txnFee = txnFee;
        this.txnData = txnData;
        this.txnType = txnType;
        this.success = success;
        this.errorMessage = errorMessage;
        this.extraData = extraData;
        this.nonce = nonce;
        this.actionFee = actionFee;
        this.paid = paid;
        this.feePayer = feePayer;
    }

    // Getters for all fields
    public String getHash() {
        return hash;
    }

    public int getSize() {
        return size;
    }

    public int getPositionInBlock() {
        return positionInBlock;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getToAddress() {
        return toAddress;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getValue() {
        return value;
    }

    public long getTxnFee() {
        return txnFee;
    }

    public byte[] getTxnData() {
        return txnData;
    }

    public String getTxnType() {
        return txnType;
    }

    public boolean getSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public JSONObject getExtraData() {
        return extraData;
    }

    public long getNonce() {
        return nonce;
    }

    public long getActionFee() {
        return actionFee;
    }

    public boolean isPaid() {
        return paid;
    }

    public String getFeePayer() {
        return feePayer;
    }
}
