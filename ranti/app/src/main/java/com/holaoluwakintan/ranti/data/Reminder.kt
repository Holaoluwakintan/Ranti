package com.holaoluwakintan.ranti.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.holaoluwakintan.ranti.core.Repeat
import kotlinx.coroutines.flow.Flow

/** v1.0: a plain reminder ("Call mum Friday 6pm", "Pay rent every month on the 1st"). */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    /** When it rings next (epoch millis, the phone's time zone at the time it was set). */
    val dueAt: Long,
    /** A [Repeat] name. */
    val repeat: String = Repeat.NONE.name,
    /** Day of month kept for monthly/yearly repeats (so "the 31st" survives short months). 0 = from dueAt. */
    val anchorDay: Int = 0,
    /** Ticked off (one-off reminders only; repeating ones just move on). */
    val done: Boolean = false,
    /** When it last rang for the current [dueAt] (0 = not yet). Stops double notifications. */
    val firedAt: Long = 0,
    /** A snoozed birthday wish points at its person (0 = a plain reminder). */
    val occasionId: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val repeatKind: Repeat get() = Repeat.from(repeat)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY done, dueAt")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun byId(id: Long): Reminder?

    @Insert
    suspend fun insert(r: Reminder): Long

    @Insert
    suspend fun insertAll(list: List<Reminder>)

    @Update
    suspend fun update(r: Reminder)

    @Delete
    suspend fun delete(r: Reminder)

    @Query("DELETE FROM reminders WHERE done = 1")
    suspend fun clearDone()
}

/** v0.3 (schema 2) -> v1.0 (schema 3): adds the reminders table. Nothing existing is touched. */
const val REMINDERS_CREATE_SQL = "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
    "`title` TEXT NOT NULL, `note` TEXT NOT NULL, `dueAt` INTEGER NOT NULL, `repeat` TEXT NOT NULL, `anchorDay` INTEGER NOT NULL, " +
    "`done` INTEGER NOT NULL, `firedAt` INTEGER NOT NULL, `occasionId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(REMINDERS_CREATE_SQL)
    }
}
