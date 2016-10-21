package com.campusconnect.previewdemo.CameraController;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;

/** Provides support using Android 5's Camera 2 API
 *  android.hardware.camera2.*.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraController2 extends CameraController {
	private static final String TAG = "CameraController2";

	private Context context = null;
	private CameraDevice camera = null;
	private String cameraIdS = null;
	private CameraCharacteristics characteristics = null;
	private List<Integer> zoom_ratios = null;
	private int current_zoom_value = 0;
	private ErrorCallback preview_error_cb = null;
	private CameraCaptureSession captureSession = null;
	private CaptureRequest.Builder previewBuilder = null;
	private AutoFocusCallback autofocus_cb = null;
	private Object image_reader_lock = new Object(); // lock to make sure we only handle one image being available at a time
	private ImageReader imageReader = null;
	private boolean want_hdr = false;
	private boolean want_raw = false;
	private android.util.Size raw_size = null;
	private ImageReader imageReaderRaw = null;
	private OnRawImageAvailableListener onRawImageAvailableListener = null;
	private PictureCallback jpeg_cb = null;
	private PictureCallback raw_cb = null;
	private int n_burst = 0;
	private List<byte []> pending_burst_images = new Vector<byte []>();
	private DngCreator pending_dngCreator = null;
	private Image pending_image = null;
	private ErrorCallback take_picture_error_cb = null;
	private SurfaceTexture texture = null;
	private Surface surface_texture = null;
	private HandlerThread thread = null; 
	Handler handler = null;
	
	private int preview_width = 0;
	private int preview_height = 0;
	
	private int picture_width = 0;
	private int picture_height = 0;
	
	private static final int STATE_NORMAL = 0;
	private static final int STATE_WAITING_AUTOFOCUS = 1;
	private static final int STATE_WAITING_PRECAPTURE_START = 2;
	private static final int STATE_WAITING_PRECAPTURE_DONE = 3;
	private int state = STATE_NORMAL;
	private long precapture_started = -1; // set for STATE_WAITING_PRECAPTURE_START state
	private boolean ready_for_capture = false;

	private ContinuousFocusMoveCallback continuous_focus_move_callback = null;
	
	private MediaActionSound media_action_sound = new MediaActionSound();
	private boolean sounds_enabled = true;

	private boolean capture_result_has_iso = false;
	private int capture_result_iso = 0;
	private boolean capture_result_has_exposure_time = false;
	private long capture_result_exposure_time = 0;
	private boolean capture_result_has_frame_duration = false;
	private long capture_result_frame_duration = 0;
	
	private static enum RequestTag {
		CAPTURE
	}
	
	private class CameraSettings {
		// keys that we need to store, to pass to the stillBuilder, but doesn't need to be passed to previewBuilder (should set sensible defaults)
		private int rotation = 0;
		private Location location = null;
		private byte jpeg_quality = 90;

		// keys that we have passed to the previewBuilder, that we need to store to also pass to the stillBuilder (should set sensible defaults, or use a has_ boolean if we don't want to set a default)
		private int scene_mode = CameraMetadata.CONTROL_SCENE_MODE_DISABLED;
		private int color_effect = CameraMetadata.CONTROL_EFFECT_MODE_OFF;
		private int white_balance = CameraMetadata.CONTROL_AWB_MODE_AUTO;
		private String flash_value = "flash_off";
		private boolean has_iso = false;
		//private int ae_mode = CameraMetadata.CONTROL_AE_MODE_ON;
		//private int flash_mode = CameraMetadata.FLASH_MODE_OFF;
		private int iso = 0;
		private long exposure_time = EXPOSURE_TIME_DEFAULT;
		private Rect scalar_crop_region = null; // no need for has_scalar_crop_region, as we can set to null instead
		private boolean has_ae_exposure_compensation = false;
		private int ae_exposure_compensation = 0;
		private boolean has_af_mode = false;
		private int af_mode = CaptureRequest.CONTROL_AF_MODE_AUTO;
		private float focus_distance = 0.0f; // actual value passed to camera device (set to 0.0 if in infinity mode)
		private float focus_distance_manual = 0.0f; // saved setting when in manual mode
		private boolean ae_lock = false;
		private MeteringRectangle [] af_regions = null; // no need for has_scalar_crop_region, as we can set to null instead
		private MeteringRectangle [] ae_regions = null; // no need for has_scalar_crop_region, as we can set to null instead
		private boolean has_face_detect_mode = false;
		private int face_detect_mode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;
		private boolean video_stabilization = false;
		
		private int getExifOrientation() {
			int exif_orientation = ExifInterface.ORIENTATION_NORMAL;
			switch( (rotation + 360) % 360 ) {
				case 0:
					exif_orientation = ExifInterface.ORIENTATION_NORMAL;
					break;
				case 90:
					exif_orientation = isFrontFacing() ?
							ExifInterface.ORIENTATION_ROTATE_270 :
							ExifInterface.ORIENTATION_ROTATE_90;
					break;
				case 180:
					exif_orientation = ExifInterface.ORIENTATION_ROTATE_180;
					break;
				case 270:
					exif_orientation = isFrontFacing() ?
							ExifInterface.ORIENTATION_ROTATE_90 :
							ExifInterface.ORIENTATION_ROTATE_270;
					break;
				default:
					// leave exif_orientation unchanged
					break;
			}
			return exif_orientation;
		}

		private void setupBuilder(CaptureRequest.Builder builder, boolean is_still) {

			builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);

			setSceneMode(builder);
			setColorEffect(builder);
			setWhiteBalance(builder);
			setAEMode(builder, is_still);
			setCropRegion(builder);
			setExposureCompensation(builder);
			setFocusMode(builder);
			setFocusDistance(builder);
			setAutoExposureLock(builder);
			setAFRegions(builder);
			setAERegions(builder);
			setFaceDetectMode(builder);
			setRawMode(builder);
			setVideoStabilization(builder);

			if( is_still ) {
				if( location != null ) {
					builder.set(CaptureRequest.JPEG_GPS_LOCATION, location);
				}
				builder.set(CaptureRequest.JPEG_ORIENTATION, rotation);
				builder.set(CaptureRequest.JPEG_QUALITY, jpeg_quality);
			}
		}

		private boolean setSceneMode(CaptureRequest.Builder builder) {
			/*if( builder.get(CaptureRequest.CONTROL_SCENE_MODE) == null && scene_mode == CameraMetadata.CONTROL_SCENE_MODE_DISABLED ) {
				// can leave off
			}
			else*/ if( builder.get(CaptureRequest.CONTROL_SCENE_MODE) == null || builder.get(CaptureRequest.CONTROL_SCENE_MODE) != scene_mode ) {
				if( scene_mode == CameraMetadata.CONTROL_SCENE_MODE_DISABLED ) {
					builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
				}
				else {
					builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
				}
				builder.set(CaptureRequest.CONTROL_SCENE_MODE, scene_mode);
				return true;
			}
			return false;
		}

		private boolean setColorEffect(CaptureRequest.Builder builder) {
			/*if( builder.get(CaptureRequest.CONTROL_EFFECT_MODE) == null && color_effect == CameraMetadata.CONTROL_EFFECT_MODE_OFF ) {
				// can leave off
			}
			else*/ if( builder.get(CaptureRequest.CONTROL_EFFECT_MODE) == null || builder.get(CaptureRequest.CONTROL_EFFECT_MODE) != color_effect ) {
				builder.set(CaptureRequest.CONTROL_EFFECT_MODE, color_effect);
				return true;
			}
			return false;
		}

		private boolean setWhiteBalance(CaptureRequest.Builder builder) {
			/*if( builder.get(CaptureRequest.CONTROL_AWB_MODE) == null && white_balance == CameraMetadata.CONTROL_AWB_MODE_AUTO ) {
				// can leave off
			}
			else*/ if( builder.get(CaptureRequest.CONTROL_AWB_MODE) == null || builder.get(CaptureRequest.CONTROL_AWB_MODE) != white_balance ) {
				builder.set(CaptureRequest.CONTROL_AWB_MODE, white_balance);
				return true;
			}
			return false;
		}

		private boolean setAEMode(CaptureRequest.Builder builder, boolean is_still) {
			if( has_iso ) {
				builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
				builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
				builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure_time);
				// for now, flash is disabled when using manual iso - it seems to cause ISO level to jump to 100 on Nexus 6 when flash is turned on!
				// if we enable this ever, remember to still keep disabled for hdr (unless we've added support for flash with hdr by then)
				builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
			}
			else {
				// prefer to set flash via the ae mode (otherwise get even worse results), except for torch which we can't
				// for now, flash not supported for HDR
		    	if( CameraController2.this.want_hdr || flash_value.equals("flash_off") ) {
					builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
					builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
		    	}
		    	else if( flash_value.equals("flash_auto") ) {
					builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);
					builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
		    	}
		    	else if( flash_value.equals("flash_on") ) {
					builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
					builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
		    	}
		    	else if( flash_value.equals("flash_torch") ) {
					builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
					builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
		    	}
		    	else if( flash_value.equals("flash_red_eye") ) {
					builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
					builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
		    	}
			}
			return true;
		}

		private void setCropRegion(CaptureRequest.Builder builder) {
			if( scalar_crop_region != null ) {
				builder.set(CaptureRequest.SCALER_CROP_REGION, scalar_crop_region);
			}
		}

		private boolean setExposureCompensation(CaptureRequest.Builder builder) {
			if( !has_ae_exposure_compensation )
				return false;
			if( has_iso ) {
				return false;
			}
			if( builder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) == null || ae_exposure_compensation != builder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) ) {
				builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ae_exposure_compensation);
	        	return true;
			}
			return false;
		}

		private void setFocusMode(CaptureRequest.Builder builder) {
			if( has_af_mode ) {
				builder.set(CaptureRequest.CONTROL_AF_MODE, af_mode);
			}
		}
		
		private void setFocusDistance(CaptureRequest.Builder builder) {
			builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus_distance);
		}

		private void setAutoExposureLock(CaptureRequest.Builder builder) {
	    	builder.set(CaptureRequest.CONTROL_AE_LOCK, ae_lock);
		}

		private void setAFRegions(CaptureRequest.Builder builder) {
			if( af_regions != null && characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0 ) {
				builder.set(CaptureRequest.CONTROL_AF_REGIONS, af_regions);
			}
		}

		private void setAERegions(CaptureRequest.Builder builder) {
			if( ae_regions != null && characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0 ) {
				builder.set(CaptureRequest.CONTROL_AE_REGIONS, ae_regions);
			}
		}

		private void setFaceDetectMode(CaptureRequest.Builder builder) {
			if( has_face_detect_mode )
				builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, face_detect_mode);
		}
		
		private void setRawMode(CaptureRequest.Builder builder) {
			// DngCreator says "For best quality DNG files, it is strongly recommended that lens shading map output is enabled if supported"
			// docs also say "ON is always supported on devices with the RAW capability", so we don't check for STATISTICS_LENS_SHADING_MAP_MODE_ON being available
			if( want_raw ) {
				builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON);
			}
		}
		
		private void setVideoStabilization(CaptureRequest.Builder builder) {
			builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, video_stabilization ? CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON : CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
		}
		
		// n.b., if we add more methods, remember to update setupBuilder() above!
	}

	class OnRawImageAvailableListener implements ImageReader.OnImageAvailableListener {
		private CaptureResult capture_result = null;
		private Image image = null;
		
		void setCaptureResult(CaptureResult capture_result) {
			synchronized( image_reader_lock ) {
				/* synchronize, as we don't want to set the capture_result, at the same time that onImageAvailable() is called, as
				 * we'll end up calling processImage() both in onImageAvailable() and here.
				 */
				this.capture_result = capture_result;
				if( image != null ) {
					processImage();
				}
			}
		}
		
		void clear() {
			synchronized( image_reader_lock ) {
				// synchronize just to be safe?
				capture_result = null;
				image = null;
			}
		}
		
		private void processImage() {
			if( capture_result == null ) {
				return;
			}
			if( image == null ) {
				return;
			}
            DngCreator dngCreator = new DngCreator(characteristics, capture_result);
            // set fields
            dngCreator.setOrientation(camera_settings.getExifOrientation());
			if( camera_settings.location != null ) {
                dngCreator.setLocation(camera_settings.location);
			}
			
			pending_dngCreator = dngCreator;
			pending_image = image;
		}

		@Override
		public void onImageAvailable(ImageReader reader) {
			if( raw_cb == null ) {
				return;
			}
			synchronized( image_reader_lock ) {
				// see comment above in setCaptureResult() for why we sychonize
				image = reader.acquireNextImage();
				processImage();
			}
		}
	}
	
	private CameraSettings camera_settings = new CameraSettings();
	private boolean push_repeating_request_when_torch_off = false;
	private CaptureRequest push_repeating_request_when_torch_off_id = null;
	/*private boolean push_set_ae_lock = false;
	private CaptureRequest push_set_ae_lock_id = null;*/

	public CameraController2(Context context, int cameraId, ErrorCallback preview_error_cb) throws CameraControllerException {
		super(cameraId);

		this.context = context;
		this.preview_error_cb = preview_error_cb;

		thread = new HandlerThread("CameraBackground"); 
		thread.start(); 
		handler = new Handler(thread.getLooper());

		final CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);

		class MyStateCallback extends CameraDevice.StateCallback {
			boolean callback_done = false; // must sychronize on this and notifyAll when setting to true
			boolean first_callback = true; // Google Camera says we may get multiple callbacks, but only the first indicates the status of the camera opening operation
			@Override
			public void onOpened(CameraDevice cam) {
				if( first_callback ) {
					first_callback = false;

				    try {
				    	// we should be able to get characteristics at any time, but Google Camera only does so when camera opened - so do so similarly to be safe
						characteristics = manager.getCameraCharacteristics(cameraIdS);

						CameraController2.this.camera = cam;

						// note, this won't start the preview yet, but we create the previewBuilder in order to start setting camera parameters
						createPreviewRequest();
					}
				    catch(CameraAccessException e) {
						e.printStackTrace();
						// don't throw CameraControllerException here - instead error is handled by setting callback_done to callback_done, and the fact that camera will still be null
					}

				    synchronized( this ) {
				    	callback_done = true;
				    	this.notifyAll();
				    }
				}
			}

			@Override
			public void onClosed(CameraDevice cam) {
				// caller should ensure camera variables are set to null
				if( first_callback ) {
					first_callback = false;
				}
			}

			@Override
			public void onDisconnected(CameraDevice cam) {
				if( first_callback ) {
					first_callback = false;
					// must call close() if disconnected before camera was opened
					// need to set the camera to null first, as closing the camera may take some time, and we don't want any other operations to continue (if called from main thread)
					CameraController2.this.camera = null;

					cam.close();
				    synchronized( this ) {
				    	callback_done = true;
				    	this.notifyAll();
				    }
				}
			}

			@Override
			public void onError(CameraDevice cam, int error) {
				if( first_callback ) {
					first_callback = false;
				}
				// need to set the camera to null first, as closing the camera may take some time, and we don't want any other operations to continue (if called from main thread)
				CameraController2.this.camera = null;
				cam.close();
			    synchronized( this ) {
			    	callback_done = true;
			    	this.notifyAll();
			    }
			}
		};
		MyStateCallback myStateCallback = new MyStateCallback();

		try {
			this.cameraIdS = manager.getCameraIdList()[cameraId];
			manager.openCamera(cameraIdS, myStateCallback, handler);
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
			throw new CameraControllerException();
		}
		catch(UnsupportedOperationException e) {
			// Google Camera catches UnsupportedOperationException
			e.printStackTrace();
			throw new CameraControllerException();
		}
		catch(SecurityException e) {
			// Google Camera catches SecurityException
			e.printStackTrace();
			throw new CameraControllerException();
		}
		// need to wait until camera is opened
		synchronized( myStateCallback ) {
			while( !myStateCallback.callback_done ) {
				try {
					// release the myStateCallback lock, and wait until myStateCallback calls notifyAll()
					myStateCallback.wait();
				}
				catch(InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		if( camera == null ) {
			throw new CameraControllerException();
		}

		/*CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);
	    StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
	    android.util.Size [] camera_picture_sizes = configs.getOutputSizes(ImageFormat.JPEG);
		imageReader = ImageReader.newInstance(camera_picture_sizes[0].getWidth(), , ImageFormat.JPEG, 2);*/
		
		// preload sounds to reduce latency - important so that START_VIDEO_RECORDING sound doesn't play after video has started (which means it'll be heard in the resultant video)
		media_action_sound.load(MediaActionSound.START_VIDEO_RECORDING);
		media_action_sound.load(MediaActionSound.STOP_VIDEO_RECORDING);
		media_action_sound.load(MediaActionSound.SHUTTER_CLICK);
	}

	@Override
	public void release() {
		if( thread != null ) {
			thread.quitSafely();
			try {
				thread.join();
				thread = null;
				handler = null;
			}
			catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
		if( captureSession != null ) {
			captureSession.close();
			captureSession = null;
		}
		previewBuilder = null;
		if( camera != null ) {
			camera.close();
			camera = null;
		}
		closePictureImageReader();
	}
	
	private void closePictureImageReader() {
		if( imageReader != null ) {
			imageReader.close();
			imageReader = null;
		}
		if( imageReaderRaw != null ) {
			imageReaderRaw.close();
			imageReaderRaw = null;
			onRawImageAvailableListener = null;
		}
	}

	private List<String> convertFocusModesToValues(int [] supported_focus_modes_arr, float minimum_focus_distance) {
		if( supported_focus_modes_arr.length == 0 )
			return null;
	    List<Integer> supported_focus_modes = new ArrayList<Integer>();
	    for(int i=0;i<supported_focus_modes_arr.length;i++)
	    	supported_focus_modes.add(supported_focus_modes_arr[i]);
	    List<String> output_modes = new Vector<String>();
		// also resort as well as converting
		if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) ) {
			output_modes.add("focus_mode_auto");
		}
		if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_MACRO) ) {
			output_modes.add("focus_mode_macro");
		}
		if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) ) {
			output_modes.add("focus_mode_locked");
		}
		if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_OFF) ) {
			output_modes.add("focus_mode_infinity");
			if( minimum_focus_distance > 0.0f ) {
				output_modes.add("focus_mode_manual2");
			}
		}
		if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_EDOF) ) {
			output_modes.add("focus_mode_edof");
		}
		if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) ) {
			output_modes.add("focus_mode_continuous_picture");
		}
		if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) ) {
			output_modes.add("focus_mode_continuous_video");
		}
		return output_modes;
	}

	public String getAPI() {
		return "Camera2 (Android L)";
	}
	
	@Override
	public CameraFeatures getCameraFeatures() {
		CameraFeatures camera_features = new CameraFeatures();

		float max_zoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
		camera_features.is_zoom_supported = max_zoom > 0.0f;
		if( camera_features.is_zoom_supported ) {
			// set 20 steps per 2x factor
			final int steps_per_2x_factor = 20;
			//final double scale_factor = Math.pow(2.0, 1.0/(double)steps_per_2x_factor);
			int n_steps =(int)( (steps_per_2x_factor * Math.log(max_zoom + 1.0e-11)) / Math.log(2.0));
			final double scale_factor = Math.pow(max_zoom, 1.0/(double)n_steps);
			camera_features.zoom_ratios = new ArrayList<Integer>();
			camera_features.zoom_ratios.add(100);
			double zoom = 1.0;
			for(int i=0;i<n_steps-1;i++) {
				zoom *= scale_factor;
				camera_features.zoom_ratios.add((int)(zoom*100));
			}
			camera_features.zoom_ratios.add((int)(max_zoom*100));
			camera_features.max_zoom = camera_features.zoom_ratios.size()-1;
			this.zoom_ratios = camera_features.zoom_ratios;
		}
		else {
			this.zoom_ratios = null;
		}

		int [] face_modes = characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
		camera_features.supports_face_detection = false;
		for(int i=0;i<face_modes.length;i++) {
			// Although we currently only make use of the "SIMPLE" features, some devices (e.g., Nexus 6) support FULL and not SIMPLE.
			// We don't support SIMPLE yet, as I don't have any devices to test this.
			if( face_modes[i] == CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_FULL ) {
				camera_features.supports_face_detection = true;
			}
		}
		if( camera_features.supports_face_detection ) {
			int face_count = characteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
			if( face_count <= 0 ) {
				camera_features.supports_face_detection = false;
			}
		}

		int [] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES); 
		boolean capabilities_raw = false;
		for(int capability : capabilities) {
			if( capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW ) {
				capabilities_raw = true;
			}
		}

		StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

	    android.util.Size [] camera_picture_sizes = configs.getOutputSizes(ImageFormat.JPEG);
		camera_features.picture_sizes = new ArrayList<Size>();
		for(android.util.Size camera_size : camera_picture_sizes) {
			camera_features.picture_sizes.add(new CameraController.Size(camera_size.getWidth(), camera_size.getHeight()));
		}

    	raw_size = null;
    	if( capabilities_raw ) {
		    android.util.Size [] raw_camera_picture_sizes = configs.getOutputSizes(ImageFormat.RAW_SENSOR);
		    if( raw_camera_picture_sizes == null ) {
				want_raw = false; // just in case it got set to true somehow
		    }
		    else {
				for(int i=0;i<raw_camera_picture_sizes.length;i++) {
					android.util.Size size = raw_camera_picture_sizes[i];
		        	if( raw_size == null || size.getWidth()*size.getHeight() > raw_size.getWidth()*raw_size.getHeight() ) {
		        		raw_size = size;
		        	}
		        }
				if( raw_size == null ) {
					want_raw = false; // just in case it got set to true somehow
				}
				else {
					camera_features.supports_raw = true;				
				}
			}
    	}
    	else {
			want_raw = false; // just in case it got set to true somehow
    	}
		
	    android.util.Size [] camera_video_sizes = configs.getOutputSizes(MediaRecorder.class);
		camera_features.video_sizes = new ArrayList<Size>();
		for(android.util.Size camera_size : camera_video_sizes) {
			if( camera_size.getWidth() > 4096 || camera_size.getHeight() > 2160 )
				continue; // Nexus 6 returns these, even though not supported?!
			camera_features.video_sizes.add(new CameraController.Size(camera_size.getWidth(), camera_size.getHeight()));
		}

		android.util.Size [] camera_preview_sizes = configs.getOutputSizes(SurfaceTexture.class);
		camera_features.preview_sizes = new ArrayList<Size>();
        Point display_size = new Point();
		Activity activity = (Activity)context;
        {
            Display display = activity.getWindowManager().getDefaultDisplay();
            display.getRealSize(display_size);
        }
		for(android.util.Size camera_size : camera_preview_sizes) {
			if( camera_size.getWidth() > display_size.x || camera_size.getHeight() > display_size.y ) {
				// Nexus 6 returns these, even though not supported?! (get green corruption lines if we allow these)
				// Google Camera filters anything larger than height 1080, with a todo saying to use device's measurements
				continue;
			}
			camera_features.preview_sizes.add(new CameraController.Size(camera_size.getWidth(), camera_size.getHeight()));
		}
		
		if( characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ) {
			camera_features.supported_flash_values = new ArrayList<String>();
			camera_features.supported_flash_values.add("flash_off");
			camera_features.supported_flash_values.add("flash_auto");
			camera_features.supported_flash_values.add("flash_on");
			camera_features.supported_flash_values.add("flash_torch");
			camera_features.supported_flash_values.add("flash_red_eye");
		}

		camera_features.minimum_focus_distance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
		int [] supported_focus_modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES); // Android format
		camera_features.supported_focus_values = convertFocusModesToValues(supported_focus_modes, camera_features.minimum_focus_distance); // convert to our format (also resorts)
		camera_features.max_num_focus_areas = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);

		camera_features.is_exposure_lock_supported = true;
		
        camera_features.is_video_stabilization_supported = true;

		Range<Integer> iso_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
		if( iso_range != null ) {
			camera_features.supports_iso_range = true;
			camera_features.min_iso = iso_range.getLower();
			camera_features.max_iso = iso_range.getUpper();
			// we only expose exposure_time if iso_range is supported
			Range<Long> exposure_time_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
			if( exposure_time_range != null ) {
				camera_features.supports_exposure_time = true;
				camera_features.supports_hdr = true;
				camera_features.min_exposure_time = exposure_time_range.getLower();
				camera_features.max_exposure_time = exposure_time_range.getUpper();
			}
		}

		Range<Integer> exposure_range = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
		camera_features.min_exposure = exposure_range.getLower();
		camera_features.max_exposure = exposure_range.getUpper();
		camera_features.exposure_step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue();

		camera_features.can_disable_shutter_sound = true;

		return camera_features;
	}

	@Override
	public void setSceneMode(String value) {
		// we convert to/from strings to be compatible with original Android Camera API
		camera_settings.scene_mode = CameraMetadata.CONTROL_SCENE_MODE_DISABLED;
		if( camera_settings.setSceneMode(previewBuilder) ) {
			try {
				setRepeatingRequest();
			}
			catch(CameraAccessException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public String getSceneMode() {
		if( previewBuilder.get(CaptureRequest.CONTROL_SCENE_MODE) == null )
			return null;
		return "auto";
	}

	@Override
	public void setColorEffect(String value) {

		camera_settings.color_effect = CameraMetadata.CONTROL_EFFECT_MODE_OFF;
		if( camera_settings.setColorEffect(previewBuilder) ) {
			try {
				setRepeatingRequest();
			}
			catch(CameraAccessException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void setWhiteBalance(String value) {

		camera_settings.white_balance = CameraMetadata.CONTROL_AWB_MODE_AUTO;
		if( camera_settings.setWhiteBalance(previewBuilder) ) {
			try {
				setRepeatingRequest();
			}
			catch(CameraAccessException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void setISO(String value) {

		try {
				camera_settings.iso = 0;
				if( camera_settings.setAEMode(previewBuilder, false) ) {
			    	setRepeatingRequest();
				}
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getISOKey() {
		return "";
	}

	@Override
	public int getISO() {
		return camera_settings.iso;
	}
	
	@Override
	// Returns whether ISO was modified
	// N.B., use setISO(String) to switch between auto and manual mode
	public boolean setISO(int iso) {
		if( camera_settings.iso == iso ) {
			return false;
		}
		try {
			camera_settings.iso = iso;
			if( camera_settings.setAEMode(previewBuilder, false) ) {
		    	setRepeatingRequest();
			}
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
		} 
		return true;
	}

	@Override
	// Returns whether exposure time was modified
	// N.B., use setISO(String) to switch between auto and manual mode
	public boolean setExposureTime(long exposure_time) {
		if( camera_settings.exposure_time == exposure_time ) {
			return false;
		}
		try {
			camera_settings.exposure_time = exposure_time;
			if( camera_settings.setAEMode(previewBuilder, false) ) {
		    	setRepeatingRequest();
			}
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
		} 
		return true;
	}

	@Override
	public Size getPictureSize() {
		Size size = new Size(picture_width, picture_height);
		return size;
	}

	@Override
	public void setPictureSize(int width, int height) {
		if( camera == null ) {
			return;
		}
		if( captureSession != null ) {
			// can only call this when captureSession not created - as the surface of the imageReader we create has to match the surface we pass to the captureSession
			throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
		}
		this.picture_width = width;
		this.picture_height = height;
	}

	private void createPictureImageReader() {
		if( captureSession != null ) {
			// can only call this when captureSession not created - as the surface of the imageReader we create has to match the surface we pass to the captureSession
			throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
		}
		closePictureImageReader();
		if( picture_width == 0 || picture_height == 0 ) {
			throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
		}
		imageReader = ImageReader.newInstance(picture_width, picture_height, ImageFormat.JPEG, 2);
		imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
			@Override
			public void onImageAvailable(ImageReader reader) {
				if( jpeg_cb == null ) {
					return;
				}
				synchronized( image_reader_lock ) {
					/* Whilst in theory the two setOnImageAvailableListener methods (for JPEG and RAW) seem to be called separately, I don't know if this is always true;
					 * also, we may process the RAW image when the capture result is available (see
					 * OnRawImageAvailableListener.setCaptureResult()), which may be in a separte thread.
					 */
					Image image = reader.acquireNextImage();
		            ByteBuffer buffer = image.getPlanes()[0].getBuffer(); 
		            byte [] bytes = new byte[buffer.remaining()];
		            buffer.get(bytes);
		            image.close();

			            // need to set jpeg_cb etc to null before calling onCompleted, as that may reenter CameraController to take another photo (if in burst mode) - see testTakePhotoBurst()
			            PictureCallback cb = jpeg_cb;
			            jpeg_cb = null;
			            cb.onPictureTaken(bytes);
			            if( raw_cb == null ) {
							cb.onCompleted();
			            }
			            else if( pending_dngCreator != null ) {
							cb.onCompleted();
			            }

				}
			}
		}, null);
	}
	
	private void clearPending() {
		pending_burst_images.clear();
		pending_dngCreator = null;
		pending_image = null;
		if( onRawImageAvailableListener != null ) {
			onRawImageAvailableListener.clear();
		}
		n_burst = 0;
	}

	@Override
	public void setPreviewSize(int width, int height) {
		/*if( texture != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "set size of preview texture");
			texture.setDefaultBufferSize(width, height);
		}*/
		preview_width = width;
		preview_height = height;
		/*if( previewImageReader != null ) {
			previewImageReader.close();
		}
		previewImageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2); 
		*/
	}

	@Override
	public void setJpegQuality(int quality) {
		if( quality < 0 || quality > 100 ) {
			throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
		}
		this.camera_settings.jpeg_quality = (byte)quality;
	}

	@Override
	public int getZoom() {
		return this.current_zoom_value;
	}

	@Override
	public void setZoom(int value) {
		if( zoom_ratios == null ) {
			return;
		}
		if( value < 0 || value > zoom_ratios.size() ) {
			throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
		}
		float zoom = zoom_ratios.get(value)/100.0f;
		Rect sensor_rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
		int left = sensor_rect.width()/2;
		int right = left;
		int top = sensor_rect.height()/2;
		int bottom = top;
		int hwidth = (int)(sensor_rect.width() / (2.0*zoom));
		int hheight = (int)(sensor_rect.height() / (2.0*zoom));
		left -= hwidth;
		right += hwidth;
		top -= hheight;
		bottom += hheight;
		camera_settings.scalar_crop_region = new Rect(left, top, right, bottom);
		camera_settings.setCropRegion(previewBuilder);
    	this.current_zoom_value = value;
    	try {
    		setRepeatingRequest();
    	}
		catch(CameraAccessException e) {
			e.printStackTrace();
		} 
	}
	
	@Override
	public int getExposureCompensation() {
		if( previewBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) == null )
			return 0;
		return previewBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION);
	}

	@Override
	// Returns whether exposure was modified
	public boolean setExposureCompensation(int new_exposure) {
		camera_settings.has_ae_exposure_compensation = true;
		camera_settings.ae_exposure_compensation = new_exposure;
		if( camera_settings.setExposureCompensation(previewBuilder) ) {
			try {
				setRepeatingRequest();
			}
			catch(CameraAccessException e) {
				e.printStackTrace();
			} 
        	return true;
		}
		return false;
	}
	
	@Override
	public void setPreviewFpsRange(int min, int max) {
		// TODO Auto-generated method stub

	}

	@Override
	public List<int[]> getSupportedPreviewFpsRange() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setFocusValue(String focus_value) {
		int focus_mode = CaptureRequest.CONTROL_AF_MODE_AUTO;
    	if( focus_value.equals("focus_mode_auto") || focus_value.equals("focus_mode_locked") ) {
    		focus_mode = CaptureRequest.CONTROL_AF_MODE_AUTO;
    	}
    	else if( focus_value.equals("focus_mode_infinity") ) {
    		focus_mode = CaptureRequest.CONTROL_AF_MODE_OFF;
        	camera_settings.focus_distance = 0.0f;
    	}
    	else if( focus_value.equals("focus_mode_manual2") ) {
    		focus_mode = CaptureRequest.CONTROL_AF_MODE_OFF;
        	camera_settings.focus_distance = camera_settings.focus_distance_manual;
    	}
    	else if( focus_value.equals("focus_mode_macro") ) {
    		focus_mode = CaptureRequest.CONTROL_AF_MODE_MACRO;
    	}
    	else if( focus_value.equals("focus_mode_edof") ) {
    		focus_mode = CaptureRequest.CONTROL_AF_MODE_EDOF;
    	}
    	else if( focus_value.equals("focus_mode_continuous_picture") ) {
    		focus_mode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    	}
    	else if( focus_value.equals("focus_mode_continuous_video") ) {
    		focus_mode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
    	}
    	else {
    		return;
    	}
    	camera_settings.has_af_mode = true;
    	camera_settings.af_mode = focus_mode;
    	camera_settings.setFocusMode(previewBuilder);
    	camera_settings.setFocusDistance(previewBuilder); // also need to set distance, in case changed between infinity, manual or other modes
    	try {
    		setRepeatingRequest();
    	}
		catch(CameraAccessException e) {
			e.printStackTrace();
		} 
	}

	private String convertFocusModeToValue(int focus_mode) {
		String focus_value = "";
		if( focus_mode == CaptureRequest.CONTROL_AF_MODE_AUTO ) {
    		focus_value = "focus_mode_auto";
    	}
		else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_MACRO ) {
    		focus_value = "focus_mode_macro";
    	}
		else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_EDOF ) {
    		focus_value = "focus_mode_edof";
    	}
		else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ) {
    		focus_value = "focus_mode_continuous_picture";
    	}
		else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO ) {
    		focus_value = "focus_mode_continuous_video";
    	}
		else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_OFF ) {
    		focus_value = "focus_mode_manual2"; // n.b., could be infinity
		}
    	return focus_value;
	}
	
	@Override
	public String getFocusValue() {
		int focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE) != null ?
				previewBuilder.get(CaptureRequest.CONTROL_AF_MODE) : CaptureRequest.CONTROL_AF_MODE_AUTO;
		return convertFocusModeToValue(focus_mode);
	}

	@Override
	public boolean setFocusDistance(float focus_distance) {
		if( camera_settings.focus_distance == focus_distance ) {
			return false;
		}
    	camera_settings.focus_distance = focus_distance;
    	camera_settings.focus_distance_manual = focus_distance;
    	camera_settings.setFocusDistance(previewBuilder);
    	try {
    		setRepeatingRequest();
    	}
		catch(CameraAccessException e) {
			e.printStackTrace();
		} 
    	return true;
	}

	@Override
	public void setFlashValue(String flash_value) {
		if( !characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ) {
			return;
		}
		else if( camera_settings.flash_value.equals(flash_value) ) {
			return;
		}

		try {
			if( camera_settings.flash_value.equals("flash_torch") && !flash_value.equals("flash_off") ) {
				// hack - if switching to something other than flash_off, we first need to turn torch off, otherwise torch remains on (at least on Nexus 6)
				camera_settings.flash_value = "flash_off";
				camera_settings.setAEMode(previewBuilder, false);
				CaptureRequest request = previewBuilder.build();
	
				// need to wait until torch actually turned off
		    	camera_settings.flash_value = flash_value;
				camera_settings.setAEMode(previewBuilder, false);
				push_repeating_request_when_torch_off = true;
				push_repeating_request_when_torch_off_id = request;
	
				setRepeatingRequest(request);
			}
			else {
				camera_settings.flash_value = flash_value;
				if( camera_settings.setAEMode(previewBuilder, false) ) {
			    	setRepeatingRequest();
				}
			}
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
		} 
	}

	@Override
	public String getFlashValue() {
		// returns "" if flash isn't supported
		if( !characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ) {
			return "";
		}
		return camera_settings.flash_value;
	}

	@Override
	public void setAutoExposureLock(boolean enabled) {
		camera_settings.ae_lock = enabled;
		camera_settings.setAutoExposureLock(previewBuilder);
		try {
			setRepeatingRequest();
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
		} 
	}

	@Override
	public void setRotation(int rotation) {
		this.camera_settings.rotation = rotation;
	}

	@Override
	public void setLocationInfo(Location location) {
		this.camera_settings.location = location;
	}

	@Override
	public void removeLocationInfo() {
		this.camera_settings.location = null;
	}

	@Override
	public void enableShutterSound(boolean enabled) {
		this.sounds_enabled = enabled;
	}

	/** Returns the viewable rect - this is crop region if available.
	 *  We need this as callers will pass in (or expect returned) CameraController.Area values that
	 *  are relative to the current view (i.e., taking zoom into account) (the old Camera API in
	 *  CameraController1 always works in terms of the current view, whilst Camera2 works in terms
	 *  of the full view always). Similarly for the rect field in CameraController.Face.
	 */
	private Rect getViewableRect() {
		if( previewBuilder != null ) {
			Rect crop_rect = previewBuilder.get(CaptureRequest.SCALER_CROP_REGION);
			if( crop_rect != null ) {
				return crop_rect;
			}
		}
		Rect sensor_rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
		sensor_rect.right -= sensor_rect.left;
		sensor_rect.left = 0;
		sensor_rect.bottom -= sensor_rect.top;
		sensor_rect.top = 0;
		return sensor_rect;
	}
	
	private Rect convertRectToCamera2(Rect crop_rect, Rect rect) {
		// CameraController.Area is always [-1000, -1000] to [1000, 1000] for the viewable region
		// but for CameraController2, we must convert to be relative to the crop region
		double left_f = (rect.left+1000)/2000.0;
		double top_f = (rect.top+1000)/2000.0;
		double right_f = (rect.right+1000)/2000.0;
		double bottom_f = (rect.bottom+1000)/2000.0;
		int left = (int)(crop_rect.left + left_f * (crop_rect.width()-1));
		int right = (int)(crop_rect.left + right_f * (crop_rect.width()-1));
		int top = (int)(crop_rect.top + top_f * (crop_rect.height()-1));
		int bottom = (int)(crop_rect.top + bottom_f * (crop_rect.height()-1));
		left = Math.max(left, crop_rect.left);
		right = Math.max(right, crop_rect.left);
		top = Math.max(top, crop_rect.top);
		bottom = Math.max(bottom, crop_rect.top);
		left = Math.min(left, crop_rect.right);
		right = Math.min(right, crop_rect.right);
		top = Math.min(top, crop_rect.bottom);
		bottom = Math.min(bottom, crop_rect.bottom);

		Rect camera2_rect = new Rect(left, top, right, bottom);
		return camera2_rect;
	}

	private MeteringRectangle convertAreaToMeteringRectangle(Rect sensor_rect, Area area) {
		Rect camera2_rect = convertRectToCamera2(sensor_rect, area.rect);
		MeteringRectangle metering_rectangle = new MeteringRectangle(camera2_rect, area.weight);
		return metering_rectangle;
	}

	private Rect convertRectFromCamera2(Rect crop_rect, Rect camera2_rect) {
		// inverse of convertRectToCamera2()
		double left_f = (camera2_rect.left-crop_rect.left)/(double)(crop_rect.width()-1);
		double top_f = (camera2_rect.top-crop_rect.top)/(double)(crop_rect.height()-1);
		double right_f = (camera2_rect.right-crop_rect.left)/(double)(crop_rect.width()-1);
		double bottom_f = (camera2_rect.bottom-crop_rect.top)/(double)(crop_rect.height()-1);
		int left = (int)(left_f * 2000) - 1000;
		int right = (int)(right_f * 2000) - 1000;
		int top = (int)(top_f * 2000) - 1000;
		int bottom = (int)(bottom_f * 2000) - 1000;

		left = Math.max(left, -1000);
		right = Math.max(right, -1000);
		top = Math.max(top, -1000);
		bottom = Math.max(bottom, -1000);
		left = Math.min(left, 1000);
		right = Math.min(right, 1000);
		top = Math.min(top, 1000);
		bottom = Math.min(bottom, 1000);

		Rect rect = new Rect(left, top, right, bottom);
		return rect;
	}

	private Area convertMeteringRectangleToArea(Rect sensor_rect, MeteringRectangle metering_rectangle) {
		Rect area_rect = convertRectFromCamera2(sensor_rect, metering_rectangle.getRect());
		Area area = new Area(area_rect, metering_rectangle.getMeteringWeight());
		return area;
	}

	@Override
	public boolean setFocusAndMeteringArea(List<Area> areas) {
		Rect sensor_rect = getViewableRect();
		boolean has_focus = false;
		boolean has_metering = false;
		if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0 ) {
			has_focus = true;
			camera_settings.af_regions = new MeteringRectangle[areas.size()];
			int i = 0;
			for(CameraController.Area area : areas) {
				camera_settings.af_regions[i++] = convertAreaToMeteringRectangle(sensor_rect, area);
			}
			camera_settings.setAFRegions(previewBuilder);
		}
		else
			camera_settings.af_regions = null;
		if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0 ) {
			has_metering = true;
			camera_settings.ae_regions = new MeteringRectangle[areas.size()];
			int i = 0;
			for(CameraController.Area area : areas) {
				camera_settings.ae_regions[i++] = convertAreaToMeteringRectangle(sensor_rect, area);
			}
			camera_settings.setAERegions(previewBuilder);
		}
		else
			camera_settings.ae_regions = null;
		if( has_focus || has_metering ) {
			try {
				setRepeatingRequest();
			}
			catch(CameraAccessException e) {
				e.printStackTrace();
			} 
		}
		return has_focus;
	}
	
	@Override
	public void clearFocusAndMetering() {
		Rect sensor_rect = getViewableRect();
		boolean has_focus = false;
		boolean has_metering = false;
		if( sensor_rect.width() <= 0 || sensor_rect.height() <= 0 ) {
			// had a crash on Google Play due to creating a MeteringRectangle with -ve width/height ?!
			camera_settings.af_regions = null;
			camera_settings.ae_regions = null;
		}
		else {
			if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0 ) {
				has_focus = true;
				camera_settings.af_regions = new MeteringRectangle[1];
				camera_settings.af_regions[0] = new MeteringRectangle(0, 0, sensor_rect.width()-1, sensor_rect.height()-1, 0);
				camera_settings.setAFRegions(previewBuilder);
			}
			else
				camera_settings.af_regions = null;
			if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0 ) {
				has_metering = true;
				camera_settings.ae_regions = new MeteringRectangle[1];
				camera_settings.ae_regions[0] = new MeteringRectangle(0, 0, sensor_rect.width()-1, sensor_rect.height()-1, 0);
				camera_settings.setAERegions(previewBuilder);
			}
			else
				camera_settings.ae_regions = null;
		}
		if( has_focus || has_metering ) {
			try {
				setRepeatingRequest();
			}
			catch(CameraAccessException e) {
				e.printStackTrace();
			} 
		}
	}

	@Override
	public boolean supportsAutoFocus() {
		if( previewBuilder.get(CaptureRequest.CONTROL_AF_MODE) == null )
			return true;
		int focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
		if( focus_mode == CaptureRequest.CONTROL_AF_MODE_AUTO || focus_mode == CaptureRequest.CONTROL_AF_MODE_MACRO )
			return true;
		return false;
	}

	@Override
	public boolean focusIsContinuous() {
		if( previewBuilder == null || previewBuilder.get(CaptureRequest.CONTROL_AF_MODE) == null )
			return false;
		int focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
		if( focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE || focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO )
			return true;
		return false;
	}

	@Override
	public void setPreviewDisplay(SurfaceHolder holder) throws CameraControllerException {
		throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
	}

	@Override
	public void setPreviewTexture(SurfaceTexture texture) throws CameraControllerException {
		if( this.texture != null ) {
			throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
		}
		this.texture = texture;
	}

	private void setRepeatingRequest() throws CameraAccessException {
		setRepeatingRequest(previewBuilder.build());
	}

	private void setRepeatingRequest(CaptureRequest request) throws CameraAccessException {
		if( camera == null || captureSession == null ) {
			return;
		}
		captureSession.setRepeatingRequest(request, previewCaptureCallback, handler);
	}

	private void capture() throws CameraAccessException {
		capture(previewBuilder.build());
	}

	private void capture(CaptureRequest request) throws CameraAccessException {
		if( camera == null || captureSession == null ) {
			return;
		}
		captureSession.capture(request, previewCaptureCallback, handler);
	}
	
	private void createPreviewRequest() {
		if( camera == null  ) {
			return;
		}
		try {
			previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			previewBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW);
			camera_settings.setupBuilder(previewBuilder, false);
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
		} 
	}

	private Surface getPreviewSurface() {
		return surface_texture;
	}

	private void createCaptureSession(final MediaRecorder video_recorder) throws CameraControllerException {
		
		if( previewBuilder == null ) {
			throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
		}
		if( camera == null ) {
			return;
		}

		if( captureSession != null ) {
			captureSession.close();
			captureSession = null;
		}

		try {
			captureSession = null;

			if( video_recorder != null ) {
				closePictureImageReader();
			}
			else {
				// in some cases need to recreate picture imageReader and the texture default buffer size (e.g., see test testTakePhotoPreviewPaused())
				createPictureImageReader();
			}
			if( texture != null ) {
				// need to set the texture size
				if( preview_width == 0 || preview_height == 0 ) {
					throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
				}
				texture.setDefaultBufferSize(preview_width, preview_height);
				// also need to create a new surface for the texture, in case the size has changed - but make sure we remove the old one first!
				if( surface_texture != null ) {
					previewBuilder.removeTarget(surface_texture);
				}
				this.surface_texture = new Surface(texture);
			}
			if( video_recorder != null ) {
			}
			/*if( MyDebug.LOG )
			Log.d(TAG, "preview size: " + previewImageReader.getWidth() + " x " + previewImageReader.getHeight());*/

			class MyStateCallback extends CameraCaptureSession.StateCallback {
				boolean callback_done = false; // must sychronize on this and notifyAll when setting to true
				@Override
				public void onConfigured(CameraCaptureSession session) {
					if( camera == null ) {
					    synchronized( this ) {
					    	callback_done = true;
					    	this.notifyAll();
					    }
						return;
					}
					captureSession = session;
		        	Surface surface = getPreviewSurface();
	        		previewBuilder.addTarget(surface);
	        		if( video_recorder != null )
	        			previewBuilder.addTarget(video_recorder.getSurface());
	        		try {
	        			setRepeatingRequest();
	        		}
					catch(CameraAccessException e) {
						e.printStackTrace();
						preview_error_cb.onError();
					} 
				    synchronized( this ) {
				    	callback_done = true;
				    	this.notifyAll();
				    }
				}

				@Override
				public void onConfigureFailed(CameraCaptureSession session) {
				    synchronized( this ) {
				    	callback_done = true;
				    	this.notifyAll();
				    }
					// don't throw CameraControllerException here, as won't be caught - instead we throw CameraControllerException below
				}
			}
			MyStateCallback myStateCallback = new MyStateCallback();

        	Surface preview_surface = getPreviewSurface();
        	List<Surface> surfaces = null;
        	if( video_recorder != null ) {
        		surfaces = Arrays.asList(preview_surface, video_recorder.getSurface());
        	}
    		else if( imageReaderRaw != null ) {
        		surfaces = Arrays.asList(preview_surface, imageReader.getSurface(), imageReaderRaw.getSurface());
    		}
    		else {
        		surfaces = Arrays.asList(preview_surface, imageReader.getSurface());
    		}
			camera.createCaptureSession(surfaces,
				myStateCallback,
		 		handler);
			synchronized( myStateCallback ) {
				while( !myStateCallback.callback_done ) {
					try {
						// release the myStateCallback lock, and wait until myStateCallback calls notifyAll()
						myStateCallback.wait();
					}
					catch(InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			if( captureSession == null ) {
				throw new CameraControllerException();
			}
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
			throw new CameraControllerException();
		}
	}

	@Override
	public void startPreview() throws CameraControllerException {
		if( captureSession != null ) {
			try {
				setRepeatingRequest();
			}
			catch(CameraAccessException e) {
				e.printStackTrace();
				// do via CameraControllerException instead of preview_error_cb, so caller immediately knows preview has failed
				throw new CameraControllerException();
			} 
			return;
		}
		createCaptureSession(null);
	}

	@Override
	public void stopPreview() {
		if( camera == null || captureSession == null ) {
			return;
		}
		try {
			captureSession.stopRepeating();
			// although stopRepeating() alone will pause the preview, seems better to close captureSession altogether - this allows the app to make changes such as changing the picture size
			captureSession.close();
			captureSession = null;
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void autoFocus(final AutoFocusCallback cb) {
		if( camera == null || captureSession == null ) {
			// should call the callback, so the application isn't left waiting (e.g., when we autofocus before trying to take a photo)
			cb.onAutoFocus(false);
			return;
		}
		Integer focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
		if( focus_mode == null ) {
			// we preserve the old Camera API where calling autoFocus() on a device without autofocus immediately calls the callback
			// (unclear if Open Camera needs this, but just to be safe and consistent between camera APIs)
			cb.onAutoFocus(true);
			return;
		}
		else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ) {
			/* In the old Camera API, doing an autofocus in FOCUS_MODE_CONTINUOUS_PICTURE mode would call the callback when the camera isn't focusing,
			 * and return whether focus was successful or not. So we replicate the behaviour here too (see previewCaptureCallback.process()).
			 * This is essential to have correct behaviour for flash mode in continuous picture focus mode. Otherwise:
			 *  - Taking photo with flash auto when flash is used, or flash on, takes longer (excessive amount of flash firing due to an additional unnecessary focus before taking photo).
			 *  - Taking photo with flash auto when flash is needed sometime results in flash firing for the (unnecessary) autofocus, then not firing for final picture, resulting in too dark pictures.
			 *    This seems to happen with scenes that have both light and dark regions.
			 *  (All tested on Nexus 6, Android 6.)
			 */
			this.autofocus_cb = cb;
			return;
		}
		/*if( state == STATE_WAITING_AUTOFOCUS ) {
			if( MyDebug.LOG )
				Log.d(TAG, "already waiting for an autofocus");
			// need to update the callback!
			this.autofocus_cb = cb;
			return;
		}*/
		CaptureRequest.Builder afBuilder = previewBuilder;
		state = STATE_WAITING_AUTOFOCUS;
		precapture_started = -1;
		this.autofocus_cb = cb;
		// Camera2Basic sets a trigger with capture
		// Google Camera sets to idle with a repeating request, then sets af trigger to start with a capture
		try {
			afBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
			setRepeatingRequest(afBuilder.build());
			afBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
			capture(afBuilder.build());
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
			state = STATE_NORMAL;
			precapture_started = -1;
			autofocus_cb.onAutoFocus(false);
			autofocus_cb = null;
		} 
		afBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE); // ensure set back to idle
	}

	@Override
	public void cancelAutoFocus() {
		if( camera == null || captureSession == null ) {
			return;
		}
    	previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
		// Camera2Basic does a capture then sets a repeating request - do the same here just to be safe
    	try {
    		capture();
    	}
		catch(CameraAccessException e) {
			e.printStackTrace();
		}
    	previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
		this.autofocus_cb = null;
		state = STATE_NORMAL;
		precapture_started = -1;
		try {
			setRepeatingRequest();
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
		} 
	}
	
	@Override
	public void setContinuousFocusMoveCallback(ContinuousFocusMoveCallback cb) {
		this.continuous_focus_move_callback = cb;
	}

	private void takePictureAfterPrecapture() {
		if( camera == null || captureSession == null ) {
			return;
		}
		try {
			CaptureRequest.Builder stillBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
			stillBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
			stillBuilder.setTag(RequestTag.CAPTURE);
			camera_settings.setupBuilder(stillBuilder, true);
			//stillBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
			//stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
			clearPending();
        	Surface surface = getPreviewSurface();
        	stillBuilder.addTarget(surface); // Google Camera adds the preview surface as well as capture surface, for still capture
    		stillBuilder.addTarget(imageReader.getSurface());
        	if( imageReaderRaw != null )
    			stillBuilder.addTarget(imageReaderRaw.getSurface());

			captureSession.stopRepeating(); // need to stop preview before capture (as done in Camera2Basic; otherwise we get bugs such as flash remaining on after taking a photo with flash)
			captureSession.capture(stillBuilder.build(), previewCaptureCallback, handler);
			if( sounds_enabled ) // play shutter sound asap, otherwise user has the illusion of being slow to take photos
				media_action_sound.play(MediaActionSound.SHUTTER_CLICK);
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
			jpeg_cb = null;
			if( take_picture_error_cb != null ) {
				take_picture_error_cb.onError();
				take_picture_error_cb = null;
				return;
			}
		}
	}

	private void takePictureBurstHdr() {
		if( camera == null || captureSession == null ) {
			return;
		}
		try {
			CaptureRequest.Builder stillBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
			stillBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
			stillBuilder.setTag(RequestTag.CAPTURE);
			camera_settings.setupBuilder(stillBuilder, true);
			clearPending();
        	Surface surface = getPreviewSurface();
        	stillBuilder.addTarget(surface); // Google Camera adds the preview surface as well as capture surface, for still capture
			stillBuilder.addTarget(imageReader.getSurface());

			List<CaptureRequest> requests = new ArrayList<CaptureRequest>();

			stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
			stillBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
			if( capture_result_has_iso )
				stillBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, capture_result_iso );
			else
				stillBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 800);
			if( capture_result_has_frame_duration  )
				stillBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, capture_result_frame_duration);
			else
				stillBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, 1000000000l/30);

			long base_exposure_time = 1000000000l/30;
			if( capture_result_has_exposure_time )
				base_exposure_time = capture_result_exposure_time;
			long dark_exposure_time = base_exposure_time;
			long light_exposure_time = base_exposure_time;
			long min_exposure_time = base_exposure_time;
			long max_exposure_time = base_exposure_time;
			//final double scale = 2.0;
			final double scale = 4.0;
			Range<Long> exposure_time_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
			if( exposure_time_range != null ) {
				min_exposure_time = exposure_time_range.getLower();
				max_exposure_time = exposure_time_range.getUpper();
				dark_exposure_time = (long)(base_exposure_time/scale);
				light_exposure_time = (long)(base_exposure_time*scale);
				if( dark_exposure_time < min_exposure_time )
					dark_exposure_time = min_exposure_time;
				if( light_exposure_time > max_exposure_time )
					light_exposure_time = max_exposure_time;
			}

			stillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, dark_exposure_time);
			requests.add( stillBuilder.build() );
			stillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, base_exposure_time);
			requests.add( stillBuilder.build() );
			stillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, light_exposure_time);
			requests.add( stillBuilder.build() );

			n_burst = requests.size();

			captureSession.stopRepeating(); // see note under takePictureAfterPrecapture()
			captureSession.captureBurst(requests, previewCaptureCallback, handler);
			if( sounds_enabled ) // play shutter sound asap, otherwise user has the illusion of being slow to take photos
				media_action_sound.play(MediaActionSound.SHUTTER_CLICK);
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
			jpeg_cb = null;
			if( take_picture_error_cb != null ) {
				take_picture_error_cb.onError();
				take_picture_error_cb = null;
				return;
			}
		}
	}

	private void runPrecapture() {
		// first run precapture sequence
		try {
			// use a separate builder for precapture - otherwise have problem that if we take photo with flash auto/on of dark scene, then point to a bright scene, the autoexposure isn't running until we autofocus again
			final CaptureRequest.Builder precaptureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
			precaptureBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);

			camera_settings.setupBuilder(precaptureBuilder, false);
			precaptureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
			precaptureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

			precaptureBuilder.addTarget(getPreviewSurface());

	    	state = STATE_WAITING_PRECAPTURE_START;
	    	precapture_started = System.currentTimeMillis();

	    	// first set precapture to idle - this is needed, otherwise we hang in state STATE_WAITING_PRECAPTURE_START, because precapture already occurred whilst autofocusing, and it doesn't occur again unless we first set the precapture trigger to idle
			captureSession.capture(precaptureBuilder.build(), previewCaptureCallback, handler);
			captureSession.setRepeatingRequest(precaptureBuilder.build(), previewCaptureCallback, handler);

			// now set precapture
			precaptureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
			captureSession.capture(precaptureBuilder.build(), previewCaptureCallback, handler);
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
			jpeg_cb = null;
			if( take_picture_error_cb != null ) {
				take_picture_error_cb.onError();
				take_picture_error_cb = null;
				return;
			}
		}
	}
	
	@Override
	public void takePicture(final PictureCallback picture, final ErrorCallback error) {
		if( camera == null || captureSession == null ) {
			error.onError();
			return;
		}
		// we store as two identical callbacks, so we can independently set each to null as the two callbacks occur
		this.jpeg_cb = picture;
		if( imageReaderRaw != null )
			this.raw_cb = picture;
		else
			this.raw_cb = null;
		this.take_picture_error_cb = error;
		if( !ready_for_capture ) {
			//throw new RuntimeException(); // debugging
		}
		if( want_hdr ) {
			takePictureBurstHdr();
		}
		else {
			// Don't need precapture if flash off or torch
			// And currently has_iso manual mode doesn't support flash - but just in case that's changed later, we still probably don't want to be doing a precapture...
			if( camera_settings.has_iso || camera_settings.flash_value.equals("flash_off") || camera_settings.flash_value.equals("flash_torch") ) {
				takePictureAfterPrecapture();
			}
			else {
				runPrecapture();
			}
		}
	}

	@Override
	public void setDisplayOrientation(int degrees) {
		// for CameraController2, the preview display orientation is handled via the TextureView's transform
		throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
	}

	@Override
	public int getDisplayOrientation() {
		throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
	}

	@Override
	public int getCameraOrientation() {
		return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
	}

	@Override
	public boolean isFrontFacing() {
		return characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
	}

	@Override
	public void reconnect() throws CameraControllerException {
		// if we change where we play the STOP_VIDEO_RECORDING sound, make sure it can't be heard in resultant video
		if( sounds_enabled )
			media_action_sound.play(MediaActionSound.STOP_VIDEO_RECORDING);
		createPreviewRequest();
		createCaptureSession(null);
		/*if( MyDebug.LOG )
			Log.d(TAG, "add preview surface to previewBuilder");
    	Surface surface = getPreviewSurface();
		previewBuilder.addTarget(surface);*/
		//setRepeatingRequest();
	}

	@Override
	public boolean captureResultHasIso() {
		return capture_result_has_iso;
	}

	@Override
	public int captureResultIso() {
		return capture_result_iso;
	}

	@Override
	public boolean captureResultHasExposureTime() {
		return capture_result_has_exposure_time;
	}

	@Override
	public long captureResultExposureTime() {
		return capture_result_exposure_time;
	}

	private CameraCaptureSession.CaptureCallback previewCaptureCallback = new CameraCaptureSession.CaptureCallback() {
		private long last_process_frame_number = 0;
		private int last_af_state = -1;

		public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
			if( request.getTag() == RequestTag.CAPTURE ) {
				// n.b., we don't play the shutter sound here, as it typically sounds "too late"
			}
		}

		public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
			/*if( MyDebug.LOG )
				Log.d(TAG, "onCaptureProgressed");*/
			process(request, partialResult);
			super.onCaptureProgressed(session, request, partialResult); // API docs say this does nothing, but call it just to be safe (as with Google Camera)
		}

		public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
			/*if( MyDebug.LOG )
				Log.d(TAG, "onCaptureCompleted");*/
			process(request, result);
			processCompleted(request, result);
			super.onCaptureCompleted(session, request, result); // API docs say this does nothing, but call it just to be safe (as with Google Camera)
		}

		/** Processes either a partial or total result.
		 */
		private void process(CaptureRequest request, CaptureResult result) {
			/*if( MyDebug.LOG )
			Log.d(TAG, "process, state: " + state);*/
			if( result.getFrameNumber() < last_process_frame_number ) {
				/*if( MyDebug.LOG )
					Log.d(TAG, "processAF discarded outdated frame " + result.getFrameNumber() + " vs " + last_process_frame_number);*/
				return;
			}
			last_process_frame_number = result.getFrameNumber();
			
			// use Integer instead of int, so can compare to null: Google Play crashes confirmed that this can happen; Google Camera also ignores cases with null af state
			Integer af_state = result.get(CaptureResult.CONTROL_AF_STATE);
			if( af_state != null && af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN ) {
				/*if( MyDebug.LOG )
					Log.d(TAG, "not ready for capture: " + af_state);*/
				ready_for_capture = false;
			}
			else {
				/*if( MyDebug.LOG )
					Log.d(TAG, "ready for capture: " + af_state);*/
				ready_for_capture = true;
				if( autofocus_cb != null && focusIsContinuous() ) {
					Integer focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
					if( focus_mode != null && focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ) {
						boolean focus_success = af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED;
						autofocus_cb.onAutoFocus(focus_success);
						autofocus_cb = null;
					}
				}
			}

			if( state == STATE_NORMAL ) {
				// do nothing
			}
			else if( state == STATE_WAITING_AUTOFOCUS ) {
				if( af_state == null ) {
					// autofocus shouldn't really be requested if af not available, but still allow this rather than getting stuck waiting for autofocus to complete
					state = STATE_NORMAL;
					precapture_started = -1;
					if( autofocus_cb != null ) {
						autofocus_cb.onAutoFocus(false);
						autofocus_cb = null;
					}
				}
				else if( af_state != last_af_state ) {
					// check for autofocus completing
					// need to check that af_state != last_af_state, except for continuous focus mode where if we're already focused, should return immediately
					if( af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || af_state == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED ||
							af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED || af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED
							) {
						boolean focus_success = af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED;

						state = STATE_NORMAL;
						precapture_started = -1;
						if( autofocus_cb != null ) {
							autofocus_cb.onAutoFocus(focus_success);
							autofocus_cb = null;
						}
					}
				}
			}
			else if( state == STATE_WAITING_PRECAPTURE_START ) {
				// CONTROL_AE_STATE can be null on some devices
				Integer ae_state = result.get(CaptureResult.CONTROL_AE_STATE);
				if( ae_state == null || ae_state == CaptureResult.CONTROL_AE_STATE_PRECAPTURE /*|| ae_state == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED*/ ) {
					// we have to wait for CONTROL_AE_STATE_PRECAPTURE; if we allow CONTROL_AE_STATE_FLASH_REQUIRED, then on Nexus 6 at least we get poor quality results with flash:
					// varying levels of brightness, sometimes too bright or too dark, sometimes with blue tinge, sometimes even with green corruption
					state = STATE_WAITING_PRECAPTURE_DONE;
					precapture_started = -1;
				}
				else if( precapture_started != -1 && System.currentTimeMillis() - precapture_started > 2000 ) {
					// hack - give up waiting - sometimes we never get a CONTROL_AE_STATE_PRECAPTURE so would end up stuck
					count_precapture_timeout++;
					state = STATE_WAITING_PRECAPTURE_DONE;
					precapture_started = -1;
				}
			}
			else if( state == STATE_WAITING_PRECAPTURE_DONE ) {
				// CONTROL_AE_STATE can be null on some devices
				Integer ae_state = result.get(CaptureResult.CONTROL_AE_STATE);
				if( ae_state == null || ae_state != CaptureResult.CONTROL_AE_STATE_PRECAPTURE ) {
					state = STATE_NORMAL;
					precapture_started = -1;
					takePictureAfterPrecapture();
				}
			}

			if( af_state != null && af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN && af_state != last_af_state ) {
				if( continuous_focus_move_callback != null ) {
					continuous_focus_move_callback.onContinuousFocusMove(true);
				}
			}
			else if( af_state != null && last_af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN && af_state != last_af_state ) {
				if( continuous_focus_move_callback != null ) {
					continuous_focus_move_callback.onContinuousFocusMove(false);
				}
			}

			if( af_state != null && af_state != last_af_state ) {
				last_af_state = af_state;
			}
		}
		
		/** Processes a total result.
		 */
		private void processCompleted(CaptureRequest request, CaptureResult result) {
			/*if( MyDebug.LOG )
				Log.d(TAG, "processCompleted");*/

			if( result.get(CaptureResult.SENSOR_SENSITIVITY) != null ) {
				capture_result_has_iso = true;
				capture_result_iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
				/*if( MyDebug.LOG )
					Log.d(TAG, "capture_result_iso: " + capture_result_iso);*/
				if( camera_settings.has_iso && camera_settings.iso != capture_result_iso ) {
					// ugly hack: problem that when we start recording video (video_recorder.start() call), this often causes the ISO setting to reset to the wrong value!
					// seems to happen more often with shorter exposure time
					// seems to happen on other camera apps with Camera2 API too
					// this workaround still means a brief flash with incorrect ISO, but is best we can do for now!

					try {
						setRepeatingRequest();
					}
					catch(CameraAccessException e) {

						e.printStackTrace();
					} 
				}
			}
			else {
				capture_result_has_iso = false;
			}
			if( result.get(CaptureResult.SENSOR_EXPOSURE_TIME) != null ) {
				capture_result_has_exposure_time = true;
				capture_result_exposure_time = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
			}
			else {
				capture_result_has_exposure_time = false;
			}
			if( result.get(CaptureResult.SENSOR_FRAME_DURATION) != null ) {
				capture_result_has_frame_duration = true;
				capture_result_frame_duration = result.get(CaptureResult.SENSOR_FRAME_DURATION);
			}
			else {
				capture_result_has_frame_duration = false;
			}
			/*if( MyDebug.LOG ) {
				if( result.get(CaptureResult.SENSOR_EXPOSURE_TIME) != null ) {
					long capture_result_exposure_time = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
					Log.d(TAG, "capture_result_exposure_time: " + capture_result_exposure_time);
				}
				if( result.get(CaptureResult.SENSOR_FRAME_DURATION) != null ) {
					long capture_result_frame_duration = result.get(CaptureResult.SENSOR_FRAME_DURATION);
					Log.d(TAG, "capture_result_frame_duration: " + capture_result_frame_duration);
				}
			}*/


			if( push_repeating_request_when_torch_off && push_repeating_request_when_torch_off_id == request ) {
				Integer flash_state = result.get(CaptureResult.FLASH_STATE);

				if( flash_state != null && flash_state == CaptureResult.FLASH_STATE_READY ) {
					push_repeating_request_when_torch_off = false;
					push_repeating_request_when_torch_off_id = null;
					try {
						setRepeatingRequest();
					}
					catch(CameraAccessException e) {
						e.printStackTrace();
					} 
				}
			}
			
			if( request.getTag() == RequestTag.CAPTURE ) {
				if( onRawImageAvailableListener != null ) {
					if( test_wait_capture_result ) {
						// for RAW capture, we require the capture result before creating DngCreator
						// but for testing purposes, we need to test the possibility where onImageAvailable() for
						// the RAW image is called before we receive the capture result here
						try {
							Thread.sleep(500); // 200ms is enough to test the problem on Nexus 6, but use 500ms to be sure
						}
						catch(InterruptedException e) {
							e.printStackTrace();
						}
					}
					onRawImageAvailableListener.setCaptureResult(result);
				}
				// actual parsing of image data is done in the imageReader's OnImageAvailableListener()
				// need to cancel the autofocus, and restart the preview after taking the photo
				// Camera2Basic does a capture then sets a repeating request - do the same here just to be safe
				previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
				camera_settings.setAEMode(previewBuilder, false); // not sure if needed, but the AE mode is set again in Camera2Basic
				// n.b., if capture/setRepeatingRequest throw exception, we don't call the take_picture_error_cb.onError() callback, as the photo should have been taken by this point
				try {
	            	capture();
				}
				catch(CameraAccessException e) {
					e.printStackTrace();
				}
				previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE); // ensure set back to idle
				try {
					setRepeatingRequest();
				}
				catch(CameraAccessException e) {
					e.printStackTrace();
					preview_error_cb.onError();
				}
			}
		}
	};
}
