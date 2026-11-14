#pragma once

#include <memory>
#include <string>
#include <vector>

namespace geo {

class Canvas;

enum class Unit { Pixel, Point };

struct Bounds {
    double left = 0;
    double top = 0;
    double width = 0;
    double height = 0;

    bool contains(double x, double y) const;
};

class Figure {
public:
    explicit Figure(std::string name);
    virtual ~Figure();

    virtual Bounds bounds() const = 0;
    virtual void draw(Canvas& canvas) const;

    const std::string& name() const { return name_; }
    static int count();

protected:
    std::string name_;

private:
    static int count_;
};

class Rectangle : public Figure {
public:
    Rectangle(double width, double height);
    Bounds bounds() const override;

private:
    double width_;
    double height_;
};

class Canvas {
public:
    void add(std::unique_ptr<Figure> figure);
    void render() const;
    std::size_t size() const;
    Unit unit = Unit::Pixel;

private:
    std::vector<std::unique_ptr<Figure>> figures_;
};

} // namespace geo
