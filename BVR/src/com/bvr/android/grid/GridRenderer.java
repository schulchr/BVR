package com.bvr.android.grid;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
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

/**
 * This class implements our custom renderer. Note that the GL10 parameter
 * passed in is unused for OpenGL ES 2.0 renderers -- the static class GLES30 is
 * used instead.
 */
public class GridRenderer implements GLSurfaceView.Renderer {
	/** Used for debug logs. */
	private static final String TAG = "GridRenderer";

	private static GridActivity mGridActivity;
	private final GLSurfaceView mGlSurfaceView;
	
	/**
	 * The grid points from the GRID file
	 * and other information related to it
	 */
	static GridGridpoint[] gridPoints;
	static int gridWidth, gridHeight, gridDepth;
	static int gridTexWidth, gridTexHeight, gridTexDepth;
	static GridDataCamera gridCamera;
	static int loadedTextures[];
	static int loadedPoint = -100;
	static int radius;
	static float gridUsed = 0.0f;
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
	
	/** Allocate storage for the final combined matrix. This will be passed into the shader program. */
	private float[] mMVPMatrix = new float[16];
	
	/** Allocate storage for the final combined matrix. This will be passed into the shader program. */
	private float[] mVPMatrix = new float[16];
	
	/** Store the accumulated rotation. */
	private final float[] mAccumulatedRotation = new float[16];
	
	/** Store the current rotation. */
	private final float[] mCurrentRotation = new float[16];
	
	/** Store the current rotation. */
	private final float[] mZoomMatrix = new float[16];
	
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
	private int mTextureUniformHandle[];
	
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
	private int mAndroidDataHandle[] = new int[8];		
	
	// These still work without volatile, but refreshes are not guaranteed to happen.					
	public volatile float mDeltaX;					
	public volatile float mDeltaY;	
	
	/** Thread executor for generating cube data in the background. */
	private final ExecutorService mSingleThreadedExecutor = Executors.newSingleThreadExecutor();
	
	/** The current cubes object. */
	private Cubes mCubes;
	
	
	/** Filename of data to be read in */
	private static String mFilename;
	
	/** This will be used to pass in model slider information. */
	private int mAlphaHandle;
	private int mMinHandle;
	private int mMaxHandle;
	private int mDistHandle;
	private int mStepsHandle;
	private int mZoomHandle;
	private int mLightHandle;
	private int mGridHandle;
	
	/**
	 * values that are passed into the shader
	 */
	private static float mAlpha = 1.0f;
	private static float mMin = 0.0f;
	private static float mMax = 1.0f;
	private static float mSteps = 100.0f;
	private static float mDist  = 100.0f;
	private static float mZoom = 2.0f;
	private static float mLight = 0.0f;
	private static float mX = 0.0f;
	private static float mY = 0.0f;
	private static float mZ = 0.0f;
	private static float mZo = 0.0f;
	

	/**
	 * Initialize the model data.
	 */
	public GridRenderer(final GridActivity gridActivity, final GLSurfaceView glSurfaceView) {
		mGridActivity = gridActivity;	
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
							
							mGridActivity.runOnUiThread(new Runnable() {							
								@Override
								public void run() {
//									Toast.makeText(mGridActivity, "Out of memory; Dalvik takes a while to clean up the memory. Please try again.\nExternal bytes allocated=" + dalvik.system.VMRuntime.getRuntime().getExternalBytesAllocated(), Toast.LENGTH_LONG).show();								
								}
							});										
						}																	
					}				
				});
			} catch (OutOfMemoryError e) {
				// Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
				System.gc();
				
				mGridActivity.runOnUiThread(new Runnable() {							
					@Override
					public void run() {
//						Toast.makeText(mGridActivity, "Out of memory; Dalvik takes a while to clean up the memory. Please try again.\nExternal bytes allocated=" + dalvik.system.VMRuntime.getRuntime().getExternalBytesAllocated(), Toast.LENGTH_LONG).show();								
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
		loadedTextures = new int[8];
		
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
		final float eyeZ = 10.0f;

		// We are looking toward the distance
		final float lookX = 0.0f;
		final float lookY = 0.0f;
		final float lookZ = 1.0f;

		// Set our up vector. This is where our head would be pointing were we holding the camera.
		final float upX = 0.0f;
		final float upY = 1.0f;
		final float upZ = 0.0f;

		// Set the view matrix. This matrix can be said to represent the camera position.
		// NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination of a model and
		// view matrix. In OpenGL 2, we can keep track of these matrices separately if we choose.
		Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);		

		final String vertexShader = RawResourceReader.readTextFileFromRawResource(mGridActivity, R.raw.grid_vertex_shader);   		
 		final String fragmentShader = RawResourceReader.readTextFileFromRawResource(mGridActivity, R.raw.grid_fragment_shader);
 				
		final int vertexShaderHandle = ShaderHelper.compileShader(GLES30.GL_VERTEX_SHADER, vertexShader);		
		final int fragmentShaderHandle = ShaderHelper.compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentShader);		
		
		mProgramHandle = ShaderHelper.createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle, 
				new String[] {"a_Position",  "a_Normal", "a_TexCoordinate"});		            
        
		//setup grid points
		readGridFile();
		
		//setup grid camera
		float lengthX = (2.0f) / (float) (gridWidth - 1);
		float lengthY = (2.0f) / (float) (gridHeight - 1);
		float lengthZ = (2.0f) / (float) (gridDepth - 1);
		//GridDataCamera(float f, float n, float l, float r, float t, float b)
		gridCamera = new GridDataCamera(1.5f * lengthZ, 0.0f, .5f * lengthX, .5f * lengthX, .75f*lengthY, .75f*lengthY);
		
		gridCamera.updateLocation(0, 0, -1);
		
		//choose which textures to load in here
		if(mZoom >= 2.0)
			setGridTextures();
		//if(mZoom < 2.0 && mZoom >= 1.0)
		//	mAndroidDataHandle[0] = loadDownscaled(-1);
		//if(mZoom < 1.0)
			//load in small
			
        // Initialize the accumulated rotation matrix
        Matrix.setIdentityM(mAccumulatedRotation, 0);  
        
        //reset everything
        resetValues();
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
		final float far = 100.0f;		

        mTextureUniformHandle = new int[8];
		
		//Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, near, far);
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
        
                
        mTextureUniformHandle[GridConstants.BLL_TEX] = GLES30.glGetUniformLocation(mProgramHandle, "u_Texture_BLL");
        mTextureUniformHandle[GridConstants.BUL_TEX] = GLES30.glGetUniformLocation(mProgramHandle, "u_Texture_BUL");
        mTextureUniformHandle[GridConstants.BLR_TEX] = GLES30.glGetUniformLocation(mProgramHandle, "u_Texture_BLR");
        mTextureUniformHandle[GridConstants.BUR_TEX] = GLES30.glGetUniformLocation(mProgramHandle, "u_Texture_BUR");
        
        mTextureUniformHandle[GridConstants.TLL_TEX] = GLES30.glGetUniformLocation(mProgramHandle, "u_Texture_TLL");
        mTextureUniformHandle[GridConstants.TLR_TEX] = GLES30.glGetUniformLocation(mProgramHandle, "u_Texture_TLR");
        mTextureUniformHandle[GridConstants.TUL_TEX] = GLES30.glGetUniformLocation(mProgramHandle, "u_Texture_TUL");
        mTextureUniformHandle[GridConstants.TUR_TEX] = GLES30.glGetUniformLocation(mProgramHandle, "u_Texture_TUR");               
        
        mPositionHandle = GLES30.glGetAttribLocation(mProgramHandle, "a_Position");        
        mNormalHandle = GLES30.glGetAttribLocation(mProgramHandle, "a_Normal"); 
        mTextureCoordinateHandle = GLES30.glGetAttribLocation(mProgramHandle, "a_TexCoordinate");        

        mAlphaHandle    = GLES30.glGetUniformLocation(mProgramHandle, "uAmax");
        mMaxHandle    = GLES30.glGetUniformLocation(mProgramHandle, "uMax");
        mMinHandle    = GLES30.glGetUniformLocation(mProgramHandle, "uMin");
        mDistHandle    = GLES30.glGetUniformLocation(mProgramHandle, "uDist");
        mStepsHandle    = GLES30.glGetUniformLocation(mProgramHandle, "uNumSteps");
        mLightHandle   = GLES30.glGetUniformLocation(mProgramHandle, "uLightToggle");
        mGridHandle   = GLES30.glGetUniformLocation(mProgramHandle, "uGrid");
        mZoomHandle    = GLES30.glGetUniformLocation(mProgramHandle, "u_Zoom");
        
        // Calculate position of the light. Push into the distance.
        Matrix.setIdentityM(mLightModelMatrix, 0);                     
        Matrix.translateM(mLightModelMatrix, 0, 0.0f, 0.0f, 1.0f);
               
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
    	
    	if(mZoom < 2.25)
    		Matrix.scaleM(mZoomMatrix, 0, mZoom, mZoom, mZoom); 
    	else
    		Matrix.scaleM(mZoomMatrix, 0, 2f, 2f, 2f); 
    	
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
		
		//choose which textures to load in here
				if(mZoom >= 1.4)
					setGridTextures();
				else if(mZoom < 1.4 && mZoom >= 1.0)
					mAndroidDataHandle[0] = loadDownscaled(-1);
				else if(mZoom < 1.0)
					mAndroidDataHandle[0] = loadDownscaled(-2);
		
		// Pass in the texture information for each of the loaded in textures
		for(int i = 0; i < 8; i++)
		{
			// Set the active texture unit to texture unit 0.
			GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + i);
			// Bind the texture to this unit.
			GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, mAndroidDataHandle[i]);
			// Tell the texture uniform sampler to use this texture in the
			// shader by binding to texture unit 0.
			GLES30.glUniform1i(mTextureUniformHandle[i], i);
			
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);	
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE);
			
		}
		//Send in all slider info
		GLES30.glUniform1f(mAlphaHandle, mAlpha);
		GLES30.glUniform1f(mMaxHandle, mMax);
		GLES30.glUniform1f(mMinHandle, mMin);
		GLES30.glUniform1f(mStepsHandle, mSteps);
		GLES30.glUniform1f(mDistHandle, mDist);
		GLES30.glUniform1f(mZoomHandle, mZoom);
		GLES30.glUniform1f(mLightHandle, mLight);
		GLES30.glUniform1f(mGridHandle, gridUsed);

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
    // Read in the GRID file to populate the grid points 
    //
    public static void readGridFile()
    {
        //Read in the .grid file for data information
        try {
			File file = new File(mFilename);
			FileReader fileReader = new FileReader(file);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			StringBuffer stringBuffer = new StringBuffer();
			String line;
			
			//grab the dimensions of the grid. 
			line = bufferedReader.readLine();
			String[] split = line.split(" ");			
			gridWidth = Integer.parseInt(split[1]);
			
			line = bufferedReader.readLine();
			split = line.split(" ");			
			gridHeight = Integer.parseInt(split[1]);
			
			line = bufferedReader.readLine();
			split = line.split(" ");			
			gridDepth = Integer.parseInt(split[1]);
			
			//grab the dimensions each texture inside the grid. 
			line = bufferedReader.readLine();
			split = line.split(" ");			
			gridTexWidth = Integer.parseInt(split[1]);
			
			line = bufferedReader.readLine();
			split = line.split(" ");			
			gridTexHeight = Integer.parseInt(split[1]);
			
			line = bufferedReader.readLine();
			split = line.split(" ");			
			gridTexDepth = Integer.parseInt(split[1]);
			
			//setup the array for the number of gridpoints
			gridPoints = new GridGridpoint[gridWidth * gridHeight * gridDepth];
			
			int count = 0;
			while((line = bufferedReader.readLine()) != null){
				split = line.split(" "); 
				int i = 0;
				
				GridGridpoint point = new GridGridpoint();
				
				//grab the position of the grid point
				if(!split[i].startsWith("-"))
				{
					point.x = Float.parseFloat(split[i++]);
				}
				else
				{
					point.x = -Float.parseFloat(split[i++].substring(1));
				}
				
				if(!split[i].startsWith("-"))
				{
					point.y = Float.parseFloat(split[i++]);
				}
				else
				{
					point.y = -Float.parseFloat(split[i++].substring(1));
				}
				
				if(!split[i].startsWith("-"))
				{
					point.z = Float.parseFloat(split[i++]);
				}
				else
				{
					point.z = -Float.parseFloat(split[i++].substring(1));
				}
				
				//grab the textures related to the textures
				point.textures[GridConstants.BLL_TEX] = Integer.parseInt(split[i++]);
				point.textures[GridConstants.BLR_TEX] = Integer.parseInt(split[i++]);
				point.textures[GridConstants.BUL_TEX] = Integer.parseInt(split[i++]);
				point.textures[GridConstants.BUR_TEX] = Integer.parseInt(split[i++]);
				
				point.textures[GridConstants.TLL_TEX] = Integer.parseInt(split[i++]);
				point.textures[GridConstants.TLR_TEX] = Integer.parseInt(split[i++]);
				point.textures[GridConstants.TUL_TEX] = Integer.parseInt(split[i++]);
				point.textures[GridConstants.TUR_TEX] = Integer.parseInt(split[i++]);
				
				gridPoints[count++] = point;
			}
			
			fileReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
              
    }
    
    /**
     * This will loop through all the grid points and figure out which textures to use. 
     * Right now, this will just brute force the entire list of grid points. This can be optimized with the 
     */
    public void setGridTextures()
    {
    	gridCamera.updateViewVolume();
    	gridUsed = 1.0f;

    	for(int i = 0; i < gridPoints.length; i++)
    	{
    		
    		if(gridCamera.isInsideView(gridPoints[i]))
    		{
    			if(loadedPoint != i)
    			{
    				loadedPoint = i;
	    			loadGridTextures(gridPoints[i]);
    			}
    			return;
    		}
    		    			
    	}
    	
    }
    
    public void loadGridTextures(GridGridpoint point)
    {
    	//Just to get this working, reload all the textures.
    	//If it causes lag, then we'll deal with only loading what's needed.
    	
    	GLES30.glDeleteTextures(8, mAndroidDataHandle, 0);
    	
    	for(int i = 0; i < 8; i++)
    	{
    		mAndroidDataHandle[i] = loadRaw(point.textures[i]);
    	}
    	
    }
    
    //
    // loads in the raw file
    //
    public static int loadRaw(int fileNum)
    {
        // Texture object handle
        int[] textureId = new int[1];             
        
        /*
         *Here I will check whether or not this is non grid or not. 
         *For now, a fileNum of -1 will be small, -2 medium quality
         */
        //Read in the .raw file (binary file)
        String filename = null;
        if (fileNum >= 0)
        {
	        filename = mFilename.substring(0, mFilename.length() - 5);
	        filename = filename.concat("_");
	        filename = filename.concat(Integer.toString(fileNum));
	        filename = filename.concat(".raw");
        }
        File file = new File(filename);
        byte[] result = new byte[(int)file.length()];
        try {
          InputStream input = null;
          try {
            int totalBytesRead = 0;
            input = new BufferedInputStream(new FileInputStream(file));
            while(totalBytesRead < result.length){
              int bytesRemaining = result.length - totalBytesRead;
              //input.read() returns -1, 0, or more :
              int bytesRead = input.read(result, totalBytesRead, bytesRemaining); 
              if (bytesRead > 0){
                totalBytesRead = totalBytesRead + bytesRead;
              }
            }
            
          }
          finally {
            input.close();
          }
        }
        catch (FileNotFoundException ex) {
        	
        }
        catch (IOException ex) {
        	
        }
        
        ByteBuffer pixelPad, pixelBuffer;
        int width, height, depth;
       
    	pixelBuffer = ByteBuffer.allocateDirect(result.length);
        pixelBuffer.put(result).position(0);
        width  = gridTexWidth;
        height = gridTexHeight;
        depth  = gridTexDepth;
        
        

        // Use tightly packed data
        GLES30.glPixelStorei ( GLES30.GL_UNPACK_ALIGNMENT, 1 );

        //  Generate a texture object
        GLES30.glGenTextures ( 1, textureId, 0 );
        
        
        // Bind the texture object
        GLES30.glBindTexture ( GLES30.GL_TEXTURE_3D, textureId[0] );        
        //  Load the texture
        GLES30.glTexImage3D ( GLES30.GL_TEXTURE_3D, 0, GLES30.GL_R8, width, height, depth, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, pixelBuffer );

        // Set the filtering mode
        GLES30.glTexParameteri ( GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST );
        GLES30.glTexParameteri ( GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST );
        
        return textureId[0];        
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
    	//mZoom = zoom;
    	if(mX == 1)
    	{
    		gridCamera.loc[0] = 2 * zoom - 1;
    		
    		if(gridCamera.loc[0] > 1)
    			gridCamera.loc[0] = 1;
    		if(gridCamera.loc[0] < -1)
    			gridCamera.loc[0] = -1;
    	}
    	if(mY == 1)
    	{
    		gridCamera.loc[1] = 2 * zoom - 1;
    		if(gridCamera.loc[1] > 1)
    			gridCamera.loc[1] = 1;
    		if(gridCamera.loc[1] < -1)
    			gridCamera.loc[1] = -1;
    	}
    	
    	if(mZ == 1)
    	{
    		gridCamera.loc[2] = 2 * zoom - 1;
    		if(gridCamera.loc[2] > 1)
    			gridCamera.loc[2] = 1;
    		if(gridCamera.loc[2] < -1)
    			gridCamera.loc[2] = -1;
    	}
    	if(mZo == 1)
    		mZoom = zoom;
    }
    public void setFilename(String filename)
    {
    	mFilename = filename;
    }
    public void setLightToggle(float toggle)
    {
    	mLight = toggle;
    }    
    
    public void setXToggle(float toggle)
    {
    	mX = toggle;
    	if(mX == 1)
    	{
    		mY = 0;
    		mZ = 0;
    		mZo = 0;
    	}
    }
    public void setYToggle(float toggle)
    {
    	mY = toggle;
    	if(mY == 1)
    	{
    		mX = 0;
    		mZo = 0;
    		mZ = 0;
    	}
    }
    public void setZToggle(float toggle)
    {
    	mZ = toggle;
    	if(mZ == 1)
    	{
    		mY = 0;
    		mX = 0;
    		mZo = 0;
    	}
    }
    
    public void setZoomToggle(float toggle)
    {
    	mZo = toggle;
    	if(mZ == 1)
    	{
    		mY = 0;
    		mX = 0;
    		mZ = 0;
    	}
    }
    
    public void resetValues()
    {
    	mAlpha = 1.0f;
    	mMin = 0.0f;
    	mMax = 1.0f;
    	mSteps = 100.0f;
    	mDist  = 100.0f;
    	mZoom = 2.0f;
    	mLight = 0.0f;
    }
    public float getGridLocationPoint(int i)
    {
    	return gridCamera.loc[i];
    }
    
    
    public int loadDownscaled(int type)
    {
    	//already loaded in this type, don't do it again
    	if(loadedPoint == type)
    		return mAndroidDataHandle[0];
    	
    	//turn of the toggle for the grid
    	gridUsed = 0;
    	
    	GLES30.glDeleteTextures(8, mAndroidDataHandle, 0);
        // Texture object handle
        int[] textureId = new int[1];      
        String filename = null;
        
        filename = mFilename.substring(0, mFilename.length() - 5);        
        if(type == -1)
        	filename = filename.concat("Medium.raw");
        if(type == -2)
        	filename = filename.concat("Small.raw");
        
		int dim[] = new int[3];
		int ratio[] = new int[3];
        //Read in the .dat file for data dimensions
        try {
			File file = new File(filename + ".dat");
			FileReader fileReader = new FileReader(file);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			StringBuffer stringBuffer = new StringBuffer();
			String line;
			//dimensions of the data
			int count = 0;
			int count2 = 0;
			while ((line = bufferedReader.readLine()) != null) {
				if(count < 3)
					dim[count++] = Integer.parseInt(line);
				else
					ratio[count2++] = Integer.parseInt(line);				
			}
			fileReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

        
        //Read in the .raw file (binary file)
        File file = new File(filename);
        byte[] result = new byte[(int)file.length()];
        try {
          InputStream input = null;
          try {
            int totalBytesRead = 0;
            input = new BufferedInputStream(new FileInputStream(file));
            while(totalBytesRead < result.length){
              int bytesRemaining = result.length - totalBytesRead;
              //input.read() returns -1, 0, or more :
              int bytesRead = input.read(result, totalBytesRead, bytesRemaining); 
              if (bytesRead > 0){
                totalBytesRead = totalBytesRead + bytesRead;
              }
            }
            
          }
          finally {
            input.close();
          }
        }
        catch (FileNotFoundException ex) {
        	
        }
        catch (IOException ex) {
        	
        }
        
        ByteBuffer pixelPad, pixelBuffer;
        int width, height, depth;
        
        //If the data is needing padding in the z direction  
        float dimRatio = ((float)dim[1]/(float)dim[2]);
        float highEndRatio = (float) (ratio[2] + .1);
        float lowEndRatio = (float) (ratio[2] - .1);
        
       /* if(dimRatio >= highEndRatio || dimRatio  <= lowEndRatio)
        {
        	
        	//Find out how much padding is needed
        	int paddingNeeded = (dim[1] / ratio[2]) - dim[2];
        	
        	//Don't want to mess with negative padding right now...
        	if(paddingNeeded < 0)
        	{
        		pixelBuffer = ByteBuffer.allocateDirect(result.length);
                pixelBuffer.put(result).position(0);
                width = dim[0];
                height = dim[1];
                depth = dim[2];
        	}
        	else
        	{
	        	pixelPad = ByteBuffer.allocateDirect(dim[0] * dim[1] * paddingNeeded/2);
	            pixelBuffer = ByteBuffer.allocateDirect(dim[0] * dim[0] * dim[0]);
	            pixelBuffer.put(pixelPad);
	            pixelBuffer.put(result).position(0);
	            
	            width = dim[0];
	            height = dim[1];
	            depth = paddingNeeded + dim[2];
        	}
        }
        
        else
        {*/
        	pixelBuffer = ByteBuffer.allocateDirect(result.length);
            pixelBuffer.put(result).position(0);
            width = dim[0];
            height = dim[1];
            depth = dim[2];
       // }
        
        

        // Use tightly packed data
        GLES30.glPixelStorei ( GLES30.GL_UNPACK_ALIGNMENT, 1 );

        //  Generate a texture object
        GLES30.glGenTextures ( 1, textureId, 0 );

        // Bind the texture object
        GLES30.glBindTexture ( GLES30.GL_TEXTURE_3D, textureId[0] );

        //  Load the texture
        GLES30.glTexImage3D ( GLES30.GL_TEXTURE_3D, 0, GLES30.GL_R8, width, height, depth, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, pixelBuffer );
        
        // Set the filtering mode
        GLES30.glTexParameteri ( GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST );
        GLES30.glTexParameteri ( GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST );
        
        loadedPoint = type;
        
        return textureId[0];      
	}
}
