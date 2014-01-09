package com.esri.apl.mapStoryBuilder;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.esri.apl.mapStoryBuilder.interfaces.IMapStoryLister;
import com.esri.apl.mapStoryBuilder.tasks.UploadMapStoryToDropboxSvc;
import com.esri.apl.mapStoryBuilder.utils.FileUtils;
import com.esri.apl.mapStoryBuilder.utils.UIUtils;

public class MapStoryList extends FragmentActivity implements IMapStoryLister {
	private ArrayList<String> alstStories;
	private ArrayAdapter<String> aryadptStories;

	//	private MapStoryListAdapter aryadptStories;
	private File extStorageDir;
	private ListFragment lfStories;
	private Boolean bLytDetailsPresent;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setTitle(R.string.defaultActivityTitle);
        setContentView(R.layout.mapstorylist);

        FragmentManager fm = getSupportFragmentManager();
        // Get widget references & set listeners
        lfStories = (ListFragment)fm.findFragmentById(R.id.mapStoryListFragment);
        View vPoints = findViewById(R.id.mapStoryPointsListFragment);
        bLytDetailsPresent = (vPoints != null);

        // Remove any detail points fragment that was carried over from before
        ListFragment mapPointList = (ListFragment) fm.findFragmentById(R.id.mapStoryPointsListFragment);
		if (bLytDetailsPresent && mapPointList != null)
        	fm.beginTransaction().remove(mapPointList).commit();        
        
/*		// If this activity was invoked by a cancel notification command, show the dialog
		Intent intent = getIntent();
		boolean bOfferUploadCancelDialog = 
			intent.getBooleanExtra(getString(R.string.extraKey_cancelUploadViaNotification), false);
		if (bOfferUploadCancelDialog)
			showUploadCancelConfirmationDialog(fm);*/
		
        extStorageDir = getExternalFilesDir(null);
        if (extStorageDir == null) {
        	String sMessage = "Necessary external storage is currently unavailable.";
        	DialogInterface.OnClickListener onClick = new DialogInterface.OnClickListener() {
    			
    			@Override
    			public void onClick(DialogInterface dialog, int which) {
    				dialog.dismiss();
    				finish();
    			}
    		};
        	UIUtils.showAlert(this, sMessage, null, onClick);
//        	finish(); - done in dialog click handler
        	return;
        }
        if (savedInstanceState == null) { // First time the activity has been opened
	        // Read stories list - directories in file system
	        alstStories = getStories();
        }
        else { // Configuration changed, e.g. orientation
        	alstStories = savedInstanceState.getStringArrayList("alstStories");
        }
        
        // Populate adapter        
//        aryadptStories =
//        		new MapStoryListAdapter(this, alstStories);
        aryadptStories = 
        		new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, alstStories);
        lfStories.setListAdapter(aryadptStories); 
        
        lfStories.getListView().setOnItemClickListener(onClickStory);
//        lfStories.getListView().setOnItemSelectedListener(onSelectStory); -- doesn't work
        
		// Remove any checkmarks that might have been applied
        ListView lv = lfStories.getListView();
        for (int i = 0; i < lv.getCount(); i++) {
/*        	View item = lv.getChildAt(i);
        	if (item instanceof CheckedTextView)
        		((CheckedTextView)item).setSelected(false);*/
        	lv.setItemChecked(i, false);
        }
    }

	@Override
	protected void onResume() {
		super.onResume();
		
		// If this activity was invoked by a cancel notification command, show the dialog
		Intent intent = getIntent();
		boolean bOfferUploadCancelDialog = 
			intent.getBooleanExtra(getString(R.string.extraKey_cancelUploadViaNotification), false);
		if (bOfferUploadCancelDialog) {
			FragmentManager fm = getSupportFragmentManager();
			showUploadCancelConfirmationDialog(fm);
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		
/*		// If this activity was invoked by a cancel notification command, show the dialog
		boolean bOfferUploadCancelDialog = 
			intent.getBooleanExtra(getString(R.string.extraKey_cancelUploadViaNotification), false);
		if (bOfferUploadCancelDialog) {
			FragmentManager fm = getSupportFragmentManager();
			showUploadCancelConfirmationDialog(fm);
		}*/
	}

	///// Upload-cancel confirmation dialog /////
    void showUploadCancelConfirmationDialog(FragmentManager fm) {
        DialogFragment newFragment = StopUploadConfirmationDF.newInstance(
        		"Cancel the upload?");
        newFragment.show(fm, "dialog");
    }

    /** The user does want to cancel the upload in progress **/
    public void doPositiveClick() {
		Intent intent = new Intent();
		intent.setClass(this, UploadMapStoryToDropboxSvc.class);
		intent.putExtra(getString(R.string.extraKey_cancelUploadViaNotification), true);
		startService(intent);
    }
    
/*    public void doNegativeClick() {
    }*/
    
    public static class StopUploadConfirmationDF extends DialogFragment {

        public static StopUploadConfirmationDF newInstance(String title) {
            StopUploadConfirmationDF frag = new StopUploadConfirmationDF();
            Bundle args = new Bundle();
            args.putString("title", title);
            frag.setArguments(args);
            return frag;
        }
        
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            String title = getArguments().getString("title");
            return new AlertDialog.Builder(getActivity())
                    .setIcon(android.R.drawable.ic_menu_upload)
                    .setTitle(title)
                    .setPositiveButton("Yes",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                ((MapStoryList)getActivity()).doPositiveClick();
                            }
                        }
                    )
                    .setNegativeButton("No", null
                        /*new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                ((MapStoryList)getActivity()).doNegativeClick();
                            }
                        }*/
                    )
                    .create();
        }
    }
    
    ///// End upload-cancel confirmation dialog /////
    
/*	private class MapStoryListAdapter extends BaseAdapter implements ListAdapter {
		private ArrayList<String> data;
		private LayoutInflater inflater;
		
		public MapStoryListAdapter (Context context, ArrayList<String> alstData) {
			super();
			this.data = alstData;
			inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
		}
		
		@Override
		public int getCount() {
			return data.size();
		}

		@Override
		public Object getItem(int position) {
			return data.get(position);
		}

		@Override
		public long getItemId(int position) {
			// Index is okay as a unique ID here
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewGroup layout;
			TextView lblMapName;
			Button btnAddMapLoc;
			String story = data.get(position);
			
			if (convertView == null) {
				layout = (ViewGroup) inflater.inflate(R.layout.mapstory_listitem, parent, false);
			}
			else {
				layout = (ViewGroup) convertView;
			}

			lblMapName = (TextView) layout.findViewById(R.id.lblMapStoryName);
			lblMapName.setText(story);

			btnAddMapLoc = (Button) layout.findViewById(R.id.btnAddMapLocation);
			btnAddMapLoc.setTag(story);
//			btnAddMapLoc.setOnClickListener(onSelectStory);

			return layout;
		}
    
		public void add(String story) {
			data.add(story);
			this.notifyDataSetChanged();
		}
    }*/

	/*    private OnItemSelectedListener onSelectStory = new OnItemSelectedListener() {

		@Override
		public void onItemSelected(AdapterView<?> parent, View view,
				int position, long id) {
	   		// Determine whether to show corresponding points on this activity,
    		// or to bring up a new activity for them
    		ListView lv = (ListView) parent;
    		String sStory = lv.getItemAtPosition(position).toString();
    		File storyFilesDir = new File(extStorageDir, sStory);
    		
    		// if ( layout is landscape and there's room )
    		//		* add story points list fragment to this activity
    		//		* call fragment method to set story points location
    		if (bLytDetailsPresent) {
     			// Show the chosen map as checked
//    			lv.setItemChecked(position, true);
//    			parent.setSelection(position);
//    			view.setSelected(true);
    			
    			MapStoryPointsListFragment lf = new MapStoryPointsListFragment();
    			lf.setMapStoryLoc(storyFilesDir, getString(R.string.storyConfigFilename));
    			
    			// Execute a transaction, replacing any existing fragment
    			// with this one inside the frame.
    			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
    			ft.replace(R.id.mapStoryPointsListFragment, lf);
    			ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    			ft.commit();
    		}
    		// else bring up a separate activity...
    		else {
    			Intent intent = new Intent();
    			intent.setClass(getBaseContext(), MapStoryPointsList.class);
    			intent.putExtra(getString(R.string.extStorageDir), storyFilesDir);
    			intent.putExtra(getString(R.string.chosenStoryName), sStory);
    			startActivity(intent);
    		}
    	}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
			// 
			
		}
	};*/
     private OnItemClickListener onClickStory = new OnItemClickListener() {

    	@Override
    	public void onItemClick(AdapterView<?> parent, View view, int position,
    			long id) {
    		// Determine whether to show corresponding points on this activity,
    		// or to bring up a new activity for them
    		ListView lv = (ListView) parent;
    		String sStory = lv.getItemAtPosition(position).toString();
    		File storyFilesDir = new File(extStorageDir, sStory);
    		
    		// if ( layout is landscape and there's room )
    		//		* add story points list fragment to this activity
    		//		* call fragment method to set story points location
    		if (bLytDetailsPresent) {
     			// Show the chosen map as checked
//    			lv.setItemChecked(position, true);
//    			parent.setSelection(position);
//    			view.setSelected(true);
    			
    			MapStoryPointsListFragment lf = new MapStoryPointsListFragment();
    			lf.setMapStoryInfo(storyFilesDir, getString(R.string.storyConfigFilename));
//    			lf.addMapStoryPointsListCountChangedListener(onPointListCountChanged);
    			
    			// Execute a transaction, replacing any existing fragment
    			// with this one inside the frame.
    			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
    			ft.replace(R.id.mapStoryPointsListFragment, lf);
    			ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    			ft.commit();
    		}
    		// else bring up a separate activity...
    		else {
    			Intent intent = new Intent();
    			intent.setClass(getBaseContext(), MapStoryPointsList.class);
    			intent.putExtra(getString(R.string.extra_StorageDir), storyFilesDir);
    			intent.putExtra(getString(R.string.chosenStoryName), sStory);
    			startActivity(intent);
    		}
    	}
	};


/*	private IMapStoryPointListCountChangedListener onPointListCountChanged = 
			new IMapStoryPointListCountChangedListener() {
		
		@Override
		public void onMapStoryPointListCountChanged(
				MapStoryPointListCountChangedEvent event) {
			String sText = 
				event.get_newCount() + 
				event.get_newCount() == 1 ? " item" : " items";
		}
	};*/
	
	private FileFilter isDirectory = new FileFilter() {
		
		@Override
		public boolean accept(File pathname) {
			return pathname.isDirectory();
		}
	};

	/**
	 * Reads the subdirectories under this app's storage location; returns the names as a list of stories
	 * @return ArrayList of story name strings corresponding to existing subdirectories
	 */
	private ArrayList<String> getStories() {
		ArrayList<String> aryStories = new ArrayList<String>();
		File[] subdirs = extStorageDir.listFiles(isDirectory);
		for (File subdir : subdirs) {
			aryStories.add(subdir.getName());
		}
		
		return aryStories;
	}
	@Override
	public void addMapStory(String newStoryName) {
		try {
			if (alstStories.contains(newStoryName)) {
				UIUtils.showAlert(this, "A story by that name already exists; please choose another.");
				return;
			}
			
			// Create new story directory
			File newStory = new File(extStorageDir, newStoryName);
			newStory.mkdir();
			// Create do-not-index flag for media manager
			File noMedia = new File(newStory, ".nomedia");
			noMedia.createNewFile();
			// Create original photos subdir
			String sPhotosOrigDir = getString(R.string.photoOriginalsDirectoryName);
			File photosOrig = new File(newStory, sPhotosOrigDir);
			photosOrig.mkdir();
			// Create photos subdir
			String sPhotosDir = getString(R.string.photosDirectoryName);
			File photos = new File(newStory, sPhotosDir);
			photos.mkdir();
			// Copy template locations file
			File csvFile = new File(newStory, getString(R.string.storyConfigFilename));
			FileUtils.copyRawResource(this, R.raw.locations, csvFile);
			// Copy template index.html file
//			InputStream inIndex = getResources().openRawResource(R.raw.index);
			File indexFile = new File(newStory, getString(R.string.indexHTMLTemplateFileName));
			FileUtils.copyRawResource(this, R.raw.index_template, indexFile);
//			OutputStream outIndex = new FileOutputStream(indexFile);

		    aryadptStories.add(newStoryName);
		}
		catch (NotFoundException e) {
			
		} catch (IOException e) {
			
		}
	}
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putStringArrayList("alstStories", alstStories);
//		outState.putInt("selectedListRow", lfStories.getSelectedItemPosition());
	}
	
	public static class MapStoryListFragment extends ListFragment implements OnClickListener {
//		private ListView lvStories;
		private Button btnAddMapStory;

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View v = inflater.inflate(R.layout.mapstorylist_listfragment, null);

			btnAddMapStory = (Button)v.findViewById(R.id.btnAddMapStory);
			btnAddMapStory.setOnClickListener(this);

			return v;
		}

		// New map button click handler
		@Override
		public void onClick(View v) {
			FragmentManager fm = getActivity().getSupportFragmentManager();
			NewStoryDialogFragment dlg = new NewStoryDialogFragment();
//			dlg.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
			dlg.show(fm, "NewStoryDialog");
		}

	}

	@Override
	public String getSelectedMapStoryName() {
		String sSelName = null;
		try {
			sSelName = lfStories.getListView().getSelectedItem().toString();
		}
		catch (Exception ex) {
			// Nothing
		}
		return sSelName;
	}
}