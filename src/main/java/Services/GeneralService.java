package Services;

import Core.Cache.CacheManager;
import DataModel.Block;
import DataModel.NewTxn;
import Utils.Settings;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Request;
import spark.Response;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static Database.Queries.getLastXTransactions;
import static Utils.ResponseBuilder.getError;
import static Utils.ResponseBuilder.getSuccess;

public class GeneralService {
    private static final Logger logger = LogManager.getLogger(GeneralService.class);
    private static CacheManager cacheManager;
    private static PWRJ pwrj;

    public static void initialize(PWRJ pwrjInstance) {
        pwrj = pwrjInstance;
        cacheManager = new CacheManager(pwrj);
    }

    public static Object getExplorerInfo(Request request, Response response) {
        getLastXTransactions(5);
        try {
            response.header("Content-Type", "application/json");

            CompletableFuture<JSONArray> blocksFuture = CompletableFuture.supplyAsync(() -> {
                JSONArray blocks = new JSONArray();
                List<Block> blockList = cacheManager.getBlocks(5);
                for (Block block : blockList) {
                    JSONObject object = new JSONObject();
                    object.put("blockHeight", block.blockNumber());
                    object.put("timeStamp", block.timeStamp() / 1000);
                    object.put("txnsCount", block.txnCount());
                    object.put("blockReward", block.blockReward());
                    object.put("blockSubmitter", "0x" + block.blockSubmitter());
                    blocks.put(object);
                }
                return new JSONArray().put(blocks);
            });

            CompletableFuture<JSONArray> txnsFuture = CompletableFuture.supplyAsync(() -> {
                JSONArray txns = new JSONArray();
                List<NewTxn> txnsList = cacheManager.getRecentTxns(5);
                for (NewTxn txn : txnsList) {
                    if (txn == null) continue;
                    JSONObject object = new JSONObject();
                    object.put("txnHash", txn.hash());
                    object.put("timeStamp", txn.timestamp() / 1000);
                    object.put("from", "0x" + txn.fromAddress());
                    object.put("to", txn.toAddress());
                    object.put("value", txn.value());
                    txns.put(object);
                }
                return new JSONArray().put(txns);
            });

            CompletableFuture<JSONArray> otherDataFuture = CompletableFuture.supplyAsync(() -> {
                Instant start = Instant.now();
                JSONObject data = new JSONObject();

                data.put("fourteenDaysTxn", cacheManager.getFourteenDaysTxn());
                data.put("totalTransactionsCount", cacheManager.getTotalTransactionCount());
                data.put("validators", cacheManager.getActiveValidatorsCount());
                data.put("tps", cacheManager.getAverageTps(100, cacheManager.getBlocksCount()));

                long duration = Duration.between(start, Instant.now()).toMillis();
                return new JSONArray().put(data).put(duration);
            });

            JSONArray blocksResult = blocksFuture.get();
            JSONArray txnsResult = txnsFuture.get();
            JSONArray otherData = otherDataFuture.get();
            JSONArray arr = blocksResult != null ? blocksResult.optJSONArray(0) : null;
            JSONObject otherDataObj = (JSONObject) otherData.get(0);

            long blocksCount = 0;
            if (arr != null && !arr.isEmpty()) {
                blocksCount = arr.optJSONObject(0).optLong("blockHeight", 0);
            }

            return getSuccess(
                    "price", Settings.getPrice(),
                    "priceChange", 2.5,
                    "marketCap", 1000000000L,
                    "totalTransactionsCount", otherDataObj.getLong("totalTransactionsCount"),
                    "blocksCount", blocksCount,
                    "validators", otherDataObj.getInt("validators"),
                    "tps", otherDataObj.getDouble("tps"),
                    "txns", txnsResult.getJSONArray(0),
                    "blocks", blocksResult != null ? blocksResult.getJSONArray(0) : 0,
                    "fourteenDaysTxn", otherDataObj.get("fourteenDaysTxn")
            );
        } catch (Exception e) {
            logger.error("An error occurred while fetching explorer info: ", e);
            return getError(response, e.getMessage());
        }
    }

    public static Object getDailyStats(Request request, Response response) {
        try {
            response.header("Content-Type", "application/json");

            JSONArray blocks = new JSONArray();
            JSONArray txns = new JSONArray();

            List<Block> blockList = cacheManager.getBlocks(5);
            for (Block block : blockList) {
                JSONObject object = new JSONObject();
                object.put("blockNumber", block.blockNumber());
                object.put("blockHeight", cacheManager.getBlocksCount());
                object.put("timeStamp", block.timeStamp() / 1000);
                object.put("txnsCount", block.txnCount());
                object.put("blockReward", block.blockReward());
                object.put("blockSubmitter", "0x" + block.blockSubmitter());

                blocks.put(object);
            }

            List<NewTxn> txnsList = cacheManager.getRecentTxns(5);
            for (NewTxn txn : txnsList) {
                if (txn == null) continue;
                JSONObject object = new JSONObject();
                object.put("txnHash", txn.hash());
                object.put("timeStamp", txn.timestamp() / 1000);
                object.put("from", "0x" + txn.fromAddress());
                object.put("to", txn.toAddress());
                object.put("value", txn.value());

                txns.put(object);
            }

            JSONObject responseObject = new JSONObject();
            responseObject.put("price", Settings.getPrice());
            responseObject.put("priceChange", 2.5);
            responseObject.put("marketCap", 1000000000L);
            responseObject.put("totalTransactionsCount", cacheManager.getTxnsCountPast24Hours());
            responseObject.put("blocksCount", cacheManager.getBlocksCount());
            responseObject.put("validators", cacheManager.getActiveValidatorsCount());
            responseObject.put("tps", cacheManager.getAverageTps(100, cacheManager.getBlocksCount()));
            responseObject.put("txns", txns);
            responseObject.put("blocks", blocks);
            responseObject.put("avgTxnFeeChange", cacheManager.getAvgTxnFeePercentageChange());
            responseObject.put("totalTxnFeesChange", cacheManager.getTotalTxnFeesPercentageChange());
            responseObject.put("avgTxnFeePast24Hours", cacheManager.getAverageTxnFeePast24Hours());
            responseObject.put("totalTxnFeesPast24Hours", cacheManager.getTotalTxnsFeesPast24Hours());
            responseObject.put("txnCountChange", cacheManager.getTxnCountPercentageChange());

            return responseObject.toString();
        } catch (Exception e) {
            logger.error("An error occurred while fetching daily stats: ", e);
            return getError(response, e.getLocalizedMessage());
        }
    }

    public static Object getBalanceOf(Request request, Response response) {
        try {
            response.header("Content-Type", "application/json");

            String address = request.queryParams("userAddress").toLowerCase();

            long balance = pwrj.getBalanceOfAddress(address);
            long usdValue = balance * Settings.getPrice();
            BigDecimal usdValueBigDec = new BigDecimal(usdValue).divide(BigDecimal.valueOf((long) Math.pow(10, 11)));

            return getSuccess("balance", balance, "balanceUsdValue", usdValueBigDec);
        } catch (Exception e) {
            return getError(response, "Failed to fetch balance of user balance due to an internal error " + e.getLocalizedMessage());
        }
    }

    public static Object getNonceOfUser(Request request, Response response) {
        try {
            response.header("Content-Type", "application/json");
            String userAddress = request.queryParams("userAddress");

            return getSuccess("nonce", pwrj.getNonceOfAddress(userAddress));
        } catch (Exception e) {
            return getError(response, "Failed to get nonce of user: " + e.getLocalizedMessage());
        }
    }
}