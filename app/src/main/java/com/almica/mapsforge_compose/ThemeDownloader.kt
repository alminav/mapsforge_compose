package com.almica.mapsforge_compose

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ThemeDownloader {
    /**
     * Extracts the theme ZIP from the application assets
     * if it is missing from the local storage.
     * https://github.com/alminav/mapsforge_compose/blob/main/backups/renderthemes.zip not used
     *
     * @param context The context to access assets.
     * @param targetDir The directory where the theme should be stored.
     */
    suspend fun extractThemesIfMissing(context: Context, targetDir: File) {
        if (targetDir.exists() && targetDir.list()?.isNotEmpty() == true) return

        withContext(Dispatchers.IO) {
            try {
                targetDir.mkdirs()
                val zipInput = ZipInputStream(context.assets.open("renderthemes.zip"))
                
                var entry = zipInput.nextEntry
                while (entry != null) {
                    val newFile = targetDir.resolve(entry.name)
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
