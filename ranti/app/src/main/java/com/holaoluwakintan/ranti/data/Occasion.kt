package com.holaoluwakintan.ranti.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.holaoluwakintan.ranti.core.DateMath
import com.holaoluwakintan.ranti.core.LadderInput
import com.holaoluwakintan.ranti.core.OccasionType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "occasions")
data class Occasion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relationship: String = "",
    val type: String = OccasionType.BIRTHDAY.name,
    val customLabel: String = "",
    val month: Int,
    val day: Int,
    val year: Int? = null,
    val recurring: Boolean = true,
    val phone: String = "",
    val notes: String = "",
    /** v0.2: email (from contacts, the birthday link, or typed in). */
    val email: String = "",
    /** v0.2: send a birthday email automatically on the day (opt-in per person, birthdays only). */
    val autoEmail: Boolean = false,
    /** v0.2: the custom message used for the automatic email (blank = a warm default). */
    val emailNote: String = "",
    /** v0.2: where the person came from: "", "contacts" or "link". */
    val source: String = "",
    val giftIdea: String = "",
    /** Plan progress applies to this occurrence date (yyyy-MM-dd); resets automatically for the next one. */
    val planFor: String = "",
    val giftDone: Boolean = false,
    val messageDone: Boolean = false,
    val callDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val kind: OccasionType get() = OccasionType.from(type)

    val title: String get() = when (kind) {
        OccasionType.CUSTOM -> customLabel.ifBlank { "Special day" }
        else -> kind.label
    }

    fun next(today: LocalDate): LocalDate? = DateMath.nextOccurrence(month, day, year, recurring, today)

    fun ladderInput() = LadderInput(id, month, day, year, recurring, createdAt)

    fun planActive(occurrence: LocalDate?) = occurrence != null && planFor == occurrence.toString()
    fun gift(occ: LocalDate?) = planActive(occ) && giftDone
    fun message(occ: LocalDate?) = planActive(occ) && messageDone
    fun call(occ: LocalDate?) = planActive(occ) && callDone

    /** Returns a copy whose plan flags are scoped to [occ] (clearing stale ones from a past year). */
    fun scopedTo(occ: LocalDate): Occasion =
        if (planFor == occ.toString()) this
        else copy(planFor = occ.toString(), giftDone = false, messageDone = false, callDone = false)

    val firstName: String get() = name.trim().split(" ").firstOrNull().orEmpty().ifBlank { name }
}

@Dao
interface OccasionDao {
    @Query("SELECT * FROM occasions ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Occasion>>

    @Query("SELECT * FROM occasions")
    suspend fun all(): List<Occasion>

    @Query("SELECT * FROM occasions WHERE id = :id")
    suspend fun byId(id: Long): Occasion?

    @Insert
    suspend fun insert(o: Occasion): Long

    @Insert
    suspend fun insertAll(list: List<Occasion>)

    @Update
    suspend fun update(o: Occasion)

    @Delete
    suspend fun delete(o: Occasion)
}

/** v0.1 -> v0.2: four new columns, existing rows keep everything. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE occasions ADD COLUMN email TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE occasions ADD COLUMN autoEmail INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE occasions ADD COLUMN emailNote TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE occasions ADD COLUMN source TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [Occasion::class, Reminder::class], version = 3, exportSchema = true)
abstract class RantiDb : RoomDatabase() {
    abstract fun occasions(): OccasionDao
    abstract fun reminders(): ReminderDao

    companion object {
        @Volatile private var instance: RantiDb? = null
        fun get(ctx: Context): RantiDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, RantiDb::class.java, "ranti.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
