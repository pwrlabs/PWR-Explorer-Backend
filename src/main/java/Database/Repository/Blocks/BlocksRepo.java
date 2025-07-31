package Database.Repository.Blocks;

import DataModel.Block;
import DataModel.NewTxn;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public interface BlocksRepo {
    void insertBlock(com.github.pwrlabs.pwrj.entities.Block block);

    Block getDbBlock(long blockNumber);

    long getLastStoredBlock();

    void updateLastStoredBlock(long blockNumber);

    long getLatestBlockNumberForFeeRecipient(String feeRecipient);

    String getBlockHash(long blockNumber);

    List<Block> getLastXBlocks(int x);

    List<Block> getLastXBlocks(int pageSize, int page);

    List<NewTxn> getBlockTxns(String blockNumberString);

    JSONObject get24HourBlockStats();

    JSONArray getBlocksCreated(String address, int pageSize, int page);

    int getBlocksSubmitted(String address);

    void incrementSubmittedBlocksCount(com.github.pwrlabs.pwrj.entities.Block block);
}
