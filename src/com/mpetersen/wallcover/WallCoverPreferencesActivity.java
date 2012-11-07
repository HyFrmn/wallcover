package com.mpetersen.wallcover;

import com.mpetersen.wallcover.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.MediaStore.MediaColumns;
import android.view.Display;
import android.widget.Toast;

public class WallCoverPreferencesActivity extends PreferenceActivity implements
		SharedPreferences.OnSharedPreferenceChangeListener {

	public static final String SHARED_PREFS_NAME = "preferences";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		PreferenceManager prefs = getPreferenceManager();
		prefs.setSharedPreferencesName(SHARED_PREFS_NAME);
		addPreferencesFromResource(R.xml.prefs);
		prefs.getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);

		prefs.findPreference("image_filepath").setSummary(prefs.getSharedPreferences().getString("image_filepath", "none"));
		
		prefs.findPreference("image_gallery")
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference preference) {
						Display display = getWindowManager()
								.getDefaultDisplay();
						int width = display.getWidth();
						int height = display.getHeight();
						Toast.makeText(getBaseContext(),
								"Select Image - " + (width) + " x " + height,
								Toast.LENGTH_LONG).show();
						Intent photoPickerIntent = new Intent(
								Intent.ACTION_PICK);
						photoPickerIntent.setType("image/*");
						startActivityForResult(photoPickerIntent, 1);
						return true;
					}
				});
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 1) {
			if (resultCode == Activity.RESULT_OK) {
				Uri selectedImage = data.getData();
				String RealPath;
				SharedPreferences customSharedPreference = getSharedPreferences(
						SHARED_PREFS_NAME, Context.MODE_PRIVATE);
				SharedPreferences.Editor editor = customSharedPreference.edit();
				RealPath = getRealPathFromURI(selectedImage);
				editor.putString("image_filepath", RealPath);
				editor.commit();
				getPreferenceManager().findPreference("image_filepath")
						.setSummary(RealPath);

			}
		}
	}

	public String getRealPathFromURI(Uri contentUri) {
		String[] proj = { MediaColumns.DATA };
		Cursor cursor = managedQuery(contentUri, proj, // Which columns to
														// return
				null, // WHERE clause; which rows to return (all rows)
				null, // WHERE clause selection arguments (none)
				null); // Order-by clause (ascending by name)
		int column_index = cursor.getColumnIndexOrThrow(MediaColumns.DATA);
		cursor.moveToFirst();
		return cursor.getString(column_index);
	}

	@Override
	protected void onDestroy() {
		getPreferenceManager().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
	}

	public void onSharedPreferenceChanged(SharedPreferences arg0, String arg1) {
		// TODO Auto-generated method stub
		
	}
}
