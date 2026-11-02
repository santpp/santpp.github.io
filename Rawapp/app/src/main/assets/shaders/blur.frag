#version 300 es
// Separable Gaussian blur, 13 taps along uDirection.
precision highp float;

uniform sampler2D uSource;
uniform vec2 uDirection;

out vec4 outColor;

const float WEIGHTS[7] = float[](0.1596, 0.1473, 0.1159, 0.0776, 0.0443, 0.0215, 0.0089);

void main() {
    vec2 size = vec2(textureSize(uSource, 0));
    vec2 uv = gl_FragCoord.xy / size;
    vec2 stepUv = uDirection * 2.0 / size;
    vec3 sum = texture(uSource, uv).rgb * WEIGHTS[0];
    for (int i = 1; i < 7; i++) {
        sum += texture(uSource, uv + stepUv * float(i)).rgb * WEIGHTS[i];
        sum += texture(uSource, uv - stepUv * float(i)).rgb * WEIGHTS[i];
    }
    outColor = vec4(sum, 1.0);
}
