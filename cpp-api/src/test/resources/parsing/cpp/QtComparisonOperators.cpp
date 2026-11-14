struct QLatin1Char {
    friend constexpr bool operator==(QLatin1Char const &lhs, QLatin1Char const &rhs) noexcept(true) { constexpr auto f = []() noexcept(true) {}; static_assert(!noexcept(f()) || noexcept(comparesEqual(lhs, rhs)), "Use *_NON_NOEXCEPT version of the macro, " "or make the helper function noexcept"); return comparesEqual(lhs, rhs); } friend constexpr std::strong_ordering operator<=>(QLatin1Char const &lhs, QLatin1Char const &rhs) noexcept(true) { return compareThreeWay(lhs, rhs); }
    friend constexpr bool comparesEqual(const QLatin1Char &lhs, char rhs) noexcept { return true; }
};
