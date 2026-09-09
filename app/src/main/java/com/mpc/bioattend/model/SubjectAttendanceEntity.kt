package com.mpc.bioattend.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subject_attendance")
data class SubjectAttendanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectCode: String,
    val subjectName: String,
    val studentSrNo: Long,
    val fingerprintId: Int,
    val studentName: String,
    val enrollmentNo: String,
    val classSem: String,
    val grNo: String,
    val timestamp: String,
    val dateStr: String
)
