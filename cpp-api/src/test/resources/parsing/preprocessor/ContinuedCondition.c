#if (!defined(_STDDEF_H) && !defined(_STDDEF_H_) && !defined(_ANSI_STDDEF_H) \
     && !defined(__STDDEF_H__)) \
    || defined(__need_wchar_t) || defined(__need_size_t) \
    || defined(__need_ptrdiff_t) || defined(__need_NULL) \
    || defined(__need_wint_t)
typedef unsigned long size_t;
#endif
