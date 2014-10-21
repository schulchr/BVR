#version 300 es
precision mediump float;       	// Set the default precision to medium. We don't need as high of a 
								// precision in the fragment shader.
uniform mat4 u_MVPMatrix;		// A constant representing the combined model/view/projection matrix.      		       
uniform mat4 u_MVMatrix;		// A constant representing the combined model/view matrix. 
uniform mat4 u_VPMatrix;		// A constant representing the combined model/view matrix.
uniform mat4 u_MMatrix;		// A constant representing the combined model/view matrix.
uniform float u_Zoom;
		  			
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
	v_Position = u_Zoom * a_Position.xyz;
	
	float factor = u_Zoom * .5;
	
	// Pass through the texture coordinate.
	v_TexCoordinate = vec3((v_Position.x + factor)/(2. * factor), (v_Position.y + factor)/(2. * factor), (v_Position.z + factor)/(2. * factor));
	
	// Transform the normal's orientation into eye space.
    v_Normal = vec3(u_MVMatrix * vec4(a_Normal, 0.0));
          
	// gl_Position is a special variable used to store the final position.

	gl_Position = u_MVPMatrix * a_Position;                   		  
}                                                          