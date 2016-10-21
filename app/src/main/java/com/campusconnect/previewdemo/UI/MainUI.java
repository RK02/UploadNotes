package com.campusconnect.previewdemo.UI;

import com.campusconnect.previewdemo.MainActivity;
import com.campusconnect.previewdemo.PreferenceKeys;
import com.campusconnect.previewdemo.R;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

/** This contains functionality related to the main UI.
 */
public class MainUI {
	private static final String TAG = "MainUI";

	private MainActivity main_activity = null;

    private int current_orientation = 0;
	private boolean ui_placement_right = true;

	private boolean immersive_mode = false;
    private boolean show_gui = true; // result of call to showGUI() - false means a "reduced" GUI is displayed, whilst taking photo or video

	public MainUI(MainActivity main_activity) {
		this.main_activity = main_activity;
	}

    public void layoutUI() {
		long debug_time = 0;
		//this.preview.updateUIPlacement();
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
		String ui_placement = sharedPreferences.getString(PreferenceKeys.getUIPlacementPreferenceKey(), "ui_right");
    	// we cache the preference_ui_placement to save having to check it in the draw() method
		this.ui_placement_right = ui_placement.equals("ui_right");
		// new code for orientation fixed to landscape	
		// the display orientation should be locked to landscape, but how many degrees is that?
	    int rotation = main_activity.getWindowManager().getDefaultDisplay().getRotation();
	    int degrees = 0;
	    switch (rotation) {
	    	case Surface.ROTATION_0: degrees = 0; break;
	        case Surface.ROTATION_90: degrees = 90; break;
	        case Surface.ROTATION_180: degrees = 180; break;
	        case Surface.ROTATION_270: degrees = 270; break;
    		default:
    			break;
	    }
	    // getRotation is anti-clockwise, but current_orientation is clockwise, so we add rather than subtract
	    // relative_orientation is clockwise from landscape-left
    	//int relative_orientation = (current_orientation + 360 - degrees) % 360;
    	int relative_orientation = (current_orientation + degrees) % 360;

		int ui_rotation = (360 - relative_orientation) % 360;
		main_activity.getPreview().setUIRotation(ui_rotation);
		int left_of = RelativeLayout.LEFT_OF;
		int right_of = RelativeLayout.RIGHT_OF;
		int align_parent_left = RelativeLayout.ALIGN_PARENT_LEFT;
		int align_parent_right = RelativeLayout.ALIGN_PARENT_RIGHT;
		int align_parent_top = RelativeLayout.ALIGN_PARENT_TOP;
		int align_parent_bottom = RelativeLayout.ALIGN_PARENT_BOTTOM;
		if( !ui_placement_right ) {
			align_parent_top = RelativeLayout.ALIGN_PARENT_BOTTOM;
			align_parent_bottom = RelativeLayout.ALIGN_PARENT_TOP;
		}
		{
			// we use a dummy button, so that the GUI buttons keep their positioning even if the Settings button is hidden (visibility set to View.GONE)

			View view = main_activity.findViewById(R.id.exposure_lock);
			RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);
	
			view = main_activity.findViewById(R.id.take_photo);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);
		}

		setTakePhotoIcon();
		// no need to call setSwitchCameraContentDescription()

    }

    /** Set icon for taking photos vs videos.
	 *  Also handles content descriptions for the take photo button and switch video button.
     */
    public void setTakePhotoIcon() {
		if( main_activity.getPreview() != null ) {
			ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
			int resource = 0;
			int content_description = 0;

				resource = R.drawable.take_photo_selector;
				content_description = R.string.take_photo;

			view.setImageResource(resource);
			view.setContentDescription( main_activity.getResources().getString(content_description) );
			view.setTag(resource); // for testing
		}
    }

    public boolean getUIPlacementRight() {
    	return this.ui_placement_right;
    }

    public void onOrientationChanged(int orientation) {
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "onOrientationChanged()");
			Log.d(TAG, "orientation: " + orientation);
			Log.d(TAG, "current_orientation: " + current_orientation);
		}*/
		if( orientation == OrientationEventListener.ORIENTATION_UNKNOWN )
			return;
		int diff = Math.abs(orientation - current_orientation);
		if( diff > 180 )
			diff = 360 - diff;
		// only change orientation when sufficiently changed
		if( diff > 60 ) {
		    orientation = (orientation + 45) / 90 * 90;
		    orientation = orientation % 360;
		    if( orientation != current_orientation ) {
			    this.current_orientation = orientation;
				layoutUI();
		    }
		}
	}

    public void setImmersiveMode(final boolean immersive_mode) {
    	this.immersive_mode = immersive_mode;
		main_activity.runOnUiThread(new Runnable() {
			public void run() {
				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
				// if going into immersive mode, the we should set GONE the ones that are set GONE in showGUI(false)
		    	//final int visibility_gone = immersive_mode ? View.GONE : View.VISIBLE;
		    	final int visibility = immersive_mode ? View.GONE : View.VISIBLE;
		    	// n.b., don't hide share and trash buttons, as they require immediate user input for us to continue
			    View exposureLockButton = (View) main_activity.findViewById(R.id.exposure_lock);

			    if( main_activity.getPreview().supportsExposureLock() )
			    	exposureLockButton.setVisibility(visibility);

        		String pref_immersive_mode = sharedPreferences.getString(PreferenceKeys.getImmersiveModePreferenceKey(), "immersive_mode_low_profile");
        		if( pref_immersive_mode.equals("immersive_mode_everything") ) {
    			    View takePhotoButton = (View) main_activity.findViewById(R.id.take_photo);
    			    takePhotoButton.setVisibility(visibility);
        		}
				if( !immersive_mode ) {
					// make sure the GUI is set up as expected
					showGUI(show_gui);
				}
			}
		});
    }
    
    public boolean inImmersiveMode() {
    	return immersive_mode;
    }

    public void showGUI(final boolean show) {
		this.show_gui = show;
		if( inImmersiveMode() )
			return;
		if( show && main_activity.usingKitKatImmersiveMode() ) {
			// call to reset the timer
			main_activity.initImmersiveMode();
		}
		main_activity.runOnUiThread(new Runnable() {
			public void run() {
		    	final int visibility = show ? View.VISIBLE : View.GONE;
			    View exposureLockButton = (View) main_activity.findViewById(R.id.exposure_lock);

			    if( main_activity.getPreview().supportsExposureLock() ) // still allow exposure lock when recording video
			    	exposureLockButton.setVisibility(visibility);

			}
		});
    }

}
