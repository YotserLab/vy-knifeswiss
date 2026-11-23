package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * QrSnapshotDecoder — Robust QR Code reader for screenshot / snapshot files.
 * Uses ML Kit Vision BarcodeScanning with ZXing fallback (HybridBinarizer & GlobalHistogramBinarizer)
 * to guarantee reading high-resolution or dark-mode screenshots.
 */
object QrSnapshotDecoder {

    /**
     * Decodes a QR code from a snapshot / screenshot Uri.
     * Calls [onResult] with the decoded text string or null if not found.
     */
    suspend fun decodeSnapshotUri(context: Context, uri: Uri, onResult: (String?) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Try ML Kit first
                val inputImage = InputImage.fromFilePath(context, uri)
                BarcodeScanning.getClient().process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val text = barcodes.firstOrNull()?.rawValue
                        if (!text.isNullOrBlank()) {
                            onResult(text)
                        } else {
                            // Fallback to ZXing on bitmap
                            val bitmap = decodeBitmapFromUri(context, uri)
                            val zxingText = bitmap?.let { decodeZxing(it) }
                            onResult(zxingText)
                        }
                    }
                    .addOnFailureListener {
                        val bitmap = decodeBitmapFromUri(context, uri)
                        val zxingText = bitmap?.let { decodeZxing(it) }
                        onResult(zxingText)
                    }
            } catch (e: Exception) {
                // Fallback to ZXing directly
                val bitmap = decodeBitmapFromUri(context, uri)
                val zxingText = bitmap?.let { decodeZxing(it) }
                onResult(zxingText)
            }
        }
    }

    private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeZxing(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val intArray = IntArray(width * height)
            bitmap.getPixels(intArray, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, intArray)

            // Try HybridBinarizer
            try {
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                val result = MultiFormatReader().decode(binaryBitmap)
                return result.text
            } catch (e: Exception) {
                // Try GlobalHistogramBinarizer (great for dark mode screenshots)
                val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
                val result = MultiFormatReader().decode(binaryBitmap)
                return result.text
            }
        } catch (e: Exception) {
            null
        }
    }
}
