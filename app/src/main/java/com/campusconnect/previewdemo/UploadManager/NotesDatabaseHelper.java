package com.campusconnect.previewdemo.UploadManager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

/**
 * Created by aniru on 13-10-2016.
 */

public class NotesDatabaseHelper extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "notesManager";
    public static final String TABLE_PENDING_NOTES = "pendingNotes";
    public static final String KEY_COURSEID = "courseId";
    public static final String KEY_URILIST = "uriList";
    public static final String KEY_NOOFPAGESUPLOADED = "noOfPagesUploaded";
    public static final String KEY_TITLE = "title";
    public static final String KEY_NOTESDESC = "notesDesc";
    public static final String KEY_URLS = "urls";
    public static final String TAG = "DatabaseHelper";

    public NotesDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_CONTACTS_TABLE = "CREATE TABLE " + TABLE_PENDING_NOTES + "("
                + KEY_COURSEID + " TEXT PRIMARY KEY, " + KEY_URILIST + " TEXT, " + KEY_NOOFPAGESUPLOADED +
                " INTEGER, " + KEY_TITLE + " TEXT, " + KEY_NOTESDESC + " TEXT, " + KEY_URLS + " TEXT" + ");";
        db.execSQL(CREATE_CONTACTS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PENDING_NOTES);

        // Create tables again
        onCreate(db);
    }

    public void deleteNotesDatabase() {
        // Drop older table if existed
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PENDING_NOTES);

        onCreate(db);
    }

    public void insertPendingNote(PendingNote notes) throws JSONException {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        JSONArray jsonArray = new JSONArray(notes.getUriList());
        values.put(KEY_COURSEID, notes.getCourseId());
        values.put(KEY_URILIST, jsonArray.toString());
        values.put(KEY_NOOFPAGESUPLOADED, notes.getNoOfPagesUploaded());
        values.put(KEY_TITLE, notes.getTitle());
        values.put(KEY_NOTESDESC, notes.getNotesDesc());
        jsonArray = new JSONArray(notes.getUrls());
        values.put(KEY_URLS, jsonArray.toString());

        db.insert(TABLE_PENDING_NOTES, null, values);
        db.close();
    }

    public PendingNote getPendingNote(String courseId) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_PENDING_NOTES, new String[] { KEY_COURSEID,
                        KEY_URILIST, KEY_NOOFPAGESUPLOADED, KEY_TITLE, KEY_NOTESDESC, KEY_URLS}, KEY_COURSEID + "=?",
                new String[] { String.valueOf(courseId) }, null, null,
                null, null);
        if (cursor != null)
            cursor.moveToFirst();
        else {
            return null;
        }

        if (cursor.getCount() == 0)
            return null;

        JSONArray JSONUriList = null;
        ArrayList<String> uriList = new ArrayList<>();
        try {
            JSONUriList = new JSONArray(cursor.getString(1));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (JSONUriList != null) {
            Log.d(TAG, "jsonarray = " + JSONUriList);
            for (int i = 0; i < JSONUriList.length(); i++) {
                uriList.add(JSONUriList.optString(i));
            }
        }
        ArrayList<String> urlList = new ArrayList<>();
        JSONUriList = null;
        try {
            JSONUriList = new JSONArray(cursor.getString(5));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (JSONUriList != null) {
            Log.d(TAG, "jsonarray urls = " + JSONUriList);
            for (int i = 0; i < JSONUriList.length(); i++) {
                urlList.add(JSONUriList.optString(i));
            }
        }

        PendingNote pendingNote = new PendingNote(cursor.getString(0),
                uriList, Integer.parseInt(cursor.getString(2)), cursor.getString(3), cursor.getString(4), urlList);

        return pendingNote;
    }

    public PendingNote getNextPendingNote() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PENDING_NOTES, new String[] { KEY_COURSEID,
                KEY_URILIST, KEY_NOOFPAGESUPLOADED, KEY_TITLE, KEY_NOTESDESC , KEY_URLS}, null, null, null, null, null,
                null);

        if (cursor != null)
            cursor.moveToFirst();
        else {
            return null;
        }

        if (cursor.getCount() == 0)
            return null;

        JSONArray JSONUriList = null;
        ArrayList<String> uriList = new ArrayList<>();
        try {
            JSONUriList = new JSONArray(cursor.getString(1));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (JSONUriList != null) {
            Log.d(TAG, "jsonarray = " + JSONUriList);
            for (int i = 0; i < JSONUriList.length(); i++) {
                uriList.add(JSONUriList.optString(i));
            }
        }

        ArrayList<String> urlList = new ArrayList<>();
        JSONUriList = null;
        try {
            JSONUriList = new JSONArray(cursor.getString(5));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (JSONUriList != null) {
            Log.d(TAG, "jsonarray urls = " + JSONUriList);
            for (int i = 0; i < JSONUriList.length(); i++) {
                urlList.add(JSONUriList.optString(i));
            }
        }

        PendingNote pendingNote = new PendingNote(cursor.getString(0),
                uriList, Integer.parseInt(cursor.getString(2)), cursor.getString(3), cursor.getString(4), urlList);

        return pendingNote;
    }

    public int getPendingNotesCount() {
        String countQuery = "SELECT  * FROM " + TABLE_PENDING_NOTES;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);
        cursor.close();

        return cursor.getCount();
    }

    public int incrementPageNumberOfPendingNote(String courseId) {
        SQLiteDatabase db = this.getWritableDatabase();
        PendingNote note = getPendingNote(courseId);
        ContentValues values = new ContentValues();

        values.put(KEY_NOOFPAGESUPLOADED, note.getNoOfPagesUploaded() + 1);

        return db.update(TABLE_PENDING_NOTES, values, KEY_COURSEID + "=?",
                new String[] {note.getCourseId()});
    }

    public int deletePendingNote(String courseId) {
        SQLiteDatabase db = this.getWritableDatabase();
        Log.d(TAG, "Deleting course " + courseId);
        return db.delete(TABLE_PENDING_NOTES, KEY_COURSEID + " = '" + courseId + "'", null);
    }

    public int appendUrlToList(String courseId, String url) {
        SQLiteDatabase db = this.getWritableDatabase();
        PendingNote note = getPendingNote(courseId);
        ContentValues values = new ContentValues();
        ArrayList<String> urlList = note.getUrls();
        urlList.add(url);
        JSONArray jsonArray = new JSONArray(urlList);
        values.put(KEY_URLS, jsonArray.toString());

        return db.update(TABLE_PENDING_NOTES, values, KEY_COURSEID + "=?", new String[] {courseId});
    }
}
