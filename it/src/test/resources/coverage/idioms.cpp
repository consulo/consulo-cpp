#include <algorithm>
#include <array>
#include <iomanip>
#include <iostream>
#include <limits>
#include <map>
#include <memory>
#include <numeric>
#include <optional>
#include <string>
#include <tuple>
#include <vector>

using namespace std;

struct Point {
    int x = 0;
    int y = 0;
};

class Shape {
public:
    enum class Kind { Circle, Square };

    explicit Shape(Kind kind) : kind_(kind) {}
    virtual ~Shape() = default;
    virtual double area() const = 0;
    Kind kind() const { return kind_; }

private:
    Kind kind_;
};

class Square : public Shape {
public:
    explicit Square(double side) : Shape(Kind::Square), side_(side) {}
    double area() const override { return side_ * side_; }
    double side() const { return side_; }

private:
    double side_;
};

template <typename T, typename Compare = less<T>>
class SortedList {
public:
    void insert(const T& value);
    const T& front() const { return items_.front(); }
    size_t size() const { return items_.size(); }

private:
    vector<T> items_;
    Compare compare_;
};

template <typename T, typename Compare>
void SortedList<T, Compare>::insert(const T& value) {
    auto at = lower_bound(items_.begin(), items_.end(), value, compare_);
    items_.insert(at, value);
}

static pair<int, string> lookup(int key) { return make_pair(key, to_string(key)); }

int main() {
    map<string, Point> named{{"origin", {0, 0}}, {"unit", {1, 1}}};
    for (auto& entry : named) {
        entry.second.x += entry.first.size();  //> x -> Point
    }
    named["far"].y = 100;

    array<Point, 3> corners{};
    corners[1].x = 5;  //> x -> Point
    corners.at(2).y = 6;

    vector<Point> points{{3, 4}, {1, 2}};
    auto found = find_if(points.begin(), points.end(), [](const Point& p) { return p.x > 2; });
    if (found != points.end()) {
        found->y = 0;  //> y -> Point
    }
    int total = accumulate(points.begin(), points.end(), 0, [](int sum, const Point& p) { return sum + p.x; });

    auto [key, text] = lookup(7);
    (void) text.size();
    int a = 0;
    string b;
    tie(a, b) = lookup(key);
    (void) b.empty();

    unique_ptr<Shape> shape = make_unique<Square>(2.0);
    if (shape->kind() == Shape::Kind::Square) {
        auto* square = static_cast<Square*>(shape.get());
        (void) square->side();  //> side -> Square
    }
    decltype(points) copy = points;
    (void) copy.back().x;

    SortedList<int> sorted;
    sorted.insert(3);
    (void) sorted.front();

    optional<Point> maybe;
    (void) maybe.value_or(Point{}).x;  //> x -> Point

    cout << setw(4) << numeric_limits<int>::max() << ' ' << string::npos << ' ' << total << endl;
    return static_cast<int>(sorted.size()) + a;
}
