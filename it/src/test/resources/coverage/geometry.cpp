#include "geometry.h"

#include <iostream>

namespace geo {

int Figure::count_ = 0;

bool Bounds::contains(double x, double y) const {
    return x >= left && x <= left + width && y >= top && y <= top + height;  //> left -> Bounds@geometry.h
}

Figure::Figure(std::string name) : name_(std::move(name)) {
    ++count_;  //> count_ -> Figure@geometry.h
}

Figure::~Figure() {
    --count_;
}

void Figure::draw(Canvas& canvas) const {
    std::cout << name_ << " in a canvas of " << canvas.size() << '\n';  //> size -> Canvas@geometry.h
}

int Figure::count() {
    return count_;
}

Rectangle::Rectangle(double width, double height) : Figure("rectangle"), width_(width), height_(height) {}

Bounds Rectangle::bounds() const {
    return Bounds{0, 0, width_, height_};
}

void Canvas::add(std::unique_ptr<Figure> figure) {
    figures_.push_back(std::move(figure));
}

void Canvas::render() const {
    for (const auto& figure : figures_) {
        figure->draw(const_cast<Canvas&>(*this));  //> draw -> Figure@geometry.h
        (void) figure->bounds().contains(1, 1);  //> contains -> Bounds@geometry.h
    }
}

std::size_t Canvas::size() const {
    return figures_.size();  //> size -> vector
}

} // namespace geo
