import io.github.goquati.qr.QrCode
import io.kotest.assertions.throwables.shouldThrowExactly
import kotlin.test.Test

class CommonTest {
    @Test
    fun testVersion() {
        shouldThrowExactly<IllegalStateException> {
            QrCode.Version(0)
        }
        shouldThrowExactly<IllegalStateException> {
            QrCode.Version(41)
        }
        QrCode.Version(1)
        QrCode.Version(40)
    }
}