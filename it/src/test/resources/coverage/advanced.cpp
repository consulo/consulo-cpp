#include <algorithm>
#include <array>
#include <functional>
#include <map>
#include <memory>
#include <set>
#include <sstream>
#include <string>
#include <tuple>
#include <utility>
#include <vector>

namespace geo {

enum class Color { Red, Green, Blue };

struct Vec {
    double x = 0;
    double y = 0;

    Vec operator+(const Vec& other) const { return Vec{x + other.x, y + other.y}; }
    double dot(const Vec& other) const { return x * other.x + y * other.y; }
};

class Node {
public:
    struct Edge {
        int target;
        double weight;
    };

    explicit Node(std::string label) : label_(std::move(label)) {}

    void connect(int target, double weight) { edges_.push_back(Edge{target, weight}); }
    const std::vector<Edge>& edges() const { return edges_; }
    const std::string& label() const { return label_; }

private:
    std::string label_;
    std::vector<Edge> edges_;
};

template <typename T>
class Stack {
public:
    void push(const T& value) { items_.push_back(value); }
    T& top() { return items_.back(); }
    bool empty() const { return items_.empty(); }
    std::size_t size() const { return items_.size(); }

private:
    std::vector<T> items_;
};

using Graph = std::vector<Node>;

} // namespace geo

static std::string describe(geo::Color color) {
    switch (color) {
        case geo::Color::Red: return "red";
        case geo::Color::Green: return "green";
        case geo::Color::Blue: return "blue";
    }
    return "?";
}

int main() {
    geo::Graph graph;
    graph.emplace_back("a");
    graph.emplace_back("b");
    graph[0].connect(1, 2.5);
    graph.front().connect(0, 1.0);

    for (const geo::Node& node : graph) {
        for (const auto& edge : node.edges()) {
            std::ostringstream out;
            out << node.label() << " -> " << edge.target << " (" << edge.weight << ")";  //> label -> Node
        }
    }

    std::map<std::string, int> ages{{"ann", 31}, {"bob", 42}};
    for (const auto& [name, age] : ages) {
        (void) name.size();
        (void) age;
    }
    auto it = ages.find("ann");
    if (it != ages.end()) {
        it->second += 1;  //> second -> pair
        (void) it->first.length();
    }

    std::pair<int, std::string> pair{1, "one"};
    (void) pair.first;
    (void) pair.second.empty();  //> empty -> basic_string
    auto tuple = std::make_tuple(1, 2.0, std::string("three"));
    (void) std::get<0>(tuple);

    std::shared_ptr<geo::Node> shared = std::make_shared<geo::Node>("shared");
    shared->connect(0, 0.5);
    std::weak_ptr<geo::Node> weak = shared;
    if (auto locked = weak.lock()) {
        (void) locked->label();  //> label -> Node
    }

    geo::Stack<geo::Vec> stack;
    stack.push(geo::Vec{1, 2});
    double length = stack.top().dot(stack.top());  //> dot -> Vec
    geo::Vec sum = stack.top() + geo::Vec{3, 4};
    (void) sum.x;

    std::function<int(int)> twice = [](int value) { return value * 2; };
    std::array<int, 3> values{1, 2, 3};
    std::transform(values.begin(), values.end(), values.begin(), twice);
    std::set<int> unique(values.begin(), values.end());
    (void) unique.count(2);

    std::vector<geo::Vec> points{{1, 1}, {2, 2}};
    std::sort(points.begin(), points.end(), [](const geo::Vec& a, const geo::Vec& b) { return a.x < b.x; });
    points.back().y = length;

    return describe(geo::Color::Green).empty() ? 1 : 0;
}
