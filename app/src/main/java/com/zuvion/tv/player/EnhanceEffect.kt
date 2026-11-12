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
 * Realce de imagem na GPU, pensado para caber até em TVs simples (ex.: Mali-G31):
 *
 * 1. **Limpeza** (só Forte/Máximo, na resolução ORIGINAL do vídeo — barata em SD/HD):
 *    filtro bilateral que alisa blocos de compressão, "mosquitos" e granulado,
 *    preservando bordas de verdade.
 * 2. **Ampliação + nitidez numa passada só**, já na resolução da tela (Full HD ou 4K):
 *    interpolação (bilinear no Suave/Forte, bicúbica Catmull-Rom no Máximo) seguida de
 *    nitidez adaptativa estilo AMD FidelityFX CAS, que reforça contornos sem criar halos.
 *
 * Cores em precisão média (rápidas na GPU de TV); coordenadas em precisão alta.
 */
object Enhance {
    data class Level(val label: String, val denoise: Float, val sharpen: Float, val bicubic: Boolean)

    val levels = linkedMapOf(
        "soft" to Level("Suave", denoise = 0f, sharpen = 0.35f, bicubic = false),
        "strong" to Level("Forte", denoise = 0.45f, sharpen = 0.7f, bicubic = false),
        "max" to Level("Máximo", denoise = 1f, sharpen = 1f, bicubic = true)
    )
    val order = listOf("off", "soft", "strong", "max")

    /** Tamanho de saída da última ampliação (para as estatísticas). */
    @Volatile var lastOutput: Size? = null
    /** Quadros que saíram do realce (mede a fluidez real). */
    val processed = java.util.concurrent.atomic.AtomicLong()

    /** Vale a pena limpar/ampliar com bicúbica: vídeo conhecido e menor que a tela. */
    fun needsUpscale(sourceHeight: Int, targetHeight: Int) = sourceHeight in 1 until (targetHeight * 0.95f).toInt()

    /**
     * Efeitos do nível; [targetHeight] = lado menor da tela física, [sourceHeight] = altura do vídeo
     * (0 = desconhecida). Limpeza e bicúbica só rodam quando o vídeo é menor que a tela: aí elas
     * trabalham na resolução pequena (baratas) e fazem diferença; num vídeo já Full HD fica só a nitidez.
     */
    fun effects(level: String, targetHeight: Int, sourceHeight: Int): List<Effect> {
        val l = levels[level] ?: return emptyList()
        val up = needsUpscale(sourceHeight, targetHeight)
        val list = ArrayList<Effect>(2)
        if (l.denoise > 0f && up) list += ShaderEffect { hdr -> DenoiseProgram(hdr, l.denoise) }
        list += ShaderEffect { hdr -> ScaleSharpenProgram(hdr, targetHeight, l.sharpen, l.bicubic && up) }
        return list
    }

    fun label(level: String) = levels[level]?.label ?: "Desligado"
    fun lower(level: String) = order.getOrElse(order.indexOf(level) - 1) { "off" }
}

private class ShaderEffect(private val make: (Boolean) -> GlShaderProgram) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = make(useHdr)
}

// 3 texturas por etapa: com 1, cada etapa espera a seguinte liberar a textura e a GPU trabalha em série.
private abstract class Pass(useHdr: Boolean, fragment: String) : BaseGlShaderProgram(useHdr, 3) {
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
    open fun uniforms() {}

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
varying highp vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""
    }
}

/** Limpeza: bilateral em cruz (5 amostras), na resolução original. */
private class DenoiseProgram(useHdr: Boolean, private val strength: Float) : Pass(useHdr, """
precision mediump float;
uniform sampler2D uTexSampler;
uniform highp vec2 uTexel;
uniform float uRange;
varying highp vec2 vTexSamplingCoord;
vec3 acc(vec3 c, highp vec2 o, inout float ws) {
  vec3 n = texture2D(uTexSampler, vTexSamplingCoord + o).rgb;
  vec3 d = n - c;
  float w = exp(-dot(d, d) / uRange);
  ws += w;
  return n * w;
}
void main() {
  vec3 c = texture2D(uTexSampler, vTexSamplingCoord).rgb;
  float ws = 1.0;
  vec3 s = c;
  s += acc(c, vec2(uTexel.x, 0.0), ws);
  s += acc(c, vec2(-uTexel.x, 0.0), ws);
  s += acc(c, vec2(0.0, uTexel.y), ws);
  s += acc(c, vec2(0.0, -uTexel.y), ws);
  gl_FragColor = vec4(s / ws, 1.0);
}
""") {
    override fun uniforms() = program.setFloatUniform("uRange", 0.002f + 0.010f * strength)
}

/**
 * Ampliação até a tela + nitidez CAS numa passada só. A nitidez amostra os vizinhos na grade
 * do vídeo de origem (onde estão os detalhes reais), sobre a ampliação bilinear ou bicúbica.
 */
private class ScaleSharpenProgram(
    useHdr: Boolean, private val targetHeight: Int, private val strength: Float, bicubic: Boolean
) : Pass(useHdr, """
precision mediump float;
uniform sampler2D uTexSampler;
uniform highp vec2 uTexel;
uniform float uSharp;
varying highp vec2 vTexSamplingCoord;
vec3 base(highp vec2 uv) {
""" + (if (bicubic) """
  highp vec2 texSize = 1.0 / uTexel;
  highp vec2 sp = uv * texSize;
  highp vec2 t1 = floor(sp - 0.5) + 0.5;
  highp vec2 f = sp - t1;
  highp vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
  highp vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
  highp vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
  highp vec2 w3 = f * f * (-0.5 + 0.5 * f);
  highp vec2 w12 = w1 + w2;
  highp vec2 a = (t1 - 1.0) * uTexel;
  highp vec2 b = (t1 + w2 / w12) * uTexel;
  highp vec2 d = (t1 + 2.0) * uTexel;
  vec3 r = texture2D(uTexSampler, vec2(b.x, a.y)).rgb * (w12.x * w0.y)
         + texture2D(uTexSampler, vec2(a.x, b.y)).rgb * (w0.x * w12.y)
         + texture2D(uTexSampler, b).rgb * (w12.x * w12.y)
         + texture2D(uTexSampler, vec2(d.x, b.y)).rgb * (w3.x * w12.y)
         + texture2D(uTexSampler, vec2(b.x, d.y)).rgb * (w12.x * w3.y);
  float norm = w12.x * w0.y + w0.x * w12.y + w12.x * w12.y + w3.x * w12.y + w12.x * w3.y;
  return clamp(r / norm, 0.0, 1.0);
""" else """
  return texture2D(uTexSampler, uv).rgb;
""") + """
}
void main() {
  highp vec2 uv = vTexSamplingCoord;
  vec3 c = base(uv);
  vec3 n = texture2D(uTexSampler, uv - vec2(0.0, uTexel.y)).rgb;
  vec3 s = texture2D(uTexSampler, uv + vec2(0.0, uTexel.y)).rgb;
  vec3 e = texture2D(uTexSampler, uv + vec2(uTexel.x, 0.0)).rgb;
  vec3 w = texture2D(uTexSampler, uv - vec2(uTexel.x, 0.0)).rgb;
  vec3 mn = min(c, min(min(n, s), min(e, w)));
  vec3 mx = max(c, max(max(n, s), max(e, w)));
  vec3 amp = sqrt(clamp(min(mn, 1.0 - mx) / max(mx, vec3(0.0001)), 0.0, 1.0));
  vec3 wgt = amp * mix(-0.08, -0.2, uSharp);
  gl_FragColor = vec4(clamp((c + (n + s + e + w) * wgt) / (1.0 + 4.0 * wgt), 0.0, 1.0), 1.0);
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
    override fun uniforms() {
        program.setFloatUniform("uSharp", strength)
        Enhance.processed.incrementAndGet()
    }
}
