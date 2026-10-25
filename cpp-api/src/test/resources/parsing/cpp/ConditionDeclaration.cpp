void conditions(int a, int b)
{
    if (int x = next())
        use(x);
    if (const ::testing::AssertionResult result = (compare(a, b)))
        ;
    if (TrueWithString message{})
        use(message);
    while (Node* node = take())
        use(node);
    if (auto it = find(a); it != end())
        use(it);
    switch (int c = read(); c)
    {
        case 0:
            break;
    }
    if (a * b)
        use(a);
    if (a < b)
        use(b);
    do
        use(a);
    while (a < b);
}
