package com.campusconnect.previewdemo.CameraController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusMoveCallback;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;

/** Provides support using Android's original camera API
 *  android.hardware.Camera.
 */
@SuppressWarnings("deprecation")
public class CameraController1 extends CameraController {
	private static final String TAG = "CameraController1";

	private Camera camera = null;
    private int display_orientation = 0;
    private Camera.CameraInfo camera_info = new Camera.CameraInfo();
	private String iso_key = null;

	public CameraController1(int cameraId) throws CameraControllerException {
		super(cameraId);
		try {
			camera = Camera.open(cameraId);
		}
		catch(RuntimeException e) {
			e.printStackTrace();
			throw new CameraControllerException();
		}
		if( camera == null ) {
			// Although the documentation says Camera.open() should throw a RuntimeException, it seems that it some cases it can return null
			// I've seen this in some crashes reported in Google Play; also see:
			// http://stackoverflow.com/questions/12054022/camera-open-returns-null
			throw new CameraControllerException();
		}
		try {
			Camera.getCameraInfo(cameraId, camera_info);
		}
		catch(RuntimeException e) {
			// Had reported RuntimeExceptions from Google Play
			// also see http://stackoverflow.com/questions/22383708/java-lang-runtimeexception-fail-to-get-camera-info
			e.printStackTrace();
			this.release();
			throw new CameraControllerException();
		}
		/*{
			// TEST cam_mode workaround from http://stackoverflow.com/questions/7225571/camcorderprofile-quality-high-resolution-produces-green-flickering-video
			if( MyDebug.LOG )
				Log.d(TAG, "setting cam_mode workaround");
	    	Camera.Parameters parameters = this.getParameters();
	    	parameters.set("cam_mode", 1);
	    	setCameraParameters(parameters);
		}*/
		
		camera.setErrorCallback(new CameraErrorCallback());
	}
	
	private static class CameraErrorCallback implements Camera.ErrorCallback {
		@Override
		public void onError(int error, Camera camera) {
			// n.b., as this is potentially serious error, we always log even if MyDebug.LOG is false
			Log.e(TAG, "camera onError: " + error);
			if( error == Camera.CAMERA_ERROR_SERVER_DIED ) {
				Log.e(TAG, "    CAMERA_ERROR_SERVER_DIED");
			}
			else if( error == Camera.CAMERA_ERROR_UNKNOWN  ) {
				Log.e(TAG, "    CAMERA_ERROR_UNKNOWN ");
			}
		}
	}
	
	public void release() {
		camera.release();
		camera = null;
	}

	public Camera getCamera() {
		return camera;
	}
	
	private Camera.Parameters getParameters() {
		return camera.getParameters();
	}
	
	private void setCameraParameters(Camera.Parameters parameters) {
	    try {
			camera.setParameters(parameters);
	    }
	    catch(RuntimeException e) {
	    	// just in case something has gone wrong
    		e.printStackTrace();
    		count_camera_parameters_exception++;
	    }
	}
	
	private List<String> convertFlashModesToValues(List<String> supported_flash_modes) {
		List<String> output_modes = new Vector<String>();
		if( supported_flash_modes != null ) {
			// also resort as well as converting
			if( supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_OFF) ) {
				output_modes.add("flash_off");
			}
			if( supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_AUTO) ) {
				output_modes.add("flash_auto");
			}
			if( supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_ON) ) {
				output_modes.add("flash_on");
			}
			if( supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_TORCH) ) {
				output_modes.add("flash_torch");
			}
			if( supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_RED_EYE) ) {
				output_modes.add("flash_red_eye");
			}
		}
		return output_modes;
	}

	private List<String> convertFocusModesToValues(List<String> supported_focus_modes) {
		List<String> output_modes = new Vector<String>();
		if( supported_focus_modes != null ) {
			// also resort as well as converting
			if( supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_AUTO) ) {
				output_modes.add("focus_mode_auto");
			}
			if( supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY) ) {
				output_modes.add("focus_mode_infinity");
			}
			if( supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_MACRO) ) {
				output_modes.add("focus_mode_macro");
			}
			if( supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_AUTO) ) {
				output_modes.add("focus_mode_locked");
			}
			if( supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_FIXED) ) {
				output_modes.add("focus_mode_fixed");
			}
			if( supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_EDOF) ) {
				output_modes.add("focus_mode_edof");
			}
			if( supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) ) {
				output_modes.add("focus_mode_continuous_picture");
			}
			if( supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) ) {
				output_modes.add("focus_mode_continuous_video");
			}
		}
		return output_modes;
	}
	
	public String getAPI() {
		return "Camera";
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	public CameraFeatures getCameraFeatures() {
	    Camera.Parameters parameters = this.getParameters();
	    CameraFeatures camera_features = new CameraFeatures();

		// get available sizes
		List<Camera.Size> camera_picture_sizes = parameters.getSupportedPictureSizes();
		camera_features.picture_sizes = new ArrayList<Size>();
		//camera_features.picture_sizes.add(new CameraController.Size(1920, 1080)); // test
		for(Camera.Size camera_size : camera_picture_sizes) {
			camera_features.picture_sizes.add(new CameraController.Size(camera_size.width, camera_size.height));
		}

        //camera_features.supported_flash_modes = parameters.getSupportedFlashModes(); // Android format
        List<String> supported_flash_modes = parameters.getSupportedFlashModes(); // Android format
		camera_features.supported_flash_values = convertFlashModesToValues(supported_flash_modes); // convert to our format (also resorts)

        List<String> supported_focus_modes = parameters.getSupportedFocusModes(); // Android format
		camera_features.supported_focus_values = convertFocusModesToValues(supported_focus_modes); // convert to our format (also resorts)
		camera_features.max_num_focus_areas = parameters.getMaxNumFocusAreas();

        camera_features.is_exposure_lock_supported = parameters.isAutoExposureLockSupported();
        
        camera_features.min_exposure = parameters.getMinExposureCompensation();
        camera_features.max_exposure = parameters.getMaxExposureCompensation();
        try {
        	camera_features.exposure_step = parameters.getExposureCompensationStep();
        }
        catch(Exception e) {
        	// received a NullPointerException from StringToReal.parseFloat() beneath getExposureCompensationStep() on Google Play!
        	e.printStackTrace();
        	camera_features.exposure_step = 1.0f/3.0f; // make up a typical example
        }

		List<Camera.Size> camera_preview_sizes = parameters.getSupportedPreviewSizes();
		camera_features.preview_sizes = new ArrayList<Size>();
		for(Camera.Size camera_size : camera_preview_sizes) {
			camera_features.preview_sizes.add(new CameraController.Size(camera_size.width, camera_size.height));
		}

		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 ) {
        	// Camera.canDisableShutterSound requires JELLY_BEAN_MR1 or greater
        	camera_features.can_disable_shutter_sound = camera_info.canDisableShutterSound;
        }
        else {
        	camera_features.can_disable_shutter_sound = false;
        }

		return camera_features;
	}

	// important, from docs:
	// "Changing scene mode may override other parameters (such as flash mode, focus mode, white balance).
	// For example, suppose originally flash mode is on and supported flash modes are on/off. In night
	// scene mode, both flash mode and supported flash mode may be changed to off. After setting scene
	// mode, applications should call getParameters to know if some parameters are changed."

	public void setSceneMode(String value) {
		String default_value = getDefaultSceneMode();
    	Camera.Parameters parameters = this.getParameters();
		parameters.setSceneMode(default_value);
		setCameraParameters(parameters);
	}
	
	public String getSceneMode() {
    	Camera.Parameters parameters = this.getParameters();
    	return parameters.getSceneMode();
	}

	public void setColorEffect(String value) {
		String default_value = getDefaultColorEffect();
    	Camera.Parameters parameters = this.getParameters();
		parameters.setColorEffect(default_value);
		setCameraParameters(parameters);
	}

	public void setWhiteBalance(String value) {
		String default_value = getDefaultWhiteBalance();
    	Camera.Parameters parameters = this.getParameters();
		parameters.setWhiteBalance(default_value);
		setCameraParameters(parameters);
	}

	@Override
	public void setISO(String value) {
		String default_value = getDefaultISO();
    	Camera.Parameters parameters = this.getParameters();

		iso_key = "iso";
		if( parameters.get(iso_key) == null ) {
			iso_key = "iso-speed"; // Micromax A101
			if( parameters.get(iso_key) == null ) {
				iso_key = "nv-picture-iso"; // LG dual P990
				if( parameters.get(iso_key) == null ) {
					if ( Build.MODEL.contains("Z00") )
						iso_key = "iso"; // Asus Zenfone 2 Z00A and Z008: see https://sourceforge.net/p/opencamera/tickets/183/
					else
						iso_key = null; // not supported
				}
			}
		}

		if( iso_key != null ){
	        	parameters.set(iso_key, default_value);
	        	setCameraParameters(parameters);
		}
	}

	@Override
	public String getISOKey() {
    	return this.iso_key;
    }

	@Override
	public int getISO() {
		// not supported for CameraController1
		return 0;
	}

	@Override
	public boolean setISO(int iso) {
		// not supported for CameraController1
		return false;
	}

	@Override
	public boolean setExposureTime(long exposure_time) {
		// not supported for CameraController1
		return false;
	}

	@Override
    public CameraController.Size getPictureSize() {
    	Camera.Parameters parameters = this.getParameters();
    	Camera.Size camera_size = parameters.getPictureSize();
    	return new CameraController.Size(camera_size.width, camera_size.height);
    }

	@Override
	public void setPictureSize(int width, int height) {
    	Camera.Parameters parameters = this.getParameters();
		parameters.setPictureSize(width, height);
    	setCameraParameters(parameters);
	}

//	@Override
//    public CameraController.Size getPreviewSize() {
//    	Camera.Parameters parameters = this.getParameters();
//    	Camera.Size camera_size = parameters.getPreviewSize();
//    	return new CameraController.Size(camera_size.width, camera_size.height);
//    }

	@Override
	public void setPreviewSize(int width, int height) {
    	Camera.Parameters parameters = this.getParameters();
        parameters.setPreviewSize(width, height);
    	setCameraParameters(parameters);
    }

	public void setJpegQuality(int quality) {
	    Camera.Parameters parameters = this.getParameters();
		parameters.setJpegQuality(quality);
    	setCameraParameters(parameters);
	}
	
	public int getZoom() {
		Camera.Parameters parameters = this.getParameters();
		return parameters.getZoom();
	}
	
	public void setZoom(int value) {
		Camera.Parameters parameters = this.getParameters();
		parameters.setZoom(value);
    	setCameraParameters(parameters);
	}

	public int getExposureCompensation() {
		Camera.Parameters parameters = this.getParameters();
		return parameters.getExposureCompensation();
	}
	
	// Returns whether exposure was modified
	public boolean setExposureCompensation(int new_exposure) {
		Camera.Parameters parameters = this.getParameters();
		int current_exposure = parameters.getExposureCompensation();
		if( new_exposure != current_exposure ) {
			parameters.setExposureCompensation(new_exposure);
        	setCameraParameters(parameters);
        	return true;
		}
		return false;
	}
	
	public void setPreviewFpsRange(int min, int max) {
		Camera.Parameters parameters = this.getParameters();
        parameters.setPreviewFpsRange(min, max);
    	setCameraParameters(parameters);
	}
	
	public List<int []> getSupportedPreviewFpsRange() {
		Camera.Parameters parameters = this.getParameters();
		try {
			List<int []> fps_ranges = parameters.getSupportedPreviewFpsRange();
			return fps_ranges;
		}
		catch(StringIndexOutOfBoundsException e) {
			/* Have had reports of StringIndexOutOfBoundsException on Google Play on Sony Xperia M devices
				at android.hardware.Camera$Parameters.splitRange(Camera.java:4098)
				at android.hardware.Camera$Parameters.getSupportedPreviewFpsRange(Camera.java:2799)
				*/
			e.printStackTrace();
		}
		return null;
	}
	
	@Override
	public void setFocusValue(String focus_value) {
		Camera.Parameters parameters = this.getParameters();
    	if( focus_value.equals("focus_mode_auto") || focus_value.equals("focus_mode_locked") ) {
    		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
    	}
    	else if( focus_value.equals("focus_mode_infinity") ) {
    		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
    	}
    	else if( focus_value.equals("focus_mode_macro") ) {
    		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
    	}
    	else if( focus_value.equals("focus_mode_fixed") ) {
    		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
    	}
    	else if( focus_value.equals("focus_mode_edof") ) {
    		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_EDOF);
    	}
    	else if( focus_value.equals("focus_mode_continuous_picture") ) {
    		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
    	}
    	else if( focus_value.equals("focus_mode_continuous_video") ) {
    		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    	}
    	else {
    	}
    	setCameraParameters(parameters);
	}
	
	private String convertFocusModeToValue(String focus_mode) {
		// focus_mode may be null on some devices; we return ""
		String focus_value = "";
		if( focus_mode == null ) {
			// ignore, leave focus_value at ""
		}
		else if( focus_mode.equals(Camera.Parameters.FOCUS_MODE_AUTO) ) {
    		focus_value = "focus_mode_auto";
    	}
		else if( focus_mode.equals(Camera.Parameters.FOCUS_MODE_INFINITY) ) {
    		focus_value = "focus_mode_infinity";
    	}
		else if( focus_mode.equals(Camera.Parameters.FOCUS_MODE_MACRO) ) {
    		focus_value = "focus_mode_macro";
    	}
		else if( focus_mode.equals(Camera.Parameters.FOCUS_MODE_FIXED) ) {
    		focus_value = "focus_mode_fixed";
    	}
		else if( focus_mode.equals(Camera.Parameters.FOCUS_MODE_EDOF) ) {
    		focus_value = "focus_mode_edof";
    	}
		else if( focus_mode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) ) {
    		focus_value = "focus_mode_continuous_picture";
    	}
		else if( focus_mode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) ) {
    		focus_value = "focus_mode_continuous_video";
    	}
    	return focus_value;
	}
	
	@Override
	public String getFocusValue() {
		// returns "" if Parameters.getFocusMode() returns null
		Camera.Parameters parameters = this.getParameters();
		String focus_mode = parameters.getFocusMode();
		// getFocusMode() is documented as never returning null, however I've had null pointer exceptions reported in Google Play
		return convertFocusModeToValue(focus_mode);
	}

	@Override
	public boolean setFocusDistance(float focus_distance) {
		// not supported for CameraController1!
		return false;
	}

	private String convertFlashValueToMode(String flash_value) {
		String flash_mode = "";
    	if( flash_value.equals("flash_off") ) {
    		flash_mode = Camera.Parameters.FLASH_MODE_OFF;
    	}
    	else if( flash_value.equals("flash_auto") ) {
    		flash_mode = Camera.Parameters.FLASH_MODE_AUTO;
    	}
    	else if( flash_value.equals("flash_on") ) {
    		flash_mode = Camera.Parameters.FLASH_MODE_ON;
    	}
    	else if( flash_value.equals("flash_torch") ) {
    		flash_mode = Camera.Parameters.FLASH_MODE_TORCH;
    	}
    	else if( flash_value.equals("flash_red_eye") ) {
    		flash_mode = Camera.Parameters.FLASH_MODE_RED_EYE;
    	}
    	return flash_mode;
	}
	
	public void setFlashValue(String flash_value) {
		Camera.Parameters parameters = this.getParameters();
		if( parameters.getFlashMode() == null )
			return; // flash mode not supported
		final String flash_mode = convertFlashValueToMode(flash_value);
    	if( flash_mode.length() > 0 && !flash_mode.equals(parameters.getFlashMode()) ) {
    		if( parameters.getFlashMode().equals(Camera.Parameters.FLASH_MODE_TORCH) && !flash_mode.equals(Camera.Parameters.FLASH_MODE_OFF) ) {
    			// workaround for bug on Nexus 5 and Nexus 6 where torch doesn't switch off until we set FLASH_MODE_OFF
        		parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            	setCameraParameters(parameters);
            	// need to set the correct flash mode after a delay
            	Handler handler = new Handler();
            	handler.postDelayed(new Runnable(){
            		@Override
            	    public void run(){
            			if( camera != null ) { // make sure camera wasn't released in the meantime (has a Google Play crash as a result of this)
	            			Camera.Parameters parameters = getParameters();
	                		parameters.setFlashMode(flash_mode);
	                    	setCameraParameters(parameters);
            			}
            	   }
            	}, 100);
    		}
    		else {
        		parameters.setFlashMode(flash_mode);
            	setCameraParameters(parameters);
    		}
    	}
	}
	
	private String convertFlashModeToValue(String flash_mode) {
		// flash_mode may be null, meaning flash isn't supported; we return ""
		String flash_value = "";
		if( flash_mode == null ) {
			// ignore, leave flash_value at null
		}
		else if( flash_mode.equals(Camera.Parameters.FLASH_MODE_OFF) ) {
    		flash_value = "flash_off";
    	}
    	else if( flash_mode.equals(Camera.Parameters.FLASH_MODE_AUTO) ) {
    		flash_value = "flash_auto";
    	}
    	else if( flash_mode.equals(Camera.Parameters.FLASH_MODE_ON) ) {
    		flash_value = "flash_on";
    	}
    	else if( flash_mode.equals(Camera.Parameters.FLASH_MODE_TORCH) ) {
    		flash_value = "flash_torch";
    	}
    	else if( flash_mode.equals(Camera.Parameters.FLASH_MODE_RED_EYE) ) {
    		flash_value = "flash_red_eye";
    	}
    	return flash_value;
	}
	
	public String getFlashValue() {
		// returns "" if flash isn't supported
		Camera.Parameters parameters = this.getParameters();
		String flash_mode = parameters.getFlashMode(); // will be null if flash mode not supported
		return convertFlashModeToValue(flash_mode);
	}

	public void setAutoExposureLock(boolean enabled) {
		Camera.Parameters parameters = this.getParameters();
		parameters.setAutoExposureLock(enabled);
    	setCameraParameters(parameters);
	}

	public void setRotation(int rotation) {
		Camera.Parameters parameters = this.getParameters();
		parameters.setRotation(rotation);
    	setCameraParameters(parameters);
	}
	
	public void setLocationInfo(Location location) {
        Camera.Parameters parameters = this.getParameters();
        parameters.removeGpsData();
        parameters.setGpsTimestamp(System.currentTimeMillis() / 1000); // initialise to a value (from Android camera source)
        parameters.setGpsLatitude(location.getLatitude());
        parameters.setGpsLongitude(location.getLongitude());
        parameters.setGpsProcessingMethod(location.getProvider()); // from http://boundarydevices.com/how-to-write-an-android-camera-app/
        if( location.hasAltitude() ) {
            parameters.setGpsAltitude(location.getAltitude());
        }
        else {
        	// Android camera source claims we need to fake one if not present
        	// and indeed, this is needed to fix crash on Nexus 7
            parameters.setGpsAltitude(0);
        }
        if( location.getTime() != 0 ) { // from Android camera source
        	parameters.setGpsTimestamp(location.getTime() / 1000);
        }
    	setCameraParameters(parameters);
	}
	
	public void removeLocationInfo() {
        Camera.Parameters parameters = this.getParameters();
        parameters.removeGpsData();
    	setCameraParameters(parameters);
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	public void enableShutterSound(boolean enabled) {
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 ) {
        	camera.enableShutterSound(enabled);
        }
	}
	
	public boolean setFocusAndMeteringArea(List<Area> areas) {
		List<Camera.Area> camera_areas = new ArrayList<Camera.Area>();
		for(CameraController.Area area : areas) {
			camera_areas.add(new Camera.Area(area.rect, area.weight));
		}
        Camera.Parameters parameters = this.getParameters();
		String focus_mode = parameters.getFocusMode();
		// getFocusMode() is documented as never returning null, however I've had null pointer exceptions reported in Google Play
        if( parameters.getMaxNumFocusAreas() != 0 && focus_mode != null && ( focus_mode.equals(Camera.Parameters.FOCUS_MODE_AUTO) || focus_mode.equals(Camera.Parameters.FOCUS_MODE_MACRO) || focus_mode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) || focus_mode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) ) ) {
		    parameters.setFocusAreas(camera_areas);

		    // also set metering areas
		    if( parameters.getMaxNumMeteringAreas() == 0 ) {
		    }
		    else {
		    	parameters.setMeteringAreas(camera_areas);
		    }

		    setCameraParameters(parameters);

		    return true;
        }
        else if( parameters.getMaxNumMeteringAreas() != 0 ) {
	    	parameters.setMeteringAreas(camera_areas);

		    setCameraParameters(parameters);
        }
        return false;
	}
	
	public void clearFocusAndMetering() {
        Camera.Parameters parameters = this.getParameters();
        boolean update_parameters = false;
        if( parameters.getMaxNumFocusAreas() > 0 ) {
        	parameters.setFocusAreas(null);
        	update_parameters = true;
        }
        if( parameters.getMaxNumMeteringAreas() > 0 ) {
        	parameters.setMeteringAreas(null);
        	update_parameters = true;
        }
        if( update_parameters ) {
		    setCameraParameters(parameters);
        }
	}

	@Override
	public boolean supportsAutoFocus() {
        Camera.Parameters parameters = this.getParameters();
		String focus_mode = parameters.getFocusMode();
		// getFocusMode() is documented as never returning null, however I've had null pointer exceptions reported in Google Play from the below line (v1.7),
		// on Galaxy Tab 10.1 (GT-P7500), Android 4.0.3 - 4.0.4; HTC EVO 3D X515m (shooteru), Android 4.0.3 - 4.0.4
        if( focus_mode != null && ( focus_mode.equals(Camera.Parameters.FOCUS_MODE_AUTO) || focus_mode.equals(Camera.Parameters.FOCUS_MODE_MACRO) ) ) {
        	return true;
        }
        return false;
	}
	
	@Override
	public boolean focusIsContinuous() {
        Camera.Parameters parameters = this.getParameters();
		String focus_mode = parameters.getFocusMode();
		// getFocusMode() is documented as never returning null, however I've had null pointer exceptions reported in Google Play from the below line (v1.7),
		// on Galaxy Tab 10.1 (GT-P7500), Android 4.0.3 - 4.0.4; HTC EVO 3D X515m (shooteru), Android 4.0.3 - 4.0.4
        if( focus_mode != null && ( focus_mode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) || focus_mode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) ) ) {
        	return true;
        }
        return false;
	}
	
	@Override
	public 
	void reconnect() throws CameraControllerException {
		try {
			camera.reconnect();
		}
		catch(IOException e) {
			e.printStackTrace();
			throw new CameraControllerException();
		}
	}
	
	@Override
	public void setPreviewDisplay(SurfaceHolder holder) throws CameraControllerException {
		try {
			camera.setPreviewDisplay(holder);
		}
		catch(IOException e) {
			e.printStackTrace();
			throw new CameraControllerException();
		}
	}

	@Override
	public void setPreviewTexture(SurfaceTexture texture) throws CameraControllerException {
		try {
			camera.setPreviewTexture(texture);
		}
		catch(IOException e) {
			e.printStackTrace();
			throw new CameraControllerException();
		}
	}

	@Override
	public void startPreview() throws CameraControllerException {
		try {
			camera.startPreview();
		}
		catch(RuntimeException e) {
			e.printStackTrace();
			throw new CameraControllerException();
		}
	}
	
	@Override
	public void stopPreview() {
		camera.stopPreview();
	}
	
	// returns false if RuntimeException thrown (may include if face-detection already started)
	public boolean startFaceDetection() {
	    try {
			camera.startFaceDetection();
	    }
	    catch(RuntimeException e) {
	    	return false;
	    }
	    return true;
	}

	public void autoFocus(final CameraController.AutoFocusCallback cb) {
        Camera.AutoFocusCallback camera_cb = new Camera.AutoFocusCallback() {
    		boolean done_autofocus = false;

    		@Override
			public void onAutoFocus(boolean success, Camera camera) {
				// in theory we should only ever get one call to onAutoFocus(), but some Samsung phones at least can call the callback multiple times
				// see http://stackoverflow.com/questions/36316195/take-picture-fails-on-samsung-phones
				// needed to fix problem on Samsung S7 with flash auto/on and continuous picture focus where it would claim failed to take picture even though it'd succeeded,
				// because we repeatedly call takePicture(), and the subsequent ones cause a runtime exception
				if( !done_autofocus ) {
					done_autofocus = true;
					cb.onAutoFocus(success);
				}
			}
        };
        try {
        	camera.autoFocus(camera_cb);
        }
		catch(RuntimeException e) {
			// just in case? We got a RuntimeException report here from 1 user on Google Play:
			// 21 Dec 2013, Xperia Go, Android 4.1
			e.printStackTrace();
			// should call the callback, so the application isn't left waiting (e.g., when we autofocus before trying to take a photo)
			cb.onAutoFocus(false);
		}
	}
	
	public void cancelAutoFocus() {
		try {
			camera.cancelAutoFocus();
		}
		catch(RuntimeException e) {
			// had a report of crash on some devices, see comment at https://sourceforge.net/p/opencamera/tickets/4/ made on 20140520
    		e.printStackTrace();
		}
	}
	
	@Override
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	public void setContinuousFocusMoveCallback(final ContinuousFocusMoveCallback cb) {
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN ) {
			// setAutoFocusMoveCallback() requires JELLY_BEAN
			try {
				if( cb != null ) {
					camera.setAutoFocusMoveCallback(new AutoFocusMoveCallback() {
						@Override
						public void onAutoFocusMoving(boolean start, Camera camera) {
							cb.onContinuousFocusMove(start);
						}
					});
				}
				else {
					camera.setAutoFocusMoveCallback(null);
				}
			}
			catch(RuntimeException e) {
				// received RuntimeException reports from some users on Google Play - seems to be older devices, but still important to catch!
				e.printStackTrace();
			}
		}
	}

	private static class TakePictureShutterCallback implements Camera.ShutterCallback {
		// don't do anything here, but we need to implement the callback to get the shutter sound (at least on Galaxy Nexus and Nexus 7)
		@Override
        public void onShutter() {
        }
	}
	
	public void takePicture(final CameraController.PictureCallback picture, final ErrorCallback error) {
    	Camera.ShutterCallback shutter = new TakePictureShutterCallback();
        Camera.PictureCallback camera_jpeg = picture == null ? null : new Camera.PictureCallback() {
    	    public void onPictureTaken(byte[] data, Camera cam) {
    	    	// n.b., this is automatically run in a different thread
    	    	picture.onPictureTaken(data);
    	    	picture.onCompleted();
    	    }
        };

        try {
        	camera.takePicture(shutter, null, camera_jpeg);
        }
		catch(RuntimeException e) {
			// just in case? We got a RuntimeException report here from 1 user on Google Play; I also encountered it myself once of Galaxy Nexus when starting up
			e.printStackTrace();
			error.onError();
		}
	}
	
	public void setDisplayOrientation(int degrees) {
	    int result = 0;

	        result = (camera_info.orientation - degrees + 360) % 360;

		camera.setDisplayOrientation(result);
	    this.display_orientation = result;
	}
	
	public int getDisplayOrientation() {
		return this.display_orientation;
	}
	
	public int getCameraOrientation() {
		return camera_info.orientation;
	}

	@Override
	public boolean isFrontFacing() {
		return false;
	}

	public void unlock() {
		this.stopPreview(); // although not documented, we need to stop preview to prevent device freeze or video errors shortly after video recording starts on some devices (e.g., device freeze on Samsung Galaxy S2 - I could reproduce this on Samsung RTL; also video recording fails and preview becomes corrupted on Galaxy S3 variant "SGH-I747-US2"); also see http://stackoverflow.com/questions/4244999/problem-with-video-recording-after-auto-focus-in-android
		camera.unlock();
	}

}
