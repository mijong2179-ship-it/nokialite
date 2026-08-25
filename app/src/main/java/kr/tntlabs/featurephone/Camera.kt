package kr.tntlabs.featurephone

import android.content.ContentValues
import android.media.MediaActionSound
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// LCD 뷰파인더 카메라. @/5 촬영, 4/6 전후면 전환
class CameraHolder {
    var capture: ImageCapture? = null
    var front by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var flash by mutableStateOf(false)
    private val sound = MediaActionSound()

    fun shoot(ctx: android.content.Context) {
        val cap = capture ?: return
        val name = "FP_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FeaturePhone")
        }
        val out = ImageCapture.OutputFileOptions.Builder(
            ctx.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()
        sound.play(MediaActionSound.SHUTTER_CLICK)
        flash = true
        cap.takePicture(out, ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) { status = "저장되었습니다" }
            override fun onError(e: ImageCaptureException) { status = "저장 실패" }
        })
    }
}

@Composable
fun LcdCamera(holder: CameraHolder, granted: Boolean) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    LaunchedEffect(granted, holder.front) {
        if (!granted) return@LaunchedEffect
        val provider = ProcessCameraProvider.getInstance(ctx).get()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        val selector = if (holder.front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(owner, selector, preview, capture)
            holder.capture = capture
        }
    }
    LaunchedEffect(holder.flash) { if (holder.flash) { delay(120); holder.flash = false } }
    LaunchedEffect(holder.status) { if (holder.status != null) { delay(1500); holder.status = null } }

    Column(Modifier.fillMaxSize()) {
        LcdHeader("카메라", if (holder.front) "전면" else "후면")
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
            if (granted) AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            else Text(
                "카메라 권한이 필요합니다", Modifier.align(Alignment.Center),
                color = Color.White, fontFamily = Pixel, fontSize = 16.sp
            )
            // 뷰파인더 모서리 브래킷
            Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                val l = 28f; val w = size.width; val h = size.height; val sw = 4f
                val c = Color.White
                drawLine(c, Offset(0f, 0f), Offset(l, 0f), sw); drawLine(c, Offset(0f, 0f), Offset(0f, l), sw)
                drawLine(c, Offset(w, 0f), Offset(w - l, 0f), sw); drawLine(c, Offset(w, 0f), Offset(w, l), sw)
                drawLine(c, Offset(0f, h), Offset(l, h), sw); drawLine(c, Offset(0f, h), Offset(0f, h - l), sw)
                drawLine(c, Offset(w, h), Offset(w - l, h), sw); drawLine(c, Offset(w, h), Offset(w, h - l), sw)
            }
            if (holder.flash) Box(Modifier.fillMaxSize().background(Color.White))
            holder.status?.let {
                Text(
                    it, Modifier.align(Alignment.BottomCenter).padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    color = Color.White, fontFamily = Pixel, fontSize = 15.sp
                )
            }
        }
        SoftkeyRow("촬영", "", "뒤로")
    }
}
