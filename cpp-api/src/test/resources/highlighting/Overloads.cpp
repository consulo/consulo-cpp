namespace geo
{
    struct Point { int x; int y; };
    struct Point3 : Point { int z; };
    int distance(Point a, Point b);
}

struct Box { int width; };
template <typename T> struct Holder { T item; T get() const; };

void pick(int value);
void pick(double value);
void pick(const char* text);
void pick(geo::Point* point);
void pick(bool flag, int count = 1, ...);
template <typename T> void pick(T first, T second, T third);

int area(geo::Point point);
int scale(int factor, int by = 2);
struct stat { int size; };
int stat(const char* path, struct stat* out);

int use(geo::Point point, geo::Point3 point3, Holder<geo::Point> holder, Box box, short small, float real)
{
    pick(1);
    pick(1.5);
    pick(real);
    pick(small);
    pick("text");
    pick(&point3);
    pick(nullptr);
    pick(true, 2, 3, 4);
    pick(1, 2, 3);
    area(point3);
    distance(point, point);
    scale(1);
    scale(1, 2, 3);
    area(box);
    area(1);
    struct stat info;
    stat("path", &info);
    return holder.item.x + holder.get().y + info.size;
}

// a class with operators to a pointer: only by a user-defined conversion, so the exact overload wins
struct Text { Text(const char* chars); bool operator==(const Text& other) const; };
int show(const char* chars);
int show(const Text& text);
int showText(Text text)
{
    return show(text);
}
