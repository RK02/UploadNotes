package com.campusconnect.previewdemo;

import com.campusconnect.previewdemo.CameraController.CameraController;
import com.campusconnect.previewdemo.CameraController.CameraControllerManager2;
import com.campusconnect.previewdemo.Preview.Preview;
import com.campusconnect.previewdemo.UI.FolderChooserDialog;
import com.campusconnect.previewdemo.UI.MainUI;
import com.campusconnect.previewdemo.UploadManager.Constants;
import com.campusconnect.previewdemo.UploadManager.NotificationService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.renderscript.RenderScript;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.w3c.dom.Text;

/** The main Activity for Open Camera.
 */
public class MainActivity extends AppCompatActivity{
	private static final String TAG = "MainActivity";
	private SensorManager mSensorManager = null;
	private Sensor mSensorAccelerometer = null;
	private Sensor mSensorMagnetic = null;
	private MainUI mainUI = null;
	private MyApplicationInterface applicationInterface = null;
	private Preview preview = null;
	private OrientationEventListener orientationEventListener = null;
	private boolean supports_auto_stabilise = false;
	private boolean supports_camera2 = false;
    private boolean saf_dialog_from_preferences = false; // if a SAF dialog is opened, this records whether we opened it from the Preferences
	private boolean camera_in_background = false; // whether the camera is covered by a fragment/dialog (such as settings or folder picker)
    private boolean screen_is_locked = false; // whether screen is "locked" - this is Open Camera's own lock to guard against accidental presses, not the standard Android lock
    private Map<Integer, Bitmap> preloaded_bitmap_resources = new Hashtable<Integer, Bitmap>();
	private ValueAnimator gallery_save_anim = null;

    private SoundPool sound_pool = null;
	private SparseIntArray sound_ids = null;

    private ToastBoxer screen_locked_toast = new ToastBoxer();
	private ToastBoxer exposure_lock_toast = new ToastBoxer();
	private boolean block_startup_toast = false; // used when returning from Settings/Popup - if we're displaying a toast anyway, don't want to display the info toast too

	// for testing:
	public boolean is_test = false; // whether called from OpenCamera.test testing
	public Bitmap gallery_bitmap = null;
	public boolean test_low_memory = false;
	public boolean test_have_angle = false;
	public float test_angle = 0.0f;
	public String test_last_saved_image = null;

	int numberOfImages=0;

	TextView newUser, welcomeBack, takePhoto, fromGallery;
	Button signUp, signIn;
	public static Button capturePhoto;
	public static RelativeLayout infoContainer;
	LinearLayout transparentOverlayView;
	public static FloatingActionButton done;
	public static TextView imageNumber;
	public static View transparencyOnCapture;
	public static ImageView doneSign;

	public static ArrayList<String> uriList;
	public static NotificationManager notificationManager;
	public static NotificationCompat.Builder builder;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		long debug_time = 0;
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		uriList = new ArrayList<>();
		newUser = (TextView)findViewById(R.id.tv_new_user);
		welcomeBack = (TextView)findViewById(R.id.tv_welcome_back);
		takePhoto = (TextView)findViewById(R.id.tv_takePhoto);
		fromGallery = (TextView)findViewById(R.id.tv_fromGallery);
		imageNumber = (TextView)findViewById(R.id.tv_imageNumber);
		signIn = (Button)findViewById(R.id.b_signIn);
		signUp = (Button)findViewById(R.id.b_signUp);
		capturePhoto = (Button)findViewById(R.id.b_capture);
		infoContainer = (RelativeLayout) findViewById(R.id.viewfinder_info_container);
		transparentOverlayView = (LinearLayout)findViewById(R.id.transparent_overlay);
		done = (FloatingActionButton)findViewById(R.id.fab);
		transparencyOnCapture = (View)findViewById(R.id.transparent_view);
		doneSign = (ImageView)findViewById(R.id.iv_done);

		signIn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				newUser.setVisibility(View.GONE);
				welcomeBack.setVisibility(View.GONE);
				signIn.setVisibility(View.GONE);
				signUp.setVisibility(View.GONE);

				takePhoto.setVisibility(View.VISIBLE);
				fromGallery.setVisibility(View.VISIBLE);
			}
		});

		takePhoto.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				takePhoto.setVisibility(View.GONE);
				fromGallery.setVisibility(View.GONE);

				transparentOverlayView.setVisibility(View.GONE);
			}
		});

		fromGallery.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				takePhoto.setVisibility(View.GONE);
				fromGallery.setVisibility(View.GONE);

				transparentOverlayView.setVisibility(View.GONE);
			}
		});

		done.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent intent_temp = new Intent(MainActivity.this, UploadPicturesActivity.class);
				intent_temp.putStringArrayListExtra("uriList", uriList);
				intent_temp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent_temp);
			}
		});

		PreferenceManager.setDefaultValues(this, R.xml.preferences, false); // initialise any unset preferences to their default values

		if( getIntent() != null && getIntent().getExtras() != null ) {
			// whether called from testing
			is_test = getIntent().getExtras().getBoolean("test_project");
		}
		if( getIntent() != null && getIntent().getExtras() != null ) {
			// whether called from Take Photo widget
			boolean take_photo = getIntent().getExtras().getBoolean(TakePhoto.TAKE_PHOTO);
		}
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		// determine whether we should support "auto stabilise" feature
		// risk of running out of memory on lower end devices, due to manipulation of large bitmaps
		ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

		//if( activityManager.getMemoryClass() >= 128 ) { // test
		if( activityManager.getLargeMemoryClass() >= 128 ) {
			supports_auto_stabilise = true;
		}

		// set up components
		mainUI = new MainUI(this);
		applicationInterface = new MyApplicationInterface(this, savedInstanceState);

		// determine whether we support Camera2 API
		initCamera2Support();

		// set up window flags for normal operation
        setWindowFlagsForCamera();

		// set up sensors
        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);

        // accelerometer sensor (for device orientation)
        if( mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ) {
			mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		}

		// magnetic sensor (for compass direction)
		if( mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null ) {
			mSensorMagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		}

		// set up the camera and its preview
        preview = new Preview(applicationInterface, savedInstanceState, ((ViewGroup) this.findViewById(R.id.preview)));


		// listen for orientation event change
	    orientationEventListener = new OrientationEventListener(this) {
			@Override
			public void onOrientationChanged(int orientation) {
				MainActivity.this.mainUI.onOrientationChanged(orientation);
			}
        };

		// set up listener to handle immersive mode options
        View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener
                (new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                // Note that system bars will only be "visible" if none of the
                // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.
            	if( !usingKitKatImmersiveMode() )
            		return;
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    // The system bars are visible. Make any desired
                    // adjustments to your UI, such as showing the action bar or
                    // other navigational controls.
            		mainUI.setImmersiveMode(false);
                	setImmersiveTimer();
                }
                else {
                    // The system bars are NOT visible. Make any desired
                    // adjustments to your UI, such as hiding the action bar or
                    // other navigational controls.
            		mainUI.setImmersiveMode(true);
                }
            }
        });

		// show "about" dialog for first time use
		boolean has_done_first_time = sharedPreferences.contains(PreferenceKeys.getFirstTimePreferenceKey());
        if( !has_done_first_time && !is_test ) {
	        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
            alertDialog.setTitle(R.string.app_name);
            alertDialog.setMessage(R.string.intro_text);
            alertDialog.setPositiveButton(R.string.intro_ok, null);
            alertDialog.show();

            setFirstTimeFlag();
        }

        // load icons
        preloadIcons(R.array.flash_icons);
        preloadIcons(R.array.focus_mode_icons);

		notificationManager =
				(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		builder = new NotificationCompat.Builder(this);
		Intent intent = new Intent(this, NotificationService.class);
		intent.setAction(Constants.ACTION_PAUSE);
		PendingIntent pauseIntent = PendingIntent.getService(this, 0,
				intent, 0);
		Intent intent1 = new Intent(this, NotificationService.class);
		intent1.setAction(Constants.ACTION_RESUME);
		PendingIntent resumeIntent = PendingIntent.getService(this, 0,
				intent1, 0);

		builder.setContentTitle("Notes Uploader")
				.setContentText("Uploading notes ")
				.setSmallIcon(R.mipmap.ic_launcher)
				.setPriority(Notification.PRIORITY_MAX)
				.setOngoing(true)
				.addAction(0, "Pause", pauseIntent)
				.addAction(0, "Resume", resumeIntent);
	}

	/** Determine whether we support Camera2 API.
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void initCamera2Support() {
    	supports_camera2 = false;
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
        	CameraControllerManager2 manager2 = new CameraControllerManager2(this);
        	supports_camera2 = true;
        	if( manager2.getNumberOfCameras() == 0 ) {
            	supports_camera2 = false;
        	}
        	for(int i=0;i<manager2.getNumberOfCameras() && supports_camera2;i++) {
        		if( !manager2.allowCamera2Support(i) ) {
                	supports_camera2 = false;
        		}
        	}
        }
	}
	
	private void preloadIcons(int icons_id) {
		long debug_time = 0;
    	String [] icons = getResources().getStringArray(icons_id);
    	for(int i=0;i<icons.length;i++) {
    		int resource = getResources().getIdentifier(icons[i], null, this.getApplicationContext().getPackageName());

    		Bitmap bm = BitmapFactory.decodeResource(getResources(), resource);
    		this.preloaded_bitmap_resources.put(resource, bm);
    	}
	}
	
    @TargetApi(Build.VERSION_CODES.M)
	@Override
	protected void onDestroy() {

		if( applicationInterface != null ) {
			applicationInterface.onDestroy();
		}
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
			// see note in HDRProcessor.onDestroy() - but from Android M, renderscript contexts are released with releaseAllContexts()
			// doc for releaseAllContexts() says "If no contexts have been created this function does nothing"
			RenderScript.releaseAllContexts();
		}
		// Need to recycle to avoid out of memory when running tests - probably good practice to do anyway
		for(Map.Entry<Integer, Bitmap> entry : preloaded_bitmap_resources.entrySet()) {
			entry.getValue().recycle();
		}
		preloaded_bitmap_resources.clear();

	    super.onDestroy();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	private void setFirstTimeFlag() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(PreferenceKeys.getFirstTimePreferenceKey(), true);
		editor.apply();
	}

	private SensorEventListener accelerometerListener = new SensorEventListener() {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			preview.onAccelerometerSensorChanged(event);
		}
	};
	
	private SensorEventListener magneticListener = new SensorEventListener() {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			preview.onMagneticSensorChanged(event);
		}
	};

	@Override
    protected void onResume() {
        super.onResume();

        // Set black window background; also needed if we hide the virtual buttons in immersive mode
        // Note that we do it here rather than customising the theme's android:windowBackground, so this doesn't affect other views - in particular, the MyPreferenceFragment settings
		getWindow().getDecorView().getRootView().setBackgroundColor(Color.BLACK);

        mSensorManager.registerListener(accelerometerListener, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(magneticListener, mSensorMagnetic, SensorManager.SENSOR_DELAY_NORMAL);
        orientationEventListener.enable();

        initLocation();
        initSound();
    	loadSound(R.raw.beep);
    	loadSound(R.raw.beep_hi);

		mainUI.layoutUI();

		preview.onResume();
    }
	
	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
        if( !this.camera_in_background && hasFocus ) {
			// low profile mode is cleared when app goes into background
        	// and for Kit Kat immersive mode, we want to set up the timer
        	// we do in onWindowFocusChanged rather than onResume(), to also catch when window lost focus due to notification bar being dragged down (which prevents resetting of immersive mode)
            initImmersiveMode();
        }
	}

    @Override
    protected void onPause() {
		long debug_time = 0;
		waitUntilImageQueueEmpty(); // so we don't risk losing any images
        super.onPause();
        mSensorManager.unregisterListener(accelerometerListener);
        mSensorManager.unregisterListener(magneticListener);
        orientationEventListener.disable();
        applicationInterface.getLocationSupplier().freeLocationListeners();
		releaseSound();
		applicationInterface.clearLastImages(); // this should happen when pausing the preview, but call explicitly just to be safe
		preview.onPause();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
		// configuration change can include screen orientation (landscape/portrait) when not locked (when settings is open)
		// needed if app is paused/resumed when settings is open and device is in portrait mode
        preview.setCameraDisplayOrientation();
        super.onConfigurationChanged(newConfig);
    }
    
    public void waitUntilImageQueueEmpty() {
        applicationInterface.getImageSaver().waitUntilDone();
    }
    
    public void clickedTakePhoto(View view) {
    	this.takePicture();
    }

    public void clickedExposureLock(View view) {
    	this.preview.toggleExposureLock();
	    ImageButton exposureLockButton = (ImageButton) findViewById(R.id.exposure_lock);
		exposureLockButton.setImageResource(preview.isExposureLocked() ? R.mipmap.exposure_locked : R.mipmap.exposure_unlocked);
		preview.showToast(exposure_lock_toast, preview.isExposureLocked() ? R.string.exposure_locked : R.string.exposure_unlocked);
    }

    public void updateForSettings() {
    	updateForSettings(null);
    }

    public void updateForSettings(String toast_message) {
    	String saved_focus_value = null;
    	if( preview.getCameraController() != null ) {
    		saved_focus_value = preview.getCurrentFocusValue(); // n.b., may still be null
			// make sure we're into continuous video mode
			// workaround for bug on Samsung Galaxy S5 with UHD, where if the user switches to another (non-continuous-video) focus mode, then goes to Settings, then returns and records video, the preview freezes and the video is corrupted
			// so to be safe, we always reset to continuous video mode, and then reset it afterwards
    	}
		// no need to update save_location_history_saf, as we always do this in onActivityResult()

		// update camera for changes made in prefs - do this without closing and reopening the camera app if possible for speed!
		// but need workaround for Nexus 7 bug, where scene mode doesn't take effect unless the camera is restarted - I can reproduce this with other 3rd party camera apps, so may be a Nexus 7 issue...
		boolean need_reopen = false;
		if( preview.getCameraController() != null ) {
			String scene_mode = preview.getCameraController().getSceneMode();
			String key = PreferenceKeys.getSceneModePreferenceKey();
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
			String value = sharedPreferences.getString(key, preview.getCameraController().getDefaultSceneMode());
			if( !value.equals(scene_mode) ) {
				need_reopen = true;
			}
		}

		mainUI.layoutUI(); // needed in case we've changed left/right handed UI

		initLocation(); // in case we've enabled or disabled GPS
		if( toast_message != null )
			block_startup_toast = true;
		if( need_reopen || preview.getCameraController() == null ) { // if camera couldn't be opened before, might as well try again
			preview.onPause();
			preview.onResume();
		}
		else {
			preview.setCameraDisplayOrientation(); // need to call in case the preview rotation option was changed
			preview.pausePreview();
			preview.setupCamera(false);
		}
		block_startup_toast = false;
		if( toast_message != null && toast_message.length() > 0 )
			preview.showToast(null, toast_message);

    	if( saved_focus_value != null ) {
    		preview.updateFocus(saved_focus_value, true, false);
    	}
    }
    
    MyPreferenceFragment getPreferenceFragment() {
        MyPreferenceFragment fragment = (MyPreferenceFragment)getFragmentManager().findFragmentByTag("PREFERENCE_FRAGMENT");
        return fragment;
    }
    
    @Override
    public void onBackPressed() {
        final MyPreferenceFragment fragment = getPreferenceFragment();
        if( screen_is_locked ) {
			preview.showToast(screen_locked_toast, R.string.screen_is_locked);
        	return;
        }
        if( fragment != null ) {
			setWindowFlagsForCamera();
			updateForSettings();
        }
        super.onBackPressed();        
    }
    
    public boolean usingKitKatImmersiveMode() {
    	// whether we are using a Kit Kat style immersive mode (either hiding GUI, or everything)
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ) {
    		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    		String immersive_mode = sharedPreferences.getString(PreferenceKeys.getImmersiveModePreferenceKey(), "immersive_mode_low_profile");
    		if( immersive_mode.equals("immersive_mode_gui") || immersive_mode.equals("immersive_mode_everything") )
    			return true;
		}
		return false;
    }
    
    private Handler immersive_timer_handler = null;
    private Runnable immersive_timer_runnable = null;
    
    private void setImmersiveTimer() {
    	if( immersive_timer_handler != null && immersive_timer_runnable != null ) {
    		immersive_timer_handler.removeCallbacks(immersive_timer_runnable);
    	}
    	immersive_timer_handler = new Handler();
    	immersive_timer_handler.postDelayed(immersive_timer_runnable = new Runnable(){
    		@Override
    	    public void run(){
    			if( !camera_in_background && usingKitKatImmersiveMode() )
    				setImmersiveMode(true);
    	   }
    	}, 5000);
    }

    public void initImmersiveMode() {
        if( !usingKitKatImmersiveMode() ) {
			setImmersiveMode(true);
		}
        else {
        	// don't start in immersive mode, only after a timer
        	setImmersiveTimer();
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
	void setImmersiveMode(boolean on) {
    	// n.b., preview.setImmersiveMode() is called from onSystemUiVisibilityChange()
    	if( on ) {
    		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && usingKitKatImmersiveMode() ) {
        		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
    		}
    		else {
        		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        		String immersive_mode = sharedPreferences.getString(PreferenceKeys.getImmersiveModePreferenceKey(), "immersive_mode_low_profile");
        		if( immersive_mode.equals("immersive_mode_low_profile") )
        			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        		else
            		getWindow().getDecorView().setSystemUiVisibility(0);
    		}
    	}
    	else
    		getWindow().getDecorView().setSystemUiVisibility(0);
    }

    /** Sets the window flags for normal operation (when camera preview is visible).
     */
    public void setWindowFlagsForCamera() {
    	/*{
    		Intent intent = new Intent(this, MyWidgetProvider.class);
    		intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
    		AppWidgetManager widgetManager = AppWidgetManager.getInstance(this);
    		ComponentName widgetComponent = new ComponentName(this, MyWidgetProvider.class);
    		int[] widgetIds = widgetManager.getAppWidgetIds(widgetComponent);
    		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
    		sendBroadcast(intent);    		
    	}*/
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		// force to landscape mode
//		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		// keep screen active - see http://stackoverflow.com/questions/2131948/force-screen-on
		if( sharedPreferences.getBoolean(PreferenceKeys.getKeepDisplayOnPreferenceKey(), true) ) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		if( sharedPreferences.getBoolean(PreferenceKeys.getShowWhenLockedPreferenceKey(), true) ) {
	        // keep Open Camera on top of screen-lock (will still need to unlock when going to gallery or settings)
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		}
		else {
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		}

        // set screen to max brightness - see http://stackoverflow.com/questions/11978042/android-screen-brightness-max-value
		// done here rather than onCreate, so that changing it in preferences takes effect without restarting app
		{
	        WindowManager.LayoutParams layout = getWindow().getAttributes();
			if( sharedPreferences.getBoolean(PreferenceKeys.getMaxBrightnessPreferenceKey(), true) ) {
		        layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
	        }
			else {
		        layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
			}
	        getWindow().setAttributes(layout); 
		}
		
		initImmersiveMode();
		camera_in_background = false;
    }
    
    /** Sets the window flags for when the settings window is open.
     */
    public void setWindowFlagsForSettings() {
		// allow screen rotation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		// revert to standard screen blank behaviour
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // settings should still be protected by screen lock
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

		{
	        WindowManager.LayoutParams layout = getWindow().getAttributes();
	        layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
	        getWindow().setAttributes(layout); 
		}

		setImmersiveMode(false);
		camera_in_background = true;
    }
    
    public void showPreview(boolean show) {
		final ViewGroup container = (ViewGroup)findViewById(R.id.hide_container);
		container.setBackgroundColor(Color.BLACK);
		container.setAlpha(show ? 0.0f : 1.0f);
    }

	//Animation for saving
	void savingImage(final boolean started) {

		this.runOnUiThread(new Runnable() {
			public void run() {

			}
		});
    }
    
    /** Listens for the response from the Storage Access Framework dialog to select a folder
     *  (as opened with openFolderChooserDialogSAF()).
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if( requestCode == 42 ) {
            if( resultCode == RESULT_OK && resultData != null ) {
	            Uri treeUri = resultData.getData();
	    		// from https://developer.android.com/guide/topics/providers/document-provider.html#permissions :
	    		int takeFlags = resultData.getFlags();
				takeFlags &= (Intent.FLAG_GRANT_READ_URI_PERMISSION
	    	            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		    	// Check for the freshest data.
		    	getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), treeUri.toString());
				editor.apply();

				String filename = applicationInterface.getStorageUtils().getImageFolderNameSAF();
				if( filename != null ) {
					preview.showToast(null, getResources().getString(R.string.changed_save_location) + "\n" + filename);
				}
	        }
	        else {
	        	// cancelled - if the user had yet to set a save location, make sure we switch SAF back off
				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
	    		String uri = sharedPreferences.getString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "");
	    		if( uri.length() == 0 ) {
	    			SharedPreferences.Editor editor = sharedPreferences.edit();
	    			editor.putBoolean(PreferenceKeys.getUsingSAFPreferenceKey(), false);
	    			editor.apply();
	    			preview.showToast(null, R.string.saf_cancelled);
	    		}
	        }

            if( !saf_dialog_from_preferences ) {
				setWindowFlagsForCamera();
				showPreview(true);
            }
        }
    }

    private void takePicture() {
		numberOfImages++;
    	this.preview.takePicturePressed(numberOfImages);
    }

	@Override
	protected void onSaveInstanceState(Bundle state) {
	    super.onSaveInstanceState(state);
	    if( this.preview != null ) {
	    	preview.onSaveInstanceState(state);
	    }
	    if( this.applicationInterface != null ) {
	    	applicationInterface.onSaveInstanceState(state);
	    }
	}

    void cameraSetup() {

	    ImageButton exposureLockButton = (ImageButton) findViewById(R.id.exposure_lock);
	    exposureLockButton.setVisibility(preview.supportsExposureLock() && !mainUI.inImmersiveMode() ? View.VISIBLE : View.GONE);
	    if( preview.supportsExposureLock() ) {
			exposureLockButton.setImageResource(preview.isExposureLocked() ? R.mipmap.exposure_locked : R.mipmap.exposure_unlocked);
	    }


		if( !block_startup_toast ) {
			this.showPhotoVideoToast(false);
		}
    }
    
    public boolean supportsAutoStabilise() {
    	return this.supports_auto_stabilise;
    }

    public boolean supportsCamera2() {
    	return this.supports_camera2;
    }

    @SuppressWarnings("deprecation")
	public long freeMemory() { // return free memory in MB
    	try {
    		File folder = applicationInterface.getStorageUtils().getImageFolder();
    		if( folder == null ) {
    			throw new IllegalArgumentException(); // so that we fall onto the backup
    		}
	        StatFs statFs = new StatFs(folder.getAbsolutePath());
	        // cast to long to avoid overflow!
	        long blocks = statFs.getAvailableBlocks();
	        long size = statFs.getBlockSize();
	        long free  = (blocks*size) / 1048576;
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "freeMemory blocks: " + blocks + " size: " + size + " free: " + free);
			}*/
	        return free;
    	}
    	catch(IllegalArgumentException e) {
    		// this can happen if folder doesn't exist, or don't have read access
    		// if the save folder is a subfolder of DCIM, we can just use that instead
        	try {
        		if( !applicationInterface.getStorageUtils().isUsingSAF() ) {
        			// StorageUtils.getSaveLocation() only valid if !isUsingSAF()
            		String folder_name = applicationInterface.getStorageUtils().getSaveLocation();
            		if( !folder_name.startsWith("/") ) {
            			File folder = StorageUtils.getBaseFolder();
            	        StatFs statFs = new StatFs(folder.getAbsolutePath());
            	        // cast to long to avoid overflow!
            	        long blocks = statFs.getAvailableBlocks();
            	        long size = statFs.getBlockSize();
            	        long free  = (blocks*size) / 1048576;
            			/*if( MyDebug.LOG ) {
            				Log.d(TAG, "freeMemory blocks: " + blocks + " size: " + size + " free: " + free);
            			}*/
            	        return free;
            		}
        		}
        	}
        	catch(IllegalArgumentException e2) {
        		// just in case
        	}
    	}
		return -1;
    }
    
    public static String getDonateLink() {
    	return "https://play.google.com/store/apps/details?id=harman.mark.donation";
    }

    /*public static String getDonateMarketLink() {
    	return "market://details?id=harman.mark.donation";
    }*/

    public Preview getPreview() {
    	return this.preview;
    }
    
    public MainUI getMainUI() {
    	return this.mainUI;
    }
    
    public MyApplicationInterface getApplicationInterface() {
    	return this.applicationInterface;
    }

    public StorageUtils getStorageUtils() {
    	return this.applicationInterface.getStorageUtils();
    }

    /** Displays a toast with information about the current preferences.
     *  If always_show is true, the toast is always displayed; otherwise, we only display
     *  a toast if it's important to notify the user (i.e., unusual non-default settings are
     *  set). We want a balance between not pestering the user too much, whilst also reminding
     *  them if certain settings are on.
     */
	private void showPhotoVideoToast(boolean always_show) {
		CameraController camera_controller = preview.getCameraController();
		if( camera_controller == null || this.camera_in_background ) {
			return;
		}
		String toast_string = "";
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		boolean simple = true;

			toast_string = getResources().getString(R.string.photo);
			CameraController.Size current_size = preview.getCurrentPictureSize();
			toast_string += " " + current_size.width + "x" + current_size.height;
			if( preview.supportsFocus() && preview.getSupportedFocusValues().size() > 1 ) {
				String focus_value = preview.getCurrentFocusValue();
				if( focus_value != null && !focus_value.equals("focus_mode_auto") && !focus_value.equals("focus_mode_continuous_picture") ) {
					String focus_entry = preview.findFocusEntryForValue(focus_value);
					if( focus_entry != null ) {
						toast_string += "\n" + focus_entry;
					}
				}
			}
			if( sharedPreferences.getBoolean(PreferenceKeys.getAutoStabilisePreferenceKey(), false) ) {
				// important as users are sometimes confused at the behaviour if they don't realise the option is on
				toast_string += "\n" + getResources().getString(R.string.preference_auto_stabilise);
				simple = false;
			}

		String lock_orientation = sharedPreferences.getString(PreferenceKeys.getLockOrientationPreferenceKey(), "none");
		if( !lock_orientation.equals("none") ) {
			String [] entries_array = getResources().getStringArray(R.array.preference_lock_orientation_entries);
			String [] values_array = getResources().getStringArray(R.array.preference_lock_orientation_values);
			int index = Arrays.asList(values_array).indexOf(lock_orientation);
			if( index != -1 ) { // just in case!
				String entry = entries_array[index];
				toast_string += "\n" + entry;
				simple = false;
			}
		}
		String timer = sharedPreferences.getString(PreferenceKeys.getTimerPreferenceKey(), "0");
		if( !timer.equals("0") ) {
			String [] entries_array = getResources().getStringArray(R.array.preference_timer_entries);
			String [] values_array = getResources().getStringArray(R.array.preference_timer_values);
			int index = Arrays.asList(values_array).indexOf(timer);
			if( index != -1 ) { // just in case!
				String entry = entries_array[index];
				toast_string += "\n" + getResources().getString(R.string.preference_timer) + ": " + entry;
				simple = false;
			}
		}
		String repeat = applicationInterface.getRepeatPref();
		if( !repeat.equals("1") ) {
			String [] entries_array = getResources().getStringArray(R.array.preference_burst_mode_entries);
			String [] values_array = getResources().getStringArray(R.array.preference_burst_mode_values);
			int index = Arrays.asList(values_array).indexOf(repeat);
			if( index != -1 ) { // just in case!
				String entry = entries_array[index];
				toast_string += "\n" + getResources().getString(R.string.preference_burst_mode) + ": " + entry;
				simple = false;
			}
		}

	}

	private void initLocation() {
        if( !applicationInterface.getLocationSupplier().setupLocationListener() ) {
    		preview.showToast(null, R.string.permission_location_not_available);
    		// now switch off so we don't keep showing this message
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean(PreferenceKeys.getLocationPreferenceKey(), false);
			editor.apply();
        }
	}
	
	@SuppressWarnings("deprecation")
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void initSound() {
		if( sound_pool == null ) {
	        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
	        	AudioAttributes audio_attributes = new AudioAttributes.Builder()
	        		.setLegacyStreamType(AudioManager.STREAM_SYSTEM)
	        		.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
	        		.build();
	        	sound_pool = new SoundPool.Builder()
	        		.setMaxStreams(1)
	        		.setAudioAttributes(audio_attributes)
        			.build();
	        }
	        else {
				sound_pool = new SoundPool(1, AudioManager.STREAM_SYSTEM, 0);
	        }
			sound_ids = new SparseIntArray();
		}
	}
	
	private void releaseSound() {
        if( sound_pool != null ) {
            sound_pool.release();
        	sound_pool = null;
    		sound_ids = null;
        }
	}
	
	// must be called before playSound (allowing enough time to load the sound)
	void loadSound(int resource_id) {
		if( sound_pool != null ) {
			int sound_id = sound_pool.load(this, resource_id, 1);
			sound_ids.put(resource_id, sound_id);
		}
	}

}
