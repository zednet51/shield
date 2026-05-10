package com.aggregatorx.shielded.engine.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * ML Kit OCR Vision Engine.
 * Downloads thumbnails and extracts text keywords for result enrichment.
 */
@Singleton
class VisionEngine @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Download [imageUrl] and return OCR-extracted keywords, or empty string on failure. */
    suspend fun extractKeywords(imageUrl: String): String {
        if (imageUrl.isBlank()) return ""
        val bitmap = downloadBitmap(imageUrl) ?: return ""
        return runOcr(bitmap)
    }

    /** Run OCR on an already-loaded [Bitmap]. Returns space-separated keywords. */
    suspend fun runOcr(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val keywords = result.textBlocks
                    .flatMap { block -> block.lines.map { it.text.trim() } }
                    .filter { it.length in 2..40 }
                    .joinToString(" ")
                cont.resume(keywords)
            }
            .addOnFailureListener { cont.resume("") }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            http.newCall(req).execute().use { resp ->
                resp.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        } catch (_: Exception) { null }
    }
}
