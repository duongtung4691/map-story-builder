package com.esri.apl.mapStoryBuilder.tasks;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;

import com.esri.apl.mapStoryBuilder.R;
import com.esri.apl.mapStoryBuilder.utils.FileUtils;
import com.esri.apl.mapStoryBuilder.utils.UIUtils;

public class ZipAndShip extends AsyncTask<Void, Void, IOException>  {
	private File			zipFile;
	private File			localStoryDir;
	private File			photosDir;
	private File			indexHTMLFile;
	private File			locationsCSVFile;
	private boolean			includeWebTemplateFiles;
	private ProgressDialog	progDlg;
	private Context			context;
	private String			zipFileName;
	InputStream				zipIn;

	public ZipAndShip(String sMapName, boolean bIncludeWebTemplateFiles, Context context) {
		this.context = context;
		this.includeWebTemplateFiles = bIncludeWebTemplateFiles;
		localStoryDir = new File(context.getExternalFilesDir(null), sMapName);
		zipFileName = context.getString(R.string.zipFileName);
		photosDir = new File(localStoryDir, context.getString(R.string.photosDirectoryName));
		indexHTMLFile = new File(localStoryDir, context.getString(R.string.indexHTMLFileName));
		locationsCSVFile = new File(localStoryDir, context.getString(R.string.storyConfigFilename));
	}
	
	/** File filter to filter out large original-size photo directory and zip file **/
	private FileFilter photosFilter = new FileFilter() {
		@Override
		public boolean accept(File pathname) {
			return (
					!pathname.getName().equals(context.getString(R.string.photoOriginalsDirectoryName))
					&& !pathname.getName().equals(context.getString(R.string.zipFileName))
					&& !pathname.getName().equals(".nomedia")
					);
		}
	};

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		zipIn = context.getResources().openRawResource(R.raw.storytelling_maptour_template);
		progDlg = ProgressDialog.show(context, null, "Creating zip archive...", true, false);
	}

	@Override
	protected IOException doInBackground(Void... params) {
		zipFile = new File(localStoryDir, zipFileName);
		FileOutputStream fout = null;
		
		// Locations CSV and index.html already exist in their finished states; add them to zip root
		IOException result = null;
		try {
			fout = new FileOutputStream(zipFile);
			ZipOutputStream zout = new ZipOutputStream(fout);
			
			// Copy web template files from resources?
			if (includeWebTemplateFiles) {
				ZipInputStream zin = new ZipInputStream(zipIn);
				FileUtils.copyEntireZipArchive(zin, zout);
			}
			
			// Add Locations CSV and index.html file to zip
			FileUtils.addSingleFileToZipRootDir(indexHTMLFile, zout);
			FileUtils.addSingleFileToZipRootDir(locationsCSVFile, zout);
			// Add photos directory to zip
			FileUtils.addDirectoryToZip(photosDir, zout, photosFilter, true);
			zout.close();
		} catch (IOException e) {
			result = e;
		}
		return result;
		
		// Send happens in onPostExecute
	}

	@Override
	protected void onPostExecute(IOException result) {
		super.onPostExecute(result);

		progDlg.dismiss();

		// If successfully zipped, ship it via send intent
		if (result == null) {
			Intent intent = new Intent();
			intent.setAction(Intent.ACTION_SEND);			
			intent.setType("application/zip");
			Uri zipUri = Uri.fromFile(zipFile);
			intent.putExtra(Intent.EXTRA_STREAM, zipUri);
			intent.putExtra(Intent.EXTRA_SUBJECT, "Map Story template files attached");
			intent.putExtra(Intent.EXTRA_TEXT, "You can use the attached files with an Esri Map Story Template");
			context.startActivity(intent);
		}
		else
			UIUtils.showAlert(context, "Problem zipping files: " + result.getMessage());
		
	}

}
