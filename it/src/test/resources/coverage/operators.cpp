#include <concepts>
#include <functional>
#include <list>
#include <memory>
#include <span>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

namespace geom {

struct Vec {
    double x = 0;
    double y = 0;

    Vec operator-() const { return Vec{-x, -y}; }
    Vec& operator+=(const Vec& other) {
        x += other.x;
        y += other.y;
        return *this;
    }
    double length() const { return x * x + y * y; }
};

Vec operator+(const Vec& a, const Vec& b) { return Vec{a.x + b.x, a.y + b.y}; }
Vec operator*(const Vec& a, double k) { return Vec{a.x * k, a.y * k}; }

class Grid {
public:
    struct Cell {
        int value = 0;
        bool visited = false;
    };

    using Row = std::vector<Cell>;

    static constexpr int kSize = 4;
    static Grid make() { return Grid{}; }

    Grid() : rows_(kSize, Row(kSize)) {}
    Row& operator[](std::size_t i) { return rows_[i]; }
    const Cell& at(int r, int c) const { return rows_[r][c]; }

private:
    std::vector<Row> rows_;
};

template <typename T>
concept HasLength = requires(const T& t) { t.length(); };

template <HasLength T>
double measure(const T& value) { return value.length(); }

} // namespace geom

struct Pair {
    int first;
    std::string second;
};

int main() {
    geom::Vec a{1, 2};
    geom::Vec b{3, 4};
    double total = (a + b).length() + (a * 2.0).x + (-a).y;
    a += b;
    (void) (a += b).length();

    std::vector<std::vector<geom::Vec>> rows(2, std::vector<geom::Vec>(2));
    rows[0][1].x = 1;

    std::list<geom::Vec> path{a, b};
    std::list<geom::Vec>::iterator first = path.begin();
    (void) (*first).y;
    (void) first->x;

    std::unordered_map<int, geom::Vec> byId{{1, a}};
    if (auto it = byId.find(1); it != byId.end()) {
        (void) it->second.length();
    }

    geom::Grid grid = geom::Grid::make();
    grid[0][0].value = geom::Grid::kSize;
    geom::Grid::Cell cell = grid.at(1, 1);
    (void) cell.visited;
    geom::Grid::Row& row = grid[1];
    (void) row.front().value;

    Pair pair{1, "one"};
    auto [number, name] = pair;
    (void) name.size();

    auto make = [](double k) { return geom::Vec{k, k}; };
    (void) make(2.0).length();
    std::function<geom::Vec(double)> wrapped = make;
    (void) wrapped(3.0).x;

    auto shared = std::make_shared<geom::Vec>(geom::Vec{5, 6});
    geom::Vec moved = std::move(*shared);
    (void) moved.y;

    std::vector<geom::Vec> values{a, b};
    std::span<geom::Vec> view(values);
    (void) view[0].x;
    (void) view.front().length();

    return static_cast<int>(total + geom::measure(a)) + number;
}
