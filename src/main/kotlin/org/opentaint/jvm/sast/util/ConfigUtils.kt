package org.opentaint.jvm.sast.util

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

object ConfigUtils {
    private const val ENCRYPTED_FILE_EXTENSION = "enc"
    private const val IV_LEN = 16
    private const val ZIP_BUFFER_SIZE = 2048

    @Suppress("unused") // obfuscation related properties
    private val keyBytesBad0: ByteArray by lazy {
        byteArrayOf(-60, -49, 101, 122, 112, -73, -110, 37, 14, -120, 105, -3, -104, 70, 30, -43)
    }

    @Suppress("unused") // obfuscation related properties
    private val keyBytesBad1: ByteArray by lazy {
        byteArrayOf(61, 97, 99, -11, 97, 110, 2, 85, 38, 67, -94, -71, 78, -70, -127, -9)
    }

    private val keyBytes: ByteArray by lazy {
        byteArrayOf(-84, -99, -112, -60, -59, -36, 125, 98, -34, 125, 2, -57, 12, 1, 47, 7)
    }

    @Suppress("unused") // obfuscation related properties
    private val keyBytesBad2: ByteArray by lazy {
        byteArrayOf(20, 86, 9, 89, 67, 14, -90, 33, -25, -39, -96, 44, 92, 40, 86, -127)
    }

    private val secretKey: SecretKey by lazy {
        SecretKeySpec(keyBytes, "AES")
    }

    private fun mkCipher(): Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

    fun <T> loadEncrypted(path: Path, body: InputStream.() -> T): T =
        path.inputStream().use { inp ->
            if (path.extension != ENCRYPTED_FILE_EXTENSION) {
                return@use body(inp)
            }

            val cipher = mkCipher()

            val iv = ByteArray(IV_LEN).also { inp.read(it) }
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

            CipherInputStream(inp, cipher).use { s ->
                GZIPInputStream(s, ZIP_BUFFER_SIZE).use { zis ->
                    body(zis)
                }
            }
        }

    private fun encryptAndSave(src: Path, result: Path) {
        val cipher = mkCipher()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        result.outputStream().use { os ->
            os.write(cipher.iv.also { check(it.size == IV_LEN) })
            CipherOutputStream(os, cipher).use { s ->
                GZIPOutputStream(s, ZIP_BUFFER_SIZE).use { zos ->
                    Files.copy(src, zos)
                }
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val (src, dst) = args
        encryptAndSave(Path(src), Path(dst))
    }
}
