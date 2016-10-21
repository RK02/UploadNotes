package com.campusconnect.previewdemo.Preview;

import com.campusconnect.previewdemo.R;
import com.campusconnect.previewdemo.TakePhoto;
import com.campusconnect.previewdemo.ToastBoxer;
import com.campusconnect.previewdemo.CameraController.CameraController;
import com.campusconnect.previewdemo.CameraController.CameraController1;
import com.campusconnect.previewdemo.CameraController.CameraController2;
import com.campusconnect.previewdemo.CameraController.CameraControllerException;
import com.campusconnect.previewdemo.CameraController.CameraControllerManager;
import com.campusconnect.previewdemo.CameraController.CameraControllerManager1;
import com.campusconnect.previewdemo.CameraController.CameraControllerManager2;
import com.campusconnect.previewdemo.Preview.CameraSurface.CameraSurface;
import com.campusconnect.previewdemo.Preview.CameraSurface.MySurfaceView;
import com.campusconnect.previewdemo.Preview.CameraSurface.MyTextureView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.camera2.DngCreator;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.View.MeasureSpec;
import android.widget.Toast;

/** This class was originally named due to encapsulating the camera preview,
 *  but in practice it's grown to more than this, and includes most of the
 *  operation of the camera. It exists at a higher level than CameraController
 *  (i.e., this isn't merely a low level wrapper to the camera API, but
 *  supports much of the Open Camera logic and functionality). Communication to
 *  the rest of the application is available through ApplicationInterface.
 *  We could probably do with decoupling this class into separate components!
 */
public class Preview implements SurfaceHolder.Callback, TextureView.SurfaceTextureListener {
	private static final String TAG = "Preview";

	private boolean using_android_l = false;
	private boolean using_texture_view = false;

	private ApplicationInterface applicationInterface = null;
	private CameraSurface cameraSurface = null;
	private CanvasView canvasView = null;
	private boolean set_preview_size = false;
	private int preview_w = 0, preview_h = 0;
	private boolean set_textureview_size = false;
	private int textureview_w = 0, textureview_h = 0;

    private Matrix camera_to_preview_matrix = new Matrix();
    private Matrix preview_to_camera_matrix = new Matrix();
	//private RectF face_rect = new RectF();
    private double preview_targetRatio = 0.0;

	//private boolean ui_placement_right = true;

	private boolean app_is_paused = true;
	private boolean has_surface = false;
	private boolean has_aspect_ratio = false;
	private double aspect_ratio = 0.0f;
	private CameraControllerManager camera_controller_manager = null;
	private CameraController camera_controller = null;

	private static final int PHASE_NORMAL = 0;
	private static final int PHASE_TIMER = 1;
	private static final int PHASE_TAKING_PHOTO = 2;
	private static final int PHASE_PREVIEW_PAUSED = 3; // the paused state after taking a photo
	private int phase = PHASE_NORMAL;
	private long take_photo_time = 0;
	private int remaining_burst_photos = 0;

	private boolean is_preview_started = false;

	private int current_orientation = 0; // orientation received by onOrientationChanged
	private int current_rotation = 0; // orientation relative to camera's orientation (used for parameters.setRotation())
	private boolean has_level_angle = false;
	private double level_angle = 0.0f;
	private double orig_level_angle = 0.0f;
	
	private boolean has_zoom = false;
	private int max_zoom_factor = 0;
	private GestureDetector gestureDetector = null;
	private ScaleGestureDetector scaleGestureDetector = null;
	private List<Integer> zoom_ratios = null;
	private float minimum_focus_distance = 0.0f;
	private boolean touch_was_multitouch = false;
	private float touch_orig_x = 0.0f;
	private float touch_orig_y = 0.0f;

	private List<String> supported_flash_values = null; // our "values" format
	private int current_flash_index = -1; // this is an index into the supported_flash_values array, or -1 if no flash modes available

	private List<String> supported_focus_values = null; // our "values" format
	private int current_focus_index = -1; // this is an index into the supported_focus_values array, or -1 if no focus modes available
	private boolean continuous_focus_move_is_started = false;
	
	private boolean is_exposure_lock_supported = false;
	private boolean is_exposure_locked = false;

	private List<String> color_effects = null;
	private List<String> scene_modes = null;
	private List<String> white_balances = null;
	private List<String> isos = null;
	private boolean supports_iso_range = false;
	private int min_iso = 0;
	private int max_iso = 0;
	private boolean supports_exposure_time = false;
	private long min_exposure_time = 0l;
	private long max_exposure_time = 0l;
	private List<String> exposures = null;
	private int min_exposure = 0;
	private int max_exposure = 0;
	private float exposure_step = 0.0f;
	private boolean supports_hdr = false;
	private boolean supports_raw = false;

	private List<CameraController.Size> supported_preview_sizes = null;
	
	private List<CameraController.Size> sizes = null;
	private int current_size_index = -1; // this is an index into the sizes array, or -1 if sizes not yet set

	// video_quality can either be:
	// - an int, in which case it refers to a CamcorderProfile
	// - of the form [CamcorderProfile]_r[width]x[height] - we use the CamcorderProfile as a base, and override the video resolution - this is needed to support resolutions which don't have corresponding camcorder profiles
	private List<String> video_quality = null;
	private int current_video_quality = -1; // this is an index into the video_quality array, or -1 if not found (though this shouldn't happen?)
	private List<CameraController.Size> video_sizes = null;
	
	/*private Bitmap location_bitmap = null;
	private Bitmap location_off_bitmap = null;
	private Rect location_dest = new Rect();*/

	private Toast last_toast = null;
	private ToastBoxer flash_toast = new ToastBoxer();
	private ToastBoxer focus_toast = new ToastBoxer();
	private ToastBoxer take_photo_toast = new ToastBoxer();
	private ToastBoxer seekbar_toast = new ToastBoxer();
	
	private int ui_rotation = 0;

	private boolean supports_face_detection = false;
	private boolean using_face_detection = false;
	private boolean supports_video_stabilization = false;
	private boolean can_disable_shutter_sound = false;
	private boolean has_focus_area = false;
	private int focus_screen_x = 0;
	private int focus_screen_y = 0;
	private long focus_complete_time = -1;
	private long focus_started_time = -1;
	private int focus_success = FOCUS_DONE;
	private static final int FOCUS_WAITING = 0;
	private static final int FOCUS_SUCCESS = 1;
	private static final int FOCUS_FAILED = 2;
	private static final int FOCUS_DONE = 3;
	private String set_flash_value_after_autofocus = "";
	private boolean take_photo_after_autofocus = false; // set to take a photo when the in-progress autofocus has completed
	private boolean successfully_focused = false;
	private long successfully_focused_time = -1;

	/*private IntentFilter battery_ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
	private boolean has_battery_frac = false;
	private float battery_frac = 0.0f;
	private long last_battery_time = 0;*/

	// accelerometer and geomagnetic sensor info
	private static final float sensor_alpha = 0.8f; // for filter
    private boolean has_gravity = false;
    private float [] gravity = new float[3];
    private boolean has_geomagnetic = false;
    private float [] geomagnetic = new float[3];
    private float [] deviceRotation = new float[9];
    private float [] cameraRotation = new float[9];
    private float [] deviceInclination = new float[9];
    private boolean has_geo_direction = false;
    private float [] geo_direction = new float[3];

	private final DecimalFormat decimal_format_1dp = new DecimalFormat("#.#");
	private final DecimalFormat decimal_format_2dp = new DecimalFormat("#.##");

	/* If the user touches to focus in continuous mode, we switch the camera_controller to autofocus mode.
	 * autofocus_in_continuous_mode is set to true when this happens; the runnable reset_continuous_focus_runnable
	 * switches back to continuous mode.
	 */
	private Handler reset_continuous_focus_handler = new Handler();
	private Runnable reset_continuous_focus_runnable = null;
	private boolean autofocus_in_continuous_mode = false;

	// for testing:
	public int count_cameraStartPreview = 0;
	public int count_cameraAutoFocus = 0;
	public int count_cameraTakePicture = 0;
	public int count_cameraContinuousFocusMoving = 0;
	public boolean test_fail_open_camera = false;

	public Preview(ApplicationInterface applicationInterface, Bundle savedInstanceState, ViewGroup parent) {
		
		this.applicationInterface = applicationInterface;
		
		this.using_android_l = applicationInterface.useCamera2();
		
		if( using_android_l ) {
        	// use a TextureView for Android L - had bugs with SurfaceView not resizing properly on Nexus 7; and good to use a TextureView anyway
        	// ideally we'd use a TextureView for older camera API too, but sticking with SurfaceView to avoid risk of breaking behaviour
			this.using_texture_view = true;
		}

        if( using_texture_view ) {
    		this.cameraSurface = new MyTextureView(getContext(), savedInstanceState, this);
    		// a TextureView can't be used both as a camera preview, and used for drawing on, so we use a separate CanvasView
    		this.canvasView = new CanvasView(getContext(), savedInstanceState, this);
    		camera_controller_manager = new CameraControllerManager2(getContext());
        }
        else {
    		this.cameraSurface = new MySurfaceView(getContext(), savedInstanceState, this);
    		camera_controller_manager = new CameraControllerManager1();
        }

	    gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener());
	    gestureDetector.setOnDoubleTapListener(new DoubleTapListener());

		parent.addView(cameraSurface.getView());
		if( canvasView != null ) {
			parent.addView(canvasView);
		}
	}

	private Resources getResources() {
		return cameraSurface.getView().getResources();
	}
	
	public View getView() {
		return cameraSurface.getView();
	}

	private void calculateCameraToPreviewMatrix() {
		if( camera_controller == null )
			return;
		camera_to_preview_matrix.reset();
	    if( !using_android_l ) {
			// from http://developer.android.com/reference/android/hardware/Camera.Face.html#rect
			// Need mirror for front camera
			boolean mirror = camera_controller.isFrontFacing();
			camera_to_preview_matrix.setScale(mirror ? -1 : 1, 1);
			// This is the value for android.hardware.Camera.setDisplayOrientation.
			camera_to_preview_matrix.postRotate(camera_controller.getDisplayOrientation());
	    }
	    else {
	    	// unfortunately the transformation for Android L API isn't documented, but this seems to work for Nexus 6
			boolean mirror = camera_controller.isFrontFacing();
			camera_to_preview_matrix.setScale(1, mirror ? -1 : 1);
	    }
		// Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
		// UI coordinates range from (0, 0) to (width, height).
		camera_to_preview_matrix.postScale(cameraSurface.getView().getWidth() / 2000f, cameraSurface.getView().getHeight() / 2000f);
		camera_to_preview_matrix.postTranslate(cameraSurface.getView().getWidth() / 2f, cameraSurface.getView().getHeight() / 2f);
	}
	
	private void calculatePreviewToCameraMatrix() {
		if( camera_controller == null )
			return;
		calculateCameraToPreviewMatrix();
	}

	private ArrayList<CameraController.Area> getAreas(float x, float y) {
		float [] coords = {x, y};
		calculatePreviewToCameraMatrix();
		preview_to_camera_matrix.mapPoints(coords);
		float focus_x = coords[0];
		float focus_y = coords[1];
		
		int focus_size = 50;

		Rect rect = new Rect();
		rect.left = (int)focus_x - focus_size;
		rect.right = (int)focus_x + focus_size;
		rect.top = (int)focus_y - focus_size;
		rect.bottom = (int)focus_y + focus_size;
		if( rect.left < -1000 ) {
			rect.left = -1000;
			rect.right = rect.left + 2*focus_size;
		}
		else if( rect.right > 1000 ) {
			rect.right = 1000;
			rect.left = rect.right - 2*focus_size;
		}
		if( rect.top < -1000 ) {
			rect.top = -1000;
			rect.bottom = rect.top + 2*focus_size;
		}
		else if( rect.bottom > 1000 ) {
			rect.bottom = 1000;
			rect.top = rect.bottom - 2*focus_size;
		}

	    ArrayList<CameraController.Area> areas = new ArrayList<CameraController.Area>();
	    areas.add(new CameraController.Area(rect, 1000));
	    return areas;
	}

	public boolean touchEvent(MotionEvent event) {
        if( gestureDetector.onTouchEvent(event) ) {
        	return true;
        }
        if( camera_controller == null ) {
    		this.openCamera();
    		return true;
        }
        applicationInterface.touchEvent(event);
		if( event.getPointerCount() != 1 ) {
			touch_was_multitouch = true;
			return true;
		}
		if( event.getAction() != MotionEvent.ACTION_UP ) {
			if( event.getAction() == MotionEvent.ACTION_DOWN && event.getPointerCount() == 1 ) {
				touch_was_multitouch = false;
				if( event.getAction() == MotionEvent.ACTION_DOWN ) {
					touch_orig_x = event.getX();
					touch_orig_y = event.getY();
				}
			}
			return true;
		}
		// now only have to handle MotionEvent.ACTION_UP from this point onwards

		if( touch_was_multitouch ) {
			return true;
		}
		if( this.isTakingPhotoOrOnTimer() ) {
			// if video, okay to refocus when recording
			return true;
		}
		
		// ignore swipes
		{
			float x = event.getX();
			float y = event.getY();
			float diff_x = x - touch_orig_x;
			float diff_y = y - touch_orig_y;
			float dist2 = diff_x*diff_x + diff_y*diff_y;
			float scale = getResources().getDisplayMetrics().density;
			float tol = 31 * scale + 0.5f; // convert dps to pixels (about 0.5cm)

			if( dist2 > tol*tol ) {
				return true;
			}
		}

		// note, we always try to force start the preview (in case is_preview_paused has become false)
		// except if recording video (firstly, the preview should be running; secondly, we don't want to reset the phase!)

			startCameraPreview();

		cancelAutoFocus();

        if( camera_controller != null && !this.using_face_detection ) {
    		this.has_focus_area = false;
			ArrayList<CameraController.Area> areas = getAreas(event.getX(), event.getY());
        	if( camera_controller.setFocusAndMeteringArea(areas) ) {
				this.has_focus_area = true;
				this.focus_screen_x = (int)event.getX();
				this.focus_screen_y = (int)event.getY();
        	}
        }
        
		if( applicationInterface.getTouchCapturePref() ) {
			// interpret as if user had clicked take photo/video button, except that we set the focus/metering areas
	    	this.takePicturePressed();
	    	return true;
		}

		tryAutoFocus(false, true);
		return true;
	}
	
	public boolean onDoubleTap() {
		if( applicationInterface.getDoubleTapCapturePref() ) {
			// interpret as if user had clicked take photo/video button (don't need to set focus/metering, as this was done in touchEvent() for the first touch of the double-tap)
	    	takePicturePressed();
		}
		return true;
	}
    
	private class DoubleTapListener extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onDoubleTap(MotionEvent e) {
			return Preview.this.onDoubleTap();
		}
    }
    
    public void clearFocusAreas() {
		if( camera_controller == null ) {
			return;
		}
		// don't cancelAutoFocus() here, otherwise we get sluggish zoom behaviour on Camera2 API
		// don't cancelAutoFocus() here, otherwise we get sluggish zoom behaviour on Camera2 API
        camera_controller.clearFocusAndMetering();
		has_focus_area = false;
		focus_success = FOCUS_DONE;
		successfully_focused = false;
    }

    public void getMeasureSpec(int [] spec, int widthSpec, int heightSpec) {
    	if( !this.hasAspectRatio() ) {
    		spec[0] = widthSpec;
    		spec[1] = heightSpec;
    		return;
    	}
    	double aspect_ratio = this.getAspectRatio();

    	int previewWidth = MeasureSpec.getSize(widthSpec);
        int previewHeight = MeasureSpec.getSize(heightSpec);

        // Get the padding of the border background.
        int hPadding = cameraSurface.getView().getPaddingLeft() + cameraSurface.getView().getPaddingRight();
        int vPadding = cameraSurface.getView().getPaddingTop() + cameraSurface.getView().getPaddingBottom();

        // Resize the preview frame with correct aspect ratio.
        previewWidth -= hPadding;
        previewHeight -= vPadding;

        boolean widthLonger = previewWidth > previewHeight;
        int longSide = (widthLonger ? previewWidth : previewHeight);
        int shortSide = (widthLonger ? previewHeight : previewWidth);
        if (longSide > shortSide * aspect_ratio) {
            longSide = (int) ((double) shortSide * aspect_ratio);
        } else {
            shortSide = (int) ((double) longSide / aspect_ratio);
        }
        if (widthLonger) {
            previewWidth = longSide;
            previewHeight = shortSide;
        } else {
            previewWidth = shortSide;
            previewHeight = longSide;
        }

        // Add the padding of the border.
        previewWidth += hPadding;
        previewHeight += vPadding;

        spec[0] = MeasureSpec.makeMeasureSpec(previewWidth, MeasureSpec.EXACTLY);
        spec[1] = MeasureSpec.makeMeasureSpec(previewHeight, MeasureSpec.EXACTLY);
    }
    
    private void mySurfaceCreated() {
		this.has_surface = true;
		this.openCamera();
    }
    
    private void mySurfaceDestroyed() {
		this.has_surface = false;
		this.closeCamera();
    }
    
    private void mySurfaceChanged() {
		// surface size is now changed to match the aspect ratio of camera preview - so we shouldn't change the preview to match the surface size, so no need to restart preview here
        if( camera_controller == null ) {
            return;
        }
        
		// need to force a layoutUI update (e.g., so UI is oriented correctly when app goes idle, device is then rotated, and app is then resumed)
        applicationInterface.layoutUI();
    }
    
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		mySurfaceCreated();
		cameraSurface.getView().setWillNotDraw(false); // see http://stackoverflow.com/questions/2687015/extended-surfaceviews-ondraw-method-never-called
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// Surface will be destroyed when we return, so stop the preview.
		// Because the CameraDevice object is not a shared resource, it's very
		// important to release it when the activity is paused.
		mySurfaceDestroyed();
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if( holder.getSurface() == null ) {
            // preview surface does not exist
            return;
        }
		mySurfaceChanged();
	}
	
	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture arg0, int width, int height) {
		this.set_textureview_size = true;
		this.textureview_w = width;
		this.textureview_h = height;
		mySurfaceCreated();
		configureTransform();
	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture arg0) {
		this.set_textureview_size = false;
		this.textureview_w = 0;
		this.textureview_h = 0;
		mySurfaceDestroyed();
		return true;
	}

	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture arg0, int width, int height) {
		this.set_textureview_size = true;
		this.textureview_w = width;
		this.textureview_h = height;
		mySurfaceChanged();
		configureTransform();
	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture arg0) {
	}

    private void configureTransform() {
    	if( camera_controller == null || !this.set_preview_size || !this.set_textureview_size )
    		return;
    	int rotation = getDisplayRotation();
    	Matrix matrix = new Matrix(); 
		RectF viewRect = new RectF(0, 0, this.textureview_w, this.textureview_h); 
		RectF bufferRect = new RectF(0, 0, this.preview_h, this.preview_w); 
		float centerX = viewRect.centerX(); 
		float centerY = viewRect.centerY(); 
        if( Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation ) { 
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY()); 
	        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL); 
	        float scale = Math.max(
	        		(float) textureview_h / preview_h, 
                    (float) textureview_w / preview_w); 
            matrix.postScale(scale, scale, centerX, centerY); 
            matrix.postRotate(90 * (rotation - 2), centerX, centerY); 
        } 
        cameraSurface.setTransform(matrix); 
    }
	
	private Context getContext() {
		return applicationInterface.getContext();
	}
	
	private void reconnectCamera(boolean quiet) {
        if( camera_controller != null ) { // just to be safe
    		try {
    			camera_controller.reconnect();
    			this.setPreviewPaused(false);
			}
    		catch(CameraControllerException e) {
				e.printStackTrace();
				applicationInterface.onFailedReconnectError();
	    	    closeCamera();
			}
    		try {
    			tryAutoFocus(false, false);
    		}
    		catch(RuntimeException e) {
    			e.printStackTrace();
    			// this happens on Nexus 7 if trying to record video at bitrate 50Mbits or higher - it's fair enough that it fails, but we need to recover without a crash!
    			// not safe to call closeCamera, as any call to getParameters may cause a RuntimeException
    			// update: can no longer reproduce failures on Nexus 7?!
    			this.is_preview_started = false;
    			if( !quiet ) {
    	        	CamcorderProfile profile = getCamcorderProfile();
    			}
    			camera_controller.release();
    			camera_controller = null;
    			openCamera();
    		}
		}
	}

	private void closeCamera() {
		removePendingContinuousFocusReset();
		has_focus_area = false;
		focus_success = FOCUS_DONE;
		focus_started_time = -1;
		synchronized( this ) {
			// synchronise for consistency (keep FindBugs happy)
			take_photo_after_autofocus = false;
		}
		set_flash_value_after_autofocus = "";
		successfully_focused = false;
		preview_targetRatio = 0.0;
		// n.b., don't reset has_set_location, as we can remember the location when switching camera
		if( continuous_focus_move_is_started ) {
			continuous_focus_move_is_started = false;
			applicationInterface.onContinuousFocusMove(false);
		}
		applicationInterface.cameraClosed();
		if( camera_controller != null ) {

			// need to check for camera being non-null again - if an error occurred stopping the video, we will have closed the camera, and may not be able to reopen
			if( camera_controller != null ) {
				pausePreview();
				camera_controller.release();
				camera_controller = null;
			}
		}
	}
	
	public void pausePreview() {
		if( camera_controller == null ) {
			return;
		}

		this.setPreviewPaused(false);

		camera_controller.stopPreview();
		this.phase = PHASE_NORMAL;
		this.is_preview_started = false;

		applicationInterface.cameraInOperation(false);

	}

	private void openCamera() {
		// need to init everything now, in case we don't open the camera (but these may already be initialised from an earlier call - e.g., if we are now switching to another camera)
		// n.b., don't reset has_set_location, as we can remember the location when switching camera
		is_preview_started = false; // theoretically should be false anyway, but I had one RuntimeException from surfaceCreated()->openCamera()->setupCamera()->setPreviewSize() because is_preview_started was true, even though the preview couldn't have been started
    	set_preview_size = false;
    	preview_w = 0;
    	preview_h = 0;
		has_focus_area = false;
		focus_success = FOCUS_DONE;
		focus_started_time = -1;
		synchronized( this ) {
			// synchronise for consistency (keep FindBugs happy)
			take_photo_after_autofocus = false;
		}
		set_flash_value_after_autofocus = "";
		successfully_focused = false;
		preview_targetRatio = 0.0;
		scene_modes = null;
		has_zoom = false;
		max_zoom_factor = 0;
		minimum_focus_distance = 0.0f;
		zoom_ratios = null;
		supports_face_detection = false;
		using_face_detection = false;
		supports_video_stabilization = false;
		can_disable_shutter_sound = false;
		color_effects = null;
		white_balances = null;
		isos = null;
		supports_iso_range = false;
		min_iso = 0;
		max_iso = 0;
		supports_exposure_time = false;
		min_exposure_time = 0l;
		max_exposure_time = 0l;
		exposures = null;
		min_exposure = 0;
		max_exposure = 0;
		exposure_step = 0.0f;
		supports_hdr = false;
		supports_raw = false;
		sizes = null;
		current_size_index = -1;
		video_quality = null;
		current_video_quality = -1;
		supported_flash_values = null;
		current_flash_index = -1;
		supported_focus_values = null;
		current_focus_index = -1;
		applicationInterface.cameraInOperation(false);
		if( !this.has_surface ) {
			return;
		}
		if( this.app_is_paused ) {
			return;
		}

		try {
			int cameraId = applicationInterface.getCameraIdPref();
			if( cameraId < 0 || cameraId >= camera_controller_manager.getNumberOfCameras() ) {
				cameraId = 0;
				applicationInterface.setCameraIdPref(cameraId);
			}
			if( test_fail_open_camera ) {
				throw new CameraControllerException();
			}
	        if( using_android_l ) {
	    		CameraController.ErrorCallback previewErrorCallback = new CameraController.ErrorCallback() {
	    			public void onError() {
	        			applicationInterface.onFailedStartPreview();
	        	    }
	    		};
	        	camera_controller = new CameraController2(this.getContext(), cameraId, previewErrorCallback);
	        }
	        else
				camera_controller = new CameraController1(cameraId);
			//throw new CameraControllerException(); // uncomment to test camera not opening
		}
		catch(CameraControllerException e) {
			e.printStackTrace();
			camera_controller = null;
		}
		boolean take_photo = false;
		if( camera_controller != null ) {
			Activity activity = (Activity)this.getContext();
			if( activity.getIntent() != null && activity.getIntent().getExtras() != null ) {
				take_photo = activity.getIntent().getExtras().getBoolean(TakePhoto.TAKE_PHOTO);
				activity.getIntent().removeExtra(TakePhoto.TAKE_PHOTO);
			}

	        this.setCameraDisplayOrientation();
	        new OrientationEventListener(activity) {
				@Override
				public void onOrientationChanged(int orientation) {
					Preview.this.onOrientationChanged(orientation);
				}
	        }.enable();

			cameraSurface.setPreviewDisplay(camera_controller);

		    setupCamera(take_photo);
		}

	}
	
	/* Should only be called after camera first opened, or after preview is paused.
	 * take_photo is true if we have been called from the TakePhoto widget (which means
	 * we'll take a photo immediately after startup).
	 */
	public void setupCamera(boolean take_photo) {
		if( camera_controller == null ) {
			return;
		}
		boolean do_startup_focus = !take_photo && applicationInterface.getStartupFocusPref();

		setupCameraParameters();
		
		// now switch to video if saved
		boolean saved_is_video = applicationInterface.isVideoPref();

		if( do_startup_focus && using_android_l && camera_controller.supportsAutoFocus() ) {
			// need to switch flash off for autofocus - and for Android L, need to do this before starting preview (otherwise it won't work in time); for old camera API, need to do this after starting preview!
			set_flash_value_after_autofocus = "";
			String old_flash_value = camera_controller.getFlashValue();
			// getFlashValue() may return "" if flash not supported!
			// also set flash_torch - otherwise we get bug where torch doesn't turn on when starting up in video mode (and it's not like we want to turn torch off for startup focus, anyway)
			if( old_flash_value.length() > 0 && !old_flash_value.equals("flash_off") && !old_flash_value.equals("flash_torch") ) {
				set_flash_value_after_autofocus = old_flash_value;
				camera_controller.setFlashValue("flash_off");
			}
		}

		// Must set preview size before starting camera preview
		// and must do it after setting photo vs video mode
		setPreviewSize(); // need to call this when we switch cameras, not just when we run for the first time

		// Must call startCameraPreview after checking if face detection is present - probably best to call it after setting all parameters that we want
		startCameraPreview();

		applicationInterface.cameraSetup(); // must call this after the above take_photo code for calling switchVideo

	    if( take_photo ) {
			// take photo after a delay - otherwise we sometimes get a black image?!
	    	// also need a longer delay for continuous picture focus, to allow a chance to focus - 1000ms seems to work okay for Nexus 6, put 1500ms to be safe
	    	String focus_value = getCurrentFocusValue();
			final int delay = ( focus_value != null && focus_value.equals("focus_mode_continuous_picture") ) ? 1500 : 500;

	    	final Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					takePicture(false);
				}
			}, delay);
		}

	    if( do_startup_focus ) {
	    	final Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					tryAutoFocus(true, false); // so we get the autofocus when starting up - we do this on a delay, as calling it immediately means the autofocus doesn't seem to work properly sometimes (at least on Galaxy Nexus)
				}
			}, 500);
	    }

	}

	private void setupCameraParameters() {

		long debug_time = 0;

			camera_controller.setSceneMode("auto");
		
		{
			// grab all read-only info from parameters
			CameraController.CameraFeatures camera_features = camera_controller.getCameraFeatures();

			this.minimum_focus_distance = camera_features.minimum_focus_distance;
			this.sizes = camera_features.picture_sizes;
	        supported_flash_values = camera_features.supported_flash_values;
	        supported_focus_values = camera_features.supported_focus_values;
	        this.is_exposure_lock_supported = camera_features.is_exposure_lock_supported;
	        this.can_disable_shutter_sound = camera_features.can_disable_shutter_sound;
	        this.supports_iso_range = camera_features.supports_iso_range;
	        this.min_iso = camera_features.min_iso;
	        this.max_iso = camera_features.max_iso;
	        this.supports_exposure_time = camera_features.supports_exposure_time;
	        this.min_exposure_time = camera_features.min_exposure_time;
	        this.max_exposure_time = camera_features.max_exposure_time;
			this.min_exposure = camera_features.min_exposure;
			this.max_exposure = camera_features.max_exposure;
			this.exposure_step = camera_features.exposure_step;
	        this.supported_preview_sizes = camera_features.preview_sizes;
		}

			camera_controller.setColorEffect("none");

		camera_controller.setWhiteBalance("auto");

			camera_controller.setISO("auto");

		{
			// get min/max exposure
			if( min_exposure != 0 || max_exposure != 0 ) {

				// if in manual ISO mode, we still want to get the valid exposure compensations, but shouldn't set exposure compensation

					camera_controller.setExposureCompensation(0);
		    		// now save, so it's available for PreferenceActivity
					applicationInterface.setExposureCompensationPref(0);

			}
			else {
				// delete key in case it's present (e.g., if feature no longer available due to change in OS, or switching APIs)
				applicationInterface.clearExposureCompensationPref();
			}
		}


		{

			current_size_index = -1;
			Pair<Integer, Integer> resolution = applicationInterface.getCameraResolutionPref();
			if( resolution != null ) {
				int resolution_w = resolution.first;
				int resolution_h = resolution.second;
				// now find size in valid list
				for(int i=0;i<sizes.size() && current_size_index==-1;i++) {
					CameraController.Size size = sizes.get(i);
		        	if( size.width == resolution_w && size.height == resolution_h ) {
		        		current_size_index = i;
		        	}
				}
			}

			if( current_size_index == -1 ) {
				// set to largest
				CameraController.Size current_size = null;
				for(int i=0;i<sizes.size();i++) {
					CameraController.Size size = sizes.get(i);
		        	if( current_size == null || size.width*size.height > current_size.width*current_size.height ) {
		        		current_size_index = i;
		        		current_size = size;
		        	}
		        }
			}
			if( current_size_index != -1 ) {
				CameraController.Size current_size = sizes.get(current_size_index);

	    		// now save, so it's available for PreferenceActivity
	    		applicationInterface.setCameraResolutionPref(current_size.width, current_size.height);
			}
			// size set later in setPreviewSize()
		}

		{
			camera_controller.setJpegQuality(90);
		}

		{
			current_flash_index = -1;
			if( supported_flash_values != null && supported_flash_values.size() > 1 ) {

				String flash_value = applicationInterface.getFlashPref();
				if( flash_value.length() > 0 ) {
					if( !updateFlash(flash_value, false) ) { // don't need to save, as this is the value that's already saved
						updateFlash(0, true);
					}
				}
				else {
					updateFlash("flash_auto", true);
				}
			}
			else {
				supported_flash_values = null;
			}
		}

		{
			current_focus_index = -1;
			if( supported_focus_values != null && supported_focus_values.size() > 1 ) {

				setFocusPref(true);
			}
			else {
				supported_focus_values = null;
			}
		}

		{
			float focus_distance_value = applicationInterface.getFocusDistancePref();
			if( focus_distance_value < 0.0f )
				focus_distance_value = 0.0f;
			else if( focus_distance_value > minimum_focus_distance )
				focus_distance_value = minimum_focus_distance;
			camera_controller.setFocusDistance(focus_distance_value);
			// now save
			applicationInterface.setFocusDistancePref(focus_distance_value);
		}

		{
	    	is_exposure_locked = false;
		}

	}
	
	private void setPreviewSize() {
		// also now sets picture size
		if( camera_controller == null ) {
			return;
		}
		if( is_preview_started ) {
			throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
		}
		if( !using_android_l ) {
			// don't do for Android L, else this means we get flash on startup autofocus if flash is on
			this.cancelAutoFocus();
		}
		// first set picture size (for photo mode, must be done now so we can set the picture size from this; for video, doesn't really matter when we set it)
		CameraController.Size new_size = null;

    		if( current_size_index != -1 ) {
    			new_size = sizes.get(current_size_index);
    		}

    	if( new_size != null ) {
    		camera_controller.setPictureSize(new_size.width, new_size.height);
    	}
		// set optimal preview size
        if( supported_preview_sizes != null && supported_preview_sizes.size() > 0 ) {

        	CameraController.Size best_size = getOptimalPreviewSize(supported_preview_sizes);
        	camera_controller.setPreviewSize(best_size.width, best_size.height);
        	this.set_preview_size = true;
        	this.preview_w = best_size.width;
        	this.preview_h = best_size.height;
    		this.setAspectRatio( ((double)best_size.width) / (double)best_size.height );
        }
	}

	private CamcorderProfile getCamcorderProfile(String quality) {
		if( camera_controller == null ) {
			return CamcorderProfile.get(0, CamcorderProfile.QUALITY_HIGH);
		}
		int cameraId = camera_controller.getCameraId();
		CamcorderProfile camcorder_profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH); // default
		try {
			String profile_string = quality;
			int index = profile_string.indexOf('_');
			if( index != -1 ) {
				profile_string = quality.substring(0, index);
			}
			int profile = Integer.parseInt(profile_string);
			camcorder_profile = CamcorderProfile.get(cameraId, profile);
			if( index != -1 && index+1 < quality.length() ) {
				String override_string = quality.substring(index+1);
				if( override_string.charAt(0) == 'r' && override_string.length() >= 4 ) {
					index = override_string.indexOf('x');
					if( index == -1 ) {
					}
					else {
						String resolution_w_s = override_string.substring(1, index); // skip first 'r'
						String resolution_h_s = override_string.substring(index+1);

						// copy to local variable first, so that if we fail to parse height, we don't set the width either
						int resolution_w = Integer.parseInt(resolution_w_s);
						int resolution_h = Integer.parseInt(resolution_h_s);
						camcorder_profile.videoFrameWidth = resolution_w;
						camcorder_profile.videoFrameHeight = resolution_h;
					}
				}
			}
		}
        catch(NumberFormatException e) {
    		e.printStackTrace();
        }
		return camcorder_profile;
	}
	
	public CamcorderProfile getCamcorderProfile() {
		// 4K UHD video is not yet supported by Android API (at least testing on Samsung S5 and Note 3, they do not return it via getSupportedVideoSizes(), nor via a CamcorderProfile (either QUALITY_HIGH, or anything else)
		// but it does work if we explicitly set the resolution (at least tested on an S5)
		if( camera_controller == null ) {
			return CamcorderProfile.get(0, CamcorderProfile.QUALITY_HIGH);
		}
		CamcorderProfile profile = null;
		int cameraId = camera_controller.getCameraId();
		if( applicationInterface.getForce4KPref() ) {
			profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
			profile.videoFrameWidth = 3840;
			profile.videoFrameHeight = 2160;
			profile.videoBitRate = (int)(profile.videoBitRate*2.8); // need a higher bitrate for the better quality - this is roughly based on the bitrate used by an S5's native camera app at 4K (47.6 Mbps, compared to 16.9 Mbps which is what's returned by the QUALITY_HIGH profile)
		}
		else if( current_video_quality != -1 ) {
			profile = getCamcorderProfile(video_quality.get(current_video_quality));
		}
		else {
			profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
		}

		String bitrate_value = applicationInterface.getVideoBitratePref();
		if( !bitrate_value.equals("default") ) {
			try {
				int bitrate = Integer.parseInt(bitrate_value);
				profile.videoBitRate = bitrate;
			}
			catch(NumberFormatException exception) {
			}
		}
		String fps_value = applicationInterface.getVideoFPSPref();
		if( !fps_value.equals("default") ) {
			try {
				int fps = Integer.parseInt(fps_value);
				profile.videoFrameRate = fps;
			}
			catch(NumberFormatException exception) {
			}
		}		
		return profile;
	}
	
	private static String formatFloatToString(final float f) {
		final int i=(int)f;
		if( f == i )
			return Integer.toString(i);
		return String.format(Locale.getDefault(), "%.2f", f);
	}

	private static int greatestCommonFactor(int a, int b) {
	    while( b > 0 ) {
	        int temp = b;
	        b = a % b;
	        a = temp;
	    }
	    return a;
	}
	
	private static String getAspectRatio(int width, int height) {
		int gcf = greatestCommonFactor(width, height);
		if( gcf > 0 ) {
			// had a Google Play crash due to gcf being 0!? Implies width must be zero
			width /= gcf;
			height /= gcf;
		}
		return width + ":" + height;
	}
	
	public static String getMPString(int width, int height) {
		float mp = (width*height)/1000000.0f;
		return formatFloatToString(mp) + "MP";
	}
	
	public static String getAspectRatioMPString(int width, int height) {
		return "(" + getAspectRatio(width, height) + ", " + getMPString(width, height) + ")";
	}

	public double getTargetRatio() {
		return preview_targetRatio;
	}

	private double calculateTargetRatioForPreview(Point display_size) {
        double targetRatio = 0.0f;
		String preview_size = applicationInterface.getPreviewSizePref();
		// should always use wysiwig for video mode, otherwise we get incorrect aspect ratio shown when recording video (at least on Galaxy Nexus, e.g., at 640x480)
		// also not using wysiwyg mode with video caused corruption on Samsung cameras (tested with Samsung S3, Android 4.3, front camera, infinity focus)
		if( preview_size.equals("preference_preview_size_wysiwyg")) {

	        	CameraController.Size picture_size = camera_controller.getPictureSize();
	        	targetRatio = ((double)picture_size.width) / (double)picture_size.height;

		}
		else {
        	// base target ratio from display size - means preview will fill the device's display as much as possible
        	// but if the preview's aspect ratio differs from the actual photo/video size, the preview will show a cropped version of what is actually taken
            targetRatio = ((double)display_size.x) / (double)display_size.y;
		}
		this.preview_targetRatio = targetRatio;
		return targetRatio;
	}

	public CameraController.Size getClosestSize(List<CameraController.Size> sizes, double targetRatio) {
		CameraController.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        for(CameraController.Size size : sizes) {
            double ratio = (double)size.width / size.height;
            if( Math.abs(ratio - targetRatio) < minDiff ) {
                optimalSize = size;
                minDiff = Math.abs(ratio - targetRatio);
            }
        }
        return optimalSize;
	}

	public CameraController.Size getOptimalPreviewSize(List<CameraController.Size> sizes) {
		final double ASPECT_TOLERANCE = 0.05;
        if( sizes == null )
        	return null;
        CameraController.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        Point display_size = new Point();
		Activity activity = (Activity)this.getContext();
        {
            Display display = activity.getWindowManager().getDefaultDisplay();
            display.getSize(display_size);
        }
        double targetRatio = calculateTargetRatioForPreview(display_size);
        int targetHeight = Math.min(display_size.y, display_size.x);
        if( targetHeight <= 0 ) {
            targetHeight = display_size.y;
        }
        // Try to find the size which matches the aspect ratio, and is closest match to display height
        for(CameraController.Size size : sizes) {
            double ratio = (double)size.width / size.height;
            if( Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE )
            	continue;
            if( Math.abs(size.height - targetHeight) < minDiff ) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        if( optimalSize == null ) {
        	// can't find match for aspect ratio, so find closest one
    		optimalSize = getClosestSize(sizes, targetRatio);
        }
        return optimalSize;
    }

    private void setAspectRatio(double ratio) {
        if( ratio <= 0.0 )
        	throw new IllegalArgumentException();

        has_aspect_ratio = true;
        if( aspect_ratio != ratio ) {
        	aspect_ratio = ratio;
    		cameraSurface.getView().requestLayout();
    		if( canvasView != null ) {
    			canvasView.requestLayout();
    		}
        }
    }
    
    private boolean hasAspectRatio() {
    	return has_aspect_ratio;
    }

    private double getAspectRatio() {
    	return aspect_ratio;
    }

    public int getDisplayRotation() {
    	// gets the display rotation (as a Surface.ROTATION_* constant), taking into account the getRotatePreviewPreferenceKey() setting
		Activity activity = (Activity)this.getContext();
	    int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

		String rotate_preview = applicationInterface.getPreviewRotationPref();
		if( rotate_preview.equals("180") ) {
		    switch (rotation) {
		    	case Surface.ROTATION_0: rotation = Surface.ROTATION_180; break;
		    	case Surface.ROTATION_90: rotation = Surface.ROTATION_270; break;
		    	case Surface.ROTATION_180: rotation = Surface.ROTATION_0; break;
		    	case Surface.ROTATION_270: rotation = Surface.ROTATION_90; break;
	    		default:
	    			break;
		    }
		}

		return rotation;
    }
    
    // for the Preview - from http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
	// note, if orientation is locked to landscape this is only called when setting up the activity, and will always have the same orientation
	public void setCameraDisplayOrientation() {
		if( camera_controller == null ) {
			return;
		}
	    if( using_android_l ) {
	    	// need to configure the textureview
			configureTransform();
	    }
	    else {
		    int rotation = getDisplayRotation();
		    int degrees = 0;
		    switch (rotation) {
		    	case Surface.ROTATION_0: degrees = 0; break;
		        case Surface.ROTATION_90: degrees = 90; break;
		        case Surface.ROTATION_180: degrees = 180; break;
		        case Surface.ROTATION_270: degrees = 270; break;
	    		default:
	    			break;
		    }

			camera_controller.setDisplayOrientation(degrees);
	    }
	}
	
	// for taking photos - from http://developer.android.com/reference/android/hardware/Camera.Parameters.html#setRotation(int)
	private void onOrientationChanged(int orientation) {

		if( orientation == OrientationEventListener.ORIENTATION_UNKNOWN )
			return;
		if( camera_controller == null ) {
			return;
		}
	    orientation = (orientation + 45) / 90 * 90;
	    this.current_orientation = orientation % 360;
	    int new_rotation = 0;
	    int camera_orientation = camera_controller.getCameraOrientation();
	    if( camera_controller.isFrontFacing() ) {
	    	new_rotation = (camera_orientation - orientation + 360) % 360;
	    }
	    else {
	    	new_rotation = (camera_orientation + orientation) % 360;
	    }
	    if( new_rotation != current_rotation ) {
	    	this.current_rotation = new_rotation;
	    }
	}

	private int getDeviceDefaultOrientation() {
	    WindowManager windowManager = (WindowManager)this.getContext().getSystemService(Context.WINDOW_SERVICE);
	    Configuration config = getResources().getConfiguration();
	    int rotation = windowManager.getDefaultDisplay().getRotation();
	    if( ( (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) &&
	    		config.orientation == Configuration.ORIENTATION_PORTRAIT)
	    		|| ( (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) &&    
	            config.orientation == Configuration.ORIENTATION_PORTRAIT ) ) {
	    	return Configuration.ORIENTATION_LANDSCAPE;
	    }
	    else { 
	    	return Configuration.ORIENTATION_PORTRAIT;
	    }
	}

	/* Returns the rotation (in degrees) to use for images/videos, taking the preference_lock_orientation into account.
	 */
	private int getImageVideoRotation() {
		String lock_orientation = applicationInterface.getLockOrientationPref();
		if( lock_orientation.equals("landscape") ) {
			int camera_orientation = camera_controller.getCameraOrientation();
		    int device_orientation = getDeviceDefaultOrientation();
		    int result = 0;
		    if( device_orientation == Configuration.ORIENTATION_PORTRAIT ) {
		    	// should be equivalent to onOrientationChanged(270)
			    if( camera_controller.isFrontFacing() ) {
			    	result = (camera_orientation + 90) % 360;
			    }
			    else {
			    	result = (camera_orientation + 270) % 360;
			    }
		    }
		    else {
		    	// should be equivalent to onOrientationChanged(0)
		    	result = camera_orientation;
		    }
		    return result;
		}
		else if( lock_orientation.equals("portrait") ) {
			int camera_orientation = camera_controller.getCameraOrientation();
		    int result = 0;
		    int device_orientation = getDeviceDefaultOrientation();
		    if( device_orientation == Configuration.ORIENTATION_PORTRAIT ) {
		    	// should be equivalent to onOrientationChanged(0)
		    	result = camera_orientation;
		    }
		    else {
		    	// should be equivalent to onOrientationChanged(90)
			    if( camera_controller.isFrontFacing() ) {
			    	result = (camera_orientation + 270) % 360;
			    }
			    else {
			    	result = (camera_orientation + 90) % 360;
			    }
		    }
		    return result;
		}
		return this.current_rotation;
	}

	public void draw(Canvas canvas) {
		if( this.app_is_paused ) {
			return;
		}

		if( this.focus_success != FOCUS_DONE ) {
			if( focus_complete_time != -1 && System.currentTimeMillis() > focus_complete_time + 1000 ) {
				focus_success = FOCUS_DONE;
			}
		}
		applicationInterface.onDrawPreview(canvas);
	}
	public String getISOString(int iso) {
		return getResources().getString(R.string.iso) + " " + iso;
	}

	public String getExposureTimeString(long exposure_time) {
		double exposure_time_s = exposure_time/1000000000.0;
		double exposure_time_r = 1.0/exposure_time_s;
		return " 1/" + decimal_format_1dp.format(exposure_time_r);
	}

	public int [] chooseBestPreviewFps(List<int []> fps_ranges) {

		// find value with lowest min that has max >= 30; if more than one of these, pick the one with highest max
		int selected_min_fps = -1, selected_max_fps = -1;
        for(int [] fps_range : fps_ranges) {
			int min_fps = fps_range[0];
			int max_fps = fps_range[1];
			if( max_fps >= 30000 ) {
				if( selected_min_fps == -1 || min_fps < selected_min_fps ) {
    				selected_min_fps = min_fps;
    				selected_max_fps = max_fps;
				}
				else if( min_fps == selected_min_fps && max_fps > selected_max_fps ) {
    				selected_min_fps = min_fps;
    				selected_max_fps = max_fps;
				}
			}
        }

        if( selected_min_fps != -1 ) {
        }
        else {
        	// just pick the widest range; if more than one, pick the one with highest max
        	int selected_diff = -1;
            for(int [] fps_range : fps_ranges) {
    			int min_fps = fps_range[0];
    			int max_fps = fps_range[1];
    			int diff = max_fps - min_fps;
    			if( selected_diff == -1 || diff > selected_diff ) {
    				selected_min_fps = min_fps;
    				selected_max_fps = max_fps;
    				selected_diff = diff;
    			}
    			else if( diff == selected_diff && max_fps > selected_max_fps ) {
    				selected_min_fps = min_fps;
    				selected_max_fps = max_fps;
    				selected_diff = diff;
    			}
            }
        }
    	return new int[]{selected_min_fps, selected_max_fps};
	}

	/* It's important to set a preview FPS using chooseBestPreviewFps() rather than just leaving it to the default, as some devices
	 * have a poor choice of default - e.g., Nexus 5 and Nexus 6 on original Camera API default to (15000, 15000), which means very dark
	 * preview and photos in low light, as well as a less smooth framerate in good light.
	 * See http://stackoverflow.com/questions/18882461/why-is-the-default-android-camera-preview-smoother-than-my-own-camera-preview .
	 */
	private void setPreviewFps() {
		CamcorderProfile profile = getCamcorderProfile();
		List<int []> fps_ranges = camera_controller.getSupportedPreviewFpsRange();
		if( fps_ranges == null || fps_ranges.size() == 0 ) {
			return;
		}
		int [] selected_fps = null;

			// note that setting an fps here in continuous video focus mode causes preview to not restart after taking a photo on Galaxy Nexus
			// but we need to do this, to get good light for Nexus 5 or 6
			// we could hardcode behaviour like we do for video, but this is the same way that Google Camera chooses preview fps for photos
			// or I could hardcode behaviour for Galaxy Nexus, but since it's an old device (and an obscure bug anyway - most users don't really need continuous focus in photo mode), better to live with the bug rather than complicating the code
			// Update for v1.29: this doesn't seem to happen on Galaxy Nexus with continuous picture focus mode, which is what we now use
			// Update for v1.31: we no longer seem to need this - I no longer get a dark preview in photo or video mode if we don't set the fps range;
			// but leaving the code as it is, to be safe.
			selected_fps = chooseBestPreviewFps(fps_ranges);

        camera_controller.setPreviewFpsRange(selected_fps[0], selected_fps[1]);
	}
	
	private void setFocusPref(boolean auto_focus) {
		String focus_value = applicationInterface.getFocusPref(false);
		if( focus_value.length() > 0 ) {
			if( !updateFocus(focus_value, true, false, auto_focus) ) { // don't need to save, as this is the value that's already saved
				updateFocus(0, true, true, auto_focus);
			}
		}
		else {
			updateFocus("focus_mode_auto", true, true, auto_focus);
		}
	}

	private boolean updateFlash(String flash_value, boolean save) {
		if( supported_flash_values != null ) {
	    	int new_flash_index = supported_flash_values.indexOf(flash_value);
	    	if( new_flash_index != -1 ) {
	    		updateFlash(new_flash_index, save);
	    		return true;
	    	}
		}
    	return false;
	}
	
	private void updateFlash(int new_flash_index, boolean save) {
		// updates the Flash button, and Flash camera mode
		if( supported_flash_values != null && new_flash_index != current_flash_index ) {
			boolean initial = current_flash_index==-1;
			current_flash_index = new_flash_index;

	    	String [] flash_entries = getResources().getStringArray(R.array.flash_entries);
			String flash_value = supported_flash_values.get(current_flash_index);
	    	String [] flash_values = getResources().getStringArray(R.array.flash_values);
	    	for(int i=0;i<flash_values.length;i++) {
	    		if( flash_value.equals(flash_values[i]) ) {
	    			if( !initial ) {
	    				showToast(flash_toast, flash_entries[i]);
	    			}
	    			break;
	    		}
	    	}
	    	this.setFlash(flash_value);
	    	if( save ) {
				// now save
	    		applicationInterface.setFlashPref(flash_value);
	    	}
		}
	}

	private void setFlash(String flash_value) {
		set_flash_value_after_autofocus = ""; // this overrides any previously saved setting, for during the startup autofocus
		if( camera_controller == null ) {
			return;
		}
		cancelAutoFocus();
        camera_controller.setFlashValue(flash_value);
	}

	// this returns the flash value indicated by the UI, rather than from the camera parameters (may be different, e.g., in startup autofocus!)
    public String getCurrentFlashValue() {
    	if( this.current_flash_index == -1 )
    		return null;
    	return this.supported_flash_values.get(current_flash_index);
    }

	public void updateFocus(String focus_value, boolean quiet, boolean auto_focus) {
		if( this.phase == PHASE_TAKING_PHOTO ) {
			// just to be safe - otherwise problem that changing the focus mode will cancel the autofocus before taking a photo, so we never take a photo, but is_taking_photo remains true!
			return;
		}
		updateFocus(focus_value, quiet, true, auto_focus);
	}

	private boolean supportedFocusValue(String focus_value) {
		if( this.supported_focus_values != null ) {
	    	int new_focus_index = supported_focus_values.indexOf(focus_value);
	    	return new_focus_index != -1;
		}
		return false;
	}

	private boolean updateFocus(String focus_value, boolean quiet, boolean save, boolean auto_focus) {
		if( this.supported_focus_values != null ) {
	    	int new_focus_index = supported_focus_values.indexOf(focus_value);
	    	if( new_focus_index != -1 ) {
	    		updateFocus(new_focus_index, quiet, save, auto_focus);
	    		return true;
	    	}
		}
    	return false;
	}

	private String findEntryForValue(String value, int entries_id, int values_id) {
    	String [] entries = getResources().getStringArray(entries_id);
    	String [] values = getResources().getStringArray(values_id);
    	for(int i=0;i<values.length;i++) {
    		if( value.equals(values[i]) ) {
				return entries[i];
    		}
    	}
    	return null;
	}
	
	public String findFocusEntryForValue(String focus_value) {
		return findEntryForValue(focus_value, R.array.focus_mode_entries, R.array.focus_mode_values);
	}
	
	private void updateFocus(int new_focus_index, boolean quiet, boolean save, boolean auto_focus) {
		// updates the Focus button, and Focus camera mode
		if( this.supported_focus_values != null && new_focus_index != current_focus_index ) {
			current_focus_index = new_focus_index;

			String focus_value = supported_focus_values.get(current_focus_index);
			if( !quiet ) {
				String focus_entry = findFocusEntryForValue(focus_value);
				if( focus_entry != null ) {
    				showToast(focus_toast, focus_entry);
				}
			}
	    	this.setFocusValue(focus_value, auto_focus);

	    	if( save ) {
				// now save
	    		applicationInterface.setFocusPref(focus_value, false);
	    	}
		}
	}
	
	/** This returns the flash mode indicated by the UI, rather than from the camera parameters.
	 */
	public String getCurrentFocusValue() {
		if( camera_controller == null ) {
			return null;
		}
		if( this.supported_focus_values != null && this.current_focus_index != -1 )
			return this.supported_focus_values.get(current_focus_index);
		return null;
	}

	private void setFocusValue(String focus_value, boolean auto_focus) {
		if( camera_controller == null ) {
			return;
		}
		cancelAutoFocus();
		removePendingContinuousFocusReset(); // this isn't strictly needed as the reset_continuous_focus_runnable will check the ui focus mode when it runs, but good to remove it anyway
		autofocus_in_continuous_mode = false;
        camera_controller.setFocusValue(focus_value);
		setupContinuousFocusMove();
		clearFocusAreas();
		if( auto_focus && !focus_value.equals("focus_mode_locked") ) {
			tryAutoFocus(false, false);
		}
	}
	
	private void setupContinuousFocusMove() {
		if( continuous_focus_move_is_started ) {
			continuous_focus_move_is_started = false;
			applicationInterface.onContinuousFocusMove(false);
		}
		String focus_value = current_focus_index != -1 ? supported_focus_values.get(current_focus_index) : null;
		if( camera_controller != null && focus_value != null && focus_value.equals("focus_mode_continuous_picture") ) {
			camera_controller.setContinuousFocusMoveCallback(new CameraController.ContinuousFocusMoveCallback() {
				@Override
				public void onContinuousFocusMove(boolean start) {
					if( start != continuous_focus_move_is_started ) { // filter out repeated calls with same start value
						continuous_focus_move_is_started = start;
						count_cameraContinuousFocusMoving++;
						applicationInterface.onContinuousFocusMove(start);
					}
				}
			});
		}
		else if( camera_controller != null ) {
			camera_controller.setContinuousFocusMoveCallback(null);
		}
	}

	public void toggleExposureLock() {
		// n.b., need to allow when recording video, so no check on PHASE_TAKING_PHOTO
		if( camera_controller == null ) {
			return;
		}
		if( is_exposure_lock_supported ) {
			is_exposure_locked = !is_exposure_locked;
			cancelAutoFocus();
	        camera_controller.setAutoExposureLock(is_exposure_locked);
		}
	}

	/** User has clicked the "take picture" button (or equivalent GUI operation).
	 */
	public void takePicturePressed() {
		if( camera_controller == null ) {
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
			this.phase = PHASE_NORMAL;
			return;
		}
		if( !this.has_surface ) {
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
			this.phase = PHASE_NORMAL;
			return;
		}
    	//if( is_taking_photo ) {
		if( this.phase == PHASE_TAKING_PHOTO ) {
    			if( remaining_burst_photos != 0 ) {
    				remaining_burst_photos = 0;
    			    showToast(take_photo_toast, R.string.cancelled_burst_mode);
    			}

    		return;
    	}

    	// make sure that preview running (also needed to hide trash/share icons)
        this.startCameraPreview();

        //is_taking_photo = true;
		long timer_delay = applicationInterface.getTimerPref();

		String burst_mode_value = applicationInterface.getRepeatPref();
		int n_burst = 1;
		if( burst_mode_value.equals("unlimited") ) {
			remaining_burst_photos = -1;
		}
		else {
			try {
				n_burst = Integer.parseInt(burst_mode_value);
			}
	        catch(NumberFormatException e) {
	    		e.printStackTrace();
	    		n_burst = 1;
	        }
			remaining_burst_photos = n_burst-1;
		}
		
		if( timer_delay == 0 ) {
			takePicture(false);
		}
	}

	/** Initiate "take picture" command. In video mode this means starting video command. In photo mode this may involve first
	 * autofocusing.
	 */
	private void takePicture(boolean max_filesize_restart) {
		//this.thumbnail_anim = false;
        this.phase = PHASE_TAKING_PHOTO;
		synchronized( this ) {
			// synchronise for consistency (keep FindBugs happy)
			take_photo_after_autofocus = false;
		}
		if( camera_controller == null ) {
			this.phase = PHASE_NORMAL;
			applicationInterface.cameraInOperation(false);
			return;
		}
		if( !this.has_surface ) {
			this.phase = PHASE_NORMAL;
			applicationInterface.cameraInOperation(false);
			return;
		}

		boolean store_location = applicationInterface.getGeotaggingPref();
		if( store_location ) {
			boolean require_location = applicationInterface.getRequireLocationPref();
			if( require_location ) {
				if( applicationInterface.getLocation() != null ) {
					// fine, we have location
				}
				else {
		    	    showToast(null, R.string.location_not_available);
					this.phase = PHASE_NORMAL;
					applicationInterface.cameraInOperation(false);
		    	    return;
				}
			}
		}

		takePhoto(false);
	}

	/** Take photo. The caller should aready have set the phase to PHASE_TAKING_PHOTO.
	 */
	private void takePhoto(boolean skip_autofocus) {
		applicationInterface.cameraInOperation(true);
        String current_ui_focus_value = getCurrentFocusValue();

		if( autofocus_in_continuous_mode ) {
			synchronized(this) {
				// as below, if an autofocus is in progress, then take photo when it's completed
				if( focus_success == FOCUS_WAITING ) {
					take_photo_after_autofocus = true;
				}
				else {
					// when autofocus_in_continuous_mode==true, it means the user recently touched to focus in continuous focus mode, so don't do another focus
					takePhotoWhenFocused();
				}
			}
		}
		else if( camera_controller.focusIsContinuous() ) {
			// we call via autoFocus(), to avoid risk of taking photo while the continuous focus is focusing - risk of blurred photo, also sometimes get bug in such situations where we end of repeatedly focusing
			// this is the case even if skip_autofocus is true (as we still can't guarantee that continuous focusing might be occurring)
			// note: if the user touches to focus in continuous mode, we camera controller may be in auto focus mode, so we should only enter this codepath if the camera_controller is in continuous focus mode
	        CameraController.AutoFocusCallback autoFocusCallback = new CameraController.AutoFocusCallback() {
				@Override
				public void onAutoFocus(boolean success) {
					takePhotoWhenFocused();
				}
	        };
			camera_controller.autoFocus(autoFocusCallback);
		}
		else if( skip_autofocus || this.recentlyFocused() ) {
			takePhotoWhenFocused();
		}
		else if( current_ui_focus_value != null && ( current_ui_focus_value.equals("focus_mode_auto") || current_ui_focus_value.equals("focus_mode_macro") ) ) {
			// n.b., we check focus_value rather than camera_controller.supportsAutoFocus(), as we want to discount focus_mode_locked
			synchronized(this) {
				if( focus_success == FOCUS_WAITING ) {
					// Needed to fix bug (on Nexus 6, old camera API): if flash was on, pointing at a dark scene, and we take photo when already autofocusing, the autofocus never returned so we got stuck!
					// In general, probably a good idea to not redo a focus - just use the one that's already in progress
					take_photo_after_autofocus = true;
				}
				else {
					focus_success = FOCUS_DONE; // clear focus rectangle for new refocus
			        CameraController.AutoFocusCallback autoFocusCallback = new CameraController.AutoFocusCallback() {
						@Override
						public void onAutoFocus(boolean success) {
							ensureFlashCorrect(); // need to call this in case user takes picture before startup focus completes!
							prepareAutoFocusPhoto();
							takePhotoWhenFocused();
						}
			        };
					camera_controller.autoFocus(autoFocusCallback);
					count_cameraAutoFocus++;
				}
			}
		}
		else {
			takePhotoWhenFocused();
		}
	}
	
	/** Should be called when taking a photo immediately after an autofocus.
	 *  This is needed for a workaround for Camera2 bug (at least on Nexus 6) where photos sometimes come out dark when using flash
	 *  auto, when the flash fires. This happens when taking a photo in autofocus mode (including when continuous mode has
	 *  transitioned to autofocus mode due to touching to focus). Seems to happen with scenes that have bright and dark regions,
	 *  i.e., on verge of flash firing.
	 *  Seems to be fixed if we have a short delay...
	 */
	private void prepareAutoFocusPhoto() {
		if( using_android_l ) {
			String flash_value = camera_controller.getFlashValue();
			// getFlashValue() may return "" if flash not supported!
			if( flash_value.length() > 0 && ( flash_value.equals("flash_auto") || flash_value.equals("flash_red_eye") ) ) {
				try {
					Thread.sleep(100);
				}
				catch(InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/** Take photo, assumes any autofocus has already been taken care of, and that applicationInterface.cameraInOperation(true) has
	 *  already been called.
	 *  Note that even if a caller wants to take a photo without focusing, you probably want to call takePhoto() with skip_autofocus
	 *  set to true (so that things work okay in continuous picture focus mode).
	 */
	private void takePhotoWhenFocused() {
		// should be called when auto-focused
		if( camera_controller == null ) {
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
			this.phase = PHASE_NORMAL;
			applicationInterface.cameraInOperation(false);
			return;
		}
		if( !this.has_surface ) {
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
			this.phase = PHASE_NORMAL;
			applicationInterface.cameraInOperation(false);
			return;
		}

		final String focus_value = current_focus_index != -1 ? supported_focus_values.get(current_focus_index) : null;

		if( focus_value != null && focus_value.equals("focus_mode_locked") && focus_success == FOCUS_WAITING ) {
			// make sure there isn't an autofocus in progress - can happen if in locked mode we take a photo while autofocusing - see testTakePhotoLockedFocus() (although that test doesn't always properly test the bug...)
			// we only cancel when in locked mode and if still focusing, as I had 2 bug reports for v1.16 that the photo was being taken out of focus; both reports said it worked fine in 1.15, and one confirmed that it was due to the cancelAutoFocus() line, and that it's now fixed with this fix
			// they said this happened in every focus mode, including locked - so possible that on some devices, cancelAutoFocus() actually pulls the camera out of focus, or reverts to preview focus?
			cancelAutoFocus();
		}
		removePendingContinuousFocusReset(); // to avoid switching back to continuous focus mode while taking a photo - instead we'll always make sure we switch back after taking a photo
		updateParametersFromLocation(); // do this now, not before, so we don't set location parameters during focus (sometimes get RuntimeException)

		focus_success = FOCUS_DONE; // clear focus rectangle if not already done
		successfully_focused = false; // so next photo taken will require an autofocus

		CameraController.PictureCallback pictureCallback = new CameraController.PictureCallback() {
			private boolean success = false; // whether jpeg callback succeeded
			private boolean has_date = false;
			private Date current_date = null;

			public void onCompleted() {
				applicationInterface.onPictureCompleted();
    	        if( !using_android_l ) {
    	        	is_preview_started = false; // preview automatically stopped due to taking photo on original Camera API
    	        }
    	        phase = PHASE_NORMAL; // need to set this even if remaining burst photos, so we can restart the preview
    	        if( remaining_burst_photos == -1 || remaining_burst_photos > 0 ) {
    	        	if( !is_preview_started ) {
    	    	    	// we need to restart the preview; and we do this in the callback, as we need to restart after saving the image
    	    	    	// (otherwise this can fail, at least on Nexus 7)
    		            startCameraPreview();
    	        	}
    	        }
    	        else {
    		        phase = PHASE_NORMAL;
    				boolean pause_preview = applicationInterface.getPausePreviewPref();
    				if( pause_preview && success ) {
    					if( is_preview_started ) {
    						// need to manually stop preview on Android L Camera2
    						camera_controller.stopPreview();
    						is_preview_started = false;
    					}
    	    			setPreviewPaused(true);
    				}
    				else {
    	            	if( !is_preview_started ) {
    		    	    	// we need to restart the preview; and we do this in the callback, as we need to restart after saving the image
    		    	    	// (otherwise this can fail, at least on Nexus 7)
    			            startCameraPreview();
    	            	}
    	        		applicationInterface.cameraInOperation(false);
    				}
    	        }
				continuousFocusReset(); // in case we took a photo after user had touched to focus (causing us to switch from continuous to autofocus mode)
    			if( camera_controller != null && focus_value != null && ( focus_value.equals("focus_mode_continuous_picture") || focus_value.equals("focus_mode_continuous_video") ) ) {
    				camera_controller.cancelAutoFocus(); // needed to restart continuous focusing
    			}
    	        if( remaining_burst_photos == -1 || remaining_burst_photos > 0 ) {
    	        	if( remaining_burst_photos > 0 )
    	        		remaining_burst_photos--;

    	    		long timer_delay = applicationInterface.getRepeatIntervalPref();
    	    		if( timer_delay == 0 ) {
    	    			// we set skip_autofocus to go straight to taking a photo rather than refocusing, for speed
    	    			// need to manually set the phase
    	    	        phase = PHASE_TAKING_PHOTO;
    	        		takePhoto(true);
    	    		}
    	        }
			}

			/** Ensures we get the same date for both JPEG and RAW; and that we set the date ASAP so that it corresponds to actual
			 *  photo time.
			 */
			private void initDate() {
				if( !has_date ) {
					has_date = true;
					current_date = new Date();
				}
			}
			
			public void onPictureTaken(byte[] data) {
    	    	// n.b., this is automatically run in a different thread
				initDate();
				if( !applicationInterface.onPictureTaken(data, current_date) ) {
					success = false;
				}
				else {
					success = true;
				}
    	    }

    	};
		CameraController.ErrorCallback errorCallback = new CameraController.ErrorCallback() {
			public void onError() {
        		count_cameraTakePicture--; // cancel out the increment from after the takePicture() call
	            applicationInterface.onPhotoError();
				phase = PHASE_NORMAL;
	            startCameraPreview();
	    		applicationInterface.cameraInOperation(false);
    	    }
		};
    	{
    		camera_controller.setRotation(getImageVideoRotation());

			boolean enable_sound = applicationInterface.getShutterSoundPref();
        	camera_controller.enableShutterSound(enable_sound);
			camera_controller.takePicture(pictureCallback, errorCallback);
    		count_cameraTakePicture++;
    	}
    }

    private void tryAutoFocus(final boolean startup, final boolean manual) {
    	// manual: whether user has requested autofocus (e.g., by touching screen, or volume focus, or hardware focus button)
    	// consider whether you want to call requestAutoFocus() instead (which properly cancels any in-progress auto-focus first)

		if( camera_controller == null ) {
		}
		else if( !this.has_surface ) {
		}
		else if( !this.is_preview_started ) {
		}
		//else if( is_taking_photo ) {
		else if( !(manual) && this.isTakingPhotoOrOnTimer() ) {
			// if taking a video, we allow manual autofocuses
			// autofocus may cause problem if there is a video corruption problem, see testTakeVideoBitrate() on Nexus 7 at 30Mbs or 50Mbs, where the startup autofocus would cause a problem here
		}
		else {
			if( manual ) {
				// remove any previous request to switch back to continuous
				removePendingContinuousFocusReset();
			}
			if( manual && camera_controller.focusIsContinuous() && supportedFocusValue("focus_mode_auto") ) {
		        camera_controller.setFocusValue("focus_mode_auto"); // switch to autofocus
		        autofocus_in_continuous_mode = true;
		        // we switch back to continuous via a new reset_continuous_focus_runnable in autoFocusCompleted()
			}
			// it's only worth doing autofocus when autofocus has an effect (i.e., auto or macro mode)
			// but also for continuous focus mode, triggering an autofocus is still important to fire flash when touching the screen
			if( camera_controller.supportsAutoFocus() ) {
				if( !using_android_l ) {
					set_flash_value_after_autofocus = "";
					String old_flash_value = camera_controller.getFlashValue();
	    			// getFlashValue() may return "" if flash not supported!
					if( startup && old_flash_value.length() > 0 && !old_flash_value.equals("flash_off") && !old_flash_value.equals("flash_torch") ) {
	    				set_flash_value_after_autofocus = old_flash_value;
	        			camera_controller.setFlashValue("flash_off");
	    			}
				}
    			CameraController.AutoFocusCallback autoFocusCallback = new CameraController.AutoFocusCallback() {
					@Override
					public void onAutoFocus(boolean success) {
						autoFocusCompleted(manual, success, false);
					}
		        };
	
				this.focus_success = FOCUS_WAITING;
	    		this.focus_complete_time = -1;
	    		this.successfully_focused = false;
    			camera_controller.autoFocus(autoFocusCallback);
    			count_cameraAutoFocus++;
    			this.focus_started_time = System.currentTimeMillis();
	        }
	        else if( has_focus_area ) {
	        	// do this so we get the focus box, for focus modes that support focus area, but don't support autofocus
				focus_success = FOCUS_SUCCESS;
				focus_complete_time = System.currentTimeMillis();
				// n.b., don't set focus_started_time as that may be used for application to show autofocus animation
	        }
		}
    }
    
    /** If the user touches the screen in continuous focus mode, we switch the camera_controller to autofocus mode.
     *  After the autofocus completes, we set a reset_continuous_focus_runnable to switch back to the camera_controller
     *  back to continuous focus after a short delay.
     *  This function removes any pending reset_continuous_focus_runnable.
     */
    private void removePendingContinuousFocusReset() {
		if( reset_continuous_focus_runnable != null ) {
			reset_continuous_focus_handler.removeCallbacks(reset_continuous_focus_runnable);
			reset_continuous_focus_runnable = null;
		}
    }

    /** If the user touches the screen in continuous focus mode, we switch the camera_controller to autofocus mode.
     *  This function is called to see if we should switch from autofocus mode back to continuous focus mode.
     *  If this isn't required, calling this function does nothing.
     */
    private void continuousFocusReset() {
		if( camera_controller != null && autofocus_in_continuous_mode ) {
	        autofocus_in_continuous_mode = false;
			// check again
	        String current_ui_focus_value = getCurrentFocusValue();
	        if( current_ui_focus_value != null && !camera_controller.getFocusValue().equals(current_ui_focus_value) && camera_controller.getFocusValue().equals("focus_mode_auto") ) {
				camera_controller.cancelAutoFocus();
		        camera_controller.setFocusValue(current_ui_focus_value);
	        }
		}
    }
    
    private void cancelAutoFocus() {
        if( camera_controller != null ) {
			camera_controller.cancelAutoFocus();
    		autoFocusCompleted(false, false, true);
        }
    }
    
    private void ensureFlashCorrect() {
    	// ensures flash is in correct mode, in case where we had to turn flash temporarily off for startup autofocus 
		if( set_flash_value_after_autofocus.length() > 0 && camera_controller != null ) {
			camera_controller.setFlashValue(set_flash_value_after_autofocus);
			set_flash_value_after_autofocus = "";
		}
    }
    
    private void autoFocusCompleted(boolean manual, boolean success, boolean cancelled) {
		if( cancelled ) {
			focus_success = FOCUS_DONE;
		}
		else {
			focus_success = success ? FOCUS_SUCCESS : FOCUS_FAILED;
			focus_complete_time = System.currentTimeMillis();
		}
		if( manual && !cancelled && ( success || applicationInterface.isTestAlwaysFocus() ) ) {
			successfully_focused = true;
			successfully_focused_time = focus_complete_time;
		}
		if( manual && camera_controller != null && autofocus_in_continuous_mode ) {
	        String current_ui_focus_value = getCurrentFocusValue();
	        if( current_ui_focus_value != null && !camera_controller.getFocusValue().equals(current_ui_focus_value) && camera_controller.getFocusValue().equals("focus_mode_auto") ) {
				reset_continuous_focus_runnable = new Runnable() {
					@Override
					public void run() {
						reset_continuous_focus_runnable = null;
						continuousFocusReset();
					}
				};
				reset_continuous_focus_handler.postDelayed(reset_continuous_focus_runnable, 3000);
	        }
		}
		ensureFlashCorrect();
		if( this.using_face_detection && !cancelled ) {
			// On some devices such as mtk6589, face detection does not resume as written in documentation so we have
			// to cancelfocus when focus is finished
			if( camera_controller != null ) {
				camera_controller.cancelAutoFocus();
			}
		}
		synchronized(this) {
			if( take_photo_after_autofocus ) {
				take_photo_after_autofocus = false;
				prepareAutoFocusPhoto();
				takePhotoWhenFocused();
			}
		}
    }
    
    public void startCameraPreview() {
		//if( camera != null && !is_taking_photo && !is_preview_started ) {
		if( camera_controller != null && !this.isTakingPhotoOrOnTimer() && !is_preview_started ) {
			setPreviewFps();
    		try {
    			camera_controller.startPreview();
		    	count_cameraStartPreview++;
    		}
    		catch(CameraControllerException e) {
    			e.printStackTrace();
    			applicationInterface.onFailedStartPreview();
    			return;
    		}
			this.is_preview_started = true;
		}
		this.setPreviewPaused(false);
		this.setupContinuousFocusMove();
    }

    private void setPreviewPaused(boolean paused) {
		applicationInterface.hasPausedPreview(paused);
	    if( paused ) {
	    	this.phase = PHASE_PREVIEW_PAUSED;
		    // shouldn't call applicationInterface.cameraInOperation(true), as should already have done when we started to take a photo (or above when exiting immersive mode)
		}
		else {
	    	this.phase = PHASE_NORMAL;
			applicationInterface.cameraInOperation(false);
		}
    }

    public void onAccelerometerSensorChanged(SensorEvent event) {

    	this.has_gravity = true;
    	for(int i=0;i<3;i++) {
    		//this.gravity[i] = event.values[i];
    		this.gravity[i] = sensor_alpha * this.gravity[i] + (1.0f-sensor_alpha) * event.values[i];
    	}
    	calculateGeoDirection();
    	
		double x = gravity[0];
		double y = gravity[1];
		this.has_level_angle = true;
		this.level_angle = Math.atan2(-x, y) * 180.0 / Math.PI;
		if( this.level_angle < -0.0 ) {
			this.level_angle += 360.0;
		}
		this.orig_level_angle = this.level_angle;
		this.level_angle -= (float)this.current_orientation;
		if( this.level_angle < -180.0 ) {
			this.level_angle += 360.0;
		}
		else if( this.level_angle > 180.0 ) {
			this.level_angle -= 360.0;
		}

		cameraSurface.getView().invalidate();
	}
    
    public boolean hasLevelAngle() {
    	return this.has_level_angle;
    }
    
    public double getLevelAngle() {
    	return this.level_angle;
    }
    
    public double getOrigLevelAngle() {
    	return this.orig_level_angle;
    }

    public void onMagneticSensorChanged(SensorEvent event) {
    	this.has_geomagnetic = true;
    	for(int i=0;i<3;i++) {
    		this.geomagnetic[i] = sensor_alpha * this.geomagnetic[i] + (1.0f-sensor_alpha) * event.values[i];
    	}
    	calculateGeoDirection();
    }
    
    private void calculateGeoDirection() {
    	if( !this.has_gravity || !this.has_geomagnetic ) {
    		return;
    	}
    	if( !SensorManager.getRotationMatrix(this.deviceRotation, this.deviceInclination, this.gravity, this.geomagnetic) ) {
    		return;
    	}
        SensorManager.remapCoordinateSystem(this.deviceRotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, this.cameraRotation);
    	this.has_geo_direction = true;
    	SensorManager.getOrientation(cameraRotation, geo_direction);
    }
    
    public boolean hasGeoDirection() {
    	return has_geo_direction;
    }
    
    public double getGeoDirection() {
    	return geo_direction[0];
    }
    
    public CameraController.Size getCurrentPictureSize() {
    	if( current_size_index == -1 || sizes == null )
    		return null;
    	return sizes.get(current_size_index);
    }

	public List<String> getSupportedFocusValues() {
		return supported_focus_values;
	}

    public int getCameraId() {
        if( camera_controller == null )
            return 0;
        return camera_controller.getCameraId();
    }
    
    public void onResume() {
		this.app_is_paused = false;
		this.openCamera();
    }

    public void onPause() {
		this.app_is_paused = true;
		this.closeCamera();
    }
    
    /*void updateUIPlacement() {
    	// we cache the preference_ui_placement to save having to check it in the draw() method
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		String ui_placement = sharedPreferences.getString(MainActivity.getUIPlacementPreferenceKey(), "ui_right");
		this.ui_placement_right = ui_placement.equals("ui_right");
    }*/

	public void onSaveInstanceState(Bundle state) {
	}

    public void showToast(final ToastBoxer clear_toast, final int message_id) {
    	showToast(clear_toast, getResources().getString(message_id));
    }

    public void showToast(final ToastBoxer clear_toast, final String message) {
    	showToast(clear_toast, message, 32);
    }

    public void showToast(final ToastBoxer clear_toast, final String message, final int offset_y_dp) {
		if( !applicationInterface.getShowToastsPref() ) {
			return;
		}
    	
		class RotatedTextView extends View {
			private String [] lines = null;
			private Paint paint = new Paint();
			private Rect bounds = new Rect();
			private Rect sub_bounds = new Rect();
			private RectF rect = new RectF();

			public RotatedTextView(String text, Context context) {
				super(context);

				this.lines = text.split("\n");
			}

			@Override 
			protected void onDraw(Canvas canvas) {
				final float scale = Preview.this.getResources().getDisplayMetrics().density;
				paint.setTextSize(14 * scale + 0.5f); // convert dps to pixels
				paint.setShadowLayer(1, 0, 1, Color.BLACK);
				//paint.getTextBounds(text, 0, text.length(), bounds);
				boolean first_line = true;
				for(String line : lines) {
					paint.getTextBounds(line, 0, line.length(), sub_bounds);
					/*if( MyDebug.LOG ) {
						Log.d(TAG, "line: " + line + " sub_bounds: " + sub_bounds);
					}*/
					if( first_line ) {
						bounds.set(sub_bounds);
						first_line = false;
					}
					else {
						bounds.top = Math.min(sub_bounds.top, bounds.top);
						bounds.bottom = Math.max(sub_bounds.bottom, bounds.bottom);
						bounds.left = Math.min(sub_bounds.left, bounds.left);
						bounds.right = Math.max(sub_bounds.right, bounds.right);
					}
				}
				int height = bounds.bottom - bounds.top + 2;
				bounds.bottom += ((lines.length-1) * height)/2;
				bounds.top -= ((lines.length-1) * height)/2;
				final int padding = (int) (14 * scale + 0.5f); // convert dps to pixels
				final int offset_y = (int) (offset_y_dp * scale + 0.5f); // convert dps to pixels
				canvas.save();
				canvas.rotate(ui_rotation, canvas.getWidth()/2.0f, canvas.getHeight()/2.0f);

				rect.left = canvas.getWidth()/2 - bounds.width()/2 + bounds.left - padding;
				rect.top = canvas.getHeight()/2 + bounds.top - padding + offset_y;
				rect.right = canvas.getWidth()/2 - bounds.width()/2 + bounds.right + padding;
				rect.bottom = canvas.getHeight()/2 + bounds.bottom + padding + offset_y;

				paint.setStyle(Paint.Style.FILL);
				paint.setColor(Color.rgb(50, 50, 50));
				//canvas.drawRect(rect, paint);
				final float radius = (24 * scale + 0.5f); // convert dps to pixels
				canvas.drawRoundRect(rect, radius, radius, paint);

				paint.setColor(Color.WHITE);
				int ypos = canvas.getHeight()/2 + offset_y - ((lines.length-1) * height)/2;
				for(String line : lines) {
					canvas.drawText(line, canvas.getWidth()/2 - bounds.width()/2, ypos, paint);
					ypos += height;
				}
				canvas.restore();
			} 
		}

		final Activity activity = (Activity)this.getContext();
		// We get a crash on emulator at least if Toast constructor isn't run on main thread (e.g., the toast for taking a photo when on timer).
		// Also see http://stackoverflow.com/questions/13267239/toast-from-a-non-ui-thread
		activity.runOnUiThread(new Runnable() {
			public void run() {

				// This method is better, as otherwise a previous toast (with different or no clear_toast) never seems to clear if we repeatedly issue new toasts - this doesn't happen if we reuse existing toasts if possible
				// However should only do this if the previous toast was the most recent toast (to avoid messing up ordering)
				Toast toast = null;
				if( clear_toast != null && clear_toast.toast != null && clear_toast.toast == last_toast ) {

					toast = clear_toast.toast;
				}
				else {
					if( clear_toast != null && clear_toast.toast != null ) {
						clear_toast.toast.cancel();
					}
					toast = new Toast(activity);
					if( clear_toast != null )
						clear_toast.toast = toast;
				}
				View text = new RotatedTextView(message, activity);
				toast.setView(text);
				toast.setDuration(Toast.LENGTH_SHORT);
				toast.show();
				last_toast = toast;
			}
		});
	}
	
	public void setUIRotation(int ui_rotation) {
		this.ui_rotation = ui_rotation;
	}
	
	public int getUIRotation() {
		return this.ui_rotation;
	}

	/** If geotagging is enabled, pass the location info to the camera controller (for photos).
	 */
    private void updateParametersFromLocation() {
    	if( camera_controller != null ) {
    		boolean store_location = applicationInterface.getGeotaggingPref();
    		if( store_location && applicationInterface.getLocation() != null ) {
    			Location location = applicationInterface.getLocation();
	    		camera_controller.setLocationInfo(location);
    		}
    		else {
	    		camera_controller.removeLocationInfo();
    		}
    	}
    }

    public boolean usingCamera2API() {
    	return this.using_android_l;
    }

    public CameraController getCameraController() {
    	return this.camera_controller;
    }
    
    public boolean supportsFocus() {
    	return this.supported_focus_values != null;
    }

    public boolean supportsExposureLock() {
    	return this.is_exposure_lock_supported;
    }
    
    public boolean isExposureLocked() {
    	return this.is_exposure_locked;
    }
    
    public boolean supportsZoom() {
    	return this.has_zoom;
    }

    public boolean hasFocusArea() {
    	return this.has_focus_area;
    }
    
    public Pair<Integer, Integer> getFocusPos() {
    	return new Pair<Integer, Integer>(focus_screen_x, focus_screen_y);
    }

    public boolean isTakingPhotoOrOnTimer() {
    	//return this.is_taking_photo;
    	return this.phase == PHASE_TAKING_PHOTO || this.phase == PHASE_TIMER;
    }
    
    public boolean isOnTimer() {
    	//return this.is_taking_photo_on_timer;
    	return this.phase == PHASE_TIMER;
    }
    
    public long getTimerEndTime() {
    	return take_photo_time;
    }
    
    public boolean isPreviewPaused() {
    	return this.phase == PHASE_PREVIEW_PAUSED;
    }

    public boolean isFocusWaiting() {
    	return focus_success == FOCUS_WAITING;
    }
    
    public boolean isFocusRecentSuccess() {
    	return focus_success == FOCUS_SUCCESS;
    }
    
    public long timeSinceStartedAutoFocus() {
    	if( focus_started_time != -1 )
    		return System.currentTimeMillis() - focus_started_time;
    	return 0;
    }
    
    public boolean isFocusRecentFailure() {
    	return focus_success == FOCUS_FAILED;
    }

    /** Whether we can skip the autofocus before taking a photo.
     */
    private boolean recentlyFocused() {
    	return this.successfully_focused && System.currentTimeMillis() < this.successfully_focused_time + 5000;
    }

	public float getZoomRatio() {
		int zoom_factor = camera_controller.getZoom();
		float zoom_ratio = this.zoom_ratios.get(zoom_factor)/100.0f;
		return zoom_ratio;
	}
}
