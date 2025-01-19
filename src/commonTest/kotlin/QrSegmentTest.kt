import io.github.goquati.qr.QrSegment
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class QrSegmentTest {
    @Test
    fun testInvalidNumeric() {
        shouldThrowExactly<IllegalArgumentException> {
            QrSegment.makeNumeric("12z34")
        }
    }

    @Test
    fun testInvalidAlphanumeric() {
        shouldThrowExactly<IllegalArgumentException> {
            QrSegment.makeAlphanumeric("100,0")
        }
        QrSegment.makeAlphanumeric("10")
    }

    @Test
    fun testConstructor() {
        shouldThrowExactly<IllegalArgumentException> {
            QrSegment(mode = QrSegment.Mode.ECI, numChars = -1, data = emptyList())
        }
    }

    @Test
    fun testEci() {
        shouldThrowExactly<IllegalArgumentException> { QrSegment.makeEci(-1) }
        QrSegment.makeEci(0).data shouldBe List(8) { false }
        QrSegment.makeEci(1 shl 7).data.size shouldBe 16
        QrSegment.makeEci(1 shl 14).data.size shouldBe 24
        shouldThrowExactly<IllegalArgumentException> { QrSegment.makeEci(1_000_000) }
    }

    @Test
    fun testAutoEncode() {
        shouldThrowExactly<IllegalArgumentException> { QrSegment.makeSegment("") }
        QrSegment.makeSegment("0123456789").mode shouldBe QrSegment.Mode.NUMERIC
        QrSegment.makeSegment("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 \$%*+./:-").mode shouldBe QrSegment.Mode.ALPHANUMERIC
        QrSegment.makeSegment("@").mode shouldBe QrSegment.Mode.BYTE
    }
}