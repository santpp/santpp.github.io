package app.rawapp.develop.gl

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.GLES30
import app.rawapp.develop.FilmLook
import app.rawapp.develop.LookParams
import app.rawapp.develop.RawFrame
import app.rawapp.develop.math.ColorTransform
import app.rawapp.develop.math.bakeFilmLut
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

private const val CURVE_SIZE = 1024
private const val CURVE_MIN_STOPS = -12.0
private const val CURVE_MAX_STOPS = 10.0
private const val LUT_SIZE = 33
private const val HALATION_SCALE = 4

/** GL resources and passes of the develop pipeline. Must live on one GL thread. */
internal class GlPipeline(assets: AssetManager) : AutoCloseable {
    private val egl = EglContext()
    private val demosaic: GlProgram
    private val extract: GlProgram
    private val blur: GlProgram
    private val look: GlProgram
    private val vao: Int
    private val rawTexture = createTexture(GLES30.GL_TEXTURE_2D, GLES30.GL_NEAREST)
    private val shadingTexture = createTexture(GLES30.GL_TEXTURE_2D, GLES30.GL_LINEAR)
    private val curveTexture = createTexture(GLES30.GL_TEXTURE_2D, GLES30.GL_LINEAR)
    private val lutTexture = createTexture(GLES30.GL_TEXTURE_3D, GLES30.GL_LINEAR)
    private var targets: Targets? = null
    private var bakedCurveFor: LookParams? = null
    private var bakedLutFor: LookParams? = null

    init {
        val extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS).orEmpty()
        check("GL_EXT_color_buffer_half_float" in extensions || "GL_EXT_color_buffer_float" in extensions) {
            "La GPU no permite renderizar en coma flotante"
        }
        fun source(name: String) = assets.open("shaders/$name").bufferedReader().use { it.readText() }
        val vertex = source("fullscreen.vert")
        demosaic = GlProgram(vertex, source("demosaic.frag"))
        extract = GlProgram(vertex, source("halation_extract.frag"))
        blur = GlProgram(vertex, source("blur.frag"))
        look = GlProgram(vertex, source("look.frag"))
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        vao = ids[0]
    }

    fun render(frame: RawFrame, color: ColorTransform, exposure: Float, params: LookParams, lookEnabled: Boolean): Bitmap {
        val t = targetsFor(frame.width, frame.height)
        uploadRaw(frame)
        uploadShading(frame)
        bakeCurve(params)
        bakeLut(params)
        GLES30.glBindVertexArray(vao)

        runDemosaic(t.scene, frame, color, exposure)
        if (lookEnabled) runHalation(t)
        runLook(t, params, lookEnabled)
        return readBitmap(t.output)
    }

    override fun close() {
        targets?.close()
        GLES30.glDeleteTextures(4, intArrayOf(rawTexture, shadingTexture, curveTexture, lutTexture), 0)
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        listOf(demosaic, extract, blur, look).forEach(GlProgram::close)
        egl.close()
    }

    private fun runDemosaic(target: RenderTarget, frame: RawFrame, color: ColorTransform, exposure: Float) {
        target.bind()
        demosaic.use()
        bindTexture(0, GLES30.GL_TEXTURE_2D, rawTexture, demosaic.uniform("uRaw"))
        bindTexture(1, GLES30.GL_TEXTURE_2D, shadingTexture, demosaic.uniform("uShading"))
        GLES30.glUniform1i(demosaic.uniform("uHasShading"), if (frame.lensShading != null) 1 else 0)
        GLES30.glUniform2i(demosaic.uniform("uRedPos"), frame.cfa.redX, frame.cfa.redY)
        GLES30.glUniform4fv(demosaic.uniform("uBlack"), 1, frame.blackLevels, 0)
        GLES30.glUniform1f(demosaic.uniform("uWhite"), frame.whiteLevel)
        val gains = color.whiteBalanceGains
        GLES30.glUniform3f(demosaic.uniform("uWbGains"), gains.x.toFloat(), gains.y.toFloat(), gains.z.toFloat())
        GLES30.glUniformMatrix3fv(demosaic.uniform("uCameraToWorking"), 1, false, color.cameraToWorking.toGlFloats(), 0)
        GLES30.glUniform1f(demosaic.uniform("uExposure"), exposure)
        drawFullscreen()
    }

    private fun runHalation(t: Targets) {
        t.halationA.bind()
        extract.use()
        bindTexture(0, GLES30.GL_TEXTURE_2D, t.scene.texture, extract.uniform("uScene"))
        GLES30.glUniform1f(extract.uniform("uThreshold"), FilmLook.HALATION_THRESHOLD)
        drawFullscreen()

        blur.use()
        t.halationB.bind()
        bindTexture(0, GLES30.GL_TEXTURE_2D, t.halationA.texture, blur.uniform("uSource"))
        GLES30.glUniform2f(blur.uniform("uDirection"), 1f, 0f)
        drawFullscreen()
        t.halationA.bind()
        bindTexture(0, GLES30.GL_TEXTURE_2D, t.halationB.texture, blur.uniform("uSource"))
        GLES30.glUniform2f(blur.uniform("uDirection"), 0f, 1f)
        drawFullscreen()
    }

    private fun runLook(t: Targets, params: LookParams, lookEnabled: Boolean) {
        t.output.bind()
        look.use()
        bindTexture(0, GLES30.GL_TEXTURE_2D, t.scene.texture, look.uniform("uScene"))
        bindTexture(1, GLES30.GL_TEXTURE_2D, t.halationA.texture, look.uniform("uHalation"))
        bindTexture(2, GLES30.GL_TEXTURE_2D, curveTexture, look.uniform("uToneCurve"))
        bindTexture(3, GLES30.GL_TEXTURE_3D, lutTexture, look.uniform("uLut"))
        GLES30.glUniform2f(look.uniform("uCurveStops"), CURVE_MIN_STOPS.toFloat(), CURVE_MAX_STOPS.toFloat())
        GLES30.glUniform1f(look.uniform("uLutSize"), LUT_SIZE.toFloat())
        GLES30.glUniform1f(look.uniform("uHalationAmount"), params.halation)
        GLES30.glUniform1f(look.uniform("uGrainAmount"), params.grain)
        GLES30.glUniform1f(look.uniform("uVignette"), FilmLook.VIGNETTE)
        GLES30.glUniform1i(look.uniform("uLookEnabled"), if (lookEnabled) 1 else 0)
        drawFullscreen()
    }

    private fun readBitmap(target: RenderTarget): Bitmap {
        target.bind()
        val pixels = ByteBuffer.allocateDirect(target.width * target.height * 4).order(ByteOrder.nativeOrder())
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 4)
        GLES30.glReadPixels(0, 0, target.width, target.height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
        checkGl("lectura del resultado")
        return Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(pixels)
        }
    }

    private fun uploadRaw(frame: RawFrame) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, rawTexture)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 2)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R16UI, frame.width, frame.height, 0,
            GLES30.GL_RED_INTEGER, GLES30.GL_UNSIGNED_SHORT, frame.pixels.rewind(),
        )
        checkGl("subida del RAW")
    }

    private fun uploadShading(frame: RawFrame) {
        val map = frame.lensShading ?: return
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadingTexture)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, map.columns, map.rows, 0,
            GLES30.GL_RGBA, GLES30.GL_FLOAT, FloatBuffer.wrap(map.gains).asDirect(),
        )
    }

    private fun bakeCurve(params: LookParams) {
        if (bakedCurveFor?.contrast == params.contrast) return
        val curve = FilmLook.toneCurve(params).bake(CURVE_SIZE, CURVE_MIN_STOPS, CURVE_MAX_STOPS)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, curveTexture)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R16F, CURVE_SIZE, 1, 0,
            GLES30.GL_RED, GLES30.GL_FLOAT, FloatBuffer.wrap(curve).asDirect(),
        )
        bakedCurveFor = params
    }

    private fun bakeLut(params: LookParams) {
        if (bakedLutFor?.saturation == params.saturation) return
        val lut = bakeFilmLut(LUT_SIZE, FilmLook.color(params))
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTexture)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB16F, LUT_SIZE, LUT_SIZE, LUT_SIZE, 0,
            GLES30.GL_RGB, GLES30.GL_FLOAT, FloatBuffer.wrap(lut).asDirect(),
        )
        bakedLutFor = params
    }

    private fun targetsFor(width: Int, height: Int): Targets {
        targets?.takeIf { it.output.width == width && it.output.height == height }?.let { return it }
        targets?.close()
        return Targets(width, height).also { targets = it }
    }

    private fun bindTexture(unit: Int, target: Int, texture: Int, uniform: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(target, texture)
        GLES30.glUniform1i(uniform, unit)
    }

    private fun drawFullscreen() = GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)

    private class Targets(width: Int, height: Int) : AutoCloseable {
        val scene = RenderTarget(width, height, GLES30.GL_RGBA16F, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT)
        val halationA = halfFloat(width / HALATION_SCALE, height / HALATION_SCALE)
        val halationB = halfFloat(width / HALATION_SCALE, height / HALATION_SCALE)
        val output = RenderTarget(width, height, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE)

        override fun close() = listOf(scene, halationA, halationB, output).forEach(RenderTarget::close)

        private fun halfFloat(w: Int, h: Int) = RenderTarget(w, h, GLES30.GL_RGBA16F, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT)
    }
}

private fun FloatBuffer.asDirect(): FloatBuffer {
    val direct = ByteBuffer.allocateDirect(remaining() * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    direct.put(this).rewind()
    return direct
}

private fun checkGl(step: String) {
    val error = GLES30.glGetError()
    check(error == GLES30.GL_NO_ERROR) { "Error de OpenGL en $step (0x${error.toString(16)})" }
}
