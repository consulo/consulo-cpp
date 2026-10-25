#define LIST_TESTS(T, FUNC) static_assert(FUNC(T(1), T(2)) == T(2));

LIST_TESTS(double, __builtin_fmax)
LIST_TESTS(float, __builtin_fmaxf)

void g()
{
    f(nullptr, __builtin_bit_cast(std::nullptr_t, static_cast<void*>(&i)));
}
