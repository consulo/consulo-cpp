void build()
{
    const FVector2f Half(BallSize * 0.5f, BallSize * 0.5f);
    const FVector2f Min(PaddleX - PaddleWidth * 0.5f, PaddleY);
    const FVector2f Corner(Left + Column * (BrickWidth + BrickGap), BricksTop + Row * (BrickHeight + BrickGap));
    FVector2f Scaled(Size & 0xFF, Offset && true);
    FVector2f Negative(Size * -1, Size * Factor.X);
    Foo pointer(Bar * baz);
    Foo reference(Bar & baz, const Qux * quux);
    Foo pointers(Bar **, Qux * const);
}

void loops()
{
    for (FBrick& Brick : Bricks)
    {
        Brick.Hit();
    }
    for (const FBrick* Brick : Pointers)
    {
    }
    FBrick* Last{nullptr};
}
