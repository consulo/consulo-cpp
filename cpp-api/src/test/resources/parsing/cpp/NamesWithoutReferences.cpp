namespace a { namespace b { struct X { int v; }; } }
namespace alias = a::b;
using alias::X;

struct Base { Base(int) {} };
struct Derived : Base {
    int field;
    Derived(int value) : Base(value), field(value) {}
};

void f(int top) {
    auto g = [top, &top2 = top](int n) { return n < top; };
    (void) typeid(Derived).name();
}
