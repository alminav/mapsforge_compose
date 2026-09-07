package com.almica.mapsforge_compose.gh

import android.content.Context
import com.almica.mapsforge_compose.charts.LatLngH
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Class HgtReader reads data from SRTM HGT files. Currently, this class is restricted to a resolution of 3 arc seconds.
 *
 *
 * SRTM data files are available at the [NASA SRTM site](http://dds.cr.usgs.gov/srtm/version2_1/SRTM3)
 *
 * @author Oliver Wieland &lt;oliver.wieland@online.de&gt;
 */
class HgtReader(private val context: Context, private var hgtFile: File?) {
    private val cache = HashMap<String, ShortBuffer?>()
    init {
        hgtFile?.let {
            Timber.i( "${it.path} ${it.exists()}")
            bounds = getTileRect(it.name.replace(Const.HGT_EXT, ""))
            Timber.i( "bounds: $bounds")
            loadHgtFileToCache(it)
        }
    }

    fun getElevationFromHgt(position: LatLng): Double {
        val hgtFolder = File(context.getExternalFilesDir(null), Const.HGT_FOLDER_NAME)
        
        // 1. Try the primary hgtFile first if it covers the position
        hgtFile?.let { file ->
            val tileName = file.name.replace(Const.HGT_EXT, "")
            if (getTileRect(tileName)?.contains(position) == true) {
                return readElevation(position, file)
            }
        }

        // 2. Otherwise, look for the correct tile by name
        val expectedTileName = getTileName(position.latitude, position.longitude)
        val specificHgtFile = File(hgtFolder, "$expectedTileName${Const.HGT_EXT}")
        
        if (specificHgtFile.exists()) {
            return try {
                readElevation(position, specificHgtFile)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get elevation from HGT for $position")
                0.0
            }
        }
        return 0.0
    }

    private fun loadHgtFileToCache(file: File) {
        val path = file.path
        if (cache.containsKey(path)) return

        // Marker to indicate 'file has been searched'
        cache[path] = null

        if (file.exists()) {
            try {
                val data = readHgtFile(path)
                cache[path] = data
            } catch (e: Exception) {
                Timber.e(e, "Failed to load HGT file: $path")
            }
        } else {
            Timber.i("HGT file not found: $path")
        }
    }

    private fun readHgtFile(path: String): ShortBuffer? {
        return FileInputStream(path).use { fis ->
            val channel = fis.channel
            val byteBuffer = ByteBuffer.allocateDirect(channel.size().toInt())
            while (byteBuffer.remaining() > 0) {
                if (channel.read(byteBuffer) == -1) break
            }
            byteBuffer.flip()
            byteBuffer.order(ByteOrder.BIG_ENDIAN).asShortBuffer()
        }
    }

    /**
     * Reads the elevation value for the given coordinate.
     *
     * @param position the coordinate to get the elevation data for
     * @param localHgtFile the HGT file to read from
     * @return the elevation value or `NO_ELEVATION`, if no value is present
     */
    fun readElevation(position: LatLng, localHgtFile: File?): Double {
        val file = localHgtFile ?: return NO_ELEVATION
        loadHgtFileToCache(file)
        
        val sb = cache[file.path] ?: return NO_ELEVATION

        val lat = position.latitude
        val lon = position.longitude

        // SRTM tiles are named by their south-west corner.
        // Example: N45E008 covers lat [45, 46] and lon [8, 9]
        val latMin = Math.floor(lat)
        val lonMin = Math.floor(lon)

        // Calculate relative position within the tile (0.0 to 1.0)
        val relLat = lat - latMin
        val relLon = lon - lonMin

        // HGT rows go North -> South (row 0 is at latMin + 1.0)
        // HGT cols go West -> East (col 0 is at lonMin)
        val row = ((1.0 - relLat) * (HGT_ROW_LENGTH - 1)).roundToInt()
        val col = (relLon * (HGT_ROW_LENGTH - 1)).roundToInt()

        val index = row * HGT_ROW_LENGTH + col

        if (index in 0 until sb.limit()) {
            val ele = sb[index].toInt()
            // check for data voids
            return if (ele == HGT_VOID) 0.0 else ele.toDouble()
        }
        return 0.0
    }

    fun contains(latLng: LatLng) : Boolean {
        return bounds != null && bounds?.contains(latLng) == true
    }

    data class SrtmRefresh(val hMax: Double, val lllh: List<LatLngH>?)
    fun refreshRouteElevationFromSrtm(lllh: List<LatLngH>?) : SrtmRefresh {
        var hMax = 0.0
        val resultLllh = lllh?.let {
            //Timber.i( "lllh: ${lllh.size}")
            List(it.size) {index ->
                val latLng = LatLng(lllh[index].latitude, lllh[index].longitude)
                if (contains(latLng)) {
                    val srtmAltitude = getElevationFromHgt(latLng)
                    hMax = hMax.coerceAtLeast(srtmAltitude)
                    LatLngH(latLng.latitude, latLng.longitude, getElevationFromHgt(latLng))
                } else
                    LatLngH(latLng.latitude, latLng.longitude,
                        0.0.coerceAtLeast(lllh[index].altitude)
                    )
            }
        }
        return SrtmRefresh(hMax, resultLllh)
    }

    companion object {
        // alter these values for different SRTM resolutions
        private const val HGT_ROW_LENGTH = 1201 // number of elevation values per line
        private const val HGT_VOID = -32768 // magic number which indicates 'void data' in HGT file

        var NO_ELEVATION: Double = Double.NaN

        var bounds : LatLngBounds? = null
    }
}

fun getTileRect(tileName: String): LatLngBounds? {
    try {
        // Expected format: N45E008 or S45W008
        val latChar = tileName[0].uppercaseChar()
        var lat = tileName.substring(1, 3).toDouble()
        if (latChar == 'S') lat *= -1

        val lonChar = tileName[3].uppercaseChar()
        var lon = tileName.substring(4, 7).toDouble()
        if (lonChar == 'W') lon *= -1

        val latLngBounds = LatLngBounds.Builder()
        latLngBounds.include(LatLng(lat.coerceIn(-90.0, 90.0), lon.coerceIn(-180.0, 180.0)))
        latLngBounds.include(LatLng((lat + 1.0).coerceIn(-90.0, 90.0), (lon + 1.0).coerceIn(-180.0, 180.0)))
        return latLngBounds.build()
    } catch (e: Exception) {
        Timber.i( "$tileName parse error: ${e.message}")
        return null
    }
}


fun getTileName(lat: Double, lon: Double): String {
    val latInt = Math.floor(lat).toInt()
    val lonInt = Math.floor(lon).toInt()
    val latChar = if (latInt >= 0) 'N' else 'S'
    val lonChar = if (lonInt >= 0) 'E' else 'W'
    return String.format(Locale.US, "%c%02d%c%03d", latChar, Math.abs(latInt), lonChar, Math.abs(lonInt))
}
