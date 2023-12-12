package Validator;

import com.github.pwrlabs.dbm.SDBM;

import java.util.HashMap;
import java.util.Map;

public class Validators {

    public static void add(String validatorAddress, long joinTime) {
        SDBM.store("validators/joiningTime/" + validatorAddress.toLowerCase(), joinTime);
    }

    public static long getJoinTime(String validatorAddress) {
        return SDBM.loadLong("validators/joiningTime/" + validatorAddress.toLowerCase());
    }
}
