package vn.thanhtaxixanh.bg98g00107.ai

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Explicit user-initiated download. Never bundled in the APK.
 *
 * Attribution: liamxdev/vtsr, 56 Vietnamese traffic sign classes,
 * https://huggingface.co/liamxdev/vtsr (AGPL-3.0).
 *
 * Files are only promoted to active model after their layout and label order
 * have been inspected. No class labels may be guessed from description order.
 */
internal class VtsrModelInstaller(private val modelDir: File) {
    companion object {
        private const val MODEL_URL =
            "https://huggingface.co/liamxdev/vtsr/resolve/main/vtsr_int8.onnx"
        private const val MAPPING_URL =
            "https://huggingface.co/liamxdev/vtsr/resolve/main/label-mapping.json"
    }

    fun install(progress: (String) -> Unit): String {
        modelDir.mkdirs()
        val pendingModel = File(modelDir,"vtsr_int8.onnx.part")
        val pendingMapping = File(modelDir,"vtsr_mapping.part")
        val activeModel=File(modelDir,"traffic_signs.onnx")
        val activeLabels=File(modelDir,"labels.txt")
        try {
            progress("Đang tải VTSR INT8 từ Hugging Face")
            download(MODEL_URL,pendingModel,120_000_000L)
            require(pendingModel.length()>100_000L) { "Mô hình rỗng/không đầy đủ" }
            progress("Đang tải label-mapping.json")
            download(MAPPING_URL,pendingMapping,256_000L)
            val mapping=VtsrLabelMapping.fromJson(pendingMapping.readText(),56)
            progress("Đang xác minh kiến trúc và thứ tự 56 lớp")
            val labels=validateModel(pendingModel,mapping)
            val digest=MessageDigest.getInstance("SHA-256")
                .digest(pendingModel.readBytes()).joinToString("") {
                    "%02x".format(it)
                }
            require(labels.size==56) { "Thiếu 56 nhãn chuẩn từ model" }
            // Labels are from ONNX class-order metadata or indexed mapping,
            // never from sorted sign descriptions.
            val activeMeta=File(modelDir,"vtsr_model_source.txt")
            val oldModel=File(modelDir,"traffic_signs.onnx.previous")
            if (oldModel.exists()) oldModel.delete()
            if (activeModel.exists()) require(activeModel.renameTo(oldModel)) {
                "Không sao lưu được mô hình hiện hành"
            }
            try {
                require(pendingModel.renameTo(activeModel)) {
                    "Không thể kích hoạt mô hình VTSR"
                }
                activeLabels.writeText(labels.joinToString("\n")+"\n")
                activeMeta.writeText(
                    "source=liamxdev/vtsr\nartifact=vtsr_int8.onnx\n"+
                    "license=AGPL-3.0\nsha256="+digest+"\n"+
                    "label_source=ONNX metadata or indexed mapping\n"
                )
                oldModel.delete()
            } catch (e:Exception) {
                activeModel.delete()
                if (oldModel.isFile) oldModel.renameTo(activeModel)
                throw e
            }
            progress("Đã cài VTSR (56 lớp). SHA-256: "+digest.take(16)+"…")
            return digest
        } finally {
            pendingModel.delete()
            pendingMapping.delete()
        }
    }

    private fun validateModel(file: File, mapping: List<String>?): List<String> {
        val env=OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { opts ->
            opts.setIntraOpNumThreads(2)
            env.createSession(file.absolutePath,opts).use { session ->
                val input=session.inputInfo.values.firstOrNull()?.info as? TensorInfo
                    ?: error("Thiếu tensor đầu vào")
                require(input.shape.size==4 && input.shape[1]==3L) {
                    "VTSR ONNX không có input RGB NCHW"
                }
                val output=session.outputInfo.values.firstOrNull()?.info as? TensorInfo
                    ?: error("Thiếu tensor đầu ra")
                val shape=output.shape
                require(shape.size==3 && shape[0]==1L &&
                    (shape[1]==60L || shape[2]==60L)) {
                    "Output phải là YOLO raw 4+56 classes, không hỗ trợ NMS baked-in"
                }
                val names=session.metadata.customMetadata
                val embedded=YoloLabelOrder.fromOnnxMetadata(
                    names["names"] ?: names["metadata"],56
                )
                require(embedded!=null || mapping!=null) {
                    "Model/mapping không có index lớp 0..55; cần labels.txt đúng thứ tự"
                }
                if (embedded!=null && mapping!=null && embedded!=mapping) {
                    // Some mappings describe the same code with Vietnamese names.
                    // An indexed map with a different class order is a hard error.
                    error("ONNX metadata và label-mapping.json không cùng thứ tự lớp")
                }
                return embedded ?: mapping!!
            }
        }
    }

    private fun download(address: String, target: File, limit: Long) {
        var url=URL(address)
        repeat(6) {
            require(url.protocol=="https") { "Chỉ cho phép HTTPS" }
            val con=url.openConnection() as HttpURLConnection
            con.instanceFollowRedirects=false
            con.connectTimeout=12_000
            con.readTimeout=45_000
            con.setRequestProperty("User-Agent","TTX-AICamera/1")
            try {
                val code=con.responseCode
                if (code in 300..399) {
                    val next=con.getHeaderField("Location")
                        ?: error("Server redirect thiếu Location")
                    url=URL(url,next)
                    return@repeat
                }
                require(code==200) { "Tải Hugging Face lỗi HTTP "+code }
                val size=con.contentLengthLong
                require(size<=limit || size<0) { "Mô hình quá lớn" }
                con.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer=ByteArray(64*1024)
                        var sum=0L
                        while (true) {
                            val count=input.read(buffer)
                            if (count<0) break
                            sum+=count
                            require(sum<=limit) { "Vượt giới hạn kích thước" }
                            output.write(buffer,0,count)
                        }
                        require(sum>0) { "Tệp tải rỗng" }
                    }
                }
                return
            } finally { con.disconnect() }
        }
        error("Quá nhiều redirect từ Hugging Face")
    }
}

internal object VtsrLabelMapping {
    fun fromJson(raw: String, expected: Int): List<String>? {
        val doc=JSONObject(raw)
        val container=sequenceOf("names","id_to_label","id2label","labels",
            "classes","class_names")
            .mapNotNull { doc.opt(it) }.firstOrNull() ?: doc
        val entries=Array<String?>(expected) { null }
        when (container) {
            is JSONObject -> {
                val keys=container.keys()
                while (keys.hasNext()) {
                    val key=keys.next()
                    val id=key.toIntOrNull() ?: continue
                    if (id !in 0 until expected) return null
                    entries[id]=readLabel(container.opt(key))
                }
            }
            is JSONArray -> for (i in 0 until container.length()) {
                val item=container.opt(i)
                val index=(item as? JSONObject)?.optInt("id",i) ?: i
                if (index !in 0 until expected) return null
                entries[index]=readLabel(item)
            }
            else -> return null
        }
        val names=entries.map { it?.takeIf(String::isNotBlank) ?: return null }
        return names.takeIf { it.size==expected && it.toSet().size==expected }
    }

    private fun readLabel(value: Any?): String? = when (value) {
        is String -> value.trim().takeIf { it.length in 1..80 }
        is JSONObject -> sequenceOf("code","name","label","class_name")
            .mapNotNull { value.optString(it).takeIf(String::isNotBlank) }
            .firstOrNull()
        else -> null
    }
}
