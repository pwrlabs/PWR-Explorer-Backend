package Services;

import Core.Cache.CacheManager;
import DataModel.Block;
import DataModel.NewTxn;
import Database.Constants.Repository.Blocks.BlocksRepo;
import Database.Constants.Repository.Blocks.BlocksRepoImpl;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Request;
import spark.Response;

import java.util.List;

import static Utils.Helpers.*;
import static Utils.ResponseBuilder.getError;
import static Utils.ResponseBuilder.getSuccess;

public class BlockService {
    private static CacheManager cacheManager;
    private static BlocksRepo blocksRepo;

    public static void initialize(PWRJ pwrjInstance) {
        cacheManager = new CacheManager(pwrjInstance);
        blocksRepo = new BlocksRepoImpl();
    }

    public static Object getLatestBlocks(Request request, Response response) {
        try {
            response.header("Content-Type", "application/json");
            int count = validateAndParseCountParam(request.queryParams("count"), response);
            int page = validateAndParsePageParam(request.queryParams("page"), response);

            int offset = (page - 1) * count;

            long totalBlockCount = cacheManager.getBlocksCount();

            JSONArray blocksArray = new JSONArray();
            List<Block> blockList = blocksRepo.getLastXBlocks(count, offset);

            for (Block block : blockList) {
                JSONObject object = new JSONObject();
                object.put("blockHeight", block.blockNumber());
                object.put("timeStamp", block.timeStamp() / 1000);
                object.put("txnsCount", block.txnCount());
                object.put("blockReward", block.blockReward());
                object.put("blockSubmitter", returnHexStringWith0x(block.blockSubmitter()));
                blocksArray.put(object);
            }

            JSONObject metadata = createPaginationMetadata(totalBlockCount, page, count);

            JSONObject blockStats = cacheManager.get24HourBlockStats();

            return getSuccess(
                    "networkUtilizationPast24Hours", blockStats.getDouble("networkUtilization"),
                    "averageBlockSizePast24Hours", blockStats.getInt("averageBlockSize"),
                    "totalBlockRewardsPast24Hours", blockStats.getLong("totalRewards"),
                    "blocks", blocksArray,
                    "metadata", metadata);
        } catch (Exception e) {
            return getError(response, "Failed to get latest blocks: " + e.getLocalizedMessage());
        }
    }

    public static Object getBlockDetails(Request request, Response response) {
        try {
            response.header("Content-Type", "application/json");

            long blockNumber = Long.parseLong(request.queryParams("blockNumber"));

            Block block = blocksRepo.getDbBlock(blockNumber);
            if (block == null) return getError(response, "Invalid DataModel.Block Number");

            return getSuccess(
                    "blockHeight", blockNumber,
                    "blockHash", returnHexStringWith0x(block.blockHash()),
                    "timeStamp", block.timeStamp() / 1000,
                    "txnsCount", block.txnCount(),
                    "blockSize", block.blockSize(),
                    "blockReward", block.blockReward(),
                    "blockSubmitter", returnHexStringWith0x(block.blockSubmitter()),
                    "blockConfirmations", cacheManager.getBlocksCount() - blockNumber);
        } catch (Exception e) {
            return getError(response, "Failed to get block details: " + e.getLocalizedMessage());
        }
    }

    public static Object getBlock(Request request, Response response) {
        try {
            response.header("Content-Type", "application/json");

            long blockNumber = Long.parseLong(request.queryParams("blockNumber"));

            String blockHash = blocksRepo.getBlockHash(blockNumber);
            if (blockHash == null) {
                return getError(response, "Invalid DataModel.Block Number");
            }

            return getSuccess(
                    "blockNumber", blockNumber,
                    "blockHash", blockHash
            );
        } catch (Exception e) {
            return getError(response, e.getLocalizedMessage());
        }
    }

    public static Object getBlockTransactions(Request request, Response response) {
        try {
            response.header("Content-Type", "application/json");

            String blockNumber = request.queryParams("blockNumber");
            int count = validateAndParseCountParam(request.queryParams("count"), response);
            int page = validateAndParsePageParam(request.queryParams("page"), response);

            //Metadata variables
            int previousTxnsCount = (page - 1) * count;
            int totalTxnCount;

            Block block = blocksRepo.getDbBlock(Long.parseLong(blockNumber));
            if (block == null) return getError(response, "Invalid DataModel.Block Number");

            int txnsCount = 0;
            JSONArray txns = new JSONArray();
            List<NewTxn> txnsArray = blocksRepo.getBlockTxns(blockNumber);
            totalTxnCount = txnsArray.size();

            for (int t = previousTxnsCount; t < txnsArray.size(); ++t) {
                NewTxn txn = txnsArray.get(t);
                if (txn == null) continue;
                if (txnsCount == count) break;

                JSONObject object = new JSONObject();

                object.put("txnHash", returnHexStringWith0x(txn.hash()));
                object.put("txnType", txn.txnType());
                object.put("blockNumber", blockNumber);
                object.put("timeStamp", block.timeStamp() / 1000);
                object.put("from", txn.fromAddress());
                object.put("to", txn.toAddress());
                object.put("value", txn.value());

                txns.put(object);
                ++txnsCount;
            }

            JSONObject metadata = createPaginationMetadata(totalTxnCount, page, count);

            return getSuccess("metadata", metadata, "transactions", txns);
        } catch (Exception e) {
            return getError(response, e.getLocalizedMessage());
        }
    }

    public static Object getBlocksCreated(Request request, Response response) throws Exception {
        response.type("application/json");

        String address = request.queryParams("validatorAddress");
        int count = validateAndParseCountParam(request.queryParams("count"), response);
        int page = validateAndParsePageParam(request.queryParams("page"), response);
        int offset = (page - 1) * count;

        int totalBlocksCount;

        try {
            JSONArray blocks = blocksRepo.getBlocksCreated(address.toLowerCase().substring(2), count, offset);

            if (blocks.isEmpty()) totalBlocksCount = 0;
            else totalBlocksCount = blocksRepo.getBlocksSubmitted(address.toLowerCase().substring(2));

            JSONObject metadata = createPaginationMetadata(totalBlocksCount, page, count);

            return getSuccess("blocks", blocks,
                    "metadata", metadata
            );
        } catch (Exception e) {
            return getError(response, e.getLocalizedMessage());
        }
    }

    private static JSONObject createPaginationMetadata(long totalCount, int currentPage, int itemsPerPage) {
        int totalPages = (int) Math.ceil((double) totalCount / itemsPerPage);
        int startIndex = (currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, (int)totalCount);

        JSONObject metadata = new JSONObject();
        metadata.put("totalPages", totalPages);
        metadata.put("currentPage", currentPage);
        metadata.put("itemsPerPage", itemsPerPage);
        metadata.put("totalItems", totalCount);
        metadata.put("startIndex", startIndex);
        metadata.put("endIndex", endIndex);
        metadata.put("nextPage", currentPage < totalPages ? currentPage + 1 : -1);
        metadata.put("previousPage", currentPage > 1 ? currentPage - 1 : -1);

        return metadata;
    }
}