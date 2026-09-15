package com.almica.mapsforge_compose.gh

import android.content.Context
import android.net.Uri
import com.almica.mapsforge_compose.RoutePoint
import com.almica.mapsforge_compose.externalData.MagentaCloud
import com.almica.mapsforge_compose.externalData.MagentaCloudDownloader
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private const val logtag = "HgtReader"
/**
 * Class HgtReader reads data from SRTM HGT files. Currently, this class is restricted to a resolution of 3 arc seconds.
 *
 *
 * SRTM data files are available at the [NASA SRTM site](http://dds.cr.usgs.gov/srtm/version2_1/SRTM3)
 *
 * @author Oliver Wieland &lt;oliver.wieland@online.de&gt;
 *
 * 14sep2026
 * The HgtReader class has been updated to correctly handle routes that span multiple SRTM (.hgt) files.
 * Previously, the class was limited by a single bounds check and would only process points within a specific file
 * if one was provided in the constructor.
 */
class HgtReader(private val context: Context, private var hgtFile: File?) {
    private val cache = HashMap<String, ShortBuffer?>()
    private var instanceBounds: LatLngBounds? = null

    init {
        hgtFile?.let {
            instanceBounds = getTileRect(it.name.replace(Const.HGT_EXT, ""))
        }
        val hgtFolder = File(context.getExternalFilesDir(null), Const.HGT_FOLDER_NAME)
        val hgtFiles: Array<File>? = hgtFolder.listFiles { _, name -> name.endsWith(Const.HGT_EXT) }

        synchronized(hgtNames) {
            hgtNames.clear()
            hgtFiles?.forEach { file ->
                val hgtName = file.name.replace(Const.HGT_EXT, "")
                hgtNames.add(hgtName)
            }
        }
    }

    fun getElevationFromHgt(position: LatLng): Double {
        val hgtFolder = File(context.getExternalFilesDir(null), Const.HGT_FOLDER_NAME)

        // 1. Check if the provided hgtFile matches
        hgtFile?.let { file ->
            val tileName = file.name.replace(Const.HGT_EXT, "")
            if (getTileRect(tileName)?.contains(position) == true) {
                return tryReadElevation(position, file)
            }
        }

        // 2. Fallback: search in all available hgt files
        val names = synchronized(hgtNames) { ArrayList(hgtNames) }
        for (hgtName in names) {
            val rect = getTileRect(hgtName)
            if (rect?.contains(position) == true) {
                val file = File(hgtFolder, "$hgtName${Const.HGT_EXT}")
                if (file.exists()) {
                    return tryReadElevation(position, file)
                }
            }
        }

        return NO_ELEVATION
    }

    private fun tryReadElevation(position: LatLng, file: File): Double {
        try {
            if (!cache.containsKey(file.path)) {
                cache[file.path] = null
                if (file.exists()) {
                    val data = readHgtFile(file.path)
                    cache[file.path] = data
                }
            }
            return readElevation(position, file)
        } catch (e: Exception) {
            Timber.e(e, "Error reading elevation from ${file.name}")
            return NO_ELEVATION
        }
    }

    @Throws(Exception::class)
    private fun readHgtFile(file: String): ShortBuffer? {
        var fc: FileChannel? = null
        try {
            fc = FileInputStream(file).channel
            val bb = ByteBuffer.allocateDirect(fc.size().toInt())
            while (bb.remaining() > 0) {
                if (fc.read(bb) == -1) break
            }
            bb.flip()
            return bb.order(ByteOrder.BIG_ENDIAN).asShortBuffer()
        } finally {
            fc?.close()
        }
    }

    fun readElevation(position: LatLng, localHgtFile: File?): Double {
        val tag = localHgtFile?.path
        val sb = cache[tag] ?: return NO_ELEVATION

        val fLat = frac(position.latitude) * SECONDS_PER_MINUTE
        val fLon = frac(position.longitude) * SECONDS_PER_MINUTE

        var row = (fLat * SECONDS_PER_MINUTE / HGT_RES).roundToInt()
        val col = (fLon * SECONDS_PER_MINUTE / HGT_RES).roundToInt()

        row = HGT_ROW_LENGTH - row
        val cell = (HGT_ROW_LENGTH * (row - 1)) + col

        if (cell >= 0 && cell < sb.limit()) {
            val ele = sb[cell]
            return if (ele.toInt() == HGT_VOID) 0.0 else ele.toDouble()
        }
        return NO_ELEVATION
    }

    fun contains(latLng: LatLng): Boolean {
        if (instanceBounds?.contains(latLng) == true) return true
        val names = synchronized(hgtNames) { ArrayList(hgtNames) }
        return names.any { getTileRect(it)?.contains(latLng) == true }
    }

    data class SrtmRefresh(
        val hMax: Double,
        val routePoints: List<RoutePoint>?,
        val usedHgtFiles: Set<String> = emptySet(),
        val missingHgtFiles: Set<String> = emptySet(),
        val hasDownloaded: Int = 0
    )

    fun refreshRouteElevationFromSrtm(routePoints: List<RoutePoint>?, withDownload: Boolean = false): SrtmRefresh {
        var hMax = 0.0
        val usedFiles = mutableSetOf<String>()
        val missingFiles = mutableSetOf<String>()

        val resultLllh = routePoints?.map { point ->
            val latLng = LatLng(point.latitude, point.longitude)
            val srtmAltitude = getElevationFromHgt(latLng)
            val tileName = getTileName(point.latitude, point.longitude)

            if (srtmAltitude != NO_ELEVATION) {
                hMax = maxOf(hMax, srtmAltitude)
                usedFiles.add(tileName)
                RoutePoint(point.latitude, point.longitude, srtmAltitude)
            } else {
                missingFiles.add(tileName)
                RoutePoint(point.latitude, point.longitude, maxOf(0.0, point.altitude))
            }
        }
        Timber.i("HgtReader missingFiles: $missingFiles")
        val remoteHgtFiles = mutableSetOf<String>()
        missingFiles.forEach { tileName ->
            val fileName = tileName.lowercase() + Const.HGT_TAG + Const.ZIP_EXT
            if (MagentaCloud.hgt[fileName] != null) {
                remoteHgtFiles.add(fileName)
            }
        }

        if (withDownload && remoteHgtFiles.isNotEmpty()) {
            Timber.i("Downloading missing files: $remoteHgtFiles")
            val downloader = MagentaCloudDownloader(context)
            CoroutineScope(Dispatchers.IO).launch {
                remoteHgtFiles.forEach { fileName ->
                    val remotePath = MagentaCloud.hgt[fileName]
                    if (remotePath != null) {
                        val destination = context.cacheDir
                        if (!destination.exists()) destination.mkdirs()
                        val downloadedFile = downloader.downloadFile(remotePath, File(destination, fileName))
                        if (downloadedFile != null && downloadedFile.exists()) {
                            try {
                                val hgtRootDir = context.getExternalFilesDir(null)?.resolve(Const.HGT_FOLDER_NAME)
                                if (hgtRootDir != null) {
                                    GhHelper.unzipFile(context, Uri.fromFile(downloadedFile), hgtRootDir, flatten = true)
                                }
                                downloadedFile.delete()
                                Timber.i("Successfully unzipped and deleted ${downloadedFile.path}")
                            } catch (e: Exception) {
                                Timber.e(e, "Error unzipping $fileName")
                            }
                        }
                    }
                }
                downloader.close()
            }
        }
        return SrtmRefresh(hMax, resultLllh, usedFiles, missingFiles,
            hasDownloaded = if (withDownload) remoteHgtFiles.size else 0)
    }

    companion object {
        private const val SECONDS_PER_MINUTE = 60

        // alter these values for different SRTM resolutions
        private const val HGT_RES = 3 // resolution in arc seconds
        private const val HGT_ROW_LENGTH = 1201 // number of elevation values per line
        private const val HGT_VOID = -32768 // magic number which indicates 'void data' in HGT file

        var NO_ELEVATION: Double = Int.MIN_VALUE.toDouble()

        private fun frac(d: Double): Double = d - floor(d)

        fun getTileName(lat: Double, lon: Double): String {
            val latPrefix = if (lat >= 0) "N" else "S"
            val latVal = abs(floor(lat).toInt())
            val lonPrefix = if (lon >= 0) "E" else "W"
            val lonVal = abs(floor(lon).toInt())
            return String.format(Locale.US, "%s%02d%s%03d", latPrefix, latVal, lonPrefix, lonVal)
        }

        val hgtNames = ArrayList<String>()
    }
}

fun getTileRect(tileName: String): LatLngBounds? {
    try {
        if (tileName.length < 7) return null

        var lat = tileName.substring(1, 3).toDouble()
        if (tileName.startsWith("S", ignoreCase = true)) lat *= -1

        var lon = tileName.substring(4, 7).toDouble()
        if (tileName[3].toString().equals("W", ignoreCase = true)) lon *= -1

        return LatLngBounds.Builder()
            .include(LatLng(lat, lon))
            .include(LatLng(lat + 1.0, lon + 1.0))
            .build()
    } catch (e: Exception) {
        return null
    }
}
