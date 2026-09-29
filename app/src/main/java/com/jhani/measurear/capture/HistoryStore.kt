package com.jhani.measurear.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.core.content.FileProvider
import com.jhani.measurear.measurement.MeasureUnit
import com.jhani.measurear.measurement.MeasurementSummary
import com.jhani.measurear.measurement.ValueKind
import com.jhani.measurear.measurement.formatSummaries
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * One saved entry in History: a name, when it was taken, the measurements (in meters) and an
 * optional photo of the screen at that moment.
 */
data class HistoryRecord(
    val id: String,
    val name: String,
    val createdAt: Long,
    val imagePath: String?,
    val items: List<MeasurementSummary>
) {
    val image: File? get() = imagePath?.let(::File)?.takeIf { it.exists() }
}

/**
 * Persistent measurement history in app-private storage — Android's equivalent of
 * localStorage: `files/history.json` holds the list, photos sit next to it as JPEGs. It
 * survives app restarts and reboots and is removed only with the app's data.
 */
object HistoryStore {

    private const val INDEX = "history.json"
    private const val PHOTOS = "captures"
    private const val JPEG_QUALITY = 85

    private val mutex = Mutex()
    private val _records = MutableStateFlow<List<HistoryRecord>?>(null)

    /** Newest first; null until first loaded. */
    val records: StateFlow<List<HistoryRecord>?> = _records.asStateFlow()

    private fun indexFile(context: Context) = File(context.filesDir, INDEX)
    private fun photoDir(context: Context) = File(context.filesDir, PHOTOS).apply { mkdirs() }

    suspend fun load(context: Context) = mutex.withLock {
        _records.value = withContext(Dispatchers.IO) { read(context) }
    }

    /**
     * Saves a new record. [bitmap] (optional) is stored as its photo.
     */
    suspend fun add(context: Context, name: String, items: List<MeasurementSummary>, bitmap: Bitmap?): HistoryRecord =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val id = UUID.randomUUID().toString()
                val image = bitmap?.let {
                    File(photoDir(context), "$id.jpg").also { file ->
                        file.outputStream().use { out -> it.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
                    }
                }
                val record = HistoryRecord(id, name, System.currentTimeMillis(), image?.path, items)
                val updated = listOf(record) + read(context)
                write(context, updated)
                _records.value = updated
                record
            }
        }

    suspend fun rename(context: Context, id: String, name: String) = update(context) { list ->
        list.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it }
    }

    suspend fun delete(context: Context, id: String) = update(context) { list ->
        list.filter { record ->
            if (record.id == id) record.image?.delete()
            record.id != id
        }
    }

    suspend fun clearAll(context: Context) = update(context) { list ->
        list.forEach { it.image?.delete() }
        emptyList()
    }

    private suspend fun update(context: Context, change: (List<HistoryRecord>) -> List<HistoryRecord>) =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val updated = change(read(context))
                write(context, updated)
                _records.value = updated
            }
        }

    // ---------------------------------------------------------------------------------------
    // JSON persistence
    // ---------------------------------------------------------------------------------------

    private fun read(context: Context): List<HistoryRecord> {
        val file = indexFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i -> fromJson(array.getJSONObject(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(context: Context, records: List<HistoryRecord>) {
        val array = JSONArray()
        records.forEach { array.put(toJson(it)) }
        // Write-then-rename so a crash mid-write never corrupts the history
        val tmp = File(context.filesDir, "$INDEX.tmp")
        tmp.writeText(array.toString())
        tmp.renameTo(indexFile(context))
    }

    private fun toJson(record: HistoryRecord) = JSONObject().apply {
        put("id", record.id)
        put("name", record.name)
        put("createdAt", record.createdAt)
        put("image", record.imagePath ?: JSONObject.NULL)
        put("items", JSONArray().apply {
            record.items.forEach { item ->
                put(JSONObject().apply {
                    put("value", item.value.toDouble())
                    put("isArea", item.isArea)
                    put("isEstimate", item.isEstimate)
                    put("horizontal", item.horizontal.toDouble())
                    put("vertical", item.vertical.toDouble())
                    put("kind", item.kind.name)
                    item.label?.let { put("label", it) }
                })
            }
        })
    }

    private fun fromJson(json: JSONObject): HistoryRecord {
        val items = json.optJSONArray("items") ?: JSONArray()
        return HistoryRecord(
            id = json.getString("id"),
            name = json.optString("name"),
            createdAt = json.optLong("createdAt"),
            imagePath = if (json.isNull("image")) null else json.optString("image"),
            items = (0 until items.length()).map { i ->
                val item = items.getJSONObject(i)
                MeasurementSummary(
                    value = item.optDouble("value").toFloat(),
                    isArea = item.optBoolean("isArea"),
                    isEstimate = item.optBoolean("isEstimate"),
                    horizontal = item.optDouble("horizontal", 0.0).toFloat(),
                    vertical = item.optDouble("vertical", 0.0).toFloat(),
                    kind = item.optString("kind").let { k -> ValueKind.values().firstOrNull { it.name == k } }
                        ?: if (item.optBoolean("isArea")) ValueKind.AREA else ValueKind.LENGTH,
                    label = if (item.has("label")) item.optString("label") else null
                )
            }
        )
    }

    // ---------------------------------------------------------------------------------------
    // Photos and sharing
    // ---------------------------------------------------------------------------------------

    /**
     * Photo of the measuring screen. The AR camera renders into its own [SurfaceView] surface,
     * which a window copy leaves black, so the camera surface is copied separately and the
     * window (lines, labels, HUD) is drawn over it. Null if the window copy fails.
     */
    suspend fun captureScreen(activity: Activity, cameraView: SurfaceView?): Bitmap? {
        val window = copyWindow(activity) ?: return null
        val camera = cameraView?.takeIf { it.width > 0 && it.height > 0 }?.let { copySurface(it) }
        camera ?: return window

        val result = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        // Camera at its on-screen position, then the window on top (transparent over the camera)
        val location = IntArray(2)
        cameraView.getLocationInWindow(location)
        canvas.drawBitmap(camera, location[0].toFloat(), location[1].toFloat(), null)
        canvas.drawBitmap(window, 0f, 0f, null)
        camera.recycle()
        window.recycle()
        return result
    }

    private suspend fun copyWindow(activity: Activity): Bitmap? = suspendCancellableCoroutine { cont ->
        val view = activity.window.decorView
        if (view.width == 0 || view.height == 0) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(activity.window, bitmap, { result ->
                cont.resume(if (result == PixelCopy.SUCCESS) bitmap else null)
            }, Handler(Looper.getMainLooper()))
        } catch (e: IllegalArgumentException) {
            cont.resume(null)
        }
    }

    private suspend fun copySurface(view: SurfaceView): Bitmap? = suspendCancellableCoroutine { cont ->
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(view, bitmap, { result ->
                cont.resume(if (result == PixelCopy.SUCCESS) bitmap else null)
            }, Handler(Looper.getMainLooper()))
        } catch (e: IllegalArgumentException) {
            cont.resume(null)
        }
    }

    /** Small bitmap for list thumbnails. */
    suspend fun thumbnail(record: HistoryRecord, targetWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        val file = record.image ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun share(context: Context, record: HistoryRecord, unit: MeasureUnit) {
        val text = "${record.name}\n${formatSummaries(record.items, unit)}"
        val image = record.image
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            if (image != null) {
                type = "image/jpeg"
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share measurement"))
    }
}
