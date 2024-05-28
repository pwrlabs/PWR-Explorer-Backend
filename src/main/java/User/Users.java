package User;

import java.util.HashMap;
import java.util.Map;

import static Core.Sql.Queries.*;

public class Users {
    private static Map<String /*Address*/, User> userByAddress = new HashMap<>();

    public static void add(User user) {
        userByAddress.put(user.getAddress(), user);
    }

    public static User getUser(String address) {
        address = address.toLowerCase().trim();
        User user = userByAddress.get(address);
        if (user == null) {
            user = getDbUser(address);
            if (user != null) {
                userByAddress.put(address, user);
            }
        }
        return user;
    }

    public static User getUser(byte[] address) {
        return getUser("0x" + bytesToHex(address));
    }

    public static User getUserCreateIfMissing(String address) {
        address = address.toLowerCase().trim();
        User user = getUser(address);
        if (user == null) {
            if (!dbUserExists(address)) {
                insertUser(address, null, null, null);
            }
            user = getDbUser(address);
            userByAddress.put(address, user);
        }
        return user;
    }

    public static User getUserCreateIfMissing(byte[] address) {
        return getUserCreateIfMissing("0x" + bytesToHex(address));
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }
}