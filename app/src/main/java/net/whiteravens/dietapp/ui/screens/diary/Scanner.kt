// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.diary

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.components.ScreenDialog
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/** Whether this phone has let the application use its camera. */
private fun Context.mayUseCamera(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * A window with the camera, which reads a barcode and hands it on. The permission is asked for when
 * the window opens, not before: somebody who never scans is never asked. Without it the window says
 * so, and leads to the phone's settings and back to typing the code.
 */
@Composable
internal fun ScannerDialog(onCode: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(context.mayUseCamera()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var noCamera by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        asked = true
        allowed = granted
    }
    LaunchedEffect(Unit) {
        if (!allowed && !asked) ask.launch(Manifest.permission.CAMERA)
    }
    ScreenDialog(onClose) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.scanner_title), style = MaterialTheme.typography.titleLarge)
            if (allowed) {
                Text(stringResource(R.string.scanner_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (noCamera) {
                    Column(Modifier.weight(1f)) { Text(stringResource(R.string.scanner_no_camera)) }
                } else {
                    CameraPicture(onCode, { noCamera = true }, Modifier.weight(1f).fillMaxWidth())
                }
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (asked) {
                        Text(stringResource(R.string.scanner_no_permission))
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        }) { Text(stringResource(R.string.scanner_settings)) }
                    }
                }
            }
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.scanner_type_instead)) }
            }
        }
    }
}

/** What the camera sees, and a light for a dim shop where the phone has one; [onNoCamera] where it has no camera to use. */
@Composable
private fun CameraPicture(onCode: (String) -> Unit, onNoCamera: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onCode)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var light by remember { mutableStateOf(false) }
    val described = stringResource(R.string.scanner_picture)
    val view = remember { PreviewView(context) }
    DisposableEffect(lifecycle) {
        val analysis = Executors.newSingleThreadExecutor()
        val reader = BarcodeReader()
        var handedOn = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val frames = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                frames.setAnalyzer(analysis) { frame ->
                    val code = frame.use { reader.readFrame(it) }
                    if (code != null && !handedOn) {
                        handedOn = true
                        scope.launch { latest(code) }
                    }
                }
                camera = provider.bindOnly(lifecycle, preview, frames)
            } catch (e: ExecutionException) {
                onNoCamera()
            } catch (e: IllegalArgumentException) {
                onNoCamera()
            } catch (e: IllegalStateException) {
                onNoCamera()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            if (future.isDone) runCatching { future.get().unbindAll() }
            analysis.shutdown()
        }
    }
    Box(modifier) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().semantics { contentDescription = described })
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            Button(
                onClick = {
                    light = !light
                    camera?.cameraControl?.enableTorch(light)
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            ) { Text(stringResource(if (light) R.string.scanner_light_off else R.string.scanner_light_on)) }
        }
    }
}

/** The back camera, for this window alone. */
private fun ProcessCameraProvider.bindOnly(owner: LifecycleOwner, preview: Preview, frames: ImageAnalysis): Camera {
    unbindAll()
    return bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, frames)
}

/** The brightness plane of a frame of the camera, which is all a barcode needs. */
private fun BarcodeReader.readFrame(frame: ImageProxy): String? {
    val plane = frame.planes[0]
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    return read(bytes, frame.width, frame.height, plane.rowStride)
}
