package Block;

import Txn.NewTxn;
import Txn.Txns;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static Core.Sql.Queries.getBlockTxns;

public class Block {
    private static final Logger logger = LogManager.getLogger(Block.class);
    private static final int MAX_TRANSACTIONS_IN_MEMORY = 1000;

    private String blockNumber;
    private final long timeStamp;
    private String blockSubmitter;
    private long blockReward;
    private int blockSize;
    private int txnCount;

    // Use lazy loading for transactions
    private volatile List<NewTxn> txns;
    private final Object txnLock = new Object();

    // Keep track of number of blocks in memory
    private static final AtomicInteger activeBlocks = new AtomicInteger(0);

    public Block(String blockNumber, long timeStamp, String blockSubmitter,
                 long blockReward, int blockSize, int txnCount) {
        this.blockNumber = blockNumber;
        this.timeStamp = timeStamp;
        this.blockSubmitter = blockSubmitter;
        this.blockReward = blockReward;
        this.blockSize = blockSize;
        this.txnCount = txnCount;

        // Don't load transactions in constructor
        activeBlocks.incrementAndGet();
        logger.debug("Created new Block {}. Active blocks: {}", blockNumber, activeBlocks.get());

        // Add to blocks collection
        //Blocks.add(this);
    }

    public void addTxn(NewTxn txn, int positionInTheBlock) {
        if (txn == null) {
            logger.warn("Attempted to add null transaction to block {}", blockNumber);
            return;
        }

        initializeTransactionsIfNeeded();

        try {
            if (positionInTheBlock >= 0 && positionInTheBlock <= txns.size()) {
                txns.add(positionInTheBlock, txn);
                txnCount = txns.size();
                logger.debug("Added transaction at position {} in block {}", positionInTheBlock, blockNumber);
            } else {
                logger.warn("Invalid position {} for transaction in block {}",
                        positionInTheBlock, blockNumber);
            }
        } catch (Exception e) {
            logger.error("Error adding transaction to block {}: {}", blockNumber, e.getMessage(), e);
        }
    }

    private void initializeTransactionsIfNeeded() {
        if (txns == null) {
            synchronized (txnLock) {
                if (txns == null) {
                    try {
                        logger.debug("Loading transactions for block {}", blockNumber);
                        List<NewTxn> loadedTxns = getBlockTxns(blockNumber);

                        if (loadedTxns == null) {
                            loadedTxns = new ArrayList<>();
                            logger.warn("No transactions loaded for block {}, initializing empty list", blockNumber);
                        }

                        // If too many transactions, warn about potential memory issues
                        if (loadedTxns.size() > MAX_TRANSACTIONS_IN_MEMORY) {
                            logger.warn("Block {} contains {} transactions, which exceeds the recommended limit of {}",
                                    blockNumber, loadedTxns.size(), MAX_TRANSACTIONS_IN_MEMORY);
                        }

                        txns = new ArrayList<>(loadedTxns);
                        txnCount = txns.size();
                    } catch (Exception e) {
                        logger.error("Error initializing transactions for block {}: {}",
                                blockNumber, e.getMessage(), e);
                        txns = new ArrayList<>(); // Initialize empty list on error
                    }
                }
            }
        }
    }

    // Cleanup method to be called when block is no longer needed
    public void cleanup() {
        synchronized (txnLock) {
            if (txns != null) {
                txns.clear();
                txns = null;
            }
        }
        activeBlocks.decrementAndGet();
        logger.debug("Cleaned up Block {}. Active blocks: {}", blockNumber, activeBlocks.get());
    }

    // Getters and setters
    public String getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(String blockNumber) {
        this.blockNumber = blockNumber;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public String getBlockSubmitter() {
        return blockSubmitter;
    }

    public void setBlockSubmitter(String blockSubmitter) {
        this.blockSubmitter = blockSubmitter;
    }

    public long getBlockReward() {
        return blockReward;
    }

    public void setBlockReward(long blockReward) {
        this.blockReward = blockReward;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
    }

    public int getTxnCount() {
        return txnCount;
    }

    public void setTxnCount(int txnCount) {
        this.txnCount = txnCount;
    }

    public NewTxn[] getTxns() {
        initializeTransactionsIfNeeded();
        return txns.toArray(new NewTxn[0]);
    }

    // Get active blocks count - useful for monitoring
    public static int getActiveBlocksCount() {
        return activeBlocks.get();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            cleanup();
        } finally {
            super.finalize();
        }
    }

    @Override
    public String toString() {
        return "Block{" +
                "blockNumber='" + blockNumber + '\'' +
                ", timeStamp=" + timeStamp +
                ", blockSubmitter='" + blockSubmitter + '\'' +
                ", blockReward=" + blockReward +
                ", blockSize=" + blockSize +
                ", txnCount=" + txnCount +
                '}';
    }
}