#version 300 es
// Halation, vignette, gamut compression, filmic tone curve, 3D colour LUT, grain, sRGB.
precision highp float;
precision highp sampler3D;

uniform sampler2D uScene;
uniform sampler2D uHalation;
uniform sampler2D uToneCurve;   // 1D, indexed by stops around mid-grey
uniform sampler3D uLut;
uniform vec2 uCurveStops;       // min, max stops
uniform float uLutSize;
uniform float uHalationAmount;
uniform float uGrainAmount;
uniform float uVignette;
uniform bool uLookEnabled;

out vec4 outColor;

const vec3 HALATION_TINT = vec3(1.0, 0.32, 0.10);
const float MID_GREY = 0.18;

float encodeSrgb(float v) {
    v = clamp(v, 0.0, 1.0);
    return v <= 0.0031308 ? 12.92 * v : 1.055 * pow(v, 1.0 / 2.4) - 0.055;
}

vec3 encodeSrgb(vec3 c) { return vec3(encodeSrgb(c.r), encodeSrgb(c.g), encodeSrgb(c.b)); }

// Pulls out-of-gamut and extremely saturated colours toward the achromatic axis.
vec3 compressGamut(vec3 rgb) {
    float ach = max(max(rgb.r, rgb.g), rgb.b);
    if (ach <= 0.0) return vec3(0.0);
    vec3 dist = (ach - rgb) / ach;
    const float threshold = 0.8;
    const float scale = 0.3;
    vec3 over = max(dist - threshold, 0.0);
    vec3 compressed = threshold + over / (1.0 + over / scale);
    dist = mix(dist, compressed, step(threshold, dist));
    return ach - dist * ach;
}

float toneCurve(float v) {
    float stops = log2(max(v, 1e-6) / MID_GREY);
    float u = (stops - uCurveStops.x) / (uCurveStops.y - uCurveStops.x);
    float size = float(textureSize(uToneCurve, 0).x);
    return texture(uToneCurve, vec2((clamp(u, 0.0, 1.0) * (size - 1.0) + 0.5) / size, 0.5)).r;
}

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float grainNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i), b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0)), d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y) - 0.5;
}

void main() {
    vec2 size = vec2(textureSize(uScene, 0));
    vec2 uv = gl_FragCoord.xy / size;
    vec3 scene = texture(uScene, uv).rgb;

    if (!uLookEnabled) {
        outColor = vec4(encodeSrgb(clamp(scene, 0.0, 1.0)), 1.0);
        return;
    }

    scene += texture(uHalation, uv).rgb * HALATION_TINT * uHalationAmount;
    vec2 centered = uv * 2.0 - 1.0;
    scene *= 1.0 - uVignette * smoothstep(0.3, 2.0, dot(centered, centered));
    scene = compressGamut(max(scene, vec3(-0.05)));
    vec3 display = vec3(toneCurve(scene.r), toneCurve(scene.g), toneCurve(scene.b));

    vec3 encoded = encodeSrgb(display);
    vec3 lutCoord = (encoded * (uLutSize - 1.0) + 0.5) / uLutSize;
    vec3 graded = texture(uLut, lutCoord).rgb;

    float luma = dot(graded, vec3(0.2126, 0.7152, 0.0722));
    float mids = 4.0 * luma * (1.0 - luma);
    vec2 g = gl_FragCoord.xy / 1.4;
    float n = grainNoise(g) + 0.5 * grainNoise(g * 2.1 + 17.0);
    vec3 chroma = vec3(grainNoise(g + 101.0), grainNoise(g + 211.0), grainNoise(g + 307.0));
    graded += (vec3(n) + chroma * 0.25) * uGrainAmount * 0.07 * (0.35 + 0.65 * mids);

    outColor = vec4(clamp(graded, 0.0, 1.0), 1.0);
}
