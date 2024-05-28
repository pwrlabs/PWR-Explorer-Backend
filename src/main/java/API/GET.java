package API;


import Block.Block;
import Block.Blocks;
import Main.Settings;
import Txn.Txn;
import Txn.Txns;
import User.User;
import User.Users;
import Validator.Validators;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import com.github.pwrlabs.pwrj.record.delegator.Delegator;
import com.github.pwrlabs.pwrj.record.transaction.Transaction;
import com.github.pwrlabs.pwrj.record.validator.Validator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static Core.Sql.Queries.*;
import static spark.Spark.get;

public class GET {
    private static final Logger logger = LogManager.getLogger(GET.class);
    public static void run(PWRJ pwrj) {
        get("/test/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                return "Server is on";
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        //Explorer Calls
        get("/explorerInfo/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                JSONArray blocks = new JSONArray();
                JSONArray txns = new JSONArray();

                //Fetch the latest 5 blocks
                long blockNumberToCheck = Blocks.getLatestBlockNumber();
                List<Block> blockList = getLastXBlocks(5);
                for(Block block: blockList){

                    JSONObject object = new JSONObject();
                    object.put("blockNumber",block.getBlockNumber());
                    object.put("blockHeight", blockNumberToCheck);
                    object.put("timeStamp", block.getTimeStamp());
                    object.put("txnsCount", block.getTxnCount());
                    object.put("blockReward", block.getBlockReward());
                    object.put("blockSubmitter", "0x" + bytesToHex(block.getBlockSubmitter()));

                    blocks.put(object);
                }

                //Fetch the latest 5 txns
                List<Txn> txnsList = getLastXTransactions(5);
                    for(Txn txn : txnsList) {
                        if(txn == null) continue;
                        JSONObject object = new JSONObject();
                        object.put("txnHash", txn.getHash());
                        object.put("timeStamp", txn.getTimestamp());
                        object.put("from", "0x" + txn.getFromAddress());
                        object.put("to", txn.getToAddress());
                        object.put("value", txn.getValue());

                        txns.put(object);
                    }

                return getSuccess(
                        "price", Settings.getPrice(),
                        "priceChange", 2.5,
                        "marketCap", 1000000000L,
                        "totalTransactionsCount",getTotalTransactionCount(),
                        "blocksCount", blockNumberToCheck,
                        "validators", pwrj.getActiveValidatorsCount(),
                        "tps", Blocks.getAverageTps(100),
                        "txns", txns,
                        "blocks", blocks
                );


            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/dailyStats", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                // Initialize JSON objects for the response
                JSONArray blocks = new JSONArray();
                JSONArray txns = new JSONArray();

                // Fetch the latest 5 blocks (assuming a function getLastXBlocks exists)
                List<Block> blockList = getLastXBlocks(5);
                for (Block block : blockList) {
                    JSONObject object = new JSONObject();
                    object.put("blockNumber", block.getBlockNumber());
                    object.put("blockHeight", Blocks.getLatestBlockNumber());
                    object.put("timeStamp", block.getTimeStamp());
                    object.put("txnsCount", block.getTxnCount());
                    object.put("blockReward", block.getBlockReward());
                    object.put("blockSubmitter", "0x" + bytesToHex(block.getBlockSubmitter()));

                    blocks.put(object);
                }

                // Fetch the latest 5 transactions (assuming a function getLastXTransactions exists)
                List<Txn> txnsList = getLastXTransactions(5);
                for (Txn txn : txnsList) {
                    if (txn == null) continue;
                    JSONObject object = new JSONObject();
                    object.put("txnHash", txn.getHash());
                    object.put("timeStamp", txn.getTimestamp());
                    object.put("from", "0x" + txn.getFromAddress());
                    object.put("to", txn.getToAddress());
                    object.put("value", txn.getValue());

                    txns.put(object);
                }

                // Build the response JSON
                JSONObject responseObject = new JSONObject();
                responseObject.put("price", Settings.getPrice());
                responseObject.put("priceChange", 2.5);  // example value
                responseObject.put("marketCap", 1000000000L);  // example value
                responseObject.put("totalTransactionsCount", getTransactionCountPast24Hours());
                responseObject.put("blocksCount", Blocks.getLatestBlockNumber());
                responseObject.put("validators", pwrj.getActiveValidatorsCount());  // example value
                responseObject.put("tps", Blocks.getAverageTps(100));  // example value
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
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/latestBlocks/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                //Metadata variables
                long previousBlocksCount = (page - 1) * count;
                int totalBlockCount, totalPages;

                int blocksCount = 0;
                JSONArray blocksArray = new JSONArray();

                long latestBlockNumber = Blocks.getLatestBlockNumber();

                totalPages = (int) (latestBlockNumber / count);
                if(latestBlockNumber % count != 0) ++totalPages;

                totalBlockCount = (int) latestBlockNumber;
                List<Block> blockList = getLastXBlocks(count);
                for(Block block: blockList){

                    JSONObject object = new JSONObject();

                    object.put("blockHeight", block.getBlockSize());
                    object.put("timeStamp", block.getTimeStamp());
                    object.put("txnsCount", block.getTxnCount());
                    object.put("blockReward", block.getBlockReward());
                    object.put("blockSubmitter", "0x" + bytesToHex(block.getBlockSubmitter()));

                    blocksArray.put(object);
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalBlocks", Blocks.getLatestBlockNumber());
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", count);
                metadata.put("totalItems", totalBlockCount);
                metadata.put("startIndex", previousBlocksCount + 1);

                if(previousBlocksCount + count <= totalBlockCount) metadata.put("endIndex", previousBlocksCount + count);
                else metadata.put("endIndex", totalBlockCount);

                if(page < totalPages) metadata.put("nextPage", page + 1);
                else metadata.put("nextPage", -1);

                if(page > 1) metadata.put("previousPage", page - 1);
                else metadata.put("previousPage", -1);

                return getSuccess(
                        "networkUtilizationPast24Hours", Blocks.getNetworkUtilizationPast24Hours(),
                        "averageBlockSizePast24Hours", Blocks.getAverageBlockSizePast24Hours(),
                        "totalBlockRewardsPast24Hours", Blocks.getTotalBlockRewardsPast24Hours(),
                        "blocks", blocksArray,
                        "metadata", metadata);

            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });
        get("/blockDetails/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                long blockNumber = Long.parseLong(request.queryParams("blockNumber"));

                com.github.pwrlabs.pwrj.record.block.Block block = pwrj.getBlockByNumber(blockNumber);
                if(block == null) return getError(response, "Invalid Block Number");

                return getSuccess(
                        "blockHeight", blockNumber,
                        "timeStamp", block.getTimestamp(),
                        "txnsCount", block.getTransactionCount(),
                        "blockSize", block.getSize(),
                        "blockReward", block.getReward(),
                        "blockSubmitter", block.getSubmitter(),
                        "blockConfirmations", Blocks.getLatestBlockNumber() - blockNumber);
            } catch (Exception e) {
                e.printStackTrace();
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

                com.github.pwrlabs.pwrj.record.block.Block block = pwrj.getBlockByNumber(blockNumber);
                if(block == null) return getError(response, "Invalid Block Number");

                int txnsCount = 0;
                JSONArray txns = new JSONArray();
                Transaction[] txnsArray = block.getTransactions();

                if(txnsArray == null) totalTxnCount = 0;
                else totalTxnCount = txnsArray.length;

                totalPages = totalTxnCount / count;
                if(totalTxnCount % count != 0) ++totalPages;

                for(int t = previousTxnsCount; t < txnsArray.length; ++t) {
                    Transaction txn = txnsArray[t];
                    //Txn txn = txnsArray[t];
                    if(txn == null) continue;
                    if(txnsCount == count) break;

                    JSONObject object = new JSONObject();

                    object.put("txnHash", txn.getHash());
                    object.put("txnType", txn.getType());
                    object.put("blockNumber", blockNumber);
                    object.put("timeStamp", block.getTimestamp());
                    object.put("from", txn.getSender());
                    object.put("to", txn.getReceiver());
                    object.put("value", txn.getValue());

                    txns.put(object);
                    ++txnsCount;
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", count);
                metadata.put("totalItems", totalTxnCount);
                metadata.put("startIndex", previousTxnsCount + 1);
                if(previousTxnsCount + count <= totalTxnCount) metadata.put("endIndex", previousTxnsCount + count);
                else metadata.put("endIndex", totalTxnCount);

                if(page < totalPages) metadata.put("nextPage", page + 1);
                else metadata.put("nextPage", -1);

                if(page > 1) metadata.put("previousPage", page - 1);
                else metadata.put("previousPage", -1);

                return getSuccess("metadata", metadata, "transactions", txns);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

//        get("/latestTransactions/", (request, response) -> {
//            try {
//                response.header("Content-Type", "application/json");
//
//                int count = Integer.parseInt(request.queryParams("count"));
//                int page = Integer.parseInt(request.queryParams("page"));
//                int previousTxnsCount = (page - 1) * count;
//
//                int fetchedTxns = 0;
//                JSONArray txnsArray = new JSONArray();
//
//                for(int t=0; fetchedTxns < count + previousTxnsCount; ++t) {
//                    long blockNumber = getLastBlockNumber();
//
//                    List<Txn> txns = getBlockTxns(""+blockNumber);
//                    for(Txn txn : txns) {
//                        if(txn == null) continue;
//
//                        if(fetchedTxns < previousTxnsCount) {
//                            ++fetchedTxns;
//                            continue;
//                        }
//
//                        JSONObject object = new JSONObject();
//
//                        object.put("txnHash", txn.getHash());
//                        object.put("block", blockNumber);
//                        object.put("txnType", txn.getTxnType());
//                        object.put("timeStamp", txn.getTimestamp());
//                        object.put("from", "0x" + bytesToHex(txn.getFromAddress()));
//                        object.put("to", txn.getToAddress());
//                        object.put("txnFee", txn.getTxnFee());
//                        object.put("value", txn.getValue());
//                        object.put("valueInUsd", txn.getValue());
//                        object.put("txnFeeInUsd", txn.getTxnFee());
//                        object.put("nonceOrValidationHash", txn.getNonceOrValidationHash());
//                        object.put("positionInBlock", txn.getPositionInBlock());
//
//                        txnsArray.put(object);
//                        ++fetchedTxns;
//                        if(fetchedTxns == count + previousTxnsCount) break;
//                    }
//                }
//
//                int totalTxnCount = Txns.getTxnCount(); //TODO: what is this intended to return
//                int totalPages = totalTxnCount / count;
//                if(totalTxnCount % count != 0) ++totalPages;
//
//                JSONObject metadata = new JSONObject();
//                metadata.put("totalTxns", Txns.getTxnCount());
//                metadata.put("totalPages", totalPages);
//                metadata.put("currentPage", page);
//                metadata.put("itemsPerPage", count);
//                metadata.put("totalItems", totalTxnCount);
//                metadata.put("startIndex", previousTxnsCount + 1);
//
//                if(previousTxnsCount + count <= totalTxnCount) metadata.put("endIndex", previousTxnsCount + count);
//                else metadata.put("endIndex", totalTxnCount);
//
//                if(page < totalPages) metadata.put("nextPage", page + 1);
//                else metadata.put("nextPage", -1);
//
//                if(page > 1) metadata.put("previousPage", page - 1);
//                else metadata.put("previousPage", -1);
//
//
//                return getSuccess(
//                        "transactionCountPast24Hours", Txns.getTxnCountPast24Hours(),
//                        "transactionCountPercentageChangeComparedToPreviousDay", Txns.getTxnCountPercentageChangeComparedToPreviousDay(),
//                        "totalTransactionFeesPast24Hours", Txns.getTotalTxnFeesPast24Hours(),
//                        "totalTransactionFeesPercentageChangeComparedToPreviousDay", Txns.getTotalTxnFeesPercentageChangeComparedToPreviousDay(),
//                        "averageTransactionFeePast24Hours", Txns.getAverageTxnFeePast24Hours(),
//                        "averageTransactionFeePercentageChangeComparedToPreviousDay", Txns.getAverageTxnFeePercentageChangeComparedToPreviousDay(),
//                        "transactions", txnsArray,
//                        "metadata", metadata);
//            } catch (Exception e) {
//                e.printStackTrace();
//                return getError(response, e.getLocalizedMessage());
//            }
//        });

        get("/latestTransactions/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                int limit = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));
                int offset = (page - 1) * limit;
                System.out.println(">>hello");

                List<Txn> txnsList = getTransactions(limit, offset);

                //Metadata variables
                int previousTxnsCount = (page - 1) * limit;
                int totalTxnCount = getTotalTransactionCount();
                int totalPages = totalTxnCount / limit;
                if (totalTxnCount % limit != 0) ++totalPages;

                JSONArray transactions = new JSONArray();

                for (Txn txn : txnsList) {
                    JSONObject object = new JSONObject();
                    object.put("txnHash", txn.getHash());
                    object.put("txnType", txn.getTxnType());
                    object.put("blockNumber", txn.getBlockNumber());
                    object.put("timeStamp", txn.getTimestamp());
                    object.put("from", "0x" + txn.getFromAddress());
                    object.put("to", txn.getToAddress());
                    object.put("value", txn.getValue());
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

//                JSONObject response = new JSONObject();
//                response.put("metadata", metadata);
//                response.put("transactions", transactions);

                return getSuccess("metadata", metadata,
                        "transactionCountPast24Hours", Txns.getTxnCountPast24Hours(),
                        "transactionCountPercentageChangeComparedToPreviousDay", Txns.getTxnCountPercentageChangeComparedToPreviousDay(),
                        "totalTransactionFeesPast24Hours", Txns.getTotalTxnFeesPast24Hours(),
                        "totalTransactionFeesPercentageChangeComparedToPreviousDay", Txns.getTotalTxnFeesPercentageChangeComparedToPreviousDay(),
                        "averageTransactionFeePast24Hours", Txns.getAverageTxnFeePast24Hours(),
                        "averageTransactionFeePercentageChangeComparedToPreviousDay", Txns.getAverageTxnFeePercentageChangeComparedToPreviousDay(),
                        "transactions",transactions);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });
        get("/transactionDetails/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String txnHash = request.queryParams("txnHash").toLowerCase();

                Txn txn = Txns.getTxn(txnHash);
                if (txn == null) return getError(response, "Invalid Txn Hash");

                String data;
                if(txn.getTxnData() == null) data = "0x";
                else data = "0x" + bytesToHex(txn.getTxnData());

                return getSuccess(
                        "txnHash", txnHash,
                        "txnType", txn.getTxnType(),
                        "size", txn.getSize(),
                        "blockNumber", txn.getBlockNumber(),
                        "timeStamp", txn.getTimestamp(),
                        "from", "0x" + txn.getFromAddress(),
                        "to", txn.getToAddress(),
                        "value", txn.getValue(),
                        "valueInUsd", txn.getValue(),
                        "txnFee", txn.getTxnFee(),
                        "txnFeeInUsd", txn.getTxnFee(),
                        "data", data,
                        "success", txn.getSuccess(),
                        "error_message", txn.getErrorMessage()
                );
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
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

                return  getSuccess("balance", balance, "balanceUsdValue", usdValueBigDec);
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
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

//        get("/transactionHistory/", (request, response) -> {
//            try {
//                response.type("application/json");
//                response.header("Content-Encoding", "gzip");
//
//                String address = request.queryParams("address").toLowerCase();
//                int count = Integer.parseInt(request.queryParams("count"));
//                int page = Integer.parseInt(request.queryParams("page"));
//
//                address = address.substring(2);
//
//                long startFetchTime = System.currentTimeMillis();
//                List<Txn> txns = getUserTxns(address, page, count);
//                long endFetchTime = System.currentTimeMillis();
//                logger.info(">>>Time taken for getUserTxns: " + (endFetchTime - startFetchTime) + " ms");
//
//                response.raw().setHeader("Transfer-Encoding", "chunked");
//
//                try (PrintWriter writer = new PrintWriter(new GZIPOutputStream(response.raw().getOutputStream()))) {
//                    writer.write("[");
//                    boolean first = true;
//                    for (Txn txn : txns) {
//                        if (!first) {
//                            writer.write(",");
//                        } else {
//                            first = false;
//                        }
//                        writer.write(JsonUtil.toJson(txn));
//                        writer.flush(); // Ensure data is sent to the client as it's written
//                    }
//                    writer.write("]");
//                }
//                long end = System.currentTimeMillis();
//                logger.info(">>>Time taken to stream data: " + (end - endFetchTime) + " ms");
//                return null;
//            } catch (Exception e) {
//                e.printStackTrace();
//                return getError(response, e.getLocalizedMessage());
//            }
//        });

        get("/transactionHistory/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String address = request.queryParams("address").toLowerCase();
                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                address = address.substring(2);
                long startCountTimeOne = System.currentTimeMillis();
                List<Txn> txns = getUserTxns(address, page, count);
                long endCountTimeOne = System.currentTimeMillis();
                logger.info(">>>Time taken for userTxns: " + (endCountTimeOne - startCountTimeOne) + " ms");

                long startCountTimeTwo = System.currentTimeMillis();
                int totalTxnCount = getTotalTxnCount(address);
                long endCountTimeTwo = System.currentTimeMillis();
                logger.info(">>>Time taken for getTotalTxnCount: " + (endCountTimeTwo - startCountTimeTwo) + " ms");
                int totalPages = (int) Math.ceil((double) totalTxnCount / count);

                if (page > totalPages) {
                    return getError(response, "Page number is greater than addresses' total pages");
                }

                JSONArray txnsArray = new JSONArray();
                long start = System.currentTimeMillis();

                for (Txn txn : txns) {
                    JSONObject object = new JSONObject();

                    object.put("txnHash", txn.getHash());
                    object.put("block", txn.getBlockNumber());
                    object.put("txnType", txn.getTxnType());
                    object.put("timeStamp", txn.getTimestamp());
                    object.put("from", "0x" +txn.getFromAddress());
                    object.put("to", txn.getToAddress());
                    object.put("txnFee", txn.getTxnFee());
                    object.put("value", txn.getValue());
                    object.put("valueInUsd", txn.getValue());
                    object.put("txnFeeInUsd", txn.getTxnFee());
                    object.put("nonceOrValidationHash", txn.getNonceOrValidationHash());
                    object.put("success",txn.getSuccess());
                    object.put("error_message", txn.getErrorMessage());

                    txnsArray.put(object);
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", count);
                metadata.put("totalItems", totalTxnCount);
                metadata.put("startIndex", (page - 1) * count + 1);
                metadata.put("endIndex", Math.min(page * count, totalTxnCount));
                metadata.put("nextPage", page < totalPages ? page + 1 : -1);
                metadata.put("previousPage", page > 1 ? page - 1 : -1);
                long end = System.currentTimeMillis();
                logger.info(">>>Time taken for loops: " + (end - start) + " ms");
                return getSuccess("transactions", txnsArray, "metadata", metadata);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/isTxnProcessed/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String txnHash = request.queryParams("txnHash").toLowerCase();

                Txn txn = Txns.getTxn(txnHash);
                if(txn == null) return getSuccess("isProcessed", false);
                else return getSuccess("isProcessed", true);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        //Staking app calls
        get("/staking/homepageInfo/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                List<Validator> activeValidators = pwrj.getActiveValidators();
                for(Validator val: activeValidators){
                    System.out.println(">>>validatorrr"+ val.getAddress());
                }
                List<Validator> standbyValidators = pwrj.getStandbyValidators();
                int totalValidatorsCount = activeValidators.size() + standbyValidators.size();
                int availablePages = totalValidatorsCount / count;
                if(totalValidatorsCount % count != 0) ++availablePages;

                logger.info("Active validators count: {}", activeValidators.size());
                logger.info("Standby validators count: {}", standbyValidators.size());
                logger.info("Total validators count: {}", totalValidatorsCount);
                if(page > availablePages) return getError(response, "Page number is greater than the total available pages: " + availablePages);

                //APY calculation for active validators

                //check APY of active validators for the past 34,560 blocks (2 days)
//                long currentBlockNumber = Blocks.getLatestBlockNumber();
//                long blockNumberToCheck = currentBlockNumber - 34560;
//                if(blockNumberToCheck < 0) blockNumberToCheck = 1;
//                long timeDifference = Blocks.getBlock(currentBlockNumber).getTimeStamp() - Blocks.getBlock(blockNumberToCheck).getTimeStamp();

                List<String> activeValidatorsList = new LinkedList<>();
                for(Validator validator : activeValidators) activeValidatorsList.add(validator.getAddress());
//                BigDecimal oldShareValues = PWRJ.getShareValue(activeValidatorsList, blockNumberToCheck);
//                JSONObject currentShareValues = PWRJ.getShareValue(activeValidatorsList, currentBlockNumber);

//                Map<String, BigDecimal> apyByValidator = new HashMap<>();
//                for(String validator: activeValidatorsList) {
//                    BigDecimal oldShareValue;
//                    if(oldShareValues.has(validator)) {
//                        oldShareValue = oldShareValues.getBigDecimal(validator);
//                    } else {
//                        oldShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
//                    }
//
//                    BigDecimal currentShareValue;
//
//                    if(currentShareValues.has(validator)) {
//                        currentShareValue = currentShareValues.getBigDecimal(validator);
//                    } else {
//                        currentShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
//                    }
//
//                    BigDecimal apy = currentShareValue.subtract(oldShareValue).divide(oldShareValue, 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(31536000)).divide(BigDecimal.valueOf(timeDifference), 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100));
//                    apyByValidator.put(validator, apy);
//                }

                //Get the validators for the requested page

                int startingIndex = (page - 1) * count;
                JSONArray validatorsArray = new JSONArray();
                long activeVotingPower = pwrj.getActiveVotingPower();
                for(int t = startingIndex; t < totalValidatorsCount; ++t) {
                    Validator validator;

                    if(t >= activeValidators.size()) {
                        validator = standbyValidators.get(t - activeValidators.size());
                    }
                    else {
                        validator = activeValidators.get(t);
                    }

                    if(validator == null) continue;

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
                        "activeValidatorsCount", pwrj.getActiveValidatorsCount(),
                        "activeVotingPower", BigDecimal.valueOf(pwrj.getActiveVotingPower()).divide(BigDecimal.TEN.pow(9), 0, BigDecimal.ROUND_HALF_UP),
                        "delegatorsCount", pwrj.getTotalDelegatorsCount(),
                        "validators", validatorsArray,
                        "metadata", metadata);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/staking/validatorInfo/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String validatorAddress = request.queryParams("validatorAddress");
                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                Validator v = pwrj.getValidator(validatorAddress);
                System.out.println(">>Validator address "+ v.getAddress());
                if(v == null) return getError(response, "Invalid Validator Address");

                int totalPages = v.getDelegatorsCount() / count;
                if(v.getDelegatorsCount() % count != 0) ++totalPages;
                int startingIndex = (page - 1) * count;

                List<Delegator> delegators = v.getDelegators(pwrj);
                JSONArray delegatorsArray = new JSONArray();
                for (int t = startingIndex; t < v.getDelegatorsCount(); ++t) {
                    Delegator delegator = delegators.get(t);
                    if(delegator == null) continue;

                    JSONObject object = new JSONObject();

                    object.put("address", delegator.getAddress());
                    object.put("delegatedPWR", BigDecimal.valueOf(delegator.getDelegatedPWR()).divide(BigDecimal.TEN.pow(9), 0, BigDecimal.ROUND_HALF_UP));

                    delegatorsArray.put(object);
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
                        "joiningDate", Validators.getJoinTime(validatorAddress),
                        "hosting", "vps",
                        "website", "null",
                        "description", "null",
                        "delegators", delegatorsArray,
                        "metadata", metadata);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });
        get("/staking/portfolio/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String userAddress = request.queryParams("userAddress");

                User u = Users.getUser(userAddress);
                if(u == null) return getError(response, "Invalid User Address");

                List<String> delegatedValidators = u.getDelegatedValidators();

                long totalDelegatedPWR = 0;
                long totalRewards = 0;
                for(String validator: delegatedValidators) {
                    long delegatedPWR = u.getDelegatedAmount(validator);
                    totalDelegatedPWR += delegatedPWR;
                    totalRewards += pwrj.getDelegatedPWR(userAddress, validator) - delegatedPWR;
                }

                JSONArray portfolioAllocation = new JSONArray();
                for(String validator: delegatedValidators) {
                    long delegatedPWR = u.getDelegatedAmount(validator);

                    JSONObject object = new JSONObject();
                    object.put("validatorName", validator);
                    object.put("portfolioShare", BigDecimal.valueOf(delegatedPWR).divide(BigDecimal.valueOf(totalDelegatedPWR), 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)));

                    portfolioAllocation.put(object);
                }

                JSONArray validatorsArray = new JSONArray();
                long currentBlockNumber = Blocks.getLatestBlockNumber();
                long blockNumberToCheck = currentBlockNumber - 34560;
                if(blockNumberToCheck < 0) blockNumberToCheck = 1;
//                long timeDifference = Blocks.getBlock(currentBlockNumber).getTimeStamp() - Blocks.getBlock(blockNumberToCheck).getTimeStamp();
//                JSONObject oldShareValues = PWRJ.getShareValue(delegatedValidators, blockNumberToCheck);
//                JSONObject currentShareValues = PWRJ.getShareValue(delegatedValidators, currentBlockNumber);

//                Map<String, BigDecimal> apyByValidator = new HashMap<>();
//                for(String validator: delegatedValidators) {
//                    BigDecimal oldShareValue;
//                    if(oldShareValues.has(validator)) {
//                        oldShareValue = oldShareValues.getBigDecimal(validator);
//                    } else {
//                        oldShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
//                    }
//
//                    BigDecimal currentShareValue;
//
//                    if(currentShareValues.has(validator)) {
//                        currentShareValue = currentShareValues.getBigDecimal(validator);
//                    } else {
//                        currentShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
//                    }
//
//                    BigDecimal apy = currentShareValue.subtract(oldShareValue).divide(oldShareValue, 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(31536000)).divide(BigDecimal.valueOf(timeDifference), 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100));
//                    apyByValidator.put(validator, apy);
//                }

                for(String validator: delegatedValidators) {
                    Validator v = pwrj.getValidator(validator);

                    JSONObject object = new JSONObject();
                    object.put("name", validator);
                    object.put("status", v.getStatus());
                    object.put("votingPower", v.getVotingPower());
                    object.put("totalPowerShare", BigDecimal.valueOf(v.getVotingPower()).divide(BigDecimal.valueOf(pwrj.getActiveVotingPower()), 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)));
                    object.put("delegatedPWR", u.getDelegatedAmount(validator));
                    object.put("apy", 5);
                }

                return getSuccess(
                        "totalDelegatedPWR", totalDelegatedPWR,
                        "totalRewards", totalRewards,
                        "portfolioAllocation", portfolioAllocation,
                        "validators", validatorsArray);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
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

    public static void main(String[] args) {
        logger.info(1 / 2);
    }
}

