package Block;

import java.util.ArrayList;
import java.util.List;

import Txn.Txn;
import com.github.pwrlabs.dbm.DBM;
import org.bouncycastle.util.encoders.Hex;

public class Block extends DBM {

    private final long timeStamp;
    private Txn[] txns;

    public Block(String blockNumber, long timeStamp, byte[] blockSubmitter, long blockReward, int blockSize, int txnCount) {
        super(blockNumber);

        store("timeStamp", timeStamp,
                "blockSubmitter", Hex.toHexString(blockSubmitter),
                "blockReward", blockReward,
                "blockSize", blockSize,
                "txnCount", txnCount);

        this.timeStamp = timeStamp;
        txns = new Txn[txnCount];

        Blocks.add(this);
    }

    public Block(String blockNumber) {
        super(blockNumber);

        timeStamp = loadLong("timeStamp");
        txns = new Txn[getTxnsCount()];

        Blocks.add(this);
    }

    public void addTxn(Txn txn, int positionInTheBlock) {
        txns[positionInTheBlock] = txn;
        System.out.println("New txn added to block: " + id + " at position: " + positionInTheBlock);
    }

    public long getTimeStamp() {
        return timeStamp;
    }
    public byte[] getBlockSubmitter() {
        return load("blockSubmitter");
    }
    public long getBlockReward() {
        return loadLong("blockReward");
    }
    public int getBlockSize() {
        return loadInt("blockSize");
    }
    public int getTxnsCount() {
        if(txns == null) return loadInt("txnCount");
        else return txns.length;
    }
    public Txn[] getTxns() {
        return txns;
    }

}
