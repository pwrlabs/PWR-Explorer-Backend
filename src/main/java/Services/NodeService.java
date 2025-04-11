package Services;

import Core.Cache.CacheManager;
import com.github.pwrlabs.pwrj.entities.Validator;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Request;
import spark.Response;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static Database.Queries.getBlocksSubmitted;
import static Database.Queries.getLatestBlockNumberForFeeRecipient;
import static Utils.Helpers.*;
import static Utils.ResponseBuilder.getError;
import static Utils.ResponseBuilder.getSuccess;

public class NodeService {
    private static final Logger logger = LogManager.getLogger(NodeService.class);
    private static CacheManager cacheManager;
    private static PWRJ pwrj;

    public static void initialize(PWRJ pwrjInstance) {
        pwrj = pwrjInstance;
        cacheManager = new CacheManager(pwrj);
    }

    public static Object getNodesInfo(Request request, Response response) throws Exception {
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
    }

    public static Object getNodesStatus(Request request, Response response) {
        response.header("Content-Type", "application/json");
        String address = request.queryParams("userAddress").toLowerCase();

        try {
            Validator node = pwrj.getValidator(address);

            BigDecimal sparks = new BigDecimal(node.getShares());
            BigDecimal sharesInPwr = sparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);

            BigDecimal votingPowerSparks = new BigDecimal(node.getVotingPower());
            BigDecimal votingPowerInPwr = votingPowerSparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);

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
    }

}