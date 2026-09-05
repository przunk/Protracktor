/* The Amiga types the replayer wants, given the widths the Amiga actually had.
 *
 * Upstream `hvl2wav/types.h` says `typedef unsigned long uint32`. On the Amiga that is 32 bits; on
 * arm64, x86_64 and this host it is 64. **The app also ships armeabi-v7a**, where it is 32 again --
 * so the two widths are not hypothetical, they are two of the three ABIs in `abiFilters`, and a
 * replayer that behaves differently between them would be broken on exactly the older phones
 * nobody here tests on.
 *
 * Nothing here is a fix applied to upstream. It is a *second* set of typedefs, used to build the
 * probe a second time, so that "the same files give the same verdicts either way" is a measurement.
 *
 * It is forced in with `-include` rather than an include path: `replay.c` says `#include "types.h"`,
 * and the quoted form searches the including file's own directory first, so an `-I` never reaches
 * it. With `-include` this file is read before anything else and the guard below makes upstream's
 * copy a no-op -- in every translation unit, which is the part that matters. Half of them taking
 * one set of typedefs and half the other is not a portability test, it is a layout mismatch.
 */
#ifndef EXEC_TYPES_H
#define EXEC_TYPES_H

#include <stdint.h>

typedef uint16_t  uint16;
typedef uint8_t   uint8;
typedef int16_t   int16;
typedef int8_t    int8;
typedef uint32_t  uint32;
typedef int32_t   int32;

typedef double    float64;
typedef char      TEXT;
typedef short     BOOL;
typedef int32_t   LONG;
typedef uint32_t  ULONG;
#define FALSE 0
#define TRUE 1
#define CONST const

#endif
