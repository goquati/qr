package io.github.goquati.qr

/**
 * A segment of character/binary/control data in a QR Code symbol.
 * Instances of this class are immutable.
 * <p>The mid-level way to create a segment is to take the payload data and call a
 * static factory function such as {@link QrSegment#makeNumeric(CharSequence)}. The low-level
 * way to create a segment is to custom-make the bit buffer and call the {@link
 * QrSegment#QrSegment(Mode,int,BitBuffer) constructor} with appropriate values.</p>
 * <p>This segment class imposes no length restrictions, but QR Codes have restrictions.
 * Even in the most favorable conditions, a QR Code can only hold 7089 characters of data.
 * Any segment longer than this is meaningless for the purpose of generating QR Codes.
 * This class can represent kanji mode segments, but provides no help in encoding them
 * - see {@link QrSegmentAdvanced} for full kanji support.</p>
 */
@ConsistentCopyVisibility
public data class QrSegment internal constructor(
    /** The mode indicator of this segment. Not {@code null}. */
    val mode: Mode,

    /** The length of this segment's unencoded data. Measured in characters for
     * numeric/alphanumeric/kanji mode, bytes for byte mode, and 0 for ECI mode.
     * Always zero or positive. Not the same as the data's bit length. */
    val numChars: Int,

    /** The data bits of this segment. Not null. Accessed through getData(). */
    val data: List<Boolean>,
) {
    init {
        if (numChars < 0) throw IllegalArgumentException("Invalid value")
    }

    /**
     * Describes how a segment's data bits are interpreted.
     */
    public enum class Mode { NUMERIC, ALPHANUMERIC, BYTE, ECI }

    public companion object {
        // Describes precisely all strings that are encodable in numeric mode.
        private const val NUMERIC_CHARSET = "0123456789"

        // The set of all legal characters in alphanumeric mode, where
        // each character value maps to the index in the string.
        private const val ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

        // Calculates the number of bits needed to encode the given segments at the given version.
        // Returns a non-negative number if successful. Otherwise, returns -1 if a segment has too
        // many characters to fit its length field, or the total bits exceeds Integer.MAX_VALUE.
        internal fun getTotalBits(segs: List<QrSegment>, version: QrCode.Version): Int = segs.sumOf { seg ->
            val ccBits = seg.mode.numCharCountBits(version)
            if (seg.numChars >= (1 shl ccBits))
                return -1  // The segment's length doesn't fit the field's bit width
            4L + ccBits + seg.data.size
        }.toInt()

        internal val Mode.modeBits
            get() = when (this) {
                Mode.NUMERIC -> 0x1
                Mode.ALPHANUMERIC -> 0x2
                Mode.BYTE -> 0x4
                Mode.ECI -> 0x7
            }

        // Returns the bit width of the character count field for a segment in this mode
        // in a QR Code at the given version number. The result is in the range [0, 16].
        internal fun Mode.numCharCountBits(version: QrCode.Version): Int {
            val i = (version.value + 7) / 17
            return when (this) {
                Mode.NUMERIC -> arrayOf(10, 12, 14)[i]
                Mode.ALPHANUMERIC -> arrayOf(9, 11, 13)[i]
                Mode.BYTE -> arrayOf(8, 16, 16)[i]
                Mode.ECI -> 0
            }
        }

        /**
         * Adds a segment representing the specified binary data
         * encoded in byte mode. All input byte arrays are acceptable.
         * <p>Any text string can be converted to UTF-8 bytes ({@code
         * s.getBytes(StandardCharsets.UTF_8)}) and encoded as a byte mode segment.</p>
         * @param data the binary data
         * @return true because the list is always modified as the result of this operation.
         */
        public fun MutableList<QrSegment>.addBytes(data: ByteArray): Boolean = add(makeBytes(data))

        /**
         * Returns a segment representing the specified binary data
         * encoded in byte mode. All input byte arrays are acceptable.
         * <p>Any text string can be converted to UTF-8 bytes ({@code
         * s.getBytes(StandardCharsets.UTF_8)}) and encoded as a byte mode segment.</p>
         * @param data the binary data
         * @return a segment containing the data
         */
        public fun makeBytes(data: ByteArray): QrSegment {
            val bb = buildList {
                for (b in data) appendBits((b.toInt() and 0xFF), 8)
            }
            return QrSegment(Mode.BYTE, data.size, bb)
        }

        /**
         * Adds a segment representing the specified string of decimal digits encoded in numeric mode.
         * @param digits the text, with only digits from 0 to 9 allowed
         * @return true because the list is always modified as the result of this operation.
         * @throws IllegalArgumentException if the string contains non-digit characters
         */
        public fun MutableList<QrSegment>.addNumeric(digits: CharSequence): Boolean = add(makeNumeric(digits))

        /**
         * Returns a segment representing the specified string of decimal digits encoded in numeric mode.
         * @param digits the text, with only digits from 0 to 9 allowed
         * @return a segment containing the text
         * @throws IllegalArgumentException if the string contains non-digit characters
         */
        public fun makeNumeric(digits: CharSequence): QrSegment {
            if (!digits.isNumeric())
                throw IllegalArgumentException("String contains non-numeric characters")
            val bb = buildList {
                var i = 0
                while (i < digits.length) {
                    val n = (digits.length - i).coerceAtMost(3)
                    appendBits((digits.subSequence(i, i + n).toString()).toInt(), n * 3 + 1)
                    i += n
                }
            }
            return QrSegment(Mode.NUMERIC, digits.length, bb)
        }

        /**
         * Adds a segment representing the specified text string encoded in alphanumeric mode.
         * The characters allowed are: 0 to 9, A to Z (uppercase only), space,
         * dollar, percent, asterisk, plus, hyphen, period, slash, colon.
         * @param text the text, with only certain characters allowed
         * @return true because the list is always modified as the result of this operation.
         * @throws IllegalArgumentException if the string contains non-encodable characters
         */
        public fun MutableList<QrSegment>.addAlphanumeric(text: CharSequence): Boolean = add(makeAlphanumeric(text))

        /**
         * Returns a segment representing the specified text string encoded in alphanumeric mode.
         * The characters allowed are: 0 to 9, A to Z (uppercase only), space,
         * dollar, percent, asterisk, plus, hyphen, period, slash, colon.
         * @param text the text, with only certain characters allowed
         * @return a segment containing the text
         * @throws IllegalArgumentException if the string contains non-encodable characters
         */
        public fun makeAlphanumeric(text: CharSequence): QrSegment {
            if (!text.isAlphanumeric())
                throw IllegalArgumentException("String contains unencodable characters in alphanumeric mode")
            val bb = buildList {
                var i = 0
                while (i < text.length - 1) { // Process groups of 2
                    val temp = ALPHANUMERIC_CHARSET.indexOf(text[i]) * 45 + ALPHANUMERIC_CHARSET.indexOf(text[i + 1])
                    appendBits(temp, 11)
                    i += 2
                }
                if (i < text.length)  // 1 character remaining
                    appendBits(ALPHANUMERIC_CHARSET.indexOf(text[i]), 6)
            }
            return QrSegment(Mode.ALPHANUMERIC, text.length, bb)
        }

        /**
         * Adds a list of zero or more segments to represent the specified Unicode text string.
         * The result may use various segment modes and switch modes to optimize the length of the bit stream.
         * @param text the text to be encoded, which can be any Unicode string
         * @return true because the list is always modified as the result of this operation.
         */
        public fun MutableList<QrSegment>.addSegment(text: CharSequence): Boolean = add(makeSegment(text))

        /**
         * Returns a list of zero or more segments to represent the specified Unicode text string.
         * The result may use various segment modes and switch modes to optimize the length of the bit stream.
         * @param text the text to be encoded, which can be any Unicode string
         * @return a new mutable list of segments containing the text
         */
        public fun makeSegment(text: CharSequence): QrSegment = when {
            text.isEmpty() -> throw IllegalArgumentException("Empty Segment")
            text.isNumeric() -> makeNumeric(text)
            text.isAlphanumeric() -> makeAlphanumeric(text)
            else -> makeBytes(text.toString().encodeToByteArray())
        }

        /**
         * Adds a segment representing an Extended Channel Interpretation
         * (ECI) designator with the specified assignment value.
         * @param assignVal the ECI assignment number (see the AIM ECI specification)
         * @return true because the list is always modified as the result of this operation.
         * @throws IllegalArgumentException if the value is outside the range [0, 10<sup>6</sup>)
         */
        public fun MutableList<QrSegment>.addEci(assignVal: Int): Boolean = add(makeEci(assignVal))

        /**
         * Returns a segment representing an Extended Channel Interpretation
         * (ECI) designator with the specified assignment value.
         * @param assignVal the ECI assignment number (see the AIM ECI specification)
         * @return a segment containing the data
         * @throws IllegalArgumentException if the value is outside the range [0, 10<sup>6</sup>)
         */
        public fun makeEci(assignVal: Int): QrSegment {
            val bb = buildList {
                when (assignVal) {
                    in 0..<(1 shl 7) -> appendBits(assignVal, 8)
                    in (1 shl 7)..<(1 shl 14) -> {
                        appendBits(0b10, 2)
                        appendBits(assignVal, 14)
                    }

                    in (1 shl 14)..<1_000_000 -> {
                        appendBits(0b110, 3)
                        appendBits(assignVal, 21)
                    }

                    else -> throw IllegalArgumentException("ECI assignment value out of range")
                }
            }
            return QrSegment(Mode.ECI, 0, bb)
        }

        /**
         * Tests whether the specified string can be encoded as a segment in numeric mode.
         * A string is encodable iff each character is in the range 0 to 9.
         */
        private fun CharSequence.isNumeric() = all { it in NUMERIC_CHARSET }

        /**
         * Tests whether the specified string can be encoded as a segment in alphanumeric mode.
         * A string is encodable iff each character is in the following set: 0 to 9, A to Z
         * (uppercase only), space, dollar, percent, asterisk, plus, hyphen, period, slash, colon.
         */
        private fun CharSequence.isAlphanumeric() = all { it in ALPHANUMERIC_CHARSET }
    }
}
