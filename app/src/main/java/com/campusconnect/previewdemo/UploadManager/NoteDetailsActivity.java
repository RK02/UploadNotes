package com.campusconnect.previewdemo.UploadManager;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.campusconnect.previewdemo.R;

import org.json.JSONException;

import java.util.ArrayList;

/**
 * Created by aniru on 28-10-2016.
 */

public class NoteDetailsActivity extends AppCompatActivity {

    public static final String TAG = "NoteDetailsActivity";
    private ArrayList<String> uriList;

    private EditText title;
    private EditText description;
    private EditText course;

    private Button submit;

    private String noteTitle;
    private String noteDesc;
    private String noteCourse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_event);

        uriList = getIntent().getStringArrayListExtra("uriList");

        title = (EditText) findViewById(R.id.noteTitle);
        description = (EditText) findViewById(R.id.noteDescription);
        course = (EditText) findViewById(R.id.course);

        submit = (Button) findViewById(R.id.submit);

        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int flag = 0;
                if (title.getText() != null) {
                    noteTitle = title.getText().toString();
                } else {
                    flag = 1;
                    Toast.makeText(getApplicationContext(), "Please enter a title", Toast.LENGTH_SHORT).show();
                }
                if (description.getText() != null) {
                    noteDesc = description.getText().toString();
                } else {
                    flag = 1;
                    Toast.makeText(getApplicationContext(), "Please enter a description", Toast.LENGTH_SHORT).show();
                }
                if (course.getText() != null) {
                    noteCourse = course.getText().toString();
                } else {
                    flag = 1;
                    Toast.makeText(getApplicationContext(), "Please enter a course name", Toast.LENGTH_SHORT).show();
                }
                if (flag == 0) {
                    storeImages();
                }
            }
        });
    }

    private void storeImages() {
        Log.d(TAG, "storing " + uriList.size() + " images");
        if(uriList.size() == 0) {
            Log.d(TAG, "No images to upload");
            return;
        }
        PendingNote note = new PendingNote(noteCourse, uriList, 0, noteTitle, noteDesc, new ArrayList<String>());
        NotesDatabaseHelper notesDatabaseHelper = new NotesDatabaseHelper(getApplicationContext());
        try {
            notesDatabaseHelper.insertPendingNote(note);
            Log.d(TAG, "inserted");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        resetData();
        startService(new Intent(this, UploadService.class));
        Toast.makeText(this, "Check notification for upload progress", Toast.LENGTH_LONG).show();
        finish();
    }

    private void resetData() {
        uriList.clear();
        noteCourse = "";
        noteTitle = "";
        noteDesc = "";
    }

}
