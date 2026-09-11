package com.leadrdrk.umapatcher.core

import android.content.Context
import com.leadrdrk.umapatcher.utils.qoopyCacheDir
import java.io.File

data class QoopyCacheEntry(
    val version: String,
    val files: List<File>
)

// Persists the raw (unpatched) APKs downloaded via Qoopy, keyed by game version, so that
// re-patching after a Hachimi update doesn't require re-downloading the same split APKs.
// Only one version is kept at a time.
object QoopyCache {
    fun findAny(context: Context): QoopyCacheEntry? {
        val versionDir = context.qoopyCacheDir.listFiles()?.firstOrNull { it.isDirectory } ?: return null
        val files = versionDir.listFiles { f -> f.extension == "apk" }
        return if (files.isNullOrEmpty()) null else QoopyCacheEntry(versionDir.name, files.toList())
    }

    // Moves the given files into the persistent cache for [version] and discards any other
    // cached version's files.
    fun commit(context: Context, version: String, files: List<File>): List<File> {
        val dir = versionDir(context, version)
        dir.deleteRecursively()
        dir.mkdirs()

        val committed = files.map { file ->
            val dest = dir.resolve(file.name)
            if (!file.renameTo(dest)) {
                file.copyTo(dest, overwrite = true)
                file.delete()
            }
            dest
        }

        context.qoopyCacheDir.listFiles()?.forEach { child ->
            if (child.name != version) child.deleteRecursively()
        }

        return committed
    }

    private fun versionDir(context: Context, version: String): File =
        context.qoopyCacheDir.resolve(version)
}
