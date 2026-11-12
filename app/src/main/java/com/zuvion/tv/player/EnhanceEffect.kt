package com.zuvion.tv.player

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.Effect
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlin.math.roundToInt

/**
 * Realce de imagem em até 3 etapas na GPU (uma passada de shader cada):
 *
 * 1. **Limpeza** (resolução original): filtro bilateral que alisa blocos de compressão,
 *    "mosquitos" ao redor das bordas e granulado — diferenças pequenas entre pixels vizinhos —
 *    sem borrar bordas de verdade (diferenças grandes).
 * 2. **Ampliação** até a resolução da tela (1080p numa TV Full HD, 2160p numa TV 4K) com
 *    interpolação bicúbica Catmull-Rom: bordas mais firmes que a ampliação comum (bilinear).
 * 3. **Nitidez adaptativa** (estilo AMD FidelityFX CAS) na resolução final: reforça contornos
 *    onde há pouco contraste e se contém onde já há muito, para não criar halos.
 */
object Enhance {
    data class Level(val label: String, val denoise: Float, val sharpen: Float, val bicubic: Boolean)

    val levels = linkedMapOf(
        "soft" to Level("Suave", denoise = 0f, sharpen = 0.3f, bicubic = true),
        "strong" to Level("Forte", denoise = 0.45f, sharpen = 0.65f, bicubic = true),
        "max" to Level("Máximo", denoise = 1f, sharpen = 1f, bicubic = true)
    )

    /** Tamanho de saída da última ampliação (para as estatísticas). */
    @Volatile var lastOutput: Size? = null

    /** Lista de efeitos do nível escolhido; [targetHeight] = altura da tela (lado menor). */
    fun effects(level: String, targetHeight: Int): List<Effect> {
        val l = levels[level] ?: return emptyList()
        val list = ArrayList<Effect>(3)
        if (l.denoise > 0f) list += ShaderEffect { useHdr -> DenoiseProgram(useHdr, l.denoise) }
        list += ShaderEffect { useHdr -> UpscaleProgram(useHdr, targetHeight) }
        list += ShaderEffect { useHdr -> SharpenProgram(useHdr, l.sharpen) }
        return list
    }

    fun label(level: String) = levels[level]?.label ?: "Desligado"
}

private class ShaderEffect(private val make: (Boolean) -> GlShaderProgram) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = make(useHdr)
}

/** Base: uma passada de shader com amostragem da textura anterior. */
private abstract class Pass(useHdr: Boolean, fragment: String) : BaseGlShaderProgram(useHdr, 1) {
    protected val program: GlProgram = try {
        GlProgram(VERTEX, fragment)
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }
    protected var inW = 1
    protected var inH = 1

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    open fun outSize(w: Int, h: Int) = Size(w, h)
    abstract fun uniforms()

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        inW = inputWidth.coerceAtLeast(1); inH = inputHeight.coerceAtLeast(1)
        return outSize(inW, inH)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uTexel", floatArrayOf(1f / inW, 1f / inH))
            uniforms()
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    companion object {
        const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""
    }
}

/** Etapa 1: bilateral 3×3 (remove blocos/ruído de compressão, preserva bordas). */
private class DenoiseProgram(useHdr: Boolean, private val strength: Float) : Pass(useHdr, """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform float uRange;
varying vec2 vTexSamplingCoord;
void main() {
  vec3 c = texture2D(uTexSampler, vTexSamplingCoord).rgb;
  vec3 sum = c;
  float wsum = 1.0;
  for (int y = -1; y <= 1; y++) {
    for (int x = -1; x <= 1; x++) {
      if (x == 0 && y == 0) continue;
      vec3 n = texture2D(uTexSampler, vTexSamplingCoord + vec2(float(x), float(y)) * uTexel).rgb;
      vec3 d = n - c;
      float spatial = (x == 0 || y == 0) ? 0.88 : 0.61;
      float w = spatial * exp(-dot(d, d) / uRange);
      sum += n * w;
      wsum += w;
    }
  }
  gl_FragColor = vec4(sum / wsum, 1.0);
}
""") {
    // Quanto maior, mais diferença entre vizinhos é tratada como "defeito" (e alisada).
    override fun uniforms() = program.setFloatUniform("uRange", 0.0015f + 0.0085f * strength)
}

/** Etapa 2: ampliação bicúbica Catmull-Rom (9 amostras) até a altura da tela. */
private class UpscaleProgram(useHdr: Boolean, private val targetHeight: Int) : Pass(useHdr, """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexel;
varying vec2 vTexSamplingCoord;
vec3 tap(vec2 p) { return texture2D(uTexSampler, p).rgb; }
void main() {
  vec2 texSize = 1.0 / uTexel;
  vec2 samplePos = vTexSamplingCoord * texSize;
  vec2 t1 = floor(samplePos - 0.5) + 0.5;
  vec2 f = samplePos - t1;
  vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
  vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
  vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
  vec2 w3 = f * f * (-0.5 + 0.5 * f);
  vec2 w12 = w1 + w2;
  vec2 t0 = (t1 - 1.0) * uTexel;
  vec2 t3 = (t1 + 2.0) * uTexel;
  vec2 t12 = (t1 + w2 / w12) * uTexel;
  vec3 r = tap(vec2(t0.x, t0.y)) * w0.x * w0.y + tap(vec2(t12.x, t0.y)) * w12.x * w0.y + tap(vec2(t3.x, t0.y)) * w3.x * w0.y
         + tap(vec2(t0.x, t12.y)) * w0.x * w12.y + tap(vec2(t12.x, t12.y)) * w12.x * w12.y + tap(vec2(t3.x, t12.y)) * w3.x * w12.y
         + tap(vec2(t0.x, t3.y)) * w0.x * w3.y + tap(vec2(t12.x, t3.y)) * w12.x * w3.y + tap(vec2(t3.x, t3.y)) * w3.x * w3.y;
  gl_FragColor = vec4(clamp(r, 0.0, 1.0), 1.0);
}
""") {
    override fun outSize(w: Int, h: Int): Size {
        val out = if (targetHeight <= 0 || h >= targetHeight * 0.95f) Size(w, h) else {
            val scale = minOf(targetHeight.toFloat() / h, 4f)
            Size(((w * scale).roundToInt() / 2) * 2, ((h * scale).roundToInt() / 2) * 2)
        }
        Enhance.lastOutput = out
        return out
    }
    override fun uniforms() {}
}

/** Etapa 3: nitidez adaptativa (CAS) na resolução final. */
private class SharpenProgram(useHdr: Boolean, private val strength: Float) : Pass(useHdr, """
precision highp float;
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
  float peak = mix(-0.08, -0.2, uSharp);
  vec3 wgt = amp * peak;
  vec3 res = (c + (n + s + e + w) * wgt) / (1.0 + 4.0 * wgt);
  gl_FragColor = vec4(clamp(res, 0.0, 1.0), 1.0);
}
""") {
    override fun uniforms() = program.setFloatUniform("uSharp", strength)
}
