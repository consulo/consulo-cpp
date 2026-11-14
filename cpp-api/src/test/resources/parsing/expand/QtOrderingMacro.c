#define QT_VA_ARGS_CHOOSE(_1, _2, _3, _4, _5, _6, _7, _8, _9, N, ...) N
#define QT_VA_ARGS_EXPAND(...) __VA_ARGS__ // Needed for MSVC
#define QT_VA_ARGS_COUNT(...) QT_VA_ARGS_EXPAND(QT_VA_ARGS_CHOOSE(__VA_ARGS__, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0))
#define QT_OVERLOADED_MACRO_EXPAND(MACRO, ARGC) MACRO##_##ARGC
#define QT_OVERLOADED_MACRO_IMP(MACRO, ARGC) QT_OVERLOADED_MACRO_EXPAND(MACRO, ARGC)
#define QT_OVERLOADED_MACRO(MACRO, ...) QT_VA_ARGS_EXPAND(QT_OVERLOADED_MACRO_IMP(MACRO, QT_VA_ARGS_COUNT(__VA_ARGS__))(__VA_ARGS__))
#define QT_DECLARE_EQUALITY_OPERATORS_HELPER(LeftType, RightType, Constexpr, Noexcept, ...) \
    __VA_ARGS__ \
    Constexpr int comparesEqual(LeftType const *lhs, RightType const *rhs) Noexcept;
#define QT_DECLARE_ORDERING_HELPER_STRONG(LeftType, RightType, Constexpr, Noexcept, ...) \
    __VA_ARGS__ \
    Constexpr int compareThreeWay(LeftType const *lhs, RightType const *rhs) Noexcept;
#define QT_DECLARE_ORDERING_OPERATORS_HELPER(OrderingType, LeftType, RightType, Constexpr, \
                                             Noexcept, ...) \
    QT_DECLARE_EQUALITY_OPERATORS_HELPER(LeftType, RightType, Constexpr, Noexcept, __VA_ARGS__) \
    QT_DECLARE_ORDERING_HELPER_ ## OrderingType (LeftType, RightType, Constexpr, Noexcept, \
                                                 __VA_ARGS__)
#define QT_DECLARE_STRONGLY_ORDERED_LITERAL_TYPE_1(Type) \
    QT_DECLARE_ORDERING_OPERATORS_HELPER(STRONG, Type, Type, static, /* noexcept */, \
                                         /* no attributes */)
#define Q_DECLARE_STRONGLY_ORDERED_LITERAL_TYPE(...) \
    QT_OVERLOADED_MACRO(QT_DECLARE_STRONGLY_ORDERED_LITERAL_TYPE, __VA_ARGS__)
struct latin {
    char ch;
};
Q_DECLARE_STRONGLY_ORDERED_LITERAL_TYPE(struct latin)
int after;
