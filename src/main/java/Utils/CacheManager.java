package Utils;

import DTOs.BlockDTO;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {
    private static final Map<Long, BlockDTO> blockCache = new ConcurrentHashMap<>();

    public static BlockDTO getBlockDetails(long blockNumber) {
        return blockCache.get(blockNumber);
    }

    public static void cacheBlockDetails(long blockNumber, BlockDTO blockDTO) {
        blockCache.put(blockNumber, blockDTO);
    }
}

