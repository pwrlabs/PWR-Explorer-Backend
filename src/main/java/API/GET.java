package API;

import DataModel.Block;
import Core.Cache.CacheManager;
import Utils.Settings;
import DataModel.NewTxn;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static Database.Queries.*;
import static spark.Spark.get;

public class GET {
    private static final Logger logger = LogManager.getLogger(GET.class);
    private static CacheManager cacheManager;
    private final static BigDecimal ONE_PWR_TO_USD = BigDecimal.ONE;

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
            getLastXTransactions(5);
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
                responseObject.put("totalTransactionsCount", cacheManager.getTxnsCountPast24Hours());
                responseObject.put("blocksCount", cacheManager.getBlocksCount());
                responseObject.put("validators", cacheManager.getActiveValidatorsCount());  // example value
                responseObject.put("tps", cacheManager.getAverageTps(100, cacheManager.getBlocksCount()));  // example value
                responseObject.put("txns", txns);
                responseObject.put("blocks", blocks);
                responseObject.put("avgTxnFeeChange", cacheManager.getAvgTxnFeePercentageChange());
                responseObject.put("totalTxnFeesChange", cacheManager.getTotalTxnFeesPercentageChange());
                responseObject.put("avgTxnFeePast24Hours", cacheManager.getAverageTxnFeePast24Hours());
                responseObject.put("totalTxnFeesPast24Hours", cacheManager.getTotalTxnsFeesPast24Hours());
                responseObject.put("txnCountChange", cacheManager.getTxnCountPercentageChange());

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
                int count = validateAndParseCountParam(request.queryParams("count"), response);
                int page = validateAndParsePageParam(request.queryParams("page"), response);

                int offset = (page - 1) * count;

                long totalBlockCount = cacheManager.getBlocksCount();

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
        });
        get("/blockDetails/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                long blockNumber = Long.parseLong(request.queryParams("blockNumber"));

                Block block = getDbBlock(blockNumber);
                if (block == null) return getError(response, "Invalid DataModel.Block Number");

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
                    return getError(response, "Invalid DataModel.Block Number");
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

                String blockNumber = request.queryParams("blockNumber");
                int count = validateAndParseCountParam(request.queryParams("count"), response);
                int page = validateAndParsePageParam(request.queryParams("page"), response);

                //Metadata variables
                int previousTxnsCount = (page - 1) * count;
                int totalTxnCount;

                Block block = getDbBlock(Long.parseLong(blockNumber));
                if (block == null) return getError(response, "Invalid DataModel.Block Number");

                int txnsCount = 0;
                JSONArray txns = new JSONArray();
                List<NewTxn> txnsArray = getBlockTxns(blockNumber);
                totalTxnCount = txnsArray.size();

                for (int t = previousTxnsCount; t < txnsArray.size(); ++t) {
                    NewTxn txn = txnsArray.get(t);
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

                JSONObject metadata = createPaginationMetadata(totalTxnCount, page, count);

                return getSuccess("metadata", metadata, "transactions", txns);
            } catch (Exception e) {
                return getError(response, e.getLocalizedMessage());
            }
        });
        get("/blocksCreated/", ((request, response) -> {
            response.type("application/json");
            String address = request.queryParams("validatorAddress");
            int count = validateAndParseCountParam(request.queryParams("count"), response);
            int page = validateAndParsePageParam(request.queryParams("page"), response);
            int offset = (page - 1) * count;

            // Metadata variables
            int totalBlocksCount;

            try {
                JSONArray blocks = getBlocksCreated(address.toLowerCase().substring(2), count, offset);

                if (blocks.isEmpty()) totalBlocksCount = 0;
                else totalBlocksCount = getBlocksSubmitted(address.toLowerCase().substring(2));

                JSONObject metadata = createPaginationMetadata(totalBlocksCount, page, count);

                return getSuccess("blocks", blocks,
                        "metadata", metadata
                );
            } catch (Exception e) {
                return getError(response, e.getLocalizedMessage());
            }
        }));

        get("/latestTransactions/", (request, response) -> {
            JSONArray transactions = new JSONArray();
            try {
                response.header("Content-Type", "application/json");

                int count = validateAndParseCountParam(request.queryParams("count"), response);
                int page = validateAndParsePageParam(request.queryParams("page"), response);
                int offset = (page - 1) * count;

                List<NewTxn> txns = getTransactions(count, offset);

                int totalTxnCount = cacheManager.getTotalTransactionCount();

                for (NewTxn txn : txns) {
                    JSONObject object = populateTxnsResponse(txn);
                    transactions.put(object);
                }

                JSONObject metadata = createPaginationMetadata(totalTxnCount, page, count);

                return getSuccess("metadata", metadata,
                        "transactionCountPast24Hours", cacheManager.getTxnsCountPast24Hours(),
                        "transactionCountPercentageChangeComparedToPreviousDay", cacheManager.getTxnCountPercentageChange(),
                        "totalTransactionFeesPast24Hours", cacheManager.getTotalTxnsFeesPast24Hours(),
                        "totalTransactionFeesPercentageChangeComparedToPreviousDay", cacheManager.getTotalTxnFeesPercentageChange(),
                        "averageTransactionFeePast24Hours", cacheManager.getAverageTxnFeePast24Hours(),
                        "averageTransactionFeePercentageChangeComparedToPreviousDay", cacheManager.getAvgTxnFeePercentageChange(),
                        "transactions", transactions
                );
            } catch (Exception e) {
                return getError(response, "Failed to fetch latest transactions: " + e.getLocalizedMessage());
            }
        });
        get("/transactionDetails/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String txnHash = request.queryParams("txnHash").toLowerCase();

                Transaction txn = pwrj.getTransactionByHash(txnHash);
                if (txn == null) return getError(response, "Invalid DataModel.Block.Txn Hash");

                String data = null;
                if (txn instanceof VmDataTransaction) {
                    data = ((VmDataTransaction) txn).getData();
                } else if (txn instanceof PayableVmDataTransaction) {
                    data = ((PayableVmDataTransaction) txn).getData();
                }

                long txnFee = txn.getFee();


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
                return getError(response, "Failed to fetch balance of user balance due to an internal error " + e.getLocalizedMessage());
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
            int count = validateAndParseCountParam(request.queryParams("count"), response);
            int page = validateAndParsePageParam(request.queryParams("page"), response);
            int offset = (page - 1) * count;

            int totalNodesCount;

            try {
                BigDecimal activeVotingPower = new BigDecimal(cacheManager.getActiveVotingPower());
                BigDecimal hundredBD = BigDecimal.valueOf(100L);
                BigDecimal millionBD = BigDecimal.valueOf(1000000000L);

                // Fetch data once
                List<Validator> nodes = cacheManager.getActiveValidators();
                totalNodesCount = nodes.size();
                int standbyNodesCount = cacheManager.getStandByValidatorsCount();
                long totalVotingPower = cacheManager.getTotalVotingPower();

                // Calculate pagination bounds
                int startIndex = Math.min(offset, totalNodesCount);
                int endIndex = Math.min(offset + count, totalNodesCount);
                List<Validator> paginatedNodes = nodes.subList(startIndex, endIndex);

                JSONArray nodesArray = new JSONArray();

                for (Validator node : paginatedNodes) {
                    String address = node.getAddress();

                    // Calculate voting power percentage
                    BigDecimal votingPower = new BigDecimal(node.getVotingPower())
                            .divide(activeVotingPower, 7, RoundingMode.HALF_UP)
                            .multiply(hundredBD)
                            .setScale(5, RoundingMode.HALF_UP);

                    // Calculate shares
                    BigDecimal sharesInPwr = new BigDecimal(node.getShares())
                            .divide(millionBD, 9, RoundingMode.HALF_EVEN);

                    // Create and populate node object
                    nodesArray.put(new JSONObject()
                            .put("address", "0x" + address)
                            .put("host", node.getIp())
                            .put("votingPowerInPercentage", votingPower)
                            .put("votingPowerInPwr", node.getVotingPower())
                            .put("earnings", sharesInPwr)
                            .put("blocksSubmitted", getBlocksSubmitted(address)));
                }

                JSONObject metadata = createPaginationMetadata(totalNodesCount, page, count);

                return getSuccess("nodes", nodesArray,
                        "metadata", metadata,
                        "totalActiveNodes", totalNodesCount,
                        "totalStandbyNodes", standbyNodesCount,
                        "totalVotingPower", totalVotingPower
                );
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
                long serverTime = System.currentTimeMillis();
                response.header("Content-Type", "application/json");

                String address = request.queryParams("address");
                if (address == null) {
                    return getError(response, "Address is required");
                }
                address = address.substring(2).toLowerCase();

                int count = validateAndParseCountParam(request.queryParams("count"), response);
                int page = validateAndParsePageParam(request.queryParams("page"), response);

                long time = System.currentTimeMillis();
                List<NewTxn> txns = getUserTxns(address, page, count);
                long userTxnsTime = System.currentTimeMillis() - time;
                time = System.currentTimeMillis();
                int totalTxnCount = getTotalTxnCount(address);
                long totalTxnsTime = System.currentTimeMillis() - time;

                time = System.currentTimeMillis();
                JSONArray transactions = new JSONArray();
                for (NewTxn txn : txns) {
                    JSONObject object = populateTxnsResponse(txn);
                    transactions.put(object);
                }

                JSONObject metadata = createPaginationMetadata(totalTxnCount, page, count);

                Pair<NewTxn, NewTxn> firstLastTxns = new Pair<>(null, null);

                if (totalTxnCount != 0) {
                    firstLastTxns = getFirstAndLastTransactionsByAddress(address);
                }

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

                return getSuccess(
                        "transactions", transactions,
                        "metadata", metadata,
                        "firstLastTransactions", firstLastTxnsObject,
                        "totalTxnsTime", totalTxnsTime,
                        "userTxnsTime", userTxnsTime,
                        "serverTime", System.currentTimeMillis() - serverTime
                );
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

                return new JSONObject()
                        .put("success", true)
                        .put("totalTransactionCount", cacheManager.getTotalTransactionCount())
                        .put("totalBlockCount", cacheManager.getBlocksCount())
                        .put("tps", cacheManager.getAverageTps(100, cacheManager.getBlocksCount()))
                        .put("totalFees24h", cacheManager.getTotalTxnsFeesPast24Hours())
                        .put("avgFeePerTransaction24h", cacheManager.getAverageTxnFeePast24Hours())
                        .put("transactions24h", cacheManager.getTxnsCountPast24Hours())
                        .put("avgBlockSize24h", cacheManager.get24HourBlockStats().get("averageBlockSize"))
                        .put("totalFees", getTotalFees())
                        .toString();

            } catch (Exception e) {
                return getError(response, "Failed to get stats " + e.getLocalizedMessage());
            }
        });

    }

    //#region Pagination Utilities
    private static JSONObject createPaginationMetadata(long totalItems, int currentPage, int itemsPerPage) {
        int totalPages = (int) Math.ceil((double) Math.min(totalItems, 100_000) / itemsPerPage);
        int startIndex = (currentPage - 1) * itemsPerPage + 1;
        long endIndex = Math.min((long) currentPage * itemsPerPage, totalItems);

        return new JSONObject()
                .put("totalPages", totalPages)
                .put("currentPage", currentPage)
                .put("itemsPerPage", itemsPerPage)
                .put("totalItems", totalItems)
                .put("startIndex", startIndex)
                .put("endIndex", endIndex)
                .put("nextPage", currentPage < totalPages ? currentPage + 1 : -1)
                .put("previousPage", currentPage > 1 ? currentPage - 1 : -1);
    }

    private static int validateAndParsePageParam(String pageStr, spark.Response response) throws Exception {
        try {
            int page = Integer.parseInt(pageStr);
            if (page < 1) {
                throw new Exception("Page number must be positive");
            }
            return page;
        } catch (NumberFormatException e) {
            throw new Exception("Invalid page number format");
        }
    }

    private static int validateAndParseCountParam(String countStr, spark.Response response) throws Exception {
        try {
            int count = Integer.parseInt(countStr);
            if (count < 1) {
                throw new Exception("Count must be positive");
            }
            return count;
        } catch (NumberFormatException e) {
            throw new Exception("Invalid count format");
        }
    }

    //#region Helpers
    private static JSONObject getError(spark.Response response, String message) {
        response.status(400);
        JSONObject object = new JSONObject();
        object.put("message", message);

        return object;
    }

    private static JSONObject getSuccess(Object... variables) throws Exception {
        JSONObject object = new JSONObject();

        int size = variables.length;
        if (size % 2 != 0)
            throw new Exception("Provided variables length should be even when using getSuccess");

        for (int t = 0; t < size; t += 2) {
            object.put(variables[t].toString(), variables[t + 1]);
        }

        return object;
    }

    private static JSONObject populateTxnsResponse(NewTxn txn) {
        long fee = txn.txnFee();
        BigDecimal sparks = BigDecimal.valueOf(fee);
        BigDecimal pwrAmount = sparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);
        BigDecimal feeValueInUSD = pwrAmount.multiply(ONE_PWR_TO_USD).setScale(9, RoundingMode.HALF_EVEN);

        JSONObject object = new JSONObject();
        object.put("txnHash", txn.hash());
        object.put("txnType", txn.txnType());
        object.put("block", txn.blockNumber());
        object.put("timeStamp", txn.timestamp() / 1000);
        object.put("from", txn.fromAddress());
        object.put("to", txn.toAddress());
        object.put("value", txn.value());
        object.put("txnFee", fee);
        object.put("valueInUsd", txn.value());
        object.put("txnFeeInUsd", feeValueInUSD);
        object.put("positionInBlock", txn.positionInBlock()
        );
        return object;
    }
    //#endregion
}

