package com.bvr.android.grid;

public class GridGridpoint {
	float x, y, z; //location of the grid point
	int[] textures;
	
	public GridGridpoint()
	{
		x = y = z = 0;
		textures = new int[8];
	}
}
