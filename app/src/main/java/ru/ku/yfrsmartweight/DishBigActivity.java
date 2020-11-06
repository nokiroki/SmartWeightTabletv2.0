package ru.ku.yfrsmartweight;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;

import androidx.appcompat.app.AppCompatActivity;
import ru.ku.yfrsmartweight.ServerConnection.ImageLoader;
import ru.ku.yfrsmartweight.ServerConnection.JsonEncoderDecoder;

public class DishBigActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String TAG = "AppLogs";

    private int dishId;
    private String dishName;
    private int mass;
    private String ImageName;

    private Timer timer;

    private TextView massText;

    private ServiceConnection sc = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "Service bind from DishPanelActivity");
            mWebSocket = IWebSocketInterface.Stub.asInterface(iBinder);
            try {
                mWebSocket.bindListener(new MWebSocketListener());
                mWebSocket.sendMSG(JsonEncoderDecoder
                        .queryActivateDeactivateReader(true, dishId)
                        .toString());
            } catch (RemoteException | JSONException e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.e(TAG, "Server connection error!");
        }
    };

    private IWebSocketInterface mWebSocket;

    private class MWebSocketListener extends IWebSocketListener.Stub {

        @Override
        public void omMessageGet(String message) {
            try {
                final JSONObject json = new JSONObject(message);
                String query = json.getString("query");
                if (query.equals("ActivateReader") && MainActivity.isInitToRaspberry) {
                    DishBigActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView text = findViewById(R.id.panel_big_info_text);
                            text.setText(R.string.card_got);
                        }
                    });
                } else if (query.equals("GetReader") && MainActivity.isInitToRaspberry) {
                    timer.cancel();
                    if (!json.getJSONObject("data").getBoolean("isOk")) {
                        missingNFCInformation();
                    } else {
                        startFinalActivity(json);
                    }
                } else if (query.equals("SetActivePanels") && MainActivity.isInitToRaspberry) {
                    if (!json.getJSONObject("data").getBoolean("bool")) {
                        Toast.makeText(DishBigActivity.this,
                                "Mass was removed",
                                Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Mass removed from dish big");
                        MainActivity.CURR_MASS = 0;
                        finish();
                    }
                } else if (query.equals("CurrentWeight") && MainActivity.isInitToRaspberry) {
                    MainActivity.CURR_MASS = json.getJSONObject("data").getInt("mass");
                    massText.setText(String.format(getResources()
                            .getString(R.string.mass_compare_dialog), MainActivity.CURR_MASS, mass));
                }
                else {
                    if (!MainActivity.isInitToRaspberry) {
                        Log.e(TAG, "Tablet unknown!");
                    } else {
                        Log.e(TAG, "Unknown query!");
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dish_big);
        this.setFinishOnTouchOutside(false);

        getIntentExtra();

        bindService(new Intent(MainActivity.INTENT_ACTION).setPackage(getPackageName()), sc, 0);



        TextView dish = findViewById(R.id.panel_big_description);
        massText = findViewById(R.id.panel_big_mass_compare);
        ImageView photo = findViewById(R.id.panel_big_image);

        dish.setText(dishName);

        massText.setText(String.format(massText.getText().toString(), MainActivity.CURR_MASS, mass));
        ImageLoader.setImageView(ImageName, photo, this);

        Button finishButton = findViewById(R.id.panel_big_cancel_button);
        finishButton.setOnClickListener(this);

        timer = new Timer();
        Log.d(TAG, "Timer on 15s started");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                finish();
            }
        }, 15000);
    }


    // Получаем параметры вызываемой панели
    private void getIntentExtra() {
        Intent intent = getIntent();
        dishId = intent.getIntExtra("dishId", 0);
        dishName = intent.getStringExtra("dishName");
        mass = intent.getIntExtra("mass", 0);
        ImageName = intent.getStringExtra("ImageName");
        Log.d(TAG, dishName + " " + mass + " " + ImageName);
    }

    private void missingNFCInformation () {
        //TODO диалоговое окно с информацией
        Log.d(TAG, "Unknown NFC");
        finish();
    }

    private void startFinalActivity (JSONObject json) throws JSONException {
        JSONObject json_ = json.getJSONObject("data");
        // TODO загрузка картинки с сервера
        Intent intent = new Intent(this, FinalActivity.class)
                .putExtra("dishName", dishName)
                .putExtra("mass", mass)
                .putExtra("fullName", json_.getString("fullName"))
                .putExtra("department_name", json_.getString("department_name"))
                .putExtra("photo_id_top", json_.getJSONObject("photo").getString("top"))
                .putExtra("photo_id_rear", json_.getJSONObject("photo").getString("rear"))
                .putExtra("ImageName", ImageName);
        startActivity(intent);
        finish();
    }

    private synchronized void changedWeight(int weight) {
        MainActivity.CURR_MASS = weight;
        massText.setText(String.format(massText.getText().toString(), mass, MainActivity.CURR_MASS));

    }

    private void sendDeactivateJson() throws JSONException, RemoteException {
        mWebSocket.sendMSG(JsonEncoderDecoder.queryActivateDeactivateReader
                (false, dishId).toString());
    }

    @Override
    public void onClick(View view) { finish(); }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "DishBig stopped");
        timer.cancel();
        try {
            sendDeactivateJson();
        } catch (JSONException | RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
        }
        unbindService(sc);
    }
}
