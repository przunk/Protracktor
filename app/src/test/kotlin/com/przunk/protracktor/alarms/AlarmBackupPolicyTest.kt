// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AlarmBackupPolicyTest {
    @Test fun `packaged policy opts out of backup and excludes device files from both transports`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        val excluded = mutableSetOf<String>()
        var transport = ""
        context.resources.getXml(R.xml.data_extraction_rules).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                if (parser.name in setOf("cloud-backup", "device-transfer")) transport = parser.name
                if (parser.name == "exclude" && parser.getAttributeValue(null, "domain") == "device_file" &&
                    parser.getAttributeValue(null, "path") == ".") excluded += transport
            }
        }
        assertEquals(setOf("cloud-backup", "device-transfer"), excluded)
    }
}
