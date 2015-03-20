#version 300 es
precision mediump float;       	// Set the default precision to medium. We don't need as high of a 
								// precision in the fragment shader.
uniform vec3 u_LightPos;       	// The position of the light in eye space.


// The input textures.
uniform sampler3D u_Texture_BLL;  
uniform sampler3D u_Texture_BLR;  
uniform sampler3D u_Texture_BUL;  
uniform sampler3D u_Texture_BUR;  

uniform sampler3D u_Texture_TLL;  
uniform sampler3D u_Texture_TLR;   
uniform sampler3D u_Texture_TUL;   
uniform sampler3D u_Texture_TUR;   

uniform mat4 u_MMatrix;		// A constant representing the combined model/view matrix.    
uniform mat4 u_MVMatrix;		// A constant representing the combined model/view matrix.
uniform mat4 u_MVPMatrix;		// A constant representing the combined model/view matrix. 
   
in vec3 v_Position;		    // Interpolated position for this fragment.
in vec3 v_Normal;         	// Interpolated normal for this fragment.
in vec3 v_TexCoordinate;    // Interpolated texture coordinate per fragment.

//Slider values
uniform float uAmax;
uniform float uMin;
uniform float uMax;
uniform float uNumSteps;
uniform float uDist;
uniform float uLightToggle;
uniform float uGrid;


//This function will take a given global texture coordinate and translate it to the appropriate local texture coordinate
float sampleTextures(vec3 tc)
{
	vec3 temp;
	//left hand side of the texture
	if(tc.x < .5)
	{
		temp.x = 2.0 * tc.x;
		
		//lower half of the texture
		if(tc.y < .5)
		{
			temp.y = 2.0 * tc.y;
			
			//closer part of the texture
			if(tc.z < .5)
			{
				temp.z = 2.0 * tc.z;
				
				//sample BLL
				return texture(u_Texture_BLL, temp).r;
			}
			else
			{
				temp.z = 2.0 * tc.z - 1.0;
				
				//sample BUL
				return texture(u_Texture_BUL, temp).r;
			}
			
		}
		else //upper half of the texture
		{
			temp.y = 2.0 * tc.y - 1.0;
			
			//closer part of the texture
			if(tc.z < .5)
			{
				temp.z = 2.0 * tc.z;
				
				//sample TLL
				return texture(u_Texture_TLL, temp).r;
			}
			else
			{
				temp.z = 2.0 * tc.z - 1.0;
				
				//sample TUL
				return texture(u_Texture_TUL, temp).r;
			}
		}
	}
	else //right hand side of the texture
	{
		temp.x = 2.0 * tc.x - 1.0;
		
		//lower half of the texture
		if(tc.y < .5)
		{
			temp.y = 2.0 * tc.y;
			
			//closer part of the texture
			if(tc.z < .5)
			{
				temp.z = 2.0 * tc.z;
				
				//sample BLR
				return texture(u_Texture_BLR, temp).r;
			}
			else
			{
				temp.z = 2.0 * tc.z - 1.0;
				
				//sample BUR
				return texture(u_Texture_BUR, temp).r;
			}
			
		}
		else //upper half of the texture
		{
			temp.y = 2.0 * tc.y - 1.0;
			
			//closer part of the texture
			if(tc.z < .5)
			{
				temp.z = 2.0 * tc.z;
				
				//sample TLR
				return texture(u_Texture_TLR, temp).r;
			}
			else
			{
				temp.z = 2.0 * tc.z - 1.0;
				
				//sample TUR
				return texture(u_Texture_TUR, temp).r;
			}
		}
	}
	
	return 0.0;
	
	
}
 
// The entry point for our fragment shader.
void main()                    		
{    
	float astar = 1.0;
	vec3 cstar  = vec3(0., 0., 0.);
	vec3 camDir = vec3(inverse(u_MMatrix) * vec4(0.0, 0.0, -1., 0.));
	
	//Get the sampling ray direction		
	vec3 uDirSTP = camDir/uDist;
	
	
	vec3 STP = v_TexCoordinate;
	
	vec3 gradient;
	bool gradFound = false;
	
	for(int i = 0; i < int(uNumSteps); i++, STP += uDirSTP)
	{
		if(any(lessThan(STP, vec3(0., 0., 0.))))
			break;
		if(any(greaterThan(STP, vec3(1.0, 1.0, 1.0))))
			break;
		
		
		//Sample the texture
		
		float scalar;
		
		if(uGrid == 1.0f)
			scalar = sampleTextures(STP);	
		else	
			scalar = texture(u_Texture_BLL, STP).r;
		
		//Skip if they're past thresholds
		if(scalar <= uMin || scalar >= uMax)
		{
			continue;
		}
		
		//Convert to color here
		vec3 rgb = vec3(scalar, scalar, scalar);
		
		float alpha = uAmax;
		
		cstar += astar * alpha * rgb; 
		
		astar *= (1.0 - alpha);
		
		//Break if rest of trace doesn't matter
		if(astar <= 0.001)
			break;
	}
	
	if(uLightToggle == 0.)
	{
    	gl_FragColor = vec4(cstar, 1.0);
    	return;
    }
	
	
	//Apply lighting
	vec3 lightPos = vec3(0.0, 0.0, 1.0);
	vec3 lf = normalize(lightPos) - normalize(v_Position);
	vec3 light = normalize(lf);
	vec3 ambient = .5 * cstar;
	float d  = max(dot(gradient, light), 0.);
	vec3 diffuse = d * cstar;
	
    gl_FragColor = vec4(ambient + diffuse, 1.0);
    
  }                                                                     	

