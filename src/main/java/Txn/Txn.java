package Txn;

import Block.Blocks;
import VM.VMs;
import com.github.pwrlabs.dbm.DBM;
import Main.Settings;
import User.Users;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;

import static Core.Sql.Queries.getDbBlock;

public class Txn {
    private static final Logger logger = LogManager.getLogger(Txn.class);
    private String hash;
    private int size;
    private int positionInTheBlock;
    private long blockNumber;
    private byte[] from;
    private String to;
    private long value;
    private long txnFee;
    private byte[] data;
    private String txnType;
    private String nonceOrValidationHash;
    private  BigDecimal valueInUsd;
    private BigDecimal txnFeeInUsd;

    
    public Txn(String hash, int size, int positionInTheBlock, long blockNumber, byte[] from, String to, long value, long txnFee, byte[] data, String txnType, String nonceOrValidationHash) {
        this.hash = hash;
        this.size = size;
        this.positionInTheBlock = positionInTheBlock;
        this.blockNumber = blockNumber;
        this.from = from;
        this.to = to;
        this.value = value;
        this.txnFee = txnFee;
        this.data = data;
        this.txnType = txnType;
        this.nonceOrValidationHash = nonceOrValidationHash;

        Blocks.getBlock(blockNumber).addTxn(this, positionInTheBlock);
        Users.getUserCreateIfMissing(from).addTxn(this);

        if(txnType.equalsIgnoreCase("transfer")) {
            Users.getUserCreateIfMissing(to).addTxn(this);
        }

        Txns.add(this);
        logger.info("New txn created: {}", hash);
    }


    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getPositionInTheBlock() {
        return positionInTheBlock;
    }

    public void setPositionInTheBlock(int positionInTheBlock) {
        this.positionInTheBlock = positionInTheBlock;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(long blockNumber) {
        this.blockNumber = blockNumber;
    }

    public byte[] getFrom() {
        return from;
    }

    public void setFrom(byte[] from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    public long getTxnFee() {
        return txnFee;
    }

    public void setTxnFee(long txnFee) {
        this.txnFee = txnFee;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public String getNonceOrValidationHash() {
        return nonceOrValidationHash;
    }

    public void setNonceOrValidationHash(String nonceOrValidationHash) {
        this.nonceOrValidationHash = nonceOrValidationHash;
    }

    public BigDecimal getValueInUsd() {
        return valueInUsd;
    }

    public void setValueInUsd(BigDecimal valueInUsd) {
        this.valueInUsd = valueInUsd;
    }

    public BigDecimal getTxnFeeInUsd() {
        return txnFeeInUsd;
    }

    public void setTxnFeeInUsd(BigDecimal txnFeeInUsd) {
        this.txnFeeInUsd = txnFeeInUsd;
    }

    public long getTimeStamp() {
        return getDbBlock(blockNumber+"").getTimeStamp();
    }

    //    public String getTxnHash() {
//        return id;
//    }
//    public int getSize() {
//        return loadInt("size");
//    }
//    public long getBlockNumber() {
//        return loadLong("blockNumber");
//    }
//    public long getTimeStamp() {
//        return Blocks.getBlock(getBlockNumber()).getTimeStamp();
//    }
//    public byte[] getFrom() {
//        return load("from");
//    }
//    public String getTo() {
//        return loadString("to");
//    }
//    public long getValue() {
//        return loadLong("value");
//    }
//    public BigDecimal getValueInUsd() {
//        return loadBigDec("amountUsdValue");
//    }
//    public long getTxnFee() {
//        return txnFee;
//    }
//    public BigDecimal getTxnFeeInUsd() {
//        return loadBigDec("feeUsdValue");
//    }
//    public byte[] getData() {
//        return load("data");
//    }
//    public String getTxnType() {
//        return loadString("txnType");
//    }
//    public int getPositionInTheBlock() {
//        return loadInt("positionInTheBlock");
//    }
//    public String getNonceOrValidationHash() {
//        return loadString("nonceOrValidationHash");
//    }

}
