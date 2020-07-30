package com.example.distancelocation;

import android.app.Application;

public class GlobalClass extends Application {
    public static double lat;
    public static double lng;

    @Override
    public void onCreate() {
        super.onCreate();
        lat =0l;
        lng =0l;

    }
}
