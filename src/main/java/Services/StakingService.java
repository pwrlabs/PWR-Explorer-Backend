package Services;

import Core.Cache.CacheManager;
import com.github.pwrlabs.pwrj.entities.Delegator;
import com.github.pwrlabs.pwrj.entities.Validator;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Request;
import spark.Response;

import java.math.BigDecimal;
import java.util.List;

import static Database.Queries.getTotalFees;
import static Database.Queries.getValidatorJoiningTime;
import static Utils.ResponseBuilder.getError;
import static Utils.ResponseBuilder.getSuccess;

public class StakingService {
    private static final Logger logger = LogManager.getLogger(StakingService.class);
    private static CacheManager cacheManager;
    private static PWRJ pwrj;

    public static void initialize(PWRJ pwrjInstance) {
        pwrj = pwrjInstance;
        cacheManager = new CacheManager(pwrj);
    }

    public static Object getHomePageInfo(Request request, Response response) {
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
    }

    public static Object getValidatorInfo(Request request, Response response) {
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
                    "website", null,
                    "description", null,
                    "delegators", delegatorsArray,
                    "metadata", metadata);
        } catch (Exception e) {
            return getError(response, e.getLocalizedMessage());
        }
    }

    public static Object getPortfolio(Request request, Response response) {
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
    }

    public static Object getStats(Request request, Response response) {
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
    }
}