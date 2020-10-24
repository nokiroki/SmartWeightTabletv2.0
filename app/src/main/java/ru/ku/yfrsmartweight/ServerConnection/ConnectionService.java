package ru.ku.yfrsmartweight.ServerConnection;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import ru.ku.yfrsmartweight.IWebSocketInterface;
import ru.ku.yfrsmartweight.IWebSocketListener;


public class ConnectionService extends Service {

    private static final String TAG = "AppLogs";

    private CountDownLatch latch = new CountDownLatch(1);

    //private final String URI = "ws://10.0.1.17:8080";
    //private final String URI = "ws://192.168.43.237:8080";
    //private final String URI = "ws://192.168.88.32:8080";
    private final String URI = "ws://10.0.1.4:8080";


    private static int increment = 0;
    private WebSocket ws;
    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            try {
                Log.d(TAG, "Got new message with id " + msg.what);
                JSONObject json = (JSONObject)msg.obj;
                if (json.has("data") && json.has("query")) {
                    Log.d(TAG, "Message with id " + msg.what
                            + " had successfully sent to activity!");
                    mListener.omMessageGet(json.toString());
                } else {Log.e(TAG, "Unknown json!");}
            } catch (ClassCastException | RemoteException e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }

        }
    };

    private IWebSocketListener mListener;


    private class MyWebSocketInterface extends IWebSocketInterface.Stub {

        @Override
        public void bindListener(IBinder callback) throws RemoteException {
            mListener = IWebSocketListener.Stub.asInterface(callback);
        }

        @Override
        public void sendMSG(String message) throws RemoteException {
            tryToSendMsgAsync(message);
            Log.d(TAG, "Message sent");
        }
    }

    private void tryToSendMsgAsync (final String message) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    latch.await();
                    sendTextAsync(message);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Log.e(TAG, e.toString());
                }
            }
        }).start();
    }

    synchronized private void sendTextAsync(String message) {
        ws.sendText(message);
    }

    public void obtainCurrentJson(JSONObject json) throws JSONException {
        Log.d(TAG, "Message number " + ++increment + " got with query "
                + json.getString("query"));
        handler.sendMessage(handler.obtainMessage(increment, 0, 0 ,json));
    }


    public void setConnection() throws InterruptedException {
        try {
            ExecutorService s = Executors.newSingleThreadExecutor();
            Log.d(TAG, "Start connection");
            WebSocketFactory factory = new WebSocketFactory().setConnectionTimeout(3000);
            ws = factory.createSocket(URI);
            //ws = factory.createSocket("wss://echo.websocket.org");
            ws.addListener(new WebSocketListenerMine(this));
            Future<WebSocket> future = ws.connect(s);
            future.get();
            Log.d(TAG, "" + ws.isOpen());
            latch.countDown();
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
            Log.d(TAG, "Failed to connect webSocket");
            Log.e(TAG, e.toString());
            Thread.sleep(TimeUnit.SECONDS.toMillis(3));
            setConnection();
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created!");
        //setConnection();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    setConnection();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        increment = 0;
        ws.disconnect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service onBind!");
        return new MyWebSocketInterface();
    }

}
