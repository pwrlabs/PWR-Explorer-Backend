package Txn;

import Block.Blocks;
import VM.VMs;
import com.github.pwrlabs.dbm.DBM;
import Main.Settings;
import User.Users;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;

public class Txn extends DBM {
    private final long txnFee;

    
    public Txn(String hash, int size, int positionInTheBlock, long blockNumber, byte[] from, String to, long value, long txnFee, byte[] data, String txnType, String nonceOrValidationHash) {
        super(hash);

        if(data != null) {
            store(
                    "size", size,
                    "positionInTheBlock", positionInTheBlock,
                    "blockNumber", blockNumber,
                    "from", Hex.toHexString(from),
                    "to", to,
                    "value", value,
                    "txnFee", txnFee,
                    "data", Hex.toHexString(data),
                    "txnType", txnType,
                    "nonceOrValidationHash", nonceOrValidationHash);
        } else {
            store(
                    "size", size,
                    "positionInTheBlock", positionInTheBlock,
                    "blockNumber", blockNumber,
                    "from", Hex.toHexString(from),
                    "to", to,
                    "value", value,
                    "txnFee", txnFee,
                    "txnType", txnType,
                    "nonceOrValidationHash", nonceOrValidationHash);
        }

        this.txnFee = txnFee;

        if(value > 0) {
            double usdValue = (value * Settings.getPrice());
            BigDecimal result = BigDecimal.valueOf(usdValue).divide(BigDecimal.valueOf((long) Math.pow(10, 11)));
            DecimalFormat df = new DecimalFormat("#.0000");
            BigDecimal formattedResult = new BigDecimal(df.format(result));

            store("amountUsdValue", formattedResult);
        }

        //fee USD Value
        double usdFeeValue = (txnFee * Settings.getPrice());
        BigDecimal feeResult = BigDecimal.valueOf(usdFeeValue).divide(BigDecimal.valueOf((long) Math.pow(10, 11)));
        DecimalFormat feeDf = new DecimalFormat("#.00000000");
        BigDecimal formattedFeeResult = new BigDecimal(feeDf.format(feeResult));

        store("feeUsdValue", formattedFeeResult);

        //store("usdValue", )

        Blocks.getBlock(blockNumber).addTxn(this, positionInTheBlock);
        Users.getUserCreateIfMissing(from).addTxn(this);

        if(txnType.equalsIgnoreCase("transfer")) {
            Users.getUserCreateIfMissing(to).addTxn(this);
        }

        Txns.add(this);
        System.out.println("New txn created: " + hash);
    }

    public Txn(String hash) {
        super(hash);

        txnFee = loadLong("txnFee");

        Blocks.getBlock(getBlockNumber()).addTxn(this, getPositionInTheBlock());
        Users.getUserCreateIfMissing(getFrom()).addTxn(this);

        if(getTxnType().equalsIgnoreCase("transfer")) {
            Users.getUserCreateIfMissing(getTo()).addTxn(this);
        }

        Txns.add(this);
    }

    public String getTxnHash() {
        return id;
    }
    public int getSize() {
        return loadInt("size");
    }
    public long getBlockNumber() {
        return loadLong("blockNumber");
    }
    public long getTimeStamp() {
        return Blocks.getBlock(getBlockNumber()).getTimeStamp();
    }
    public byte[] getFrom() {
        return load("from");
    }
    public String getTo() {
        return loadString("to");
    }
    public long getValue() {
        return loadLong("value");
    }
    public BigDecimal getValueInUsd() {
        return loadBigDec("amountUsdValue");
    }
    public long getTxnFee() {
        return txnFee;
    }
    public BigDecimal getTxnFeeInUsd() {
        return loadBigDec("feeUsdValue");
    }
    public byte[] getData() {
        return load("data");
    }
    public String getTxnType() {
        return loadString("txnType");
    }
    public int getPositionInTheBlock() {
        return loadInt("positionInTheBlock");
    }
    public String getNonceOrValidationHash() {
        return loadString("nonceOrValidationHash");
    }

}
