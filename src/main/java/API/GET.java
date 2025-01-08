package API;


import Block.Block;
import Block.Blocks;
import Core.Cache.CacheManager;
import DailyActivity.Stats;
import Main.Config;
import Main.Settings;
import Txn.NewTxn;
import Txn.Txns;
import User.User;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import com.github.pwrlabs.pwrj.record.delegator.Delegator;
import com.github.pwrlabs.pwrj.record.transaction.PayableVmDataTransaction;
import com.github.pwrlabs.pwrj.record.transaction.Transaction;
import com.github.pwrlabs.pwrj.record.transaction.VmDataTransaction;
import com.github.pwrlabs.pwrj.record.validator.Validator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static Core.Sql.Queries.*;
import static Core.Sql.Queries.getFourteenDaysTxn;
import static spark.Spark.get;

public class GET {
    private static final Logger logger = LogManager.getLogger(GET.class);
    private static CacheManager cacheManager;

    public static void run(PWRJ pwrj) {
        cacheManager = new CacheManager(pwrj);
        get("/test/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");
                return "Server is on";
            } catch (Exception e) {
                return getError(response, "Error in test request: " + e.getLocalizedMessage());
            }
        });

        //Explorer Calls
        get("/explorerInfo/", (request, response) -> {
            Instant serverStart = Instant.now();
            try {
                response.header("Content-Type", "application/json");

                CompletableFuture<JSONArray> blocksFuture = CompletableFuture.supplyAsync(() -> {
                    Instant start = Instant.now();
                    JSONArray blocks = new JSONArray();
                    List<Block> blockList = cacheManager.getBlocks(5);
                    for (Block block : blockList) {
                        JSONObject object = new JSONObject();
                        object.put("blockHeight", block.getBlockNumber());
                        object.put("timeStamp", block.getTimeStamp() / 1000);
                        object.put("txnsCount", block.getTxnCount());
                        object.put("blockReward", block.getBlockReward());
                        object.put("blockSubmitter", "0x" + block.getBlockSubmitter());
                        blocks.put(object);
                    }
                    long duration = Duration.between(start, Instant.now()).toMillis();
                    return new JSONArray().put(blocks).put(duration);
                });

                CompletableFuture<JSONArray> txnsFuture = CompletableFuture.supplyAsync(() -> {
                    Instant start = Instant.now();
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
                    long duration = Duration.between(start, Instant.now()).toMillis();
                    return new JSONArray().put(txns).put(duration);
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
                        "blocksTime", blocksResult != null ? blocksResult.get(1) : 0,
                        "txnsTime", txnsResult.get(1),
                        "fourteenDaysTxn", otherDataObj.get("fourteenDaysTxn"),
                        "otherDataTime", otherData.get(1),
                        "serverDuration", Duration.between(serverStart, Instant.now()).toMillis()
                );
            } catch (Exception e) {
                logger.error("An error occurred while fetching explorer info: ", e);
                return getError(response, e.getMessage());
            }
        });

        get("/dailyStats", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                // Initialize JSON objects for the response
                JSONArray blocks = new JSONArray();
                JSONArray txns = new JSONArray();

                // Fetch the latest 5 blocks (assuming a function getLastXBlocks exists)
                List<Block> blockList = cacheManager.getBlocks(5);
                for (Block block : blockList) {
                    JSONObject object = new JSONObject();
                    object.put("blockNumber", block.getBlockNumber());
                    object.put("blockHeight", cacheManager.getBlocksCount());
                    object.put("timeStamp", block.getTimeStamp() / 1000);
                    object.put("txnsCount", block.getTxnCount());
                    object.put("blockReward", block.getBlockReward());
                    object.put("blockSubmitter", "0x" + block.getBlockSubmitter());

                    blocks.put(object);
                }

                // Fetch the latest 5 transactions (assuming a function getLastXTransactions exists)
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

                // Build the response JSON
                JSONObject responseObject = new JSONObject();
                responseObject.put("price", Settings.getPrice());
                responseObject.put("priceChange", 2.5);  // example value
                responseObject.put("marketCap", 1000000000L);  // example value
                responseObject.put("totalTransactionsCount", getTransactionCountPast24Hours());
                responseObject.put("blocksCount", cacheManager.getBlocksCount());
                responseObject.put("validators", cacheManager.getActiveValidatorsCount());  // example value
                responseObject.put("tps", cacheManager.getAverageTps(100, cacheManager.getBlocksCount()));  // example value
                responseObject.put("txns", txns);
                responseObject.put("blocks", blocks);
                responseObject.put("avgTxnFeeChange", getAverageTransactionFeePercentageChange());
                responseObject.put("totalTxnFeesChange", getTotalTransactionFeesPercentageChange());
                responseObject.put("avgTxnFeePast24Hours", getAverageTransactionFeePast24Hours());
                responseObject.put("totalTxnFeesPast24Hours", getTotalTransactionFeesPast24Hours());
                responseObject.put("txnCountChange", getTransactionCountPercentageChangeComparedToPreviousDay());

                // Return the response as a JSON string
                return responseObject.toString();

            } catch (Exception e) {
                logger.error("An error occurred while fetching daily stats: ", e);
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/latestBlocks/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");
                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                int offset = (page - 1) * count;

                long latestBlockNumber = cacheManager.getBlocksCount();
                int totalBlockCount = (int) latestBlockNumber;
                int totalPages = (int) Math.ceil((double) totalBlockCount / count);

                JSONArray blocksArray = new JSONArray();
                List<Block> blockList = getLastXBlocks(count, offset);

                for (Block block : blockList) {
                    JSONObject object = new JSONObject();
                    object.put("blockHeight", block.getBlockNumber());
                    object.put("timeStamp", block.getTimeStamp() / 1000);
                    object.put("txnsCount", block.getTxnCount());
                    object.put("blockReward", block.getBlockReward());
                    object.put("blockSubmitter", "0x" + block.getBlockSubmitter());
                    blocksArray.put(object);
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalBlocks", totalBlockCount);
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", count);
                metadata.put("totalItems", totalBlockCount);
                metadata.put("startIndex", offset + 1);
                metadata.put("endIndex", Math.min(offset + count, totalBlockCount));
                metadata.put("nextPage", page < totalPages ? page + 1 : -1);
                metadata.put("previousPage", page > 1 ? page - 1 : -1);

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
        });
        get("/blockDetails/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                long blockNumber = Long.parseLong(request.queryParams("blockNumber"));

                Block block = getDbBlock(blockNumber);
                if (block == null) return getError(response, "Invalid Block Number");

                return getSuccess(
                        "blockHeight", blockNumber,
                        "blockHash", "0x" + block.getBlockHash(),
                        "timeStamp", block.getTimeStamp() / 1000,
                        "txnsCount", block.getTxnCount(),
                        "blockSize", block.getBlockSize(),
                        "blockReward", block.getBlockReward(),
                        "blockSubmitter", "0x" + block.getBlockSubmitter(),
                        "blockConfirmations", cacheManager.getBlocksCount() - blockNumber);
            } catch (Exception e) {
                return getError(response, "Failed to get block details: " + e.getLocalizedMessage());
            }
        });
        get("/block/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                long blockNumber = Long.parseLong(request.queryParams("blockNumber"));

                String blockHash = getBlockHash(blockNumber);
                if (blockHash == null) {
                    return getError(response, "Invalid Block Number");
                }

                return getSuccess(
                        "blockNumber", blockNumber,
                        "blockHash", blockHash
                );
            } catch (Exception e) {
                return getError(response, e.getLocalizedMessage());
            }
        });
        get("/blockTransactions/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                long blockNumber = Long.parseLong(request.queryParams("blockNumber"));
                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                //Metadata variables
                int previousTxnsCount = (page - 1) * count;
                int totalTxnCount, totalPages;

                Block block = getDbBlock(blockNumber);
                if (block == null) return getError(response, "Invalid Block Number");

                int txnsCount = 0;
                JSONArray txns = new JSONArray();
                NewTxn[] txnsArray = block.getTxns();

                if (txnsArray == null) totalTxnCount = 0;
                else totalTxnCount = txnsArray.length;

                totalPages = totalTxnCount / count;
                if (totalTxnCount % count != 0) ++totalPages;

                assert txnsArray != null;
                for (int t = previousTxnsCount; t < txnsArray.length; ++t) {
                    NewTxn txn = txnsArray[t];
                    //Txn txn = txnsArray[t];
                    if (txn == null) continue;
                    if (txnsCount == count) break;

                    JSONObject object = new JSONObject();

                    object.put("txnHash", txn.hash());
                    object.put("txnType", txn.txnType());
                    object.put("blockNumber", blockNumber);
                    object.put("timeStamp", block.getTimeStamp() / 1000);
                    object.put("from", txn.fromAddress());
                    object.put("to", txn.toAddress());
                    object.put("value", txn.value());

                    txns.put(object);
                    ++txnsCount;
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", count);
                metadata.put("totalItems", totalTxnCount);
                metadata.put("startIndex", previousTxnsCount + 1);
                if (previousTxnsCount + count <= totalTxnCount) metadata.put("endIndex", previousTxnsCount + count);
                else metadata.put("endIndex", totalTxnCount);

                if (page < totalPages) metadata.put("nextPage", page + 1);
                else metadata.put("nextPage", -1);

                if (page > 1) metadata.put("previousPage", page - 1);
                else metadata.put("previousPage", -1);

                return getSuccess("metadata", metadata, "transactions", txns);
            } catch (Exception e) {
                return getError(response, e.getLocalizedMessage());
            }
        });
        get("/blocksCreated/", ((request, response) -> {
            response.type("application/json");
            String address = request.queryParams("validatorAddress");
            int count = Integer.parseInt(request.queryParams("count"));
            int page = Integer.parseInt(request.queryParams("page"));
            int offset = (page - 1) * count;

            // Metadata variables
            int previousBlocksCount = (page - 1) * count;
            int totalBlocksCount, totalPages;

            try {
                JSONArray blocks = getBlocksCreated(address.toLowerCase().substring(2), count, offset);
                JSONObject metadata = new JSONObject();

                if (blocks.isEmpty()) totalBlocksCount = 0;
                else totalBlocksCount = getBlocksSubmitted(address.toLowerCase().substring(2));

                totalPages = totalBlocksCount / count;
                if (totalBlocksCount % count != 0) ++totalPages;

                metadata.put("totalBlocksCreatedByValidator", totalBlocksCount);
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", count);
                metadata.put("totalItems", totalBlocksCount);
                metadata.put("startIndex", previousBlocksCount + 1);

                if (previousBlocksCount + count <= totalBlocksCount)
                    metadata.put("endIndex", previousBlocksCount + count);
                else metadata.put("endIndex", totalBlocksCount);

                if (page < totalPages) metadata.put("nextPage", page + 1);
                else metadata.put("nextPage", -1);

                if (page > 1) metadata.put("previousPage", page - 1);
                else metadata.put("previousPage", -1);

                return getSuccess("blocks", blocks,
                        "metadata", metadata
                );
            } catch (Exception e) {
                return getError(response, e.getLocalizedMessage());
            }
        }));

        get("/latestTransactions/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                int limit = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));
                int offset = (page - 1) * limit;

                List<NewTxn> txnsList = getTransactions(limit, offset);

                //Metadata variables
                int previousTxnsCount = (page - 1) * limit;
                int totalTxnCount = getTotalTransactionCount();
                int totalPages = totalTxnCount / limit;
                if (totalTxnCount % limit != 0) ++totalPages;

                JSONArray transactions = new JSONArray();

                BigDecimal ONE_PWR_TO_USD = BigDecimal.ONE;

                for (NewTxn txn : txnsList) {
                    Transaction subTxn = pwrj.getTransactionByHash(txn.hash());

                    BigDecimal sparks = BigDecimal.valueOf(txn.txnFee());
                    BigDecimal pwrAmount = sparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);

                    BigDecimal feeValueInUSD = pwrAmount.multiply(ONE_PWR_TO_USD).setScale(9, RoundingMode.HALF_EVEN);

                    JSONObject object = new JSONObject();
                    object.put("txnHash", txn.hash());
                    object.put("txnType", txn.txnType());
                    object.put("block", txn.blockNumber());
                    object.put("timeStamp", txn.timestamp() / 1000);
                    object.put("from", "0x" + txn.fromAddress());
                    object.put("to", txn.toAddress());
                    object.put("value", txn.value());
                    object.put("txnFee", txn.txnFee());
                    object.put("valueInUsd", txn.value());
                    object.put("txnFeeInUsd", feeValueInUSD);
                    object.put("nonceOrValidationHash", subTxn.getNonce());
                    object.put("positionInBlock", txn.positionInBlock());
                    transactions.put(object);
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", limit);
                metadata.put("totalItems", totalTxnCount);
                metadata.put("startIndex", previousTxnsCount + 1);
                if (previousTxnsCount + limit <= totalTxnCount) {
                    metadata.put("endIndex", previousTxnsCount + limit);
                } else {
                    metadata.put("endIndex", totalTxnCount);
                }

                if (page < totalPages) {
                    metadata.put("nextPage", page + 1);
                } else {
                    metadata.put("nextPage", -1);
                }

                if (page > 1) {
                    metadata.put("previousPage", page - 1);
                } else {
                    metadata.put("previousPage", -1);
                }

                return getSuccess("metadata", metadata,
                        "transactionCountPast24Hours", Txns.getTxnCountPast24Hours(),
                        "transactionCountPercentageChangeComparedToPreviousDay", Txns.getTxnCountPercentageChangeComparedToPreviousDay(),
                        "totalTransactionFeesPast24Hours", Txns.getTotalTxnFeesPast24Hours(),
                        "totalTransactionFeesPercentageChangeComparedToPreviousDay", Txns.getTotalTxnFeesPercentageChangeComparedToPreviousDay(),
                        "averageTransactionFeePast24Hours", Txns.getAverageTxnFeePast24Hours(),
                        "averageTransactionFeePercentageChangeComparedToPreviousDay", Txns.getAverageTxnFeePercentageChangeComparedToPreviousDay(),
                        "transactions", transactions);
            } catch (Exception e) {
                return getError(response, "Failed to fetch latest transactions: " + e.getLocalizedMessage());
            }
        });
        get("/transactionDetails/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String txnHash = request.queryParams("txnHash").toLowerCase();

                Transaction txn = pwrj.getTransactionByHash(txnHash);
                if (txn == null) return getError(response, "Invalid Txn Hash");

                String data = null;
                if (txn instanceof VmDataTransaction) {
                    data = ((VmDataTransaction) txn).getData();
                } else if (txn instanceof PayableVmDataTransaction) {
                    data = ((PayableVmDataTransaction) txn).getData();
                }

                long txnFee = txn.getFee();

                BigDecimal ONE_PWR_TO_USD = BigDecimal.ONE;

                BigDecimal sparks = new BigDecimal(txnFee);
                BigDecimal pwrAmount = sparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);

                BigDecimal feeValueInUSD = pwrAmount.multiply(ONE_PWR_TO_USD).setScale(9, RoundingMode.HALF_EVEN);
                return getSuccess(
                        "txnHash", txnHash,
                        "txnType", txn.getType(),
                        "blockNumber", txn.getBlockNumber(),
                        "timeStamp", txn.getTimestamp() / 1000,
                        "from", txn.getSender(),
                        "to", txn.getReceiver(),
                        "value", txn.getValue(),
                        "valueInUsd", txn.getValue(),
                        "success", !txn.hasError(),
                        "txnFee", txnFee,
                        "txnFeeInUsd", feeValueInUSD,
                        "data", data == null ? data : data.toLowerCase(),
                        "size", txn.getSize(),
                        "errorMessage", txn.getErrorMessage() != null ? txn.getErrorMessage() : null,
                        "extraData", txn.getExtraData()
                );
            } catch (Exception e) {
                return getError(response, "Failed to get txn details: " + e.getLocalizedMessage());
            }
        });

        //Wallet Calls
        get("/balanceOf/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String address = request.queryParams("userAddress").toLowerCase();

                long balance = pwrj.getBalanceOfAddress(address);
                long usdValue = balance * Settings.getPrice();
                BigDecimal usdValueBigDec = new BigDecimal(usdValue).divide(BigDecimal.valueOf((long) Math.pow(10, 11)));

                return getSuccess("balance", balance, "balanceUsdValue", usdValueBigDec);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/nonceOfUser/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");
                String userAddress = request.queryParams("userAddress");

                return getSuccess("nonce", pwrj.getNonceOfAddress(userAddress));
            } catch (Exception e) {
                return getError(response, "Failed to get nonce of user: " + e.getLocalizedMessage());
            }
        });

        get("/nodesInfo/", ((request, response) -> {
            Instant overallStart = Instant.now();
            Instant sectionStart;

            int count = Integer.parseInt(request.queryParams("count"));
            int page = Integer.parseInt(request.queryParams("page"));
            int offset = (page - 1) * count;

            int previousNodesCount = (page - 1) * count;
            int totalNodesCount, totalPages;

            try {
                // Section 1: Initial data fetching
                sectionStart = Instant.now();
                JSONArray nodesArray = new JSONArray();
                List<Validator> nodes = pwrj.getActiveValidators();
                totalNodesCount = nodes.size();
                logger.info("Section 1 (Initial data fetching) duration: {} ms", Duration.between(sectionStart, Instant.now()).toMillis());

                sectionStart = Instant.now();
                BigDecimal activeVotingPower = new BigDecimal(pwrj.getActiveVotingPower());
                int standbyNodesCount = pwrj.getStandbyValidatorsCount();
                int activeNodesCount = cacheManager.getActiveValidatorsCount();
                long totalVotingPower = pwrj.getTotalVotingPower();
                logger.info("Section middle (Middle data fetching) duration: {} ms", Duration.between(sectionStart, Instant.now()).toMillis());

                // Section 2: Pagination
                sectionStart = Instant.now();
                List<Validator> paginatedNodes = nodes.subList(
                        Math.min(offset, totalNodesCount),
                        Math.min(offset + count, totalNodesCount)
                );
                logger.info("Section 2 (Pagination) duration: {} ms", Duration.between(sectionStart, Instant.now()).toMillis());

                // Section 3: Node processing
                sectionStart = Instant.now();
                for (Validator node : paginatedNodes) {
                    Instant nodeStart = Instant.now();
                    JSONObject nodeObj = new JSONObject();
                    String address = node.getAddress();
                    long shares = node.getShares();
                    BigDecimal sparks = new BigDecimal(shares);
                    BigDecimal sharesInPwr = sparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);

                    BigDecimal votingPowerSparks = new BigDecimal(node.getVotingPower());
                    BigDecimal votingPower = votingPowerSparks.divide(activeVotingPower, 7, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(5, RoundingMode.HALF_UP);

                    nodeObj.put("address", "0x" + address)
                            .put("host", node.getIp())
                            .put("votingPowerInPercentage", votingPower)
                            .put("votingPowerInPwr", node.getVotingPower())
                            .put("earnings", sharesInPwr)
                            .put("blocksSubmitted", getBlocksSubmitted(address.toLowerCase()));

                    nodesArray.put(nodeObj);

                    logger.info("Processing time for node {}: {} ms", address, Duration.between(nodeStart, Instant.now()).toMillis());
                }
                logger.info("Section 3 (Node processing) total duration: {} ms", Duration.between(sectionStart, Instant.now()).toMillis());

                // Section 4: Metadata calculation
                sectionStart = Instant.now();
                totalPages = totalNodesCount / count;
                if (totalNodesCount % count != 0) ++totalPages;

                JSONObject metadata = new JSONObject()
                        .put("totalNodesCount", nodesArray.length())
                        .put("totalPages", totalPages)
                        .put("currentPage", page)
                        .put("itemsPerPage", count)
                        .put("totalItems", totalNodesCount)
                        .put("startIndex", previousNodesCount + 1)
                        .put("endIndex", Math.min(previousNodesCount + count, totalNodesCount))
                        .put("nextPage", page < totalPages ? page + 1 : -1)
                        .put("previousPage", page > 1 ? page - 1 : -1);
                logger.info("Section 4 (Metadata calculation) duration: {} ms", Duration.between(sectionStart, Instant.now()).toMillis());

                // Section 5: Response preparation
                sectionStart = Instant.now();
                Object result = getSuccess("nodes", nodesArray,
                        "metadata", metadata,
                        "totalActiveNodes", activeNodesCount,
                        "totalStandbyNodes", standbyNodesCount,
                        "totalVotingPower", totalVotingPower
                );
                logger.info("Section 5 (Response preparation) duration: {} ms", Duration.between(sectionStart, Instant.now()).toMillis());

                logger.info("Overall function duration: {} ms", Duration.between(overallStart, Instant.now()).toMillis());
                return result;
            } catch (Exception e) {
                return getError(response, e.getLocalizedMessage());
            }
        }));

        get("/nodesStatus/", ((request, response) -> {
            response.header("Content-Type", "application/json");
            String address = request.queryParams("userAddress").toLowerCase();

            try {
                Validator node = pwrj.getValidator(address);

                BigDecimal sparks = new BigDecimal(node.getShares());
                BigDecimal sharesInPwr = sparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);

                BigDecimal votingPowerSparks = new BigDecimal(node.getVotingPower());
                BigDecimal votingPowerInPwr = votingPowerSparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);

                // Section 5: Response preparation
                return getSuccess("address", address,
                        "ipAddress", node.getIp(),
                        "status", node.getStatus(),
                        "votingPower", votingPowerInPwr,
                        "numberOfDelegators", node.getDelegatorsCount(),
                        "totalShares", sharesInPwr,
                        "blocksCreated", getBlocksSubmitted(address),
                        "timeOfLastBlock", getLatestBlockNumberForFeeRecipient(address.substring(2)) / 1000
                );
            } catch (Exception e) {
                return getError(response, e.getLocalizedMessage());
            }
        }));

        get("/transactionHistory/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String address = request.queryParams("address");
                if (address == null) {
                    return getError(response, "Address is required");
                }
                address = address.substring(2).toLowerCase();

                String countStr = request.queryParams("count");
                if (countStr == null) {
                    return getError(response, "Count is required");
                }
                int count;
                try {
                    count = Integer.parseInt(countStr);
                } catch (NumberFormatException e) {
                    return getError(response, "Invalid count");
                }

                String pageStr = request.queryParams("page");
                if (pageStr == null) {
                    return getError(response, "Page is required");
                }
                int page;
                try {
                    page = Integer.parseInt(pageStr);
                } catch (NumberFormatException e) {
                    return getError(response, "Invalid page");
                }

                long startCountTimeOne = System.currentTimeMillis();
                List<NewTxn> txns = getUserTxns(address, page, count);
                long endCountTimeOne = System.currentTimeMillis();
                logger.info("Time taken for userTxns: " + (endCountTimeOne - startCountTimeOne) + " ms");

                long startCountTimeTwo = System.currentTimeMillis();
                int totalTxnCount = getTotalTxnCount(address);
                long endCountTimeTwo = System.currentTimeMillis();
                logger.info("Time taken for getTotalTxnCount: " + (endCountTimeTwo - startCountTimeTwo) + " ms");

                int totalPages = (int) Math.ceil((double) totalTxnCount / count);

                JSONArray txnsArray = new JSONArray();
                long start = System.currentTimeMillis();

                BigDecimal ONE_PWR_TO_USD = BigDecimal.ONE;

                for (NewTxn txn : txns) {
                    Transaction subTxn = pwrj.getTransactionByHash(txn.hash());
                    BigDecimal sparks = new BigDecimal(txn.txnFee());
                    BigDecimal pwrAmount = sparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);

                    BigDecimal feeValueInUSD = pwrAmount.multiply(ONE_PWR_TO_USD).setScale(9, RoundingMode.HALF_EVEN);

                    JSONObject object = new JSONObject();
                    object.put("txnHash", txn.hash());
                    object.put("block", txn.blockNumber());
                    object.put("positionInBlock", txn.positionInBlock());
                    object.put("timeStamp", txn.timestamp() / 1000);
                    object.put("from", "0x" + txn.fromAddress());
                    object.put("to", txn.toAddress());
                    object.put("value", txn.value());
                    object.put("txnType", txn.txnType());
                    object.put("success", txn.success());
                    object.put("txnFee", txn.txnFee());
                    object.put("txnFeeInUsd", feeValueInUSD);
                    object.put("nonceOrValidationHash", subTxn.getNonce());

                    txnsArray.put(object);
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", page > totalPages ? 0 : count);
                metadata.put("totalItems", totalTxnCount);
                metadata.put("startIndex", (page - 1) * count + 1);
                metadata.put("endIndex", Math.min(page * count, totalTxnCount));
                metadata.put("nextPage", page < totalPages ? page + 1 : -1);
                metadata.put("previousPage", page > 1 ? page - 1 : -1);
                long end = System.currentTimeMillis();
                logger.info("Time taken for loops: " + (end - start) + " ms");

                // Get first and last transactions
                Pair<NewTxn, NewTxn> firstLastTxns = getFirstAndLastTransactionsByAddress(address);
                JSONObject firstLastTxnsObject = new JSONObject();

                if (firstLastTxns.first != null) {
                    JSONObject firstTxnObject = new JSONObject();
                    firstTxnObject.put("txnHash", firstLastTxns.first.hash());
                    firstTxnObject.put("block", firstLastTxns.first.blockNumber());
                    firstTxnObject.put("timeStamp", firstLastTxns.first.timestamp() / 1000);
                    firstLastTxnsObject.put("firstTransaction", firstTxnObject);
                }

                if (firstLastTxns.second != null) {
                    JSONObject lastTxnObject = new JSONObject();
                    lastTxnObject.put("txnHash", firstLastTxns.second.hash());
                    lastTxnObject.put("block", firstLastTxns.second.blockNumber());
                    lastTxnObject.put("timeStamp", firstLastTxns.second.timestamp() / 1000);
                    firstLastTxnsObject.put("lastTransaction", lastTxnObject);
                }

                return getSuccess("transactions", txnsArray, "metadata", metadata, "firstLastTransactions", firstLastTxnsObject);
            } catch (Exception e) {
                return getError(response, "Failed to get transaction history " + e.getLocalizedMessage());
            }
        });

        get("/isTxnProcessed/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String txnHash = request.queryParams("txnHash").toLowerCase();

                NewTxn txn = getDbTxn(txnHash);
                if (txn == null) return getSuccess("isProcessed", false);
                else return getSuccess("isProcessed", true);
            } catch (Exception e) {
                return getError(response, "Failed to get txn status: " + e);
            }
        });

        //Staking app calls
        get("/staking/homepageInfo/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                List<Validator> activeValidators = pwrj.getActiveValidators();
                List<Validator> standbyValidators = pwrj.getStandbyValidators();
                int totalValidatorsCount = activeValidators.size() + standbyValidators.size();
                int availablePages = totalValidatorsCount / count;
                if (totalValidatorsCount % count != 0) ++availablePages;

                if (page > availablePages)
                    return getError(response, "Page number is greater than the total available pages: " + availablePages);

                int startingIndex = (page - 1) * count;
                JSONArray validatorsArray = new JSONArray();
                long activeVotingPower = pwrj.getActiveVotingPower();
                for (int t = startingIndex; t < totalValidatorsCount; ++t) {
                    Validator validator;

                    if (t >= activeValidators.size()) {
                        validator = standbyValidators.get(t - activeValidators.size());
                    } else {
                        validator = activeValidators.get(t);
                    }

                    if (validator == null) continue;

                    JSONObject object = new JSONObject();

                    object.put("name", validator.getAddress());
                    object.put("status", validator.getStatus());
                    object.put("votingPower", BigDecimal.valueOf(validator.getVotingPower()).divide(BigDecimal.valueOf(1000000000), 2, BigDecimal.ROUND_HALF_UP));
                    object.put("totalPowerShare", BigDecimal.valueOf(validator.getVotingPower()).divide(BigDecimal.valueOf(activeVotingPower), 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)));
                    object.put("commission", 1);
                    object.put("apy", 5);
                    object.put("hosting", "vps");

                    validatorsArray.put(object);
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalPages", availablePages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", count);
                metadata.put("totalItems", totalValidatorsCount);
                metadata.put("startIndex", startingIndex);
                metadata.put("endIndex", page < availablePages ? startingIndex + count : totalValidatorsCount);
                metadata.put("nextPage", page < availablePages ? page + 1 : -1);
                metadata.put("previousPage", page > 1 ? page - 1 : -1);

                return getSuccess(
                        "activeValidatorsCount", cacheManager.getActiveValidatorsCount(),
                        "activeVotingPower", BigDecimal.valueOf(pwrj.getActiveVotingPower()).divide(BigDecimal.TEN.pow(9), 0, BigDecimal.ROUND_HALF_UP),
                        "delegatorsCount", pwrj.getTotalDelegatorsCount(),
                        "validators", validatorsArray,
                        "metadata", metadata);
            } catch (Exception e) {
                return getError(response, "Failed to get staking home page info: " + e.getLocalizedMessage());
            }
        });

        get("/staking/validatorInfo/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String validatorAddress = request.queryParams("validatorAddress");
                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                Validator v = pwrj.getValidator(validatorAddress);
                if (v.getAddress() == null) return getError(response, "Invalid Validator Address");

                int totalPages = v.getDelegatorsCount() / count;
                if (v.getDelegatorsCount() % count != 0) ++totalPages;
                int startingIndex = (page - 1) * count;

                List<Delegator> delegators = v.getDelegators(pwrj);
                JSONArray delegatorsArray = new JSONArray();

                try {
                    int delegatorsCount = Math.max(v.getDelegatorsCount(), 0);

                    for (int t = 0; t < delegatorsCount; t++) {
                        Delegator delegator = delegators.get(t);
                        if (delegator == null) continue;

                        JSONObject object = new JSONObject();

                        object.put("address", delegator.getAddress());
                        object.put("delegatedPWR", BigDecimal.valueOf(delegator.getDelegatedPWR())
                                .divide(BigDecimal.TEN.pow(9), 0, BigDecimal.ROUND_HALF_UP));

                        delegatorsArray.put(object);
                    }
                } catch (Exception e) {
                    delegatorsArray = new JSONArray();
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", count);
                metadata.put("totalItems", v.getDelegatorsCount());
                metadata.put("startIndex", startingIndex);
                metadata.put("endIndex", page < totalPages ? startingIndex + count : v.getDelegatorsCount());
                metadata.put("nextPage", page < totalPages ? page + 1 : -1);
                metadata.put("previousPage", page > 1 ? page - 1 : -1);

                return getSuccess(
                        "name", "Unnamed Validator",
                        "status", v.getStatus(),
                        "joiningDate", getValidatorJoiningTime(validatorAddress.toLowerCase()),
                        "hosting", "vps",
                        "website", "null",
                        "description", "null",
                        "delegators", delegatorsArray,
                        "metadata", metadata);
            } catch (Exception e) {
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/staking/portfolio/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String userAddress = request.queryParams("userAddress");

                List<Validator> delegatedValidators = pwrj.getDelegatees(userAddress);
                JSONArray portfolioAllocation = new JSONArray();

                long totalDelegatedPWR = 0;
                long totalRewards = 0;
                for (Validator validator : delegatedValidators) {
                    long delegatedPWR = pwrj.getDelegatedPWR(userAddress, validator.getAddress());
                    totalDelegatedPWR += delegatedPWR;
                    totalRewards += pwrj.getDelegatedPWR(userAddress, validator.getAddress()) - delegatedPWR;

                    JSONObject object = new JSONObject();
                    object.put("validatorName", validator);
                    object.put("portfolioShare", BigDecimal.valueOf(delegatedPWR).divide(BigDecimal.valueOf(totalDelegatedPWR), 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)));
                    portfolioAllocation.put(object);

                    JSONObject data = new JSONObject();
                    data.put("name", validator);
                    data.put("status", validator.getStatus());
                    data.put("votingPower", validator.getVotingPower());
                    data.put("totalPowerShare", BigDecimal.valueOf(validator.getVotingPower()).divide(BigDecimal.valueOf(pwrj.getActiveVotingPower()), 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)));
                    data.put("delegatedPWR", delegatedPWR);
                    data.put("apy", 5);
                }
                JSONArray validatorsArray = new JSONArray();

                return getSuccess(
                        "totalDelegatedPWR", totalDelegatedPWR,
                        "totalRewards", totalRewards,
                        "portfolioAllocation", portfolioAllocation,
                        "validators", validatorsArray);
            } catch (Exception e) {
                return getError(response, "Failed to fetch staking portfolio: " + e.getLocalizedMessage());
            }
        });

        get("/stats", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");
                Stats stats = Stats.getInstance();

                return new JSONObject()
                        .put("success", true)
                        .put("totalTransactionCount", stats.getTotalTransactionCount())
                        .put("totalBlockCount", stats.getTotalBlockCount())
                        .put("tps", stats.getTPS())
                        .put("totalFees24h", stats.getTotalFees24h())
                        .put("avgFeePerTransaction24h", stats.getAvgFeePerTransaction24h())
                        .put("transactions24h", stats.getTransactions24h())
                        .put("avgBlockSize24h", stats.getAvgBlockSize24h())
                        .put("totalFees", stats.getTotalFees())
                        .toString();

            } catch (Exception e) {
                return getError(response, "Failed to get stats " + e.getLocalizedMessage());
            }
        });

    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            // Convert each byte to a 2-digit hexadecimal string.
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString().toLowerCase();
    }

    // There was a problem on the server side
    public static JSONObject getError(spark.Response response, String message) {
        response.status(400);
        JSONObject object = new JSONObject();
        object.put("message", message);

        return object;
    }

    public static JSONObject getSuccess(Object... variables) throws Exception {
        JSONObject object = new JSONObject();

        int size = variables.length;
        if (size % 2 != 0)
            throw new Exception("Provided variables length should be even when using getSuccess");

        for (int t = 0; t < size; t += 2) {
            object.put(variables[t].toString(), variables[t + 1]);
        }

        return object;
    }
}

