void f(std::map<int, int>& m, Point p) {
    auto [x, y] = p;
    for (const auto& [key, value] : m) {
        use(key, value);
    }
}
