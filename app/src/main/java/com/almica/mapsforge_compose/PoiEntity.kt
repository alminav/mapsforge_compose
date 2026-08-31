package com.almica.mapsforge_compose

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pois")
data class PoiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val description: String? = null,
    val latitude: Double,
    val longitude: Double,
    val type: String? = null, // e.g., "favorite", "peak", "refuge"
    val timestamp: Long = System.currentTimeMillis()
)
