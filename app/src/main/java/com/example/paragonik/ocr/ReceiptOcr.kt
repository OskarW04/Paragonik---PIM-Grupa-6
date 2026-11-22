package com.example.paragonik.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2

// OCR przez ML Kit. Model jest w aplikacji, więc działa bez internetu.
object ReceiptOcr {

    private const val TAG = "ParagonikOCR"

    suspend fun recognize(context: Context, imageUri: Uri): List<OcrLine> = withContext(Dispatchers.Default) {
        // fromFilePath sam obraca zdjęcie według EXIF
        val image = InputImage.fromFilePath(context, imageUri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = Tasks.await(recognizer.process(image))
            toLines(text).also { lines ->
                Log.d(TAG, "Rozpoznane linie: ${lines.size}")
                lines.forEach { Log.d(TAG, "${it.left},${it.top},${it.right},${it.bottom} a=${it.angle} | ${it.text}") }
            }
        } finally {
            recognizer.close()
        }
    }

    private fun toLines(text: Text): List<OcrLine> =
        text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            val corners = line.cornerPoints
            // nachylenie linii z dwóch górnych narożników ramki
            val angle = if (corners != null && corners.size >= 2) {
                atan2((corners[1].y - corners[0].y).toDouble(), (corners[1].x - corners[0].x).toDouble())
            } else {
                0.0
            }
            OcrLine(line.text, box.left, box.top, box.right, box.bottom, angle)
        }
}
