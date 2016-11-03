package com.campusconnect.previewdemo.UploadManager;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by aniru on 28-10-2016.
 */

public class NotificationService extends Service {
    public static final String TAG = "NotificationService";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent.getAction() != null) {
            if (intent.getAction().equals(Constants.ACTION_PAUSE)) {
                Toast.makeText(this, "Upload paused", Toast.LENGTH_LONG).show();
                Log.d(TAG, "paused");
                UploadService.call.cancel();
                stopService(new Intent(this, UploadService.class));
            } else if (intent.getAction().equals(Constants.ACTION_RESUME)) {
                Toast.makeText(this, "Resuming upload", Toast.LENGTH_LONG).show();
                Log.d(TAG, "resumed");
                startService(new Intent(this, UploadService.class));
            }
        }
        return START_NOT_STICKY;
    }
}
