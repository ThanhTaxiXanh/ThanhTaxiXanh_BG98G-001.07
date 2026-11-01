package vn.thanhtaxixanh.bg98g00107.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import kotlin.math.min
import kotlin.math.max

/**
 * Imports evidence downloaded from 70mai. Video frames have an offset, NOT a
 * trustworthy GPS position. GPS sidecar correlation is a separate step.
 * Call only on a background executor.
 */
internal class AiCameraImporter(private val context: Context) {
    private val dir = File(context.filesDir,"ai_camera").apply { mkdirs() }
    private val modelDir = File(context.filesDir,"ai_models").apply { mkdirs() }
    private val detector = YoloOnnxSignDetector(modelDir)
    private val review = AiReviewStore(context)
    fun isReady() = detector.isReady()

    fun installVtsr(progress: (String) -> Unit): String {
        // All importer operations are serialized on the activity's AI worker.
        detector.resetModel()
        return VtsrModelInstaller(modelDir).install(progress)
    }

    fun install(uri: Uri, model: Boolean) {
        detector.resetModel()
        val destination=File(modelDir,if (model) "traffic_signs.onnx" else "labels.txt")
        val temp=File(modelDir,destination.name+".part")
        val max=if (model) 120_000_000L else 100_000L
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    val b=ByteArray(64*1024)
                    var total=0L
                    while (true) {
                        val n=input.read(b)
                        if (n<0) break
                        total+=n
                        require(total<=max) { "Tệp vượt giới hạn" }
                        output.write(b,0,n)
                    }
                    require(total>0L) { "Tệp rỗng" }
                }
            } ?: error("Không thể đọc tệp")
            if (destination.exists()) destination.delete()
            require(temp.renameTo(destination)) { "Không thể cài model/labels" }
        } finally { temp.delete() }
    }

    fun close() = detector.close()

    fun scan(uri: Uri, notify: (String)->Unit): Int {
        require(detector.isReady()) { "Cần nhập mô hình ONNX và labels.txt" }
        var saved=0
        var frames=0
        val seen=HashMap<String,Long>()
        fun scanFrame(frame: Bitmap, offset: Long?) {
            try {
                frames++
                val result=detector.detect(frame)
                for (det in result) {
                    if (saved>=60) break
                    val tick=offset?:0L
                    if (seen[det.label]?.let { tick-it<7000 }==true) continue
                    seen[det.label]=tick
                    val item=AiObservation(
                        kind=AiEvidenceKind.SIGN_DETECTION,
                        label=det.label, confidence=det.confidence,
                        proposedSpeedKph=AiObservationRules.speedFromLabel(det.label),
                        videoOffsetMs=offset,
                        source=if (offset==null) "70mai_image" else "70mai_video")
                    val evidence=File(dir,"evidence").apply { mkdirs() }
                    // Crop the sign box with margin, rather than shrinking the whole
                    // A500S frame: digits remain legible for human review.
                    val size=640f
                    val scale=size/max(frame.width,frame.height).toFloat()
                    val padX=(size-frame.width*scale)/2f
                    val padY=(size-frame.height*scale)/2f
                    val x1=((det.left-padX)/scale).toInt().coerceIn(0,frame.width-1)
                    val y1=((det.top-padY)/scale).toInt().coerceIn(0,frame.height-1)
                    val x2=((det.right-padX)/scale).toInt().coerceIn(x1+1,frame.width)
                    val y2=((det.bottom-padY)/scale).toInt().coerceIn(y1+1,frame.height)
                    val margin=max(12,(x2-x1)/3)
                    val left=(x1-margin).coerceAtLeast(0)
                    val top=(y1-margin).coerceAtLeast(0)
                    val right=(x2+margin).coerceAtMost(frame.width)
                    val bottom=(y2+margin).coerceAtMost(frame.height)
                    val thumb=Bitmap.createBitmap(frame,left,top,right-left,bottom-top)
                    File(evidence,item.id+".jpg").outputStream().use {
                        thumb.compress(Bitmap.CompressFormat.JPEG,82,it)
                    }
                    if (thumb!==frame) thumb.recycle()
                    review.add(item.copy(evidenceFile=item.id+".jpg"))
                    saved++
                }
                if (frames%10==0) notify("Đã quét "+frames+" khung; "+saved+" đề xuất")
            } finally { frame.recycle() }
        }
        val type=context.contentResolver.getType(uri).orEmpty()
        if (type.startsWith("image/")) {
            context.contentResolver.openInputStream(uri)?.use {
                val opts=BitmapFactory.Options().apply { inSampleSize=2 }
                val frame=BitmapFactory.decodeStream(it,null,opts)
                    ?: error("Không đọc được ảnh")
                scanFrame(frame,null)
            } ?: error("Không mở được ảnh")
        } else {
            val reader=MediaMetadataRetriever()
            try {
                reader.setDataSource(context,uri)
                val duration=reader.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?: error("Video không có thời lượng")
                // Maximum 3 minutes; 1 frame every 2 seconds. Never decode on UI.
                for (timeMs in 0L..min(duration,180_000L) step 2000L) {
                    if (saved>=60) break
                    reader.getFrameAtTime(timeMs*1000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
                        scanFrame(it,timeMs)
                    }
                }
            } finally { reader.release() }
        }
        notify("Hoàn tất: "+frames+" khung, "+saved+
            " phát hiện; chưa có tọa độ GPS đáng tin cậy")
        return saved
    }
}
