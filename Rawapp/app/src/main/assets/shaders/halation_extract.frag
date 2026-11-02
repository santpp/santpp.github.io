#version 300 es
// Downsampled bright-light extraction feeding the halation glow.
precision highp float;

uniform sampler2D uScene;
uniform float uThreshold;

out vec4 outColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(uScene, 0));
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(uScene, 0) / 4);
    vec3 sum = texture(uScene, uv + texel * vec2(-1.0, -1.0)).rgb
             + texture(uScene, uv + texel * vec2( 1.0, -1.0)).rgb
             + texture(uScene, uv + texel * vec2(-1.0,  1.0)).rgb
             + texture(uScene, uv + texel * vec2( 1.0,  1.0)).rgb;
    vec3 rgb = sum * 0.25;
    float bright = max(max(rgb.r, rgb.g), rgb.b);
    float excess = max(bright - uThreshold, 0.0);
    outColor = vec4(rgb * (excess / max(bright, 1e-4)), 1.0);
}
