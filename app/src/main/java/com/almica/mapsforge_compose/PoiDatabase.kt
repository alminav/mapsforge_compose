package com.almica.mapsforge_compose

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PoiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoi(poi: PoiEntity): Long

    @Query("SELECT * FROM pois ORDER BY timestamp DESC")
    fun getAllPois(): Flow<List<PoiEntity>>

    @Delete
    suspend fun deletePoi(poi: PoiEntity): Int

    @Query("DELETE FROM pois WHERE id = :id")
    suspend fun deletePoiById(id: Long): Int
}

@Database(entities = [PoiEntity::class], version = 1, exportSchema = false)
abstract class PoiDatabase : RoomDatabase() {
    abstract fun poiDao(): PoiDao

    companion object {
        @Volatile
        private var INSTANCE: PoiDatabase? = null

        fun getDatabase(context: Context): PoiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PoiDatabase::class.java,
                    "poi_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
