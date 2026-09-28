package com.jhani.measurear.ar

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Renders detected planes as a subtle teal dot grid. Dots are generated per-fragment inside
 * each plane polygon, brightest around the reticle and fading with distance, so only the
 * surface being aimed at stands out.
 */
class PlaneDotRenderer {

    private var program = 0
    private var positionHandle = 0
    private var mvpHandle = 0
    private var modelHandle = 0
    private var focusHandle = 0
    private var focusEnabledHandle = 0
    private var colorHandle = 0
    private var modelViewHandle = 0
    private var depthTextureHandle = 0
    private var useDepthHandle = 0
    private var viewportHandle = 0
    private var uvOriginHandle = 0
    private var uvAxisUHandle = 0
    private var uvAxisVHandle = 0

    private val modelMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val focus = FloatArray(3)

    private var vertexBuffer: FloatBuffer? = null
    private var vertexBufferCapacity = 0

    companion object {
        private const val TAG = "PlaneDotRenderer"
        private const val FLOAT_SIZE_BYTES = 4

        // Teal accent #1DE9D0
        private val DOT_COLOR = floatArrayOf(0.114f, 0.914f, 0.816f, 1.0f)

        private const val VERTEX_SHADER_CODE = """
            uniform mat4 u_MVP;
            uniform mat4 u_Model;
            uniform mat4 u_ModelView;
            attribute vec3 a_Position;
            varying vec2 v_Local;
            varying vec3 v_World;
            varying float v_ViewDepth;
            void main() {
                v_Local = a_Position.xz;
                v_World = (u_Model * vec4(a_Position, 1.0)).xyz;
                v_ViewDepth = -(u_ModelView * vec4(a_Position, 1.0)).z;
                gl_Position = u_MVP * vec4(a_Position, 1.0);
            }
        """

        // 5 cm grid, 4 mm dots. Brightest within ~35 cm of the reticle, gone by ~1 m.
        // With depth, dots behind real objects (cups, laptops…) are hidden. highp: depth is
        // in millimeters, beyond mediump's exact range.
        private const val FRAGMENT_SHADER_CODE = """
            precision highp float;
            uniform vec3 u_Focus;
            uniform float u_FocusEnabled;
            uniform vec4 u_Color;
            uniform sampler2D u_Depth;
            uniform float u_UseDepth;
            uniform vec2 u_Viewport;
            uniform vec2 u_UvOrigin;
            uniform vec2 u_UvAxisU;
            uniform vec2 u_UvAxisV;
            varying vec2 v_Local;
            varying vec3 v_World;
            varying float v_ViewDepth;

            float realDepthMm() {
                // Screen pixel -> view-normalized (y down) -> depth/camera texture coords
                vec2 view = vec2(gl_FragCoord.x / u_Viewport.x, 1.0 - gl_FragCoord.y / u_Viewport.y);
                vec2 uv = u_UvOrigin + u_UvAxisU * view.x + u_UvAxisV * view.y;
                vec2 bytes = texture2D(u_Depth, uv).ra;
                return bytes.x * 255.0 + bytes.y * 255.0 * 256.0;
            }

            void main() {
                float spacing = 0.05;
                vec2 cell = fract(v_Local / spacing) - 0.5;
                float d = length(cell) * spacing;
                float dotMask = 1.0 - smoothstep(0.0025, 0.004, d);
                float focusFade = 1.0 - smoothstep(0.35, 1.0, distance(v_World, u_Focus));
                float alpha = dotMask * mix(0.18, 0.18 + 0.72 * focusFade, u_FocusEnabled);
                if (alpha < 0.01) discard;

                if (u_UseDepth > 0.5) {
                    float real = realDepthMm();
                    float dotMm = v_ViewDepth * 1000.0;
                    // Allow for depth noise: at least 3 cm, growing to 5% of the distance
                    float tolerance = max(30.0, dotMm * 0.05);
                    // 0 = no depth estimate for this pixel: keep the dot
                    if (real > 0.0) {
                        alpha *= smoothstep(dotMm - tolerance * 1.5, dotMm - tolerance * 0.5, real);
                    }
                    if (alpha < 0.01) discard;
                }
                gl_FragColor = vec4(u_Color.rgb, alpha);
            }
        """
    }

    fun createOnGlThread() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) Log.e(TAG, "Program link failed: " + GLES20.glGetProgramInfoLog(program))
        else Log.i(TAG, "Dot grid shader ready")

        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        mvpHandle = GLES20.glGetUniformLocation(program, "u_MVP")
        modelHandle = GLES20.glGetUniformLocation(program, "u_Model")
        focusHandle = GLES20.glGetUniformLocation(program, "u_Focus")
        focusEnabledHandle = GLES20.glGetUniformLocation(program, "u_FocusEnabled")
        colorHandle = GLES20.glGetUniformLocation(program, "u_Color")
        modelViewHandle = GLES20.glGetUniformLocation(program, "u_ModelView")
        depthTextureHandle = GLES20.glGetUniformLocation(program, "u_Depth")
        useDepthHandle = GLES20.glGetUniformLocation(program, "u_UseDepth")
        viewportHandle = GLES20.glGetUniformLocation(program, "u_Viewport")
        uvOriginHandle = GLES20.glGetUniformLocation(program, "u_UvOrigin")
        uvAxisUHandle = GLES20.glGetUniformLocation(program, "u_UvAxisU")
        uvAxisVHandle = GLES20.glGetUniformLocation(program, "u_UvAxisV")
    }

    /**
     * Draws all tracked, non-subsumed floor/table/wall planes.
     *
     * @param focusPose reticle position to highlight around, or null for a uniform faint grid
     * @param depth depth texture for occlusion, or null to draw over everything
     * @param viewToUv affine map from view-normalized coords to depth texture coords:
     *   [originU, originV, axisUx, axisUy, axisVx, axisVy]
     */
    fun drawPlanes(
        planes: Collection<Plane>,
        focusPose: Pose?,
        projectionMatrix: FloatArray,
        viewMatrix: FloatArray,
        depth: DepthTexture?,
        viewToUv: FloatArray,
        viewportWidth: Int,
        viewportHeight: Int
    ) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glUniform4fv(colorHandle, 1, DOT_COLOR, 0)
        if (focusPose != null) {
            focus[0] = focusPose.tx(); focus[1] = focusPose.ty(); focus[2] = focusPose.tz()
        }
        GLES20.glUniform3fv(focusHandle, 1, focus, 0)
        GLES20.glUniform1f(focusEnabledHandle, if (focusPose != null) 1f else 0f)

        val useDepth = depth != null && depth.hasDepth
        GLES20.glUniform1f(useDepthHandle, if (useDepth) 1f else 0f)
        if (useDepth) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depth!!.textureId)
            GLES20.glUniform1i(depthTextureHandle, 1)
            GLES20.glUniform2f(viewportHandle, viewportWidth.toFloat(), viewportHeight.toFloat())
            GLES20.glUniform2f(uvOriginHandle, viewToUv[0], viewToUv[1])
            GLES20.glUniform2f(uvAxisUHandle, viewToUv[2], viewToUv[3])
            GLES20.glUniform2f(uvAxisVHandle, viewToUv[4], viewToUv[5])
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        }

        for (plane in planes) {
            if (plane.trackingState != TrackingState.TRACKING ||
                plane.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING ||
                plane.subsumedBy != null
            ) {
                continue
            }

            val polygon = plane.polygon
            val numPoints = polygon.remaining() / 2
            if (numPoints < 3) continue

            val required = numPoints * 3
            if (vertexBuffer == null || vertexBufferCapacity < required) {
                vertexBufferCapacity = required * 2
                vertexBuffer = ByteBuffer.allocateDirect(vertexBufferCapacity * FLOAT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
            }
            val buffer = vertexBuffer ?: continue
            buffer.clear()
            polygon.rewind()
            while (polygon.hasRemaining()) {
                buffer.put(polygon.get())
                buffer.put(0f) // polygon is in the plane's local XZ
                buffer.put(polygon.get())
            }
            buffer.position(0)

            plane.centerPose.toMatrix(modelMatrix, 0)
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(modelHandle, 1, false, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(modelViewHandle, 1, false, modelViewMatrix, 0)

            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, buffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, numPoints)
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) Log.e(TAG, "Shader compile failed: " + GLES20.glGetShaderInfoLog(shader))
        return shader
    }
}
