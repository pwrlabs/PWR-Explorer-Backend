package API;

import Main.OHTTP;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.json.JSONObject;

import static spark.Spark.post;

public class POST {

    public static void run() {
        post("/broadcast/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");
                JSONObject requestJson = new JSONObject(request.body());

                return OHTTP.sendPostRequest(PWRJ.getRpcNodeUrl() + "/broadcast/", requestJson);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });
    }

    public static JSONObject getError(spark.Response response, String message) {
        response.status(400);
        JSONObject object = new JSONObject();
        object.put("message", message);

        return object;
    }
}
