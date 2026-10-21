package app.ominira.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.ominira.Session
import app.ominira.core.CardLine
import app.ominira.core.Seen
import app.ominira.voice.allowButton
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.util.concurrent.Executors

enum class Lens { Face, Card }

// One frame, as the customer sees it. lines are what was read on a card; never stored or logged.
class Sight(val seen: Seen?, val luma: Int, val lines: List<CardLine> = emptyList())

// The camera's preview, and each frame's Sight on the main thread. ML Kit runs on the phone.
@Composable
fun CameraFeed(lens: Lens, onSight: (Sight) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onSight)
    val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lens, owner) {
        val executor = Executors.newSingleThreadExecutor()
        val reader = if (lens == Lens.Face) FaceReader { latest(it) } else CardReader { latest(it) }
        val future = ProcessCameraProvider.getInstance(context)
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            val provider = future.get()
            val shown = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, reader) }
            val camera = if (lens == Lens.Face) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(owner, camera, shown, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            if (future.isDone) runCatching { future.get().unbindAll() }
            executor.shutdown()
            reader.close()
        }
    }
    AndroidView(factory = { preview }, modifier = modifier)
}

private abstract class Reader<T>(private val onSight: (Sight) -> Unit) : ImageAnalysis.Analyzer, Closeable {
    abstract fun detect(image: InputImage): Task<T>
    abstract fun sight(result: T, width: Float, height: Float, luma: Int): Sight

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image ?: return proxy.close()
        val rotation = proxy.imageInfo.rotationDegrees
        // ML Kit answers in the upright image: width and height swap when the sensor is turned.
        val (w, h) = if (rotation % 180 == 0) proxy.width to proxy.height else proxy.height to proxy.width
        val luma = luma(proxy)
        detect(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { onSight(sight(it, w.toFloat(), h.toFloat(), luma)) }
            .addOnCompleteListener { proxy.close() }
    }

    // Average brightness of the Y plane, sampled.
    private fun luma(proxy: ImageProxy): Int {
        val buffer = proxy.planes[0].buffer
        var sum = 0L
        var n = 0
        var i = 0
        while (i < buffer.limit()) {
            sum += buffer.get(i).toInt() and 0xFF
            n += 1
            i += 97
        }
        return if (n == 0) 255 else (sum / n).toInt()
    }
}

private class FaceReader(onSight: (Sight) -> Unit) : Reader<List<com.google.mlkit.vision.face.Face>>(onSight) {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.12f)
            .build(),
    )

    override fun detect(image: InputImage) = detector.process(image)

    override fun sight(result: List<com.google.mlkit.vision.face.Face>, width: Float, height: Float, luma: Int): Sight {
        val face = result.maxByOrNull { it.boundingBox.width() } ?: return Sight(null, luma)
        val box = face.boundingBox
        // The front camera's frame is the mirror of what the customer sees.
        return Sight(Seen(1f - box.exactCenterX() / width, box.exactCenterY() / height, box.width() / width, face.headEulerAngleY), luma)
    }

    override fun close() = detector.close()
}

private class CardReader(onSight: (Sight) -> Unit) : Reader<com.google.mlkit.vision.text.Text>(onSight) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun detect(image: InputImage) = recognizer.process(image)

    // The card is where its text is: a lone word is not a card.
    override fun sight(result: com.google.mlkit.vision.text.Text, width: Float, height: Float, luma: Int): Sight {
        val boxes = result.textBlocks.mapNotNull { it.boundingBox }
        if (boxes.size < 2) return Sight(null, luma)
        val left = boxes.minOf { it.left }
        val right = boxes.maxOf { it.right }
        val top = boxes.minOf { it.top }
        val bottom = boxes.maxOf { it.bottom }
        val seen = Seen((left + right) / 2f / width, (top + bottom) / 2f / height, (right - left) / width)
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            line.boundingBox?.let { CardLine(line.text, it.left, it.top, it.right, it.bottom) }
        }
        return Sight(seen, luma, lines)
    }

    override fun close() = recognizer.close()
}

class CameraAccess internal constructor(private val context: Context) {
    internal var launch: () -> Unit = {}
    internal var then: (Boolean) -> Unit = {}

    private val granted: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private val hasCamera: Boolean get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    // then(true) once the camera may be used, then(false) when it was refused: asked every time it is needed.
    fun ask(session: Session, then: (Boolean) -> Unit) {
        when {
            !hasCamera -> then(false)
            granted -> then(true)
            else -> {
                // The permission box is silent and easy to miss without sight.
                session.say(session.spoken.cameraAllow(allowButton()))
                this.then = then
                launch()
            }
        }
    }
}

@Composable
fun rememberCameraAccess(): CameraAccess {
    val context = LocalContext.current
    val access = remember { CameraAccess(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { access.then(it) }
    access.launch = { launcher.launch(Manifest.permission.CAMERA) }
    return access
}
