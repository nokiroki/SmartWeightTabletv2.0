package ru.ku.yfrsmartweight;

import androidx.appcompat.app.AppCompatActivity;
import ru.ku.yfrsmartweight.ServerConnection.ImageLoader;
import ru.ku.yfrsmartweight.ServerConnection.JsonEncoderDecoder;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FinalActivity extends AppCompatActivity {

    public static final String TAG = "AppLogs";

    private Bundle bundle;
    private Timer timer;


    private ImageView imageTop;
    private ImageView imageExample;

    private TextView ratingTextGood;
    private TextView ratingTextBad;
    private TextView dishMass;

    private TextView serverAnswer;
    private Button myButton;

    private ExecutorService downloadPool;

    private IWebSocketInterface mWebSocket;
    private class MWebSocketListener extends IWebSocketListener.Stub {

        @Override
        public void omMessageGet(String message) throws RemoteException {
            final JSONObject json;
            try {
                json = new JSONObject(message);
                String query = json.getString("query");
                if (query.equals("OperationSuccess") && MainActivity.isInitToRaspberry) {
                    boolean operationSuccess = json.getJSONObject("data").getBoolean("bool");
                    timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            finish();
                        }
                    }, 10000);
                    if (operationSuccess) {
                        Log.d(TAG, "Measure got and saved!");
                        serverAnswer.setText("Измерение успешно занесено в базу данных!");
                        serverAnswer.setTextColor(getResources().getColor(R.color.ratingTextGreen));

                        // TODO Изменение textView

                    } else {
                        Log.e(TAG, "Error on server!!!");
                        serverAnswer.setText("Ошибка на сервере! Повторите измерение!");
                        serverAnswer.setTextColor(getResources().getColor(R.color.ratingTextRed));
                    }
                } else if (query.equals("DownloadImageCamera") && MainActivity.isInitToRaspberry) {
                    final JSONObject dataTop = json.getJSONObject("data").getJSONObject("top");
                    final JSONObject dataRear = json.getJSONObject("data").getJSONObject("rear");

                    downloadPool = Executors.newFixedThreadPool(1);

                    class downloadPic implements Runnable {

                        private JSONObject data;
                        private ImageView image;

                        private downloadPic(JSONObject data, ImageView image) {
                            this.data = data;
                            this.image = image;
                        }

                        @Override
                        public void run() {
                            try {
                                Log.d(TAG, "Starting to download picture - "
                                        + data.getString("photo_name"));
                                FinalActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            ImageLoader.load(data.getString("photo_name"),
                                                    Base64.decode(data.getString("photo"), Base64.DEFAULT),
                                                    FinalActivity.this,
                                                    image,
                                                    ImageLoader.Mode.CreatingImageView);
                                        } catch (JSONException e) {
                                            Log.e(TAG, e.toString());
                                            e.printStackTrace();
                                        }
                                    }
                                });
                            } catch (JSONException e) {
                                Log.e(TAG, e.toString());
                                e.printStackTrace();
                            }
                        }
                    }
                    downloadPool.execute(new downloadPic(dataTop, imageTop));
                    //myButton.setVisibility(View.VISIBLE);
                }

            } catch (JSONException e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }
        }
    }

    private ServiceConnection sc = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "Service bind from DishPanelActivity");
            mWebSocket = IWebSocketInterface.Stub.asInterface(iBinder);
            try {
                mWebSocket.bindListener(new MWebSocketListener());
                mWebSocket.sendMSG(JsonEncoderDecoder.downloadImage(bundle.getString("photo_id_top"), "camera").toString());

            } catch (RemoteException | JSONException e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.e(TAG, "Server connection error!");
        }
    };

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.final_screen);
        Log.d(TAG, "starting to create final activity");

        serverAnswer = findViewById(R.id.final_info_server_answer);

        bindService(new Intent(MainActivity.INTENT_ACTION).setPackage(getPackageName()), sc, 0);

        bundle = getIntent().getExtras();

        myButton = findViewById(R.id.final_ok_button);
        //myButton.setVisibility(View.INVISIBLE);

        myButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        TextView name = findViewById(R.id.final_name);
        TextView date = findViewById(R.id.final_date);
        TextView time = findViewById(R.id.final_time);
        TextView department = findViewById(R.id.final_department);
        TextView dish = findViewById(R.id.final_dish_name);
        TextView dishMassCurrent = findViewById(R.id.final_mass);
        dishMass = findViewById(R.id.final_current_mass);

        ratingTextGood = findViewById(R.id.rating_text_all_good);
        ratingTextBad = findViewById(R.id.rating_text_not_good);


        imageTop = findViewById(R.id.final_photo_top);
        imageExample = findViewById(R.id.final_photo_example);

        //TODO разделение топ фото и бокового фото


        name.setText(bundle.getString("fullName"));
        Date dateNow = new Date();

        date.setText(new SimpleDateFormat("dd.MM.yyyy", Locale.US).format(dateNow));
        time.setText(new SimpleDateFormat("HH:mm", Locale.US).format(dateNow));

        department.setText(bundle.getString("department_name"));
        dish.setText(bundle.getString("dishName"));
        dishMassCurrent.setText(bundle.getInt("mass") + " г");
        dishMass.setText(MainActivity.CURR_MASS + " г");
        setTextInfo();

        try {
            Log.d(TAG, "Uploading example photo to final activity");
            ImageLoader.setImageView(bundle.getString("ImageName"), imageExample, this);
        } catch (Exception e) {
            Log.e(TAG, "Exception while loading example photo");
            Log.e(TAG ,e.toString());
        }

    }

    private void setTextInfo() {
        float rating = ((float) MainActivity.CURR_MASS / (float) bundle.getInt("mass"));
        Log.d(TAG, "Rating number - " + rating);
        if (rating < 0.9) {
            ratingTextBad.setVisibility(View.VISIBLE);
            int diff = bundle.getInt("mass") - MainActivity.CURR_MASS;
            ratingTextBad.setText(String.format(ratingTextBad.getText().toString(), "Недовес!", diff));
            dishMass.setTextColor(getResources().getColor(R.color.result_with_problem));
        } else if (rating > 1.1) {
            ratingTextBad.setVisibility(View.VISIBLE);
            int diff = MainActivity.CURR_MASS - bundle.getInt("mass");
            ratingTextBad.setText(String.format(ratingTextBad.getText().toString(), "Перевес!", diff));
            dishMass.setTextColor(getResources().getColor(R.color.result_with_problem));
        } else {
            ratingTextGood.setVisibility(View.VISIBLE);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        MainActivity.CURR_MASS = 0;
    }

    @Override
    protected void onDestroy() {
        if (downloadPool != null)
            downloadPool.shutdown();
        super.onDestroy();
        unbindService(sc);
    }

    @Override
    public void onBackPressed() {

    }
}
