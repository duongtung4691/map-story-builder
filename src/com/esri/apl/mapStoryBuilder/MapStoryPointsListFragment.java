package com.esri.apl.mapStoryBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import com.esri.apl.mapStoryBuilder.events.MapStoryPointListCountChangedEvent;
import com.esri.apl.mapStoryBuilder.interfaces.IMapStoryPointListCountChangedListener;
import com.esri.apl.mapStoryBuilder.utils.UIUtils;

public class MapStoryPointsListFragment extends ListFragment implements IMapStoryPointListCountChangedListener{
	private static final String TAG			= "MapStoryPointsListFragment";
	
	public static final int ACTIVITY_NEWPOINT = 1;
	public static final int ACTIVITY_EDITPOINT = 2;

	private ArrayList<String[]> alstStoryPoints;
//	private ArrayAdapter<String[]> aryadpStoryPoints;
	private List<IMapStoryPointListCountChangedListener> _msplccListeners 
					= new ArrayList<IMapStoryPointListCountChangedListener>();
	private MapStoryListPointsAdapter aryadpStoryPoints;
	private File storyDirectory;
	private File storyFile;
	private String[] aryFields;
	private ListView lvMapStoryPoints;
	private TextView lblMapName;
	private String sMapName;
	private Button btnNewMapStoryPoint;
	private Button btnShareMap;
	private TextView lblMapStoryPointCount;
	private int nRowBeingEdited;

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		// Read pre-existing point info from config file; set up adapter
		// NOTE: This fragment may be created even if it's not present in the current layout.
		// It's critical to check if the storyFile has been set before we try to read from it.
		if (storyFile != null) try {
			CSVReader csvr = new CSVReader(new FileReader(storyFile));
			ArrayList<String[]> points = (ArrayList<String[]>) csvr.readAll();
			
			csvr.close(); csvr = null; /*System.gc();*/
			
			if (points.size() > 0) {
				aryFields = points.get(0);
				alstStoryPoints = new ArrayList<String[]>(points.subList(1, points.size()));
				aryadpStoryPoints = 
						new MapStoryListPointsAdapter(getActivity(), R.layout.mapstorypoint_listitem, 
								R.id.lblMapStoryPointName, alstStoryPoints);
				lvMapStoryPoints.setAdapter(aryadpStoryPoints);
				if (aryadpStoryPoints != null) {
					aryadpStoryPoints.notifyDataSetChanged();
					notifyMapStoryPointsListCountChanged(aryadpStoryPoints.getCount());
				}
			}
			else // Mysterious problem reading the config file 
				Toast.makeText(getActivity(), "Error reading file; please try again", Toast.LENGTH_LONG).show();
		} catch (FileNotFoundException e) {
			Log.e(TAG, "Couldn't find config file '" + storyFile.toString() + "': " + e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, "Problem reading config file '" + storyFile.toString() + "': " + e.getMessage());
		}
	}


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
			
/*		if (container == null) {
			setHasOptionsMenu(false);
			return null;
		}
		else {*/
//			setHasOptionsMenu(true);
			View v = inflater.inflate(R.layout.mapstorypointslist_listfragment, null);
			lvMapStoryPoints = (ListView)v.findViewById(android.R.id.list);
//			getListView().setOnItemClickListener(onListItemClick);
			
			btnNewMapStoryPoint = (Button)v.findViewById(R.id.btnAddMapStoryPoint);
			btnNewMapStoryPoint.setOnClickListener(onAddMapStoryPoint);
			
			btnShareMap = (Button)v.findViewById(R.id.btnSend);
			btnShareMap.setOnClickListener(onShareMap);
			
/*			btnDropbox = (Button)v.findViewById(R.id.btnDropBox);
			btnDropbox.setOnClickListener(onDropboxClick);*/
			
			// This fragment might have onCreateView() called first, then have setMapStoryInfo() called.
			// Or it might happen the other way around. It depends on whether it was automatically created
			// by layout inflation (when used in its own activity) or created in code (map list activity, landscape).
			lblMapName = (TextView)v.findViewById(R.id.lblMapStoryName);
			lblMapStoryPointCount = (TextView)v.findViewById(R.id.lblMapStoryPointsListCount);
			
			addMapStoryPointsListCountChangedListener(this);
			if (sMapName != "") lblMapName.setText(sMapName);
			return v;
//		}
	}

/*	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
//		if (storyFileChosen())
			mniShareMap = menu.add("Share this map")
			.setIcon(android.R.drawable.ic_menu_send);
	}*/

/*	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item == mniShareMap) {
			// Package up the photos and metadata and share
			zipAndShip.execute(storyDirectory);
		}
		
		return super.onOptionsItemSelected(item);
	}*/

	public void setMapStoryInfo(File dir, String sConfigFilename) {
		// Read CSV rows
		storyDirectory = dir;
		storyFile = new File(storyDirectory, sConfigFilename);

		sMapName = storyDirectory.getName();
		// This fragment might have onCreateView() called first, then have setMapStoryInfo() called.
		// Or it might happen the other way around. It depends on whether it was automatically created
		// by layout inflation (when used in its own activity) or created in code (map list activity, landscape).
		if (lblMapName != null) lblMapName.setText(sMapName);
	}
	
/*	private boolean storyFileChosen() {
		return (storyDirectory != null);
	}*/
	
	private OnClickListener onAddMapStoryPoint = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			Intent intent = new Intent();
			intent.setClass(getActivity(), MapStoryPointEditor.class);
			intent.putExtra(getString(R.string.extra_StorageDir), storyDirectory);
			startActivityForResult(intent, ACTIVITY_NEWPOINT);
		}
	};

	private OnClickListener onShareMap = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
/*			// If keys from previous session stored, use those; otherwise begin standard authentication
			Context context = getActivity();
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			String key = PrefsUtils.getPref(context, context.getString(R.string.dropboxUserToken_key));
			String secret = PrefsUtils.getPref(context, context.getString(R.string.dropboxUserToken_secret));
			AndroidAuthSession session = buildDropboxSession(key, secret);
			
			mDBApi = new DropboxAPI<AndroidAuthSession>(session);
			// 3 possibilities:			
			if (!mDBApi.getSession().isLinked()) 
				// Possibility 1: User not yet signed in; need to initiate authentication
				// (this will invoke another application, then return; upload is handled in onResume)
				mDBApi.getSession().startAuthentication(getActivity());
			else
				// Possibility 2: User already signed in; continue with upload
				(new UploadMapStoryToDropbox(sMapName, getActivity())).execute(mDBApi);
			// Possibility 3: User tried to sign in but failed for some reason
			// (this is handled in onResume)
*/			
			Intent intent = new Intent();
			intent.setClass(getActivity(), IndexPageAttributesEditor.class);
			intent.putExtra(getString(R.string.chosenStoryName), sMapName);
			startActivity(intent);
		}
	};

	private class MapStoryListPointsAdapter extends ArrayAdapter<String[]> {
		private int layoutId;
		private int labelId;
		private Context context;
		
		public MapStoryListPointsAdapter(Context context, int resource,
				int textViewResourceId, List<String[]> objects) {
			super(context, resource, textViewResourceId, objects);
			layoutId = resource;
			this.labelId = textViewResourceId;
			this.context = context;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			String[] row = getItem(position);
			ViewGroup layout = null;
			if (convertView != null && convertView instanceof LinearLayout) {
				layout = (ViewGroup) convertView;
			}
			else {
				LayoutInflater li = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				layout = (ViewGroup)li.inflate(layoutId, parent, false);
			}
			
			
			TextView label = (TextView)layout.findViewById(labelId);
			
			String sDate = null, sName = null;
			try { // It might be possible to get a truncated data row
				sDate = row[MapStoryPointEditor.COL_DATETIME];
				sName = row[MapStoryPointEditor.COL_NAME];
			} catch(IndexOutOfBoundsException ex) {
				UIUtils.showAlert(this.getContext(), "That map story has a corrputed datafile (index.csv) and cannot be opened.");
				return layout;
			}
			
			label.setText(sDate + " - " + sName);
			label.setTag(position);
			label.setOnClickListener(onEditMapStoryPoint);
			
			Button btnDelete = (Button)layout.findViewById(R.id.btnDeleteMapStoryPoint);
			btnDelete.setTag(row);
			btnDelete.setOnClickListener(onDeleteMapStoryPoint);
			
			return layout;
		}
	}
	
	private OnClickListener onEditMapStoryPoint = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			// Grab selected row of data and send it on to the activity for editing
			nRowBeingEdited = (Integer) v.getTag();
			String[] dataRow = (String[]) aryadpStoryPoints.getItem(nRowBeingEdited);
			
			Intent intent = new Intent();
			intent.setClass(getActivity(), MapStoryPointEditor.class);
			intent.putExtra(getString(R.string.extra_StorageDir), storyDirectory);
			intent.putExtra(getString(R.string.extra_MapPointRowData), dataRow);
			startActivityForResult(intent, ACTIVITY_EDITPOINT);
		}
	};
	
	private OnClickListener onDeleteMapStoryPoint = new OnClickListener() {
		
		@Override
		public void onClick(final View v) {
			// Verify the deletion
	    	new AlertDialog.Builder(getActivity())
	    	.setMessage("Really delete this entry and its photo?")
	    	.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					deleteSelectedStory(v);
					dialog.dismiss();
				}
			})
			.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			})
			.show();
		}
		
		private void deleteSelectedStory(View v) {
	    	Button btn = (Button) v;
			String[] row = (String[])btn.getTag();
			
			File photoFileDir = new File(storyDirectory, getString(R.string.photosDirectoryName));
			String sPhotoFile = row[MapStoryPointEditor.COL_PHOTOURL]; // photoDir + pathSeparator + photoFilename
			if (sPhotoFile.length() > 0) {
				String sPhotoFilename = sPhotoFile.substring(sPhotoFile.lastIndexOf(File.separator));
				File photoFile = new File(photoFileDir, sPhotoFilename);
				photoFile.delete();
			}
			
			String sThumbFile = row[MapStoryPointEditor.COL_THUMBURL];
			if (sThumbFile.length() > 0) {
				String sThumbFilename = sThumbFile.substring(sThumbFile.lastIndexOf(File.separator));
				File thumbFile = new File(photoFileDir, sThumbFilename);
				thumbFile.delete();
			}
			
			int oldCount = aryadpStoryPoints.getCount();
			aryadpStoryPoints.remove(row);
			int newCount = aryadpStoryPoints.getCount();
			
			notifyMapStoryPointsListCountChanged(oldCount, newCount);
//			aryadpStoryPoints.notifyDataSetChanged();
		}
	};
/*	private class MapStoryListPointsAdapter extends BaseAdapter implements ListAdapter {
		private ArrayList<String> data;
		private LayoutInflater inflater;

		public MapStoryListPointsAdapter (Context context, ArrayList<String> alstData) {
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
				layout = (ViewGroup) inflater.inflate(R.layout.maplistitem, parent, false);
			}
			else {
				layout = (ViewGroup) convertView;
			}

			lblMapName = (TextView) layout.findViewById(R.id.lblMapName);
			lblMapName.setText(story);

			btnAddMapLoc = (Button) layout.findViewById(R.id.btnAddMapLocation);
			btnAddMapLoc.setTag(story);
			btnAddMapLoc.setOnClickListener(onSelectStory);

			return layout;
		}

		//	public void add(String story) {
		//		data.add(story);
		//		this.notifyDataSetChanged();
		//	}
	}*/


	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == Activity.RESULT_OK) {
			if (requestCode == ACTIVITY_NEWPOINT) {
				String[] newRow = data.getStringArrayExtra(getString(R.string.extra_MapPointRowData));
				int oldCount = aryadpStoryPoints.getCount();
				aryadpStoryPoints.add(newRow);
				int newCount = aryadpStoryPoints.getCount();

				notifyMapStoryPointsListCountChanged(oldCount, newCount);
				// aryadpStoryPoints.notifyDataSetChanged();
			}
			else if (requestCode == ACTIVITY_EDITPOINT) {
				String[] editedRow = data.getStringArrayExtra(getString(R.string.extra_MapPointRowData));
				alstStoryPoints.set(nRowBeingEdited, editedRow);
			}
		}
	}

/*	private AsyncTask<File, Void, ArrayList<String[]>> readCSV = new AsyncTask<File, Void, ArrayList<String[]>>() {

		@Override
		protected ArrayList<String[]> doInBackground(File... params) {
			// Read pre-existing point info from config file; set up adapter
			// NOTE: This fragment may be created even if it's not present in the current layout.
			// It's critical to check if the storyFile has been set before we try to read from it.
			File storyFile = params[0];
			ArrayList<String[]> points = null;
			if (storyFile != null) try {
				CSVReader csvr = new CSVReader(new FileReader(storyFile));
				points = (ArrayList<String[]>) csvr.readAll();
				
				csvr.close(); csvr = null; System.gc();
			} catch (FileNotFoundException e) {
				Log.e(TAG, "Couldn't find config file '" + storyFile.toString() + "': " + e.getMessage());
			} catch (IOException e) {
				Log.e(TAG, "Problem reading config file: " + e.getMessage());
			}
			
			return points;
		}

		@Override
		protected void onPostExecute(ArrayList<String[]> points) {
			super.onPostExecute(points);
			if (points.size() > 0) {
				aryFields = points.get(0);
			alstStoryPoints = new ArrayList<String[]>(points.subList(1, points.size()));
//			aryadpStoryPoints = new ArrayAdapter<String[]>(getActivity(), android.R.layout.simple_list_item_1, alstStoryPoints);
			aryadpStoryPoints = 
					new MapStoryListPointsAdapter(getActivity(), R.layout.mapstorypoint_listitem, 
							R.id.lblMapStoryPointName, alstStoryPoints);
			lvMapStoryPoints.setAdapter(aryadpStoryPoints);
//			aryadpStoryPoints.notifyDataSetChanged();
			if (aryadpStoryPoints != null)
				aryadpStoryPoints.notifyDataSetChanged();
			}
			else Toast.makeText(getActivity(), "Error reading file; please try again", Toast.LENGTH_LONG).show();
		}
		
	};*/
	@Override
	public void onPause() {
		super.onPause();
		
		// Save dataset to disk
		// NOTE: This Fragment can be recreated and then paused upon orientation change
		// and subsequent list choice, even though it's not part of the current layout.
		if (aryFields != null && alstStoryPoints != null)
				new Thread(new Runnable() {
					
					@Override
					public void run() {
						try {
							ArrayList<String[]> aryPoints = new ArrayList<String[]>();
							aryPoints.add(aryFields);
							aryPoints.addAll(alstStoryPoints);
							FileWriter fw = new FileWriter(storyFile, false);
							CSVWriter writer = new CSVWriter(fw);
							writer.writeAll(aryPoints);
							writer.close();
							fw.close();					
						} catch (IOException exc) {
							Log.e(TAG, "Problem opening/writing to config file: " + exc.getMessage());
						}
					}
				}).start();
	}
	

	
	public synchronized void addMapStoryPointsListCountChangedListener(IMapStoryPointListCountChangedListener listener)  {
		_msplccListeners.add(listener);
	}
	public synchronized void removeMapStoryPointsListCountChangedListener(IMapStoryPointListCountChangedListener listener)   {
		_msplccListeners.remove(listener);
	}
		   
	/** Notify listeners that the number of map story points has increased or decreased **/
	private synchronized void notifyMapStoryPointsListCountChanged(int oldCount, int newCount) {
		MapStoryPointListCountChangedEvent event = new MapStoryPointListCountChangedEvent(this);
		event.set_oldCount(oldCount);
		event.set_newCount(newCount);
		Iterator<IMapStoryPointListCountChangedListener> i = _msplccListeners.iterator();
		while(i.hasNext())  {
			((IMapStoryPointListCountChangedListener) i.next()).onMapStoryPointListCountChanged(event);
		}
	}

	/** Notify listeners that the number of map story points has increased or decreased **/
	private void notifyMapStoryPointsListCountChanged(int newCount) {
		notifyMapStoryPointsListCountChanged(MapStoryPointListCountChangedEvent.NaN, newCount);
	}
	
	@Override
	public void onMapStoryPointListCountChanged(
			MapStoryPointListCountChangedEvent event) {
		String sItemCount = 				
				Integer.toString(event.get_newCount()) +
				(event.get_newCount() == 1 ? " item" : " items");
		lblMapStoryPointCount.setText(sItemCount);
				
	}

}
