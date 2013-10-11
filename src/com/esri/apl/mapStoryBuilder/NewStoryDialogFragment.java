package com.esri.apl.mapStoryBuilder;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.EditText;

import com.esri.apl.mapStoryBuilder.interfaces.IMapStoryLister;

public class NewStoryDialogFragment extends DialogFragment {
	EditText txtStoryName;
	Button	 btnOK;
	Button	 btnCancel;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.addstorydialog, container);
		txtStoryName = (EditText)view.findViewById(R.id.txtNewStoryName);	
		txtStoryName.requestFocus();
		txtStoryName.addTextChangedListener(textChanged);
		
		btnOK = (Button)view.findViewById(R.id.btnNewStoryOK);
		btnOK.setOnClickListener(onOK);
		
		btnCancel = (Button)view.findViewById(R.id.btnNewStoryCancel);
		btnCancel.setOnClickListener(onCancel);
		getDialog().getWindow().setSoftInputMode(
                LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		getDialog().setTitle("New map story name");
		return view;
	}
	
	private OnClickListener onOK = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			// Get the name and send it back to the activity
			String sName = txtStoryName.getText().toString();
			((IMapStoryLister) getActivity()).addMapStory(sName);
			dismiss();
		}
	};
	
	private OnClickListener onCancel = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			dismiss();
		}
	};
	
	TextWatcher textChanged = new TextWatcher() {
		
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			btnOK.setEnabled(txtStoryName.getText().toString().length() > 0);
		}
		
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {}
		
		@Override
		public void afterTextChanged(Editable s) {}
	};
}
