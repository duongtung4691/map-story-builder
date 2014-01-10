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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxUnlinkedException;
import com.esri.apl.mapStoryBuilder.IndexPageAttributesEditor;
import com.esri.apl.mapStoryBuilder.MapStoryBuilderApplication;
import com.esri.apl.mapStoryBuilder.MapStoryList;
import com.esri.apl.mapStoryBuilder.R;
import com.esri.apl.mapStoryBuilder.utils.FileException;
import com.esri.apl.mapStoryBuilder.utils.FileUtils;
import com.esri.apl.mapStoryBuilder.utils.PrefsUtils;
import com.esri.apl.mapStoryBuilder.utils.UIUtils;

public class UploadMapStoryToDropboxSvc extends Service {
	private static final String TAG = "UploadMapStoryService";
	private static final int ID = 1;
	private static final String DELIM = File.separator;

	private ATUploadFiles m_atUploadFiles= null;
	
	@Override
	public IBinder onBind(Intent arg0) {
		// This is not a bound service
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "UploadService: onStartCommand");
		
		String storyDir = intent.getStringExtra("storyDir");
		boolean uploadTemplateFiles = intent.getBooleanExtra("uploadTemplateFiles", false);

		boolean bCancelUpload = 
			intent.getBooleanExtra(getString(R.string.extraKey_cancelUploadViaNotification),  false);
		if (bCancelUpload)
			m_atUploadFiles.cancel(true);
		else if (m_atUploadFiles == null || m_atUploadFiles.getStatus() != AsyncTask.Status.RUNNING) {
//			doUpload(storyDir, uploadTemplateFiles);
			m_atUploadFiles = new ATUploadFiles();
			m_atUploadFiles.execute(storyDir, uploadTemplateFiles);
		}
		
		// If the service gets killed before completion, restart from the beginning automatically
		return Service.START_REDELIVER_INTENT; // Service.START_STICKY;
	}

/*	private void endService_Complete() {
       // When the loop is finished, updates the notification
//        String sPath = getDropboxFileUrl(mapName, dropboxUserId);
        Intent actionIntent = new Intent(this, MapStoryList.class);
//        Intent actionIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(sPath));
        PendingIntent pi = PendingIntent.getActivity(this, 0, actionIntent, 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentTitle("Upload complete")
        	.setSmallIcon(android.R.drawable.ic_menu_upload)
//                .setContentInfo("(Touch to open the StoryMap)")
            .setContentIntent(pi)
        // Removes the progress bar
            .setProgress(0,0,false)
            .setOngoing(false);
        NotificationManager notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notifyManager.notify(ID, builder.setWhen(System.currentTimeMillis()).build());
	}*/
	
	private class ATUploadFiles extends AsyncTask<Object, Integer, Integer> {
		private final Context context = UploadMapStoryToDropboxSvc.this;
		private int nFilesUploaded = 0;
		
		@Override
		protected void onCancelled() {
			super.onCancelled();
	 
            // Stop the service since it's no longer needed
            stopForeground(true);
            stopSelf();
            
            // When the loop is finished, updates the notification
/*            String sPath = getDropboxFileUrl(m_mapName, m_dropboxUserId);
            Intent builderAppIntent = getChooserIntent(sPath);*/
            Intent builderAppIntent = new Intent(UploadMapStoryToDropboxSvc.this, MapStoryList.class);
            builderAppIntent.putExtra(getString(R.string.extraKey_cancelUploadViaNotification), false);
            // FLAG_UPDATE_CURRENT - very important to make sure cancel-update extra boolean gets updated
            PendingIntent pi = PendingIntent.getActivity(context, 0, builderAppIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            m_nbUploadInProgress.setContentTitle("Upload canceled: " + m_mapName)            	
        		.setTicker("MapBuilder upload canceled")
                .setContentText(nFilesUploaded + " files were uploaded")
                .setContentIntent(pi)
            // Removes the progress bar
                    .setProgress(0,0,false)
                    .setOngoing(false);
            m_notifyManager.notify(ID, m_nbUploadInProgress.setWhen(System.currentTimeMillis()).build());
	 	}

		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);

            // Stop the service since it's no longer needed
            stopForeground(true);
            stopSelf();
            
            // When the loop is finished, updates the notification
            String sPath = getDropboxFileUrl(m_mapName, m_dropboxUserId);
            Intent chooserIntent = getChooserIntent(sPath);
            PendingIntent pi = PendingIntent.getActivity(context, 0, chooserIntent, 0);
            m_nbUploadInProgress.setContentTitle("Upload complete: " + m_mapName)
                    .setContentText(result + " files uploaded")
                    .setContentIntent(pi)
            // Removes the progress bar
                    .setProgress(0,0,false)
                    .setOngoing(false);
            m_notifyManager.notify(ID, m_nbUploadInProgress.setWhen(System.currentTimeMillis()).build());
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);
			int filesUploaded = values[0];
			int totalFilesToUpload = values[1];
            m_nbUploadInProgress.setProgress(totalFilesToUpload, filesUploaded, false)
    			.setContentText(filesUploaded + " of " + totalFilesToUpload);
            m_notifyManager.notify(ID, m_nbUploadInProgress.build());
		}

		private Intent getChooserIntent(String sUrl) {
		    Intent intent = new Intent(Intent.ACTION_SEND, Uri.parse(sUrl));
			intent.setType("message/rfc822");
//			intent.setType("text/plain");
			intent.putExtra(Intent.EXTRA_SUBJECT, "Map Story web site link");
//			intent.putExtra(Intent.EXTRA_TEXT, builder);
		    intent.putExtra(Intent.EXTRA_TEXT, sUrl);
			Intent chooserIntent = Intent.createChooser(intent, "How do you want to share this link?");
			chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			return chooserIntent;
		}

		/*		private NotificationCompat.Builder buildNotificationBuilder() {
	        // The PendingIntent to launch our activity if the user selects this notification
			Intent intent = new Intent(UploadMapStoryToDropboxSvc.this, MapStoryList.class);
			intent.putExtra(getString(R.string.extraKey_cancelUploadViaNotification), true);
	        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, 0);

	        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
				.setSmallIcon(android.R.drawable.ic_menu_upload)
				.setTicker("Uploading files to Dropbox...")
				.setContentTitle("Uploading to Dropbox...")
				.setContentIntent(contentIntent)
				.setWhen(System.currentTimeMillis());
			
			return builder;
		}*/

		private NotificationCompat.Builder m_nbUploadInProgress;
		private NotificationManager m_notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		private long m_dropboxUserId = Long.MIN_VALUE;
		private String m_mapName;
		
		@Override
		protected Integer doInBackground(Object... params) {
			String mapStoryDir = (String) params[0];
			boolean uploadTemplateFiles = (Boolean) params[1];
    		
    		// Determine total # of files
    		int nFilesToUpload = 3; // Main dir, CSV Config, index.html
    		nFilesUploaded = 0;
			
	        // The PendingIntent to launch our activity if the user selects this notification
			Intent intent = new Intent(UploadMapStoryToDropboxSvc.this, MapStoryList.class);
			intent.putExtra(getString(R.string.extraKey_cancelUploadViaNotification), true);
	        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

	        m_nbUploadInProgress = new NotificationCompat.Builder(context)
				.setSmallIcon(android.R.drawable.ic_menu_upload)
				.setTicker("Uploading files to Dropbox...")
				.setContentTitle("Uploading to Dropbox...")
				.setContentIntent(contentIntent)
				/*.setWhen(System.currentTimeMillis())*/;

	        DropboxAPI<AndroidAuthSession> api = ((MapStoryBuilderApplication)getApplication()).dbApi;
            // Prepare and run service
    		startForeground(ID, m_nbUploadInProgress.build());
    		
    		// Upload files and capture/update status
    		Vector<FileException> exceptions = new Vector<FileException>();
    		try {
    			m_dropboxUserId = api.accountInfo().uid;
    		}
    		catch (DropboxException exc) {
    			m_dropboxUserId = Long.MIN_VALUE;
    		}

    		File localWebTemplateArchive = null;
    		ZipFile localWebTemplateZipFile = null;
    		if (uploadTemplateFiles) {
    			localWebTemplateArchive = new File(mapStoryDir, getString(R.string.zipFileName));
    			try {
    				FileUtils.copyRawResource(context, R.raw.storytelling_maptour_template, localWebTemplateArchive);
    				localWebTemplateZipFile = new ZipFile(localWebTemplateArchive);
    			} catch (IOException e) {
    				exceptions.addElement(new FileException(localWebTemplateArchive.getName(), e));
    			}

    			nFilesToUpload += localWebTemplateZipFile.size();
    		}
    		
    		File photosDir = new File(mapStoryDir, 
    				context.getString(R.string.photosDirectoryName));
    		File[] photoFiles = photosDir.listFiles();
    		nFilesToUpload += photoFiles.length + 1; // Include the photos directory, to be created
    		
    		// -- END SETUP -- //
    		
    		// -- START UPLOADING -- //
    		
    		// Create main map directory
    		m_mapName = new File(mapStoryDir).getName();
    		String sRemoteStoryDir = 
    				context.getString(R.string.dropboxApp_baseDir) 
    				+ DELIM 
    				+ m_mapName;
    		if (!isCancelled()) try {api.createFolder(sRemoteStoryDir);} 
    		catch (DropboxException e) {
    			// It's just a folder that already exists. Don't log an exception for the user.
    			Log.w(TAG, "Map directory " + m_mapName + " already exists on the remote server.");
//    			exceptions.addElement(new FileException(m_sMapName, e));
    		} finally {publishProgress(++nFilesUploaded, nFilesToUpload);}

    		
    		// Upload CSV config file
    		String sCSVFilename = context.getString(R.string.storyConfigFilename);
    		File fileConfig = new File(mapStoryDir, sCSVFilename);
    		if (!isCancelled()) try {
    			api.putFileOverwrite(sRemoteStoryDir + DELIM + sCSVFilename, 
    					new FileInputStream(fileConfig), 
    					fileConfig.length(), null);
    		} catch (Exception e) {
    			exceptions.addElement(new FileException(fileConfig.getName(), e));
    			Log.e(TAG, "While saving " + sCSVFilename + ": " + e.getMessage());
    		} finally {publishProgress(++nFilesUploaded, nFilesToUpload);}

    		// Upload index.html
    		String sIndexFileName = context.getString(R.string.indexHTMLFileName);
    		File fileIndex = new File(mapStoryDir, sIndexFileName);
    		if (!isCancelled()) try {
    			/*m_uploadedIndexFile = */api.putFileOverwrite(sRemoteStoryDir + DELIM + sIndexFileName, 
    					new FileInputStream(fileIndex), 
    					fileIndex.length(), null);
    		} catch (Exception e) {
    			exceptions.addElement(new FileException(fileIndex.getName(), e));
    			Log.e(TAG, "While saving " + sIndexFileName + ": " + e.getMessage());
    		} finally {publishProgress(++nFilesUploaded, nFilesToUpload);}
    		
    		// Upload photos directory and photos and thumbnails
    		String sRemotePhotosDir = sRemoteStoryDir + DELIM + context.getString(R.string.photosDirectoryName);
    		if (!isCancelled()) try {api.createFolder(sRemotePhotosDir);} 
    		catch (DropboxException e) {
//    			exceptions.addElement(new FileException(sRemotePhotosDir, e));
    			// Again, this isn't really a problem if the photos directory already exists
    			Log.e(TAG, "While creating photos directory " + sRemotePhotosDir + ": " + e.getMessage());
    		}
    		finally {publishProgress(++nFilesUploaded, nFilesToUpload);}
    		
    		for (File photoFile : photoFiles) {
    			if (!isCancelled()) try {
    				api.putFileOverwrite(sRemotePhotosDir + DELIM + photoFile.getName(), 
    						new FileInputStream(photoFile), photoFile.length(), null);
    			} catch (DropboxUnlinkedException e) {
    				exceptions.addElement(new FileException(photoFile.getName(), e));
    				// Clear out stored keys so user will be prompted to re-authenticate
    				PrefsUtils.clearPref(context, context.getString(R.string.dropboxUserToken_key));
    				PrefsUtils.clearPref(context, context.getString(R.string.dropboxUserToken_secret));
    				break; // End loop; don't process any more files since it would be pointless
    			} catch (Exception e) {
    				exceptions.addElement(new FileException(photoFile.getName(), e));
    				Log.e(TAG, "While uploading photo file " + sRemotePhotosDir + DELIM + photoFile.getName() + ": " + e.getMessage());
    			} finally {publishProgress(++nFilesUploaded, nFilesToUpload);}

    			else break; // canceled
    		}
    		
    		if (uploadTemplateFiles) {
    			// Open the template file and try to upload all files therein
    			if (localWebTemplateZipFile != null) {
    				String sCurrentFilePath;
    				for (ZipEntry entry : Collections.list(localWebTemplateZipFile.entries())) {
    					// Try to upload file		
    					String sFilePathInsideZip = entry.getName();
    					sCurrentFilePath = sRemoteStoryDir + DELIM + sFilePathInsideZip;
    					if (!isCancelled()) try {
    						Log.d(TAG, entry.getName());
    						if (entry.isDirectory()) {
    							api.createFolder(sCurrentFilePath);
    						}
    						else {
    							api.putFileOverwrite(sCurrentFilePath, localWebTemplateZipFile.getInputStream(entry), entry.getSize(), null);
    						}
    					} catch (DropboxUnlinkedException e) {
    						//	if DropboxUnlinkException, add to list and exit loop
    						exceptions.addElement(new FileException(entry.getName().substring(entry.getName().lastIndexOf(DELIM)), e));
    						Log.e(TAG, "While uploading web template files, this dropbox unlinked exception occurred: " + e.getMessage());
    						// Clear out stored keys so user will be prompted to re-authenticate
    						PrefsUtils.clearPref(context, context.getString(R.string.dropboxUserToken_key));
    						PrefsUtils.clearPref(context, context.getString(R.string.dropboxUserToken_secret));
    						break; // End loop; don't process any more files since it would be pointless
    					} catch (Exception e) {
    						//	if other Exception, add to list and continue
    						exceptions.addElement(new FileException(entry.getName().substring(entry.getName().lastIndexOf(DELIM)), e));
    						Log.e(TAG, "While uploading " + entry.getName() + ": " + e.getMessage());
    					} finally {publishProgress(++nFilesUploaded, nFilesToUpload);}
    					else break; // canceled
    				}
    			}
    			localWebTemplateArchive.delete();
    		}
    		return nFilesUploaded;
		}
	}
	
/*	private void doUpload_old(final String mapStoryDir, final boolean uploadTemplateFiles) {
		
		// Start a lengthy operation in a background thread
		new Thread(
		    new Runnable() {
	    		Context context = UploadMapStoryToDropboxSvc.this;
	    		NotificationCompat.Builder mBuilder;
	    		NotificationManager notifyManager;
	    		long dropboxUserId = Long.MIN_VALUE;
	    		String mapName;
	    		
	    		// Determine total # of files
	    		int nFilesToUpload = 3; // Main dir, CSV Config, index.html
	    		int nFilesUploaded = 0;
		    	
	    		private NotificationCompat.Builder buildNotificationBuilder() {
	    	        // The PendingIntent to launch our activity if the user selects this notification
	    			Intent intent = new Intent(UploadMapStoryToDropboxSvc.this, MapStoryList.class);
	    			intent.putExtra(getString(R.string.extraKey_cancelUploadViaNotification), true);
	    	        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, 0);

	    	        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
	    				.setSmallIcon(android.R.drawable.ic_menu_upload)
	    				.setTicker("Uploading files to Dropbox...")
	    				.setContentTitle("Uploading to Dropbox...")
	    				.setContentIntent(contentIntent)
	    				.setWhen(System.currentTimeMillis());
	    			
	    			return builder;
	    		}

		    	@Override
		        public void run() {
		        	DropboxAPI<AndroidAuthSession> api = ((MapStoryBuilderApplication)getApplication()).dbApi;
		            // Prepare and run service
		            mBuilder = buildNotificationBuilder();
		    		startForeground(ID, mBuilder.build());
		    		notifyManager =
		    		        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		    		
		    		// Upload files and capture/update status
		    		Vector<FileException> exceptions = new Vector<FileException>();
		    		try {
		    			dropboxUserId = api.accountInfo().uid;
		    		}
		    		catch (DropboxException exc) {
		    			dropboxUserId = Long.MIN_VALUE;
		    		}

		    		File localWebTemplateArchive = null;
		    		ZipFile localWebTemplateZipFile = null;
		    		if (uploadTemplateFiles) {
		    			localWebTemplateArchive = new File(mapStoryDir, getString(R.string.zipFileName));
		    			try {
		    				FileUtils.copyRawResource(context, R.raw.storytelling_maptour_template, localWebTemplateArchive);
		    				localWebTemplateZipFile = new ZipFile(localWebTemplateArchive);
		    			} catch (IOException e) {
		    				exceptions.addElement(new FileException(localWebTemplateArchive.getName(), e));
		    			}

		    			nFilesToUpload += localWebTemplateZipFile.size();
		    		}
		    		
		    		File photosDir = new File(mapStoryDir, 
		    				context.getString(R.string.photosDirectoryName));
		    		File[] photoFiles = photosDir.listFiles();
		    		nFilesToUpload += photoFiles.length + 1; // Include the photos directory, to be created
		    		
		    		// -- END SETUP -- //
		    		
		    		// -- START UPLOADING -- //
		    		
		    		// Create main map directory
		    		mapName = new File(mapStoryDir).getName();
		    		String sRemoteStoryDir = 
		    				context.getString(R.string.dropboxApp_baseDir) 
		    				+ DELIM 
		    				+ mapName;
		    		if (!m_canceled) try {api.createFolder(sRemoteStoryDir);} 
		    		catch (DropboxException e) {
		    			// It's just a folder that already exists. Don't log an exception for the user.
		    			Log.w(TAG, "Map directory " + mapName + " already exists on the remote server.");
//		    			exceptions.addElement(new FileException(m_sMapName, e));
		    		}
		    		finally {publishProgress(++nFilesUploaded, nFilesToUpload);}

		    		
		    		// Upload CSV config file
		    		String sCSVFilename = context.getString(R.string.storyConfigFilename);
		    		File fileConfig = new File(mapStoryDir, sCSVFilename);
		    		if (!m_canceled) try {
		    			api.putFileOverwrite(sRemoteStoryDir + DELIM + sCSVFilename, 
		    					new FileInputStream(fileConfig), 
		    					fileConfig.length(), null);
		    		} 
		    		catch (Exception e) {
		    			exceptions.addElement(new FileException(fileConfig.getName(), e));
		    			Log.e(TAG, "While saving " + sCSVFilename + ": " + e.getMessage());
		    		}
		    		finally {publishProgress(++nFilesUploaded, nFilesToUpload);}

		    		// Upload index.html
		    		String sIndexFileName = context.getString(R.string.indexHTMLFileName);
		    		File fileIndex = new File(mapStoryDir, sIndexFileName);
		    		if (!m_canceled) try {
		    			m_uploadedIndexFile = api.putFileOverwrite(sRemoteStoryDir + DELIM + sIndexFileName, 
		    					new FileInputStream(fileIndex), 
		    					fileIndex.length(), null);
		    		} catch (Exception e) {
		    			exceptions.addElement(new FileException(fileIndex.getName(), e));
		    			Log.e(TAG, "While saving " + sIndexFileName + ": " + e.getMessage());
		    		}
		    		finally {publishProgress(++nFilesUploaded, nFilesToUpload);}
		    		
		    		// Upload photos directory and photos and thumbnails
		    		String sRemotePhotosDir = sRemoteStoryDir + DELIM + context.getString(R.string.photosDirectoryName);
		    		if (!m_canceled) try {api.createFolder(sRemotePhotosDir);} 
		    		catch (DropboxException e) {
//		    			exceptions.addElement(new FileException(sRemotePhotosDir, e));
		    			// Again, this isn't really a problem if the photos directory already exists
		    			Log.e(TAG, "While creating photos directory " + sRemotePhotosDir + ": " + e.getMessage());
		    		}
		    		finally {publishProgress(++nFilesUploaded, nFilesToUpload);}
		    		
		    		for (File photoFile : photoFiles) {
		    			if (!m_canceled) try {
		    				api.putFileOverwrite(sRemotePhotosDir + DELIM + photoFile.getName(), 
		    						new FileInputStream(photoFile), photoFile.length(), null);
		    			} catch (DropboxUnlinkedException e) {
		    				exceptions.addElement(new FileException(photoFile.getName(), e));
		    				// Clear out stored keys so user will be prompted to re-authenticate
		    				PrefsUtils.clearPref(context, context.getString(R.string.dropboxUserToken_key));
		    				PrefsUtils.clearPref(context, context.getString(R.string.dropboxUserToken_secret));
		    				break; // End loop; don't process any more files since it would be pointless
		    			} catch (Exception e) {
		    				exceptions.addElement(new FileException(photoFile.getName(), e));
		    				Log.e(TAG, "While uploading photo file " + sRemotePhotosDir + DELIM + photoFile.getName() + ": " + e.getMessage());
		    			}
		    			finally {publishProgress(++nFilesUploaded, nFilesToUpload);}

//		    			else break; // canceled
		    		}
		    		
		    		if (uploadTemplateFiles) {
		    			// Open the template file and try to upload all files therein
		    			if (localWebTemplateZipFile != null) {
		    				String sCurrentFilePath;
		    				for (ZipEntry entry : Collections.list(localWebTemplateZipFile.entries())) {
		    					// Try to upload file		
		    					String sFilePathInsideZip = entry.getName();
		    					sCurrentFilePath = sRemoteStoryDir + DELIM + sFilePathInsideZip;
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
		    						exceptions.addElement(new FileException(entry.getName().substring(entry.getName().lastIndexOf(DELIM)), e));
		    						Log.e(TAG, "While uploading web template files, this dropbox unlinked exception occurred: " + e.getMessage());
		    						// Clear out stored keys so user will be prompted to re-authenticate
		    						PrefsUtils.clearPref(context, context.getString(R.string.dropboxUserToken_key));
		    						PrefsUtils.clearPref(context, context.getString(R.string.dropboxUserToken_secret));
		    						break; // End loop; don't process any more files since it would be pointless
		    					} catch (Exception e) {
		    						//	if other Exception, add to list and continue
		    						exceptions.addElement(new FileException(entry.getName().substring(entry.getName().lastIndexOf(DELIM)), e));
		    						Log.e(TAG, "While uploading " + entry.getName() + ": " + e.getMessage());
		    					}
//		    					else break; // canceled
		    					
		    					publishProgress(++nFilesUploaded, nFilesToUpload);
		    				}
		    			}
		    			localWebTemplateArchive.delete();
		    		}

		            endService_Completed();
		        }
				private void endService_Completed() {
		            // Stop the service since it's no longer needed
		            stopForeground(true);
		            stopSelf();
		            
		            // When the loop is finished, updates the notification
		            String sPath = getDropboxFileUrl(mapName, dropboxUserId);
		            Intent actionIntent = getChooserIntent(sPath);
//		            Intent actionIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(sPath));
		            PendingIntent pi = PendingIntent.getActivity(context, 0, actionIntent, 0);
		            mBuilder.setContentTitle("Upload complete: " + mapName)
		                    .setContentText(nFilesToUpload + " files uploaded")
//		                    .setContentInfo("(Touch to open the StoryMap)")
		                    .setContentIntent(pi)
		            // Removes the progress bar
		                    .setProgress(0,0,false)
		                    .setOngoing(false);
		            notifyManager.notify(ID, mBuilder.setWhen(System.currentTimeMillis()).build());
				}

				private void publishProgress(int filesUploaded, int totalFilesToUpload) {
                    mBuilder.setProgress(totalFilesToUpload, filesUploaded, false)
            			.setContentText(filesUploaded + " of " + totalFilesToUpload);
                    notifyManager.notify(ID, mBuilder.build());
				}
				
				private Intent getChooserIntent(String sUrl) {
				    Intent intent = new Intent(Intent.ACTION_SEND, Uri.parse(sUrl));
					intent.setType("message/rfc822");
//					intent.setType("text/plain");
					intent.putExtra(Intent.EXTRA_SUBJECT, "Map Story web site link");
//					intent.putExtra(Intent.EXTRA_TEXT, builder);
				    intent.putExtra(Intent.EXTRA_TEXT, sUrl);
					Intent chooserIntent = Intent.createChooser(intent, "How do you want to share this link?");
					chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					return chooserIntent;
				}
		    }
		// Starts the thread by calling the run() method in its Runnable
		).start();		
		
	}

*/
	private String getDropboxFileUrl(String mapName, long dropboxUserId) {
		// Send a link
		String sProtocol = "http";
		String sServer = "dl.dropbox.com";
		String sPath = "/" + "u" + "/" 
				+ Long.toString(dropboxUserId) + "/"  
				+ mapName + "/"
				+ getString(R.string.indexHTMLFileName);
		URI uri = null;
		try {
			uri = new URI(sProtocol, sServer, sPath, null);
		} catch (URISyntaxException e) {
			String sMsg = "Problem encoding link for e-mail:\n" + e.getMessage();
			Log.e(TAG, sMsg);
			UIUtils.showAlert(this, sMsg);
			return null;
		}
		String sEncodedUrl = uri.toASCIIString();
		return sEncodedUrl;
/*		SpannableStringBuilder builder = new SpannableStringBuilder();
		builder.append("I'd like to share this photo map story web site with you:\n");
		int start = builder.length();
		builder.append(sEncodedUrl);
		int end = builder.length();
		builder.setSpan(new URLSpan(sEncodedUrl), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);		    
		Intent intent = new Intent();
		intent.setAction(Intent.ACTION_SEND);			
		intent.setType("message/rfc822");
		intent.putExtra(Intent.EXTRA_SUBJECT, "Map Story web site link");
		intent.putExtra(Intent.EXTRA_TEXT, builder);*/
		//			m_context.startActivity(Intent.createChooser(intent, "How do you want to send this link?"));
	}
}
