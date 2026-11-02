package app.rawapp.develop.gl

import android.opengl.GLES30

/** A texture with its framebuffer, used as the output of one render pass. */
class RenderTarget(val width: Int, val height: Int, internalFormat: Int, format: Int, type: Int) : AutoCloseable {
    val texture: Int = createTexture(GLES30.GL_TEXTURE_2D, GLES30.GL_LINEAR)
    private val framebuffer: Int

    init {
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, null)
        val ids = IntArray(1)
        GLES30.glGenFramebuffers(1, ids, 0)
        framebuffer = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0)
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        check(status == GLES30.GL_FRAMEBUFFER_COMPLETE) { "Framebuffer incompleto ($status)" }
    }

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glViewport(0, 0, width, height)
    }

    override fun close() {
        GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
    }
}

/** Creates and binds a texture with clamp-to-edge wrapping and the given filter. */
fun createTexture(target: Int, filter: Int): Int {
    val ids = IntArray(1)
    GLES30.glGenTextures(1, ids, 0)
    GLES30.glBindTexture(target, ids[0])
    GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, filter)
    GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, filter)
    GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
    GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
    return ids[0]
}
