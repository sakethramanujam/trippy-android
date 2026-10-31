package samalabs.trippy.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** The last good copy of a GET, so trips open on a plane. Partitioned by [Session.scope] like iOS's OfflineStore. */
@Entity(tableName = "responses", primaryKeys = ["scope", "path"])
data class CachedResponse(
    val scope: String,
    val path: String,
    val body: String,
    /** Epoch millis when the server sent it. */
    val savedAt: Long,
)

interface ResponseCache {
    suspend fun read(scope: String, path: String): CachedResponse?
    suspend fun write(entry: CachedResponse)
    /** Sign-out wipes every account's copy on this device. */
    suspend fun clear()

    object None : ResponseCache {
        override suspend fun read(scope: String, path: String): CachedResponse? = null
        override suspend fun write(entry: CachedResponse) = Unit
        override suspend fun clear() = Unit
    }
}

class MemoryResponseCache : ResponseCache {
    val entries = linkedMapOf<Pair<String, String>, CachedResponse>()
    override suspend fun read(scope: String, path: String) = entries[scope to path]
    override suspend fun write(entry: CachedResponse) { entries[entry.scope to entry.path] = entry }
    override suspend fun clear() = entries.clear()
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM responses WHERE scope = :scope AND path = :path")
    suspend fun read(scope: String, path: String): CachedResponse?

    @Upsert
    suspend fun write(entry: CachedResponse)

    @Query("DELETE FROM responses")
    suspend fun clear()
}

@Database(entities = [CachedResponse::class], version = 1, exportSchema = false)
abstract class TrippyDatabase : RoomDatabase() {
    abstract fun cache(): CacheDao

    companion object {
        fun open(context: Context): TrippyDatabase =
            Room.databaseBuilder(context, TrippyDatabase::class.java, "trippy-offline.db").fallbackToDestructiveMigration(true).build()
    }
}

class RoomResponseCache(private val dao: CacheDao) : ResponseCache {
    override suspend fun read(scope: String, path: String) = runCatching { dao.read(scope, path) }.getOrNull()
    override suspend fun write(entry: CachedResponse) { runCatching { dao.write(entry) } }
    override suspend fun clear() { runCatching { dao.clear() } }
}
