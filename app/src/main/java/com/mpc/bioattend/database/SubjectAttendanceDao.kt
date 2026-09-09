package com.mpc.bioattend.database

import androidx.room.*
import com.mpc.bioattend.model.SubjectAttendanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectAttendanceDao {

    @Query("SELECT * FROM subject_attendance WHERE subjectCode = :subjectCode ORDER BY id DESC")
    fun getAttendanceForSubject(subjectCode: String): Flow<List<SubjectAttendanceEntity>>

    @Query("SELECT * FROM subject_attendance WHERE subjectCode = :subjectCode ORDER BY id DESC")
    suspend fun getAttendanceListForSubject(subjectCode: String): List<SubjectAttendanceEntity>

    @Query("SELECT * FROM subject_attendance WHERE subjectCode = :subjectCode AND (studentSrNo = :srNo OR fingerprintId = :fingerId) LIMIT 1")
    suspend fun findRecordForSubject(subjectCode: String, srNo: Long, fingerId: Int): SubjectAttendanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: SubjectAttendanceEntity): Long

    @Query("DELETE FROM subject_attendance WHERE subjectCode = :subjectCode")
    suspend fun clearAttendanceForSubject(subjectCode: String)

    @Query("DELETE FROM subject_attendance")
    suspend fun clearAllAttendance()
}
