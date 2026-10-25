int separator(int size, int c)
{
    if (size > 0 &&
#ifdef WINDOWS
        c != 1 &&
#endif
        c != 2)
        return 1;
    return 0;
}
