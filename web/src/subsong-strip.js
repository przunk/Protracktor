// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Native SubsongStrip follows only while the previous chip was visible, except at opening. */
export function followSubsong({ opening, previousVisible, currentVisible }) {
  return opening || previousVisible && !currentVisible;
}

export function selectSubsong(index, current, count) {
  return Number.isInteger(index) && index >= 0 && index < count && index !== current;
}
