package ru.ku.yfrsmartweight;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import ru.ku.yfrsmartweight.ServerConnection.ImageLoader;
import ru.ku.yfrsmartweight.ServerConnection.JsonEncoderDecoder;
import ru.ku.yfrsmartweight.ServerConnection.ObjectStructures;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public static boolean isInitToRaspberry = false;
    public static int CURR_MASS = 0;

    public static final String INTENT_ACTION = "ru.ku.yfrsmartweight.p1191ConnectionService";
    private static final String TAG = "AppLogs";
    private boolean flagFirstCreated = true;


    private ArrayList<View> panels = new ArrayList<>();
    private HashMap<Integer, ObjectStructures.DishParams> listDish;
    private ServerDBHelper DBHelp;

    private ExecutorService downloadPool;
    private CountDownLatch loaderLatch;

    private Intent intent;
    private ServiceConnection sConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "Service bind from MainActivity");
            mWebSocket = IWebSocketInterface.Stub.asInterface(iBinder);
            try {
                mWebSocket.bindListener(new MyServerListener());
                if (flagFirstCreated) { mWebSocket.sendMSG(JsonEncoderDecoder.initialisationJson().toString()); }
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
    private Thread looper;

    public void DisablePanelsFromExternalClass() {
        if (!panels.isEmpty()) {
            DishPanel.setDisabled(panels, this);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "91f19 OnCreate");
        downloadPool = Executors.newFixedThreadPool(8);
        intent = new Intent(INTENT_ACTION);
        intent.setPackage(getPackageName());

        startService(intent);
        bindService(intent, sConn, 0);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "TableCreator UI Thread");
                    tableRowCreator(MainActivity.this);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Log.e(TAG, e.toString());
                }

            }
        }, "TableCreator Thread").start();

    }

    private class MyServerListener extends IWebSocketListener.Stub {
        @Override
        public void omMessageGet(String message) throws RemoteException {
            try {
                final JSONObject json = new JSONObject(message);
                String query = json.getString("query");
                if (query.equals("InitTablet")) {
                    if (isInitToRaspberry) {
                        Log.d(TAG, "Tablet had already connected to server!");
                        return;
                    }
                    isInitToRaspberry = true;
                    Toast.makeText(MainActivity.this,
                            "Raspberry has successfully connect",
                            Toast.LENGTH_LONG).show();
                    mWebSocket.sendMSG(JsonEncoderDecoder.queryActualDishList().toString());
                }
                else if (query.equals("GetDishNames") && isInitToRaspberry) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            DBConnect(json);
                        }
                    }).start();
                } else if (query.equals("DownloadImage") && isInitToRaspberry){
                    final JSONObject data = json.getJSONObject("data");
                    downloadPool.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Log.d(TAG,
                                        "Start to download picture - " + data.getString("photo_name")
                                );
                                ImageLoader.load(data.getString("photo_name"),
                                        Base64.decode(data.getString("photo"), Base64.DEFAULT),
                                        MainActivity.this,
                                        ImageLoader.Mode.loadInStorage);
                                loaderLatch.countDown();
                            } catch (JSONException e) {
                                e.printStackTrace();
                                Log.e(TAG, e.toString());
                            }
                        }
                    });
                }
                else if (query.equals("SetActivePanels") && isInitToRaspberry) {
                    boolean flag = json.getJSONObject("data").getBoolean("bool");
                    if (flag) {
                        DishPanel.setEnabled(panels, MainActivity.this);
                    } else {
                        DishPanel.setDisabled(panels, MainActivity.this);
                    }
                } else if (query.equals("CurrentWeight") && isInitToRaspberry) {
                    CURR_MASS = json.getJSONObject("data").getInt("mass");
                } else {
                    if (isInitToRaspberry) {
                            Log.e(TAG, "Unknown query!");
                    } else {
                        Log.e(TAG, "Tablet unknown!");
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }

        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "OnStart");
        looper = new Thread(new LoopSendJson());
        looper.start();
    }

    private class LoopSendJson implements Runnable {

        private final int SECS_ON_LOOP = 3600;

        @Override
        public void run() {
            try {
                while (true) {
                    Thread.sleep(SECS_ON_LOOP * 1000);
                    Log.d(TAG, "10 seconds pass");
                    mWebSocket.sendMSG(JsonEncoderDecoder.queryActualDishList().toString());
                }
            } catch (InterruptedException | JSONException | RemoteException e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (CURR_MASS == 0) {
            DishPanel.setDisabled(panels, this);
        }
        if (flagFirstCreated) { flagFirstCreated = false; }
        else { bindService(intent, sConn, 0); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "OnPause");
        unbindService(sConn);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "OnStop");
        looper.interrupt();
        downloadPool.shutdown();
    }

    // Клик по панели с блюдом
    @Override
    public void onClick(View view) {
        Intent intent = new Intent(this, DishBigActivity.class);
        int id = view.getId() - DishPanel.USER_ID_DISH;
        ObjectStructures.DishParams panel = listDish.get(id);
        try {
            intent.putExtra("dishId", id);
            intent.putExtra("dishName", panel.dishName);
            intent.putExtra("ImageName", panel.ImageName);
            intent.putExtra("mass", panel.mass);
        } catch (NullPointerException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        startActivity(intent);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(intent);
        flagFirstCreated = true;
        isInitToRaspberry = false;
    }


    private class ServerDBHelper extends SQLiteOpenHelper {

        private Context mContex;
        private String name;
        private int version;
        private JSONObject json;
        private boolean isFirstCreated = false;


        ServerDBHelper(@Nullable Context context, @Nullable String name,
                       @Nullable SQLiteDatabase.CursorFactory factory, int version,
                       JSONObject launchJson) {
            super(context, name, factory, version);
            this.mContex = context;
            this.name = name;
            this.version = version;
            json = launchJson;
        }


        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            if (json != null) {
                try {
                    isFirstCreated = true;
                    updateDataBase(json, sqLiteDatabase, true);

                } catch (JSONException | RemoteException e) {
                    e.printStackTrace();
                    Log.e(TAG, e.toString());
                }

            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        }
    }

    synchronized private void updateDataBase(JSONObject json,
                                             SQLiteDatabase db, boolean isFirstLaunch)
            throws JSONException, RemoteException {
        HashMap<Integer, ObjectStructures.DishParams> map = JsonEncoderDecoder
                .getActualDishList(json, this);

        ContentValues cv = new ContentValues();
        if (isFirstLaunch) {
            Log.d(TAG, "DataBase first time create");
            loaderLatch = new CountDownLatch(map.size());
            db.execSQL("CREATE TABLE dish (ID INTEGER PRIMARY KEY,"
                    + " DISH TEXT, PHOTO TEXT, MASS INTEGER);");
            for (HashMap.Entry<Integer, ObjectStructures.DishParams> e : map.entrySet()) {
                cv.put("ID", e.getKey());
                cv.put("DISH", e.getValue().dishName);
                cv.put("PHOTO", e.getValue().ImageName);
                cv.put("MASS", e.getValue().mass);
                long rowID = db.insert("dish", null, cv);
                Log.d(TAG, "Row inserted, ID = " + rowID);
                mWebSocket.sendMSG(
                        JsonEncoderDecoder.downloadImage(e.getValue().ImageName, "dish").toString());
                cv.clear();
            }
            listDish = new HashMap<>(map);
        } else {
            Set<Integer> oldKeySet;
            if (listDish != null) { oldKeySet = listDish.keySet(); }
            else {
                listDish = getButtons(db);
                oldKeySet = listDish.keySet();
            }
            assert map != null;
            Set<Integer> newKeySet = map.keySet();

            Log.d(TAG, "Old ids - " + oldKeySet.toString() + ", new ids - "
                    + newKeySet.toString());
            if (newKeySet.equals(oldKeySet)) {
                Log.d(TAG, "Nothing to update");
                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                "Проверка базы данных завершена.\n Обновлений нет!",
                                Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }
            Set<Integer> symmetricDiff = new HashSet<>(oldKeySet);
            symmetricDiff.addAll(newKeySet);
            Set<Integer> tmp = new HashSet<>(oldKeySet);
            tmp.retainAll(newKeySet);
            symmetricDiff.removeAll(tmp);
            loaderLatch = new CountDownLatch(symmetricDiff.size());
            for (int i : symmetricDiff) {
                if (oldKeySet.contains(i)) {
                    int delCount = db.delete("dish", "ID = " + i, null);
                    Log.d(TAG, "Row delete count = " + delCount);
                }
                else {
                    ObjectStructures.DishParams currParams = map.get(i);
                    assert currParams != null;
                    cv.put("ID", i);
                    cv.put("DISH", currParams.dishName);
                    cv.put("PHOTO", currParams.ImageName);
                    cv.put("MASS", currParams.mass);
                    Log.d(TAG, currParams.dishName);
                    long rowID = db.insert("dish", null, cv);
                    Log.d(TAG, "Row inserted, ID = " + rowID);
                    mWebSocket.sendMSG(
                            JsonEncoderDecoder.downloadImage(currParams.ImageName, "dish").toString());
                    cv.clear();
                }
            }
            db.setTransactionSuccessful();
            Log.d(TAG, "Updated with " + symmetricDiff.size() + " positions");
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this,
                            "Проверка базы данных завершена.\nБаза блюд обновлена!"
                                    + " Перезагрузите приложение",
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }


    // Получаем мапу отсортированных по блюдам заготовок для создания панелей на главном экране
    private HashMap<Integer, ObjectStructures.DishParams> getButtons(SQLiteDatabase db) {
        Cursor c;
        HashMap<Integer, ObjectStructures.DishParams> mMap = new HashMap<>();
        String[] columns = new String[] {"ID", "DISH", "PHOTO", "MASS"};
        String groupBy = "DISH";
        c = db.query("dish", columns, null, null,
                groupBy, null, null);
        if(c.moveToFirst()) {
            do {
                int id = c.getColumnIndex("ID");
                int dish = c.getColumnIndex("DISH");
                int photo = c.getColumnIndex("PHOTO");
                int mass = c.getColumnIndex("MASS");

                mMap.put(c.getInt(id), new ObjectStructures.DishParams(
                        c.getString(dish),
                        c.getInt(mass),
                        c.getString(photo)));
                Log.d(TAG, "Position "
                        + Objects.requireNonNull(mMap.get(c.getInt(id))).dishName
                        + " with photo id "
                        + Objects.requireNonNull(mMap.get(c.getInt(id))).ImageName
                        + ", mass " +  Objects.requireNonNull(mMap.get(c.getInt(id))).mass
                        + " get");
            } while (c.moveToNext());
        } else Log.d(TAG, "Cursor is empty!");
        c.close();

        return mMap;
    }

    private synchronized void DBConnect(JSONObject json) {
        Log.d(TAG, "connected to DB");
        DBHelp = new ServerDBHelper(MainActivity.this, "menu",
                null, 1, json);
        SQLiteDatabase db = DBHelp.getWritableDatabase();
        if (!DBHelp.isFirstCreated) {
            try {
                db.beginTransaction();
                updateDataBase(json, db, false);
                db.endTransaction();
            } catch (JSONException | RemoteException e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }
        } else { DBHelp.isFirstCreated = false; }
        DBHelp.close();
        Log.d(TAG, "Transaction ends");
        notifyAll();
    }


    //Создание панелей в зависимости от полей БД
    private synchronized void tableRowCreator (
                                  final Context context) throws InterruptedException {

        wait();
        if (loaderLatch != null) {
            loaderLatch.await(10, TimeUnit.SECONDS);
        }
        Log.d(TAG, "Test");

        if (listDish == null) { Log.e(TAG, "FATAL ERROR! LIST OF DISH IS EMPTY!"); return; }

        Log.d(TAG, "Entering tableRowCreator");

        //db необходима в качестве аналога "db" в xml разметке
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final float dp = getResources().getDisplayMetrics().density;

                TableLayout tLayout = findViewById(R.id.ParentTable);

                //Параметры строчки таблицы
                TableLayout.LayoutParams tableParams = new TableLayout.LayoutParams();
                tableParams.height = TableRow.LayoutParams.WRAP_CONTENT;
                tableParams.width = TableRow.LayoutParams.MATCH_PARENT;
                tableParams.setMargins(0, (int) dp * 10, 0, (int) dp * 10);


                TableRow row = null;
                for (HashMap.Entry<Integer, ObjectStructures.DishParams> e : listDish.entrySet()) {

                    Log.d(TAG, "insert id " + e.getKey() + " with dish " + e.getValue().dishName);

                    //Каждые три панельки создаем новую строчку
                    if ((e.getKey() - 1) % 4 == 0) {
                        row = new TableRow(context);
                        row.setLayoutParams(tableParams);
                        row.setGravity(Gravity.CENTER);
                        tLayout.addView(row);
                    }
                    try {
                        View v = DishPanel.createNewPanel(context, e, row);
                        v.setOnClickListener(MainActivity.this);
                        panels.add(v);
                    } catch (InterruptedException ex){
                        Log.e(TAG, ex.toString());
                    }
                }
                DishPanel.setDisabled(panels, MainActivity.this);
            }
        });
    }

}