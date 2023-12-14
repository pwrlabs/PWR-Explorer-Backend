package DTOs;

import java.math.BigDecimal;
import java.util.Date;

public class TransactionDTO {

    private long positionInTheBlock;
    private String nonceOrValidationHash;
    private long size;
    private String rawTxn;
    private long value;
    private String from;
    private String to;
    private long txnFee;
    private String type;
    private String hash;

    private String data;
    private long feeUsdValue;
    private long amountUsdValue;

    private long blockNumber;

    private Date timeStamp;

    // Constructors, getters, and setters

    public long getPositionInTheBlock() {
        return positionInTheBlock;
    }

    public void setPositionInTheBlock(long positionInTheBlock) {
        this.positionInTheBlock = positionInTheBlock;
    }

    public String getNonceOrValidationHash() {
        return nonceOrValidationHash;
    }

    public void setNonceOrValidationHash(String nonceOrValidationHash) {
        this.nonceOrValidationHash = nonceOrValidationHash;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getRawTxn() {
        return rawTxn;
    }

    public void setRawTxn(String rawTxn) {
        this.rawTxn = rawTxn;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public long getTxnFee() {
        return txnFee;
    }

    public void setTxnFee(long txnFee) {
        this.txnFee = txnFee;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public long getFeeUsdValue() {
        return feeUsdValue;
    }

    public void setFeeUsdValue(long feeUsdValue) {
        this.feeUsdValue = feeUsdValue;
    }

    public long getAmountUsdValue() {
        return amountUsdValue;
    }

    public void setAmountUsdValue(long amountUsdValue) {
        this.amountUsdValue = amountUsdValue;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(long blockNumber) {
        this.blockNumber = blockNumber;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Date timeStamp) {
        this.timeStamp = timeStamp;
    }

}