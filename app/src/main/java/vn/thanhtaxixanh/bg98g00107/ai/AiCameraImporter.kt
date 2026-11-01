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

    fun install(uri: Uri, model: Boolean) {
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
                        proposedSpeedKph=null,videoOffsetMs=offset,
                        source=if (offset==null) "70mai_image" else "70mai_video")
                    val evidence=File(dir,"evidence").apply { mkdirs() }
                    val width=min(960,frame.width)
                    val height=max(1,frame.height*width/frame.width)
                    val thumb=Bitmap.createScaledBitmap(frame,width,height,true)
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
