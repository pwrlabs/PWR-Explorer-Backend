package Core.Sql;

import Block.Block;
import Main.Hex;
import Main.Settings;
import Txn.Txn;
import User.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static Core.Constants.Constants.ADDRESS;
import static Core.Constants.Constants.AMOUNT_USD_VALUE;
import static Core.Constants.Constants.BLOCK_NUMBER;
import static Core.Constants.Constants.BLOCK_REWARD;
import static Core.Constants.Constants.BLOCK_SIZE;
import static Core.Constants.Constants.BLOCK_SUBMITTER;
import static Core.Constants.Constants.DATA;
import static Core.Constants.Constants.DELEGATOR_COUNT;
import static Core.Constants.Constants.FEE_USD_VALUE;
import static Core.Constants.Constants.FROM_ADDRESS;
import static Core.Constants.Constants.HASH;
import static Core.Constants.Constants.INITIAL_DELEGATIONS;
import static Core.Constants.Constants.NONCE_OR_VALIDATION;
import static Core.Constants.Constants.POSITION_IN_BLOCK;
import static Core.Constants.Constants.SIZE;
import static Core.Constants.Constants.TIMESTAMP;
import static Core.Constants.Constants.TO;
import static Core.Constants.Constants.TO_ADDRESS;
import static Core.Constants.Constants.TXN_COUNT;
import static Core.Constants.Constants.TXN_FEE;
import static Core.Constants.Constants.TXN_TYPE;
import static Core.Constants.Constants.VALUE;
import static Core.DatabaseConnection.getConnection;

public class Queries {
    private static final Logger logger = LogManager.getLogger(Queries.class);

    public static void insertUser(String address) {
        String sql = "INSERT INTO \"User\" ("+ADDRESS+", "+DELEGATOR_COUNT+") VALUES (?, ?);";
        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);
            preparedStatement.setInt(2, 0);

            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }

    public static JSONObject getInitialDeletations(String address) {
        String sql = "SELECT "+INITIAL_DELEGATIONS+" FROM \"User\" WHERE "+ADDRESS+" = ?;";
        JSONObject jsonObject = new JSONObject();
        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)){
            preparedStatement.setString(1, address);

            logger.info("QUERY: {}", preparedStatement.toString());

            try (ResultSet rs =  preparedStatement.executeQuery()) {
                while(rs.next()) {
                    String jsonString = rs.getString(INITIAL_DELEGATIONS);
                    if(jsonString != null) {
                        jsonObject = new JSONObject(jsonString);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
        return jsonObject;
    }

    public static User getDbUser(String address) {
        String sql = "SELECT * FROM \"User\" WHERE "+ADDRESS+" = ?;";
        User user = null;
        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);

            logger.info("QUERY: {}", preparedStatement.toString());

            try(ResultSet rs = preparedStatement.executeQuery()) {
                while(rs.next()) {
                    String retrievedAddress = rs.getString(ADDRESS);
                    if(retrievedAddress == null) {
                         user = null;
                    }
                    user = new User(retrievedAddress);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
        return user;
    }

    public static boolean DbUserExists(String address) {
        String sql ="SELECT COUNT(*) AS count FROM \"User\" WHERE "+ADDRESS+" = ?;";
        int count = 0;

        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);
            try(ResultSet rs = preparedStatement.executeQuery()) {
                count = rs.getInt("count");
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
        return count>0;
    }

    public static void updateInitialDelegations(String address, String json) {  //TODO: not tested
        String sql = "UPDATE \"User\" SET "+INITIAL_DELEGATIONS+" = CAST(? AS JSON) WHERE "+ADDRESS+" = ?;";

        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, json);
            preparedStatement.setString(2, address);

            logger.info("QUERY: {}", preparedStatement);

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }

    public static void insertBlock(String blockNumber, long timeStamp, byte[] blockSubmitter, long blockReward, int blockSize, int txnCount) {
        String sql = "INSERT INTO \"Block\" ("+
                BLOCK_NUMBER+", "+
                TIMESTAMP+", "+
                BLOCK_SUBMITTER+", "+
                BLOCK_REWARD+", "+
                BLOCK_SIZE+", "+
                TXN_COUNT+
                ") VALUES (?, ?, ?, ?, ?, ?);";

        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, blockNumber);
            preparedStatement.setLong(2, timeStamp);
            preparedStatement.setString(3, org.bouncycastle.util.encoders.Hex.toHexString(blockSubmitter));
            preparedStatement.setLong(4, blockReward);
            preparedStatement.setInt(5, blockSize);
            preparedStatement.setInt(6, txnCount);

            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        }catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }

    public static Block getDbBlock(String blockNumber) {
        String sql = "SELECT * FROM \"Block\" WHERE " +BLOCK_NUMBER+ " = ?;";

//        Block block = new Block(blockNumber);

        Block block = null;
        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, blockNumber);

            logger.info("QUERY: {}", preparedStatement.toString());

            try(ResultSet rs = preparedStatement.executeQuery()) {
                while(rs.next()) {
                    block = new Block(
                            blockNumber,
                            rs.getLong(TIMESTAMP),
                            Hex.decode(rs.getString(BLOCK_SUBMITTER)),
                            rs.getLong(BLOCK_REWARD),
                            rs.getInt(BLOCK_SIZE),
                            rs.getInt(TXN_COUNT)
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        }
        return block;
    }

    public static long getLastBlockNumber() {
//        String sql = "SELECT "+BLOCK_NUMBER+" FROM \"Block\" ORDER BY "+BLOCK_NUMBER+" DESC LIMIT 1";
        String sql = "SELECT MAX(CAST("+BLOCK_NUMBER+" AS BIGINT)) FROM \"Block\";";

        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql);
            ResultSet rs = preparedStatement.executeQuery()) {
            while(rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        }
        return 0;
    }

    public static void insertTxn(String hash, int size, int positionInTheBlock, long blockNumber, byte[] from, String to, long value, long txnFee, byte[] data, String txnType, String nonceOrValidationHash) {
        String sql = "INSERT INTO \"Txn\" (" +
                HASH +"," +
                SIZE +"," +
                POSITION_IN_BLOCK +"," +
                BLOCK_NUMBER +"," +
                FROM_ADDRESS +"," +
                TO +"," +
                VALUE +"," +
                TXN_FEE +"," +
                DATA +"," +
                TXN_TYPE +"," +
                NONCE_OR_VALIDATION +","+
                TO_ADDRESS+","+
                AMOUNT_USD_VALUE+","+
                FEE_USD_VALUE+
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?);";

        String from_address = "0x"+ org.bouncycastle.util.encoders.Hex.toHexString(from);

        if(getDbUser(from_address) == null) {
            insertUser(from_address);
        }

        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, hash);
            preparedStatement.setInt(2, size);
            preparedStatement.setInt(3, positionInTheBlock);
            preparedStatement.setLong(4, blockNumber);
            preparedStatement.setString(5,from_address);
            preparedStatement.setString(6, to);
            preparedStatement.setLong(7, value);
            preparedStatement.setLong(8, txnFee);
            if(data != null) {
                preparedStatement.setString(9, org.bouncycastle.util.encoders.Hex.toHexString(data));
            } else {
                preparedStatement.setString(9, null);
            }
            preparedStatement.setString(10, txnType);
            preparedStatement.setString(11, nonceOrValidationHash);

            if(value > 0) {
                double usdValue = (value * Settings.getPrice());
                BigDecimal result = BigDecimal.valueOf(usdValue).divide(BigDecimal.valueOf((long) Math.pow(10, 11)));
                DecimalFormat df = new DecimalFormat("#.0000");
                BigDecimal formattedResult = new BigDecimal(df.format(result));
                preparedStatement.setBigDecimal(13, formattedResult);
            } else {
                preparedStatement.setBigDecimal(13, new BigDecimal(0));
            }

            //fee USD Value
            double usdFeeValue = (txnFee * Settings.getPrice());
            BigDecimal feeResult = BigDecimal.valueOf(usdFeeValue).divide(BigDecimal.valueOf((long) Math.pow(10, 11)));
            DecimalFormat feeDf = new DecimalFormat("#.00000000");
            BigDecimal formattedFeeResult = new BigDecimal(feeDf.format(feeResult));
            preparedStatement.setBigDecimal(14, formattedFeeResult);

            if(txnType.equalsIgnoreCase("transfer")) {
                if(getDbUser(to)==null) {
                    insertUser(to);
                }
                preparedStatement.setString(12, to);
            } else {
                preparedStatement.setString(12, null);
            };

            logger.info("QUERY: {}", preparedStatement.toString());

            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
    }

    public static Txn getDbTxn(String hash) {
        String sql = "SELECT * FROM \"Txn\" WHERE "+HASH+" = ?";
        Txn txn = null;

        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, hash);
            try(ResultSet rs = preparedStatement.executeQuery()) {
                while(rs.next()) {
                    txn = populateTxnObject(rs);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
        return txn;
    }

    public static Txn[] getBlockTxns(String blockNumber) {
        String sql = "SELECT * FROM \"Txn\" " +
                "WHERE " +
                    BLOCK_NUMBER+" = ?" +
                " ORDER BY " +
                POSITION_IN_BLOCK+" ASC;";
        Queue<Txn> txnList = new LinkedList<>();

        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, blockNumber);

            logger.info("QUERY: {}", preparedStatement.toString());

            try(ResultSet rs = preparedStatement.executeQuery()) {
                while(rs.next()) {
                    Txn txn = populateTxnObject(rs);
                    if(!txnList.offer(txn)) {
                        throw new Exception("Failed to insert txn into queue");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
        Txn[] txn = new Txn[txnList.size()];

        for(int i = 0; i < txn.length; i++) {
            txn[i] = txnList.poll();
        }

        return txn;
    }

    public static List<Txn> getUserTxns(String address) {
        String sql = "SELECT * FROM \"Txn\" WHERE " + FROM_ADDRESS + " = ?";
        List<Txn> txns = new ArrayList<>();

        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, address);
            try(ResultSet rs = preparedStatement.executeQuery()) {
                while(rs.next()){
                    Txn txn = populateTxnObject(rs);
                    txns.add(txn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
        }
        return txns;
    }

//============================helpers=============================================
    private static Txn populateTxnObject(ResultSet rs) throws SQLException {
        String to = rs.getString(TO_ADDRESS);
        if(to == null) {
            to = rs.getString(TO);
        }

        String encodedData = rs.getString(DATA);
        byte[] data;
        if(encodedData != null) {
            data = Hex.decode(encodedData);
        } else {
            data = new byte[0];
        }
        return new Txn(
                rs.getString(HASH),
                rs.getInt(SIZE),
                rs.getInt(POSITION_IN_BLOCK),
                Long.parseLong(rs.getString(BLOCK_NUMBER)),
                Hex.decode(rs.getString(FROM_ADDRESS).substring(2)),
                to,
                rs.getLong(VALUE),
                rs.getLong(TXN_FEE),
                data,
                rs.getString(TXN_TYPE),
                rs.getString(NONCE_OR_VALIDATION)
        );
    }

}
