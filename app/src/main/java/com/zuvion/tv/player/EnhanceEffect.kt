package com.zuvion.tv.player

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlin.math.roundToInt

/**
 * Realce de imagem (upscaling): vídeos abaixo de 1080p são ampliados na GPU e recebem
 * nitidez adaptativa (no estilo AMD FidelityFX CAS), que reforça bordas sem estourar
 * contraste nem amplificar ruído. Leve: uma única passada de shader por quadro.
 *
 * [strength] 0..1 (suave .. forte).
 */
class EnhanceEffect(private val strength: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        EnhanceShaderProgram(useHdr, strength)
}

private class EnhanceShaderProgram(useHdr: Boolean, private val strength: Float) :
    BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram = try {
        GlProgram(VERTEX, FRAGMENT)
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }
    private var texel = floatArrayOf(0f, 0f)

    init {
        program.setBufferAttribute(
            "aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texel = floatArrayOf(1f / inputWidth.coerceAtLeast(1), 1f / inputHeight.coerceAtLeast(1))
        // Full HD ou maior: só a nitidez, sem ampliar (economiza GPU).
        if (inputHeight <= 0 || inputHeight >= 1000) return Size(inputWidth, inputHeight)
        val scale = minOf(1080f / inputHeight, 2.25f)
        val w = ((inputWidth * scale).roundToInt() / 2) * 2
        val h = ((inputHeight * scale).roundToInt() / 2) * 2
        return Size(w, h)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            program.setFloatsUniform("uTexel", texel)
            program.setFloatUniform("uSharp", strength)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    companion object {
        private const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""

        // Amostragem bilinear (ampliação) + nitidez adaptativa em cruz.
        private const val FRAGMENT = """
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform float uSharp;
varying vec2 vTexSamplingCoord;
void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 c = texture2D(uTexSampler, uv).rgb;
  vec3 n = texture2D(uTexSampler, uv - vec2(0.0, uTexel.y)).rgb;
  vec3 s = texture2D(uTexSampler, uv + vec2(0.0, uTexel.y)).rgb;
  vec3 e = texture2D(uTexSampler, uv + vec2(uTexel.x, 0.0)).rgb;
  vec3 w = texture2D(uTexSampler, uv - vec2(uTexel.x, 0.0)).rgb;
  vec3 mn = min(c, min(min(n, s), min(e, w)));
  vec3 mx = max(c, max(max(n, s), max(e, w)));
  vec3 amp = sqrt(clamp(min(mn, 1.0 - mx) / max(mx, vec3(0.0001)), 0.0, 1.0));
  float peak = mix(-0.11, -0.2, uSharp);
  vec3 wgt = amp * peak;
  vec3 res = (c + (n + s + e + w) * wgt) / (1.0 + 4.0 * wgt);
  gl_FragColor = vec4(clamp(res, 0.0, 1.0), 1.0);
}
"""
    }
}
