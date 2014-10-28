#version 300 es
precision mediump float;       	// Set the default precision to medium. We don't need as high of a 
								// precision in the fragment shader.
uniform vec3 u_LightPos;       	// The position of the light in eye space.
uniform sampler3D u_Texture;    // The input texture.
uniform mat4 u_MMatrix;		// A constant representing the combined model/view matrix.    
uniform mat4 u_MVMatrix;		// A constant representing the combined model/view matrix.
uniform mat4 u_MVPMatrix;		// A constant representing the combined model/view matrix. 
   
in vec3 v_Position;		// Interpolated position for this fragment.
in vec3 v_Normal;         	// Interpolated normal for this fragment.
in vec3 v_TexCoordinate;   // Interpolated texture coordinate per fragment.

//Slider values
uniform float uAmax;
uniform float uMin;
uniform float uMax;
uniform float uNumSteps;
uniform float uDist;
uniform float uLightToggle;


//Finds the gradient of the current point. 
vec3 findGradient(vec3 currTex, float sampleSize)
{
	vec3 s1, s2;
	
	s1.x = texture(u_Texture, vec3(currTex.x - sampleSize, currTex.y, currTex.z)).r;	
	s1.y = texture(u_Texture, vec3(currTex.x, currTex.y  - sampleSize, currTex.z)).r;	
	s1.z = texture(u_Texture, vec3(currTex.x, currTex.y, currTex.z - sampleSize)).r;	
	
	s2.x = texture(u_Texture, vec3(currTex.x + sampleSize, currTex.y, currTex.z)).r;	
	s2.y = texture(u_Texture, vec3(currTex.x, currTex.y  + sampleSize, currTex.z)).r;	
	s2.z = texture(u_Texture, vec3(currTex.x, currTex.y, currTex.z + sampleSize)).r;	
	
	return normalize(s1 - s2);
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
		float scalar = texture(u_Texture, STP).r;
		
		//Skip if they're past thresholds
		if(scalar <= uMin || scalar >= uMax)
		{
			continue;
		}
			
		if(!gradFound)
		{
			gradient = vec3(u_MVMatrix * vec4(findGradient(STP, .001), 0.));
			gradFound = true;
			//gl_FragColor = vec4(gradient, 1.0);
			//return;
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

