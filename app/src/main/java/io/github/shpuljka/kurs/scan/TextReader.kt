package io.github.shpuljka.kurs.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil

/** ML Kit result as parser input: one [OcrLine] per recognized line, one [Glyph] per symbol. */
fun Text.toOcrLines(): List<OcrLine> =
    textBlocks.flatMap { block -> block.lines.map { line -> OcrLine(line.elements.map { it.glyphs() }) } }

private fun Text.Element.glyphs(): List<Glyph> {
    val symbols = symbols
    if (symbols.isNotEmpty() && symbols.all { it.boundingBox != null }) {
        val glyphs = symbols.flatMap { s -> s.text.map { ch -> Glyph(ch, s.boundingBox!!.toBox()) } }
        return boundingBox?.let { fitInto(glyphs, it.toBox()) } ?: glyphs
    }
    // No per-symbol boxes: split the word evenly. Raised cents then can't be told apart, the rest still works.
    val b = boundingBox ?: return emptyList()
    val width = b.width().toFloat() / text.length.coerceAtLeast(1)
    return text.mapIndexed { i, ch -> Glyph(ch, Box(b.left + i * width, b.top.toFloat(), b.left + (i + 1) * width, b.bottom.toFloat())) }
}

/** Symbol boxes of big print drift sideways; stretch them horizontally onto the word's box, which is accurate. */
private fun fitInto(glyphs: List<Glyph>, word: Box): List<Glyph> {
    val left = glyphs.minOf { it.box.left }
    val right = glyphs.maxOf { it.box.right }
    if (right <= left) return glyphs
    val scale = (word.right - word.left) / (right - left)
    return glyphs.map { g -> g.copy(box = g.box.copy(left = word.left + (g.box.left - left) * scale, right = word.left + (g.box.right - left) * scale)) }
}

private fun Rect.toBox() = Box(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

/** Recognizes text in a still picture (a photo from the gallery). */
suspend fun TextRecognizer.read(bitmap: Bitmap): Text = suspendCancellableCoroutine { cont ->
    process(InputImage.fromBitmap(bitmap, 0))
        .addOnSuccessListener { cont.resume(it) }
        .addOnFailureListener { cont.resumeWithException(it) }
}

/** Decodes a picked photo, downscaled so its longer side is at most [maxSide]. Blocking. */
fun loadPhoto(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        // ML Kit and drawing need a software bitmap.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > maxSide) decoder.setTargetSampleSize(ceil(longest.toDouble() / maxSide).toInt())
    }
