package ru.ku.yfrsmartweight;


import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

import ru.ku.yfrsmartweight.ServerConnection.ImageLoader;
import ru.ku.yfrsmartweight.ServerConnection.ObjectStructures;

// Класс отвечает за управление панельками с блюдами
 class DishPanel {

    private static final String TAG = "AppLogs";

    public static final int USER_ID_DISH = 6000;


    private static final int INFLATE_CONTAINER = R.layout.dish_panel_new;


    // Создание одной панели на основе пары <id - Название> и id фото блюда
    static View createNewPanel(Context context,
                               HashMap.Entry<Integer, ObjectStructures.DishParams> e, TableRow row)
            throws InterruptedException {
        int id = e.getKey();
        String dishName = e.getValue().dishName;

        // Подключение контейнера с видом панели
        LayoutInflater inflater = LayoutInflater.from(context);

        View view = inflater.inflate(INFLATE_CONTAINER, row, false);
        view.setBackgroundResource(R.drawable.dish_panel_background);
        view.setId(id + USER_ID_DISH);

        ImageView image = view.findViewById(R.id.dish_image);
        TextView text = view.findViewById(R.id.dish_name);

        text.setText(dishName);

        tryToUploadImage(e.getValue().ImageName, image, context, 1);

        row.addView(view);
        Log.d(TAG, "Create new plate with id " + (id));
        return view;

    }

    static private void tryToUploadImage(String name, ImageView image, Context context, int attempt)
            throws InterruptedException {
        if (attempt == 0) {
            attempt = 1;
        }
        if (attempt == 5) {
            Log.d(TAG, "Failed to upload!");
            // TODO image not found
            return;
        }
        int info = ImageLoader.setImageView(name, image, context);
        if (info == 0) {
            Log.d(TAG, "Image with name " + name + " failed to upload with attempt " + attempt);
            Thread.sleep(2 * 1000);
            tryToUploadImage(name, image, context, ++attempt);
        } else {
            Log.d(TAG, "Image successfully uploaded!");
        }
    }

    //Метод для отключения панелек
    static public void setDisabled(ArrayList<View> panels, Context context) {
        float[] cmData = new float[]{
                0.3f, 0.59f, 0.11f, 0, 0,
                0.3f, 0.59f, 0.11f, 0, 0,
                0.3f, 0.59f, 0.11f, 0, 0,
                0, 0, 0, 1, 0,};
        ColorMatrix mColorMatrix = new ColorMatrix(cmData);
        ColorMatrixColorFilter mFilter = new ColorMatrixColorFilter(mColorMatrix);
        for (View v : panels) {
            ImageView im = v.findViewById(R.id.dish_image);
            im.setColorFilter(mFilter);
            v.setEnabled(false);
        }
    }

    //Метод для включения панелек
    static public    void setEnabled(ArrayList<View> panels, Context context){
        for (View v : panels) {
            ImageView im = v.findViewById(R.id.dish_image);
            im.clearColorFilter();
            v.setEnabled(true);
        }
    }


}
