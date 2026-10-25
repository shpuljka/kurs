package io.github.shpuljka.kurs.scan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.shpuljka.kurs.R
import io.github.shpuljka.kurs.calc.AmountFormat
import io.github.shpuljka.kurs.converter.ConverterViewModel
import io.github.shpuljka.kurs.data.Currencies
import io.github.shpuljka.kurs.data.Rates
import io.github.shpuljka.kurs.ui.CurrencyFlag
import io.github.shpuljka.kurs.ui.theme.AmountTextStyle
import io.github.shpuljka.kurs.ui.theme.KursShapes
import io.github.shpuljka.kurs.ui.theme.KursTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import kotlin.math.hypot

@Composable
fun ScannerRoute(onClose: () -> Unit, viewModel: ConverterViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val model = state.model
    val target = model.scanTarget(state.local)
    ScannerScreen(
        targetCode = model.codes[target],
        previewCodes = model.scanPreview(target).map { model.codes[it] },
        rates = state.rates,
        // A photo left open last time (a menu, say) comes back instead of the camera, spec.md §12.3.
        // Prices read for another currency would be mislabelled (and cents rounded for JPY), so such a photo is dropped.
        lastPhoto = viewModel.lastScanPhoto?.takeIf { it.targetCode == model.codes[target] },
        onPhotoChange = { viewModel.lastScanPhoto = it },
        onInsert = {
            viewModel.insertScanned(it)
            onClose()
        },
        onClose = onClose,
    )
}

/**
 * A still picture with the prices found on it; [selected] indexes [candidates]. [fromPhoto]: picked from the gallery.
 * [targetCode]: the currency the prices were read for (it decides the cents rules).
 */
class Frozen(val image: ImageBitmap, val candidates: List<PriceCandidate>, val selected: Int?, val fromPhoto: Boolean, val targetCode: String) {
    fun select(index: Int) = Frozen(image, candidates, index, fromPhoto, targetCode)
}

/** Price tag scanner, spec.md §12.3. Live camera with highlighted prices; a tap or a gallery photo freezes the frame. */
@Composable
fun ScannerScreen(
    targetCode: String,
    previewCodes: List<String>,
    rates: Rates?,
    lastPhoto: Frozen?,
    onPhotoChange: (Frozen?) -> Unit,
    onInsert: (BigDecimal) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    // Read when results arrive, so a target row that changes while the screen is open is respected.
    val digits by rememberUpdatedState(Currencies.fractionDigits(targetCode))

    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var refusals by rememberSaveable { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) refusals++
    }
    // Asked once per opening, when the camera is first needed: right away, or on leaving a remembered photo.
    var asked by remember { mutableStateOf(false) }
    // Back from system settings with the permission turned on.
    LifecycleResumeEffect(Unit) {
        granted = hasCameraPermission(context)
        onPauseOrDispose { }
    }

    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(recognizer) { onDispose { recognizer.close() } }

    var live by remember { mutableStateOf(emptyList<PriceCandidate>()) }
    var frozen by remember { mutableStateOf(lastPhoto) }
    LaunchedEffect(frozen == null) {
        if (frozen == null && !granted && !asked) {
            asked = true
            permission.launch(Manifest.permission.CAMERA)
        }
    }
    // Only a gallery photo is remembered; a frame frozen from the camera or the live view opens the camera next time.
    LaunchedEffect(frozen) { onPhotoChange(frozen?.takeIf { it.fromPhoto }) }
    var hasFlash by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var photoFailed by remember { mutableStateOf(false) }
    val preview = remember { PreviewHolder() }
    val controller = remember { cameraController(context) }
    val torch = rememberTorch(controller)

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            reading = true
            // A photo that can't be read leaves the current picture as it was.
            val photo = readPhoto(context, uri, recognizer, digits, targetCode)
            if (photo != null) frozen = photo
            photoFailed = photo == null
            reading = false
        }
    }
    fun freeze(f: Frozen) {
        controller.enableTorch(false)
        photoFailed = false
        frozen = f
    }
    // Back first leaves a frozen picture, then the scanner.
    BackHandler(enabled = frozen != null) { frozen = null }
    val pickPhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    CameraWindow(darkBackground = granted || frozen != null)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraPreview(
                controller = controller,
                recognizer = recognizer,
                // Recognition stops while a picture is frozen.
                analyzing = frozen == null,
                fractionDigits = { digits },
                holder = preview,
                onFlash = { hasFlash = it },
                onCandidates = { live = it },
                onTap = { x, y ->
                    val hit = hitTest(live, x, y, context)
                    val shot = preview.view?.bitmap
                    if (frozen == null && hit != null && shot != null) freeze(Frozen(shot.asImageBitmap(), live, hit, fromPhoto = false, targetCode = targetCode))
                },
            )
            if (frozen == null) Highlights(live, selected = 0, imageSize = null, onSelect = null)
        } else {
            NoCamera(
                // After two refusals Android stops asking; only system settings can turn the camera on.
                // (Dismissing the dialog also counts as a refusal but leaves it askable, hence not after the first.)
                blocked = refusals >= 2 && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false,
                onAllow = { blocked ->
                    if (blocked) context.startActivity(appSettings(context)) else permission.launch(Manifest.permission.CAMERA)
                },
                onGallery = pickPhoto,
            )
        }
        frozen?.let { f ->
            Image(f.image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().background(Color.Black))
            Highlights(f.candidates, f.selected, imageSize = ComposeSize(f.image.width.toFloat(), f.image.height.toFloat())) {
                frozen = f.select(it)
            }
        }

        TopBar(
            showTorch = granted && frozen == null && hasFlash,
            torch = torch,
            onTorch = { controller.enableTorch(!torch) },
            onGallery = pickPhoto,
            onClose = onClose,
        )

        val chosen = frozen?.let { f -> f.selected?.let(f.candidates::getOrNull) } ?: live.firstOrNull().takeIf { frozen == null }
        if (granted || frozen != null || reading || photoFailed) {
            BottomPanel(
                price = chosen?.amount,
                targetCode = targetCode,
                previewCodes = previewCodes,
                rates = rates,
                hint = when {
                    reading -> null
                    frozen != null || photoFailed -> stringResource(R.string.scan_not_found)
                    else -> stringResource(R.string.scan_hint)
                },
                frozen = frozen != null,
                onScanAgain = {
                    frozen = null
                    photoFailed = false
                },
                onInsert = onInsert,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Lets the frozen-frame snapshot reach the PreviewView created inside AndroidView. */
private class PreviewHolder {
    var view: PreviewView? = null
}

private fun cameraController(context: Context) = LifecycleCameraController(context).apply {
    setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
    // Small digits need more than the default 640×480.
    imageAnalysisResolutionSelector = ResolutionSelector.Builder()
        .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
        .build()
}

/** Whether the torch is actually on: CameraX turns it off when the camera reopens (after Home, say). */
@Composable
private fun rememberTorch(controller: LifecycleCameraController): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var on by remember { mutableStateOf(false) }
    DisposableEffect(controller, lifecycleOwner) {
        val observer = Observer<Int> { on = it == TorchState.ON }
        controller.torchState.observe(lifecycleOwner, observer)
        onDispose { controller.torchState.removeObserver(observer) }
    }
    return on
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun CameraPreview(
    controller: LifecycleCameraController,
    recognizer: TextRecognizer,
    analyzing: Boolean,
    fractionDigits: () -> Int,
    holder: PreviewHolder,
    onFlash: (Boolean) -> Unit,
    onCandidates: (List<PriceCandidate>) -> Unit,
    onTap: (Float, Float) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val candidates by rememberUpdatedState(onCandidates)
    val digits by rememberUpdatedState(fractionDigits)
    val flash by rememberUpdatedState(onFlash)
    val tap by rememberUpdatedState(onTap)
    DisposableEffect(controller, lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        controller.initializationFuture.addListener(
            { flash(controller.cameraInfo?.hasFlashUnit() == true) },
            ContextCompat.getMainExecutor(context),
        )
        onDispose { controller.unbind() }
    }
    DisposableEffect(controller, recognizer, analyzing) {
        if (analyzing) {
            val main = ContextCompat.getMainExecutor(context)
            controller.setImageAnalysisAnalyzer(main, MlKitAnalyzer(listOf(recognizer), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, main) { result ->
                result.getValue(recognizer)?.let { candidates(PriceParser.candidates(it.toOcrLines(), digits())) }
            })
        }
        onDispose { controller.clearImageAnalysisAnalyzer() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
                holder.view = this
                // PreviewView keeps pinch-zoom and tap-to-focus; a short tap is also checked against the prices.
                val slop = ViewConfiguration.get(ctx).scaledTouchSlop
                var downX = 0f
                var downY = 0f
                setOnTouchListener { _, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
                        MotionEvent.ACTION_UP ->
                            if (e.eventTime - e.downTime < ViewConfiguration.getTapTimeout() * 2 && hypot(e.x - downX, e.y - downY) < slop) tap(e.x, e.y)
                    }
                    false
                }
            }
        },
        onRelease = { holder.view = null },
    )
}

/**
 * Pills around the prices. Boxes are in view pixels when [imageSize] is null (live camera),
 * otherwise in pixels of an image drawn with [ContentScale.Fit].
 */
@Composable
private fun Highlights(candidates: List<PriceCandidate>, selected: Int?, imageSize: ComposeSize?, onSelect: ((Int) -> Unit)?) {
    val c = KursTheme.colors
    val density = LocalDensity.current
    val padX = with(density) { 8.dp.toPx() }
    val padY = with(density) { 5.dp.toPx() }
    val tapSlack = with(density) { 16.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fit = with(density) { Fit.of(imageSize, maxWidth.toPx(), maxHeight.toPx()) }
        val tapModifier = if (onSelect == null) Modifier else Modifier.pointerInput(candidates, fit) {
            detectTapGestures { p ->
                candidates.indices
                    .filter { fit.rect(candidates[it].box, tapSlack, tapSlack).contains(p.x to p.y) }
                    .minByOrNull { val b = fit.rect(candidates[it].box, 0f, 0f); hypot(b.centerX - p.x, b.centerY - p.y) }
                    ?.let(onSelect)
            }
        }
        Canvas(Modifier.fillMaxSize().then(tapModifier)) {
            candidates.forEachIndexed { i, cand ->
                pill(fit.rect(cand.box, padX, padY), if (i == selected) c.accent else Color.White, strong = i == selected)
            }
        }
    }
}

private fun DrawScope.pill(b: Box, color: Color, strong: Boolean) {
    val topLeft = Offset(b.left, b.top)
    val size = ComposeSize(b.right - b.left, b.bottom - b.top)
    val radius = CornerRadius(size.height / 2)
    drawRoundRect(color.copy(alpha = if (strong) 0.3f else 0.16f), topLeft, size, radius)
    drawRoundRect(color.copy(alpha = if (strong) 1f else 0.85f), topLeft, size, radius, style = Stroke(if (strong) 3.dp.toPx() else 1.5.dp.toPx()))
}

/** Maps image pixels to the screen for an image drawn with [ContentScale.Fit]; identity for live camera boxes. */
private data class Fit(val scale: Float, val dx: Float, val dy: Float) {
    fun rect(b: Box, padX: Float, padY: Float) =
        Box(dx + b.left * scale - padX, dy + b.top * scale - padY, dx + b.right * scale + padX, dy + b.bottom * scale + padY)

    companion object {
        fun of(image: ComposeSize?, width: Float, height: Float): Fit {
            if (image == null || image.width <= 0f || image.height <= 0f) return Fit(1f, 0f, 0f)
            val scale = minOf(width / image.width, height / image.height)
            return Fit(scale, (width - image.width * scale) / 2, (height - image.height * scale) / 2)
        }
    }
}

/** Index of the price under a tap at ([x], [y]) in view pixels, nearest first. */
private fun hitTest(candidates: List<PriceCandidate>, x: Float, y: Float, context: Context): Int? {
    val slack = 16 * context.resources.displayMetrics.density
    return candidates.indices
        .filter { Fit(1f, 0f, 0f).rect(candidates[it].box, slack, slack).contains(x to y) }
        .minByOrNull { hypot(candidates[it].box.centerX - x, candidates[it].box.centerY - y) }
}

@Composable
private fun TopBar(showTorch: Boolean, torch: Boolean, onTorch: () -> Unit, onGallery: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoundButton(R.drawable.ic_close, stringResource(R.string.close), onClose)
        Spacer(Modifier.weight(1f))
        RoundButton(R.drawable.ic_gallery, stringResource(R.string.scan_gallery), onGallery)
        if (showTorch) RoundButton(R.drawable.ic_flash, stringResource(R.string.scan_flash), onTorch, on = torch)
    }
}

@Composable
private fun RoundButton(icon: Int, label: String, onClick: () -> Unit, on: Boolean = false) {
    val c = KursTheme.colors
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).clip(KursShapes.pill).background(if (on) c.accent else Color.Black.copy(alpha = 0.45f)),
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = if (on) c.onAccent else Color.White)
    }
}

@Composable
private fun BottomPanel(
    price: BigDecimal?,
    targetCode: String,
    previewCodes: List<String>,
    rates: Rates?,
    hint: String?,
    frozen: Boolean,
    onScanAgain: () -> Unit,
    onInsert: (BigDecimal) -> Unit,
    modifier: Modifier,
) {
    val c = KursTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { AmountFormat(locale) }
    val noValue = stringResource(R.string.no_value)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KursShapes.sheet)
            .background(c.bg)
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (price == null) {
            Text(
                hint.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            )
        } else {
            AmountLine(targetCode, format.amount(price, Currencies.fractionDigits(targetCode)), big = true)
            previewCodes.forEach { code ->
                val value = rates?.convert(price, targetCode, code)
                AmountLine(code, if (value == null) noValue else format.amount(value, Currencies.fractionDigits(code)), big = false)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            if (frozen) {
                PanelButton(stringResource(R.string.scan_again), primary = false, enabled = true, onClick = onScanAgain, modifier = Modifier.weight(1f))
            }
            PanelButton(
                stringResource(R.string.scan_insert),
                primary = true,
                enabled = price != null,
                onClick = { price?.let(onInsert) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AmountLine(code: String, amount: String, big: Boolean) {
    val c = KursTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        CurrencyFlag(code, if (big) 30.dp else 24.dp)
        Text(
            code,
            style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            color = if (big) c.fg else c.muted,
            modifier = Modifier.padding(start = 10.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            amount,
            style = if (big) AmountTextStyle else MaterialTheme.typography.titleMedium,
            color = if (big) c.fg else c.muted,
            maxLines = 1,
        )
    }
}

@Composable
private fun PanelButton(text: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = KursTheme.colors
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(KursShapes.pill)
            .background(if (primary) c.accent.copy(alpha = if (enabled) 1f else 0.4f) else c.tag),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (primary) c.onAccent else c.accentText)
    }
}

@Composable
private fun NoCamera(blocked: Boolean, onAllow: (blocked: Boolean) -> Unit, onGallery: () -> Unit) {
    val c = KursTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_camera), contentDescription = null, tint = c.accentText, modifier = Modifier.size(40.dp))
        Text(stringResource(R.string.scan_no_camera), style = MaterialTheme.typography.titleLarge, color = c.fg, textAlign = TextAlign.Center)
        Text(stringResource(R.string.scan_no_camera_text), style = MaterialTheme.typography.bodyLarge, color = c.muted, textAlign = TextAlign.Center)
        PanelButton(
            stringResource(if (blocked) R.string.scan_open_settings else R.string.scan_allow),
            primary = true, enabled = true, onClick = { onAllow(blocked) }, modifier = Modifier.fillMaxWidth(),
        )
        PanelButton(stringResource(R.string.scan_gallery), primary = false, enabled = true, onClick = onGallery, modifier = Modifier.fillMaxWidth())
    }
}

/** Portrait only (spec.md §12.3), light status bar icons over the dark camera picture; both restored on leaving. */
@Composable
private fun CameraWindow(darkBackground: Boolean) {
    val activity = LocalActivity.current ?: return
    val bars = remember(activity) { WindowCompat.getInsetsController(activity.window, activity.window.decorView) }
    // Saved: locking to portrait from landscape recreates the activity, and the new one must restore the original.
    val orientation = rememberSaveable { activity.requestedOrientation }
    DisposableEffect(activity) {
        val lightStatus = bars.isAppearanceLightStatusBars
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            // Recreated for the rotation to portrait: the new instance locks again, don't flip back in between.
            if (!activity.isChangingConfigurations) activity.requestedOrientation = orientation
            bars.isAppearanceLightStatusBars = lightStatus
        }
    }
    val lightTheme = !KursTheme.colors.isDark
    LaunchedEffect(darkBackground, lightTheme) { bars.isAppearanceLightStatusBars = lightTheme && !darkBackground }
}

private suspend fun readPhoto(context: Context, uri: Uri, recognizer: TextRecognizer, digits: Int, targetCode: String): Frozen? = try {
    val bitmap: Bitmap = withContext(Dispatchers.IO) { loadPhoto(context, uri) }
    val candidates = PriceParser.candidates(recognizer.read(bitmap).toOcrLines(), digits)
    Frozen(bitmap.asImageBitmap(), candidates, candidates.indices.firstOrNull(), fromPhoto = true, targetCode = targetCode)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w("Scanner", "Can't read photo $uri", e)
    null
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun appSettings(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
