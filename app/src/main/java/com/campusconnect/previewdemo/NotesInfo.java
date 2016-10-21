package com.campusconnect.previewdemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Entry Activity for the "take photo" widget (see MyWidgetProviderTakePhoto).
 *  This redirects to MainActivity, but uses an intent extra/bundle to pass the
 *  "take photo" request.
 */
public class NotesInfo extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.notes_info);
	}
}
