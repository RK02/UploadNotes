package com.campusconnect.previewdemo.UploadManager;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.campusconnect.previewdemo.MainActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;


/**
 * Created by aniru on 14-10-2016.
 */

public class UploadService extends IntentService {
    private static final String TAG = "UploadService";
    public static Call call;

    public UploadService() {
        super("UploadService");
    }

    public UploadService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        call = null;
        NotesDatabaseHelper notesDatabaseHelper = new NotesDatabaseHelper(
                this.getApplicationContext());
        PendingNote note = notesDatabaseHelper.getNextPendingNote();
        if (note == null) {
            Log.d(TAG, "No more notes to be uploaded");
            stopService(new Intent(this, UploadService.class));
            return;
        }
        Log.d(TAG, "uploading courseId = " + note.getCourseId() + " pages uploaded = " + note.getNoOfPagesUploaded());
        OkHttpClient client = new OkHttpClient();
        MultipartBody.Builder body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("courseId", note.getCourseId())
                .addFormDataPart("profileId", "")
                .addFormDataPart("date","")
                .addFormDataPart("title", note.getTitle())
                .addFormDataPart("notesDesc", note.getNotesDesc());
        File file;
        int pos = note.getNoOfPagesUploaded() + 1;
        ArrayList<String> uriList = note.getUriList();
        if(uriList == null || uriList.size() == 0) {
            Log.d(TAG, "No notes to be uploaded");
            stopService(new Intent(this, UploadService.class));
            return;
        }
        Bitmap original = null;
        try {
            InputStream fileInputStream = getContentResolver().openInputStream(Uri.parse(uriList.get(pos-1)));
            original = BitmapFactory.decodeStream(fileInputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        file = new File(getFilesDir() + "/temp" + pos + ".jpeg");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        int size = original.getRowBytes() * original.getHeight();
        if (size > 10000000)
            original.compress(Bitmap.CompressFormat.JPEG, 40, out);
        else
            original.compress(Bitmap.CompressFormat.JPEG, 80, out);
        body.addFormDataPart("file", "test.jpg", RequestBody.create(MediaType.parse("image/*"), file));

        RequestBody requestBody = body.build();
        Request request = new Request.Builder()
                .url("https://uploadnotes-2016.appspot.com/imgweb")
                .post(requestBody)
                .build();
        Response response = null;
        MainActivity.builder.setContentTitle("Uploading " + note.getTitle());
        MainActivity.builder.setContentText("Page " + pos + " of " + note.getUriList().size());
        MainActivity.builder.setProgress(note.getUriList().size(), pos, false);
        MainActivity.notificationManager.notify(1, MainActivity.builder.build());
        try {
            Log.d(TAG, "Started uploading page " + pos);
            call = client.newCall(request);
            response = call.execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            Response res = response;
            ResponseBody responseBody = null;
            if (res != null) {
                responseBody = res.body();
            }
            String jsonresponse = null;
            if (responseBody != null) {
                jsonresponse = responseBody.string();
            } else {
                MainActivity.builder.setContentText("Upload paused");
                MainActivity.builder.setProgress(0, 0, false);
                MainActivity.notificationManager.notify(1, MainActivity.builder.build());
                Log.d(TAG, "Upload failed");
                stopService(new Intent(this, UploadService.class));
                return;
            }

            if(jsonresponse!=null) {
                Log.d(TAG, "Finished uploading page " + pos);
                JSONObject jsonObject = new JSONObject(jsonresponse);
                Log.d(TAG, jsonObject.getString("url"));
                notesDatabaseHelper.appendUrlToList(note.getCourseId(), jsonObject.getString("url"));
                ArrayList<String> urls = note.getUrls();
                urls.add(jsonObject.getString("url"));
                note.setUrls(urls);
                notesDatabaseHelper.incrementPageNumberOfPendingNote(note.getCourseId());

                Log.d(TAG, "done = " + (note.getNoOfPagesUploaded() + 1) + " total = " + note.getUriList().size());
                if (note.getNoOfPagesUploaded() + 1 == note.getUriList().size()) {
                    MainActivity.builder.setContentText("Upload complete");
                    MainActivity.builder.setProgress(0, 0, false);
                    MainActivity.builder.setOngoing(false);
                    MainActivity.notificationManager.notify(1, MainActivity.builder.build());
                    Log.d(TAG, " Final urls = " + note.getUrls());
                    int a = notesDatabaseHelper.deletePendingNote(note.getCourseId());
                    Log.d(TAG, "courseId deleted " + note.getCourseId() + " a = " + a);
                    stopSelf();
                }
            }
        }  catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        stopService(new Intent(this, UploadService.class));
        startService(new Intent(this, UploadService.class));
    }
}