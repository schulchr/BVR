package com.bvr.android.head;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.bvr.android.R;
import com.bvr.android.VerticalSeekBar;

public class HeadActivity extends Activity {
	/** Hold a reference to our GLSurfaceView */
	private HeadGLSurfaceView mGLSurfaceView;
	private HeadRenderer mRenderer;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.head);

		mGLSurfaceView = (HeadGLSurfaceView) findViewById(R.id.gl_surface_view);

		// Check if the system supports OpenGL ES 2.0.
		final ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
		final boolean supportsEs2 = configurationInfo.reqGlEsVersion >= 0x20000;

		if (supportsEs2) {
			// Request an OpenGL ES 2.0 compatible context.
			mGLSurfaceView.setEGLContextClientVersion(2);

			final DisplayMetrics displayMetrics = new DisplayMetrics();
			getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

			// Set the renderer to our demo renderer, defined below.
			mRenderer = new HeadRenderer(this, mGLSurfaceView);
			mGLSurfaceView.setRenderer(mRenderer, displayMetrics.density);
		} else {
			// This is where you could create an OpenGL ES 1.x compatible
			// renderer if you wanted to support both ES 1 and ES 2.
			return;
		}
		VerticalSeekBar alphaBar = (VerticalSeekBar)findViewById(R.id.alphaSeekbar);
		alphaBar.incrementProgressBy(100);
		alphaBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
			   public void onProgressChanged(SeekBar seekBar, int progress,
			     boolean fromUser) {					
			    	mRenderer.setAlpha(progress/100.0f);			    	
			   }
				@Override
				public void onStartTrackingTouch(SeekBar arg0) {
					// TODO Auto-generated method stub					
				}
				@Override
				public void onStopTrackingTouch(SeekBar arg0) {
					// TODO Auto-generated method stub					
				}

			  });   
		
		VerticalSeekBar minBar = (VerticalSeekBar)findViewById(R.id.minSeekbar);
		minBar.incrementProgressBy(0);
		minBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
			   public void onProgressChanged(SeekBar seekBar, int progress,
			     boolean fromUser) {					
			    	mRenderer.setMin(progress/100.0f);			    	
			   }
				@Override
				public void onStartTrackingTouch(SeekBar arg0) {
					// TODO Auto-generated method stub					
				}
				@Override
				public void onStopTrackingTouch(SeekBar arg0) {
					// TODO Auto-generated method stub					
				}

			  });   
		
		VerticalSeekBar maxBar = (VerticalSeekBar)findViewById(R.id.maxSeekbar);
		maxBar.incrementProgressBy(100);
		maxBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
			   public void onProgressChanged(SeekBar seekBar, int progress,
			     boolean fromUser) {					
			    	mRenderer.setMax(progress/100.0f);			    	
			   }
				@Override
				public void onStartTrackingTouch(SeekBar arg0) {
					// TODO Auto-generated method stub					
				}
				@Override
				public void onStopTrackingTouch(SeekBar arg0) {
					// TODO Auto-generated method stub					
				}

			  });   
		
		VerticalSeekBar distBar = (VerticalSeekBar)findViewById(R.id.distSeekbar);
		distBar.incrementProgressBy(100);
		distBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
			   public void onProgressChanged(SeekBar seekBar, int progress,
			     boolean fromUser) {					
			    	mRenderer.setDist(progress);			    	
			   }
				@Override
				public void onStartTrackingTouch(SeekBar arg0) {
					// TODO Auto-generated method stub					
				}
				@Override
				public void onStopTrackingTouch(SeekBar arg0) {
					// TODO Auto-generated method stub					
				}

			  });  
		
		VerticalSeekBar stepBar = (VerticalSeekBar)findViewById(R.id.numStepsSeekbar);
		stepBar.incrementProgressBy(100);
		stepBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
			   public void onProgressChanged(SeekBar seekBar, int progress,
			     boolean fromUser) {					
			    	mRenderer.setSteps(progress);			    	
			   }
				@Override
				public void onStartTrackingTouch(SeekBar arg0) {
					// TODO Auto-generated method stub					
				}
				@Override
				public void onStopTrackingTouch(SeekBar arg0) {
					// TODO Auto-generated method stub					
				}

			  });
		
		VerticalSeekBar zoomBar = (VerticalSeekBar)findViewById(R.id.zoomSeekbar);
		zoomBar.incrementProgressBy(100);
		zoomBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
			   public void onProgressChanged(SeekBar seekBar, int progress,
			     boolean fromUser) {					
			    	mRenderer.setZoom(progress/100.0f);			    	
			   }
				@Override
				public void onStartTrackingTouch(SeekBar arg0) {
					// TODO Auto-generated method stub					
				}
				@Override
				public void onStopTrackingTouch(SeekBar arg0) {
					// TODO Auto-generated method stub					
				}

			  });  
	}

	@Override
	protected void onResume() {
		// The activity must call the GL surface view's onResume() on activity
		// onResume().
		super.onResume();
		mGLSurfaceView.onResume();
	}

	@Override
	protected void onPause() {
		// The activity must call the GL surface view's onPause() on activity
		// onPause().
		super.onPause();
		mGLSurfaceView.onPause();
	}
}