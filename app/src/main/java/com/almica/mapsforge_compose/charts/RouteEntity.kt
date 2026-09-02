package com.almica.mapsforge_compose.charts

import android.graphics.Bitmap
import androidx.room.Entity
import androidx.room.PrimaryKey

import java.util.UUID

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey var id: UUID = UUID.randomUUID(),
    var name: String = "",
    var region: String = "",
    var latitudeStart: Double = 0.0,
    var longitudeStart: Double = 0.0,
    var latitudeCenter: Double = 0.0,
    var longitudeCenter: Double = 0.0,
    var latitudeStop: Double = 0.0,
    var longitudeStop: Double = 0.0,
    var distance: Double = 0.0,
    var kmlString: String = "",
    var bitmap: Bitmap? = null
){
    constructor(name: String, region: String, latitudeStart: Double, longitudeStart: Double,
                latitudeCenter: Double, longitudeCenter: Double,
                latitudeStop: Double, longitudeStop: Double,
                distance: Double, kmlString: String, bitmap: Bitmap) : this() {
        this.id = UUID.randomUUID()
        this.region = region
        this.name = name
        this.latitudeStart = latitudeStart
        this.longitudeStart = longitudeStart
        this.latitudeCenter = latitudeCenter
        this.longitudeCenter = longitudeCenter
        this.distance = distance
        this.kmlString = kmlString
        this.bitmap = bitmap
    }

    override fun toString(): String {
        return "$name ${Const.UC_POSITION}${latitudeStart.format(4)} ${longitudeStart.format(4)} " +
                "${Const.UC_DISTANCE_ARROW}${distance.formatDistM(true)} ${Const.UC_REGION}$region\n"
    }
    fun getString(): String {
        return "$name ${Const.UC_POSITION}${latitudeStart.format(4)} ${longitudeStart.format(4)} " +
                "${Const.UC_DISTANCE_ARROW}${distance.formatDistM(true)} ${Const.UC_REGION}$region\n"
    }
    fun getLine(): String {
        return "${Const.UC_POSITION}${latitudeStart.format(4)} ${longitudeStart.format(4)} " +
                "${Const.UC_DISTANCE_ARROW}${distance.formatDistM(true)} ${Const.UC_REGION}$region"
    }

    companion object {}
}

