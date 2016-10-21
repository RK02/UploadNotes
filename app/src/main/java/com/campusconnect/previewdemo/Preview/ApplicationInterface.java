package com.campusconnect.previewdemo.Preview;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.hardware.camera2.DngCreator;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.Image;
import android.net.Uri;
import android.util.Pair;
import android.view.MotionEvent;

/** Provides communication between the Preview and the rest of the application
 *  - so in theory one can drop the Preview/ (and CameraController/) classes
 *  into a new application, by providing an appropriate implementation of this
 *  ApplicationInterface.
 */
public interface ApplicationInterface {

	// methods that request information
	Context getContext(); // get the application context
	boolean useCamera2(); // should Android 5's Camera 2 API be used?
	Location getLocation(); // get current location - null if not available (or you don't care about geotagging)
	// for all of the get*Pref() methods, you can use Preview methods to get the supported values (e.g., getSupportedSceneModes())
	// if you just want a default or don't really care, see the comments for each method for a default or possible options
	// if Preview doesn't support the requested setting, it will check this, and choose its own
	int getCameraIdPref(); // camera to use, from 0 to getCameraControllerManager().getNumberOfCameras()
	String getFlashPref(); // flash_off, flash_auto, flash_on, flash_torch, flash_red_eye
	String getFocusPref(boolean is_video); // focus_mode_auto, focus_mode_infinity, focus_mode_macro, focus_mode_locked, focus_mode_fixed, focus_mode_manual2, focus_mode_edof, focus_mode_continuous_video
	boolean isVideoPref(); // start up in video mode?
	Pair<Integer, Integer> getCameraResolutionPref(); // return null to let Preview choose size
	boolean getForce4KPref(); // whether to force 4K mode - experimental, only really available for some devices that allow 4K recording but don't return it as an available resolution - not recommended for most uses
	String getVideoBitratePref(); // return "default" to let Preview choose
	String getVideoFPSPref(); // return "default" to let Preview choose
	String getPreviewSizePref(); // "preference_preview_size_wysiwyg" is recommended (preview matches aspect ratio of photo resolution as close as possible), but can also be "preference_preview_size_display" to maximise the preview size
	String getPreviewRotationPref(); // return "0" for default; use "180" to rotate the preview 180 degrees
	String getLockOrientationPref(); // return "none" for default; use "portrait" or "landscape" to lock photos/videos to that orientation
    boolean getTouchCapturePref(); // whether to enable touch to capture
    boolean getDoubleTapCapturePref(); // whether to enable double-tap to capture
	boolean getPausePreviewPref(); // whether to pause the preview after taking a photo
	boolean getShowToastsPref();
	boolean getShutterSoundPref(); // whether to play sound when taking photo
	boolean getStartupFocusPref(); // whether to do autofocus on startup
	long getTimerPref(); // time in ms for timer (so 0 for off)
	String getRepeatPref(); // return number of times to repeat photo in a row (as a string), so "1" for default; return "unlimited" for unlimited
	long getRepeatIntervalPref(); // time in ms between repeat
	boolean getGeotaggingPref(); // whether to geotag photos
	boolean getRequireLocationPref(); // if getGeotaggingPref() returns true, and this method returns true, then phot/video will only be taken if location data is available
	// Camera2 only modes:
	float getFocusDistancePref();
	// for testing purposes:
	boolean isTestAlwaysFocus(); // if true, pretend autofocus always successful

	// methods that transmit information/events (up to the Application whether to do anything or not)
    void cameraSetup(); // called when the camera is (re-)set up - should update UI elements/parameters that depend on camera settings
	void touchEvent(MotionEvent event);
	void onFailedStartPreview(); // called if failed to start camera preview
	void onPhotoError(); // callback for failing to take a photo
	void onFailedReconnectError(); // failed to reconnect camera after stopping video recording
	void hasPausedPreview(boolean paused); // called when the preview is paused or unpaused (due to getPausePreviewPref())
	void cameraInOperation(boolean in_operation); // called when the camera starts/stops being operation (taking photos or recording video, including if preview is paused after taking a photo), use to disable GUI elements during camera operation
	void cameraClosed();

	// methods that request actions
	void layoutUI(); // application should layout UI that's on top of the preview
	// the set/clear*Pref() methods are called if Preview decides to override the requested pref (because Camera device doesn't support requested pref) (clear*Pref() is called if the feature isn't supported at all)
	// the application can use this information to update its preferences
	void setCameraIdPref(int cameraId);
	void setFlashPref(String flash_value);
	void setFocusPref(String focus_value, boolean is_video);
	void setExposureCompensationPref(int exposure);
	void clearExposureCompensationPref();
	void setCameraResolutionPref(int width, int height);
	// Camera2 only modes:
	void setFocusDistancePref(float focus_distance);
	
	// callbacks
	void onDrawPreview(Canvas canvas);
	boolean onPictureTaken(byte[] data, Date current_date);
	void onPictureCompleted(); // called after all picture callbacks have been called and returned
	void onContinuousFocusMove(boolean start); // called when focusing starts/stop in continuous picture mode (in photo mode only)
}
