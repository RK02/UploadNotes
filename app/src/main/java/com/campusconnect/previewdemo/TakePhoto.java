package com.campusconnect.previewdemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/** Entry Activity for the "take photo" widget (see MyWidgetProviderTakePhoto).
 *  This redirects to MainActivity, but uses an intent extra/bundle to pass the
 *  "take photo" request.
 */
public class TakePhoto extends Activity {
	private static final String TAG = "TakePhoto";
	public static final String TAKE_PHOTO = "com.campusconnect.previewdemo.TAKE_PHOTO";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra(TAKE_PHOTO, true);
		this.startActivity(intent);
		this.finish();
	}

    protected void onResume() {
        super.onResume();
    }
}
