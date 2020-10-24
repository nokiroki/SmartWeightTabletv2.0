package ru.ku.yfrsmartweight.ServerConnection;

import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFrame;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

public class WebSocketListenerMine extends WebSocketAdapter {

    private static final String TAG = "AppLogs";

    private ConnectionService mService;

    WebSocketListenerMine(ConnectionService mService) {
        this.mService = mService;
    }

    @Override
    public void onConnectError(WebSocket websocket, WebSocketException exception) throws Exception {
        super.onConnectError(websocket, exception);
        Log.d(TAG, "Test");
    }

    @Override
    public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
        super.onError(websocket, cause);
        Log.d(TAG, "Test");
    }

    @Override
    public void onConnected(WebSocket websocket, Map<String, List<String>> headers)
            throws Exception {
        super.onConnected(websocket, headers);
        Log.d(TAG, "WebSocket successfully connected on URI " + websocket.getURI());
        String initText = JsonEncoderDecoder.initialisationJson().toString();
        websocket.sendText(initText);
    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception {
        super.onTextMessage(websocket, text);
        try {
            JSONObject json = new JSONObject(text);
            String query = json.getString("query");
            Log.d(TAG, "Got new message from server: " + query);
            mService.obtainCurrentJson(json);
        } catch (JSONException e) {
            Log.d(TAG, "Input msg - {" + text + "} not a JSON object");

        }

    }

    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
        super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer);
        Log.d(TAG, "WebSocket closed " + closedByServer + " with close code " + serverCloseFrame.getCloseCode());
        // TODO добавить коллбэк. Пока добавлю только логику переподкоючения
        if (closedByServer) {
            Log.d(TAG, "Trying to reconnect");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        mService.setConnection();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

    }
}
