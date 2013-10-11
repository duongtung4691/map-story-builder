package com.esri.apl.mapStoryBuilder;

import java.io.File;

import com.esri.apl.mapStoryBuilder.events.MapStoryPointListCountChangedEvent;
import com.esri.apl.mapStoryBuilder.interfaces.IMapStoryPointListCountChangedListener;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

public class MapStoryPointsList extends FragmentActivity implements IMapStoryPointListCountChangedListener {
	private MapStoryPointsListFragment listFrag;
	private String selectedMapStoryName;
	
	@Override
	protected void onCreate(Bundle arg0) {
		super.onCreate(arg0);
		setContentView(R.layout.mapstorypointslist);
		
		// Get selected map story
		selectedMapStoryName = getIntent().getStringExtra(getString(R.string.chosenStoryName));
		setTitle(selectedMapStoryName);
		
		File mapStoryDirectory = (File) getIntent().getExtras().getSerializable(getString(R.string.extra_StorageDir));
		listFrag = (MapStoryPointsListFragment) getSupportFragmentManager().findFragmentById(R.id.mapStoryPointsListFragment);
		listFrag.addMapStoryPointsListCountChangedListener(this);
		listFrag.setMapStoryInfo(mapStoryDirectory, getString(R.string.storyConfigFilename));
	}

	@Override
	public void onMapStoryPointListCountChanged(
			MapStoryPointListCountChangedEvent event) {
		String sTitle = 
				selectedMapStoryName
				+ " ("
				+ Integer.toString(event.get_newCount())
				+ (event.get_newCount() == 1 ? " item" : " items")
				+ ")";
		setTitle(sTitle);
	}


}
