package com.esri.apl.mapStoryBuilder.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

public class PrefsUtils {
	public static void setPref(Context context, String key, String value) {
			Editor prefs = PreferenceManager.getDefaultSharedPreferences(context).edit();
			prefs.putString(key, value);
			prefs.commit();
	}
	
	
	public static String getPref(Context context, String key) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getString(key, null);
	}
	public static String getPref(Context context, String key, String defaultValue) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getString(key, defaultValue);
	}
	
	public static void clearPref(Context context, String key) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		
		prefs.edit().remove(key).commit();
	}
}
