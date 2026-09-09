package com.mpc.bioattend.database

import androidx.room.*
import com.mpc.bioattend.model.TimetableEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {

    @Query("SELECT * FROM timetable_slots ORDER BY startTime ASC")
    fun getAllSlots(): Flow<List<TimetableEntity>>

    @Query("SELECT * FROM timetable_slots WHERE dayOfWeek LIKE '%' || :dayOfWeek || '%' ORDER BY startTime ASC")
    fun getSlotsForDay(dayOfWeek: String): Flow<List<TimetableEntity>>

    @Query("SELECT * FROM timetable_slots WHERE id = :id LIMIT 1")
    suspend fun getSlotById(id: Long): TimetableEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlot(slot: TimetableEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(slots: List<TimetableEntity>)

    @Update
    suspend fun updateSlot(slot: TimetableEntity)

    @Delete
    suspend fun deleteSlot(slot: TimetableEntity)

    @Query("UPDATE timetable_slots SET attendedCount = attendedCount + 1 WHERE subjectCode = :subjectCode")
    suspend fun incrementAttendedCount(subjectCode: String)

    @Query("UPDATE timetable_slots SET attendedCount = :count WHERE id = :slotId")
    suspend fun setAttendedCountById(slotId: Long, count: Int)

    @Query("UPDATE timetable_slots SET attendedCount = :count WHERE subjectCode = :subjectCode OR subjectName LIKE '%' || :subjectName || '%'")
    suspend fun setAttendedCountBySubject(subjectCode: String, subjectName: String, count: Int)

    @Query("DELETE FROM timetable_slots")
    suspend fun deleteAllSlots()

    @Query("SELECT DISTINCT subjectCode FROM timetable_slots WHERE subjectCode IS NOT NULL AND subjectCode != '' ORDER BY subjectCode ASC")
    suspend fun getDistinctSubjectCodes(): List<String>

    @Query("SELECT DISTINCT subjectName FROM timetable_slots WHERE subjectName IS NOT NULL AND subjectName != '' ORDER BY subjectName ASC")
    suspend fun getDistinctSubjectNames(): List<String>

    @Query("SELECT DISTINCT facultyName FROM timetable_slots WHERE facultyName IS NOT NULL AND facultyName != '' ORDER BY facultyName ASC")
    suspend fun getDistinctFacultyNames(): List<String>

    @Query("SELECT DISTINCT classroom FROM timetable_slots WHERE classroom IS NOT NULL AND classroom != '' ORDER BY classroom ASC")
    suspend fun getDistinctClassrooms(): List<String>
}
