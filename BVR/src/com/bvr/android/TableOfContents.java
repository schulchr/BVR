package com.bvr.android;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.SparseArray;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SimpleAdapter;

import com.bvr.android.head.HeadActivity;
import com.bvr.android.heatmap.HeatMapActivity;
import com.bvr.android.raw.RawActivity;

public class TableOfContents extends ListActivity 
{
	private static final String ITEM_IMAGE = "item_image";
	private static final String ITEM_TITLE = "item_title";
	private static final String ITEM_SUBTITLE = "item_subtitle";	
	public final static String EXTRA_MESSAGE = "com.bvr.android.MESSAGE";
	
	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setTitle(R.string.toc);
		setContentView(R.layout.table_of_contents);
		
		// Initialize data
		final List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
		final SparseArray<Class<? extends Activity>> activityMapping = new SparseArray<Class<? extends Activity>>();
		
		int i = 0;
		{
			final Map<String, Object> item = new HashMap<String, Object>();
			item.put(ITEM_IMAGE, R.drawable.teddybear);
			item.put(ITEM_TITLE, getText(R.string.raw));
			item.put(ITEM_SUBTITLE, getText(R.string.raw_subtitle));
			data.add(item);
			activityMapping.put(i++, RawActivity.class);
		}
		
		{
			final Map<String, Object> item = new HashMap<String, Object>();
			item.put(ITEM_IMAGE, R.drawable.heatmap);
			item.put(ITEM_TITLE, getText(R.string.heatmap));
			item.put(ITEM_SUBTITLE, getText(R.string.heatmap_subtitle));
			data.add(item);
			activityMapping.put(i++, HeatMapActivity.class);
		}
		
		{
			final Map<String, Object> item = new HashMap<String, Object>();
			item.put(ITEM_IMAGE, R.drawable.head);
			item.put(ITEM_TITLE, getText(R.string.head));
			item.put(ITEM_SUBTITLE, getText(R.string.head_subtitle));
			data.add(item);
			activityMapping.put(i++, HeadActivity.class);
		}
		
		
		
		final SimpleAdapter dataAdapter = new SimpleAdapter(this, data, R.layout.toc_item, new String[] {ITEM_IMAGE, ITEM_TITLE, ITEM_SUBTITLE}, new int[] {R.id.Image, R.id.Title, R.id.SubTitle});
		setListAdapter(dataAdapter);	
		
		getListView().setOnItemClickListener(new OnItemClickListener() 
		{
			@Override
			 public void onItemClick(AdapterView<?> parent, View view,
				        int position, long id) 
			{
				final Class<? extends Activity> activityToLaunch = activityMapping.get(position);
							
				if(activityToLaunch.getName().equals("com.bvr.android.raw.RawActivity"))
				{
					//Launch an alert window to allow user to select the file they want to view
					String dirName = Environment.getExternalStorageDirectory().toString() + "/raw/";
					
					File dir = new File(dirName);
					
					String[] fileNames = dir.list();
					final ArrayList<String> txtFiles = new ArrayList<String>();
					
					for (int i=0; i < fileNames.length; i++)
					{
					    if(fileNames[i].matches(".*?[.]raw$"))
					    {
					    	txtFiles.add(fileNames[i]);
					    }
					}
					
					AlertDialog.Builder builder = new AlertDialog.Builder(TableOfContents.this);
					builder.setTitle("Load a RAW file");
					
					CharSequence[] items = txtFiles.toArray(new CharSequence[txtFiles.size()]);
					
					builder.setItems(items, new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							
							File sdcard = Environment.getExternalStorageDirectory();					
										    
						    //Send this text to the new activity
							Intent intent = new Intent(TableOfContents.this, RawActivity.class);
							String filename = sdcard.getPath() + "/raw/" + txtFiles.get(which);
							intent.putExtra(EXTRA_MESSAGE, filename);
							startActivity(intent);									
						}
					});
					
					AlertDialog alert = builder.create();
					alert.show();
					
				}
				else if (activityToLaunch != null)
				{
					final Intent launchIntent = new Intent(TableOfContents.this, activityToLaunch);
					startActivity(launchIntent);
				}				
			}
		});
	}	
}
