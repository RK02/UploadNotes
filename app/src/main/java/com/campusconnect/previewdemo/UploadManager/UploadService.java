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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

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
    private static final String CREATE_NOTES_API_PATH =
            "https://uploadingtest-2016.appspot.com/_ah/api/notesapi/v1/createNotes";
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
        Log.d(TAG, "uploading courseId = " + note.getCourseId() + " pages uploaded = "
                + note.getNoOfPagesUploaded());
        OkHttpClient client = new OkHttpClient();
        MultipartBody.Builder body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("courseId", note.getCourseId())
                .addFormDataPart("profileId", "");
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
            InputStream fileInputStream = getContentResolver().openInputStream(
                    Uri.parse(uriList.get(pos-1)));
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
        body.addFormDataPart("file", "test.jpg", RequestBody.create(MediaType.parse("image/*"),
                file));

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

                Log.d(TAG, "done = " + (note.getNoOfPagesUploaded() + 1) + " total = "
                        + note.getUriList().size());
                if (note.getNoOfPagesUploaded() + 1 == note.getUriList().size()) {
                    MainActivity.builder.setContentText("Finishing upload");
                    MainActivity.builder.setProgress(0, 0, true);
                    MainActivity.notificationManager.notify(1, MainActivity.builder.build());

                    Calendar calendar = Calendar.getInstance();
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MMM-yyyy");
                    client = new OkHttpClient();

                    // TODO(anirudhgp): Get course ID and profile ID
                    body = new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("courseId",
                                    "ahRzfnVwbG9hZGluZ3Rlc3QtMjAxNnITCxIGQ291cnNlGICAgID84o8IDA")
                            .addFormDataPart("profileId",
                                    "ahRzfnVwbG9hZGluZ3Rlc3QtMjAxNnIUCxIHUHJvZmlsZRiAgICAr6uNCAw")
                            .addFormDataPart("date",simpleDateFormat.format(calendar.getTime()))
                            .addFormDataPart("title", note.getTitle())
                            .addFormDataPart("urlList", String.valueOf(note.getUrls()))
                            .addFormDataPart("notesDesc", note.getNotesDesc());

                    requestBody = body.build();
                    request = new Request.Builder()
                            .url(CREATE_NOTES_API_PATH)
                            .post(requestBody)
                            .addHeader("Content-Type","application/json; charset=UTF-8")
                            .build();
                    response = null;
                    try {
                        Log.d(TAG, "Calling notesAPI ");
                        call = client.newCall(request);
                        response = call.execute();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        res = response;
                        responseBody = null;
                        if (res != null) {
                            responseBody = res.body();
                        }
                        jsonresponse = null;
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

                        if (jsonresponse != null) {
                            Log.d(TAG, "Created note ");
                            MainActivity.builder.setContentText("Upload complete");
                            MainActivity.builder.setProgress(0, 0, false);
                            MainActivity.builder.setOngoing(false);
                            MainActivity.notificationManager.notify(1, MainActivity.builder.build());
                            int a = notesDatabaseHelper.deletePendingNote(note.getCourseId());
                            Log.d(TAG, "courseId deleted " + note.getCourseId() + " a = " + a);
                            jsonObject = new JSONObject(jsonresponse);
                            Log.d(TAG, jsonObject.getString("key"));
                            stopSelf();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
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