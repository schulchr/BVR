#version 300 es
precision mediump float;       	// Set the default precision to medium. We don't need as high of a 
								// precision in the fragment shader.
uniform vec3 u_LightPos;       	// The position of the light in eye space.
uniform sampler3D u_Texture;    // The input texture.
uniform mat4 u_MMatrix;			// A constant representing the combined model/view matrix.    

//Slider values
uniform float uAmax;
uniform float uMin;
uniform float uMax;

uniform float uNumSteps;
uniform float uDist;

in vec3 v_Position;		// Interpolated position for this fragment.
in vec3 v_Normal;         	// Interpolated normal for this fragment.
in vec3 v_TexCoordinate;   // Interpolated texture coordinate per fragment.

// routine to convert HSV to RGB 
// 
// Reference:  Foley, van Dam, Feiner, Hughes, 
//      "Computer Graphics Principles and Practices,"   
vec3 HsvRgb(vec3 hsv)
{
	vec3 rgb;
	float r, g, b;          // red, green, blue 
	float tol = 0.0001;
	// guarantee valid input: 
	if (hsv.x < (0. + tol) && hsv.x > (0. - tol))
	{
	hsv.x = 0.;
	}
	if (hsv.x < (6. + tol) && hsv.x > (6. - tol))
	{
	hsv.x = 0.;
	}

	float h = hsv.x / 60.;
	while (h >= 6.) h -= 6.;
	while (h <  0.)     h += 6.;
	
	float s = hsv.y;
	if (s < 0.)
	s = 0.;
	if (s > 1.)
	s = 1.;

	float v = hsv.z;
	if (v < 0.)
	v = 0.;
	if (v > 1.)
	v = 1.;


// if sat==0, then is a gray: 

	if (s == 0.0)
	{
		rgb.x = rgb.y = rgb.z = v;
		return rgb;
	}


// get an rgb from the hue itself: 

int i = int(floor(h));
float f = h - float(i);
float p = v * (1. - s);
float q = v * (1. - s*f);
float t = v * (1. - (s * (1. - f)));

	switch (i)
	{
		case 0:
		r = v;  g = t;  b = p;
		break;
		
		case 1:
		r = q;  g = v;  b = p;
		break;
		
		case 2:
		r = p;  g = v;  b = t;
		break;
		
		case 3:
		r = p;  g = q;  b = v;
		break;
		
		case 4:
		r = t;  g = p;  b = v;
		break;
		
		case 5:
		r = v;  g = p;  b = q;
		break;
	}
	
	rgb.r = r;
	rgb.g = g;
	rgb.b = b;
	
	return rgb;
}

 
// The entry point for our fragment shader.
void main()                    		
{    
	float astar = 1.0;
	vec3 cstar  = vec3(0., 0., 0.);
	vec3 camDir = normalize(vec3(inverse(u_MMatrix) * vec4(0.0, 0.0, 1., 0.)));
	
	//Get the sampling ray direction		
	vec3 uDirSTP = camDir/uDist;
	
		
	vec3 STP = v_TexCoordinate;
	
	for(int i = 0; i < int(uNumSteps); i++, STP += uDirSTP)
	{
		if(any(lessThan(STP, vec3(0., 0., 0.))) || any(greaterThan(STP, vec3(1.0, 1.0, 1.0))))
			break;
			
		//Sample the texture
		float scalar = texture(u_Texture, STP).r;
		
		//Skip if they're past thresholds
		if(scalar < uMin || scalar > uMax)
		{
			continue;
		}
		
			
		//Convert to color here
		vec3 rgb = HsvRgb(vec3(240.-240.*(scalar/(.39215)), 1., 1.));		
		//
		
		float alpha = uAmax;
		
		cstar += astar * alpha * rgb; 
		
		astar *= (1.0 - alpha);
		
		//Break if rest of trace doesn't matter
		if(astar == 0.)
			break;
	}
    gl_FragColor = vec4(cstar, 1.0);
  }                                                                     	

