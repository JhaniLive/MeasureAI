package com.jhani.measurear.ar

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Debug view: draws ARCore's feature point cloud (what tracking locks onto) as small dots, and
 * the outline of every detected plane. Shows at a glance why a surface isn't being found.
 */
class PointCloudRenderer {

    private var program = 0
    private var positionHandle = 0
    private var mvpHandle = 0
    private var colorHandle = 0
    private var pointSizeHandle = 0

    private val viewProjection = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvp = FloatArray(16)

    private var outlineBuffer: FloatBuffer? = null
    private var outlineCapacity = 0

    /** Points drawn in the last frame (for the stats panel). */
    var lastPointCount = 0
        private set

    companion object {
        private const val TAG = "PointCloudRenderer"

        // Amber feature points, white plane outlines
        private val POINT_COLOR = floatArrayOf(1.0f, 0.84f, 0.04f, 1.0f)
        private val OUTLINE_COLOR = floatArrayOf(1.0f, 1.0f, 1.0f, 0.9f)

        private const val VERTEX_SHADER_CODE = """
            uniform mat4 u_MVP;
            uniform float u_PointSize;
            attribute vec4 a_Position;
            void main() {
                gl_Position = u_MVP * vec4(a_Position.xyz, 1.0);
                gl_PointSize = u_PointSize;
            }
        """

        private const val FRAGMENT_SHADER_CODE = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                gl_FragColor = u_Color;
            }
        """
    }

    fun createOnGlThread() {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) Log.e(TAG, "Program link failed: " + GLES20.glGetProgramInfoLog(program))

        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        mvpHandle = GLES20.glGetUniformLocation(program, "u_MVP")
        colorHandle = GLES20.glGetUniformLocation(program, "u_Color")
        pointSizeHandle = GLES20.glGetUniformLocation(program, "u_PointSize")
    }

    fun draw(frame: Frame, planes: Collection<Plane>, projectionMatrix: FloatArray, viewMatrix: FloatArray) {
        Matrix.multiplyMM(viewProjection, 0, projectionMatrix, 0, viewMatrix, 0)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionHandle)

        // Feature points: x, y, z, confidence per point, in world space
        frame.acquirePointCloud().use { cloud ->
            val points = cloud.points
            lastPointCount = points.remaining() / 4
            if (lastPointCount > 0) {
                GLES20.glUniformMatrix4fv(mvpHandle, 1, false, viewProjection, 0)
                GLES20.glUniform4fv(colorHandle, 1, POINT_COLOR, 0)
                GLES20.glUniform1f(pointSizeHandle, 7f)
                GLES20.glVertexAttribPointer(positionHandle, 4, GLES20.GL_FLOAT, false, 16, points)
                GLES20.glDrawArrays(GLES20.GL_POINTS, 0, lastPointCount)
            }
        }

        // Plane outlines (polygon is in each plane's local XZ)
        GLES20.glUniform4fv(colorHandle, 1, OUTLINE_COLOR, 0)
        GLES20.glLineWidth(3f)
        for (plane in planes) {
            if (plane.trackingState != TrackingState.TRACKING || plane.subsumedBy != null) continue
            val polygon = plane.polygon
            val count = polygon.remaining() / 2
            if (count < 3) continue
            val needed = count * 4
            if (outlineBuffer == null || outlineCapacity < needed) {
                outlineCapacity = needed * 2
                outlineBuffer = ByteBuffer.allocateDirect(outlineCapacity * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            }
            val buffer = outlineBuffer ?: continue
            buffer.clear()
            polygon.rewind()
            while (polygon.hasRemaining()) {
                buffer.put(polygon.get()).put(0f).put(polygon.get()).put(1f)
            }
            buffer.position(0)

            plane.centerPose.toMatrix(modelMatrix, 0)
            Matrix.multiplyMM(mvp, 0, viewProjection, 0, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
            GLES20.glVertexAttribPointer(positionHandle, 4, GLES20.GL_FLOAT, false, 16, buffer)
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, count)
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) Log.e(TAG, "Shader compile failed: " + GLES20.glGetShaderInfoLog(shader))
        return shader
    }
}
