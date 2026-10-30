attribute vec4 aPosition;
attribute vec4 aTexCoord;
uniform mat4 uSTMatrix;
varying vec2 vTexCoord;
void main() {
    gl_Position = aPosition;
    vTexCoord = (uSTMatrix * aTexCoord).xy;
}
