package com.esri.apl.mapStoryBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.io.FilenameUtils;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

/** Create a new story point or edit an existing one. **/
public class MapStoryPointEditor extends Activity {
	protected static final String TAG = "EditMapStoryPoint";
	
	/** Are we creating a new point or editing an existing one? **/
	private enum actionType { NEWPOINT, EDITPOINT };
	
	public static final String PHOTO_FILE_SUFFIX = ".JPG";
	
	public static final int COL_NAME		= 0;
	public static final int COL_DESC		= 1;
	public static final int COL_COLOR		= 2;
	public static final int COL_LON			= 3;
	public static final int COL_LAT			= 4;
	public static final int COL_PHOTOURL	= 5;
	public static final int COL_THUMBURL	= 6;
	public static final int COL_DATETIME	= 7;
	public static final String COLOR_BLUE	= "B";
	public static final String COLOR_RED	= "R";

	// Location sensor
	private LocationManager m_locMgr;
	private Location m_currentGeoLocation; // Also saved in state
	
	// State variables
	private actionType m_action = actionType.NEWPOINT;
	private File m_previewImageFile; // Hate this, but have to re/store it across orientation changes
	private File m_newPhotoFile;
	private Date m_newPhotoTimestamp;
	private Location m_newPhotoGeoLocation;
	private File m_capturedPhotoDir; // Where to put the original, full-size photos
	private File m_webPhotoDir;		 // Where to put resized web and thumbnail image files
	private String[] m_outputValues = new String[8];  // Values that'll be sent to the calling activity
	
	// Widgets
	private TextView lblLocation;
	private EditText txtTitle;
	private EditText txtDescription;
	private ImageView imgPhoto;
	private Button btnSave;
//	private Button btnRotLeft, btnRotNone, btnRotRight;
	private ImageButton btnPhoto;
	private RadioGroup rbgMarkerColor;
	private RadioButton rbRed, rbBlue;

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.newmapstorypoint_linear);
		
		// Get widget references
		lblLocation = (TextView)findViewById(R.id.lblLocDetails);
		imgPhoto = (ImageView)findViewById(R.id.imgPhoto);
		txtTitle = (EditText)findViewById(R.id.txtTitle);
		txtDescription = (EditText)findViewById(R.id.txtDescription);
		btnSave = (Button)findViewById(R.id.btnSave);
		btnPhoto = (ImageButton)findViewById(R.id.btnPhoto);
/*		btnRotLeft = (Button)findViewById(R.id.btnRotLeft);
		btnRotRight = (Button)findViewById(R.id.btnRotRight);
		btnRotNone = (Button)findViewById(R.id.btnRotNone);
*/		rbgMarkerColor = (RadioGroup)findViewById(R.id.rbgMarkerColor);
		rbBlue = (RadioButton)findViewById(R.id.rbMarkerColorBlue);
		rbRed = (RadioButton)findViewById(R.id.rbMarkerColorRed);
		
		// Set event listeners
		btnPhoto.setOnClickListener(onPhotoClick);
		btnSave.setOnClickListener(onSaveClick);
/*		btnRotLeft.setOnClickListener(onRotLeftClick);
		btnRotNone.setOnClickListener(onRotNoneClick);
		btnRotRight.setOnClickListener(onRotRightClick);
*/		
		if (savedInstanceState == null) { // Activity is newly created
			Intent intent = getIntent();
			File storyStorageDir = (File) intent.getExtras().getSerializable(getString(R.string.extra_StorageDir));
			m_capturedPhotoDir = new File(storyStorageDir, getString(R.string.photoOriginalsDirectoryName));
			m_webPhotoDir = new File(storyStorageDir, getString(R.string.photosDirectoryName));
			
			if (intent.hasExtra(getString(R.string.extra_MapPointRowData))) {
				m_action = actionType.EDITPOINT;
				setTitle("Edit Location");
				loadEditDataRow();
			}
			else
				setTitle("New Location");
		}
	}

	private void loadEditDataRow() {
		m_outputValues = getIntent().getStringArrayExtra(getString(R.string.extra_MapPointRowData));
		
		txtTitle.setText(m_outputValues[COL_NAME]);
		txtDescription.setText(m_outputValues[COL_DESC]);
		
		String sColor = m_outputValues[COL_COLOR];
		rbRed.setChecked(sColor.equals(rbRed.getTag().toString()));
		rbBlue.setChecked(sColor.equals(rbBlue.getTag().toString()));
				
		String sWebPhotoFile = FilenameUtils.getName(m_outputValues[COL_PHOTOURL]);
		int nSuffixPos = sWebPhotoFile.lastIndexOf(getString(R.string.rescaledPhotoFileSuffix));
		String sExt = FilenameUtils.getExtension(sWebPhotoFile);
		String sOrigPhotoFile = sWebPhotoFile.substring(0, nSuffixPos) + "." + sExt;
		m_previewImageFile = new File(m_capturedPhotoDir, sOrigPhotoFile);
		setPreviewImage();
	}

	@Override
	protected void onResume() {
		// Start listening for location
		super.onResume();
		
		if (!(m_locMgr instanceof LocationManager)) m_locMgr = (LocationManager) getSystemService(LOCATION_SERVICE);
		m_locMgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0, locationListener);
		m_locMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0, locationListener);
	}

	@Override
	protected void onPause() {
		// Stop listening for location
		m_locMgr.removeUpdates(locationListener);
		
		super.onPause();
	}
	
	private void setCurrentLocation(Location loc) {
		this.m_currentGeoLocation = loc;
		if (loc != null) {
			String sLocInfo = String.format(
					getResources().getConfiguration().locale,
					"X: %3.3f, Y: %2.3f", loc.getLongitude(), loc.getLatitude());
			lblLocation.setText(sLocInfo);
		}
		else
			lblLocation.setText(getString(R.string.newPhotoNoLocation));
	}
	
	LocationListener locationListener = new LocationListener() {
		
		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			/*if (status != LocationProvider.AVAILABLE) {
				setLocation(null);
				enableOrDisableSave();
			}*/
		}
		
		@Override
		public void onProviderEnabled(String provider) {}
		
		@Override
		public void onProviderDisabled(String provider) {}
		
		@Override
		public void onLocationChanged(Location location) {
			// TODO User could move but still not have greater accuracy than the last reading.
			// Still need to update location in that case.
			if ( (m_currentGeoLocation == null) || (m_currentGeoLocation.getAccuracy() == 0f) ||
				 (location.getAccuracy() < m_currentGeoLocation.getAccuracy()) ) {
				setCurrentLocation(location);
			}
		}
	};


	/** Don't rely on the location service's timestamp when creating a photo file name.
	 *  A photo might be taken before the device's location can be determined.
	 * @return Date object for the last location fix or for the current clock
	 */
	private Date getTimestamp() {
		if (m_currentGeoLocation != null)
			return new Date(m_currentGeoLocation.getTime());
		else
			return new Date();
	}
	
	private String DateFormatForImageFilename(Date date) {
		return new SimpleDateFormat("yyyyMMdd_HHmmss").format(date);
	}
/*	private File photoFile(Date date, File dir) {
		 // Create a media file name
		String ts = DateFormatForImageFilename(date);
		return new File(dir, "IMG_" + ts + ".jpg");

	}*/
	
	private OnClickListener onPhotoClick = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
		    m_newPhotoTimestamp = getTimestamp();
		    m_newPhotoGeoLocation = 
		    		(m_currentGeoLocation == null) ? null : new Location(m_currentGeoLocation);
		    m_newPhotoFile = photoFile(m_newPhotoTimestamp);
		    Uri newPhotoFilePath = Uri.fromFile(m_newPhotoFile);
		    
			// Send broadcast intent for image data
		    // create Intent to take a picture and return control to the calling application
		    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		    intent.putExtra(MediaStore.EXTRA_OUTPUT, newPhotoFilePath); // set the image file name
		    startActivityForResult(intent, 0);
		    
			// Get results in onActivityResults
		}
	};
	
	private Boolean photoFileExists() {
		return (imgPhoto.getDrawable() != null);
	}
	
	private void recycleImageView() {
		BitmapDrawable bd = (BitmapDrawable)imgPhoto.getDrawable();
		if ( bd != null ) {
			Bitmap image = bd.getBitmap();
			if (image != null && !image.isRecycled() ) {
				image.recycle();
				imgPhoto.setImageDrawable( null );
			}
		}
	}
	
	private void setPreviewImage() {
		recycleImageView();
//		BitmapDrawable bitmap = new BitmapDrawable(m_previewImageFile.getPath());
//		BitmapDrawable bitmap = new BitmapDrawable(getResources(), m_previewImageFile.getPath());
		Bitmap bitmap = BitmapFactory.decodeFile(m_previewImageFile.getPath());
		// Scale the display photo down to something that won't blow out the device's memory
		Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 512, 512, true);
//		imgPhoto.setImageDrawable(bitmapScaled);
		imgPhoto.setImageBitmap(scaledBitmap);
	}
	
	/** We receive the camera's image here **/
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
        if (resultCode == RESULT_OK) {
            // Image captured and saved to fileUri specified in the Intent
        	m_previewImageFile = m_newPhotoFile;
            setPreviewImage();
//            int rotation = getImageRotation(origPhotoFile);
        } else if (resultCode == RESULT_CANCELED) {
            // User cancelled the image capture
        } else {
            // Image capture failed, advise user
        	Toast.makeText(this, "Photo capture failed", Toast.LENGTH_LONG).show();
        }
	}
	
	private void unbindDrawables(View view) {
        if (view.getBackground() != null) {
            try {
                view.getBackground().setCallback(null);
                ((BitmapDrawable) view.getBackground()).getBitmap().recycle();
                view.destroyDrawingCache();
                view.notifyAll();
            } catch (Exception e) {
            }

        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                unbindDrawables(((ViewGroup) view).getChildAt(i));
            }
            ((ViewGroup) view).removeAllViews();
        }
    }
	
	private String photoBasename(Date timeStamp) {
		return "IMG_" + DateFormatForImageFilename(timeStamp);
	}

	private File photoFile(Date date) {
		String sPhotoBasename = photoBasename(date);
		File photoFile = new File(m_capturedPhotoDir, sPhotoBasename + PHOTO_FILE_SUFFIX);
		return photoFile;
	}
	
	/** Saves the current story event and closes **/
	private OnClickListener onSaveClick = new OnClickListener() {
		String outputWebImagePath = "", outputThumbnailPath = "";
		
		@Override
		public void onClick(View v) {
			boolean newPhotoCaptured = (m_newPhotoFile != null);
			boolean oldImagesExist = 
					m_outputValues[COL_PHOTOURL] != null || 
					m_outputValues[COL_THUMBURL] != null;
			
			// Always do this - create or update config file title, description, marker color values
			saveNonPhotoValues();

			// If a new photo was taken, generate web image and thumbnail
			if (newPhotoCaptured) {
				createWebImages(m_newPhotoFile);
				
				// If old web images need to be deleted, do it
				if (oldImagesExist)
					deleteOldImageFiles();
				
				saveNewPhotoValues();
			}
			
			
			// Return the results
			Intent results = new Intent();
			results.putExtra(getString(R.string.extra_MapPointRowData), m_outputValues);
			setResult(RESULT_OK, results);
			
			// Writing shall be done by points list fragment onPause()

			// Exit the activity
			finish();
		}
		
		private void deleteOldImageFiles() {
			String sWebPhotoFile = FilenameUtils.getName(m_outputValues[COL_PHOTOURL]);
			File oldWebPhoto = new File(m_webPhotoDir, sWebPhotoFile);
			String sThumbPhotoFile = FilenameUtils.getName(m_outputValues[COL_THUMBURL]);
			File oldThumbnail = new File(m_webPhotoDir, sThumbPhotoFile);

		    oldWebPhoto.delete();
		    oldThumbnail.delete();
		}
		
		private void saveNewPhotoValues() {
			m_outputValues[COL_LON] =
				(m_newPhotoGeoLocation == null) ? "0.0" : Double.toString(m_newPhotoGeoLocation.getLongitude());
			m_outputValues[COL_LAT] = 
				(m_newPhotoGeoLocation == null) ? "0.0" : Double.toString(m_newPhotoGeoLocation.getLatitude());
			m_outputValues[COL_PHOTOURL] = outputWebImagePath;
			m_outputValues[COL_THUMBURL] = outputThumbnailPath;
			m_outputValues[COL_DATETIME] = (String) DateFormat.format("yyyy-M-d kk:mm", m_newPhotoTimestamp);
		}
		
/*		private void saveOldPhotoValues() {
			// Take no action; old values should still be in the array
		}*/

		private void saveNonPhotoValues() {
			// Icon colors:   "B" = blue; "R" = red (see newmapstorypoint_linear.xml for tag values)
			int selectedMarkerColorRBId = rbgMarkerColor.getCheckedRadioButtonId();
			RadioButton rbMarkerColor = (RadioButton)findViewById(selectedMarkerColorRBId);
			String sMarkerColor = rbMarkerColor.getTag().toString();
			
			m_outputValues[COL_NAME] = txtTitle.getText().toString();
			m_outputValues[COL_DESC] = txtDescription.getText().toString();
			m_outputValues[COL_COLOR] = sMarkerColor;
		}
		
		private void createWebImages(File newPhotoFile) {
			String sPhotoBaseName = FilenameUtils.getBaseName(newPhotoFile.getPath());
			
			// Create thumbnail from image
			if (photoFileExists()) {
				Bitmap image = ((BitmapDrawable)imgPhoto.getDrawable()).getBitmap();
				Bitmap thumbnail = ThumbnailUtils.extractThumbnail(image, 150, 100);

				// Write thumbnail
				File thumbnailFile = new File(
						m_webPhotoDir, sPhotoBaseName 
						+ getString(R.string.thumbnailFileSuffix) + PHOTO_FILE_SUFFIX);
				try {
					OutputStream outStream = new FileOutputStream(thumbnailFile);
					// Add thumbnail indicator and extension
					thumbnail.compress(CompressFormat.JPEG, 95, outStream);
//					newThumbnailPath = getString(R.string.photosDirectoryName) + File.separator + thumbnailFile.getName();
					outputThumbnailPath = FilenameUtils.concat(
							getString(R.string.photosDirectoryName), 
							thumbnailFile.getName());
				}				
				catch (Exception exc) {
					Log.e(TAG, "While saving thumbnail: " + exc.getMessage());
				}
				finally { // Release drawables
					if (thumbnail != null && !thumbnail.isRecycled()) {
						thumbnail.recycle(); thumbnail = null; 
					}
				}
				
				// Write smaller web version of photo
				File webPhotoFile = new File(
						m_webPhotoDir, sPhotoBaseName
						+ getString(R.string.rescaledPhotoFileSuffix) + PHOTO_FILE_SUFFIX);
				int width = 800;
				int height = Math.round((float)image.getHeight() * ((float)width/(float)image.getWidth()));
				Bitmap webPhoto = Bitmap.createScaledBitmap(image, width, height, true);
				try {
					OutputStream outStream = new FileOutputStream(webPhotoFile);
					webPhoto.compress(CompressFormat.JPEG, 95, outStream);
//					newWebImagePath = getString(R.string.photosDirectoryName) + File.separator + webPhotoFile.getName();
					outputWebImagePath = FilenameUtils.concat(
							getString(R.string.photosDirectoryName), 
							webPhotoFile.getName());
				}
				catch(Exception exc) {
					Log.e(TAG, "While saving web photo: " + exc.getMessage());
				}
				finally {
					if (webPhoto != null && !webPhoto.isRecycled()) {
						webPhoto.recycle(); webPhoto = null;						
					}
				}
			}
		}
	};

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putSerializable("action", m_action);
		outState.putSerializable("capturedPhotoTimeStamp", m_newPhotoTimestamp);
		outState.putParcelable("capturedPhotoLocation", m_newPhotoGeoLocation);
		outState.putSerializable("capturedPhotoFile", m_newPhotoFile);
		outState.putSerializable("capturedPhotoDir", m_capturedPhotoDir);
		outState.putSerializable("webPhotoDir", m_webPhotoDir);
		outState.putParcelable("currentLocation", m_currentGeoLocation);
		outState.putStringArray("outputValues", m_outputValues);
		outState.putSerializable("previewImageFile", m_previewImageFile);
		outState.putString("activityTitle", getTitle().toString());
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
/*		if (imgPhoto.getDrawable() instanceof BitmapDrawable) {	
			Bitmap bm = ((BitmapDrawable)imgPhoto.getDrawable()).getBitmap();
			if (bm != null && !bm.isRecycled()) 
				bm.recycle();
		}*/
		unbindDrawables(imgPhoto);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		
		// Some sort of config change happened, e.g. orientation change
		m_action = (actionType)savedInstanceState.getSerializable("action");
		m_newPhotoTimestamp = (Date)savedInstanceState.getSerializable("capturedPhotoTimeStamp");
		m_newPhotoGeoLocation = (Location)savedInstanceState.getParcelable("capturedPhotoLocation");
		m_newPhotoFile = (File)savedInstanceState.getSerializable("capturedPhotoFile");
		m_capturedPhotoDir = (File)savedInstanceState.getSerializable("capturedPhotoDir");
		m_webPhotoDir = (File)savedInstanceState.getSerializable("webPhotoDir");
		setCurrentLocation((Location)savedInstanceState.getParcelable("currentLocation"));
		m_outputValues = savedInstanceState.getStringArray("outputValues");
		m_previewImageFile = (File)savedInstanceState.getSerializable("previewImageFile");
		setTitle(savedInstanceState.getString("activityTitle"));
		
		if (m_previewImageFile != null) setPreviewImage();
	}
	
	/// FAILED ATTEMPTS ///
	
	/** Keeps information about photos captured and created in the past for this entry.
	 *  We need to know this in order to delete thumbnail and web photos when overwriting
	 *  them with a newly captured photo.
	 */
/*	@SuppressWarnings("unused")
	public class PreviousPhotoInfo implements Serializable {
		private static final long serialVersionUID = -7063090100378052791L;
		private double			_lon, _lat;
		private Date			_timeStamp;
		private File			_oldWebImageFullPath;
		private File			_oldThumbnailImageFullPath;
		
		public File get_oldWebImageFullPath() {
			return _oldWebImageFullPath;
		}

		public void set_oldWebImageFullPath(File _oldWebImageFullPath) {
			this._oldWebImageFullPath = _oldWebImageFullPath;
		}

		public File get_oldThumbnailImageFullPath() {
			return _oldThumbnailImageFullPath;
		}

		public void set_oldThumbnailImageFullPath(File _oldThumbnailImageFullPath) {
			this._oldThumbnailImageFullPath = _oldThumbnailImageFullPath;
		}

		public Date get_timeStamp() {
			return _timeStamp;
		}

		public void set_timeStamp(Date _timeStamp) {
			this._timeStamp = _timeStamp;
		}

		public double get_lon() {
			return _lon;
		}

		public double get_lat() {
			return _lat;
		}


		public void set_location(double lon, double lat) {
			this._lon = lon; this._lat = lat;
		}
		
		public boolean hasOldOverwrittenPhotos() {
			return	_oldWebImageFullPath != null  ||
					_oldThumbnailImageFullPath != null;
		}
	}*/

	/** Try to get image rotation from media store or from image EXIF info.
	 * This is a workaround for a widespread and longstanding Android bug. Solution
	 * retrieved from Stackoverflow on 9/11/2012.
	 * @see http://stackoverflow.com/questions/8450539/images-taken-with-action-image-capture-always-returns-1-for-exifinterface-tag-or/8864367#8864367
	 * @param photoFile
	 * @return int value representing rotation of photo as it was taken
	 */
/*	private int getImageRotation(File photoFile) {
	    int rotation = -1;
	    long fileSize = photoFile.length();

	    Cursor mediaCursor = getContentResolver().query(
	    		MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
	    		new String[] {MediaStore.Images.ImageColumns.ORIENTATION, MediaStore.MediaColumns.SIZE }, 
	    		MediaStore.MediaColumns.DATE_ADDED + ">=?", 
	    		new String[]{String.valueOf(captureTime.getTime()/1000 - 1)}, 
	    		MediaStore.MediaColumns.DATE_ADDED + " desc");

	    if (mediaCursor != null && captureTime.getTime() != 0 && mediaCursor.getCount() !=0 ) {
	        while(mediaCursor.moveToNext()){
	            long size = mediaCursor.getLong(1);
	            //Extra check to make sure that we are getting the orientation from the proper file
	            if(size == fileSize){
	                rotation = mediaCursor.getInt(0);
	                break;
	            }
	        }
	    }
	    if(rotation == -1){
	    	ExifInterface exif;
			try {
				exif = new ExifInterface(photoFile.getPath());
				rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, rotation);
			} catch (IOException e) {
				// Auto-generated catch block
				e.printStackTrace();
			}
	    }
	    
	    return rotation;
	}*/
	
	/** Used to tell when user may have changed title or description and therefore
	 *  whether the save button should be enabled.
	 *  Also called manually when coordinates or image have changed, for the same reason.
	 */
/*	OnFocusChangeListener checkIfOkayToSave = new OnFocusChangeListener() {
		@Override
		public void onFocusChange(View v, boolean hasFocus) {
			if (!hasFocus) enableOrDisableSave();
		}
	};*/
	/** If title or description have changed, enable the save button **/
/*	public void enableOrDisableSave() {
		Boolean okayToSave = 
					txtTitle.getText().length() > 0
				&&	txtDescription.getText().length() > 0
				&&	location != null;
//				&&	photoFileExists;
		btnSave.setEnabled(okayToSave);
	}*/

/*	private TextWatcher watchTitleAndDesc = new TextWatcher() {
		
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			enableOrDisableSave();
		}
		
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {}
		
		@Override
		public void afterTextChanged(Editable s) {}
	};*/
	
/*	private OnClickListener onRotLeftClick = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			Matrix matrix=new Matrix();
			imgPhoto.setScaleType(ScaleType.MATRIX);   //required
			matrix.postRotate(
					(float) -90f, 
					imgPhoto.getDrawable().getBounds().width()/2, 
					imgPhoto.getDrawable().getBounds().height()/2);
			imgPhoto.setImageMatrix(matrix);		
		}
	};
	private OnClickListener onRotNoneClick = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			Matrix matrix=new Matrix();
			imgPhoto.setScaleType(ScaleType.MATRIX);   //required
			matrix.postRotate(
					(float) 0f, 
					imgPhoto.getDrawable().getBounds().width()/2, 
					imgPhoto.getDrawable().getBounds().height()/2);
			imgPhoto.setImageMatrix(matrix);		
		}
	};
	private OnClickListener onRotRightClick = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			Matrix matrix=new Matrix();
			imgPhoto.setScaleType(ScaleType.MATRIX);   //required
			matrix.postRotate(
					(float) 90f, 
					imgPhoto.getDrawable().getBounds().width()/2, 
					imgPhoto.getDrawable().getBounds().height()/2);
			imgPhoto.setImageMatrix(matrix);		
		}
	};
*/	}
