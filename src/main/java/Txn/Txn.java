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
    private String hash;
    private int size;
    private int positionInBlock;
    private long blockNumber;
    private byte[] fromAddress;
    private String toAddress;
    private long timestamp;
    private long value;
    private long txnFee;
    private byte[] txnData;
    private String txnType;
    private String nonceOrValidationHash;
    private boolean success;
    private String errorMessage;

    public Txn(String hash, int size, int positionInBlock, long blockNumber, byte[] fromAddress, String toAddress,
               long timestamp, long value, long txnFee, byte[] txnData, String txnType, String nonceOrValidationHash,
               boolean success, String errorMessage) {
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
        this.nonceOrValidationHash = nonceOrValidationHash;
        this.success = success;
        this.errorMessage = errorMessage;
    }


    public Boolean getSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
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

    public byte[] getFromAddress() {
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

    public String getNonceOrValidationHash() {
        return nonceOrValidationHash;
    }
}


