package com.bvr.android.grid;

public class GridDataCamera {
	float[] loc = new float[3]; //location of the camera
	float[] dir = new float[3]; //direction that the camera is looking at
	
	//View volume information of the camera
	float far, near;
	float left, right;
	float top, bottom;
	
	//vectors defining the view volume
	float[] a = new float[3];//need this for creating a vector for a given grid point
	float[] ab = new float[3];
	float[] ad = new float[3];
	float[] ae = new float[3];
	
	public GridDataCamera(float f, float n, float l, float r, float t, float b)
	{
		loc[0] = 0;
		loc[1] = 0;
		loc[2] = 1;
		
		dir[0] = 0;
		dir[1] = 0;
		dir[2] = -1;
		
		far = f;
		near = n;
		left = l;
		right = r;
		top = t;
		bottom = b;		
	}
	
	
	/**
	 * Takes a gridpoint and determines whether or not the given gridpoint is inside the view volume of the camera.
	 */
	public boolean isInsideView(GridGridpoint point)
	{
		//Create the vector to compare to view volume vectors
		float[] ap = new float[3];
		ap[0] = point.x - a[0];
		ap[1] = point.y - a[1];
		ap[2] = point.z - a[2];
		
		//Take the dot products and determine whether or not the point in question is inside
		float APAB = dotProd(ap, ab);
		float ABAB = dotProd(ab, ab);
		float APAD = dotProd(ap, ad);
		float ADAD = dotProd(ad, ad);
		float APAE = dotProd(ap, ae);
		float AEAE = dotProd(ae, ae);
		
		if ((0 <= APAB && APAB <= ABAB) && (0 <= APAD && APAD <= ADAD) && (0 <= APAE && APAE <= AEAE))
			return true;		
		
		return false;
	}
	
	/**
	 * Will update the view volume for the current view. 
	 */
	public void updateViewVolume()
	{
		float[] perpDirY = new float[3];
		float[] perpDirZ = new float[3];
		
		//4 vertices that describe the view volume
		float[] b = new float[3];
		float[] d = new float[3];
		float[] e = new float[3];
		
		//normalize the camera direction
		float camDirLength = (float) Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
		dir[0] /= camDirLength;
		dir[1] /= camDirLength;
		dir[2] /= camDirLength;
		
		//Find vectors perpendicular to the camera
		float up[] = new float[3];//up vector
		up[0] = 0;
		up[1] = 1;
		up[2] = 0;
		
		perpDirZ = crossProd(up, dir);
		perpDirY = crossProd(dir,perpDirZ);
		
		//update the 4 points that define the view volume
		//Consult the notebook to get a better visual representation of what's going on
		
		a[0] = loc[0] + dir[0] * near + perpDirZ[0] * left + -perpDirY[0] * bottom;
		a[1] = loc[1] + dir[1] * near + perpDirZ[1] * left + -perpDirY[1] * bottom;
		a[2] = loc[2] + dir[2] * near + perpDirZ[2] * left + -perpDirY[2] * bottom;
		
		b[0] = loc[0] + dir[0] * far + perpDirZ[0] * left + -perpDirY[0] * bottom;
		b[1] = loc[1] + dir[1] * far + perpDirZ[1] * left + -perpDirY[1] * bottom;
		b[2] = loc[2] + dir[2] * far + perpDirZ[2] * left + -perpDirY[2] * bottom;
		
		d[0] = loc[0] + dir[0] * near - perpDirZ[0] * right + -perpDirY[0] * bottom;
		d[1] = loc[1] + dir[1] * near - perpDirZ[1] * right + -perpDirY[1] * bottom;
		d[2] = loc[2] + dir[2] * near - perpDirZ[2] * right + -perpDirY[2] * bottom;
		
		e[0] = loc[0] + dir[0] * near + perpDirZ[0] * left + perpDirY[0] * top;
		e[1] = loc[1] + dir[1] * near + perpDirZ[1] * left + perpDirY[1] * top;
		e[2] = loc[2] + dir[2] * near + perpDirZ[2] * left + perpDirY[2] * top;
		
		//Create the 3 vectors defining the volume
		ab[0] = b[0] - a[0];
		ab[1] = b[1] - a[1];
		ab[2] = b[2] - a[2];
		
		ad[0] = d[0] - a[0];
		ad[1] = d[1] - a[1];
		ad[2] = d[2] - a[2];
		
		ae[0] = e[0] - a[0];
		ae[1] = e[1] - a[1];
		ae[2] = e[2] - a[2];
		
	}
	
	/**
	 * Will update the location of the camera and also make the direction point to the center, for now.
	 */
	public void updateLocation(float x, float y, float z)
	{
		loc[0] = x;
		loc[1] = y;
		loc[2] = z;
		
		dir[0] = 0 - x;
		dir[1] = 0 - y;
		dir[2] = 0 - z;
	}
	
	/**
	 * Updates the direction that the camera is looking at.
	 */
	public void updateDirection(float x, float y, float z)
	{
		dir[0] = x;
		dir[1] = y;
		dir[2] = z;
	}
	
	public float dotProd(float[] a, float[] b)
	{
		float dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
		if(-0.0001 < dot && dot < 0)
			return 0;
		return dot;
	}
	
	public float[] crossProd(float[] a, float[] b)
	{
		float[] result = new float[3];		
		
		result[0] = a[1]*b[2] - a[2]*b[1];		
		result[1] = a[2]*b[0] - a[0]*b[2];		
		result[2] = a[0]*b[1] - a[1]*b[0];
		
		return result;
	}
}
