void normalize(int n)
{
#if defined(WINDOWS)
    for (int i = 0; i < n; ++i)
    {
        if (i == 1)
            mark(i);
    }
#endif
    done();
}

#if defined(RETAIN)
struct retainer
{
    int value;
};
#endif
