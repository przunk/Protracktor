// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * A `std::char_traits<uint16_t>`, force-included into ZXTune's `encoding.cpp` and nowhere else.
 *
 * That file defines `Utf16ToUtf8(std::basic_string_view<uint16_t>)`. libstdc++ still provides a
 * generic `char_traits` primary template that makes such an instantiation work; **libc++, which the
 * NDK uses, does not** -- it declares `char_traits` only for the character types the standard names.
 * So the host probe compiled the file and the first Android build did not, and the error arrives as
 * "implicit instantiation of undefined template 'std::char_traits<unsigned short>'".
 *
 * The file cannot simply be dropped: `strings/src/sanitize.cpp` calls `ToAutoUtf8` from the same
 * translation unit's sibling, and sanitising is what cleans a module's title before the app shows
 * it. Leaving it out linked to nothing.
 *
 * **This is a shim, and it is worth being plain about what it costs.** Specialising a standard
 * template for a type the standard does not name is not something the standard permits, however
 * routinely it is done. The alternative is editing a tree that `fetch-zxtune.py` re-downloads,
 * which this project does not do to vendored sources -- a patch that lives outside the fetch is a
 * patch that silently stops being applied. Confining it to one translation unit, through
 * `-include` in the CMake, keeps it away from everything else.
 */
#pragma once

#include <cstdint>
#include <cstring>
#include <string>

namespace std
{
  template<>
  struct char_traits<uint16_t>
  {
    using char_type = uint16_t;
    using int_type = uint_least32_t;
    using off_type = streamoff;
    using pos_type = streampos;
    using state_type = mbstate_t;

    static constexpr void assign(char_type& a, const char_type& b) noexcept { a = b; }
    static constexpr bool eq(char_type a, char_type b) noexcept { return a == b; }
    static constexpr bool lt(char_type a, char_type b) noexcept { return a < b; }

    static constexpr int compare(const char_type* a, const char_type* b, size_t n) noexcept
    {
      for (size_t i = 0; i < n; ++i)
      {
        if (a[i] < b[i]) return -1;
        if (b[i] < a[i]) return 1;
      }
      return 0;
    }

    static constexpr size_t length(const char_type* s) noexcept
    {
      size_t n = 0;
      while (s[n]) ++n;
      return n;
    }

    static constexpr const char_type* find(const char_type* s, size_t n, const char_type& c) noexcept
    {
      for (size_t i = 0; i < n; ++i)
      {
        if (s[i] == c) return s + i;
      }
      return nullptr;
    }

    static char_type* move(char_type* to, const char_type* from, size_t n) noexcept
    {
      return n ? static_cast<char_type*>(memmove(to, from, n * sizeof(char_type))) : to;
    }

    static char_type* copy(char_type* to, const char_type* from, size_t n) noexcept
    {
      return n ? static_cast<char_type*>(memcpy(to, from, n * sizeof(char_type))) : to;
    }

    static char_type* assign(char_type* s, size_t n, char_type c) noexcept
    {
      for (size_t i = 0; i < n; ++i)
      {
        s[i] = c;
      }
      return s;
    }

    static constexpr int_type not_eof(int_type c) noexcept { return eq_int_type(c, eof()) ? 0 : c; }
    static constexpr char_type to_char_type(int_type c) noexcept { return static_cast<char_type>(c); }
    static constexpr int_type to_int_type(char_type c) noexcept { return static_cast<int_type>(c); }
    static constexpr bool eq_int_type(int_type a, int_type b) noexcept { return a == b; }
    static constexpr int_type eof() noexcept { return static_cast<int_type>(-1); }
  };
}  // namespace std
