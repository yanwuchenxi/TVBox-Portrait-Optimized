#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTexCoord;
uniform samplerExternalOES sTexture;
uniform float uSaturation;
uniform float uContrast;
uniform float uBrightness;
uniform float uWarmth;
uniform float uGamma;

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec4 color = texture2D(sTexture, vTexCoord);
    vec3 rgb = color.rgb;

    // gamma
    rgb = pow(max(rgb, vec3(0.0001)), vec3(1.0 / max(uGamma, 0.1)));

    // saturation via HSV
    vec3 hsv = rgb2hsv(rgb);
    hsv.y = clamp(hsv.y * uSaturation, 0.0, 1.0);
    rgb = hsv2rgb(hsv);

    // contrast + brightness
    rgb = (rgb - 0.5) * uContrast + 0.5 + uBrightness;

    // warmth: shift R/B
    rgb.r = rgb.r + uWarmth * 0.06;
    rgb.b = rgb.b - uWarmth * 0.06;

    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
}
