package com.vincentwill.calm.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update

/** One stored message (or one whole notification for non-messaging apps) with its decision. */
@Entity(
    tableName = "notifications",
    indices = [Index(value = ["dedupHash"], unique = true), Index("receivedAt"), Index("urgent", "seen")],
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupHash: String,
    val notificationKey: String,
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String,
    val conversationTitle: String?,
    val sender: String?,
    val isGroup: Boolean,
    val category: String?,
    /** When the message was sent, according to the source app. */
    val postTime: Long,
    /** When Calm processed it. */
    val receivedAt: Long,
    val urgent: Boolean,
    val reason: String,
    /** [com.vincentwill.calm.classify.DecisionSource] name. */
    val decisionSource: String,
    /** [com.vincentwill.calm.classify.DecisionStep] name. */
    val decisionStep: String,
    /** Urgent: re-posted. Held: included in a digest summary. */
    val delivered: Boolean,
    /** Held items leave the digest screen once seen. */
    val seen: Boolean = false,
    /** [com.vincentwill.calm.classify.Feedback] name, if the user said the decision was wrong. */
    val feedback: String? = null,
)

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val matchType: String,
    val pattern: String,
    val packageName: String?,
    val action: String,
    val createdAt: Long,
)

@Entity(tableName = "digest_times", indices = [Index(value = ["scheduleGroup", "minuteOfDay"], unique = true)])
data class DigestTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleGroup: String,
    val minuteOfDay: Int,
    val enabled: Boolean,
)

data class AppCount(val packageName: String, val appLabel: String, val count: Int)

data class KeyCount(val key: String, val count: Int)

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<NotificationEntity>): List<Long>

    @Query("SELECT dedupHash FROM notifications WHERE dedupHash IN (:hashes)")
    suspend fun existingHashes(hashes: List<String>): List<String>

    @Query("SELECT * FROM notifications ORDER BY receivedAt DESC, id DESC LIMIT :limit")
    suspend fun history(limit: Int): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE urgent = 0 AND seen = 0 ORDER BY postTime DESC")
    suspend fun unseenHeld(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE urgent = 0 AND seen = 0 AND delivered = 0")
    suspend fun undeliveredHeld(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun byId(id: Long): NotificationEntity?

    @Query("UPDATE notifications SET delivered = 1 WHERE id IN (:ids)")
    suspend fun markDelivered(ids: List<Long>)

    @Query("UPDATE notifications SET seen = 1, delivered = 1 WHERE id IN (:ids)")
    suspend fun markSeen(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM notifications WHERE receivedAt >= :since AND urgent = :urgent")
    suspend fun countSince(since: Long, urgent: Boolean): Int

    @Query(
        "SELECT packageName, appLabel, COUNT(*) AS count FROM notifications " +
            "GROUP BY packageName ORDER BY count DESC",
    )
    suspend fun appsSeen(): List<AppCount>

    @Query("UPDATE notifications SET feedback = :feedback WHERE id = :id")
    suspend fun setFeedback(id: Long, feedback: String?)

    @Query("SELECT decisionSource AS `key`, COUNT(*) AS count FROM notifications WHERE receivedAt >= :since GROUP BY decisionSource")
    suspend fun countBySource(since: Long): List<KeyCount>

    @Query("SELECT feedback AS `key`, COUNT(*) AS count FROM notifications WHERE receivedAt >= :since AND feedback IS NOT NULL GROUP BY feedback")
    suspend fun countByFeedback(since: Long): List<KeyCount>

    @Query(
        "SELECT packageName, appLabel, COUNT(*) AS count FROM notifications " +
            "WHERE receivedAt >= :since AND urgent = :urgent GROUP BY packageName ORDER BY count DESC LIMIT :limit",
    )
    suspend fun topApps(since: Long, urgent: Boolean, limit: Int): List<AppCount>

    /** Held items that were never seen are kept: deleting them would lose notifications silently. */
    @Query("DELETE FROM notifications WHERE receivedAt < :before AND NOT (urgent = 0 AND seen = 0)")
    suspend fun deleteOlderThan(before: Long): Int
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY createdAt")
    suspend fun all(): List<RuleEntity>

    @Insert
    suspend fun insert(rule: RuleEntity): Long

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface DigestTimeDao {
    @Query("SELECT * FROM digest_times ORDER BY minuteOfDay")
    suspend fun all(): List<DigestTimeEntity>

    @Query("SELECT * FROM digest_times WHERE scheduleGroup = :group ORDER BY minuteOfDay")
    suspend fun inGroup(group: String): List<DigestTimeEntity>

    @Query("SELECT * FROM digest_times WHERE id = :id")
    suspend fun byId(id: Long): DigestTimeEntity?

    /** Returns -1 if the time already exists in the group. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(time: DigestTimeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(times: List<DigestTimeEntity>)

    @Update
    suspend fun update(time: DigestTimeEntity)

    @Query("DELETE FROM digest_times WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM digest_times WHERE scheduleGroup = :group")
    suspend fun deleteGroup(group: String)
}

@Database(
    entities = [NotificationEntity::class, RuleEntity::class, DigestTimeEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class CalmDatabase : RoomDatabase() {
    abstract fun notifications(): NotificationDao
    abstract fun rules(): RuleDao
    abstract fun digestTimes(): DigestTimeDao

    companion object {
        fun create(context: Context): CalmDatabase =
            Room.databaseBuilder(context, CalmDatabase::class.java, "calm.db").build()
    }
}
