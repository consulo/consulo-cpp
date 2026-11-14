template <typename T>
concept HasLength = requires(const T& t) { t.length(); };

template <HasLength T>
double measure(const T& value) { return value.length(); }
