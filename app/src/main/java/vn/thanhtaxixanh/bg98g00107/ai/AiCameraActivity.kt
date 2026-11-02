package vn.thanhtaxixanh.bg98g00107.ai

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import vn.thanhtaxixanh.bg98g00107.camera.LiveCameraController
import vn.thanhtaxixanh.bg98g00107.camera.LiveCameraConfig
import vn.thanhtaxixanh.bg98g00107.camera.RtspWifiDiagnostic
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.*
import java.io.File
import java.util.concurrent.Executors

/** Phone-only evidence review. Never asks the driver to tap while moving. */
class AiCameraActivity : Activity() {
    private val worker=Executors.newSingleThreadExecutor()
    private val store by lazy { AiReviewStore(applicationContext) }
    private val importer by lazy { AiCameraImporter(applicationContext) }
    private lateinit var info: TextView
    private lateinit var rows: LinearLayout
    private lateinit var livePreview: ImageView
    private lateinit var liveStatus: TextView
    private lateinit var streamUrl: EditText
    private lateinit var forceTcp: CheckBox
    private val liveHandler = Handler(Looper.getMainLooper())
    private val liveConsumer = "ai-camera@" + System.identityHashCode(this)
    private val liveTicker = object : Runnable {
        override fun run() {
            updateLivePreview()
            liveHandler.postDelayed(this, 500L)
        }
    }
    private val pad by lazy { (12*resources.displayMetrics.density).toInt() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setPadding(pad,pad,pad,pad)
        }
        body.addView(label("TTX AI Camera · Xác minh biển báo"))
        body.addView(label("70mai A500S Plus: tải video/ảnh bằng ứng dụng 70mai rồi nhập vào đây. " +
            "Chưa có RTSP/API live được xác minh. Video tải về không tự mang GPS gốc."))
        body.addView(label("VTSR YOLOv8 INT8 có thể lấy thứ tự 56 lớp từ metadata ONNX. " +
            "Nếu dùng mô hình khác, hãy nhập labels.txt. AI chỉ đề xuất, không thay thế biển thật."))
        info=label("")
        body.addView(info)

        // Share the headless RTSP decoder with the Android Auto debug tile.
        // This preview is real received video, never a fabricated camera frame.
        body.addView(label("CAMERA TRỰC TIẾP · 70mai / RTSP"))
        livePreview = ImageView(this).apply {
            setBackgroundColor(Color.BLACK)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        body.addView(livePreview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (218 * resources.displayMetrics.density).toInt()
        ))
        liveStatus = label("Camera chưa kết nối")
        body.addView(liveStatus)
        streamUrl = EditText(this).apply {
            setSingleLine(true)
            hint = "rtsp://192.168.0.1/"
            setText(LiveCameraConfig.url(applicationContext))
        }
        body.addView(streamUrl)
        forceTcp = CheckBox(this).apply {
            text = "Truyền RTP qua TCP (khuyên thử trước, bỏ chọn để dùng UDP)"
            isChecked = LiveCameraConfig.forceTcp(applicationContext)
        }
        body.addView(forceTcp)
        body.addView(button("Chẩn đoán đường phát (DESCRIBE / SDP)") {
            val requested = streamUrl.text.toString().trim()
            if (!LiveCameraConfig.isValidRtsp(requested)) {
                Toast.makeText(this, "Hãy nhập URL RTSP hợp lệ",
                    Toast.LENGTH_SHORT).show()
            } else {
                info.text = "Đang kiểm tra các đường dẫn RTSP qua Wi-Fi 70mai..."
                worker.execute {
                    val result = RtspWifiDiagnostic.check(applicationContext, requested)
                    runOnUiThread {
                        info.text = result.details
                        result.playableCandidate?.let { candidate ->
                            // A valid SDP is only a *candidate*, not guaranteed
                            // playable until SETUP/PLAY and decoding succeeds.
                            streamUrl.setText(candidate)
                            streamUrl.setSelection(streamUrl.text.length)
                        }
                    }
                }
            }
        })
        body.addView(button("Kết nối / cập nhật Camera trực tiếp") {
            try {
                LiveCameraController.connect(
                    applicationContext, streamUrl.text.toString(), forceTcp.isChecked
                )
                updateLivePreview()
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "Không thể lưu URL RTSP",
                    Toast.LENGTH_LONG).show()
            }
        })
        body.addView(button("Ngắt Camera trực tiếp") {
            LiveCameraController.disconnect(applicationContext)
            updateLivePreview()
        })
        body.addView(label(
            "Chỉ thử kết nối khi xe đã dừng. RTSP mặc định của profile 70mai G5XX " +
            "chưa xác minh được bước bắt tay và đường phát hình trên A500S Plus. " +
            "Cần kết nối đúng Wi-Fi của camera; 200 OK ở OPTIONS chưa đủ. " +
            "Chạy chẩn đoán DESCRIBE/SDP để tìm ứng viên phát, rồi kết nối. " +
            "Không nhập mật khẩu RTSP để chia sẻ công khai."
        ))
        body.addView(button("Cài VTSR YOLOv8n · 56 biển Việt Nam") {
            AlertDialog.Builder(this)
                .setTitle("Tải mô hình VTSR từ Hugging Face")
                .setMessage("VTSR INT8 từ liamxdev/vtsr, 56 loại biển báo, giấy phép AGPL-3.0. "+
                    "TTX sẽ tải mô hình trực tiếp trên điện thoại sau khi bạn đồng ý; "+
                    "cần kết nối mạng và có thể tốn dữ liệu. Mọi phát hiện vẫn chờ duyệt.")
                .setPositiveButton("Tôi đồng ý và tải") { _,_ ->
                    worker.execute {
                        try {
                            importer.installVtsr { update(it) }
                            update("Đã chuẩn bị VTSR; thử ảnh 70mai để kiểm tra.")
                        } catch (e: Exception) {
                            update("VTSR chưa cài thành công: " + e.message)
                        }
                    }
                }.setNegativeButton("Hủy",null).show()
        })
        body.addView(button("Nhập mô hình YOLOv8 ONNX") { pick(1,"*/*") })
        body.addView(button("Nhập labels.txt (mô hình tùy chỉnh)") { pick(2,"text/*") })
        body.addView(button("Kiểm tra kết nối Wi-Fi 70mai A500S Plus (RTSP)") {
            worker.execute {
                update("Đang kiểm tra RTSP 70mai qua mạng Wi-Fi đã kết nối…")
                update(SeventyMaiWifiProbe.probe(applicationContext))
            }
        })
        body.addView(button("Mở xADAS để cấu hình nguồn 70mai / Chia sẻ dữ liệu Pro") {
            val packageName="com.kaibatech.drivedeck.xadas"
            val launch=packageManager.getLaunchIntentForPackage(packageName)
            if (launch!=null) {
                startActivity(launch)
            } else {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id="+packageName)))
            }
        })
        body.addView(label("xADAS 0.2.5 có thông báo chia sẻ khoảng cách/cảnh báo với app khác ở "+
            "bản Pro; TTX chưa nhận trực tiếp luồng video hoặc dữ liệu khi chưa có tài liệu API."))
        body.addView(button("Phân tích ảnh/video đã tải từ 70mai") {
            pick(3,"*/*",arrayOf("image/*","video/*"))
        })
        body.addView(button("Thêm ghi nhận số làn/loại đường thủ công") {
            val entry=EditText(this).apply { hint="VD: đường đô thị hai làn" }
            AlertDialog.Builder(this).setTitle("Chưa xác minh").setView(entry)
                .setMessage("Số làn không thể xác định giới hạn tốc độ pháp lý.")
                .setPositiveButton("Đưa vào hàng chờ") { _,_ ->
                    val label=entry.text.toString().trim().take(100)
                    if (label.isNotBlank()) {
                        store.add(AiObservation(kind=AiEvidenceKind.ROAD_CLASS_SUGGESTION,
                            label=label,source="user_observation"))
                        refresh()
                    }
                }.setNegativeButton("Hủy",null).show()
        })
        body.addView(button("Xuất lớp GeoJSON đã duyệt") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type="application/geo+json"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE,"ttx_ai_reviewed.geojson")
            },4)
        })
        body.addView(label("Hàng chờ phê duyệt (chỉ thao tác khi dừng xe)"))
        rows=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        body.addView(rows)
        setContentView(ScrollView(this).apply { addView(body) })
        refresh()
    }
    override fun onStart() {
        super.onStart()
        LiveCameraController.attach(applicationContext, liveConsumer) {
            updateLivePreview()
        }
        updateLivePreview()
        liveHandler.post(liveTicker)
    }

    override fun onStop() {
        liveHandler.removeCallbacks(liveTicker)
        LiveCameraController.detach(liveConsumer)
        super.onStop()
    }

    private fun updateLivePreview() {
        if (!::livePreview.isInitialized) return
        val snapshot = LiveCameraController.snapshot()
        livePreview.setImageBitmap(snapshot.bitmap)
        liveStatus.text = snapshot.status
    }

    private fun label(value:String)=TextView(this).apply {
        text=value; textSize=15f; setPadding(0,pad/2,0,pad/2)
        setTextColor(android.graphics.Color.rgb(24,38,52))
    }
    private fun button(value:String, click:()->Unit)=Button(this).apply {
        text=value;isAllCaps=false;setOnClickListener { click() }
    }
    private fun pick(request:Int,type:String,filter:Array<String>?=null) {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            this.type=type
            addCategory(Intent.CATEGORY_OPENABLE)
            if (filter!=null) putExtra(Intent.EXTRA_MIME_TYPES,filter)
        },request)
    }
    @Suppress("DEPRECATION")
    override fun onActivityResult(code:Int,result:Int,intent:Intent?) {
        super.onActivityResult(code,result,intent)
        val uri=if(result==RESULT_OK) intent?.data else null
        if (uri==null) return
        worker.execute {
            try {
                when(code) {
                    1 -> { importer.install(uri,true); update("Đã nhập model; tiếp tục nhập labels.txt") }
                    2 -> { importer.install(uri,false); update("Labels đã nhập") }
                    3 -> importer.scan(uri) { update(it) }
                    4 -> {
                        contentResolver.openOutputStream(uri,"w")?.use {
                            it.write(store.approvedGeoJson().toByteArray())
                        } ?: error("Không tạo được tệp GeoJSON")
                        update("Đã xuất lớp đã duyệt")
                    }
                }
            } catch (e:Exception) { update("Lỗi: "+e.message) }
        }
    }
    private fun update(text:String) {
        runOnUiThread { info.text=text; refresh() }
    }
    private fun refresh() {
        if (!::rows.isInitialized) return
        rows.removeAllViews()
        val all=store.list()
        if (all.isEmpty()) rows.addView(label("Không có đề xuất."))
        for (item in all.take(100)) {
            rows.addView(label(item.label + " · "+ item.reviewStatus.name+
                " · " + (item.proposedSpeedKph?.toString() ?: "chưa đọc số") + " km/h"))
            if (item.reviewStatus==AiReviewStatus.PENDING)
                rows.addView(button("Xem bằng chứng / chọn số / duyệt") { review(item) })
        }
    }
    private fun review(item:AiObservation) {
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL
            setPadding(pad,pad/2,pad,pad/2) }
        item.evidenceFile?.let {
            val file=File(filesDir,"ai_camera/evidence/"+it)
            if (file.isFile) BitmapFactory.decodeFile(file.path)?.let { bitmap ->
                form.addView(ImageView(this).apply {
                    setImageBitmap(bitmap);adjustViewBounds=true
                    maxHeight=(300*resources.displayMetrics.density).toInt()
                })
            }
        }
        form.addView(label("Chỉ nhập tọa độ đã kiểm chứng từ GPS gốc, chuyến đi hoặc bản đồ. "+
            "Chưa có way ID thì chỉ lưu điểm, KHÔNG áp toàn bộ đoạn đường."))
        fun field(hint:String,current:String):EditText {
            return EditText(this).apply {
                this.hint=hint;setSingleLine(true);setText(current);form.addView(this)
            }
        }
        val lat=field("Vĩ độ của biển",item.lat?.toString().orEmpty())
        val lon=field("Kinh độ của biển",item.lon?.toString().orEmpty())
        val way=field("OSM way ID (tùy chọn, cần xác minh)",
            item.roadWayId?.toString().orEmpty())
        val values=listOf("Không xác định")+
            AiObservationRules.SPEEDS.map { it.toString()+" km/h" }
        val spinner=Spinner(this).apply {
            adapter=ArrayAdapter(this@AiCameraActivity,
                android.R.layout.simple_spinner_dropdown_item,values)
            setSelection(AiObservationRules.SPEEDS.indexOf(item.proposedSpeedKph)+1)
        }
        form.addView(spinner)
        AlertDialog.Builder(this).setTitle("Phê duyệt bằng chứng")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Duyệt cục bộ") { _,_ ->
                val speed=if(spinner.selectedItemPosition==0) null else
                    AiObservationRules.SPEEDS[spinner.selectedItemPosition-1]
                val wayString=way.text.toString().trim()
                val parsedWay=wayString.toLongOrNull()
                val approved=if(wayString.isNotBlank()&&parsedWay==null) null else
                    store.review(item.id,true,speed,
                        lat.text.toString().trim().toDoubleOrNull(),
                        lon.text.toString().trim().toDoubleOrNull(),parsedWay)
                update(if(approved!=null) "Đã duyệt trong dữ liệu cá nhân" else
                    "Chưa duyệt: thiếu tọa độ/ID hoặc giá trị không hợp lệ")
            }.setNegativeButton("Từ chối") { _,_ ->
                store.review(item.id,false,null,null,null,null)
                refresh()
            }.setNeutralButton("Để sau",null).show()
    }
    override fun onDestroy() {
        worker.execute { importer.close() }
        worker.shutdown()
        super.onDestroy()
    }
}
