package com.mpc.bioattend.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timetable_slots")
data class TimetableEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dayOfWeek: String, // MON, TUE, WED, THU, FRI, SAT
    val startTime: String, // e.g. "09:00", "11:00"
    val endTime: String,   // e.g. "10:30", "12:00"
    val subjectCode: String, // e.g. "MPC", "PE", "VPD"
    val subjectName: String, // e.g. "Mobile and Pervasive Computing"
    val facultyInitials: String, // e.g. "MS", "SA", "KL"
    val facultyName: String, // e.g. "Dr. Mitesh Solanki"
    val classroom: String, // e.g. "MA106", "MA115"
    val attendedCount: Int = 0
)
