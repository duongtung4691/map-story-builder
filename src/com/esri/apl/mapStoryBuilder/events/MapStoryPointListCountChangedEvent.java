package com.esri.apl.mapStoryBuilder.events;

import java.util.EventObject;

public class MapStoryPointListCountChangedEvent extends EventObject {
	private static final long serialVersionUID = 1L;
	public static final int NaN = -1;
	private int _oldCount = NaN;
	private int _newCount = NaN;
	
	public MapStoryPointListCountChangedEvent(Object source) {
		super(source);
	}

	public int get_oldCount() {
		return _oldCount;
	}

	public void set_oldCount(int _oldCount) {
		this._oldCount = _oldCount;
	}

	public int get_newCount() {
		return _newCount;
	}

	public void set_newCount(int _newCount) {
		this._newCount = _newCount;
	}

}
