// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.przunk.protracktor.R
import com.przunk.protracktor.net.WebRemote

/**
 * Reading a pairing code off a browser's screen.
 *
 * Camera access is requested when this screen opens. Frames are decoded and dropped;
 * nothing is stored and nothing leaves
 * the device (`docs/PLAN_HANDOFF.md` §3 H2).
 *
 * ZXing rather than ML Kit — `gradle/libs.versions.toml` says why. It reads a QR out of the
 * luminance plane CameraX already hands over, so no bitmap is ever built.
 */
@Composable
fun ScannerScreen(
    contentPadding: PaddingValues,
    onScanned: (String) -> Unit,
    onSendLink: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
com.przunk.protracktor.permissions.SystemAccess.allowed(context, com.przunk.protracktor.permissions.AppAccess.CAMERA)
        )
    }
    var refused by remember { mutableStateOf(false) }
    // RV-B035: allowed, and still no camera -- only a front one, or one that failed to start.
    var unavailable by remember { mutableStateOf(false) }

    val ask = com.przunk.protracktor.permissions.rememberAccessRequestCoordinator { _, result ->
        granted = result == com.przunk.protracktor.permissions.AccessRequestResult.GRANTED
        refused = !granted
    }
    LaunchedEffect(Unit) { if (!granted) ask.request(com.przunk.protracktor.permissions.AppAccess.CAMERA) }

    // The policy, one press from where the sending is decided (the owner, 2026-09-22). Drawn in place
    // of the scanner, as Settings draws it in place of its list; Back returns to the camera.
    var readingPolicy by remember { mutableStateOf(false) }
    if (readingPolicy) {
        BackHandler { readingPolicy = false }
        PrivacyPolicyScreen(contentPadding = contentPadding, onClose = { readingPolicy = false })
        return
    }

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        val live = granted && !unavailable
        if (live) {
            CameraPreview(onScanned = onScanned, onUnavailable = { unavailable = true })
        }
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    when {
                        refused -> R.string.scan_refused
                        unavailable -> R.string.scan_unavailable
                        else -> R.string.scan_hint
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (live) Color.White else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            // **What sending involves, where it is decided.** A scanned code names a server, and the
            // queue -- files from this phone included -- goes there; the policy says the same.
            Text(
                text = stringResource(R.string.scan_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = if (live) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = { readingPolicy = true }) {
                Icon(PlayerIcons.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_privacy))
            }
            TextButton(onClick = onSendLink) {
                Icon(PlayerIcons.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_send_as_link))
            }
            Button(onClick = onCancel) {
                Icon(PlayerIcons.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun CameraPreview(onScanned: (String) -> Unit, onUnavailable: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // **Once.** A QR sits in front of the camera for many frames, and every one of them decodes;
    // without this the pairing fires again for each, which at best is noise and at worst is a
    // playlist posted twenty times.
    var handled by remember { mutableStateOf(false) }

    val view = remember { PreviewView(context) }
    val executor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        // Left before the provider was ready: the listener must then bind nothing, or the camera
        // stays open on the activity after the screen is gone (RV-B035).
        var disposed = false
        var bound: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            val started = CameraStart.attempt {
            val provider = future.get()
            bound = provider
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { image ->
                if (!handled) {
                    readQr(image)?.let { text ->
                        if (WebRemote.looksLikePairing(text)) {
                            handled = true
                            view.post { onScanned(text.trim()) }
                        }
                    }
                }
                image.close()
            }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
            if (!started) onUnavailable()
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            // The provider the listener got, not a second blocking get() on the main thread.
            bound?.let { provider -> CameraStart.attempt { provider.unbindAll() } }
            executor.shutdown()
        }
    }

    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
}

/**
 * Starting the camera, which can fail in ways nothing here can prevent (RV-B035): a device whose
 * only camera faces the front -- the manifest does not require one at the back -- makes binding
 * the back one throw `IllegalArgumentException`; a camera service that fails makes the provider's
 * `get()` throw `ExecutionException`; others throw `IllegalStateException`. Each means the same
 * thing to the person holding the phone, so all are caught here and the screen says so.
 */
internal object CameraStart {
    fun attempt(start: () -> Unit): Boolean =
        try {
            start()
            true
        } catch (_: java.util.concurrent.ExecutionException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IllegalStateException) {
            false
        } catch (_: InterruptedException) {
            false
        }
}

private val reader = QRCodeReader()

/** One frame, from the luminance plane CameraX already has. Null when there is no code in it. */
private fun readQr(image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val bytes = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
    val source = PlanarYUVLuminanceSource(
        bytes, plane.rowStride, image.height, 0, 0,
        minOf(plane.rowStride, image.width), image.height, false,
    )
    return try {
        reader.decode(
            BinaryBitmap(HybridBinarizer(source)),
            mapOf(DecodeHintType.TRY_HARDER to true),
        ).text
    } catch (_: Exception) {
        // NotFoundException on every frame without a code, which is nearly all of them.
        null
    } finally {
        reader.reset()
    }
}
