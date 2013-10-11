package com.esri.apl.mapStoryBuilder.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import com.esri.apl.mapStoryBuilder.R;

public class UIUtils {
    public static void showAlert(Context context, String message) {
    	
    	showAlert(context, message, null, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
    }
    public static void showAlert(Context context, String message, String title, DialogInterface.OnClickListener onClick) {
    	new AlertDialog.Builder(context)
    	.setTitle(title)
    	.setMessage(message)
    	.setPositiveButton(R.string.alert_closeButtonText, onClick)
    	.show();
    }

}
