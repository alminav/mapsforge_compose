package com.almica.mapsforge_compose.externalData

import android.content.Context
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * 28aug2026 direct download from magenta cloud
 */
class MagentaCloudDownloader(private val context: Context) {

    // Ktor Client initialisieren
    private val client = HttpClient(Android) {
        // Folgt HTTP-Weiterleitungen (Redirects) automatisch
        followRedirects = true
    }

    /**
     * Lädt eine Datei im Hintergrund herunter.
     * Muss aus einem CoroutineScope aufgerufen werden (z.B. viewModelScope).
     */
    suspend fun downloadFile(directDownloadUrl: String, targetFile: File): File? {
        // Wichtig: Sicherstellen, dass die URL auf /download endet
        //val directDownloadUrl = if (shareUrl.endsWith("/download")) shareUrl else "$shareUrl/download"

        // Wechselt den Thread-Kontext auf den I/O-Thread für Festplatten-/Netzwerkzugriffe
        return withContext(Dispatchers.IO) {
            try {
                // Ziel-Datei im internen Speicher der App definieren
                //val targetFile = File(context.filesDir, fileName)

                // HTTP-Anfrage vorbereiten und streamen
                client.prepareGet(directDownloadUrl).execute { response ->
                    if (response.status.value in 200..299) {
                        Timber.i("response.status.value: ${response.status.value}")
                        // Channel aus dem Response-Body als Java InputStream lesen
                        val channel = response.bodyAsChannel()
                        channel.toInputStream().use { inputStream ->
                            targetFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    } else {
                        throw Exception("Server response statuscode: ${response.status.value}")
                    }
                }

                // Rückgabe der erfolgreichen Datei
                targetFile
            } catch (e: Exception) {
                e.printStackTrace()
                null // Im Fehlerfall null zurückgeben
            }
        }
    }

    // Client schließen, wenn er nicht mehr benötigt wird
    fun close() {
        client.close()
    }
}
/**
 * Starts the download process for specific assets from MagentaCloud. 28aug2026
 */
/*
fun startDownload() {
    viewModelScope.launch {
        val link = "https://magentacloud.de/public.php/dav/files/axgAQy5F2fjcSCB/"

        val downloadedFile = downloader.downloadFile(link, "n52e0103d.ghz")

        if (downloadedFile != null) {
            Timber.i("Download successful: ${downloadedFile.absolutePath}")
        } else {
            Timber.e("Download failed.")
        }
    }
}
 */