package com.campusconnect.previewdemo.UploadManager;

import java.util.ArrayList;

/**
 * Created by aniru on 13-10-2016.
 */

public class PendingNote {
    String courseId;
    ArrayList<String> uriList;
    int noOfPagesUploaded;
    String title;
    String notesDesc;
    ArrayList<String> urls;

    public PendingNote(String cId, ArrayList<String> uList, int pages, String title, String notesDesc, ArrayList<String> urlList) {
        this.courseId = cId;
        this.uriList = uList;
        this.noOfPagesUploaded = pages;
        this.title = title;
        this.notesDesc = notesDesc;
        this.urls = urlList;
    }

    public ArrayList<String> getUriList() {
        return this.uriList;
    }

    public void setUriList(ArrayList<String> uList) {
        this.uriList = uList;
    }

    public String getCourseId() {
        return this.courseId;
    }

    public void setCourseId(String cId) {
        this.courseId = cId;
    }

    public int getNoOfPagesUploaded() {
        return this.noOfPagesUploaded;
    }

    public void setNoOfPagesUploaded(int pages) {
        this.noOfPagesUploaded = pages;
    }

    public String getTitle() { return this.title; }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getNotesDesc() { return this.notesDesc; }

    public void setNotesDesc(String notesDesc) {
        this.notesDesc = notesDesc;
    }

    public ArrayList<String> getUrls() { return this.urls; }

    public void setUrls(ArrayList<String> urlList) { this.urls = urlList; }
}
