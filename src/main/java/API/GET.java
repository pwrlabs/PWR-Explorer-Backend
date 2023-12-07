package API;


import Block.Blocks;
import Block.Block;
import Main.OHTTP;
import Main.Settings;
import User.Users;
import User.User;
import Validator.Validators;
import com.github.pwrlabs.pwrj.Delegator.Delegator;
import com.github.pwrlabs.pwrj.Validator.Validator;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.json.JSONArray;
import org.json.JSONObject;
import Txn.Txns;
import Txn.Txn;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static spark.Spark.get;

public class GET {
    public static void run() {

        //Explorer Calls
        get("/explorerInfo/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                JSONArray blocks = new JSONArray();
                JSONArray txns = new JSONArray();

                //Fetch the latest 5 blocks
                long blockNumberToCheck = Blocks.getLatestBlockNumber();
                for(int t=0; t < 5; ++t) {
                    Block block = Blocks.getBlock(blockNumberToCheck - t);
                    if(block == null) break;

                    JSONObject object = new JSONObject();

                    object.put("blockHeight", blockNumberToCheck - t);
                    object.put("timeStamp", block.getTimeStamp());
                    object.put("txnsCount", block.getTxnsCount());
                    object.put("blockReward", block.getBlockReward());
                    object.put("blockSubmitter", "0x" + bytesToHex(block.getBlockSubmitter()));

                    blocks.put(object);
                }

                //Fetch the latest 5 txns
                int fetchedTxns = 0;
                for(int t=0; fetchedTxns < 5; ++t) {
                    Block block = Blocks.getBlock(blockNumberToCheck - t);
                    if(block == null) break;

                    for(Txn txn : block.getTxns()) {
                        if(txn == null) continue;
                        JSONObject object = new JSONObject();

                        object.put("txnHash", txn.getTxnHash());
                        object.put("timeStamp", txn.getTimeStamp());
                        object.put("from", "0x" + bytesToHex(txn.getFrom()));
                        object.put("to", txn.getTo());
                        object.put("value", txn.getValue());

                        txns.put(object);
                        ++fetchedTxns;

                        if(fetchedTxns == 5) break;
                    }
                }

                return getSuccess(
                        "price", Settings.getPrice(),
                        "priceChange", 2.5,
                        "marketCap", 1000000000L,
                        "totalTransactionsCount", Txns.getTxnCount(),
                        "blocksCount", blockNumberToCheck,
                        "validators", PWRJ.getActiveValidatorsCount(),
                        "tps", Blocks.getAverageTps(100),
                        "txns", txns,
                        "blocks", blocks
                );


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

                for(long blockNumberToCheck = latestBlockNumber - previousBlocksCount;
                    blocksCount < count;
                    --blockNumberToCheck, ++blocksCount
                ) {
                    Block block = Blocks.getBlock(blockNumberToCheck);
                    if(block == null) break;

                    JSONObject object = new JSONObject();

                    object.put("blockHeight", blockNumberToCheck);
                    object.put("timeStamp", block.getTimeStamp());
                    object.put("txnsCount", block.getTxnsCount());
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

                Block block = Blocks.getBlock(blockNumber);
                if(block == null) return getError(response, "Invalid Block Number");

                return getSuccess(
                        "blockHeight", blockNumber,
                        "timeStamp", block.getTimeStamp(),
                        "txnsCount", block.getTxnsCount(),
                        "blockSize", block.getBlockSize(),
                        "blockReward", block.getBlockReward(),
                        "blockSubmitter", "0x" + bytesToHex(block.getBlockSubmitter()),
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

                Block block = Blocks.getBlock(blockNumber);
                if(block == null) return getError(response, "Invalid Block Number");

                int txnsCount = 0;
                JSONArray txns = new JSONArray();
                Txn[] txnsArray = block.getTxns();

                if(txnsArray == null) totalTxnCount = 0;
                else totalTxnCount = txnsArray.length;

                totalPages = totalTxnCount / count;
                if(totalTxnCount % count != 0) ++totalPages;

                for(int t = previousTxnsCount; t < txnsArray.length; ++t) {
                    Txn txn = txnsArray[t];
                    if(txn == null) continue;
                    if(txnsCount == count) break;

                    JSONObject object = new JSONObject();

                    object.put("txnHash", txn.getTxnHash());
                    object.put("txnType", txn.getTxnType());
                    object.put("blockNumber", blockNumber);
                    object.put("timeStamp", txn.getTimeStamp());
                    object.put("from", "0x" + bytesToHex(txn.getFrom()));
                    object.put("to", txn.getTo());
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

        get("/latestTransactions/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                int previousTxnsCount = (page - 1) * count;

                int fetchedTxns = 0;
                JSONArray txnsArray = new JSONArray();

                for(int t=0; fetchedTxns < count + previousTxnsCount; ++t) {
                    long blockNumber = Blocks.getLatestBlockNumber() - t;
                    Block block = Blocks.getBlock(blockNumber);
                    if(block == null) break;

                    Txn[] txns = block.getTxns();
                    for(Txn txn : txns) {
                        if(txn == null) continue;

                        if(fetchedTxns < previousTxnsCount) {
                            ++fetchedTxns;
                            continue;
                        }

                        JSONObject object = new JSONObject();

                        object.put("txnHash", txn.getTxnHash());
                        object.put("block", blockNumber);
                        object.put("txnType", txn.getTxnType());
                        object.put("timeStamp", txn.getTimeStamp());
                        object.put("from", "0x" + bytesToHex(txn.getFrom()));
                        object.put("to", txn.getTo());
                        object.put("txnFee", txn.getTxnFee());
                        object.put("value", txn.getValue());
                        object.put("valueInUsd", txn.getValueInUsd());
                        object.put("txnFeeInUsd", txn.getTxnFeeInUsd());
                        object.put("nonceOrValidationHash", txn.getNonceOrValidationHash());
                        object.put("positionInBlock", txn.getPositionInTheBlock());

                        txnsArray.put(object);
                        ++fetchedTxns;
                        if(fetchedTxns == count + previousTxnsCount) break;
                    }
                }

                int totalTxnCount = Txns.getTxnCount();
                int totalPages = totalTxnCount / count;
                if(totalTxnCount % count != 0) ++totalPages;

                JSONObject metadata = new JSONObject();
                metadata.put("totalTxns", Txns.getTxnCount());
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


                return getSuccess(
                        "transactionCountPast24Hours", Txns.getTxnCountPast24Hours(),
                        "transactionCountPercentageChangeComparedToPreviousDay", Txns.getTxnCountPercentageChangeComparedToPreviousDay(),
                        "totalTransactionFeesPast24Hours", Txns.getTotalTxnFeesPast24Hours(),
                        "totalTransactionFeesPercentageChangeComparedToPreviousDay", Txns.getTotalTxnFeesPercentageChangeComparedToPreviousDay(),
                        "averageTransactionFeePast24Hours", Txns.getAverageTxnFeePast24Hours(),
                        "averageTransactionFeePercentageChangeComparedToPreviousDay", Txns.getAverageTxnFeePercentageChangeComparedToPreviousDay(),
                        "transactions", txnsArray,
                        "metadata", metadata);
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
                if(txn.getData() == null) data = "0x";
                else data = "0x" + bytesToHex(txn.getData());

                return getSuccess(
                        "txnHash", txnHash,
                        "txnType", txn.getTxnType(),
                        "size", txn.getSize(),
                        "blockNumber", txn.getBlockNumber(),
                        "timeStamp", txn.getTimeStamp(),
                        "from", "0x" + bytesToHex(txn.getFrom()),
                        "to", txn.getTo(),
                        "value", txn.getValue(),
                        "valueInUsd", txn.getValueInUsd(),
                        "txnFee", txn.getTxnFee(),
                        "txnFeeInUsd", txn.getTxnFeeInUsd(),
                        "data", data
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

                long balance = PWRJ.getBalanceOfAddress(address);
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

                return getSuccess("nonce", PWRJ.getNonceOfAddress(userAddress));
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/transactionHistory/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String address = request.queryParams("address").toLowerCase();
                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                User user = Users.getUser(address);
                if(user == null) {
                    JSONObject metadata = new JSONObject();
                    metadata.put("totalPages", 0);
                    metadata.put("currentPage", page);
                    metadata.put("itemsPerPage", 0);
                    metadata.put("totalItems", 0);
                    metadata.put("startIndex", 0);

                    System.out.println("No user found");
                    return getSuccess("transactions", new JSONArray(),
                            "metadata", metadata,
                            "hashOfFirstTxnSent", "null",
                            "timeOfFirstTxnSent", "null",
                            "hashOfLastTxnSent", "null",
                            "timeOfLastTxnSent", "null");
                }

                List<Txn> txns = user.getTxns();
                int totalTxnCount = txns.size();
                int totalPages = totalTxnCount / count;
                if(totalTxnCount % count != 0) ++totalPages;
                int previousTxnsCount = (page - 1) * count;

                if(page > totalPages) return getError(response, "Page number is greater than addresses' total pages");

                JSONArray txnsArray = new JSONArray();

                int fetchedTxns = 0;
                for(int t = previousTxnsCount; fetchedTxns++ < count && t < totalTxnCount; ++t) {
                    Txn txn = txns.get(totalTxnCount - t - 1);
                    JSONObject object = new JSONObject();

                    object.put("txnHash", txn.getTxnHash());
                    object.put("block", txn.getBlockNumber());
                    object.put("txnType", txn.getTxnType());
                    object.put("timeStamp", txn.getTimeStamp());
                    object.put("from", "0x" + bytesToHex(txn.getFrom()));
                    object.put("to", txn.getTo());
                    object.put("txnFee", txn.getTxnFee());
                    object.put("value", txn.getValue());
                    object.put("valueInUsd", txn.getValueInUsd());
                    object.put("txnFeeInUsd", txn.getTxnFeeInUsd());
                    object.put("nonceOrValidationHash", txn.getNonceOrValidationHash());


                    txnsArray.put(object);
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

                return getSuccess("transactions", txnsArray, "metadata", metadata, "hashOfFirstTxnSent", txns.get(0).getTxnHash(), "timeOfFirstTxnSent", txns.get(0).getTimeStamp(),
                        "hashOfLastTxnSent", txns.get(totalTxnCount - 1).getTxnHash(), "timeOfLastTxnSent", txns.get(totalTxnCount - 1).getTimeStamp());
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

                List<Validator> activeValidators = PWRJ.getAllValidators();
                List<Validator> standbyValidators = PWRJ.getStandbyValidators();
                int totalValidatorsCount = activeValidators.size() + standbyValidators.size();
                int availablePages = totalValidatorsCount / count;
                if(totalValidatorsCount % count != 0) ++availablePages;

                if(page > availablePages) return getError(response, "Page number is greater than the total available pages");

                //APY calculation for active validators

                //check APY of active validators for the past 34,560 blocks (2 days)
                long currentBlockNumber = Blocks.getLatestBlockNumber();
                long blockNumberToCheck = currentBlockNumber - 34560;
                if(blockNumberToCheck < 0) blockNumberToCheck = 1;
                long timeDifference = Blocks.getBlock(currentBlockNumber).getTimeStamp() - Blocks.getBlock(blockNumberToCheck).getTimeStamp();

                List<String> activeValidatorsList = new LinkedList<>();
                for(Validator validator : activeValidators) activeValidatorsList.add(validator.getAddress());
                JSONObject oldShareValues = PWRJ.getShareValue(activeValidatorsList, blockNumberToCheck);
                JSONObject currentShareValues = PWRJ.getShareValue(activeValidatorsList, currentBlockNumber);

                Map<String, BigDecimal> apyByValidator = new HashMap<>();
                for(String validator: activeValidatorsList) {
                    BigDecimal oldShareValue;
                    if(oldShareValues.has(validator)) {
                        oldShareValue = oldShareValues.getBigDecimal(validator);
                    } else {
                        oldShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
                    }

                    BigDecimal currentShareValue;

                    if(currentShareValues.has(validator)) {
                        currentShareValue = currentShareValues.getBigDecimal(validator);
                    } else {
                        currentShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
                    }

                    BigDecimal apy = currentShareValue.subtract(oldShareValue).divide(oldShareValue, 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(31536000)).divide(BigDecimal.valueOf(timeDifference), 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100));
                    apyByValidator.put(validator, apy);
                }

                //Get the validators for the requested page

                int startingIndex = (page - 1) * count;
                JSONArray validatorsArray = new JSONArray();
                long activeVotingPower = PWRJ.getActiveVotingPower();
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
                    object.put("votingPower", validator.getVotingPower());
                    object.put("totalPowerShare", BigDecimal.valueOf(validator.getVotingPower()).divide(BigDecimal.valueOf(activeVotingPower), 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)));
                    object.put("commission", 1);
                    object.put("apy", apyByValidator.getOrDefault(validator.getAddress(), BigDecimal.ZERO));
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
                        "activeValidatorsCount", PWRJ.getActiveValidatorsCount(),
                        "activeVotingPower", BigDecimal.valueOf(PWRJ.getActiveVotingPower()).divide(BigDecimal.TEN.pow(9), 0, BigDecimal.ROUND_HALF_UP),
                        "delegatorsCount", Users.getDelegatorsCount(),
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

                Validator v = PWRJ.getValidator(validatorAddress);
                if(v == null) return getError(response, "Invalid Validator Address");

                int totalPages = v.getDelegatorsCount() / count;
                if(v.getDelegatorsCount() % count != 0) ++totalPages;
                int startingIndex = (page - 1) * count;

                List<Delegator> delegators = v.getDelegators();
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
                    totalRewards += PWRJ.getDelegatedPWR(userAddress, validator) - delegatedPWR;
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
                long timeDifference = Blocks.getBlock(currentBlockNumber).getTimeStamp() - Blocks.getBlock(blockNumberToCheck).getTimeStamp();
                JSONObject oldShareValues = PWRJ.getShareValue(delegatedValidators, blockNumberToCheck);
                JSONObject currentShareValues = PWRJ.getShareValue(delegatedValidators, currentBlockNumber);

                Map<String, BigDecimal> apyByValidator = new HashMap<>();
                for(String validator: delegatedValidators) {
                    BigDecimal oldShareValue;
                    if(oldShareValues.has(validator)) {
                        oldShareValue = oldShareValues.getBigDecimal(validator);
                    } else {
                        oldShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
                    }

                    BigDecimal currentShareValue;

                    if(currentShareValues.has(validator)) {
                        currentShareValue = currentShareValues.getBigDecimal(validator);
                    } else {
                        currentShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
                    }

                    BigDecimal apy = currentShareValue.subtract(oldShareValue).divide(oldShareValue, 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(31536000)).divide(BigDecimal.valueOf(timeDifference), 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100));
                    apyByValidator.put(validator, apy);
                }

                for(String validator: delegatedValidators) {
                    Validator v = PWRJ.getValidator(validator);

                    JSONObject object = new JSONObject();
                    object.put("name", validator);
                    object.put("status", v.getStatus());
                    object.put("votingPower", v.getVotingPower());
                    object.put("totalPowerShare", BigDecimal.valueOf(v.getVotingPower()).divide(BigDecimal.valueOf(PWRJ.getActiveVotingPower()), 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)));
                    object.put("delegatedPWR", u.getDelegatedAmount(validator));
                    object.put("apy", apyByValidator.getOrDefault(validator, BigDecimal.ZERO));
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
        System.out.println(1 / 2);
    }
}

