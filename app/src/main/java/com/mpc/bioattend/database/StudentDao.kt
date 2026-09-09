package com.mpc.bioattend.database

import androidx.room.*
import com.mpc.bioattend.model.StudentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {

    @Query("SELECT * FROM students ORDER BY srNo ASC")
    fun getAllStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students ORDER BY CASE WHEN fingerprintId < 0 THEN 999999 ELSE fingerprintId END ASC, srNo ASC")
    fun getAllStudentsSortedByFingerprintAsc(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students ORDER BY srNo ASC")
    suspend fun getAllStudentsList(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE studentId = :studentId LIMIT 1")
    suspend fun getStudentByStudentId(studentId: String): StudentEntity?

    @Query("SELECT * FROM students WHERE srNo = :srNo LIMIT 1")
    suspend fun getStudentBySrNo(srNo: Long): StudentEntity?

    @Query("SELECT * FROM students WHERE fingerprintId = :fingerprintId AND fingerprintId > 0 LIMIT 1")
    suspend fun getStudentByFingerprintId(fingerprintId: Int): StudentEntity?

    @Query("SELECT COUNT(*) FROM students WHERE fingerprintId = :fingerprintId AND fingerprintId > 0")
    suspend fun countFingerprintId(fingerprintId: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: StudentEntity): Long

    @Update
    suspend fun updateStudent(student: StudentEntity)

    @Delete
    suspend fun deleteStudent(student: StudentEntity)

    @Query("DELETE FROM students WHERE srNo = :srNo")
    suspend fun deleteStudentBySrNo(srNo: Long)

    @Query("DELETE FROM students")
    suspend fun deleteAllStudents()

    @Query("UPDATE students SET fingerprintId = -1")
    suspend fun resetAllFingerprintIds()

    @Query("UPDATE students SET totalAttendedClasses = totalAttendedClasses + 1 WHERE fingerprintId = :fingerprintId AND fingerprintId > 0")
    suspend fun markAttendanceByFingerprint(fingerprintId: Int): Int

    @Query("SELECT DISTINCT classSem FROM students WHERE classSem IS NOT NULL AND classSem != '' ORDER BY classSem ASC")
    suspend fun getDistinctClassSemList(): List<String>

    @Query("SELECT DISTINCT grNo FROM students WHERE grNo IS NOT NULL AND grNo != '' ORDER BY grNo ASC")
    suspend fun getDistinctGrNoList(): List<String>
}
