package io.github.goquati.qr

import io.github.goquati.qr.Helper.panicIfNot
import io.github.goquati.qr.Helper.panicIfNotIn

/**
 * Thrown when the supplied data does not fit any QR Code version. Ways to handle this exception include:
 * <ul>
 *   <li><p>Decrease the error correction level if it was greater than {@code Ecc.LOW}.</p></li>
 *   <li><p>If the advanced {@code encodeSegments()} function with 6 arguments or the
 *     {@code makeSegmentsOptimally()} function was called, then increase the maxVersion argument
 *     if it was less than {@link QrCode#MAX_VERSION}. (This advice does not apply to the other
 *     factory functions because they search all versions up to {@code qr.QrCode.MAX_VERSION}.)</p></li>
 *   <li><p>Split the text data into better or optimal segments in order to reduce the number of
 *     bits required. (See {@link makeSegmentsOptimally(CharSequence,QrCode.Ecc,int,int)
 *     qr.QrSegmentAdvanced.makeSegmentsOptimally()}.)</p></li>
 *   <li><p>Change the text or binary data to be shorter.</p></li>
 *   <li><p>Change the text to fit the character set of a particular segment mode (e.g. alphanumeric).</p></li>
 *   <li><p>Propagate the error upward to the caller/user.</p></li>
 * </ul>
 * @see QrCode#encodeText(CharSequence, QrCode.Ecc)
 * @see QrCode#encodeBinary(byte[], QrCode.Ecc)
 * @see QrCode#encodeSegments(java.util.List, QrCode.Ecc)
 * @see QrCode#encodeSegments(java.util.List, QrCode.Ecc, int, int, int, boolean)
 */
public class DataTooLongException(msg: String) : IllegalArgumentException(msg)

internal object Helper {
    internal fun panicIf(exp: Boolean, msg: String) {
        if (exp)
            error("unexpected error: $msg")
    }

    internal infix fun Int.panicIfNotIn(range: IntRange) {
        if (this !in range)
            error("$this is not in range $range")
    }

    internal infix fun Int.panicIfNot(other: Int) {
        if (this != other)
            error("$this is not $other")
    }
}

internal fun Int.getBit(i: Int) = ((this ushr i) and 1) != 0
internal fun Byte.getBit(i: Int) = toInt().getBit(i)
internal fun Boolean.toInt() = if (this) 1 else 0

/**
 * Appends the specified number of low-order bits of the specified value to this
 * buffer. Requires 0 &#x2264; len &#x2264; 31 and 0 &#x2264; val &lt; 2<sup>len</sup>.
 * @param value the value to append
 * @param len the number of low-order bits in the value to take
 * @throws IllegalArgumentException if the value or number of bits is out of range
 * @throws IllegalStateException if appending the data
 * would make bitLength exceed Integer.MAX_VALUE
 */
internal fun MutableList<Boolean>.appendBits(value: Int, len: Int) {
    len panicIfNotIn 0..31
    value.ushr(len) panicIfNot 0
    val newBits = ((len - 1) downTo 0).map { value.getBit(it) }
    addAll(newBits)
}

internal class QuadraticBoolMatrix(
    val size: Int,
    private val incX: Int = 1,
    private val incY: Int = size,
    private val data: BooleanArray = BooleanArray(size * size)
) {
    fun transpose() = QuadraticBoolMatrix(size = size, incX = incY, incY = incX, data = data)
    operator fun get(x: Int, y: Int) = data[x * incX + y * incY]
    operator fun set(x: Int, y: Int, value: Boolean) {
        data[x * incX + y * incY] = value
    }

    val sequence: Sequence<Pair<Pair<Int, Int>, Boolean>>
        get() = (0..<size).asSequence()
            .flatMap { x -> (0..<size).asSequence().map { y -> x to y } }
            .map { pos -> pos to get(pos.first, pos.second) }

    fun <T> sequence(block: (pos: Pair<Int, Int>, color: Boolean) -> T): Sequence<T> =
        sequence.map { block(it.first, it.second) }
}
