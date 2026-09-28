package com.jhani.measurear.ar

import android.opengl.GLES20
import android.util.Log
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Uploads ARCore's per-frame depth image (16-bit millimeters) to a GL texture so shaders can
 * hide virtual content behind real objects.
 *
 * GLES 2.0 has no 16-bit single-channel format, so each pixel is uploaded as LUMINANCE_ALPHA:
 * L = low byte, A = high byte. Shaders rebuild millimeters as `L*255 + A*255*256`. Sampling
 * must be NEAREST, since interpolating the two bytes separately would corrupt the value.
 * The depth image shares the camera texture's coordinates.
 */
class DepthTexture {

    var textureId: Int = 0
        private set

    /** True once at least one depth image has been uploaded. */
    var hasDepth: Boolean = false
        private set

    private var width = 0
    private var height = 0
    private var packed: ByteBuffer? = null

    fun createOnGlThread() {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        hasDepth = false
    }

    /**
     * Uploads this frame's depth image, if ARCore has one yet. Call on the GL thread.
     */
    fun update(frame: Frame) {
        try {
            frame.acquireDepthImage16Bits().use { image ->
                val plane = image.planes[0]
                val w = image.width
                val h = image.height
                val rowBytes = w * 2
                val source = plane.buffer.order(ByteOrder.nativeOrder())

                // Repack rows if the image has padding between them
                val data: ByteBuffer = if (plane.rowStride == rowBytes) {
                    source
                } else {
                    val buffer = packed?.takeIf { it.capacity() >= rowBytes * h }
                        ?: ByteBuffer.allocateDirect(rowBytes * h).also { packed = it }
                    buffer.clear()
                    val row = ByteArray(rowBytes)
                    for (y in 0 until h) {
                        source.position(y * plane.rowStride)
                        source.get(row, 0, rowBytes)
                        buffer.put(row)
                    }
                    buffer.flip()
                    buffer
                }

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 2)
                if (w != width || h != height) {
                    width = w
                    height = h
                    GLES20.glTexImage2D(
                        GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE_ALPHA, w, h, 0,
                        GLES20.GL_LUMINANCE_ALPHA, GLES20.GL_UNSIGNED_BYTE, data
                    )
                } else {
                    GLES20.glTexSubImage2D(
                        GLES20.GL_TEXTURE_2D, 0, 0, 0, w, h,
                        GLES20.GL_LUMINANCE_ALPHA, GLES20.GL_UNSIGNED_BYTE, data
                    )
                }
                GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
                hasDepth = true
            }
        } catch (_: NotYetAvailableException) {
            // Depth needs a moment of camera motion before the first image
        } catch (e: IllegalStateException) {
            Log.w("DepthTexture", "Depth image unavailable", e)
        }
    }
}
