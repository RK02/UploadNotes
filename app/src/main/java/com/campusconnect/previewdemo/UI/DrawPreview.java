package com.campusconnect.previewdemo.UI;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Calendar;

import com.campusconnect.previewdemo.MainActivity;
import com.campusconnect.previewdemo.MyApplicationInterface;
import com.campusconnect.previewdemo.PreferenceKeys;
import com.campusconnect.previewdemo.R;
import com.campusconnect.previewdemo.CameraController.CameraController;
import com.campusconnect.previewdemo.Preview.Preview;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

public class DrawPreview {
	private static final String TAG = "DrawPreview";

	private MainActivity main_activity = null;
	private MyApplicationInterface applicationInterface = null;

	private Paint p = new Paint();
	private RectF face_rect = new RectF();
	private RectF draw_rect = new RectF();
	private int [] gui_location = new int[2];
	private DecimalFormat decimalFormat = new DecimalFormat("#0.0");
	private float stroke_width = 0.0f;

	private float free_memory_gb = -1.0f;
	private long last_free_memory_time = 0;

	private IntentFilter battery_ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
	private boolean has_battery_frac = false;
	private float battery_frac = 0.0f;
	private long last_battery_time = 0;

	private Bitmap location_bitmap = null;
	private Bitmap location_off_bitmap = null;
	private Rect location_dest = new Rect();
	
	private Bitmap last_thumbnail = null; // thumbnail of last picture taken
	private boolean thumbnail_anim = false; // whether we are displaying the thumbnail animation
	private long thumbnail_anim_start_ms = -1; // time that the thumbnail animation started
	private RectF thumbnail_anim_src_rect = new RectF();
	private RectF thumbnail_anim_dst_rect = new RectF();
	private Matrix thumbnail_anim_matrix = new Matrix();

    private boolean taking_picture = false;
    
	private boolean continuous_focus_moving = false;
	private long continuous_focus_moving_ms = 0;

	public DrawPreview(MainActivity main_activity, MyApplicationInterface applicationInterface) {
		this.main_activity = main_activity;
		this.applicationInterface = applicationInterface;

		p.setAntiAlias(true);
        p.setStrokeCap(Paint.Cap.ROUND);
		final float scale = getContext().getResources().getDisplayMetrics().density;
		this.stroke_width = (float) (0.5f * scale + 0.5f); // convert dps to pixels
		p.setStrokeWidth(stroke_width);

        location_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.mipmap.earth);
    	location_off_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.mipmap.earth_off);
	}

	private Context getContext() {
    	return main_activity;
    }
	
	public void updateThumbnail(Bitmap thumbnail) {
		if( applicationInterface.getThumbnailAnimationPref() ) {
			thumbnail_anim = true;
			thumbnail_anim_start_ms = System.currentTimeMillis();
		}
    	Bitmap old_thumbnail = this.last_thumbnail;
    	this.last_thumbnail = thumbnail;
    	if( old_thumbnail != null ) {
    		// only recycle after we've set the new thumbnail
    		old_thumbnail.recycle();
    	}
	}
    
	public boolean hasThumbnailAnimation() {
		return this.thumbnail_anim;
	}

	public void cameraInOperation(boolean in_operation) {
    	if( in_operation ) {
    		taking_picture = true;
    	}
    	else {
    		taking_picture = false;
    	}
    }

	public void onContinuousFocusMove(boolean start) {
		if( start ) {
			if( !continuous_focus_moving ) { // don't restart the animation if already in motion
				continuous_focus_moving = true;
				continuous_focus_moving_ms = System.currentTimeMillis();
			}
		}
		// if we receive start==false, we don't stop the animation - let it continue
	}

	public void clearContinuousFocusMove() {
		continuous_focus_moving = false;
		continuous_focus_moving_ms = 0;
	}

	private boolean getTakePhotoBorderPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getTakePhotoBorderPreferenceKey(), true);
    }
    
    private int getAngleHighlightColor() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		String color = sharedPreferences.getString(PreferenceKeys.getShowAngleHighlightColorPreferenceKey(), "#14e715");
		return Color.parseColor(color);
    }

    private String getTimeStringFromSeconds(long time) {
    	int secs = (int)(time % 60);
    	time /= 60;
    	int mins = (int)(time % 60);
    	time /= 60;
    	long hours = time;
    	//String time_s = hours + ":" + String.format("%02d", mins) + ":" + String.format("%02d", secs) + ":" + String.format("%03d", ms);
    	String time_s = hours + ":" + String.format("%02d", mins) + ":" + String.format("%02d", secs);
    	return time_s;
    }

	public void onDrawPreview(Canvas canvas) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		Preview preview  = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();
		int ui_rotation = preview.getUIRotation();
		boolean has_level_angle = preview.hasLevelAngle();
		double level_angle = preview.getLevelAngle();
		boolean has_geo_direction = preview.hasGeoDirection();
		double geo_direction = preview.getGeoDirection();
		boolean ui_placement_right = main_activity.getMainUI().getUIPlacementRight();
		if( main_activity.getMainUI().inImmersiveMode() ) {
			String immersive_mode = sharedPreferences.getString(PreferenceKeys.getImmersiveModePreferenceKey(), "immersive_mode_low_profile");
			if( immersive_mode.equals("immersive_mode_everything") ) {
				// exit, to ensure we don't display anything!
				return;
			}
		}
		final float scale = getContext().getResources().getDisplayMetrics().density;
		String preference_grid = sharedPreferences.getString(PreferenceKeys.getShowGridPreferenceKey(), "preference_grid_none");
		if( camera_controller != null && taking_picture && getTakePhotoBorderPref() ) {
			p.setColor(Color.WHITE);
			p.setStyle(Paint.Style.STROKE);
			float this_stroke_width = (float) (5.0f * scale + 0.5f); // convert dps to pixels
			p.setStrokeWidth(this_stroke_width);
			canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
			p.setStyle(Paint.Style.FILL); // reset
			p.setStrokeWidth(stroke_width); // reset
		}
		if( camera_controller != null && preference_grid.equals("preference_grid_3x3") ) {
			p.setColor(Color.WHITE);
			canvas.drawLine(canvas.getWidth()/3.0f, 0.0f, canvas.getWidth()/3.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(2.0f*canvas.getWidth()/3.0f, 0.0f, 2.0f*canvas.getWidth()/3.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(0.0f, canvas.getHeight()/3.0f, canvas.getWidth()-1.0f, canvas.getHeight()/3.0f, p);
			canvas.drawLine(0.0f, 2.0f*canvas.getHeight()/3.0f, canvas.getWidth()-1.0f, 2.0f*canvas.getHeight()/3.0f, p);
		}
		else if( camera_controller != null && preference_grid.equals("preference_grid_phi_3x3") ) {
			p.setColor(Color.WHITE);
			canvas.drawLine(canvas.getWidth()/2.618f, 0.0f, canvas.getWidth()/2.618f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(1.618f*canvas.getWidth()/2.618f, 0.0f, 1.618f*canvas.getWidth()/2.618f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(0.0f, canvas.getHeight()/2.618f, canvas.getWidth()-1.0f, canvas.getHeight()/2.618f, p);
			canvas.drawLine(0.0f, 1.618f*canvas.getHeight()/2.618f, canvas.getWidth()-1.0f, 1.618f*canvas.getHeight()/2.618f, p);
		}
		else if( camera_controller != null && preference_grid.equals("preference_grid_4x2") ) {
			p.setColor(Color.GRAY);
			canvas.drawLine(canvas.getWidth()/4.0f, 0.0f, canvas.getWidth()/4.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(canvas.getWidth()/2.0f, 0.0f, canvas.getWidth()/2.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(3.0f*canvas.getWidth()/4.0f, 0.0f, 3.0f*canvas.getWidth()/4.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(0.0f, canvas.getHeight()/2.0f, canvas.getWidth()-1.0f, canvas.getHeight()/2.0f, p);
			p.setColor(Color.WHITE);
			int crosshairs_radius = (int) (20 * scale + 0.5f); // convert dps to pixels
			canvas.drawLine(canvas.getWidth()/2.0f, canvas.getHeight()/2.0f - crosshairs_radius, canvas.getWidth()/2.0f, canvas.getHeight()/2.0f + crosshairs_radius, p);
			canvas.drawLine(canvas.getWidth()/2.0f - crosshairs_radius, canvas.getHeight()/2.0f, canvas.getWidth()/2.0f + crosshairs_radius, canvas.getHeight()/2.0f, p);
		}
		else if( camera_controller != null && preference_grid.equals("preference_grid_crosshair") ) {
			p.setColor(Color.WHITE);
			canvas.drawLine(canvas.getWidth()/2.0f, 0.0f, canvas.getWidth()/2.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(0.0f, canvas.getHeight()/2.0f, canvas.getWidth()-1.0f, canvas.getHeight()/2.0f, p);
		}
		else if( camera_controller != null && ( preference_grid.equals("preference_grid_golden_spiral_right") || preference_grid.equals("preference_grid_golden_spiral_left") || preference_grid.equals("preference_grid_golden_spiral_upside_down_right") || preference_grid.equals("preference_grid_golden_spiral_upside_down_left") ) ) {
			canvas.save();
			if( preference_grid.equals("preference_grid_golden_spiral_left") ) {
				canvas.scale(-1.0f, 1.0f, canvas.getWidth()*0.5f, canvas.getHeight()*0.5f);
			}
			else if( preference_grid.equals("preference_grid_golden_spiral_right") ) {
				// no transformation needed
			}
			else if( preference_grid.equals("preference_grid_golden_spiral_upside_down_left") ) {
				canvas.rotate(180.0f, canvas.getWidth()*0.5f, canvas.getHeight()*0.5f);
			}
			else if( preference_grid.equals("preference_grid_golden_spiral_upside_down_right") ) {
				canvas.scale(1.0f, -1.0f, canvas.getWidth()*0.5f, canvas.getHeight()*0.5f);
			}
			p.setColor(Color.WHITE);
			p.setStyle(Paint.Style.STROKE);
			int fibb = 34;
			int fibb_n = 21;
			int left = 0, top = 0;
			int full_width = canvas.getWidth();
			int full_height = canvas.getHeight();
			int width = (int)(full_width*((double)fibb_n)/(double)(fibb));
			int height = full_height;
			
			for(int count=0;count<2;count++) {
				canvas.save();
				draw_rect.set(left, top, left+width, top+height);
				canvas.clipRect(draw_rect);
				canvas.drawRect(draw_rect, p);
				draw_rect.set(left, top, left+2*width, top+2*height);
				canvas.drawOval(draw_rect, p);
				canvas.restore();
				
				int old_fibb = fibb;
				fibb = fibb_n;
				fibb_n = old_fibb - fibb;
	
				left += width;
				full_width = full_width - width;
				width = full_width;
				height = (int)(height*((double)fibb_n)/(double)(fibb));

				canvas.save();
				draw_rect.set(left, top, left+width, top+height);
				canvas.clipRect(draw_rect);
				canvas.drawRect(draw_rect, p);
				draw_rect.set(left-width, top, left+width, top+2*height);
				canvas.drawOval(draw_rect, p);
				canvas.restore();
	
				old_fibb = fibb;
				fibb = fibb_n;
				fibb_n = old_fibb - fibb;
	
				top += height;
				full_height = full_height - height;
				height = full_height;
				width = (int)(width*((double)fibb_n)/(double)(fibb));
				left += full_width - width;

				canvas.save();
				draw_rect.set(left, top, left+width, top+height);
				canvas.clipRect(draw_rect);
				canvas.drawRect(draw_rect, p);
				draw_rect.set(left-width, top-height, left+width, top+height);
				canvas.drawOval(draw_rect, p);
				canvas.restore();
	
				old_fibb = fibb;
				fibb = fibb_n;
				fibb_n = old_fibb - fibb;
	
				full_width = full_width - width;
				width = full_width;
				left -= width;
				height = (int)(height*((double)fibb_n)/(double)(fibb));
				top += full_height - height;

				canvas.save();
				draw_rect.set(left, top, left+width, top+height);
				canvas.clipRect(draw_rect);
				canvas.drawRect(draw_rect, p);
				draw_rect.set(left, top-height, left+2*width, top+height);
				canvas.drawOval(draw_rect, p);
				canvas.restore();

				old_fibb = fibb;
				fibb = fibb_n;
				fibb_n = old_fibb - fibb;

				full_height = full_height - height;
				height = full_height;
				top -= height;
				width = (int)(width*((double)fibb_n)/(double)(fibb));
			}
			
			canvas.restore();
			p.setStyle(Paint.Style.FILL); // reset
		}
		else if( camera_controller != null && ( preference_grid.equals("preference_grid_golden_triangle_1") || preference_grid.equals("preference_grid_golden_triangle_2") ) ) {
			p.setColor(Color.WHITE);
			double theta = Math.atan2(canvas.getWidth(), canvas.getHeight());
			double dist = canvas.getHeight() * Math.cos(theta);
			float dist_x = (float)(dist * Math.sin(theta));
			float dist_y = (float)(dist * Math.cos(theta));
			if( preference_grid.equals("preference_grid_golden_triangle_1") ) {
				canvas.drawLine(0.0f, canvas.getHeight()-1.0f, canvas.getWidth()-1.0f, 0.0f, p);
				canvas.drawLine(0.0f, 0.0f, dist_x, canvas.getHeight()-dist_y, p);
				canvas.drawLine(canvas.getWidth()-1.0f-dist_x, dist_y-1.0f, canvas.getWidth()-1.0f, canvas.getHeight()-1.0f, p);
			}
			else {
				canvas.drawLine(0.0f, 0.0f, canvas.getWidth()-1.0f, canvas.getHeight()-1.0f, p);
				canvas.drawLine(canvas.getWidth()-1.0f, 0.0f, canvas.getWidth()-1.0f-dist_x, canvas.getHeight()-dist_y, p);
				canvas.drawLine(dist_x, dist_y-1.0f, 0.0f, canvas.getHeight()-1.0f, p);
			}
		}
		else if( camera_controller != null && preference_grid.equals("preference_grid_diagonals") ) {
			p.setColor(Color.WHITE);
			canvas.drawLine(0.0f, 0.0f, canvas.getHeight()-1.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(canvas.getHeight()-1.0f, 0.0f, 0.0f, canvas.getHeight()-1.0f, p);
			int diff = canvas.getWidth() - canvas.getHeight();
			if( diff > 0 ) {
				canvas.drawLine(diff, 0.0f, diff+canvas.getHeight()-1.0f, canvas.getHeight()-1.0f, p);
				canvas.drawLine(diff+canvas.getHeight()-1.0f, 0.0f, diff, canvas.getHeight()-1.0f, p);
			}
		}

		if( sharedPreferences.getString(PreferenceKeys.getPreviewSizePreferenceKey(), "preference_preview_size_wysiwyg").equals("preference_preview_size_wysiwyg") ) {
			String preference_crop_guide = sharedPreferences.getString(PreferenceKeys.getShowCropGuidePreferenceKey(), "crop_guide_none");
			if( camera_controller != null && preview.getTargetRatio() > 0.0 && !preference_crop_guide.equals("crop_guide_none") ) {
				p.setStyle(Paint.Style.STROKE);
				p.setColor(Color.rgb(255, 235, 59)); // Yellow 500
				double crop_ratio = -1.0;
				if( preference_crop_guide.equals("crop_guide_1") ) {
					crop_ratio = 1.0;
				}
				else if( preference_crop_guide.equals("crop_guide_1.25") ) {
					crop_ratio = 1.25;
				}
				else if( preference_crop_guide.equals("crop_guide_1.33") ) {
					crop_ratio = 1.33333333;
				}
				else if( preference_crop_guide.equals("crop_guide_1.4") ) {
					crop_ratio = 1.4;
				}
				else if( preference_crop_guide.equals("crop_guide_1.5") ) {
					crop_ratio = 1.5;
				}
				else if( preference_crop_guide.equals("crop_guide_1.78") ) {
					crop_ratio = 1.77777778;
				}
				else if( preference_crop_guide.equals("crop_guide_1.85") ) {
					crop_ratio = 1.85;
				}
				else if( preference_crop_guide.equals("crop_guide_2.33") ) {
					crop_ratio = 2.33333333;
				}
				else if( preference_crop_guide.equals("crop_guide_2.35") ) {
					crop_ratio = 2.35006120; // actually 1920:817
				}
				else if( preference_crop_guide.equals("crop_guide_2.4") ) {
					crop_ratio = 2.4;
				}
				if( crop_ratio > 0.0 && Math.abs(preview.getTargetRatio() - crop_ratio) > 1.0e-5 ) {
		    		/*if( MyDebug.LOG ) {
		    			Log.d(TAG, "crop_ratio: " + crop_ratio);
		    			Log.d(TAG, "preview_targetRatio: " + preview_targetRatio);
		    			Log.d(TAG, "canvas width: " + canvas.getWidth());
		    			Log.d(TAG, "canvas height: " + canvas.getHeight());
		    		}*/
					int left = 1, top = 1, right = canvas.getWidth()-1, bottom = canvas.getHeight()-1;
					if( crop_ratio > preview.getTargetRatio() ) {
						// crop ratio is wider, so we have to crop top/bottom
						double new_hheight = ((double)canvas.getWidth()) / (2.0f*crop_ratio);
						top = (int)(canvas.getHeight()/2 - (int)new_hheight);
						bottom = (int)(canvas.getHeight()/2 + (int)new_hheight);
					}
					else {
						// crop ratio is taller, so we have to crop left/right
						double new_hwidth = (((double)canvas.getHeight()) * crop_ratio) / 2.0f;
						left = (int)(canvas.getWidth()/2 - (int)new_hwidth);
						right = (int)(canvas.getWidth()/2 + (int)new_hwidth);
					}
					canvas.drawRect(left, top, right, bottom, p);
				}
				p.setStyle(Paint.Style.FILL); // reset
			}
		}

		// note, no need to check preferences here, as we do that when setting thumbnail_anim
		if( camera_controller != null && this.thumbnail_anim && last_thumbnail != null ) {
			long time = System.currentTimeMillis() - this.thumbnail_anim_start_ms;
			final long duration = 500;
			if( time > duration ) {
				this.thumbnail_anim = false;
			}
			else {
				thumbnail_anim_src_rect.left = 0;
				thumbnail_anim_src_rect.top = 0;
				thumbnail_anim_src_rect.right = last_thumbnail.getWidth();
				thumbnail_anim_src_rect.bottom = last_thumbnail.getHeight();
				float alpha = ((float)time)/(float)duration;

				int st_x = canvas.getWidth()/2;
				int st_y = canvas.getHeight()/2;

				float st_w = canvas.getWidth();
				float st_h = canvas.getHeight();
				//int thumbnail_w = (int)( (1.0f-alpha)*st_w + alpha*nd_w );
				//int thumbnail_h = (int)( (1.0f-alpha)*st_h + alpha*nd_h );
				//canvas.drawBitmap(this.thumbnail, thumbnail_anim_src_rect, thumbnail_anim_dst_rect, p);
				thumbnail_anim_matrix.setRectToRect(thumbnail_anim_src_rect, thumbnail_anim_dst_rect, Matrix.ScaleToFit.FILL);
				//thumbnail_anim_matrix.reset();
				if( ui_rotation == 90 || ui_rotation == 270 ) {
					float ratio = ((float)last_thumbnail.getWidth())/(float)last_thumbnail.getHeight();
					thumbnail_anim_matrix.preScale(ratio, 1.0f/ratio, last_thumbnail.getWidth()/2.0f, last_thumbnail.getHeight()/2.0f);
				}
				thumbnail_anim_matrix.preRotate(ui_rotation, last_thumbnail.getWidth()/2.0f, last_thumbnail.getHeight()/2.0f);
				canvas.drawBitmap(last_thumbnail, thumbnail_anim_matrix, p);
			}
		}
		
		canvas.save();
		canvas.rotate(ui_rotation, canvas.getWidth()/2.0f, canvas.getHeight()/2.0f);


		if( camera_controller == null ) {
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "no camera!");
				Log.d(TAG, "width " + canvas.getWidth() + " height " + canvas.getHeight());
			}*/
			p.setColor(Color.WHITE);
			p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
			p.setTextAlign(Paint.Align.CENTER);
			int pixels_offset = (int) (20 * scale + 0.5f); // convert dps to pixels
			canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_1), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f, p);
			canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_2), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f + pixels_offset, p);
			canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_3), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f + 2*pixels_offset, p);
			//canvas.drawRect(0.0f, 0.0f, 100.0f, 100.0f, p);
			//canvas.drawRGB(255, 0, 0);
			//canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
		}

		canvas.restore();
		
		if( camera_controller != null && !preview.isPreviewPaused() && has_level_angle && sharedPreferences.getBoolean(PreferenceKeys.getShowAngleLinePreferenceKey(), false) ) {
			// n.b., must draw this without the standard canvas rotation
			int radius_dps = (ui_rotation == 90 || ui_rotation == 270) ? 60 : 80;
			int radius = (int) (radius_dps * scale + 0.5f); // convert dps to pixels
			double angle = - preview.getOrigLevelAngle();
			// see http://android-developers.blogspot.co.uk/2010/09/one-screen-turn-deserves-another.html
		    int rotation = main_activity.getWindowManager().getDefaultDisplay().getRotation();
		    switch (rotation) {
	    	case Surface.ROTATION_90:
	    	case Surface.ROTATION_270:
	    		angle += 90.0;
	    		break;
    		default:
    			break;
		    }
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "orig_level_angle: " + orig_level_angle);
				Log.d(TAG, "angle: " + angle);
			}*/
			int cx = canvas.getWidth()/2;
			int cy = canvas.getHeight()/2;
			
			boolean is_level = false;

			if( is_level ) {
				radius = (int)(radius * 1.2);
			}

			canvas.save();
			canvas.rotate((float)angle, cx, cy);

			final int line_alpha = 96;
			p.setStyle(Paint.Style.FILL);
			float hthickness = (0.5f * scale + 0.5f); // convert dps to pixels
			// draw outline
			p.setColor(Color.BLACK);
			p.setAlpha(64);
			// can't use drawRoundRect(left, top, right, bottom, ...) as that requires API 21
			draw_rect.set(cx - radius - hthickness, cy - 2*hthickness, cx + radius + hthickness, cy + 2*hthickness);
			canvas.drawRoundRect(draw_rect, 2*hthickness, 2*hthickness, p);
			// draw the vertical crossbar
			draw_rect.set(cx - 2*hthickness, cy - radius/2 - hthickness, cx + 2*hthickness, cy + radius/2 + hthickness);
			canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
			// draw inner portion
			if( is_level ) {
				p.setColor(getAngleHighlightColor());
			}
			else {
				p.setColor(Color.WHITE);
			}
			p.setAlpha(line_alpha);
			draw_rect.set(cx - radius, cy - hthickness, cx + radius, cy + hthickness);
			canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
			
			// draw the vertical crossbar
			draw_rect.set(cx - hthickness, cy - radius/2, cx + hthickness, cy + radius/2);
			canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);

			if( is_level ) {
				// draw a second line

				p.setColor(Color.BLACK);
				p.setAlpha(64);
				draw_rect.set(cx - radius - hthickness, cy - 7*hthickness, cx + radius + hthickness, cy - 3*hthickness);
				canvas.drawRoundRect(draw_rect, 2*hthickness, 2*hthickness, p);

				p.setColor(getAngleHighlightColor());
				p.setAlpha(line_alpha);
				draw_rect.set(cx - radius, cy - 6*hthickness, cx + radius, cy - 4*hthickness);
				canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
			}
			p.setAlpha(255);
			p.setStyle(Paint.Style.FILL); // reset

			canvas.restore();
		}

		if( camera_controller != null && continuous_focus_moving ) {
			long dt = System.currentTimeMillis() - continuous_focus_moving_ms;
			final long length = 1000;
			if( dt <= length ) {
				float frac = ((float)dt) / (float)length;
				float pos_x = canvas.getWidth()/2.0f;
				float pos_y = canvas.getHeight()/2.0f;
				float min_radius = (float) (40 * scale + 0.5f); // convert dps to pixels
				float max_radius = (float) (60 * scale + 0.5f); // convert dps to pixels
				float radius = 0.0f;
				if( frac < 0.5f ) {
					float alpha = frac*2.0f;
					radius = (1.0f-alpha) * min_radius + alpha * max_radius;
				}
				else {
					float alpha = (frac-0.5f)*2.0f;
					radius = (1.0f-alpha) * max_radius + alpha * min_radius;
				}
				/*if( MyDebug.LOG ) {
					Log.d(TAG, "dt: " + dt);
					Log.d(TAG, "radius: " + radius);
				}*/
				p.setStyle(Paint.Style.STROKE);
				canvas.drawCircle(pos_x, pos_y, radius, p);
				p.setStyle(Paint.Style.FILL); // reset
			}
			else {
				continuous_focus_moving = false;
			}
		}

		if( preview.isFocusWaiting() || preview.isFocusRecentSuccess() || preview.isFocusRecentFailure() ) {
			long time_since_focus_started = preview.timeSinceStartedAutoFocus();
			float min_radius = (float) (40 * scale + 0.5f); // convert dps to pixels
			float max_radius = (float) (45 * scale + 0.5f); // convert dps to pixels
			float radius = min_radius;
			if( time_since_focus_started > 0 ) {
				final long length = 500;
				float frac = ((float)time_since_focus_started) / (float)length;
				if( frac > 1.0f )
					frac = 1.0f;
				if( frac < 0.5f ) {
					float alpha = frac*2.0f;
					radius = (1.0f-alpha) * min_radius + alpha * max_radius;
				}
				else {
					float alpha = (frac-0.5f)*2.0f;
					radius = (1.0f-alpha) * max_radius + alpha * min_radius;
				}
			}
			int size = (int)radius;

			if( preview.isFocusRecentSuccess() )
				p.setColor(Color.rgb(20, 231, 21)); // Green A400
			else if( preview.isFocusRecentFailure() )
				p.setColor(Color.rgb(244, 67, 54)); // Red 500
			else
				p.setColor(Color.WHITE);
			p.setStyle(Paint.Style.STROKE);
			int pos_x = 0;
			int pos_y = 0;
			if( preview.hasFocusArea() ) {
				Pair<Integer, Integer> focus_pos = preview.getFocusPos();
				pos_x = focus_pos.first;
				pos_y = focus_pos.second;
			}
			else {
				pos_x = canvas.getWidth() / 2;
				pos_y = canvas.getHeight() / 2;
			}
			float frac = 0.5f;
			// horizontal strokes
			canvas.drawLine(pos_x - size, pos_y - size, pos_x - frac*size, pos_y - size, p);
			canvas.drawLine(pos_x + frac*size, pos_y - size, pos_x + size, pos_y - size, p);
			canvas.drawLine(pos_x - size, pos_y + size, pos_x - frac*size, pos_y + size, p);
			canvas.drawLine(pos_x + frac*size, pos_y + size, pos_x + size, pos_y + size, p);
			// vertical strokes
			canvas.drawLine(pos_x - size, pos_y - size, pos_x - size, pos_y - frac*size, p);
			canvas.drawLine(pos_x - size, pos_y + frac*size, pos_x - size, pos_y + size, p);
			canvas.drawLine(pos_x + size, pos_y - size, pos_x + size, pos_y - frac*size, p);
			canvas.drawLine(pos_x + size, pos_y + frac*size, pos_x + size, pos_y + size, p);
			p.setStyle(Paint.Style.FILL); // reset
		}

    }
}
