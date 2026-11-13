void launch(int n, float* a, int s)
{
    add<<<(n + 255) / 256, 256>>>(a, n);
    add<<<n >> 8, 256, 0, s>>>(a, n);
    fill<<<1, 1>>>();
    scale<float><<<n, 128>>>(a);
    int shifted = n << 2 >> 1;
}
