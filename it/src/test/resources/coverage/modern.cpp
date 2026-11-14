#include <vector>
#include <string>
#include <map>
#include <memory>
#include <algorithm>
#include <iostream>
#include <optional>
#include <unordered_map>

namespace app {

struct Point {
    int x = 0;
    int y = 0;
    int length() const { return x * x + y * y; }
};

class Shape {
public:
    virtual ~Shape() = default;
    virtual double area() const = 0;
    const std::string& name() const { return myName; }
protected:
    explicit Shape(std::string name) : myName(std::move(name)) {}
private:
    std::string myName;
};

class Circle : public Shape {
public:
    explicit Circle(double radius) : Shape("circle"), myRadius(radius) {}
    double area() const override { return 3.14159 * myRadius * myRadius; }
private:
    double myRadius;
};

template <typename T>
T largest(const std::vector<T>& values) {
    return *std::max_element(values.begin(), values.end());
}

std::optional<Point> find(const std::map<std::string, Point>& points, const std::string& key) {
    auto it = points.find(key);
    if (it == points.end()) {
        return std::nullopt;
    }
    return it->second;
}

}

int main() {
    std::vector<int> numbers = {3, 1, 4, 1, 5};
    std::sort(numbers.begin(), numbers.end());
    numbers.push_back(9);
    int top = app::largest(numbers);

    std::map<std::string, app::Point> points;
    points["origin"] = app::Point{0, 0};
    points.emplace("unit", app::Point{1, 1});
    std::optional<app::Point> found = app::find(points, "unit");
    if (found.has_value()) {
        std::cout << found->x << " " << found.value().length() << std::endl;
    }

    std::vector<std::unique_ptr<app::Shape>> shapes;
    shapes.push_back(std::make_unique<app::Circle>(2.0));
    for (const auto& shape : shapes) {
        std::cout << shape->name() << ": " << shape->area() << '\n';
    }

    std::unordered_map<std::string, int> counts;
    for (const std::string word : {"a", "b", "a"}) {
        counts[word]++;
    }
    auto total = std::count_if(numbers.begin(), numbers.end(), [top](int n) { return n < top; });
    std::string text = std::to_string(total);
    return static_cast<int>(text.size() + counts.size());
}
