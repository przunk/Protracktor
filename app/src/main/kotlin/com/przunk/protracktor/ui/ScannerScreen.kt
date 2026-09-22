// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
 * **The only screen in this app that asks for a permission**, and it asks at the moment it opens
 * rather than at start-up: a music player requesting the camera on first launch is a player that
 * looks like something else. Frames are decoded and dropped; nothing is stored and nothing leaves
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
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var refused by remember { mutableStateOf(false) }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        refused = !ok
    }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    // The policy, one press from where the sending is decided (the owner, 2026-09-22). Drawn in place
    // of the scanner, as Settings draws it in place of its list; Back returns to the camera.
    var readingPolicy by remember { mutableStateOf(false) }
    if (readingPolicy) {
        BackHandler { readingPolicy = false }
        PrivacyPolicyScreen(contentPadding = contentPadding, onClose = { readingPolicy = false })
        return
    }

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        if (granted) {
            CameraPreview(onScanned = onScanned)
        }
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (refused) R.string.scan_refused else R.string.scan_hint
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (granted) Color.White else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            // **What sending involves, where it is decided.** A scanned code names a server, and the
            // queue -- files from this phone included -- goes there; the policy says the same.
            Text(
                text = stringResource(R.string.scan_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = { readingPolicy = true }) {
                Icon(PlayerIcons.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_privacy))
            }
            TextButton(onClick = onSendLink) {
                Text(stringResource(R.string.action_send_as_link))
            }
            Button(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
private fun CameraPreview(onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // **Once.** A QR sits in front of the camera for many frames, and every one of them decodes;
    // without this the pairing fires again for each, which at best is noise and at worst is a
    // playlist posted twenty times.
    var handled by remember { mutableStateOf(false) }

    val view = remember { PreviewView(context) }
    val executor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
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
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
            executor.shutdown()
        }
    }

    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
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
