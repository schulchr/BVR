package com.bvr.android.heatmap;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import com.bvr.android.R;
import com.bvr.android.common.RawResourceReader;
import com.bvr.android.common.ShaderHelper;
import com.bvr.android.common.ShapeBuilder;
import com.bvr.android.common.TextureHelper;

/**
 * This class implements our custom renderer. Note that the GL10 parameter
 * passed in is unused for OpenGL ES 2.0 renderers -- the static class GLES30 is
 * used instead.
 */
public class HeatMapRenderer implements GLSurfaceView.Renderer {
	/** Used for debug logs. */
	private static final String TAG = "LessonSevenRenderer";

	private final HeatMapActivity mHeatMapActivity;
	private final GLSurfaceView mGlSurfaceView;
	
	/**
	 * Store the model matrix. This matrix is used to move models from object space (where each model can be thought
	 * of being located at the center of the universe) to world space.
	 */
	private float[] mModelMatrix = new float[16];

	/**
	 * Store the view matrix. This can be thought of as our camera. This matrix transforms world space to eye space;
	 * it positions things relative to our eye.
	 */
	private float[] mViewMatrix = new float[16];

	/** Store the projection matrix. This is used to project the scene onto a 2D viewport. */
	private float[] mProjectionMatrix = new float[16];
	

	/** Store the current rotation. */
	private final float[] mZoomMatrix = new float[16];
	
	/** Allocate storage for the final combined matrix. This will be passed into the shader program. */
	private float[] mMVPMatrix = new float[16];
	
	/** Allocate storage for the final combined matrix. This will be passed into the shader program. */
	private float[] mVPMatrix = new float[16];
	
	/** Store the accumulated rotation. */
	private final float[] mAccumulatedRotation = new float[16];
	
	/** Store the current rotation. */
	private final float[] mCurrentRotation = new float[16];
	
	/** A temporary matrix. */
	private float[] mTemporaryMatrix = new float[16];
	
	/** 
	 * Stores a copy of the model matrix specifically for the light position.
	 */
	private float[] mLightModelMatrix = new float[16];		
	
	/** This will be used to pass in the transformation matrix. */
	private int mMVPMatrixHandle;
	
	/** This will be used to pass in the modelview matrix. */
	private int mMVMatrixHandle;
	
	/** This will be used to pass in the modelmatrix. */
	private int mMMatrixHandle;
	
	/** This will be used to pass in the modelmatrix. */
	private int mVPMatrixHandle;
	
	/** This will be used to pass in the light position. */
	private int mLightPosHandle;
	
	/** This will be used to pass in the texture. */
	private int mTextureUniformHandle;
	
	/** This will be used to pass in model position information. */
	private int mPositionHandle;
	
	/** This will be used to pass in model normal information. */
	private int mNormalHandle;
	

	/** This will be used to pass in model texture coordinate information. */
	private int mTextureCoordinateHandle;
	
	/** Additional info for cube generation. */
	private int mLastRequestedCubeFactor;
	private int mActualCubeFactor;
	
	/** Control whether vertex buffer objects or client-side memory will be used for rendering. */
	private boolean mUseVBOs = true;	
	
	/** Control whether strides will be used. */
	private boolean mUseStride = true;
	
	/** Size of the position data in elements. */
	static final int POSITION_DATA_SIZE = 3;	
	
	/** Size of the normal data in elements. */
	static final int NORMAL_DATA_SIZE = 3;
	
	/** Size of the texture coordinate data in elements. */
	static final int TEXTURE_COORDINATE_DATA_SIZE = 2;
	
	/** How many bytes per float. */
	static final int BYTES_PER_FLOAT = 4;	
	
	/** Used to hold a light centered on the origin in model space. We need a 4th coordinate so we can get translations to work when
	 *  we multiply this by our transformation matrices. */
	private final float[] mLightPosInModelSpace = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	
	/** Used to hold the current position of the light in world space (after transformation via model matrix). */
	private final float[] mLightPosInWorldSpace = new float[4];
	
	/** Used to hold the transformed position of the light in eye space (after transformation via modelview matrix) */
	private final float[] mLightPosInEyeSpace = new float[4];
	
	/** This is a handle to our cube shading program. */
	private int mProgramHandle;
	
	/** These are handles to our texture data. */
	private int mAndroidDataHandle;		
	
	// These still work without volatile, but refreshes are not guaranteed to happen.					
	public volatile float mDeltaX;					
	public volatile float mDeltaY;	
	
	/** Thread executor for generating cube data in the background. */
	private final ExecutorService mSingleThreadedExecutor = Executors.newSingleThreadExecutor();
	
	/** The current cubes object. */
	private Cubes mCubes;
	
	/** This will be used to pass in model slider information. */
	private int mAlphaHandle;
	private int mMinHandle;
	private int mMaxHandle;
	private int mDistHandle;
	private int mStepsHandle;
	private int mZoomHandle;
	
	/**
	 * values that are passed into the shader
	 */
	private static float mAlpha = 1.0f;
	private static float mMin = 0.0f;
	private static float mMax = 1.0f;
	private static float mSteps = 100.0f;
	private static float mDist  = 100.0f;
	private static float mZoom = 1.0f;

	/**
	 * Initialize the model data.
	 */
	public HeatMapRenderer(final HeatMapActivity heatMapActivity, final GLSurfaceView glSurfaceView) {
		mHeatMapActivity = heatMapActivity;	
		mGlSurfaceView = glSurfaceView;
	}

	private void generateCubes(int cubeFactor, boolean toggleVbos, boolean toggleStride) {
		mSingleThreadedExecutor.submit(new GenDataRunnable(cubeFactor, toggleVbos, toggleStride));		
	}
	
	class GenDataRunnable implements Runnable {
		final int mRequestedCubeFactor;
		final boolean mToggleVbos;
		final boolean mToggleStride;
		
		GenDataRunnable(int requestedCubeFactor, boolean toggleVbos, boolean toggleStride) {
			mRequestedCubeFactor = requestedCubeFactor; 
			mToggleVbos = toggleVbos;	
			mToggleStride = toggleStride;
		}
		
		@Override
		public void run() {			
			try {
				// X, Y, Z
				// The normal is used in light calculations and is a vector which points
				// orthogonal to the plane of the surface. For a cube model, the normals
				// should be orthogonal to the points of each face.
				final float[] cubeNormalData =
				{												
						// Front face
						0.0f, 0.0f, 1.0f,				
						0.0f, 0.0f, 1.0f,
						0.0f, 0.0f, 1.0f,
						0.0f, 0.0f, 1.0f,				
						0.0f, 0.0f, 1.0f,
						0.0f, 0.0f, 1.0f,
						
						// Right face 
						1.0f, 0.0f, 0.0f,				
						1.0f, 0.0f, 0.0f,
						1.0f, 0.0f, 0.0f,
						1.0f, 0.0f, 0.0f,				
						1.0f, 0.0f, 0.0f,
						1.0f, 0.0f, 0.0f,
						
						// Back face 
						0.0f, 0.0f, -1.0f,				
						0.0f, 0.0f, -1.0f,
						0.0f, 0.0f, -1.0f,
						0.0f, 0.0f, -1.0f,				
						0.0f, 0.0f, -1.0f,
						0.0f, 0.0f, -1.0f,
						
						// Left face 
						-1.0f, 0.0f, 0.0f,				
						-1.0f, 0.0f, 0.0f,
						-1.0f, 0.0f, 0.0f,
						-1.0f, 0.0f, 0.0f,				
						-1.0f, 0.0f, 0.0f,
						-1.0f, 0.0f, 0.0f,
						
						// Top face 
						0.0f, 1.0f, 0.0f,			
						0.0f, 1.0f, 0.0f,
						0.0f, 1.0f, 0.0f,
						0.0f, 1.0f, 0.0f,				
						0.0f, 1.0f, 0.0f,
						0.0f, 1.0f, 0.0f,
						
						// Bottom face 
						0.0f, -1.0f, 0.0f,			
						0.0f, -1.0f, 0.0f,
						0.0f, -1.0f, 0.0f,
						0.0f, -1.0f, 0.0f,				
						0.0f, -1.0f, 0.0f,
						0.0f, -1.0f, 0.0f
				};
				
				// S, T (or X, Y)
				// Texture coordinate data.
				// Because images have a Y axis pointing downward (values increase as you move down the image) while
				// OpenGL has a Y axis pointing upward, we adjust for that here by flipping the Y axis.
				// What's more is that the texture coordinates are the same for every face.
				final float[] cubeTextureCoordinateData =
				{												
						// Front face
						0.0f, 0.0f, 				
						0.0f, 1.0f,
						1.0f, 0.0f,
						0.0f, 1.0f,
						1.0f, 1.0f,
						1.0f, 0.0f,				
						
						// Right face 
						0.0f, 0.0f, 				
						0.0f, 1.0f,
						1.0f, 0.0f,
						0.0f, 1.0f,
						1.0f, 1.0f,
						1.0f, 0.0f,	
						
						// Back face 
						0.0f, 0.0f, 				
						0.0f, 1.0f,
						1.0f, 0.0f,
						0.0f, 1.0f,
						1.0f, 1.0f,
						1.0f, 0.0f,	
						
						// Left face 
						0.0f, 0.0f, 				
						0.0f, 1.0f,
						1.0f, 0.0f,
						0.0f, 1.0f,
						1.0f, 1.0f,
						1.0f, 0.0f,	
						
						// Top face 
						0.0f, 0.0f, 				
						0.0f, 1.0f,
						1.0f, 0.0f,
						0.0f, 1.0f,
						1.0f, 1.0f,
						1.0f, 0.0f,	
						
						// Bottom face 
						0.0f, 0.0f, 				
						0.0f, 1.0f,
						1.0f, 0.0f,
						0.0f, 1.0f,
						1.0f, 1.0f,
						1.0f, 0.0f
				};		
							
				final float[] cubePositionData = new float[108 * mRequestedCubeFactor * mRequestedCubeFactor * mRequestedCubeFactor];
				int cubePositionDataOffset = 0;
									
				final int segments = mRequestedCubeFactor + (mRequestedCubeFactor - 1);
				final float minPosition = -0.5f;
				final float maxPosition = 0.5f;
				final float positionRange = maxPosition - minPosition;
				
				for (int x = 0; x < mRequestedCubeFactor; x++) {
					for (int y = 0; y < mRequestedCubeFactor; y++) {
						for (int z = 0; z < mRequestedCubeFactor; z++) {
							final float x1 = minPosition + ((positionRange / segments) * (x * 2));
							final float x2 = minPosition + ((positionRange / segments) * ((x * 2) + 1));
							
							final float y1 = minPosition + ((positionRange / segments) * (y * 2));
							final float y2 = minPosition + ((positionRange / segments) * ((y * 2) + 1));
							
							final float z1 = minPosition + ((positionRange / segments) * (z * 2));
							final float z2 = minPosition + ((positionRange / segments) * ((z * 2) + 1));
							
							// Define points for a cube.
							// X, Y, Z
							final float[] p1p = { x1, y2, z2 };
							final float[] p2p = { x2, y2, z2 };
							final float[] p3p = { x1, y1, z2 };
							final float[] p4p = { x2, y1, z2 };
							final float[] p5p = { x1, y2, z1 };
							final float[] p6p = { x2, y2, z1 };
							final float[] p7p = { x1, y1, z1 };
							final float[] p8p = { x2, y1, z1 };

							final float[] thisCubePositionData = ShapeBuilder.generateCubeData(p1p, p2p, p3p, p4p, p5p, p6p, p7p, p8p,
									p1p.length);
							
							System.arraycopy(thisCubePositionData, 0, cubePositionData, cubePositionDataOffset, thisCubePositionData.length);
							cubePositionDataOffset += thisCubePositionData.length;
						}
					}
				}					
				
				// Run on the GL thread -- the same thread the other members of the renderer run in.
				mGlSurfaceView.queueEvent(new Runnable() {
					@Override
					public void run() {												
						if (mCubes != null) {
							mCubes.release();
							mCubes = null;
						}
						
						// Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
						System.gc();
						
						try {
							mCubes = new CubesWithVboWithStride(cubePositionData, cubeNormalData, cubeTextureCoordinateData, mRequestedCubeFactor);
						} catch (OutOfMemoryError err) {
							if (mCubes != null) {
								mCubes.release();
								mCubes = null;
							}
							
							// Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
							System.gc();
							
							mHeatMapActivity.runOnUiThread(new Runnable() {							
								@Override
								public void run() {
//									Toast.makeText(mHeatMapActivity, "Out of memory; Dalvik takes a while to clean up the memory. Please try again.\nExternal bytes allocated=" + dalvik.system.VMRuntime.getRuntime().getExternalBytesAllocated(), Toast.LENGTH_LONG).show();								
								}
							});										
						}																	
					}				
				});
			} catch (OutOfMemoryError e) {
				// Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
				System.gc();
				
				mHeatMapActivity.runOnUiThread(new Runnable() {							
					@Override
					public void run() {
//						Toast.makeText(mHeatMapActivity, "Out of memory; Dalvik takes a while to clean up the memory. Please try again.\nExternal bytes allocated=" + dalvik.system.VMRuntime.getRuntime().getExternalBytesAllocated(), Toast.LENGTH_LONG).show();								
					}
				});
			}			
		}
	}

	public void decreaseCubeCount() {
		/*
		if (mLastRequestedCubeFactor > 1) {
			generateCubes(--mLastRequestedCubeFactor, false, false);
		}
		*/
	}

	public void increaseCubeCount() {
		/*
		if (mLastRequestedCubeFactor < 16) {
			generateCubes(++mLastRequestedCubeFactor, false, false);
		}
		*/
	}	
	
	public void toggleVBOs() 
	{
	/*
		generateCubes(mLastRequestedCubeFactor, true, false);	
	*/	
	}
	
	public void toggleStride() {
		/*
		generateCubes(mLastRequestedCubeFactor, false, true);		
		*/
	}
	
	@Override
	public void onSurfaceCreated(GL10 glUnused, EGLConfig config) 
	{		
		mActualCubeFactor = 1;
		generateCubes(mActualCubeFactor, true, true);			
		
		// Set the background clear color to black.
		GLES30.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		
		// Use culling to remove back faces.
		GLES30.glEnable(GLES30.GL_CULL_FACE);
		
		// Enable depth testing
		GLES30.glDisable(GLES30.GL_DEPTH_TEST);
		
		// Enable blending
		GLES30.glEnable(GLES30.GL_BLEND_COLOR);	
		
		// Position the eye in front of the origin.
		final float eyeX = 0.0f;
		final float eyeY = 0.0f;
		final float eyeZ = -10.0f;

		// We are looking toward the distance
		final float lookX = 0.0f;
		final float lookY = 0.0f;
		final float lookZ = 5.0f;

		// Set our up vector. This is where our head would be pointing were we holding the camera.
		final float upX = 0.0f;
		final float upY = 1.0f;
		final float upZ = 0.0f;

		// Set the view matrix. This matrix can be said to represent the camera position.
		// NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination of a model and
		// view matrix. In OpenGL 2, we can keep track of these matrices separately if we choose.
		Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);		

		final String vertexShader = RawResourceReader.readTextFileFromRawResource(mHeatMapActivity, R.raw.heatmap_vertex_shader);   		
 		final String fragmentShader = RawResourceReader.readTextFileFromRawResource(mHeatMapActivity, R.raw.heatmap_fragment_shader);
 				
		final int vertexShaderHandle = ShaderHelper.compileShader(GLES30.GL_VERTEX_SHADER, vertexShader);		
		final int fragmentShaderHandle = ShaderHelper.compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentShader);		
		
		mProgramHandle = ShaderHelper.createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle, 
				new String[] {"a_Position",  "a_Normal", "a_TexCoordinate"});		            
        
		// Load the texture
		//mAndroidDataHandle = TextureHelper.loadTexture(mHeatMapActivity, R.drawable.usb_android);
		/*
		 * 2D custom texture example
		 /
		mAndroidDataHandle = TextureHelper.createSimpleTexture2D();		
		GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, mAndroidDataHandle);		
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);		
		
		GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, mAndroidDataHandle);		
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);

		GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
		*/
		
		/*
		 * 3D custom texture example
		 */
		mAndroidDataHandle = TextureHelper.createSimpleTexture3D();	
		
		mAndroidDataHandle = createHeatMap3DTexture(256);	
		
		GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, mAndroidDataHandle);		
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);		
		
		GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, mAndroidDataHandle);		
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);

		GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);	
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE);
		
        // Initialize the accumulated rotation matrix
        Matrix.setIdentityM(mAccumulatedRotation, 0);        
	}	
		
	@Override
	public void onSurfaceChanged(GL10 glUnused, int width, int height) 
	{
		// Set the OpenGL viewport to the same size as the surface.
		GLES30.glViewport(0, 0, width, height);

		// Create a new perspective projection matrix. The height will stay the same
		// while the width will vary as per aspect ratio.
		final float ratio = (float) width / height;
		final float left = -ratio;
		final float right = ratio;
		final float bottom = -1.0f;
		final float top = 1.0f;
		final float near = 1.0f;
		final float far = 1000.0f;
		
		Matrix.orthoM(mProjectionMatrix, 0, left, right, bottom, top, near, far);
		//Matrix.perspectiveM(mProjectionMatrix, 0, 90, ratio, near, far);
	}	

	@Override
	public void onDrawFrame(GL10 glUnused) 
	{		
		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);			                                    
        
        // Set our per-vertex lighting program.
        GLES30.glUseProgram(mProgramHandle);   
        
        // Set program handles for cube drawing.
        mMVPMatrixHandle = GLES30.glGetUniformLocation(mProgramHandle, "u_MVPMatrix");
        mMVMatrixHandle = GLES30.glGetUniformLocation(mProgramHandle, "u_MVMatrix");
        mMMatrixHandle = GLES30.glGetUniformLocation(mProgramHandle, "u_MMatrix");
        mVPMatrixHandle = GLES30.glGetUniformLocation(mProgramHandle, "u_VPMatrix"); 
        mLightPosHandle = GLES30.glGetUniformLocation(mProgramHandle, "u_LightPos");
        
        mAlphaHandle    = GLES30.glGetUniformLocation(mProgramHandle, "uAmax");
        mMaxHandle    = GLES30.glGetUniformLocation(mProgramHandle, "uMax");
        mMinHandle    = GLES30.glGetUniformLocation(mProgramHandle, "uMin");
        mDistHandle    = GLES30.glGetUniformLocation(mProgramHandle, "uDist");
        mStepsHandle    = GLES30.glGetUniformLocation(mProgramHandle, "uNumSteps");
        
        mTextureUniformHandle = GLES30.glGetUniformLocation(mProgramHandle, "u_Texture");
        mPositionHandle = GLES30.glGetAttribLocation(mProgramHandle, "a_Position");        
        mNormalHandle = GLES30.glGetAttribLocation(mProgramHandle, "a_Normal"); 
        mTextureCoordinateHandle = GLES30.glGetAttribLocation(mProgramHandle, "a_TexCoordinate");

        mZoomHandle    = GLES30.glGetUniformLocation(mProgramHandle, "u_Zoom");
        
        // Calculate position of the light. Push into the distance.
        Matrix.setIdentityM(mLightModelMatrix, 0);                     
        Matrix.translateM(mLightModelMatrix, 0, 0.0f, 0.0f, -1.0f);
               
        Matrix.multiplyMV(mLightPosInWorldSpace, 0, mLightModelMatrix, 0, mLightPosInModelSpace, 0);
        Matrix.multiplyMV(mLightPosInEyeSpace, 0, mViewMatrix, 0, mLightPosInWorldSpace, 0);                      
        
        // Draw a cube.
        // Translate the cube into the screen.
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 0.0f, 0.0f, 0.0f);     
        
        // Set a matrix that contains the current rotation.
        Matrix.setIdentityM(mCurrentRotation, 0);        
    	Matrix.rotateM(mCurrentRotation, 0, mDeltaX, 0.0f, 1.0f, 0.0f);
    	Matrix.rotateM(mCurrentRotation, 0, mDeltaY, 1.0f, 0.0f, 0.0f);
    	mDeltaX = 0.0f;
    	mDeltaY = 0.0f;
    	    	
    	// Multiply the current rotation by the accumulated rotation, and then set the accumulated rotation to the result.
    	Matrix.multiplyMM(mTemporaryMatrix, 0, mCurrentRotation, 0, mAccumulatedRotation, 0);
    	System.arraycopy(mTemporaryMatrix, 0, mAccumulatedRotation, 0, 16);
    	    	
        // Rotate the cube taking the overall rotation into account.     	
    	Matrix.multiplyMM(mTemporaryMatrix, 0, mModelMatrix, 0, mAccumulatedRotation, 0);
    	System.arraycopy(mTemporaryMatrix, 0, mModelMatrix, 0, 16);   
    	
    	// Scale the cube  
    	Matrix.setIdentityM(mZoomMatrix, 0);
    	Matrix.scaleM(mZoomMatrix, 0, mZoom, mZoom, mZoom); 	
    	Matrix.multiplyMM(mTemporaryMatrix, 0, mModelMatrix, 0, mZoomMatrix, 0);
    	System.arraycopy(mTemporaryMatrix, 0, mModelMatrix, 0, 16);   
    	
    	
    	// This multiplies the view matrix by the model matrix, and stores
		// the result in the MVP matrix
		// (which currently contains model * view).
		Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);

		// Pass in the modelview matrix.
		GLES30.glUniformMatrix4fv(mMVMatrixHandle, 1, false, mMVPMatrix, 0);

		// This multiplies the modelview matrix by the projection matrix,
		// and stores the result in the MVP matrix
		// (which now contains model * view * projection).
		Matrix.multiplyMM(mTemporaryMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
		System.arraycopy(mTemporaryMatrix, 0, mMVPMatrix, 0, 16);
		
		// This multiplies the modelview matrix by the projection matrix,
		// and stores the result in the MVP matrix
		// (which now contains model * view * projection).
		Matrix.multiplyMM(mTemporaryMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);
		System.arraycopy(mTemporaryMatrix, 0, mVPMatrix, 0, 16);

		// Pass in the combined matrix.
		GLES30.glUniformMatrix4fv(mVPMatrixHandle, 1, false, mVPMatrix, 0);
		
		// Pass in the combined matrix.
		GLES30.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);
		
		// Pass in the model matrix.
		GLES30.glUniformMatrix4fv(mMMatrixHandle, 1, false, mModelMatrix, 0);

		// Pass in the light position in eye space.
		GLES30.glUniform3f(mLightPosHandle, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1], mLightPosInEyeSpace[2]);
		
		// Pass in the texture information
		// Set the active texture unit to texture unit 0.
		GLES30.glActiveTexture(GLES30.GL_TEXTURE0);

		// Bind the texture to this unit.
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mAndroidDataHandle);

		// Tell the texture uniform sampler to use this texture in the
		// shader by binding to texture unit 0.
		GLES30.glUniform1i(mTextureUniformHandle, 0);
		
		
		//Send in all slider info
		GLES30.glUniform1f(mAlphaHandle, mAlpha);
		GLES30.glUniform1f(mMaxHandle, mMax);
		GLES30.glUniform1f(mMinHandle, mMin);
		GLES30.glUniform1f(mStepsHandle, mSteps);
		GLES30.glUniform1f(mDistHandle, mDist);

		GLES30.glUniform1f(mZoomHandle, mZoom);
		
		if (mCubes != null) {
			mCubes.render();
		}
	}		
	
	abstract class Cubes {
		abstract void render();

		abstract void release();	
		
		FloatBuffer[] getBuffers(float[] cubePositions, float[] cubeNormals, float[] cubeTextureCoordinates, int generatedCubeFactor) {
			// First, copy cube information into client-side floating point buffers.
			final FloatBuffer cubePositionsBuffer;
			final FloatBuffer cubeNormalsBuffer;
			final FloatBuffer cubeTextureCoordinatesBuffer;
			
			cubePositionsBuffer = ByteBuffer.allocateDirect(cubePositions.length * BYTES_PER_FLOAT)
			.order(ByteOrder.nativeOrder()).asFloatBuffer();						
			cubePositionsBuffer.put(cubePositions).position(0);
			
			cubeNormalsBuffer = ByteBuffer.allocateDirect(cubeNormals.length * BYTES_PER_FLOAT * generatedCubeFactor * generatedCubeFactor * generatedCubeFactor)
			.order(ByteOrder.nativeOrder()).asFloatBuffer();
			
			for (int i = 0; i < (generatedCubeFactor * generatedCubeFactor * generatedCubeFactor); i++) {
				cubeNormalsBuffer.put(cubeNormals);
			}
						
			cubeNormalsBuffer.position(0);
			
			cubeTextureCoordinatesBuffer = ByteBuffer.allocateDirect(cubeTextureCoordinates.length * BYTES_PER_FLOAT * generatedCubeFactor * generatedCubeFactor * generatedCubeFactor)
			.order(ByteOrder.nativeOrder()).asFloatBuffer();
			
			for (int i = 0; i < (generatedCubeFactor * generatedCubeFactor * generatedCubeFactor); i++) {
				cubeTextureCoordinatesBuffer.put(cubeTextureCoordinates);
			}
			
			cubeTextureCoordinatesBuffer.position(0);
			
			return new FloatBuffer[] {cubePositionsBuffer, cubeNormalsBuffer, cubeTextureCoordinatesBuffer};
		}		
		
		FloatBuffer getInterleavedBuffer(float[] cubePositions, float[] cubeNormals, float[] cubeTextureCoordinates, int generatedCubeFactor) {
			final int cubeDataLength = cubePositions.length 
	                 + (cubeNormals.length * generatedCubeFactor * generatedCubeFactor * generatedCubeFactor) 
	                 + (cubeTextureCoordinates.length * generatedCubeFactor * generatedCubeFactor * generatedCubeFactor);			
			int cubePositionOffset = 0;
			int cubeNormalOffset = 0;
			int cubeTextureOffset = 0;
			
			final FloatBuffer cubeBuffer = ByteBuffer.allocateDirect(cubeDataLength * BYTES_PER_FLOAT)
					.order(ByteOrder.nativeOrder()).asFloatBuffer();											
			
			for (int i = 0; i < generatedCubeFactor * generatedCubeFactor * generatedCubeFactor; i++) {								
				for (int v = 0; v < 36; v++) {
					cubeBuffer.put(cubePositions, cubePositionOffset, POSITION_DATA_SIZE);
					cubePositionOffset += POSITION_DATA_SIZE;
					cubeBuffer.put(cubeNormals, cubeNormalOffset, NORMAL_DATA_SIZE);
					cubeNormalOffset += NORMAL_DATA_SIZE;
					cubeBuffer.put(cubeTextureCoordinates, cubeTextureOffset, TEXTURE_COORDINATE_DATA_SIZE);
					cubeTextureOffset += TEXTURE_COORDINATE_DATA_SIZE;					
				}
				
				// The normal and texture data is repeated for each cube.
				cubeNormalOffset = 0;
				cubeTextureOffset = 0;	
			}
			
			cubeBuffer.position(0);
			
			return cubeBuffer;
		}
	}
	
	
	class CubesWithVboWithStride extends Cubes {
		final int mCubeBufferIdx;

		CubesWithVboWithStride(float[] cubePositions, float[] cubeNormals, float[] cubeTextureCoordinates, int generatedCubeFactor) {
			FloatBuffer cubeBuffer = getInterleavedBuffer(cubePositions, cubeNormals, cubeTextureCoordinates, generatedCubeFactor);			
			
			// Second, copy these buffers into OpenGL's memory. After, we don't need to keep the client-side buffers around.					
			final int buffers[] = new int[1];
			GLES30.glGenBuffers(1, buffers, 0);						

			GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0]);
			GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, cubeBuffer.capacity() * BYTES_PER_FLOAT, cubeBuffer, GLES30.GL_STATIC_DRAW);			

			GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);

			mCubeBufferIdx = buffers[0];			
			
			cubeBuffer.limit(0);
			cubeBuffer = null;
		}

		@Override
		public void render() {	    
			final int stride = (POSITION_DATA_SIZE + NORMAL_DATA_SIZE + TEXTURE_COORDINATE_DATA_SIZE) * BYTES_PER_FLOAT;
			
			// Pass in the position information
			GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mCubeBufferIdx);
			GLES30.glEnableVertexAttribArray(mPositionHandle);
			GLES30.glVertexAttribPointer(mPositionHandle, POSITION_DATA_SIZE, GLES30.GL_FLOAT, false, stride, 0);

			// Pass in the normal information
			GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mCubeBufferIdx);
			GLES30.glEnableVertexAttribArray(mNormalHandle);
			GLES30.glVertexAttribPointer(mNormalHandle, NORMAL_DATA_SIZE, GLES30.GL_FLOAT, false, stride, POSITION_DATA_SIZE * BYTES_PER_FLOAT);
			
			// Pass in the texture information
			GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mCubeBufferIdx);
			GLES30.glEnableVertexAttribArray(mTextureCoordinateHandle);
			GLES30.glVertexAttribPointer(mTextureCoordinateHandle, TEXTURE_COORDINATE_DATA_SIZE, GLES30.GL_FLOAT, false,
					stride, (POSITION_DATA_SIZE + NORMAL_DATA_SIZE) * BYTES_PER_FLOAT);

			// Clear the currently bound buffer (so future OpenGL calls do not use this buffer).
			GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);

			// Draw the cubes.
			GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, mActualCubeFactor * mActualCubeFactor * mActualCubeFactor * 36);
		}

		@Override
		public void release() {
			// Delete buffers from OpenGL's memory
			final int[] buffersToDelete = new int[] { mCubeBufferIdx };
			GLES30.glDeleteBuffers(buffersToDelete.length, buffersToDelete, 0);
		}
	}
	
	 //
    // Create a heat map 3D texture. Is a single channel texture 
    //
    public static int createHeatMap3DTexture(int size)
    {
        // Texture object handle
        int[] textureId = new int[1];
        
        // 2x2x2 Image, 1 bytes per pixel (R)
        byte[] pixels = new byte[size*size*size];
        
        float step =  (2.0f/(float)size);
        float xpos = -1;
        float ypos = -1;
        float zpos = -1;
        int count = 0;
        for(int x = 0; x < size; x ++)
        {
        	for(int y = 0; y < size; y ++)
        	{
        		for(int z = 0; z < size; z ++)
        		{
        			int temp = temperature(xpos, ypos, zpos);
        			if(temp < 0)
        				temp = 0;
        			pixels[count++] = (byte) temp;
        			zpos += step;
        		}
        		ypos += step;
        		zpos = -1;
        	}
        	ypos = -1;
        	xpos += step;
        }
        
        ByteBuffer pixelBuffer = ByteBuffer.allocateDirect(size*size*size);
        pixelBuffer.put(pixels).position(0);

        // Use tightly packed data
        GLES30.glPixelStorei ( GLES30.GL_UNPACK_ALIGNMENT, 1 );

        //  Generate a texture object
        GLES30.glGenTextures ( 1, textureId, 0 );

        // Bind the texture object
        GLES30.glBindTexture ( GLES30.GL_TEXTURE_3D, textureId[0] );

        //  Load the texture
        GLES30.glTexImage3D ( GLES30.GL_TEXTURE_3D, 0, GLES30.GL_R8, size, size, size, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, pixelBuffer );

        // Set the filtering mode
        GLES30.glTexParameteri ( GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST );
        GLES30.glTexParameteri ( GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST );

        return textureId[0];        
    }
    
    private static int temperature(float x, float y, float z)
    {
    	double temp = 0.0f;
    	
    	for(int i = 0; i < 4; i++)
    	{
    		if(i == 0)
    		{
    			double r = (x - 1.0) * (x - 1.0)  + y*y + z*z;
    			temp += 90 * Math.exp(-5.0 * r);
    		}
    		
    		if(i == 1)
    		{
    			double r = (x + 1.0) * (x + 1.0) + (y - 0.30) * (y - 0.30)  + z * z;
    			temp += 120 * Math.exp(-5.0 * r);
    		}
    		
    		if(i == 2)
    		{
    			double r = x * x + (y - 1.0) * (y - 1.0) + z * z;
    			temp += 120 * Math.exp(-5.0 * r);
    		}
    		
    		if(i == 3)
    		{
    			double r = x*x + (y - 0.04) * (y - 0.04) + (z - 1.0) * (z - 1.0);
    			temp += 170 * Math.exp(-5.0 * r);
    		}
    	}
    	
    	return (int)temp;
    }
    
    public void setAlpha(float alpha)
    {
    	mAlpha = alpha;
    }
    public void setMin(float min)
    {
    	mMin = min;
    }
    public void setMax(float max)
    {
    	mMax = max;
    }
    public void setDist(float dist)
    {
    	mDist = dist;
    }
    public void setSteps(float steps)
    {
    	mSteps = steps;
    }
    public void setZoom(float zoom)
    {
    	mZoom = zoom;
    }
}
