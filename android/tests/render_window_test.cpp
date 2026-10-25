#include "../app/src/main/cpp/render_window.h"
#include <cassert>
#include <cmath>
#include <cstdio>
int main() {
    RenderWindow w;
    assert(RenderWindow::percent(100,0)==0);
    for(int i=0;i<749;++i) assert(!w.add(2000000,1000000,128));
    assert(w.add(4000000,1000000,128));
    assert(w.frames==96000 && w.maxNs==4000000);
    assert(std::abs(RenderWindow::percent(w.wallNs,w.frames)-75.1)<0.001);
    assert(std::abs(RenderWindow::percent(w.cpuNs,w.frames)-37.5)<0.001);
    w.clear(); assert(w.frames==0 && w.wallNs==0 && w.cpuNs==0 && w.maxNs==0);
    assert(!w.add(1000000,500000,128));
    assert(std::abs(RenderWindow::percent(w.wallNs,w.frames)-37.5)<0.001);
    std::puts("PASS: render-window mean, CPU time, peak, 2 s window and reset");
}
