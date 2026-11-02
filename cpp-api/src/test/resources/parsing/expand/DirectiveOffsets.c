/* a header */

#ifndef GUARD_H
#define GUARD_H

int a;

#if defined(X)
  extern int b;
  #define HAVE_B
#endif
int c;
#endif
