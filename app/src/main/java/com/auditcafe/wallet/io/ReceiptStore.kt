package com.auditcafe.wallet.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/** صور الفواتير: تُحفظ داخل مساحة التطبيق الخاصة فقط (لا تخرج من الجهاز). */
object ReceiptStore {
    private fun dir(ctx: Context) = File(ctx.filesDir, "receipts").apply { mkdirs() }
    fun file(ctx: Context, name: String) = File(dir(ctx), name)

    private fun sampleFor(w: Int, h: Int, maxPx: Int): Int {
        var s = 1
        while (max(w, h) / s > maxPx) s *= 2
        return s
    }

    private fun degrees(o: Int) = when (o) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }

    /** يصغّر الصورة (حتى 1800 بكسل) ويعدل اتجاهها ويحفظها JPEG. يعيد اسم الملف أو null. */
    fun save(ctx: Context, uri: Uri): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, 1800) }
        var bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val rot = ctx.contentResolver.openInputStream(uri)?.use {
            degrees(ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
        } ?: 0f
        if (bmp != null && rot != 0f) {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
            if (rotated !== bmp) bmp.recycle()
            bmp = rotated
        }
        if (bmp == null) null else {
            val name = UUID.randomUUID().toString() + ".jpg"
            FileOutputStream(file(ctx, name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            bmp.recycle()
            name
        }
    } catch (_: Exception) {
        null
    }

    fun load(ctx: Context, name: String, maxPx: Int): Bitmap? = try {
        val f = file(ctx, name)
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, b)
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sampleFor(b.outWidth, b.outHeight, maxPx) })
    } catch (_: Exception) {
        null
    }

    fun delete(ctx: Context, name: String?) {
        if (name.isNullOrEmpty() || name.contains('/')) return
        runCatching { file(ctx, name).delete() }
    }
}
