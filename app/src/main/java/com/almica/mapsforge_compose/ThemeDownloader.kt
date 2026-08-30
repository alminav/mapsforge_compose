package com.almica.mapsforge_compose

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

object ThemeDownloader {
    private const val THEME_ZIP_URL = "https://github.com/mapsforge/mapsforge/raw/master/mapsforge-themes/src/main/resources/osmarender/osmarender.zip"

    suspend fun downloadThemeIfMissing(targetDir: File): File? {
        val themeXml = File(targetDir, "osmarender/osmarender.xml")
        if (themeXml.exists()) return themeXml

        return withContext(Dispatchers.IO) {
            try {
                targetDir.mkdirs()
                val url = URL(THEME_ZIP_URL)
                val connection = url.openConnection()
                val zipInput = ZipInputStream(connection.getInputStream())
                
                var entry = zipInput.nextEntry
                while (entry != null) {
                    val newFile = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            zipInput.copyTo(fos)
                        }
                    }
                    zipInput.closeEntry()
                    entry = zipInput.nextEntry
                }
                zipInput.close()
                if (themeXml.exists()) themeXml else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
