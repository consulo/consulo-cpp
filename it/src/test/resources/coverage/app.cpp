#include "geometry.h"

using geo::Canvas;

int main() {
    Canvas canvas;
    canvas.add(std::make_unique<geo::Rectangle>(2, 3));  //> add -> Canvas@geometry.h
    canvas.render();  //> render -> Canvas@geometry.h
    if (canvas.unit == geo::Unit::Pixel) {
        geo::Rectangle rect(4, 5);
        geo::Bounds box = rect.bounds();  //> bounds -> Rectangle@geometry.h
        (void) box.width;
        (void) rect.name().size();  //> name -> Figure@geometry.h
    }
    return geo::Figure::count() + static_cast<int>(canvas.size());  //> count -> Figure@geometry.h
}
