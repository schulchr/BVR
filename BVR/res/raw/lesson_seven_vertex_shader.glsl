#version 300 es
precision mediump float;       	// Set the default precision to medium. We don't need as high of a 
								// precision in the fragment shader.
uniform mat4 u_MVPMatrix;		// A constant representing the combined model/view/projection matrix.      		       
uniform mat4 u_MVMatrix;		// A constant representing the combined model/view matrix. 
uniform mat4 u_VPMatrix;		// A constant representing the combined model/view matrix.
uniform mat4 u_MMatrix;		// A constant representing the combined model/view matrix.
       		
		  			
attribute vec4 a_Position;		// Per-vertex position information we will pass in.   							
attribute vec3 a_Normal;		// Per-vertex normal information we will pass in.      
attribute vec2 a_TexCoordinate; // Per-vertex texture coordinate information we will pass in. 		
		  
out vec3 v_Position;		// This will be passed into the fragment shader.       		          		
out vec3 v_Normal;			// This will be passed into the fragment shader.  
out vec3 v_TexCoordinate;   // This will be passed into the fragment shader.    		
		  
// The entry point for our vertex shader.  
void main()                                                 	
{                                                         
	// Transform the vertex into eye space. 	
	//v_Position = vec3(u_MMatrix * a_Position);            		
	v_Position = a_Position.xyz;
	// Pass through the texture coordinate.
	v_TexCoordinate = vec3((a_Position.x + 1.0)/2.0, (a_Position.y + 1.0)/2.0, (a_Position.z + 1.0)/2.0);                                      
	//v_TexCoordinate = vec3((v_Position.x + 1.0)/2.0, (v_Position.y + 1.0)/2.0, (v_Position.z + 1.0)/2.0);     
	// Transform the normal's orientation into eye space.
    v_Normal = vec3(u_MVMatrix * vec4(a_Normal, 0.0));
          
	// gl_Position is a special variable used to store the final position.
	// Multiply the vertex by the matrix to get the final point in normalized screen coordinates.
	gl_Position = u_MVPMatrix * a_Position;                       		  
}                                                          