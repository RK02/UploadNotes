package com.campusconnect.previewdemo.UploadManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

/**
 * Created by aniru on 27-10-2016.
 */

public class WifiReceiver extends BroadcastReceiver {

    public static final String TAG = "WifiReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = conMan.getActiveNetworkInfo();
        if (netInfo != null && netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            Log.d(TAG, "Have Wifi Connection");
            context.startService(new Intent(context.getApplicationContext(), UploadService.class));
        }
        else {
            Log.d(TAG, "Don't have Wifi Connection");
            context.stopService(new Intent(context.getApplicationContext(), UploadService.class));
        }

    }
};