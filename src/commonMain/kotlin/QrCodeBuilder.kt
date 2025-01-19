package io.github.goquati.qr

import kotlin.experimental.xor
import kotlin.math.abs
import io.github.goquati.qr.Helper.panicIf
import io.github.goquati.qr.Helper.panicIfNot
import io.github.goquati.qr.Helper.panicIfNotIn

// For use in getPenaltyScore(), when evaluating which mask is best.
private const val PENALTY_N1 = 3
private const val PENALTY_N2 = 3
private const val PENALTY_N3 = 40
private const val PENALTY_N4 = 10

// @formatter:off
private fun QrCode.Ecc.codeWordsPerBlock(version: QrCode.Version) = when(this) {
    QrCode.Ecc.LOW -> byteArrayOf(7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30)
    QrCode.Ecc.MEDIUM -> byteArrayOf(10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28)
    QrCode.Ecc.QUARTILE -> byteArrayOf(13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30)
    QrCode.Ecc.HIGH -> byteArrayOf(17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30)
}[version.value - 1]

private fun QrCode.Ecc.numErrorCorrectionBlocks(version: QrCode.Version) = when(this) {
    QrCode.Ecc.LOW -> byteArrayOf(1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25)
    QrCode.Ecc.MEDIUM -> byteArrayOf(1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49)
    QrCode.Ecc.QUARTILE -> byteArrayOf(1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68)
    QrCode.Ecc.HIGH -> byteArrayOf(1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81)
}[version.value - 1]

// Returns the number of data bits that can be stored in a QR Code of the given version number, after
// all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
private val QrCode.Version.numRawDataModules get() = intArrayOf(
    208, 359, 567, 807, 1079, 1383, 1568, 1936, 2336, 2768, 3232, 3728, 4256, 4651, 5243, 5867, 6523, 7211, 7931, 8683, 9252, 10068, 10916, 11796, 12708, 13652, 14628, 15371, 16411, 17483, 18587, 19723, 20891, 22091, 23008, 24272, 25568, 26896, 28256, 29648,
)[value - 1]
// @formatter:on

private class QrCodeBuilder(
    /** The version number of this QR Code, which is between 1 and 40 (inclusive).
     * This determines the size of this barcode. */
    val version: QrCode.Version,
    /** The error correction level used in this QR Code, which is not {@code null}. */
    val errorCorrectionLevel: QrCode.Ecc,
) {
    /** The width and height of this QR Code, measured in modules, between
     * 21 and 177 (inclusive). This is equal to version &#xD7; 4 + 17. */
    val size: Int = version.value * 4 + 17

    /** The modules of this QR Code (false = light, true = dark). */
    val modules = QuadraticBoolMatrix(size)

    /** Indicates function modules that are not subjected to masking. Discarded when constructor finishes. */
    val isFunction = QuadraticBoolMatrix(size)

    fun build(mask: QrCode.Mask) = QrCode(
        version = version,
        errorCorrectionLevel = errorCorrectionLevel,
        size = size,
        mask = mask,
        modules = modules,
    )

    // Reads this object's version field, and draws and marks all function modules.
    fun drawFunctionPatterns() {
        // Draw horizontal and vertical timing patterns
        for (i in 0..<size) {
            setFunctionModule(6, i, i % 2 == 0)
            setFunctionModule(i, 6, i % 2 == 0)
        }

        // Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
        drawFinderPattern(3, 3)
        drawFinderPattern(size - 4, 3)
        drawFinderPattern(3, size - 4)

        // Draw numerous alignment patterns
        val alignPatPos = getAlignmentPatternPositions()
        val numAlign = alignPatPos.size
        for (i in 0..<numAlign) {
            for (j in 0..<numAlign) {
                // Don't draw on the three finder corners
                if (!(i == 0 && j == 0 || i == 0 && j == numAlign - 1 || i == numAlign - 1 && j == 0))
                    drawAlignmentPattern(alignPatPos[i], alignPatPos[j])
            }
        }

        // Draw configuration data
        drawFormatBits(QrCode.Mask.M0)  // Dummy mask value; overwritten later in the constructor
        drawVersion()
    }

    // Draws two copies of the format bits (with its own error correction code)
    // based on the given mask and this object's error correction level field.
    fun drawFormatBits(msk: QrCode.Mask) {
        val eccFormatBits = when (errorCorrectionLevel) {
            QrCode.Ecc.LOW -> 1
            QrCode.Ecc.MEDIUM -> 0
            QrCode.Ecc.QUARTILE -> 3
            QrCode.Ecc.HIGH -> 2
        }
        // Calculate error correction code and pack bits
        val data = eccFormatBits shl 3 or msk.ordinal  // errCorrLvl is uint2, mask is uint3
        var rem = data
        for (i in 0..<10)
            rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        val bits = (data shl 10 or rem) xor 0x5412  // uint15
        panicIf(bits ushr 15 != 0, "expect uint15")

        // Draw first copy
        for (i in 0..5)
            setFunctionModule(8, i, bits.getBit(i))
        setFunctionModule(8, 7, bits.getBit(6))
        setFunctionModule(8, 8, bits.getBit(7))
        setFunctionModule(7, 8, bits.getBit(8))
        for (i in 9..<15)
            setFunctionModule(14 - i, 8, bits.getBit(i))

        // Draw second copy
        for (i in 0..<8)
            setFunctionModule(size - 1 - i, 8, bits.getBit(i))
        for (i in 8..<15)
            setFunctionModule(8, size - 15 + i, bits.getBit(i))
        setFunctionModule(8, size - 8, true)  // Always dark
    }

    // Draws two copies of the version bits (with its own error correction code),
    // based on this object's version field, iff 7 <= version <= 40.
    fun drawVersion() {
        if (version.value < 7) return
        // Calculate error correction code and pack bits
        var rem = version.value  // version is uint6, in the range [7, 40]
        for (i in 0..<12)
            rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
        val bits = version.value shl 12 or rem  // uint18
        panicIf(bits ushr 18 != 0, "expect uint18")

        // Draw two copies
        for (i in 0..<18) {
            val bit = bits.getBit(i)
            val a = size - 11 + i % 3
            val b = i / 3
            setFunctionModule(a, b, bit)
            setFunctionModule(b, a, bit)
        }
    }

    // Draws a 9*9 finder pattern including the border separator,
    // with the center module at (x, y). Modules can be out of bounds.
    fun drawFinderPattern(x: Int, y: Int) {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val dist = abs(dx).coerceAtLeast(abs(dy)) // Chebyshev/infinity norm
                val xx = x + dx
                val yy = y + dy
                if (xx in 0..<size && yy in 0..<size)
                    setFunctionModule(xx, yy, dist != 2 && dist != 4)
            }
        }
    }

    // Draws a 5*5 alignment pattern, with the center module
    // at (x, y). All modules must be in bounds.
    fun drawAlignmentPattern(x: Int, y: Int) {
        for (dy in -2..2)
            for (dx in -2..2)
                setFunctionModule(x + dx, y + dy, abs(dx).coerceAtLeast(abs(dy)) != 1)
    }

    // Sets the color of a module and marks it as a function module.
    // Only used by the constructor. Coordinates must be in bounds.
    fun setFunctionModule(x: Int, y: Int, isDark: Boolean) {
        modules[x, y] = isDark
        isFunction[x, y] = true
    }

    // Returns a new byte string representing the given data with the appropriate error correction
    // codewords appended to it, based on this object's version and error correction level.
    fun addEccAndInterleave(data: ByteArray): ByteArray {
        data.size panicIfNot getNumDataCodewords(version, errorCorrectionLevel)

        // Calculate parameter numbers
        val numBlocks = errorCorrectionLevel.numErrorCorrectionBlocks(version)
        val blockEccLen = errorCorrectionLevel.codeWordsPerBlock(version)
        val rawCodewords = version.numRawDataModules / 8
        val numShortBlocks = numBlocks - rawCodewords % numBlocks
        val shortBlockLen = rawCodewords / numBlocks

        // Split data into blocks and append ECC to each block
        val blocks = Array(numBlocks.toInt()) { ByteArray(0) }
        val rsDiv = reedSolomonComputeDivisor(blockEccLen)
        run {
            var k = 0
            for (i in 0..<numBlocks) {

                val dat = data.copyOfRange(k, k + shortBlockLen - blockEccLen + (if (i < numShortBlocks) 0 else 1))
                k += dat.size
                val block = dat.copyOf(shortBlockLen + 1)
                val ecc = reedSolomonComputeRemainder(dat, rsDiv)
                ecc.copyInto(
                    destination = block,
                    destinationOffset = block.size - blockEccLen,
                    startIndex = 0,
                    endIndex = ecc.size
                )
                blocks[i] = block
            }
        }

        // Interleave (not concatenate) the bytes from every block into a single sequence
        val result = ByteArray(rawCodewords)
        run {
            var k = 0
            for (i in 0..<blocks[0].size) {
                for (j in blocks.indices) {
                    // Skip the padding byte in short blocks
                    if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
                        result[k] = blocks[j][i]
                        k++
                    }
                }
            }
        }
        return result
    }

    // Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
    // data area of this QR Code. Function modules need to be marked off before this is called.
    fun drawCodewords(data: ByteArray) {
        data.size panicIfNot version.numRawDataModules / 8

        var i = 0  // Bit index into the data
        // Do the funny zigzag scan
        var right = size - 1
        while (right >= 1) { // Index of right column in each column pair
            if (right == 6)
                right = 5
            for (vert in 0..<size) {  // Vertical counter
                for (j in 0..<2) {
                    val x = right - j // Actual x coordinate
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert  // Actual y coordinate
                    if (!isFunction[x, y] && i < data.size * 8) {
                        modules[x, y] = data[i ushr 3].getBit(7 - (i and 7))
                        i++
                    }
                    // If this QR Code has any remainder bits (0 to 7), they were assigned as
                    // 0/false/light by the constructor and are left unchanged by this method
                }
            }
            right -= 2
        }
        i panicIfNot data.size * 8
    }

    // XORs the codeword modules in this QR Code with the given mask pattern.
    // The function modules must be marked and the codeword bits must be drawn
    // before masking. Due to the arithmetic of XOR, calling applyMask() with
    // the same mask value a second time will undo the mask. A final well-formed
    // QR Code needs exactly one (not zero, two, etc.) mask applied.
    fun applyMask(mask: QrCode.Mask) {
        for (y in 0..<size) {
            for (x in 0..<size) {
                val invert = when (mask) {
                    QrCode.Mask.M0 -> (x + y) % 2 == 0
                    QrCode.Mask.M1 -> y % 2 == 0
                    QrCode.Mask.M2 -> x % 3 == 0
                    QrCode.Mask.M3 -> (x + y) % 3 == 0
                    QrCode.Mask.M4 -> (x / 3 + y / 2) % 2 == 0
                    QrCode.Mask.M5 -> x * y % 2 + x * y % 3 == 0
                    QrCode.Mask.M6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    QrCode.Mask.M7 -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                modules[x, y] = modules[x, y] xor (invert and !isFunction[x, y])
            }
        }
    }

    // Calculates and returns the penalty score based on state of this QR Code's current modules.
    // This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
    fun getPenaltyScore(): Int {
        val result = modules.getPenaltyScoreFinderLikePatterns() +
                modules.transpose().getPenaltyScoreFinderLikePatterns() +
                modules.getPenaltyScore2x2Pattern() +
                modules.getPenaltyScoreColorBalance()
        result panicIfNotIn 0..2568888 // non-tight upper bound based on default values of PENALTY_N1, ..., N4
        return result
    }

    // Returns an ascending list of positions of alignment patterns for this version number.
    // Each position is in the range [0,177), and are used on both the x and y axes.
    // This could be implemented as lookup table of 40 variable-length lists of unsigned bytes.
    fun getAlignmentPatternPositions(): IntArray = if (version.value == 1)
        IntArray(0)
    else {
        val numAlign = version.value / 7 + 2
        val step = (version.value * 8 + numAlign * 3 + 5) / (numAlign * 4 - 4) * 2
        val result = IntArray(numAlign)
        result[0] = 6

        var pos = size - 7
        for (i in (result.size - 1) downTo 1) {
            result[i] = pos
            pos -= step
        }
        result
    }
}

internal fun buildQrCode(
    /** The version number of this QR Code, which is between 1 and 40 (inclusive).
     * This determines the size of this barcode. */
    version: QrCode.Version,
    /** The error correction level used in this QR Code, which is not {@code null}. */
    errorCorrectionLevel: QrCode.Ecc,
    /** the bytes representing segments to encode (without ECC)*/
    dataCodewords: ByteArray,
): QrCode {
    val builder = QrCodeBuilder(
        version = version,
        errorCorrectionLevel = errorCorrectionLevel,
    )

    // Compute ECC, draw modules, do masking
    builder.drawFunctionPatterns()
    builder.addEccAndInterleave(dataCodewords).let { allCodewords ->
        builder.drawCodewords(allCodewords)
    }

    // Automatically choose best mask
    val mask = QrCode.Mask.entries.minBy { mask ->
        builder.applyMask(mask)
        builder.drawFormatBits(mask)
        val penalty = builder.getPenaltyScore()
        builder.applyMask(mask)  // Undoes the mask due to XOR
        penalty
    }

    builder.applyMask(mask)  // Apply the final choice of mask
    builder.drawFormatBits(mask)  // Overwrite old format bits
    return builder.build(mask = mask)
}

// Returns a Reed-Solomon ECC generator polynomial for the given degree. This could be
// implemented as a lookup table over all possible parameter values, instead of as an algorithm.
private fun reedSolomonComputeDivisor(degree: Byte): ByteArray {
    // Polynomial coefficients are stored from highest to lowest power, excluding the leading term which is always 1.
    // For example the polynomial x^3 + 255x^2 + 8x + 93 is stored as the uint8 array {255, 8, 93}.
    val result = ByteArray(degree.toInt())
    result[degree - 1] = 1  // Start off with the monomial x^0

    // Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
    // and drop the highest monomial term which is always 1x^degree.
    // Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
    var root = 1
    for (i in 0..<degree) {
        // Multiply the current product by (x - r^i)
        for (j in result.indices) {
            result[j] = reedSolomonMultiply(result[j].toInt() and 0xFF, root).toByte()
            if (j + 1 < result.size)
                result[j] = result[j] xor result[j + 1]
        }
        root = reedSolomonMultiply(root, 0x02)
    }
    return result
}

// Returns the Reed-Solomon error correction codeword for the given data and divisor polynomials.
private fun reedSolomonComputeRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
    val result = ByteArray(divisor.size)
    for (b in data) {  // Polynomial division
        val factor = (b xor result[0]).toInt() and 0xFF
        result.copyInto(result, startIndex = 1, endIndex = result.size)
        result[result.size - 1] = 0
        for (i in result.indices)
            result[i] = result[i] xor reedSolomonMultiply(divisor[i].toInt() and 0xFF, factor).toByte()
    }
    return result
}

// Returns the product of the two given field elements modulo GF(2^8/0x11D). The arguments and result
// are unsigned 8-bit integers. This could be implemented as a lookup table of 256*256 entries of uint8.
private fun reedSolomonMultiply(x: Int, y: Int): Int {
    panicIf(x shr 8 != 0, "expect uint8 for x")
    panicIf(y shr 8 != 0, "expect uint8 for y")
    // Russian peasant multiplication
    var z = 0
    for (i in 7 downTo 0) {
        z = (z shl 1) xor ((z ushr 7) * 0x11D)
        z = z xor (((y ushr i) and 1) * x)
    }
    panicIf(z ushr 8 != 0, "expect uint8 for z")
    return z
}

private fun QuadraticBoolMatrix.getPenaltyScoreColorBalance(): Int {
    // Balance of dark and light modules
    val dark = sequence.count { it.second }
    val total = size * size  // Note that size is odd, so dark/total != 1/2
    // Compute the smallest integer k >= 0 such that (45-5k)% <= dark/total <= (55+5k)%
    val k = (abs(dark * 20 - total * 10) + total - 1) / total - 1
    k panicIfNotIn 0..9
    return k * PENALTY_N4
}

// 2*2 blocks of modules having same color
private fun QuadraticBoolMatrix.getPenaltyScore2x2Pattern(): Int = sequence { (x, y), color ->
    x < size - 1 && y < size - 1 && color == this[x + 1, y] && color == this[x, y + 1] && color == this[x + 1, y + 1]
}.count { it } * PENALTY_N2

// Adjacent modules in row having same color, and finder-like patterns
private fun QuadraticBoolMatrix.getPenaltyScoreFinderLikePatterns(): Int {
    // Can only be called immediately after a light run is added, and
    // returns either 0, 1, or 2. A helper function for getPenaltyScore().
    fun finderPenaltyCountPatterns(runHistory: IntArray): Int {
        val n = runHistory[1]
        n panicIfNotIn 0..(size * 3)
        val core = n > 0 && runHistory[2] == n && runHistory[3] == n * 3 && runHistory[4] == n && runHistory[5] == n
        val b1 = core && runHistory[0] >= n * 4 && runHistory[6] >= n
        val b2 = core && runHistory[6] >= n * 4 && runHistory[0] >= n
        return (if (b1) 1 else 0) + (if (b2) 1 else 0)
    }

    // Pushes the given value to the front and drops the last value. A helper function for getPenaltyScore().
    fun finderPenaltyAddHistory(currentRunLength: Int, runHistory: IntArray) {
        var result = currentRunLength
        if (runHistory[0] == 0)
            result += size  // Add light border to initial run
        runHistory.copyInto(runHistory, destinationOffset = 1, endIndex = runHistory.size - 1)
        runHistory[0] = result
    }

    // Must be called at the end of a line (row or column) of modules. A helper function for getPenaltyScore().
    fun finderPenaltyTerminateAndCount(
        currentRunColor: Boolean,
        currentRunLength: Int,
        runHistory: IntArray,
    ): Int {
        var currentRunLengthMut = currentRunLength
        if (currentRunColor) {  // Terminate dark run
            finderPenaltyAddHistory(currentRunLengthMut, runHistory)
            currentRunLengthMut = 0
        }
        currentRunLengthMut += size  // Add light border to final run
        finderPenaltyAddHistory(currentRunLengthMut, runHistory)
        return finderPenaltyCountPatterns(runHistory)
    }

    var result = 0
    for (y in 0..<size) {
        var runColor = false
        var runX = 0
        val runHistory = IntArray(7)
        for (x in 0..<size) {
            if (this[x, y] == runColor) {
                runX++
                when {
                    runX == 5 -> result += PENALTY_N1
                    runX > 5 -> result++
                }
            } else {
                finderPenaltyAddHistory(runX, runHistory)
                if (!runColor) result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3
                runColor = this[x, y]
                runX = 1
            }
        }
        result += finderPenaltyTerminateAndCount(runColor, runX, runHistory) * PENALTY_N3
    }
    return result
}

// Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
// QR Code of the given version number and error correction level, with remainder bits discarded.
// This stateless pure function could be implemented as a (40*4)-cell lookup table.
internal fun getNumDataCodewords(ver: QrCode.Version, ecl: QrCode.Ecc): Int =
    ver.numRawDataModules / 8 - ecl.codeWordsPerBlock(ver) * ecl.numErrorCorrectionBlocks(ver)
