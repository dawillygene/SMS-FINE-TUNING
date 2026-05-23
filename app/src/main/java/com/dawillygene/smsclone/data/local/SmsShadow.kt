package com.dawillygene.smsclone.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Room
import android.content.Context

@Entity(tableName = "sms_shadow")
data class SmsShadow(
    @PrimaryKey val id: Long,
    val address: String,
    val body: String?,
    val date: Long,
    val type: Int,
    val threadId: Long
)

@Dao
interface SmsShadowDao {
    @Query("SELECT * FROM sms_shadow")
    suspend fun getAll(): List<SmsShadow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sms: SmsShadow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(smsList: List<SmsShadow>)

    @Query("DELETE FROM sms_shadow WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM sms_shadow WHERE id = :id")
    suspend fun getById(id: Long): SmsShadow?
}

@Database(entities = [SmsShadow::class], version = 1)
abstract class ShadowDatabase : RoomDatabase() {
    abstract fun smsShadowDao(): SmsShadowDao

    companion object {
        @Volatile
        private var INSTANCE: ShadowDatabase? = null

        fun getDatabase(context: Context): ShadowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShadowDatabase::class.java,
                    "shadow_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
