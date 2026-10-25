typedef void (*init_func_ptr_t)();

#if defined(_MSC_VER)

#if _MSC_VER >= 1400

#pragma section(".CRT$XCM", long, read)
__declspec(allocate(".CRT$XCM")) RETAIN_ATTRIBUTE
extern const init_func_ptr_t p_init_globals = &init_globals;

#else // _MSC_VER >= 1400

#if (_MSC_VER >= 1300)
#pragma data_seg(push, old_seg)
#endif
#pragma data_seg(".CRT$XCM")
extern const init_func_ptr_t p_init_globals = &init_globals;
#pragma data_seg()

#endif // _MSC_VER >= 1400

#if defined(NO_ATTRIBUTE_RETAIN)
struct globals_retainer
{
    const init_func_ptr_t* volatile m_p_init_globals;

    globals_retainer() { m_p_init_globals = &p_init_globals; }
};
UNUSED_ATTRIBUTE
static const globals_retainer g_globals_retainer;
#endif // defined(NO_ATTRIBUTE_RETAIN)

#else // defined(_MSC_VER)

struct deleter
{
    ~deleter() { cleanup(); }
};

#endif // defined(_MSC_VER)

struct globals_retainer_active
{
    const init_func_ptr_t* volatile m_p_init_globals;

    globals_retainer_active() { m_p_init_globals = nullptr; }
};
UNUSED_ATTRIBUTE
static const globals_retainer_active g_active_retainer;
