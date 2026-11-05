// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.permissions

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

internal enum class AccessRequestResult { GRANTED, DENIED, UNAVAILABLE, BUSY }
internal class AccessRequestCoordinator(
    val request: (AppAccess) -> Unit,
    val openSettings: (AppAccess, Intent) -> Unit,
)

/** Launchers and pending requests belong to this screen; no application singleton holds an Activity. */
@Composable
internal fun rememberAccessRequestCoordinator(onResult: (AppAccess, AccessRequestResult) -> Unit): AccessRequestCoordinator {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val callback by rememberUpdatedState(onResult)
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsPending by rememberSaveable { mutableStateOf(false) }
    var leftScreen by rememberSaveable { mutableStateOf(false) }
    var attached by remember { mutableStateOf(true) }
    fun complete() {
        val access = pending?.let(AppAccess::valueOf) ?: return
        pending = null; settingsPending = false; leftScreen = false
        if (attached) callback(access, if (SystemAccess.allowed(context, access)) AccessRequestResult.GRANTED else AccessRequestResult.DENIED)
    }
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { complete() }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { complete() }
    DisposableEffect(lifecycle) {
        attached = true
        val observer = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) { if (settingsPending) leftScreen = true }
            override fun onResume(owner: LifecycleOwner) {
                // Recreation can resume the host before its pending settings result returns.
                // A denial is final only when the launcher delivers that result.
                val access = pending?.let(AppAccess::valueOf)
                if (settingsPending && leftScreen && access != null && SystemAccess.allowed(context, access)) complete()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { attached = false; lifecycle.removeObserver(observer) }
    }
    fun launch(access: AppAccess, override: Intent? = null) {
        if (pending != null) { callback(access, AccessRequestResult.BUSY); return }
        val state = SystemAccess.inspect(context, access)
        if (state == AccessState.UNAVAILABLE) { callback(access, AccessRequestResult.UNAVAILABLE); return }
        if (override == null && state.allowed) { callback(access, AccessRequestResult.GRANTED); return }
        pending = access.name
        val permission = SystemAccess.runtimePermission(access)
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext?.takeIf { base -> base !== it } }
            .filterIsInstance<Activity>().firstOrNull()
        val history = context.getSharedPreferences("access-requests", android.content.Context.MODE_PRIVATE)
        val useSettings = override != null || state == AccessState.SETTINGS_NEEDED || permission == null ||
            (history.getBoolean(access.name, false) && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission))
        try {
            if (useSettings) {
                settingsPending = true
                settings.launch(override ?: SystemAccess.settings(context, access))
            } else {
                history.edit().putBoolean(access.name, true).apply()
                runtime.launch(checkNotNull(permission))
            }
        } catch (_: android.content.ActivityNotFoundException) {
            pending = null; settingsPending = false; leftScreen = false
            callback(access, AccessRequestResult.UNAVAILABLE)
        } catch (_: SecurityException) {
            pending = null; settingsPending = false; leftScreen = false
            callback(access, AccessRequestResult.UNAVAILABLE)
        }
    }
    return AccessRequestCoordinator({ launch(it) }, { access, intent -> launch(access, intent) })
}
