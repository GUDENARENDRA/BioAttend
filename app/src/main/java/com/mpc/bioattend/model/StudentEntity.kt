package com.mpc.bioattend.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey(autoGenerate = true)
    val srNo: Long = 0,
    val studentId: String,
    val name: String,
    val classSem: String,
    val grNo: String,
    val enrollmentNo: String,
    val fingerprintId: Int = -1, // -1 means Not Enrolled
    val totalAttendedClasses: Int = 0
)
