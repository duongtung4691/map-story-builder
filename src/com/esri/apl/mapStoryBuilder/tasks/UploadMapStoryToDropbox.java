package com.esri.apl.mapStoryBuilder.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.AsyncTask;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.URLSpan;
import android.util.Log;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;
import com.esri.apl.mapStoryBuilder.R;
import com.esri.apl.mapStoryBuilder.utils.FileException;
import com.esri.apl.mapStoryBuilder.utils.FileUtils;
import com.esri.apl.mapStoryBuilder.utils.PrefsUtils;
import com.esri.apl.mapStoryBuilder.utils.UIUtils;

public class UploadMapStoryToDropbox 
	extends AsyncTask<DropboxAPI<AndroidAuthSession>, Integer, Vector<FileException>> {

	// Declarations
//	private static final String TEMPLATE_ZIPFILE = "template.zip";
	private static final String TAG = "UploadMapStoryToDropbox"; 
	private ProgressDialog	m_dlgProgress;
	private String			m_sMapName;
	private Context			m_context;
	private File			m_localStoryDir;
	private boolean			m_canceled = false;
	private boolean			m_uploadWebTemplateFiles;
	private long			m_dropboxUserId = Long.MIN_VALUE; // Assumption: no valid dropbox ID has this value
	private Entry			m_uploadedIndexFile;
	private int				m_maxProgressVal; // Right end of progress indicator
	private int				m_currentProgressVal = 0;
	
	public UploadMapStoryToDropbox(String sMapName, boolean bUploadWebTemplateFiles, Context context) {
		attach(context);
		m_sMapName = sMapName;
		m_localStoryDir = new File(context.getExternalFilesDir(null), sMapName);
		m_uploadWebTemplateFiles = bUploadWebTemplateFiles;		
	}
	
	// Functions
	@Override
	protected Vector<FileException> doInBackground(DropboxAPI<AndroidAuthSession>... params) {
		DropboxAPI<AndroidAuthSession> api = params[0];
		Vector<FileException> exceptions = new Vector<FileException>();
		try {
			m_dropboxUserId = api.accountInfo().uid;
		}
		catch (DropboxException exc) {
			m_dropboxUserId = Long.MIN_VALUE;
		}
		// Determine total # of files
		int nFilesToUpload = 3; // Main dir, CSV Config, index.html
		int nFilesUploaded = 0;

		File localWebTemplateArchive = null;
		ZipFile localWebTemplateZipFile = null;
		if (m_uploadWebTemplateFiles) {
			localWebTemplateArchive = new File(m_localStoryDir, m_context.getString(R.string.zipFileName));
			try {
				FileUtils.copyRawResource(m_context, R.raw.storytelling_maptour_template, localWebTemplateArchive);
				localWebTemplateZipFile = new ZipFile(localWebTemplateArchive);
			} catch (IOException e) {
				exceptions.addElement(new FileException(localWebTemplateArchive.getName(), e));
				return exceptions;
			}

			nFilesToUpload += localWebTemplateZipFile.size();
		}
		
		File photosDir = new File(m_localStoryDir, m_context.getString(R.string.photosDirectoryName));
		File[] photoFiles = photosDir.listFiles();
		nFilesToUpload += photoFiles.length + 1; // Include the photos directory, to be created
		
		m_maxProgressVal = nFilesToUpload;
		
		// -- END SETUP -- //
		
		// -- START UPLOADING -- //
		
		// Create main map directory
		String sRemoteStoryDir = m_context.getString(R.string.dropboxApp_baseDir) + "/" + m_sMapName;
		if (!m_canceled) try {api.createFolder(sRemoteStoryDir);} 
		catch (DropboxException e) {
			// It's just a folder that already exists. Don't log an exception for the user.
			Log.w(TAG, "Map directory " + m_sMapName + " already exists on the remote server.");
//			exceptions.addElement(new FileException(m_sMapName, e));
		}
		finally {publishProgress(++nFilesUploaded);}

		
		// Upload CSV config file
		String sCSVFilename = m_context.getString(R.string.storyConfigFilename);
		File fileConfig = new File(m_localStoryDir, sCSVFilename);
		if (!m_canceled) try {
			api.putFileOverwrite(sRemoteStoryDir + "/" + sCSVFilename, 
					new FileInputStream(fileConfig), 
					fileConfig.length(), null);
		} 
		catch (Exception e) {
			exceptions.addElement(new FileException(fileConfig.getName(), e));
			Log.e(TAG, "While saving " + sCSVFilename + ": " + e.getMessage());
		}
		finally {publishProgress(++nFilesUploaded);}

		// Upload index.html
		String sIndexFileName = m_context.getString(R.string.indexHTMLFileName);
		File fileIndex = new File(m_localStoryDir, sIndexFileName);
		if (!m_canceled) try {
			m_uploadedIndexFile = api.putFileOverwrite(sRemoteStoryDir + "/" + sIndexFileName, 
					new FileInputStream(fileIndex), 
					fileIndex.length(), null);
		} catch (Exception e) {
			exceptions.addElement(new FileException(fileIndex.getName(), e));
			Log.e(TAG, "While saving " + sIndexFileName + ": " + e.getMessage());
		}
		finally {publishProgress(++nFilesUploaded);}
		
		// Upload photos directory and photos and thumbnails
		String sRemotePhotosDir = sRemoteStoryDir + "/" + m_context.getString(R.string.photosDirectoryName);
		if (!m_canceled) try {api.createFolder(sRemotePhotosDir);} 
		catch (DropboxException e) {
//			exceptions.addElement(new FileException(sRemotePhotosDir, e));
			// Again, this isn't really a problem if the photos directory already exists
			Log.e(TAG, "While creating photos directory " + sRemotePhotosDir + ": " + e.getMessage());
		}
		finally {publishProgress(++nFilesUploaded);}
		
		for (File photoFile : photoFiles) {
			if (!m_canceled) try {
				api.putFileOverwrite(sRemotePhotosDir + "/" + photoFile.getName(), 
						new FileInputStream(photoFile), photoFile.length(), null);
			} catch (DropboxUnlinkedException e) {
				exceptions.addElement(new FileException(photoFile.getName(), e));
				// Clear out stored keys so user will be prompted to re-authenticate
				PrefsUtils.clearPref(m_context, m_context.getString(R.string.dropboxUserToken_key));
				PrefsUtils.clearPref(m_context, m_context.getString(R.string.dropboxUserToken_secret));
				break; // End loop; don't process any more files since it would be pointless
			} catch (Exception e) {
				exceptions.addElement(new FileException(photoFile.getName(), e));
				Log.e(TAG, "While uploading photo file " + sRemotePhotosDir + "/" + photoFile.getName() + ": " + e.getMessage());
			}
			finally {publishProgress(++nFilesUploaded);}

			else break; // canceled
		}
		
		if (m_uploadWebTemplateFiles) {
			// Open the template file and try to upload all files therein
			if (localWebTemplateZipFile != null) {
				String sCurrentFilePath;
				for (ZipEntry entry : Collections.list(localWebTemplateZipFile.entries())) {
					// Try to upload file		
					String sFilePathInsideZip = entry.getName();
					sCurrentFilePath = sRemoteStoryDir + "/" + sFilePathInsideZip;
					if (!m_canceled) try {
						Log.d(TAG, entry.getName());
						if (entry.isDirectory()) {
							api.createFolder(sCurrentFilePath);
						}
						else {
							api.putFileOverwrite(sCurrentFilePath, localWebTemplateZipFile.getInputStream(entry), entry.getSize(), null);
						}
					} catch (DropboxUnlinkedException e) {
						//	if DropboxUnlinkException, add to list and exit loop
						exceptions.addElement(new FileException(entry.getName().substring(entry.getName().lastIndexOf("/")), e));
						Log.e(TAG, "While uploading web template files, this dropbox unlinked exception occurred: " + e.getMessage());
						// Clear out stored keys so user will be prompted to re-authenticate
						PrefsUtils.clearPref(m_context, m_context.getString(R.string.dropboxUserToken_key));
						PrefsUtils.clearPref(m_context, m_context.getString(R.string.dropboxUserToken_secret));
						break; // End loop; don't process any more files since it would be pointless
					} catch (Exception e) {
						//	if other Exception, add to list and continue
						exceptions.addElement(new FileException(entry.getName().substring(entry.getName().lastIndexOf("/")), e));
						Log.e(TAG, "While uploading " + entry.getName() + ": " + e.getMessage());
					}
					else break; // canceled
					
					publishProgress(++nFilesUploaded);
				}
			}
			localWebTemplateArchive.delete();
		}
		
		return exceptions;
	}
	
	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		
		// Show progress bar
//		m_dlgProgress = new ProgressDialog(m_context);
//		m_dlgProgress.setCancelable(true);
//		m_dlgProgress.setButton(ProgressDialog.BUTTON_NEGATIVE, "Cancel", onCancelClick);
//		m_dlgProgress.setOnCancelListener(onCancel);
//		m_dlgProgress.setMessage("Uploading your files to Dropbox");
//		m_dlgProgress.setIndeterminate(false);
//		m_dlgProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
//		m_dlgProgress.show();
	}

	@Override
	protected void onPostExecute(Vector<FileException> result) {
		super.onPostExecute(result);
		
		// Hide progress bar
		m_dlgProgress.dismiss();
		
		// Show any exceptions
		
		if (m_canceled)
			Toast.makeText(m_context, "Upload was canceled", Toast.LENGTH_LONG).show();		
		else {
			if (result.size() > 0) {
				String sMessage = "These problems happened while saving your files:";
				for (FileException fe : result) {
					sMessage += "\n" + fe.file + ": ";
					Exception exc = fe.exception;
					if (exc instanceof DropboxServerException) {
						DropboxServerException e = (DropboxServerException) exc;
						sMessage += e.body.error;
					} else if (exc instanceof DropboxUnlinkedException) {
						sMessage += "Your Dropbox account somehow got logged out. Please try again";
					} else {
						sMessage += exc.getMessage();
					}
				}
				UIUtils.showAlert(m_context, sMessage);
			}
			else {
				Toast.makeText(m_context, "Upload complete", Toast.LENGTH_LONG).show();
			}
			// Even if there were errors, the user can opt to send the link or cancel using the back button
			sendLink();
		}
	}
	
	@Override
	protected void onProgressUpdate(Integer... values) {
		super.onProgressUpdate(values);

		m_currentProgressVal = values[0];

		if (m_context == null)
			Log.w(TAG, "Context unavailable; skipping progress update for value " + m_currentProgressVal);
		else {
			// We have a context and the capability of displaying progress
			if (m_dlgProgress != null && m_dlgProgress.getContext() != null) {
				// Update progress bar
				m_dlgProgress.setMax(m_maxProgressVal);
				m_dlgProgress.setProgress(m_currentProgressVal);
				m_dlgProgress.show();
			}

		}
	}

	private OnCancelListener onCancel = new OnCancelListener() {
		
		@Override
		public void onCancel(DialogInterface dialog) {
			m_canceled = true;
		}
	};

	private OnClickListener onCancelClick = new OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			if (which == ProgressDialog.BUTTON_NEGATIVE)
				dialog.cancel();
		}
	};
	
	private void sendLink() {
		// Send a link
		if (m_uploadedIndexFile != null && m_dropboxUserId != Long.MIN_VALUE) {
			String sProtocol = "http";
			String sServer = "dl.dropbox.com";
			String sPath = "/u" + "/" 
					+ Long.toString(m_dropboxUserId) + "/"  
					+ m_sMapName + "/"
					+ m_context.getString(R.string.indexHTMLFileName);
			URI uri = null;
			try {
				uri = new URI(sProtocol, sServer, sPath, null);
			} catch (URISyntaxException e) {
				String sMsg = "Problem encoding link for e-mail:\n" + e.getMessage();
				Log.e(TAG, sMsg);
				UIUtils.showAlert(m_context, sMsg);
				return;
			}
			String sEncodedUrl = uri.toASCIIString();
		    SpannableStringBuilder builder = new SpannableStringBuilder();
		    builder.append("I'd like to share this photo map story web site with you:\n");
		    int start = builder.length();
		    builder.append(sEncodedUrl);
		    int end = builder.length();
		    builder.setSpan(new URLSpan(sEncodedUrl), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);		    
		    Intent intent = new Intent();
			intent.setAction(Intent.ACTION_SEND);			
			intent.setType("message/rfc822");
			intent.putExtra(Intent.EXTRA_SUBJECT, "Map Story web site link");
			intent.putExtra(Intent.EXTRA_TEXT, builder);
			m_context.startActivity(Intent.createChooser(intent, "How do you want to send this link?"));
		}
	}

	/** Attach or reattach a UI with this async thread, since the UI may get destroyed due to rotation et al **/
	public void attach(Context context) {
		m_context = context;

		// This task has become detached from its UI and the dialog must be recreated
		m_dlgProgress = new ProgressDialog(m_context);
		m_dlgProgress.setCancelable(true);
		m_dlgProgress.setButton(ProgressDialog.BUTTON_NEGATIVE, "Cancel", onCancelClick);
		m_dlgProgress.setOnCancelListener(onCancel);
		m_dlgProgress.setMessage("Uploading your files to Dropbox");
		m_dlgProgress.setIndeterminate(false);
		m_dlgProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		if (getStatus() == Status.RUNNING) {
			m_dlgProgress.setMax(m_maxProgressVal);
			m_dlgProgress.setProgress(m_currentProgressVal);
			m_dlgProgress.show();
		}
}
	public void detach() {
		m_dlgProgress.dismiss();
		m_dlgProgress = null;
		m_context = null;
	}
}
