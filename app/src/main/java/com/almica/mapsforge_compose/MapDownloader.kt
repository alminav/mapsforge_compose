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
        Timber.i("downloadMapIfMissing: url=$urlStr, target=${targetFile.absolutePath}")
        if (isMapFileValid(targetFile)) {
            Timber.i("Map file already exists and is valid, skipping download")
            return true
        }
        
        if (urlStr.isEmpty()) {
            Timber.w("Download URL is empty, cannot download")
            return false
        }

        return withContext(Dispatchers.IO) {
            val tempFile = File(targetFile.parent, targetFile.name + ".tmp")
            try {
                Timber.d("Starting download from $urlStr to ${tempFile.absolutePath}")
                if (tempFile.exists()) {
                    Timber.d("Deleting existing temp file")
                    tempFile.delete()
                }
                
                val url = URL(urlStr)
                val connection = url.openConnection()
                connection.connect()

                val fileLength = connection.contentLength
                Timber.d("File length: $fileLength")
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

                Timber.d("Download finished, renaming temp file to ${targetFile.name}")
                if (tempFile.renameTo(targetFile)) {
                    Timber.i("Map download successful: ${targetFile.name}")
                    true
                } else {
                    Timber.e("Failed to rename temp file to ${targetFile.name}")
                    tempFile.delete()
                    false
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during map download")
                if (tempFile.exists()) tempFile.delete()
                if (targetFile.exists()) targetFile.delete()
                false
            }
        }
    }
}
