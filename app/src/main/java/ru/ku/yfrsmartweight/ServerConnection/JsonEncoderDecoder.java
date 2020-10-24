package ru.ku.yfrsmartweight.ServerConnection;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

/*
    Класс реализует декодер и энкодер json объектов.
    При инициализации отправляется инициализационный json.
    Методы, начинающиеся с query, реализуют формирование json для запроса на плату.
    Методы, начинающиеся с get, парсят json в объекты для удобной работы с ними.
 */

public class JsonEncoderDecoder {

    private static final String TAG = "AppLogs";

    static public JSONObject initialisationJson () throws JSONException {
        JSONObject json = new JSONObject();
        json.put("query", "InitTablet");
        json.put("data", " ");
        return json;
    }

    static public JSONObject queryActualDishList () throws JSONException {
        JSONObject json = new JSONObject();
        json.put("query", "GetDishNames");
        json.put("data", " ");
        return json;
    }

    static public JSONObject downloadImage(String name, String image) throws JSONException{
        JSONObject json = new JSONObject();
        json.put("query", "DownloadImage");
        json.put("data", new JSONObject()
                .put("name", name)
                .put("image", image));
        return json;
    }

    static public JSONObject queryActivateDeactivateReader(boolean infoActivate, int infoDishId)
            throws JSONException {
        JSONObject json = new JSONObject();
        json.put("query", "ActivateReader");
        JSONObject json_ = new JSONObject();
        json_.put("bool", infoActivate);
        json_.put("dish_id", infoDishId);
        json.put("data", json_);
        return json;
    }


    static public HashMap<Integer, ObjectStructures.DishParams> getActualDishList
            (JSONObject json, Context context) throws JSONException {
        if (json.getString("query").equals("GetDishNames")) {
            HashMap<Integer, ObjectStructures.DishParams> map = new HashMap<>();
            JSONArray dishes =  json.getJSONArray("data");

            for (int i = 0; i < dishes.length(); i++ ) {
                JSONObject object = dishes.getJSONObject(i);
                map.put(object.getInt("dish_id"),
                        new ObjectStructures.DishParams(object.getString("name"),
                                object.getInt("weight"),
                                object.getString("photo")));
            }

            Log.d(TAG, map.toString());
            return map;
        } else {Log.e(TAG, "Not dish query!"); return null;}
    }

    static public ObjectStructures.CookParams getCookName (JSONObject json) throws JSONException {
        if (json.getString("query").equals("GetCookName")) {
            JSONObject json_ = json.getJSONObject("data");

            return new ObjectStructures.CookParams(
                    json_.getString("fullName"),
                    json_.getString("department_name"));

        } else {Log.e(TAG, "Not cook query!"); return null;}
    }

}
