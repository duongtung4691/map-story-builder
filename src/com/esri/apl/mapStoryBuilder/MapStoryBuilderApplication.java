package com.esri.apl.mapStoryBuilder;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;

import android.app.Application;

public class MapStoryBuilderApplication extends Application {
	public DropboxAPI<AndroidAuthSession> dbApi;
}
