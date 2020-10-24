package ru.ku.yfrsmartweight.ServerConnection;

import android.media.Image;

import java.io.Serializable;

import androidx.annotation.NonNull;

/*
    Класс симулирует структуры.
 */

public class ObjectStructures {

    // Объект, характеризующий одно блюдо
    static public class DishParams {
        public String dishName;
        public int mass;
        public String ImageName;

        public DishParams(String dishName, int mass, String ImageName) {
            this.dishName = dishName;
            this.mass = mass;
            this.ImageName = ImageName;
        }

        @NonNull
        @Override
        public String toString() {
            return dishName + " " + mass + " " + ImageName;
        }
    }

    // Объект, характеризующий повара
    static public class CookParams {

        public String fullName;
        public String department_name;

        public CookParams(String fullName, String department_name) {
            this.fullName = fullName;
            this.department_name = department_name;
        }

    }
}
