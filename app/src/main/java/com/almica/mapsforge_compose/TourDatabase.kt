package com.almica.mapsforge_compose

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TourDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTour(tour: TourEntity): Long

    @Query("SELECT * FROM tours ORDER BY timestamp DESC")
    fun getAllTours(): Flow<List<TourEntity>>

    @Update
    suspend fun updateTour(tour: TourEntity): Int

    @Delete
    suspend fun deleteTour(tour: TourEntity): Int
}

@Database(entities = [TourEntity::class], version = 2, exportSchema = false)
@TypeConverters(RoomTypeConverters::class)
abstract class TourDatabase : RoomDatabase() {
    abstract fun tourDao(): TourDao

    companion object {
        @Volatile
        private var INSTANCE: TourDatabase? = null

        fun getDatabase(context: Context): TourDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TourDatabase::class.java,
                    "tour_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
