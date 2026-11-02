#version 300 es
// Linearisation, lens shading, white balance, Malvar-He-Cutler demosaic,
// camera → linear Rec.709 matrix and exposure. Output: scene-linear RGB.
precision highp float;
precision highp int;
precision highp usampler2D;

uniform usampler2D uRaw;
uniform sampler2D uShading;
uniform ivec2 uRedPos;
uniform vec4 uBlack;        // 2×2 tile, row-major
uniform float uWhite;
uniform vec3 uWbGains;
uniform mat3 uCameraToWorking;
uniform float uExposure;
uniform bool uHasShading;

out vec4 outColor;

ivec2 rawSize;

int channelAt(ivec2 p) {
    int dx = (p.x ^ uRedPos.x) & 1;
    int dy = (p.y ^ uRedPos.y) & 1;
    if (dx == 0 && dy == 0) return 0;
    if (dx == 1 && dy == 1) return 2;
    return 1;
}

float shadingGain(ivec2 p, int channel) {
    if (!uHasShading) return 1.0;
    vec4 g = texture(uShading, (vec2(p) + 0.5) / vec2(rawSize));
    if (channel == 0) return g.r;
    if (channel == 2) return g.a;
    return ((p.y & 1) == 0) ? g.g : g.b;
}

float sampleRaw(ivec2 p) {
    p = clamp(p, ivec2(0), rawSize - 1);
    int c = channelAt(p);
    float black = uBlack[(p.y & 1) * 2 + (p.x & 1)];
    float v = (float(texelFetch(uRaw, p, 0).r) - black) / (uWhite - black);
    v = max(v, 0.0) * shadingGain(p, c);
    float gain = c == 0 ? uWbGains.r : (c == 1 ? uWbGains.g : uWbGains.b);
    return min(v * gain, 1.0);
}

float f(ivec2 p, int dx, int dy) { return sampleRaw(p + ivec2(dx, dy)); }

void main() {
    rawSize = textureSize(uRaw, 0);
    ivec2 p = ivec2(gl_FragCoord.xy);
    int c = channelAt(p);
    float center = f(p, 0, 0);

    float cross1 = f(p, -1, 0) + f(p, 1, 0) + f(p, 0, -1) + f(p, 0, 1);
    float cross2 = f(p, -2, 0) + f(p, 2, 0) + f(p, 0, -2) + f(p, 0, 2);
    float diag = f(p, -1, -1) + f(p, 1, -1) + f(p, -1, 1) + f(p, 1, 1);
    float horiz1 = f(p, -1, 0) + f(p, 1, 0);
    float vert1 = f(p, 0, -1) + f(p, 0, 1);
    float horiz2 = f(p, -2, 0) + f(p, 2, 0);
    float vert2 = f(p, 0, -2) + f(p, 0, 2);

    float greenAtRb = (4.0 * center + 2.0 * cross1 - cross2) / 8.0;
    float oppositeAtRb = (6.0 * center + 2.0 * diag - 1.5 * cross2) / 8.0;
    float rowNeighbour = (5.0 * center + 4.0 * horiz1 - horiz2 - diag + 0.5 * vert2) / 8.0;
    float colNeighbour = (5.0 * center + 4.0 * vert1 - vert2 - diag + 0.5 * horiz2) / 8.0;

    vec3 rgb;
    if (c == 0) {
        rgb = vec3(center, greenAtRb, oppositeAtRb);
    } else if (c == 2) {
        rgb = vec3(oppositeAtRb, greenAtRb, center);
    } else {
        bool redRow = ((p.y ^ uRedPos.y) & 1) == 0;
        rgb = redRow ? vec3(rowNeighbour, center, colNeighbour) : vec3(colNeighbour, center, rowNeighbour);
    }
    rgb = max(rgb, 0.0);
    outColor = vec4(uCameraToWorking * rgb * uExposure, 1.0);
}
