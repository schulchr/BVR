package com.bvr.android.common;

import java.nio.ByteBuffer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLUtils;

public class TextureHelper
{
	public static int loadTexture(final Context context, final int resourceId)
	{
		final int[] textureHandle = new int[1];
		
		GLES30.glGenTextures(1, textureHandle, 0);
		
		if (textureHandle[0] != 0)
		{
			final BitmapFactory.Options options = new BitmapFactory.Options();
			options.inScaled = false;	// No pre-scaling

			// Read in the resource
			final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, options);
						
			// Bind to the texture in OpenGL
			GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureHandle[0]);
			
			// Set filtering
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);
			
			// Load the bitmap into the bound texture.
			GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0);
			
			// Recycle the bitmap, since its data has been loaded into OpenGL.
			bitmap.recycle();						
		}
		
		if (textureHandle[0] == 0)
		{
			throw new RuntimeException("Error loading texture.");
		}
		
		return textureHandle[0];
	}
	
	 //
    // Create a simple 2x2 texture image with four different colors
    //
    public static int createSimpleTexture2D( )
    {
        // Texture object handle
        int[] textureId = new int[1];
        
        // 2x2 Image, 3 bytes per pixel (R, G, B)
        byte[] pixels = 
            {  
                (byte) 0xff,   0,   0, // Red
                0, (byte) 0xff,   0, // Green
                0,   0, (byte) 0xff, // Blue
                (byte) 0xff, (byte) 0xff,   0  // Yellow
            };
        ByteBuffer pixelBuffer = ByteBuffer.allocateDirect(4*3);
        pixelBuffer.put(pixels).position(0);

        // Use tightly packed data
        GLES30.glPixelStorei ( GLES30.GL_UNPACK_ALIGNMENT, 1 );

        //  Generate a texture object
        GLES30.glGenTextures ( 1, textureId, 0 );

        // Bind the texture object
        GLES30.glBindTexture ( GLES30.GL_TEXTURE_2D, textureId[0] );

        //  Load the texture
        GLES30.glTexImage2D ( GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGB, 2, 2, 0, GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, pixelBuffer );

        // Set the filtering mode
        GLES30.glTexParameteri ( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST );
        GLES30.glTexParameteri ( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST );

        return textureId[0];        
    }
    
    //
    // Create a simple 2x2x2 texture image with eight different colors
    //
    public static int createSimpleTexture3D( )
    {
        // Texture object handle
        int[] textureId = new int[1];
        
        // 2x2x2 Image, 3 bytes per pixel (R, G, B)
        byte[] pixels = 
            {  
                (byte) 0xff,           0,           0, // Red
                          0, (byte) 0xff,           0, // Green
                          0,           0, (byte) 0xff, // Blue
                (byte) 0xff, (byte) 0xff,           0, // Yellow
                
                (byte) 0xff, (byte) 0xff, (byte) 0xff, // White
                          0, (byte) 0xff, (byte) 0xff, // Teal
                (byte) 0xff,           0, (byte) 0xff, // Purple
                		  0,           0,           0  // Black
            };
        ByteBuffer pixelBuffer = ByteBuffer.allocateDirect(8*3);
        pixelBuffer.put(pixels).position(0);

        // Use tightly packed data
        GLES30.glPixelStorei ( GLES30.GL_UNPACK_ALIGNMENT, 1 );

        //  Generate a texture object
        GLES30.glGenTextures ( 1, textureId, 0 );

        // Bind the texture object
        GLES30.glBindTexture ( GLES30.GL_TEXTURE_3D, textureId[0] );

        //  Load the texture
        GLES30.glTexImage3D ( GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB, 2, 2, 2, 0, GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, pixelBuffer );

        // Set the filtering mode
        GLES30.glTexParameteri ( GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST );
        GLES30.glTexParameteri ( GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST );

        return textureId[0];        
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
        
        float step = (float) (1/size);
        float xpos = 0;
        float ypos = 0;
        float zpos = 0;
        int count = 0;
        for(int x = 0; x < size; x ++)
        {
        	for(int y = 0; y < size; y ++)
        	{
        		for(int z = 0; z < size; z ++)
        		{
        			int temp = temperature(xpos, ypos, zpos);
        			
        			pixels[count++] = (byte) temp;
        			zpos += step;
        		}
        		ypos += step;
        		zpos = 0;
        	}
        	ypos = 0;
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
    	float temp = 0.0f;
    	
    	for(int i = 0; i < 4; i++)
    	{
    		if(i == 0)
    		{
    			double r = Math.pow(x - 1.0, 2) + Math.pow(y - 0.0, 2) + Math.pow(z - 0.0, 2);
    			temp += 90 * Math.exp(-5 * r);
    		}
    		
    		if(i == 1)
    		{
    			double r = Math.pow(x + 1.0, 2) + Math.pow(y - 0.30, 2) + Math.pow(z - 0.0, 2);
    			temp += 120 * Math.exp(-5 * r);
    		}
    		
    		if(i == 2)
    		{
    			double r = Math.pow(x - 0.0, 2) + Math.pow(y - 1.0, 2) + Math.pow(z - 0.0, 2);
    			temp += 120 * Math.exp(-5 * r);
    		}
    		
    		if(i == 3)
    		{
    			double r = Math.pow(x - 0.0, 2) + Math.pow(y - 0.4, 2) + Math.pow(z - 1.0, 2);
    			temp += 170 * Math.exp(-5 * r);
    		}
    	}
    	
    	return (int)temp;
    }
}
