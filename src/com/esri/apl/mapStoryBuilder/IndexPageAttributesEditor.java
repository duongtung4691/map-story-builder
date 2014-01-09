package com.esri.apl.mapStoryBuilder;

import java.io.File;
import java.io.FileFilter;

import pl.polidea.coverflow.AbstractCoverFlowImageAdapter;
import pl.polidea.coverflow.CoverFlow;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.Session.AccessType;
import com.esri.apl.mapStoryBuilder.tasks.UploadMapStoryToDropbox;
import com.esri.apl.mapStoryBuilder.tasks.UploadMapStoryToDropboxSvc;
import com.esri.apl.mapStoryBuilder.tasks.ZipAndShip;
import com.esri.apl.mapStoryBuilder.utils.FileUtils;
import com.esri.apl.mapStoryBuilder.utils.PrefsUtils;
import com.esri.apl.mapStoryBuilder.utils.UIUtils;

public class IndexPageAttributesEditor extends Activity implements OnClickListener{
	// Dropbox variables
	private final static AccessType 		ACCESS_TYPE = AccessType.DROPBOX;

	private CoverFlow						cfPhotoGallery;
	private File							localStoryDir;
	private Button							btnUpload;
	private Button							btnSendArchive;
//	private UploadMapStoryToDropbox 		uploadTask;
	private ZipAndShip						zipShipTask;
//	private DropboxAPI<AndroidAuthSession>	mDBApi;
	
	private EditText						txtMapTitle;
	private EditText						txtMapSubtitle;
	private EditText						txtIntroTitle;
	private EditText						txtIntroDesc;
	private Spinner							spnBasemap;
	private CheckBox						chkUploadWebTemplateFiles;
	private String[]						aryBasemapURLs;
	private boolean							currentlyAuthenticating = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Retoring from configuration change? Then restore tasks, too
//		UploadMapStoryToDropbox task = (UploadMapStoryToDropbox)getLastNonConfigurationInstance();
/*		if (task != null && task.getStatus() != Status.FINISHED) {
			uploadTask = task;
			uploadTask.attach(IndexPageAttributesEditor.this);
		}*/
		// Other OnCreate setup
		String sMapStoryName = getIntent().getStringExtra(getString(R.string.chosenStoryName));
		localStoryDir = new File(getExternalFilesDir(null), sMapStoryName);
		setContentView(R.layout.editindexpageattributes);
		cfPhotoGallery = (CoverFlow)findViewById(R.id.cfPhotoGallery);
		PhotoDirCoverFlowAdapter adapter = new PhotoDirCoverFlowAdapter(this);
		cfPhotoGallery.setAdapter(adapter);
		
		btnUpload = (Button)findViewById(R.id.btnUploadToDropbox);
		btnUpload.setOnClickListener(this);
		
		btnSendArchive = (Button)findViewById(R.id.btnSendArchive);
		btnSendArchive.setOnClickListener(this);
		
		txtMapTitle = (EditText)findViewById(R.id.txtMapTitle);
		txtMapTitle.setText(sMapStoryName);
		
		txtMapSubtitle = (EditText)findViewById(R.id.txtMapSubtitle);
		txtIntroTitle = (EditText)findViewById(R.id.txtIntroTitle);
		txtIntroDesc = (EditText)findViewById(R.id.txtIntroDesc);
		spnBasemap = (Spinner)findViewById(R.id.spnBasemaps);
		chkUploadWebTemplateFiles = (CheckBox)findViewById(R.id.chkUploadWebTemplateFiles);
		aryBasemapURLs = getResources().getStringArray(R.array.baseMapURLsArray);
	}

	private class PhotoDirCoverFlowAdapter extends AbstractCoverFlowImageAdapter {
		private File[] images;
		private String filter;
		
		public PhotoDirCoverFlowAdapter(Context context) {
			super();
			filter = context.getString(R.string.rescaledPhotoFileSuffix) + ".jpg";
			File photoDir = new File(localStoryDir, 
					context.getString(R.string.photosDirectoryName));
			images = photoDir.listFiles(new FileFilter() {
				
				@Override
				public boolean accept(File pathname) {
					return !pathname.isDirectory() && pathname.getName().toLowerCase().endsWith(filter);
				}
			});
		}
		
		@Override
		public int getCount() {
			return (images != null) ? images.length : 0;
		}

		@Override
		protected Bitmap createBitmap(int arg0) {
			if (images == null) return null;
			Bitmap bitmap = BitmapFactory.decodeFile(images[arg0].getPath());
			return bitmap;
		}
		
		public String getImageRelativeUrl(int pos) {
			File image = images[pos];
			return getString(R.string.photosDirectoryName) + "/" + image.getName();
		}
	}

	@Override
	public void onClick(View v) {
		// First update index.html config section with user-entered items from this activity
		updateIndexFile();
		
		// Now upload everything
		if (v == btnUpload) {
			// If keys from previous session stored, use those; otherwise begin standard authentication
			Context context = this;
			String key = PrefsUtils.getPref(context, context.getString(R.string.dropboxUserToken_key));
			String secret = PrefsUtils.getPref(context, context.getString(R.string.dropboxUserToken_secret));
			AndroidAuthSession session = buildDropboxSession(key, secret);
			
			DropboxAPI<AndroidAuthSession> dbApi = new DropboxAPI<AndroidAuthSession>(session);
			((MapStoryBuilderApplication) getApplication()).dbApi = dbApi; 
			
			// 3 possibilities:			
			if (!dbApi.getSession().isLinked()) {
				// Possibility 1: User not yet signed in; need to initiate authentication
				// (this will invoke another application, then return; upload is handled in onResume)
				currentlyAuthenticating = true;
				dbApi.getSession().startAuthentication(context);
			}
			else {
				// Possibility 2: User already signed in; continue with upload
				/*uploadTask = new UploadMapStoryToDropbox(localStoryDir.getName(), chkUploadWebTemplateFiles.isChecked(), IndexPageAttributesEditor.this);
				uploadTask.execute(mDBApi);*/
				uploadFiles();
			}
			// Possibility 3: User tried to sign in but failed for some reason
			// (this is handled in onResume)
		}
		else if (v == btnSendArchive) {
			zipShipTask = new ZipAndShip(localStoryDir.getName(), chkUploadWebTemplateFiles.isChecked(), this);
			zipShipTask.execute();
		}
	}
/*	@Override
	public void onClick(View v) {
		// First update index.html config section with user-entered items from this activity
		updateIndexFile();
		
		// Now upload everything
		if (v == btnUpload) {
			// If keys from previous session stored, use those; otherwise begin standard authentication
			Context context = this;
			String key = PrefsUtils.getPref(context, context.getString(R.string.dropboxUserToken_key));
			String secret = PrefsUtils.getPref(context, context.getString(R.string.dropboxUserToken_secret));
			AndroidAuthSession session = buildDropboxSession(key, secret);
			
			mDBApi = new DropboxAPI<AndroidAuthSession>(session);
			// 3 possibilities:			
			if (!mDBApi.getSession().isLinked()) {
				// Possibility 1: User not yet signed in; need to initiate authentication
				// (this will invoke another application, then return; upload is handled in onResume)
				currentlyAuthenticating = true;
				mDBApi.getSession().startAuthentication(context);
			}
			else {
				// Possibility 2: User already signed in; continue with upload
				uploadTask = new UploadMapStoryToDropbox(localStoryDir.getName(), chkUploadWebTemplateFiles.isChecked(), IndexPageAttributesEditor.this);
				uploadTask.execute(mDBApi);
			}
			// Possibility 3: User tried to sign in but failed for some reason
			// (this is handled in onResume)
		}
		else if (v == btnSendArchive) {
			zipShipTask = new ZipAndShip(localStoryDir.getName(), chkUploadWebTemplateFiles.isChecked(), this);
			zipShipTask.execute();
		}
	}*/
	
	private void uploadFiles() {
		Intent intent = new Intent();
		intent.setClass(this, UploadMapStoryToDropboxSvc.class);
		intent.putExtra("storyDir", localStoryDir.getAbsolutePath());
		intent.putExtra("uploadTemplateFiles", chkUploadWebTemplateFiles.isChecked());
		startService(intent);
	}

/** Reads in template index_template.html file, makes some configuration substitutions,
	 *  and saves back out to an index.html file that will be uploaded or shipped.
	 */
	private void updateIndexFile() {
		String sIndexTemplateFile = getString(R.string.indexHTMLTemplateFileName);
		File indexTemplateFile = new File(localStoryDir, sIndexTemplateFile);
		String sIndexFile = getString(R.string.indexHTMLFileName);
		File indexFile = new File(localStoryDir, sIndexFile);
		String sIndexFileContents = null;
		try {
			sIndexFileContents = FileUtils.readFileAsString(indexTemplateFile);
			
			sIndexFileContents = sIndexFileContents.replaceFirst(
				"var TITLE = \\\"Title goes here\\\";", 
				"var TITLE = \\\"" + txtMapTitle.getText().toString() + "\\\";");
			sIndexFileContents = sIndexFileContents.replaceFirst(
				"var BYLINE = \"Subtitle goes here\";",
				"var BYLINE = \"" + txtMapSubtitle.getText().toString() + "\";");
			sIndexFileContents = sIndexFileContents.replaceFirst(
				"var INTRO_NAME = \"Intro name goes here\";",
				"var INTRO_NAME = \"" + txtIntroTitle.getText().toString() + "\";");
			sIndexFileContents = sIndexFileContents.replaceFirst(
				"var INTRO_DESCRIPTION = \"Intro description goes here\";",
				"var INTRO_DESCRIPTION = \"" + txtIntroDesc.getText().toString() + "\";");
			String sBasemapUrl = aryBasemapURLs[spnBasemap.getSelectedItemPosition()];
			sIndexFileContents = sIndexFileContents.replaceFirst(
				"var BASEMAP_URL = \"http://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer\";",
				"var BASEMAP_URL = \"" + sBasemapUrl + "\";");
			int nSelectedPhoto = cfPhotoGallery.getSelectedItemPosition();
			PhotoDirCoverFlowAdapter adapter = (PhotoDirCoverFlowAdapter) cfPhotoGallery.getAdapter();
			String sPhotoRelativeUrl = adapter.getImageRelativeUrl(nSelectedPhoto);
			sIndexFileContents = sIndexFileContents.replaceFirst(
				"var INTRO_PICTURE = \"Intro picture URL goes here\";",
				"var INTRO_PICTURE = \"" + sPhotoRelativeUrl + "\";");
			
			FileUtils.saveStringAsFile(sIndexFileContents, indexFile);
		} catch (Exception e) {
			UIUtils.showAlert(this, "Problem reading index file:\n" + e.getMessage());
		}
	}
	
	private AndroidAuthSession buildDropboxSession(String key, String secret) {
		AppKeyPair appKeys = new AppKeyPair(
				getString(R.string.dropboxApp_key), getString(R.string.dropboxApp_secret));
		AndroidAuthSession session;
		if (key == null && secret == null) { // Need new authentication token
			session = new AndroidAuthSession(appKeys, ACCESS_TYPE);
		}
		else { // old credentials will do, probably
			AccessTokenPair accessToken = new AccessTokenPair(key, secret);
			session = new AndroidAuthSession(appKeys, ACCESS_TYPE, accessToken);
		}
		return session;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		if (currentlyAuthenticating) {
			currentlyAuthenticating = false;
			DropboxAPI<AndroidAuthSession> dbApi = ((MapStoryBuilderApplication) getApplication()).dbApi;
			if (dbApi.getSession() == null) // Dropbox authentication was unsuccessful
				UIUtils.showAlert(this, "This requires a free Dropbox account.");
			// Dropbox authentication has been attempted - maybe successfully, maybe not
			if (dbApi.getSession().authenticationSuccessful()) { // Dropbox authentication was successful
				try {
					// MANDATORY call to complete auth.
					// Sets the access token on the session
					dbApi.getSession().finishAuthentication();

					AccessTokenPair tokens = dbApi.getSession().getAccessTokenPair();

					// Provide your own storeKeys to persist the access token pair
					// A typical way to store tokens is using SharedPreferences
					PrefsUtils.setPref(this, getString(R.string.dropboxUserToken_key), tokens.key);
					PrefsUtils.setPref(this, getString(R.string.dropboxUserToken_secret), tokens.secret);

					// Currently, the user only invokes authentication by clicking the "Share to Dropbox" button
					// So we know to continue the upload once authentication succeeds
					uploadFiles();
/*					uploadTask = new UploadMapStoryToDropbox(localStoryDir.getName(), chkUploadWebTemplateFiles.isChecked(), IndexPageAttributesEditor.this);
					uploadTask.execute(dbApi);*/
				} catch (IllegalStateException e) {
					Log.i("DbAuthLog", "Error authenticating", e);
					UIUtils.showAlert(this, "Encountered a problem authenticating with Dropbox:\n" + e.getMessage());
				}
			}
		}
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		
		txtMapTitle.setText(savedInstanceState.getString("mapTitle"));
		txtIntroTitle.setText(savedInstanceState.getString("introTitle"));
		txtMapSubtitle.setText(savedInstanceState.getString("mapSubtitle"));
		txtIntroDesc.setText(savedInstanceState.getString("introDesc"));
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		outState.putString("mapTitle", txtMapTitle.getText().toString());
		outState.putString("introTitle", txtIntroTitle.getText().toString());
		outState.putString("mapSubtitle", txtMapSubtitle.getText().toString());
		outState.putString("introDesc", txtIntroDesc.getText().toString());
	}

/*	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// TODO Auto-generated method stub
		super.onActivityResult(requestCode, resultCode, data);
	}*/
/*	@Override
	public Object onRetainNonConfigurationInstance() {
		// We know the activity is going away and we need to save the asynctasks
		if (uploadTask != null) {
			uploadTask.detach();
		}
		return uploadTask;
	}*/
}
