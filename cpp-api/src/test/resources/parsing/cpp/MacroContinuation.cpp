struct uint3 { unsigned x; };
#if !defined(__STORAGE__)

#if defined(__CUDACC_RTC__)
#define __STORAGE__ \
        extern const __device__
#else /* !__CUDACC_RTC__ */
#define __STORAGE__ \
        extern const
#endif /* __CUDACC_RTC__ */

#endif /* __STORAGE__ */

uint3 __STORAGE__ threadIdx;

#undef __STORAGE__
