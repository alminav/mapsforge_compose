package com.almica.mapsforge_compose

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

object MapDownloader {
    suspend fun downloadMapIfMissing(urlStr: String, targetFile: File, onProgress: (Float) -> Unit): Boolean {
        if (targetFile.exists() && targetFile.length() > 0) return true

        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlStr)
                val connection = url.openConnection()
                connection.connect()

                val fileLength = connection.contentLength
                val input = connection.getInputStream()
                val output = targetFile.outputStream()

                val data = ByteArray(4096)
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
                true
            } catch (e: Exception) {
                e.printStackTrace()
                if (targetFile.exists()) targetFile.delete()
                false
            }
        }
    }
}
