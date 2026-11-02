namespace calc { int add(int a, int b); }
bool check(bool value);
#define EXPECT_EQ(a, b) check((a) == (b))
#define DECLARE(type, name) type name = 0

void test()
{
    int local = 1;
    EXPECT_EQ(calc::add(local, 2), 3);
    DECLARE(int, counter);
    EXPECT_EQ(counter, local);
}
