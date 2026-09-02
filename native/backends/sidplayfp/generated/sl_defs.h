/*
 * Hand-written stand-in for sidlite's generated sl_defs.h.
 *
 * Upstream generates it from sl_defs.h.in to record one thing: whether the compiler has
 * __builtin_expect. Every compiler the NDK ships does.
 */
#ifndef SIDLITE_DEFS_H
#define SIDLITE_DEFS_H

#define HAVE_BUILTIN_EXPECT 1

#if HAVE_BUILTIN_EXPECT
#  define LIKELY(x)      __builtin_expect(!!(x), 1)
#  define UNLIKELY(x)    __builtin_expect(!!(x), 0)
#else
#  define LIKELY(x)      (x)
#  define UNLIKELY(x)    (x)
#endif

#endif
