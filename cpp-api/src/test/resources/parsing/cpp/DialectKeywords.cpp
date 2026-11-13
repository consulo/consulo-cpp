struct Uniforms { float scale; };
kernel void scale(device const float* a [[buffer(0)]], constant Uniforms& u [[buffer(1)]],
                  device float* c [[buffer(2)]], uint id [[thread_position_in_grid]])
{
    threadgroup float tile[64];
    tile[id % 64] = a[id];
    c[id] = tile[id % 64] * u.scale;
}
vertex float4 position(uint vid [[vertex_id]], const device float4* device vertices [[buffer(0)]]);
struct after { int x; };
extern "C" {
int y;
}
