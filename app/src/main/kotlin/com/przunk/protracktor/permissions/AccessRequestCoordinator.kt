// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.permissions

import android.app.Activity
import android.content.Context
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

/**
 * Which accesses have had the system's own dialog shown at least once. The coordinator reads it to
 * know when that dialog will no longer appear; the start-up ask reads it to ask only once.
 */
internal object AccessHistory {
    private const val FILE = "access-requests"
    fun asked(context: Context, access: AppAccess): Boolean = prefs(context).getBoolean(access.name, false)
    fun markAsked(context: Context, access: AppAccess) = prefs(context).edit().putBoolean(access.name, true).apply()
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/**
 * Whether the app may ask for [state]'s access without anybody having pressed anything: only through
 * the system's dialog, and only if that dialog was never shown before.
 *
 * A request somebody pressed a button for may end in system settings; an unprompted one must not,
 * or an app asked at start-up leaves for settings on every launch for the people who said no.
 */
internal fun mayAskUnprompted(state: AccessState, askedBefore: Boolean): Boolean =
    state == AccessState.REQUEST_NEEDED && !askedBefore

/** Asks for [access] once in the app's life, at the first composition that finds it missing. */
@Composable
internal fun AskOnceUnprompted(access: AppAccess) {
    val context = LocalContext.current
    val coordinator = rememberAccessRequestCoordinator { _, _ -> }
    LaunchedEffect(access) {
        if (mayAskUnprompted(SystemAccess.inspect(context, access), AccessHistory.asked(context, access))) {
            coordinator.request(access)
        }
    }
}
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
        val useSettings = override != null || state == AccessState.SETTINGS_NEEDED || permission == null ||
            (AccessHistory.asked(context, access) && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission))
        try {
            if (useSettings) {
                settingsPending = true
                settings.launch(override ?: SystemAccess.settings(context, access))
            } else {
                AccessHistory.markAsked(context, access)
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
