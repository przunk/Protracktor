/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
package com.przunk.protracktor

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.engine.NativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Proof of concept, not the app.
 *
 * It exists to establish one thing on real hardware: that a module picked through the storage access
 * framework reaches libopenmpt and comes out of the speaker through Oboe. The real UI
 * (`docs/REQUIREMENTS.md`) is built once that holds.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ProtracktorTheme { ProofOfConceptScreen() } }
    }
}

@Composable
private fun ProtracktorTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    // Dynamic colour is API 31+; minSdk is 29, so the fallback is not optional.
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private sealed interface Loaded {
    data class Module(val track: NativeEngine.Track, val metadata: Map<String, String>) : Loaded
    data class Failed(val message: String) : Loaded
}

@Composable
private fun ProofOfConceptScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf<Loaded?>(null) }
    var playing by remember { mutableStateOf(false) }

    // The native side owns memory the garbage collector cannot see, so leaving the screen has to
    // release it explicitly.
    DisposableEffect(loaded) {
        onDispose { (loaded as? Loaded.Module)?.track?.close() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            (loaded as? Loaded.Module)?.track?.close()
            playing = false
            loaded = openModule(context, uri)
        }
    }

    Scaffold { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.poc_notice),
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Text(stringResource(R.string.action_open_module))
                }

                val module = loaded as? Loaded.Module
                OutlinedButton(
                    enabled = module != null,
                    onClick = {
                        val track = module?.track ?: return@OutlinedButton
                        if (playing) {
                            track.stop()
                            playing = false
                        } else {
                            playing = track.start()
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (playing) R.string.action_stop else R.string.action_play
                        )
                    )
                }
            }

            when (val state = loaded) {
                null -> Unit
                is Loaded.Failed -> Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is Loaded.Module -> Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.metadata.forEach { (key, value) ->
                            if (value.isNotBlank()) {
                                Text(
                                    text = "$key: $value",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun openModule(context: Context, uri: Uri): Loaded = withContext(Dispatchers.IO) {
    // Bytes rather than a path: a content:// URI has no path, and neither will archive entries or
    // files fetched from a remote catalogue. Modules are kilobytes, so reading whole is not a cost.
    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return@withContext Loaded.Failed("Could not read $uri")

    val track = NativeEngine.open(bytes)
        ?: return@withContext Loaded.Failed(
            "Not a module libopenmpt recognises (${bytes.size} bytes read)"
        )

    Loaded.Module(track, track.describe())
}
