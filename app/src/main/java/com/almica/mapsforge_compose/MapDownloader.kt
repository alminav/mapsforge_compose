package com.almica.mapsforge_compose

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mapsforge.map.reader.MapFile
import timber.log.Timber
import java.io.File
import java.net.URL

object MapDownloader {
    fun isMapFileValid(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            MapFile(file).close()
            true
        } catch (e: Exception) {
            Timber.e(e, "Map file is invalid: ${file.absolutePath}")
            file.delete()
            false
        }
    }

    suspend fun downloadMapIfMissing(urlStr: String, targetFile: File, onProgress: (Float) -> Unit): Boolean {
        Timber.i("Downloading map to ${targetFile.absolutePath}")
        if (isMapFileValid(targetFile)) {
            return true
        }

        return withContext(Dispatchers.IO) {
            val tempFile = File(targetFile.parent, targetFile.name + ".tmp")
            try {
                if (tempFile.exists()) tempFile.delete()
                
                val url = URL(urlStr)
                val connection = url.openConnection()
                connection.connect()

                val fileLength = connection.contentLength
                val input = connection.getInputStream()
                val output = tempFile.outputStream()

                val data = ByteArray(8192)
                var total: Long = 0
                var count: Int

                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        onProgress(total.toFloat() / fileLength.toFloat())
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                if (tempFile.renameTo(targetFile)) {
                    true
                } else {
                    tempFile.delete()
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (tempFile.exists()) tempFile.delete()
                if (targetFile.exists()) targetFile.delete()
                false
            }
        }
    }
}
