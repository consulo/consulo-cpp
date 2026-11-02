namespace geo
{
    struct Point
    {
        int x;
        int y;
        static int count;
        int length() const { return x + y; }
    };
}

namespace geo
{
    class Shape
    {
    public:
        Point origin;
        int area() const;
        class Inner { public: int depth; };
    private:
        int sides;
    };

    typedef Shape ShapeAlias;
}

class Square : public geo::Shape
{
public:
    int side;
    int perimeter() const;
};

int geo::Shape::area() const
{
    Point corner = origin;
    return sides * corner.x + this->sides;
}

int Square::perimeter() const
{
    return side * sides + origin.y;
}

int use(geo::ShapeAlias* shape, geo::Shape::Inner inner)
{
    Square square;
    square.side = shape->area();
    return geo::Point::count + inner.depth + square.origin.x;
}
