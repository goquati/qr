package io.github.goquati.qr

import kotlin.math.sqrt
import org.intellij.lang.annotations.Language
import kotlin.math.absoluteValue

private data class Pixel(val x: Int, val y: Int)
private data class PixelRectangle(val p0: Pixel, val p1: Pixel) {
    val width = p1.x - p0.x + 1
    val height = p1.y - p0.y + 1
    operator fun contains(p: Pixel) = p.x in p0.x..p1.x && p.y in p0.y..p1.y
}

private fun QrCode.getInnerSvgRectangle(errorCorrectionFactor: Double = 1.7): PixelRectangle {
    val nf = errorCorrectionLevel.restorePercentage / errorCorrectionFactor
    val m = sqrt(size * size * nf).toInt().let {
        it - (it % 2 - size % 2).absoluteValue
    }
    val p0 = (size - m) / 2
    val p1 = p0 + m - 1
    return PixelRectangle(
        p0 = Pixel(p0, p0),
        p1 = Pixel(p1, p1),
    )
}

private class SvgBuilder(
    private val indent: String = "",
) {
    private val stringBuilder = StringBuilder()
    fun addLine(line: String) {
        stringBuilder.appendLine(indent + line)
    }

    fun addBlock(block: SvgBuilder.() -> Unit) {
        val data = SvgBuilder(indent = indent + INDENT).apply(block).build()
        stringBuilder.append(data)
    }

    fun build() = stringBuilder.toString()

    companion object {
        private const val INDENT = "  "
    }
}

private fun buildSvg(block: SvgBuilder.() -> Unit) = SvgBuilder().apply(block).build()

private fun SvgBuilder.addFinderBlocks(qrCode: QrCode, style: QrCodeSvgConfig.StyleFinder) {
    val innerArgs = mapOf(
        "fill" to style.innerColor,
        "rx" to style.innerBorderRadius.toString(),
    ).entries.joinToString(" ") { "${it.key}=\"${it.value}\"" }
    val outerArgs = mapOf(
        "fill" to "none",
        "stroke" to style.outerColor,
        "rx" to style.outerBorderRadius.toString(),
    ).entries.joinToString(" ") { "${it.key}=\"${it.value}\"" }

    addLine("""<rect x="2" y="2" width="3" height="3" $innerArgs/>""")
    addLine("""<rect x="${qrCode.size - 5}" y="2" width="3" height="3" $innerArgs/>""")
    addLine("""<rect x="2" y="${qrCode.size - 5}" width="3" height="3" $innerArgs/>""")

    addLine("""<rect x="0.5" y="0.5" width="6" height="6" $outerArgs/>""")
    addLine("""<rect x="${qrCode.size - 7}.5" y="0.5" width="6" height="6" $outerArgs/>""")
    addLine("""<rect x="0.5" y="${qrCode.size - 7}.5" width="6" height="6" $outerArgs/>""")
}

private fun SvgBuilder.addInnerSvg(innerSvg: QrCodeSvgConfig.InnerSvg, position: PixelRectangle) {
    val bw = position.width * innerSvg.border
    val bh = position.height * innerSvg.border
    val x = position.p0.x + bw
    val y = position.p0.y + bh
    val width = position.width - 2 * bw
    val height = position.height - 2 * bh

    addLine("""<g transform="translate($x, $y)">""")
    addBlock {
        innerSvg.svg
            .replaceFirst("<svg", """<svg width="$width" height="$height"""")
            .lineSequence().forEach { addLine(it) }
    }
    addLine("""</g>""")
}


public class QrCodeSvgConfig(
    public val id: String?,
    public val svgSize: String,
    public val pixelStyle: PixelStyle,
    public val finderStyle: StyleFinder?,
    public val backgroundStyle: BackgroundStyle,
    public val innerSvg: InnerSvg?,
) {
    public val pixelId: String
        get() = pixelStyle.id
            ?: id?.let { "${it}_pixel" }
            ?: pixelStyle.run {
                "pixel_${size}_${borderRadius}_${fill}_${fillOpacity}".replace(Regex("[^a-zA-Z0-9_-]"), "-")
            }

    public class InnerSvg(
        public val svg: String,
        public val border: Double,
    )

    public data class BackgroundStyle(
        public var borderSizePixel: Double = 0.0,
        public var fill: String = "none",
        public var fillOpacity: Double = 1.0,
    )

    public data class PixelStyle(
        public var id: String? = null,
        public var size: Double = 1.0,
        public var borderRadius: Double = 0.0,
        public var fill: String = "black",
        public var fillOpacity: Double = 1.0,
    )

    public data class StyleFinder(
        var innerColor: String = "black",
        var outerColor: String = "black",
        var innerBorderRadius: Double = 0.0,
        var outerBorderRadius: Double = 0.0,
    )

    public class Builder {
        public var id: String? = null
        public var svgSize: String = "100mm"
        private var pixelStyle: PixelStyle = PixelStyle()
        private var finderStyle: StyleFinder? = null
        private var backgroundStyle: BackgroundStyle = BackgroundStyle()
        private var innerSvg: InnerSvg? = null

        public fun pixelStyle(block: PixelStyle.() -> Unit) {
            pixelStyle.apply(block)
        }

        public fun finderStyle(block: StyleFinder.() -> Unit) {
            finderStyle = (finderStyle ?: StyleFinder()).apply(block)
        }

        public fun backgroundStyle(block: BackgroundStyle.() -> Unit) {
            backgroundStyle.apply(block)
        }

        public fun innerSvg(border: Double, @Language("svg") svg: String) {
            innerSvg = InnerSvg(
                border = border,
                svg = svg,
            )
        }

        internal fun build(): QrCodeSvgConfig = QrCodeSvgConfig(
            id = id,
            svgSize = svgSize,
            pixelStyle = pixelStyle,
            finderStyle = finderStyle,
            backgroundStyle = backgroundStyle,
            innerSvg = innerSvg,
        )
    }
}


@Language("svg")
public fun QrCode.toSvg(
    block: QrCodeSvgConfig.Builder.() -> Unit,
): String = toSvg(QrCodeSvgConfig.Builder().apply(block).build())


@Language("svg")
public fun QrCode.toSvg(
    svgConfig: QrCodeSvgConfig,
): String {
    val svgSizePixel = size + 2 * svgConfig.backgroundStyle.borderSizePixel
    val svgOffsetPixel = -svgConfig.backgroundStyle.borderSizePixel
    val imageWithPosition = svgConfig.innerSvg?.let { it to getInnerSvgRectangle() }
    val imagePosition = imageWithPosition?.second

    val svgAttr = listOfNotNull(
        svgConfig.id?.let { "id" to it },
        "xmlns" to "http://www.w3.org/2000/svg",
        "viewBox" to "$svgOffsetPixel $svgOffsetPixel $svgSizePixel $svgSizePixel",
        "width" to svgConfig.svgSize,
        "height" to svgConfig.svgSize,
    ).joinToString(separator = " ") { (k, v) -> """$k="$v"""" }
    return buildSvg {
        addLine("""<svg $svgAttr>""")

        addBlock {
            addLine("""<defs>""")
            addBlock {
                addLine("""<marker id="${svgConfig.pixelId}">""")
                addBlock {
                    svgConfig.pixelStyle.apply {
                        val offset = (1.0 - size) / 2
                        addLine("""<rect x="$offset" y="$offset" width="$size" height="$size" rx="$borderRadius" fill="$fill" fill-opacity="$fillOpacity"/>""")
                    }
                }
                addLine("""</marker>""")
            }
            addLine("""</defs>""")
            svgConfig.backgroundStyle.apply {
                mapOf(
                    "x" to svgOffsetPixel,
                    "y" to svgOffsetPixel,
                    "width" to svgSizePixel,
                    "height" to svgSizePixel,
                    "fill" to fill,
                    "fill-opacity" to fillOpacity,
                ).entries.joinToString(" ") { (k, v) -> """$k="$v"""" }
                    .also { addLine("""<rect $it/>""") }
            }
            if (imageWithPosition != null)
                addInnerSvg(imageWithPosition.first, imageWithPosition.second)
            if (svgConfig.finderStyle != null)
                addFinderBlocks(this@toSvg, svgConfig.finderStyle)

            addLine("""<polyline fill="none" marker-mid="url(#${svgConfig.pixelId})"""")
            addBlock {
                blackPixelSequence(withFinderPixel = svgConfig.finderStyle == null)
                    .map { Pixel(it.first, it.second) }
                    .filter { imagePosition == null || it !in imagePosition }
                    .joinToString(" ") { "${it.x},${it.y}" }
                    .also { addLine("""points="0,0 $it 0,0"""") }
            }
            addLine("""/>""")
        }
        addLine("""</svg>""")
    }
}
