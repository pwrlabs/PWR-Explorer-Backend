package Services;

import Core.Cache.CacheManager;
import Database.Repository.Blocks.BlocksRepo;
import Database.Repository.Blocks.BlocksRepoImpl;
import com.github.pwrlabs.pwrj.entities.Validator;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Request;
import spark.Response;
import Database.Queries;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static Utils.Helpers.*;
import static Utils.ResponseBuilder.getError;
import static Utils.ResponseBuilder.getSuccess;

public class NodeService {
//    private static final Logger logger = LogManager.getLogger(NodeService.class);
    private static CacheManager cacheManager;
    private static BlocksRepo blocksRepo;
    private static PWRJ pwrj;

    public static void initialize(PWRJ pwrjInstance) {
        pwrj = pwrjInstance;
        cacheManager = new CacheManager(pwrj);
        blocksRepo = new BlocksRepoImpl();
    }

    public static Object getNodesInfo(Request request, Response response) throws Exception {
        response.header("Content-Type", "application/json");

        int count = validateAndParseCountParam(request.queryParams("count"), response);
        int page = validateAndParsePageParam(request.queryParams("page"), response);
        int offset = (page - 1) * count;

        int totalNodesCount;

        try {
            BigDecimal activeVotingPower = new BigDecimal(cacheManager.getActiveVotingPower());
            BigDecimal hundredBD = BigDecimal.valueOf(100L);
            BigDecimal millionBD = BigDecimal.valueOf(1000000000L);

            List<Validator> nodes = cacheManager.getActiveValidators();
            totalNodesCount = nodes.size();
            int standbyNodesCount = cacheManager.getStandByValidatorsCount();
            long totalVotingPower = cacheManager.getTotalVotingPower();

            int startIndex = Math.min(offset, totalNodesCount);
            int endIndex = Math.min(offset + count, totalNodesCount);
            List<Validator> paginatedNodes = nodes.subList(startIndex, endIndex);

            JSONArray nodesArray = new JSONArray();

            for (Validator node : paginatedNodes) {
                String address = node.getAddress();

                BigDecimal votingPower = new BigDecimal(node.getVotingPower())
                        .divide(activeVotingPower, 7, RoundingMode.HALF_UP)
                        .multiply(hundredBD)
                        .setScale(5, RoundingMode.HALF_UP);

                BigDecimal sharesInPwr = new BigDecimal(Queries.getLifetimeRewards(address))
                        .divide(millionBD, 9, RoundingMode.HALF_EVEN);

                nodesArray.put(new JSONObject()
                        .put("address", returnHexStringWith0x(address))
                        .put("host", node.getIp())
                        .put("votingPowerInPercentage", votingPower)
                        .put("votingPowerInPwr", node.getVotingPower())
                        .put("earnings", sharesInPwr)
                        .put("blocksSubmitted", blocksRepo.getBlocksSubmitted(address)));
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
                    "blocksCreated", blocksRepo.getBlocksSubmitted(address),
                    "timeOfLastBlock", blocksRepo.getLatestBlockNumberForFeeRecipient(address.substring(2)) / 1000
            );
        } catch (Exception e) {
            return getError(response, e.getLocalizedMessage());
        }
    }

}