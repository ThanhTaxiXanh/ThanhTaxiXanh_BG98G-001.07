package vn.thanhtaxixanh.bg98g00107.camera

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Headless RTSP decoder output: ExoPlayer writes into a SurfaceTexture,
 * a dedicated EGL thread samples the external OES texture into a small bitmap.
 * It works without any phone Activity being visible, so the Android Auto
 * debug renderer and the phone preview can consume the same frames.
 */
internal class CameraFrameSink(
    private val onFrame: (Bitmap) -> Unit,
    private val onFailure: (String) -> Unit
) {
    companion object {
        const val WIDTH = 480
        const val HEIGHT = 270
        private const val CAPTURE_INTERVAL_MS = 500L
        private val VERTICES = floatArrayOf(
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
        )
        private const val VERTEX_SHADER =
            "attribute vec2 aPosition; attribute vec2 aTexCoord;" +
            "uniform mat4 uTextureMatrix; varying vec2 vTexCoord;" +
            "void main() { gl_Position = vec4(aPosition,0.0,1.0);" +
            "vTexCoord = (uTextureMatrix * vec4(aTexCoord,0.0,1.0)).xy; }"
        private const val FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float; varying vec2 vTexCoord;" +
            "uniform samplerExternalOES uCamera;" +
            "void main() { gl_FragColor = texture2D(uCamera, vTexCoord); }"
    }

    private val thread = HandlerThread("ttx-camera-egl").apply { start() }
    private val handler = Handler(thread.looper)
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var input: SurfaceTexture? = null
    private var videoSurface: Surface? = null
    private var textureId = 0
    private var program = 0
    private var lastCaptureMs = 0L
    private val texMatrix = FloatArray(16)
    private val vertices: FloatBuffer = ByteBuffer
        .allocateDirect(VERTICES.size * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply { put(VERTICES); position(0) }
    private val pixels = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4)
        .order(ByteOrder.nativeOrder())
    private val flippedPixels = ByteArray(WIDTH * HEIGHT * 4)

    fun prepare(onReady: (Surface) -> Unit) {
        handler.post {
            try {
                setup()
                videoSurface?.let(onReady)
            } catch (_: Exception) {
                onFailure("Không tạo được bộ giải mã hình ảnh OpenGL.")
                clean()
            }
        }
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "Không tạo được shader." }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] == GLES20.GL_TRUE) { "Shader không hợp lệ." }
        return shader
    }

    private fun setup() {
        val d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(d != EGL14.EGL_NO_DISPLAY)
        display = d
        check(EGL14.eglInitialize(d, IntArray(2), 0, IntArray(2), 0))
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val choices = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        check(EGL14.eglChooseConfig(d, choices, 0, configs, 0, 1, count, 0))
        check(count[0] > 0)
        val config = checkNotNull(configs[0])
        context = EGL14.eglCreateContext(d, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT)
        eglSurface = EGL14.eglCreatePbufferSurface(d, config, intArrayOf(
            EGL14.EGL_WIDTH, WIDTH, EGL14.EGL_HEIGHT, HEIGHT, EGL14.EGL_NONE
        ), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(d, eglSurface, eglSurface, context))

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
        check(textureId != 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val v = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, v)
        GLES20.glAttachShader(program, f)
        GLES20.glLinkProgram(program)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0)
        GLES20.glDeleteShader(v)
        GLES20.glDeleteShader(f)
        check(ok[0] == GLES20.GL_TRUE) { "Không liên kết được shader." }

        input = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(WIDTH, HEIGHT)
            setOnFrameAvailableListener({ onImageAvailable() }, handler)
        }
        videoSurface = Surface(checkNotNull(input))
    }

    private fun onImageAvailable() {
        val texture = input ?: return
        try {
            // Every incoming frame must be consumed; readback is throttled.
            texture.updateTexImage()
            val now = SystemClock.elapsedRealtime()
            if (now - lastCaptureMs < CAPTURE_INTERVAL_MS) return
            lastCaptureMs = now
            texture.getTransformMatrix(texMatrix)
            GLES20.glViewport(0, 0, WIDTH, HEIGHT)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uCamera"), 0)
            GLES20.glUniformMatrix4fv(
                GLES20.glGetUniformLocation(program, "uTextureMatrix"),
                1, false, texMatrix, 0
            )
            val position = GLES20.glGetAttribLocation(program, "aPosition")
            val uv = GLES20.glGetAttribLocation(program, "aTexCoord")
            vertices.position(0)
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, vertices)
            vertices.position(2)
            GLES20.glEnableVertexAttribArray(uv)
            GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, vertices)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(position)
            GLES20.glDisableVertexAttribArray(uv)

            pixels.clear()
            GLES20.glReadPixels(0, 0, WIDTH, HEIGHT,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
            check(GLES20.glGetError() == GLES20.GL_NO_ERROR)
            // OpenGL's origin is bottom-left; Android bitmap starts top-left.
            val stride = WIDTH * 4
            for (row in 0 until HEIGHT) {
                pixels.position((HEIGHT - 1 - row) * stride)
                pixels.get(flippedPixels, row * stride, stride)
            }
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(flippedPixels))
            onFrame(bitmap)
        } catch (_: Exception) {
            onFailure("Không lấy được khung hình Camera qua OpenGL.")
        }
    }

    fun close() {
        handler.post {
            clean()
            thread.quitSafely()
        }
    }

    private fun clean() {
        input?.setOnFrameAvailableListener(null)
        input?.release()
        input = null
        videoSurface?.release()
        videoSurface = null
        if (display != EGL14.EGL_NO_DISPLAY) {
            if (context != EGL14.EGL_NO_CONTEXT) {
                GLES20.glUseProgram(0)
                if (program != 0) GLES20.glDeleteProgram(program)
                if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                context = EGL14.EGL_NO_CONTEXT
            }
            if (eglSurface != EGL14.EGL_NO_SURFACE)
                EGL14.eglDestroySurface(display, eglSurface)
            EGL14.eglTerminate(display)
        }
        eglSurface = EGL14.EGL_NO_SURFACE
        display = EGL14.EGL_NO_DISPLAY
    }
}
