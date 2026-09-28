package com.jhani.measurear.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Renders the ARCore camera background texture onto an OpenGL ES 2.0 surface.
 */
class BackgroundRenderer {

    var textureId: Int = -1
        private set

    private var program: Int = 0
    private var aPositionHandle: Int = 0
    private var aTexCoordHandle: Int = 0
    private var uTextureHandle: Int = 0
    private var uMaskCenterHandle: Int = 0
    private var uMaskRadiusHandle: Int = 0

    private val loupeViewCoords = FloatArray(8)
    private val loupeTexCoords = FloatArray(8)
    private val loupeTexCoordsBuffer: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val quadCoordsBuffer: FloatBuffer
    private val quadTexCoordsBuffer: FloatBuffer
    private val transformedTexCoordsBuffer: FloatBuffer

    companion object {
        private const val COORDS_PER_VERTEX = 2
        private const val FLOAT_SIZE = 4

        // Full screen quad in normalized device coordinates (-1 to 1)
        private val QUAD_COORDS = floatArrayOf(
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
        )

        // Initial default texture coordinates (0 to 1)
        private val QUAD_TEX_COORDS = floatArrayOf(
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
        )

        private const val VERTEX_SHADER_CODE = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        private const val FRAGMENT_SHADER_CODE = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES u_Texture;
            uniform vec2 u_MaskCenter;
            uniform float u_MaskRadius;
            void main() {
                // Circular mask for the magnifier; disabled when the radius is 0
                if (u_MaskRadius > 0.0 && distance(gl_FragCoord.xy, u_MaskCenter) > u_MaskRadius) discard;
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """
    }

    init {
        quadCoordsBuffer = ByteBuffer.allocateDirect(QUAD_COORDS.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(QUAD_COORDS)
        quadCoordsBuffer.position(0)

        quadTexCoordsBuffer = ByteBuffer.allocateDirect(QUAD_TEX_COORDS.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(QUAD_TEX_COORDS)
        quadTexCoordsBuffer.position(0)

        transformedTexCoordsBuffer = ByteBuffer.allocateDirect(QUAD_TEX_COORDS.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        transformedTexCoordsBuffer.position(0)
    }

    /**
     * Initializes OpenGL program and creates the external OES camera texture.
     */
    fun createOnGlThread() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        GLES20.glUseProgram(program)

        aPositionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        aTexCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
        uTextureHandle = GLES20.glGetUniformLocation(program, "u_Texture")
        uMaskCenterHandle = GLES20.glGetUniformLocation(program, "u_MaskCenter")
        uMaskRadiusHandle = GLES20.glGetUniformLocation(program, "u_MaskRadius")
    }

    /**
     * Draws the AR camera background for the current frame.
     */
    fun draw(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                Coordinates2d.VIEW_NORMALIZED,
                quadTexCoordsBuffer,
                Coordinates2d.TEXTURE_NORMALIZED,
                transformedTexCoordsBuffer
            )
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)

        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(uTextureHandle, 0)
        GLES20.glUniform1f(uMaskRadiusHandle, 0f)

        drawQuad(transformedTexCoordsBuffer)
    }

    /**
     * Draws a circular, magnified copy of the camera image around the screen center.
     *
     * @param centerX / centerY loupe center in view pixels (top-left origin)
     * @param radius loupe radius in pixels
     * @param zoom magnification relative to the full-screen preview
     */
    fun drawLoupe(
        frame: Frame,
        centerX: Float,
        centerY: Float,
        radius: Float,
        zoom: Float,
        viewportWidth: Int,
        viewportHeight: Int
    ) {
        if (viewportWidth == 0 || viewportHeight == 0) return

        // Screen-center sub-rectangle in view-normalized coords, same corner order as the quad
        val hw = radius / zoom / viewportWidth
        val hh = radius / zoom / viewportHeight
        val u0 = 0.5f - hw
        val u1 = 0.5f + hw
        val v0 = 0.5f - hh
        val v1 = 0.5f + hh
        loupeViewCoords[0] = u0; loupeViewCoords[1] = v1
        loupeViewCoords[2] = u1; loupeViewCoords[3] = v1
        loupeViewCoords[4] = u0; loupeViewCoords[5] = v0
        loupeViewCoords[6] = u1; loupeViewCoords[7] = v0
        frame.transformCoordinates2d(
            Coordinates2d.VIEW_NORMALIZED,
            loupeViewCoords,
            Coordinates2d.TEXTURE_NORMALIZED,
            loupeTexCoords
        )
        loupeTexCoordsBuffer.clear()
        loupeTexCoordsBuffer.put(loupeTexCoords)
        loupeTexCoordsBuffer.position(0)

        // GL viewport origin is bottom-left
        val glCenterY = viewportHeight - centerY
        GLES20.glViewport(
            (centerX - radius).toInt(),
            (glCenterY - radius).toInt(),
            (radius * 2).toInt(),
            (radius * 2).toInt()
        )

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(uTextureHandle, 0)
        GLES20.glUniform2f(uMaskCenterHandle, centerX, glCenterY)
        GLES20.glUniform1f(uMaskRadiusHandle, radius)

        drawQuad(loupeTexCoordsBuffer)

        GLES20.glUniform1f(uMaskRadiusHandle, 0f)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun drawQuad(texCoords: FloatBuffer) {
        GLES20.glDepthMask(false)
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(
            aPositionHandle,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            quadCoordsBuffer
        )

        GLES20.glEnableVertexAttribArray(aTexCoordHandle)
        GLES20.glVertexAttribPointer(
            aTexCoordHandle,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            texCoords
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aTexCoordHandle)

        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }
}
