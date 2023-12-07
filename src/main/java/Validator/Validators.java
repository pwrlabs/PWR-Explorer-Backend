package Validator;

import java.util.HashMap;
import java.util.Map;

public class Validators {
    private static Map<String, Long> validatorJoinTime = new HashMap<>();

    public static void add(String validatorAddress, long joinTime) {
        validatorJoinTime.put(validatorAddress.toLowerCase(), joinTime);
    }

    public static long getJoinTime(String validatorAddress) {
        return validatorJoinTime.getOrDefault(validatorAddress.toLowerCase(), 0L);
    }
}
