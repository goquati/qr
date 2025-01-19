# QR Code Generator Library for Kotlin Multiplatform

![GitHub License](https://img.shields.io/github/license/goquati/qr)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/goquati/qr/check.yml)
![Static Badge](https://img.shields.io/badge/coverage-100%25-success)

A Kotlin Multiplatform (KMP) library for generating QR Codes. This library supports all QR Code Model 2 specifications, including versions 1 to 40, all error correction levels, and various encoding modes. It is designed to be lightweight and efficient, providing a flexible API for encoding text, binary data, and more into QR Codes.

## Features

- **Support for all QR Code versions (1–40)**: Generate QR Codes of various sizes.
- **Error correction levels**: Includes support for LOW, MEDIUM, QUARTILE, and HIGH error correction levels.
- **Encoding modes**: Numeric, alphanumeric, byte, and Extended Channel Interpretation (ECI).
- **Immutable and thread-safe**: Designed for concurrent use in a multithreaded environment.
- **Kotlin Multiplatform support**: Can be used on JVM, Android, iOS, and other Kotlin-supported platforms.

## Installation

Add the dependency to your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("io.github.goquati:qr:$VERSION")
}
```

For multiplatform projects, include the library in the common dependencies block.

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.github.goquati:qr:$VERSION")
            }
        }
    }
}
```

Replace `$VERSION` with the latest version available on Maven Central or your preferred package repository.

## Usage

### Encode Text into a QR Code

```kotlin
import io.github.goquati.qr.QrCode
import io.github.goquati.qr.QrCode.Ecc

val qrCode = QrCode.encodeText("Hello, World!", Ecc.HIGH)
println("QR Code size: ${qrCode.size}")
```

### Retrieve Module Data

Access the color of specific modules (pixels) in the QR Code:

```kotlin
for (y in 0 until qrCode.size) {
    for (x in 0 until qrCode.size) {
        print(if (qrCode[x, y]) "██" else "  ")
    }
    println()
}
```

### Encode Binary Data

```kotlin
val binaryData = byteArrayOf(0x01, 0x02, 0x03)
val qrCode = QrCode.encodeBinary(binaryData, Ecc.MEDIUM)
```

### Custom Segments

Use segments to optimize encoding and switch between modes:

```kotlin
import io.github.goquati.qr.QrSegment

val qrCode = QrCode.encodeSegments(Ecc.QUARTILE) {
    addAlphanumeric("HELLO ")
    addNumeric("12345")
}
```

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Contributions

Contributions are welcome! Feel free to open an issue or submit a pull request. Please ensure that your code adheres to the existing coding standards and includes appropriate tests.
