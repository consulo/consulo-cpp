namespace geo
{
    struct Point { int x; int y; Point offset(int by) const; };
    Point origin();
    int distance(Point a, Point b);
}

using namespace geo;
using geo::distance;
using Location = geo::Point;

template <typename T, int Size>
struct Buffer
{
    T items[Size];
    T first() const { return items[0]; }
};

template <class Value>
Value twice(Value value)
{
    return value + value;
}

Location make()
{
    return origin();
}

int use(Point* points, Buffer<int, 4> buffer)
{
    auto start = make();
    auto& last = points[1];
    int sum = start.x + last.y + make().x + (*points).y + origin().offset(2).x;
    auto add = [&](int step) { return step + sum; };
    try
    {
        sum += distance(start, last);
    }
    catch (const Point& thrown)
    {
        sum += thrown.x;
    }
    for (auto& point : points)
    {
        sum += point.x;
    }
    return twice(sum) + add(1) + buffer.first();
}
