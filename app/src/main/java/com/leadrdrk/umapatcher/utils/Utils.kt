package com.leadrdrk.umapatcher.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

fun safeNavigate(lifecycleOwner: LifecycleOwner, callback: () -> Unit) {
    val currentState = lifecycleOwner.lifecycle.currentState
    if (currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        callback()
    }
}

fun deleteRecursive(fileOrDirectory: File, deleteRoot: Boolean = true) {
    if (fileOrDirectory.isDirectory)
        for (child in fileOrDirectory.listFiles()!!)
            deleteRecursive(child)

    if (deleteRoot) fileOrDirectory.delete()
}

fun downloadFileAndDigest(url: URL, outputFile: File, algorithm: String, onProgress: (Float) -> Unit): ByteArray {
    val conn = url.openConnection() as HttpURLConnection
    conn.doInput = true
    conn.instanceFollowRedirects = true
    conn.connect()

    if (conn.responseCode != 200) throw IOException()
    val contentLength = conn.contentLengthLong

    val digest = MessageDigest.getInstance(algorithm)

    conn.inputStream.use { input ->
        outputFile.outputStream().use { output ->
            val dataBuffer = ByteArray(1024)
            var totalBytesRead = 0L
            var bytesRead: Int
            while (input.read(dataBuffer, 0, 1024).also { bytesRead = it } != -1) {
                output.write(dataBuffer, 0, bytesRead)
                digest.update(dataBuffer, 0, bytesRead)

                totalBytesRead += bytesRead
                if (contentLength != -1L) {
                    onProgress(totalBytesRead.toFloat() / contentLength)
                }
            }
        }
    }

    return digest.digest()
}

fun downloadFileAndDigestSHA256(url: URL, outputFile: File, onProgress: (Float) -> Unit): ByteArray =
    downloadFileAndDigest(url, outputFile, "SHA-256", onProgress)

fun fetchText(url: URL, accept: String = "text/plain"): String {
    val conn = url.openConnection() as HttpURLConnection
    conn.setRequestProperty("Accept", accept)
    conn.doInput = true
    conn.instanceFollowRedirects = true
    conn.connect()

    if (conn.responseCode != 200) throw IOException()

    return conn.inputStream.use { input ->
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(1024)

        var length: Int
        while (input.read(buffer).also { length = it } != -1) {
            result.write(buffer, 0, length)
        }
        result.toString("UTF-8")
    }
}

private val gson = Gson()
fun fetchJson(url: URL, accept: String = "application/json"): HashMap<*, *> =
    gson.fromJson(fetchText(url, accept), HashMap::class.java)

private val HEX_ARRAY = "0123456789abcdef".toCharArray()
fun bytesToHex(bytes: ByteArray): String {
    val hexChars = CharArray(bytes.size * 2)
    for (j in bytes.indices) {
        val v = bytes[j].toInt() and 0xFF
        hexChars[j * 2] = HEX_ARRAY[v ushr 4]
        hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
    }
    return String(hexChars)
}

fun InputStream.copyTo(out: OutputStream, onProgress: (currentBytes: Int) -> Unit) {
    val buffer = ByteArray(524288) // 512KiB
    var lengthRead: Int
    var currentBytes = 0
    while (read(buffer).also { lengthRead = it } > 0) {
        out.write(buffer, 0, lengthRead)
        currentBytes += lengthRead
        onProgress(currentBytes)
    }
    out.flush()
}