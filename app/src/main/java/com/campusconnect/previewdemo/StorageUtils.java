package com.campusconnect.previewdemo;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentUris;
//import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
//import android.location.Location;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video.VideoColumns;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import static com.campusconnect.previewdemo.MainActivity.uriList;

/** Provides access to the filesystem. Supports both standard and Storage
 *  Access Framework.
 */
public class StorageUtils {
	private static final String TAG = "StorageUtils";

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

	Context context = null;
    private Uri last_media_scanned = null;

	// for testing:
	public boolean failed_to_scan = false;
	
	StorageUtils(Context context) {
		this.context = context;
	}
	
	Uri getLastMediaScanned() {
		return last_media_scanned;
	}
	void clearLastMediaScanned() {
		last_media_scanned = null;
	}

	/** Sends the intents to announce the new file to other Android applications. E.g., cloud storage applications like
	 *  OwnCloud use this to listen for new photos/videos to automatically upload.
	 */
	void announceUri(Uri uri, boolean is_new_picture, boolean is_new_video) {
    	if( is_new_picture ) {
    		// note, we reference the string directly rather than via Camera.ACTION_NEW_PICTURE, as the latter class is now deprecated - but we still need to broadcast the string for other apps
    		context.sendBroadcast(new Intent( "android.hardware.action.NEW_PICTURE" , uri));
    		// for compatibility with some apps - apparently this is what used to be broadcast on Android?
    		context.sendBroadcast(new Intent("com.android.camera.NEW_PICTURE", uri));
    	}
    	else if( is_new_video ) {
    		context.sendBroadcast(new Intent("android.hardware.action.NEW_VIDEO", uri));

    	}
	}

	/** Sends a "broadcast" for the new file. This is necessary so that Android recognises the new file without needing a reboot:
	 *  - So that they show up when connected to a PC using MTP.
	 *  - For JPEGs, so that they show up in gallery applications.
	 *  - This also calls announceUri() on the resultant Uri for the new file.
	 *  - Note this should also be called after deleting a file.
	 *  - Note that for DNG files, MediaScannerConnection.scanFile() doesn't result in the files being shown in gallery applications.
	 *    This may well be intentional, since most gallery applications won't read DNG files anyway. But it's still important to
	 *    call this function for DNGs, so that they show up on MTP.
	 */
    public void broadcastFile(final File file, final boolean is_new_picture, final boolean is_new_video, final boolean set_last_scanned) {
    	// note that the new method means that the new folder shows up as a file when connected to a PC via MTP (at least tested on Windows 8)
    	if( file.isDirectory() ) {
    		//this.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(file)));
        	// ACTION_MEDIA_MOUNTED no longer allowed on Android 4.4! Gives: SecurityException: Permission Denial: not allowed to send broadcast android.intent.action.MEDIA_MOUNTED
    		// note that we don't actually need to broadcast anything, the folder and contents appear straight away (both in Gallery on device, and on a PC when connecting via MTP)
    		// also note that we definitely don't want to broadcast ACTION_MEDIA_SCANNER_SCAN_FILE or use scanFile() for folders, as this means the folder shows up as a file on a PC via MTP (and isn't fixed by rebooting!)
    	}
    	else {
        	// both of these work fine, but using MediaScannerConnection.scanFile() seems to be preferred over sending an intent
    		//context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
 			failed_to_scan = true; // set to true until scanned okay
        	MediaScannerConnection.scanFile(context, new String[] { file.getAbsolutePath() }, null,
        			new MediaScannerConnection.OnScanCompletedListener() {
					public void onScanCompleted(String path, Uri uri) {
						uriList.add(uri.toString());
    		 			failed_to_scan = false;
    		 			if( set_last_scanned ) {
    		 				last_media_scanned = uri;
    		 			}
    		 			announceUri(uri, is_new_picture, is_new_video);

    	    			// it seems caller apps seem to prefer the content:// Uri rather than one based on a File
    		 			Activity activity = (Activity)context;
    		 			String action = activity.getIntent().getAction();
    		 	        if( MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
	    		 			Intent output = new Intent();
	    		 			output.setData(uri);
	    		 			activity.setResult(Activity.RESULT_OK, output);
	    		 			activity.finish();
    		 	        }
    		 		}
    			}
    		);
    	}
	}

    boolean isUsingSAF() {
    	// check Android version just to be safe
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
			if( sharedPreferences.getBoolean(PreferenceKeys.getUsingSAFPreferenceKey(), false) ) {
				return true;
			}
        }
        return false;
    }

    // only valid if !isUsingSAF()
    String getSaveLocation() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		String folder_name = sharedPreferences.getString(PreferenceKeys.getSaveLocationPreferenceKey(), "OpenCamera");
		return folder_name;
    }
    
    public static File getBaseFolder() {
    	return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    }

    public static File getImageFolder(String folder_name) {
		File file = null;
		if( folder_name.length() > 0 && folder_name.lastIndexOf('/') == folder_name.length()-1 ) {
			// ignore final '/' character
			folder_name = folder_name.substring(0, folder_name.length()-1);
		}
		//if( folder_name.contains("/") ) {
		if( folder_name.startsWith("/") ) {
			file = new File(folder_name);
		}
		else {
	        file = new File(getBaseFolder(), folder_name);
		}
        return file;
    }

    // only valid if isUsingSAF()
    String getSaveLocationSAF() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		String folder_name = sharedPreferences.getString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "");
		return folder_name;
    }

    // only valid if isUsingSAF()
    Uri getTreeUriSAF() {
    	String folder_name = getSaveLocationSAF();
		Uri treeUri = Uri.parse(folder_name);
		return treeUri;
    }

	/** Returns a human readable name for the current SAF save folder location.
     * Only valid if isUsingSAF().
	 * @return The human readable form. This will be null if the Uri is not recognised.
	 */
    // only valid if isUsingSAF()
    // return a human readable name for the SAF save folder location
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	String getImageFolderNameSAF() {
		Uri uri = getTreeUriSAF();
		return getImageFolderNameSAF(uri);
	}

	/** Returns a human readable name for a SAF save folder location.
     * Only valid if isUsingSAF().
	 * @param folder_name The SAF uri for the requested save location.
	 * @return The human readable form. This will be null if the Uri is not recognised.
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	String getImageFolderNameSAF(Uri folder_name) {
	    String filename = null;
		if( "com.android.externalstorage.documents".equals(folder_name.getAuthority()) ) {
            final String id = DocumentsContract.getTreeDocumentId(folder_name);

            String [] split = id.split(":");
            if( split.length >= 2 ) {
                String type = split[0];
    		    String path = split[1];
        		filename = path;
            }
		}
		return filename;
	}

    // only valid if isUsingSAF()
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private File getFileFromDocumentIdSAF(String id) {
	    File file = null;
        String [] split = id.split(":");
        if( split.length >= 2 ) {
            String type = split[0];
		    String path = split[1];
		    File [] storagePoints = new File("/storage").listFiles();

            if( "primary".equalsIgnoreCase(type) ) {
    			final File externalStorage = Environment.getExternalStorageDirectory();
    			file = new File(externalStorage, path);
            }
	        for(int i=0;storagePoints != null && i<storagePoints.length && file==null;i++) {
	            File externalFile = new File(storagePoints[i], path);
	            if( externalFile.exists() ) {
	            	file = externalFile;
	            }
	        }
		}
		return file;
	}

    // valid if whether or not isUsingSAF()
    // but note that if isUsingSAF(), this may return null - it can't be assumed that there is a File corresponding to the SAF Uri
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
    File getImageFolder() {
		File file = null;
    	if( isUsingSAF() ) {
    		Uri uri = getTreeUriSAF();
    		if( "com.android.externalstorage.documents".equals(uri.getAuthority()) ) {
                final String id = DocumentsContract.getTreeDocumentId(uri);
        		file = getFileFromDocumentIdSAF(id);
    		}
    	}
    	else {
    		String folder_name = getSaveLocation();
    		file = getImageFolder(folder_name);
    	}
    	return file;
    }

	// only valid if isUsingSAF()
	// This function should only be used as a last resort - we shouldn't generally assume that a Uri represents an actual File, and instead.
	// However this is needed for a workaround to the fact that deleting a document file doesn't remove it from MediaStore.
	// See:
	// http://stackoverflow.com/questions/21605493/storage-access-framework-does-not-update-mediascanner-mtp
	// http://stackoverflow.com/questions/20067508/get-real-path-from-uri-android-kitkat-new-storage-access-framework/
    // only valid if isUsingSAF()
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	File getFileFromDocumentUriSAF(Uri uri) {
	    File file = null;
		if( "com.android.externalstorage.documents".equals(uri.getAuthority()) ) {
            final String id = DocumentsContract.getDocumentId(uri);
    		file = getFileFromDocumentIdSAF(id);
		}
		return file;
	}
	
	private String createMediaFilename(int type, String suffix, int count, String extension, Date current_date) {
        String index = "";
        if( count > 0 ) {
            index = "_" + count; // try to find a unique filename
        }
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		boolean useZuluTime = sharedPreferences.getString(PreferenceKeys.getSaveZuluTimePreferenceKey(), "local").equals("zulu");
		String timeStamp = null;
		if( useZuluTime ) {
			SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd_HHmmss'Z'", Locale.US);
			fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
			timeStamp = fmt.format(current_date);
		}
		else {
			timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(current_date);
		}
		String mediaFilename = null;
        if( type == MEDIA_TYPE_IMAGE ) {
    		String prefix = sharedPreferences.getString(PreferenceKeys.getSavePhotoPrefixPreferenceKey(), "IMG_");
    		mediaFilename = prefix + timeStamp + suffix + index + "." + extension;
        }
        else if( type == MEDIA_TYPE_VIDEO ) {
    		String prefix = sharedPreferences.getString(PreferenceKeys.getSaveVideoPrefixPreferenceKey(), "VID_");
    		mediaFilename = prefix + timeStamp + suffix + index + "." + extension;
        }
        else {
        	// throw exception as this is a programming error
        	throw new RuntimeException();
        }
        return mediaFilename;
    }
    
    // only valid if !isUsingSAF()
    @SuppressLint("SimpleDateFormat")
	File createOutputMediaFile(int type, String suffix, String extension, Date current_date) throws IOException {
    	File mediaStorageDir = getImageFolder();

        // Create the storage directory if it does not exist
        if( !mediaStorageDir.exists() ) {
            if( !mediaStorageDir.mkdirs() ) {
        		throw new IOException();
            }
            broadcastFile(mediaStorageDir, false, false, false);
        }

        // Create a media file name
        File mediaFile = null;
        for(int count=0;count<100;count++) {
        	String mediaFilename = createMediaFilename(type, suffix, count, extension, current_date);
            mediaFile = new File(mediaStorageDir.getPath() + File.separator + mediaFilename);
            if( !mediaFile.exists() ) {
            	break;
            }
        }
		if( mediaFile == null )
			throw new IOException();
        return mediaFile;
    }

    // only valid if isUsingSAF()
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    Uri createOutputMediaFileSAF(int type, String suffix, String extension, Date current_date) throws IOException {
    	try {
	    	Uri treeUri = getTreeUriSAF();
	        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));

		    String mimeType = "";
	        if( type == MEDIA_TYPE_IMAGE ) {
	        	if( extension.equals("dng") ) {
	        		mimeType = "image/dng";
	        		//mimeType = "image/x-adobe-dng";
	        	}
	        	else
	        		mimeType = "image/jpeg";
	        }
	        else if( type == MEDIA_TYPE_VIDEO ) {
	        	mimeType = "video/mp4";
	        }
	        else {
	        	// throw exception as this is a programming error
	        	throw new RuntimeException();
	        }
	        // note that DocumentsContract.createDocument will automatically append to the filename if it already exists
	        String mediaFilename = createMediaFilename(type, suffix, 0, extension, current_date);
		    Uri fileUri = DocumentsContract.createDocument(context.getContentResolver(), docUri, mimeType, mediaFilename);
			if( fileUri == null )
				throw new IOException();
	    	return fileUri;
    	}
    	catch(IllegalArgumentException e) {
    		// DocumentsContract.getTreeDocumentId throws this if URI is invalid
		    e.printStackTrace();
		    throw new IOException();
    	}
    }

    static class Media {
    	long id;
    	boolean video;
    	Uri uri;
    	long date;
    	int orientation;

    	Media(long id, boolean video, Uri uri, long date, int orientation) {
    		this.id = id;
    		this.video = video;
    		this.uri = uri;
    		this.date = date;
    		this.orientation = orientation;
    	}
    }
    
    private Media getLatestMedia(boolean video) {
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ) {
			// needed for Android 6, in case users deny storage permission, otherwise we get java.lang.SecurityException from ContentResolver.query()
			// see https://developer.android.com/training/permissions/requesting.html
			// currently we don't bother requesting the permission, as still using targetSdkVersion 22
			// we restrict check to Android 6 or later just in case, see note in LocationSupplier.setupLocationListener()
			return null;
		}
    	Media media = null;
		Uri baseUri = video ? Video.Media.EXTERNAL_CONTENT_URI : Images.Media.EXTERNAL_CONTENT_URI;
		//Uri query = baseUri.buildUpon().appendQueryParameter("limit", "1").build();
		Uri query = baseUri;
		final int column_id_c = 0;
		final int column_date_taken_c = 1;
		final int column_data_c = 2;
		final int column_orientation_c = 3;
		String [] projection = video ? new String[] {VideoColumns._ID, VideoColumns.DATE_TAKEN, VideoColumns.DATA} : new String[] {ImageColumns._ID, ImageColumns.DATE_TAKEN, ImageColumns.DATA, ImageColumns.ORIENTATION};
		String selection = video ? "" : ImageColumns.MIME_TYPE + "='image/jpeg'";
		String order = video ? VideoColumns.DATE_TAKEN + " DESC," + VideoColumns._ID + " DESC" : ImageColumns.DATE_TAKEN + " DESC," + ImageColumns._ID + " DESC";
		Cursor cursor = null;
		try {
			cursor = context.getContentResolver().query(query, projection, selection, null, order);
			if( cursor != null && cursor.moveToFirst() ) {
				// now sorted in order of date - scan to most recent one in the Open Camera save folder
				boolean found = false;
				File save_folder = getImageFolder(); // may be null if using SAF
				String save_folder_string = save_folder == null ? null : save_folder.getAbsolutePath() + File.separator;
				do {
					String path = cursor.getString(column_data_c);
					// path may be null on Android 4.4!: http://stackoverflow.com/questions/3401579/get-filename-and-path-from-uri-from-mediastore
					if( save_folder_string == null || (path != null && path.contains(save_folder_string) ) ) {
						// we filter files with dates in future, in case there exists an image in the folder with incorrect datestamp set to the future
						// we allow up to 2 days in future, to avoid risk of issues to do with timezone etc
						long date = cursor.getLong(column_date_taken_c);
				    	long current_time = System.currentTimeMillis();
						if( date > current_time + 172800000 ) {
						}
						else {
							found = true;
							break;
						}
					}
				} while( cursor.moveToNext() );
				if( !found ) {
					cursor.moveToFirst();
				}
				long id = cursor.getLong(column_id_c);
				long date = cursor.getLong(column_date_taken_c);
				int orientation = video ? 0 : cursor.getInt(column_orientation_c);
				Uri uri = ContentUris.withAppendedId(baseUri, id);
				media = new Media(id, video, uri, date, orientation);
			}
		}
		catch(SQLiteException e) {
			// had this reported on Google Play from getContentResolver().query() call
			e.printStackTrace();
		}
		finally {
			if( cursor != null ) {
				cursor.close();
			}
		}
		return media;
    }
    
    Media getLatestMedia() {
		Media image_media = getLatestMedia(false);
		Media video_media = getLatestMedia(true);
		Media media = null;
		if( image_media != null && video_media == null ) {
			media = image_media;
		}
		else if( image_media == null && video_media != null ) {
			media = video_media;
		}
		else if( image_media != null && video_media != null ) {
			if( image_media.date >= video_media.date ) {
				media = image_media;
			}
			else {
				media = video_media;
			}
		}
		return media;
    }
}
