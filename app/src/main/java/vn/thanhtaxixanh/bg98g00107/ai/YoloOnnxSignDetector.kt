package vn.thanhtaxixanh.bg98g00107.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Offline YOLOv8 raw ONNX adapter. The user supplies a correctly licensed,
 * Vietnam-trained ONNX model + class-order labels.txt. We deliberately do
 * NOT download unverified weights or claim generic COCO detects road signs.
 *
 * Model: one float32 RGB NCHW input and raw [1,4+classes,N] output, no NMS.
 */
internal class YoloOnnxSignDetector(private val modelDir: File) {
    private val model = File(modelDir, "traffic_signs.onnx")
    private val labelsFile = File(modelDir, "labels.txt")
    // Reuse one ONNX session across sampled frames instead of reloading per frame.
    private val env = OrtEnvironment.getEnvironment()
    private var activeSession: OrtSession? = null
    private var activeOptions: OrtSession.SessionOptions? = null

    fun isReady() = model.isFile && model.length() > 100_000L
    fun resetModel() {
        activeSession?.close()
        activeSession = null
        activeOptions?.close()
        activeOptions = null
    }
    fun close() = resetModel()

    private fun session(): OrtSession {
        activeSession?.let { return it }
        val options = OrtSession.SessionOptions()
        return try {
            options.setIntraOpNumThreads(2)
            env.createSession(model.absolutePath,options).also {
                activeOptions=options
                activeSession=it
            }
        } catch (e: Exception) {
            options.close()
            throw e
        }
    }

    fun detect(bitmap: Bitmap): List<AiDetection> {
        require(isReady()) { "Chưa nhập mô hình YOLOv8/VTSR ONNX" }
        val session = session()
        run {
                val inputName = session.inputNames.first()
                val inputShape = (session.inputInfo[inputName]?.info as? TensorInfo)?.shape
                    ?: error("Không đọc được shape đầu vào")
                require(inputShape.size == 4 && inputShape[1] == 3L) {
                    "Cần model RGB NCHW [1,3,S,S], không hỗ trợ NHWC"
                }
                val size = inputShape[2].toInt().takeIf { it in 320..1280 } ?: 640
                require(inputShape[3] == inputShape[2] || inputShape[3] < 0) {
                    "Chỉ hỗ trợ đầu vào vuông"
                }
                val scaled = letterbox(bitmap, size)
                try {
                    val floats = ByteBuffer.allocateDirect(4 * 3 * size * size)
                        .order(ByteOrder.nativeOrder()).asFloatBuffer()
                    val pixels = IntArray(size * size)
                    scaled.getPixels(pixels,0,size,0,0,size,size)
                    for (channel in 0..2) {
                        for (pixel in pixels) {
                            val v = when(channel) {
                                0 -> pixel shr 16 and 255
                                1 -> pixel shr 8 and 255
                                else -> pixel and 255
                            }
                            floats.put(v / 255f)
                        }
                    }
                    floats.rewind()
                    OnnxTensor.createTensor(
                        env, floats, longArrayOf(1,3,size.toLong(),size.toLong())
                    ).use { tensor ->
                        session.run(mapOf(inputName to tensor)).use { result ->
                            val output = result[0] as? OnnxTensor
                                ?: error("Đầu ra YOLO không phải Tensor")
                            val shape = (output.info as TensorInfo).shape
                            require(shape.size == 3 && shape[0] == 1L) {
                                "Cần output YOLOv8 [1,4+C,N]"
                            }
                            val rows = shape[1].toInt()
                            val columns = shape[2].toInt()
                            require(rows > 0 && columns > 0 &&
                                rows.toLong()*columns <= 5_000_000) {
                                "Output YOLO quá lớn/không hợp lệ"
                            }
                            val buffer = output.floatBuffer
                                ?: error("Model phải xuất float32 (raw YOLOv8)")
                            val values = FloatArray(rows*columns)
                            buffer.get(values)
                            val channelCount = when {
                                rows in 6..516 && columns > rows -> rows
                                columns in 6..516 && rows > columns -> columns
                                else -> error("Model không có output YOLOv8 raw phù hợp")
                            }
                            val expected=channelCount-4
                            val metadata=session.metadata.customMetadata
                            val labelsFromModel=YoloLabelOrder.fromOnnxMetadata(
                                metadata["names"] ?: metadata["metadata"], expected)
                            val labelsFromFile=labelsFile.takeIf { it.isFile }?.let {
                                YoloLabelOrder.fromLabelsFile(it.readText(), expected)
                            }
                            val labels=labelsFromModel ?: labelsFromFile
                                ?: error("Thiếu thứ tự "+expected+" nhãn: " +
                                    "ONNX phải có metadata 'names', hoặc nhập labels.txt")
                            require(labelsFromModel==null || labelsFromFile==null ||
                                labelsFromModel==labelsFromFile) {
                                "Nhãn labels.txt không khớp thứ tự ghi trong ONNX"
                            }
                            return YoloV8Postprocess.decode(
                                values,rows,columns,labels,size
                            )
                        }
                    }
                } finally { scaled.recycle() }
        }
    }

    private fun letterbox(bitmap: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(114,114,114))
        val scale = size.toFloat() / max(bitmap.width,bitmap.height)
        val width = bitmap.width*scale
        val height = bitmap.height*scale
        val left = (size-width)/2f
        val top = (size-height)/2f
        canvas.drawBitmap(
            bitmap,null,android.graphics.RectF(left,top,left+width,top+height),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        return out
    }
}
