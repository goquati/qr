package io.github.goquati.qr

import io.github.goquati.qr.QrSegment.Companion.modeBits
import io.github.goquati.qr.QrSegment.Companion.numCharCountBits
import kotlin.experimental.or
import io.github.goquati.qr.Helper.panicIf
import io.github.goquati.qr.Helper.panicIfNot
import io.github.goquati.qr.Helper.panicIfNotIn
import kotlin.jvm.JvmInline

/**
 * A QR Code symbol, which is a type of two-dimension barcode.
 * Invented by Denso Wave and described in the ISO/IEC 18004 standard.
 * <p>Instances of this class represent an immutable square grid of dark and light cells.
 * The class provides static factory functions to create a QR Code from text or binary data.
 * The class covers the QR Code Model 2 specification, supporting all versions (sizes)
 * from 1 to 40, all 4 error correction levels, and 4 character encoding modes.</p>
 */
public class QrCode internal constructor(
    /** The version number of this QR Code, which is between 1 and 40 (inclusive).
     * This determines the size of this barcode. */
    public val version: Version,
    /** The error correction level used in this QR Code, which is not {@code null}. */
    public val errorCorrectionLevel: Ecc,
    /** The width and height of this QR Code, measured in modules, between
     * 21 and 177 (inclusive). This is equal to version &#xD7; 4 + 17. */
    public val size: Int,
    /** The index of the mask pattern used in this QR Code, which is between 0 and 7 (inclusive).
     * <p>Even if a QR Code is created with automatic masking requested (mask =
     * &#x2212;1), the resulting object still has a mask value between 0 and 7. */
    public val mask: Mask,
    /** The modules of this QR Code (false = light, true = dark). */
    private val modules: QuadraticBoolMatrix,
) {
    /**
     * Returns the color of the module (pixel) at the specified coordinates, which is {@code false}
     * for light or {@code true} for dark. The top left corner has the coordinates (x=0, y=0).
     * If the specified coordinates are out of bounds, then {@code false} (light) is returned.
     * @param x the x coordinate, where 0 is the left edge and size&#x2212;1 is the right edge
     * @param y the y coordinate, where 0 is the top edge and size&#x2212;1 is the bottom edge
     * @return {@code true} if the coordinates are in bounds and the module
     * at that location is dark, or {@code false} (light) otherwise
     */
    public operator fun get(x: Int, y: Int): Boolean = when {
        x !in 0..<size -> false
        y !in 0..<size -> false
        else -> modules[x, y]
    }


    @JvmInline
    public value class Version(public val value: Int) {
        init {
            value panicIfNotIn MIN_VALUE..MAX_VALUE
        }

        override fun toString(): String = value.toString()

        public companion object {
            private const val MIN_VALUE = 1
            private const val MAX_VALUE = 40

            /** The minimum version number  (1) supported in the QR Code Model 2 standard. */
            public val MIN: Version = Version(MIN_VALUE)

            /** The maximum version number (40) supported in the QR Code Model 2 standard. */
            public val MAX: Version = Version(MAX_VALUE)
        }
    }


    public enum class Mask {
        M0, M1, M2, M3, M4, M5, M6, M7;

        override fun toString(): String = ordinal.toString()
    }

    /**
     * The error correction level in a QR Code symbol.
     */
    public enum class Ecc {
        // Must be declared in ascending order of error protection
        // so that the implicit ordinal() and values() work properly
        /** The QR Code can tolerate about  7% erroneous codewords. */
        LOW,

        /** The QR Code can tolerate about 15% erroneous codewords. */
        MEDIUM,

        /** The QR Code can tolerate about 25% erroneous codewords. */
        QUARTILE,

        /** The QR Code can tolerate about 30% erroneous codewords. */
        HIGH,
    }

    public companion object {

        /**
         * Returns a QR Code representing the specified Unicode text string at the specified error correction level.
         * As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer
         * Unicode code points (not UTF-16 code units) if the low error correction level is used. The smallest possible
         * QR Code version is automatically chosen for the output. The ECC level of the result may be higher than the
         * ecl argument if it can be done without increasing the version.
         * @param text the text to be encoded, which can be any Unicode string
         * @param ecl the error correction level to use (boostable)
         * @return a QR Code representing the text
         * @throws DataTooLongException if the text fails to fit in the
         * largest version QR Code at the ECL, which means it is too long
         */
        public fun encodeText(text: CharSequence, ecl: Ecc): QrCode = encodeSegments(ecl) {
            val seg = QrSegment.makeSegment(text)
            add(seg)
        }

        /**
         * Returns a QR Code representing the specified text string encoded in alphanumeric mode.
         * The characters allowed are: 0 to 9, A to Z (uppercase only), space,
         * dollar, percent, asterisk, plus, hyphen, period, slash, colon.
         * QR Code version is automatically chosen for the output. The ECC level of the result may be higher than the
         * ecl argument if it can be done without increasing the version.
         * @param text the text, with only certain characters allowed
         * @param ecl the error correction level to use (boostable)
         * @return a QR Code representing the text
         * @throws DataTooLongException if the text fails to fit in the
         * largest version QR Code at the ECL, which means it is too long
         */
        public fun encodeAlphanumeric(text: CharSequence, ecl: Ecc): QrCode = encodeSegments(ecl) {
            val seg = QrSegment.makeAlphanumeric(text)
            add(seg)
        }

        /**
         * Returns a QR Code representing the specified text string encoded in numeric mode.
         * The characters allowed are: 0 to 9.
         * QR Code version is automatically chosen for the output. The ECC level of the result may be higher than the
         * ecl argument if it can be done without increasing the version.
         * @param text the text, with only certain characters allowed
         * @param ecl the error correction level to use (boostable)
         * @return a QR Code representing the text
         * @throws DataTooLongException if the text fails to fit in the
         * largest version QR Code at the ECL, which means it is too long
         */
        public fun encodeNumeric(text: CharSequence, ecl: Ecc): QrCode = encodeSegments(ecl) {
            val seg = QrSegment.makeNumeric(text)
            add(seg)
        }

        /**
         * Returns a QR Code representing the specified binary data at the specified error correction level.
         * This function always encodes using the binary segment mode, not any text mode. The maximum number of
         * bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
         * The ECC level of the result may be higher than the ecl argument if it can be done without increasing the version.
         * @param data the binary data to encode
         * @param ecl the error correction level to use (boostable)
         * @return a QR Code representing the data
         * @throws DataTooLongException if the data fails to fit in the
         * largest version QR Code at the ECL, which means it is too long
         */
        public fun encodeBinary(data: ByteArray, ecl: Ecc): QrCode = encodeSegments(ecl) {
            val seg = QrSegment.makeBytes(data)
            add(seg)
        }

        /**
         * Returns a QR Code representing the specified segments with the specified encoding parameters.
         * The smallest possible QR Code version within the specified range is automatically
         * chosen for the output. Iff boostEcl is {@code true}, then the ECC level of the
         * result may be higher than the ecl argument if it can be done without increasing
         * the version. The mask number is either between 0 to 7 (inclusive) to force that
         * mask, or &#x2212;1 to automatically choose an appropriate mask (which may be slow).
         * <p>This function allows the user to create a custom sequence of segments that switches
         * between modes (such as alphanumeric and byte) to encode text in less space.
         * This is a mid-level API; the high-level API is {@link #encodeText(CharSequence,Ecc)}
         * and {@link #encodeBinary(byte[],Ecc)}.</p>
         * @param ecl the error correction level to use (boostable)
         * @param segBuilder builder for the segments to encode
         * @return a QR Code representing the segments
         * @throws IllegalArgumentException if 1 &#x2264; minVersion &#x2264; maxVersion &#x2264; 40
         * or &#x2212;1 &#x2264; mask &#x2264; 7 is violated
         * @throws DataTooLongException if the segments fail to fit in
         * the maxVersion QR Code at the ECL, which means they are too long
         */
        public fun encodeSegments(
            ecl: Ecc,
            segBuilder: MutableList<QrSegment>.() -> Unit,
        ): QrCode {
            val seg = buildList(segBuilder)
            // Find the minimal version number to use
            val (version, dataUsedBits) = run {
                var version = Version.MIN
                var dataUsedBits: Int
                while (true) {
                    val dataCapacityBits = getNumDataCodewords(version, ecl) * 8  // Number of data bits available
                    dataUsedBits = QrSegment.getTotalBits(seg, version)
                    if (dataUsedBits != -1 && dataUsedBits <= dataCapacityBits)
                        break  // This version number is found to be suitable
                    if (version == Version.MAX)  // All versions in the range could not fit the given data
                        throw DataTooLongException("Data length = $dataUsedBits bits, Max capacity = $dataCapacityBits bits")
                    version = Version(version.value + 1)
                }
                version to dataUsedBits
            }

            // Increase the error correction level while the data still fits in the current version number
            val newEcl = Ecc.entries
                .dropWhile { it.ordinal <= ecl.ordinal }
                .lastOrNull { dataUsedBits <= getNumDataCodewords(version, it) * 8 }
                ?: ecl

            val bb = buildList {
                // Concatenate all segments to create the data bit string
                for (s in seg) {
                    appendBits(s.mode.modeBits, 4)
                    appendBits(s.numChars, s.mode.numCharCountBits(version))
                    addAll(s.data)
                }
                size panicIfNot dataUsedBits

                // Add terminator and pad up to a byte if applicable
                val dataCapacityBits = getNumDataCodewords(version, newEcl) * 8
                size panicIfNotIn 0..dataCapacityBits
                appendBits(0, 4.coerceAtMost(dataCapacityBits - size))
                appendBits(0, (8 - size % 8) % 8)
                panicIf(size % 8 != 0, "invalid alignment")

                // Pad with alternating bytes until data capacity is reached
                var padByte = 0xEC
                while (size < dataCapacityBits) {
                    appendBits(padByte, 8)
                    padByte = padByte xor (0xEC xor 0x11)
                }
            }
            // Pack bits into bytes in big endian
            val dataCodewords = ByteArray(bb.size / 8)
            for (i in bb.indices)
                dataCodewords[i ushr 3] = dataCodewords[i ushr 3] or (bb[i].toInt() shl (7 - (i and 7))).toByte()
            return buildQrCode(version = version, errorCorrectionLevel = newEcl, dataCodewords = dataCodewords)
        }
    }
}
