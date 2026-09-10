/*
 * Hand-written stand-in for the sidversion.h that configure would generate.
 *
 * It carries the library version and nothing else. Kept beside the other two stand-ins so a version
 * bump has one place to look.
 */
#ifndef LIBSIDPLAYFP_VERSION_H
#define LIBSIDPLAYFP_VERSION_H

#ifndef SIDPLAYFP_H
#  error Do not include directly.
#endif

#define LIBSIDPLAYFP_VERSION_MAJ 3
#define LIBSIDPLAYFP_VERSION_MIN 1
#define LIBSIDPLAYFP_VERSION_LEV 1

#endif
