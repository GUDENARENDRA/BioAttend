package com.mpc.bioattend.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mpc.bioattend.R
import com.mpc.bioattend.database.AppDatabase
import com.mpc.bioattend.model.StudentEntity
import com.mpc.bioattend.model.SubjectAttendanceEntity
import com.mpc.bioattend.network.RetrofitClient
import com.mpc.bioattend.util.ZoomableFrameLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ClassAttendanceActivity : AppCompatActivity() {

    private lateinit var tvSubjectHeader: TextView
    private lateinit var tvClassDetails: TextView
    private lateinit var btnStartFingerScan: Button
    private lateinit var btnExportSessionCsv: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvSessionStatus: TextView
    private lateinit var btnAttendanceZoomReset: Button
    private lateinit var zoomableContainer: ZoomableFrameLayout
    private lateinit var rvAttendingStudents: RecyclerView
    private lateinit var tvEmptySessionList: TextView

    private lateinit var database: AppDatabase
    private val sessionAttendedStudents = mutableListOf<StudentEntity>()
    private var isScanning = false
    private var slotId: Long = -1
    private var subjectCode = ""
    private var subjectName = ""
    private var startTime = "11:00"
    private var endTime = "12:30"
    private var isTimeUnlocked = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_attendance)

        database = AppDatabase.getDatabase(this)
        readIntentExtras()

        initViews()
        setupRecyclerView()
    }

    private fun readIntentExtras() {
        slotId = intent.getLongExtra("slotId", -1)
        subjectCode = intent.getStringExtra("subjectCode") ?: "MPC"
        subjectName = intent.getStringExtra("subjectName") ?: "Mobile & Pervasive Computing"
        startTime = intent.getStringExtra("startTime") ?: "11:00"
        endTime = intent.getStringExtra("endTime") ?: "12:30"
        val isExplicitActive = intent.getBooleanExtra("isActive", true)
        isTimeUnlocked = isExplicitActive && isCurrentTimeInSlot(startTime, endTime)
    }

    private fun initViews() {
        tvSubjectHeader = findViewById(R.id.tvSubjectHeader)
        tvClassDetails = findViewById(R.id.tvClassDetails)
        btnStartFingerScan = findViewById(R.id.btnStartFingerScan)
        btnExportSessionCsv = findViewById(R.id.btnExportSessionCsv)
        progressBar = findViewById(R.id.progressBarAttendance)
        tvSessionStatus = findViewById(R.id.tvSessionStatus)
        btnAttendanceZoomReset = findViewById(R.id.btnAttendanceZoomReset)
        zoomableContainer = findViewById(R.id.zoomableClassAttendanceContainer)
        rvAttendingStudents = findViewById(R.id.rvAttendingStudents)
        tvEmptySessionList = findViewById(R.id.tvEmptySessionList)

        val classroom = intent.getStringExtra("classroom") ?: "MA106"
        val faculty = intent.getStringExtra("facultyName") ?: "Dr. Mitesh Solanki"

        tvSubjectHeader.text = "$subjectCode - $subjectName"
        tvClassDetails.text = "Time: $startTime - $endTime | Room: $classroom | Faculty: $faculty"

        if (!isTimeUnlocked) {
            btnStartFingerScan.isEnabled = false
            btnStartFingerScan.alpha = 0.5f
            btnStartFingerScan.text = "ATTENDANCE LOCKED (OUTSIDE SCHEDULE)"
            tvSessionStatus.text = "ATTENDANCE LOCKED\nCurrent time is outside scheduled class timing ($startTime - $endTime).\nFingerprint scanning is disabled."
            btnStartFingerScan.setOnClickListener {
                Toast.makeText(this, "Attendance scanning is locked outside class hours ($startTime - $endTime)", Toast.LENGTH_LONG).show()
            }
        } else {
            btnStartFingerScan.isEnabled = true
            btnStartFingerScan.alpha = 1.0f
            btnStartFingerScan.text = "Scan Fingerprint Now"
            btnStartFingerScan.setOnClickListener { executeBiometricScan() }
        }

        btnExportSessionCsv.setOnClickListener { exportSessionCsv() }

        btnAttendanceZoomReset.setOnClickListener {
            zoomableContainer.resetZoom()
        }

        zoomableContainer.onScaleChangedListener = { scale ->
            btnAttendanceZoomReset.text = " Pinch ( ${(scale * 100).toInt()}% )"
        }
    }

    private fun setupRecyclerView() {
        rvAttendingStudents.layoutManager = LinearLayoutManager(this)
        loadAttendedStudentsFromDb()
    }

    private fun loadAttendedStudentsFromDb() {
        lifecycleScope.launch {
            try {
                val records = database.subjectAttendanceDao().getAttendanceListForSubject(subjectCode)
                val allStudents = database.studentDao().getAllStudentsList()
                sessionAttendedStudents.clear()

                for (r in records) {
                    val matched = allStudents.firstOrNull { it.srNo == r.studentSrNo || (it.fingerprintId > 0 && it.fingerprintId == r.fingerprintId) }
                    if (matched != null) {
                        if (sessionAttendedStudents.none { it.srNo == matched.srNo }) {
                            sessionAttendedStudents.add(matched)
                        }
                    } else {
                        val dummy = StudentEntity(
                            srNo = r.studentSrNo,
                            studentId = r.enrollmentNo,
                            name = r.studentName,
                            classSem = r.classSem,
                            grNo = r.grNo,
                            enrollmentNo = r.enrollmentNo,
                            fingerprintId = r.fingerprintId,
                            totalAttendedClasses = 1
                        )
                        if (sessionAttendedStudents.none { it.srNo == dummy.srNo }) {
                            sessionAttendedStudents.add(dummy)
                        }
                    }
                }
                syncTimetableAttendedCount()
                updateSessionAdapter()
            } catch (_: Exception) {}
        }
    }

    private suspend fun syncTimetableAttendedCount() {
        try {
            val count = sessionAttendedStudents.size
            val timetableDao = database.timetableDao()
            if (slotId > 0) {
                timetableDao.setAttendedCountById(slotId, count)
            }
            if (subjectCode.isNotEmpty()) {
                timetableDao.setAttendedCountBySubject(subjectCode, subjectName, count)
            }
        } catch (_: Exception) {}
    }

    private fun executeBiometricScan() {
        if (!isTimeUnlocked) {
            Toast.makeText(this, "Attendance scanning is locked outside class hours ($startTime - $endTime)", Toast.LENGTH_LONG).show()
            return
        }

        if (isScanning) return
        isScanning = true
        progressBar.visibility = View.VISIBLE
        btnStartFingerScan.isEnabled = false

        lifecycleScope.launch {
            try {
                val initRes = RetrofitClient.getService().searchFingerprint()
                if (!initRes.isSuccessful && initRes.code() != 409) {
                    tvSessionStatus.text = " Hardware Error: ${initRes.message()}"
                    return@launch
                }

                var isMatched = false
                var attempts = 0
                val maxAttempts = 100 // 100 * 200ms = 20s timeout

                while (!isMatched && attempts < maxAttempts) {
                    delay(200) // Fast 200ms polling for instant detection
                    attempts++
                    val remainingSeconds = (maxAttempts - attempts) / 5
                    tvSessionStatus.text = "Place registered finger on sensor panel...\nScanning... (${remainingSeconds}s)"

                    val response = RetrofitClient.getService().getSearchStatus()
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        if (body.isTrueMatch) {
                            val matchedId = body.effectiveFingerprintId ?: -1
                            if (matchedId > 0) {
                                isMatched = true
                                handleMatchedStudent(matchedId)
                                break
                            }
                        }
                    }
                }

                if (!isMatched) {
                    tvSessionStatus.text = "NO MATCH DETECTED\nPlease place finger firmly on sensor panel and tap 'Scan Fingerprint Now' again."
                }
            } catch (e: Exception) {
                tvSessionStatus.text = "Connection Error: ${e.localizedMessage}"
            } finally {
                isScanning = false
                progressBar.visibility = View.GONE
                btnStartFingerScan.isEnabled = isTimeUnlocked
            }
        }
    }

    private suspend fun handleMatchedStudent(matchedId: Int) {
        val studentDao = database.studentDao()
        val subjectDao = database.subjectAttendanceDao()

        val student = studentDao.getStudentByFingerprintId(matchedId)

        if (student != null) {
            val existingRecord = subjectDao.findRecordForSubject(subjectCode, student.srNo, matchedId)
            val isAlreadyMarked = existingRecord != null || sessionAttendedStudents.any { 
                it.srNo == student.srNo || (it.fingerprintId > 0 && it.fingerprintId == matchedId)
            }

            if (isAlreadyMarked) {
                tvSessionStatus.text = "ALREADY MARKED PRESENT\nFinger ID $matchedId | ${student.name}\nEnroll: ${student.enrollmentNo} | Class: ${student.classSem}"
                Toast.makeText(this, "${student.name} is already marked present in $subjectCode!", Toast.LENGTH_SHORT).show()

                try {
                    RetrofitClient.getService().showOnOled(
                        com.mpc.bioattend.model.OledShowRequest(
                            title = "ID $matchedId ALREADY",
                            message = "${student.name}"
                        )
                    )
                } catch (_: Exception) {}
                return
            }

            studentDao.markAttendanceByFingerprint(matchedId)
            val updatedStudent = studentDao.getStudentBySrNo(student.srNo) ?: student

            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
            val sdfTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
            val now = Date()

            subjectDao.insertRecord(
                SubjectAttendanceEntity(
                    subjectCode = subjectCode,
                    subjectName = subjectName,
                    studentSrNo = updatedStudent.srNo,
                    fingerprintId = matchedId,
                    studentName = updatedStudent.name,
                    enrollmentNo = updatedStudent.enrollmentNo,
                    classSem = updatedStudent.classSem,
                    grNo = updatedStudent.grNo,
                    timestamp = sdfTime.format(now),
                    dateStr = sdfDate.format(now)
                )
            )

            sessionAttendedStudents.add(updatedStudent)
            syncTimetableAttendedCount()
            updateSessionAdapter()

            tvSessionStatus.text = "ATTENDANCE MARKED\nFinger ID $matchedId | ${updatedStudent.name}\nEnroll: ${updatedStudent.enrollmentNo} | Class: ${updatedStudent.classSem} | GR: ${updatedStudent.grNo}"
            Toast.makeText(this, "Attendance marked for ${updatedStudent.name} (Finger ID $matchedId)!", Toast.LENGTH_SHORT).show()

            try {
                RetrofitClient.getService().showOnOled(
                    com.mpc.bioattend.model.OledShowRequest(
                        title = "ID $matchedId PRESENT",
                        message = "${updatedStudent.name}"
                    )
                )
            } catch (_: Exception) {}
        } else {
            tvSessionStatus.text = "Fingerprint ID $matchedId matched on sensor, but no student has Fingerprint ID $matchedId assigned!"
            Toast.makeText(this, "Finger ID $matchedId is not assigned to any student", Toast.LENGTH_LONG).show()
        }
    }

    private fun isCurrentTimeInSlot(startTimeStr: String, endTimeStr: String): Boolean {
        val startMins = parseToMinutes(startTimeStr)
        val endMins = parseToMinutes(endTimeStr)
        if (startMins < 0 || endMins < 0) return true

        val istZone = TimeZone.getTimeZone("Asia/Kolkata")
        val now = Calendar.getInstance(istZone)
        val currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        return currentMins in startMins..endMins
    }

    private fun parseToMinutes(timeStr: String): Int {
        val trimmed = timeStr.trim().uppercase(Locale.ROOT)
        val formats = arrayOf("hh:mm a", "h:mm a", "HH:mm", "H:mm")
        val istZone = TimeZone.getTimeZone("Asia/Kolkata")

        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US).apply { timeZone = istZone }
                val cal = Calendar.getInstance(istZone).apply { time = sdf.parse(trimmed)!! }
                return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            } catch (_: Exception) {}
        }

        try {
            val isPm = trimmed.contains("PM")
            val isAm = trimmed.contains("AM")
            val clean = trimmed.replace("AM", "").replace("PM", "").trim()
            val parts = clean.split(":")
            var hour = parts[0].toInt()
            val min = parts[1].toInt()
            if (isPm && hour < 12) hour += 12
            if (isAm && hour == 12) hour = 0
            return hour * 60 + min
        } catch (_: Exception) {}

        return -1
    }

    private fun updateSessionAdapter() {
        val adapter = SessionAttendedAdapter(sessionAttendedStudents)
        rvAttendingStudents.adapter = adapter

        if (sessionAttendedStudents.isEmpty()) {
            tvEmptySessionList.visibility = View.VISIBLE
            rvAttendingStudents.visibility = View.GONE
        } else {
            tvEmptySessionList.visibility = View.GONE
            rvAttendingStudents.visibility = View.VISIBLE
        }
    }

    private fun exportSessionCsv() {
        if (sessionAttendedStudents.isEmpty()) {
            Toast.makeText(this, "No present attendance records to export for $subjectCode", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
            val dateStr = sdf.format(Date())
            val header = "Sr.No,Fingerprint ID,Student Name,Class/Sem,GR.No,Enrollment No,Total Attended,Subject Code,Timestamp,Status\n"
            val sb = StringBuilder(header)
            sessionAttendedStudents.forEachIndexed { idx, s ->
                val fingerStr = if (s.fingerprintId >= 0) "Slot ${s.fingerprintId}" else "Unmapped"
                sb.append("${idx + 1},$fingerStr,\"${s.name}\",${s.classSem},${s.grNo},${s.enrollmentNo},${s.totalAttendedClasses},$subjectCode,$dateStr,Present\n")
            }

            val file = File(cacheDir, "Attendance_${subjectCode}_$dateStr.csv")
            file.writeText(sb.toString())

            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Present Attendance Report - $subjectCode")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Export Present Attendance ($subjectCode)"))
        } catch (e: Exception) {
            Toast.makeText(this, "Export Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    class SessionAttendedAdapter(private val list: List<StudentEntity>) :
        RecyclerView.Adapter<SessionAttendedAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvSrNo: TextView = v.findViewById(R.id.tvSessionSrNo)
            val tvName: TextView = v.findViewById(R.id.tvSessionStudentName)
            val tvClassSem: TextView = v.findViewById(R.id.tvSessionClassSem)
            val tvGrNo: TextView = v.findViewById(R.id.tvSessionGrNo)
            val tvEnrollNo: TextView = v.findViewById(R.id.tvSessionEnrollNo)
            val tvFingerId: TextView = v.findViewById(R.id.tvSessionFingerId)
            val tvAttendedCount: TextView = v.findViewById(R.id.tvSessionAttendedCount)
            val tvStatusBadge: TextView = v.findViewById(R.id.tvSessionStatusBadge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_session_attended_excel_row, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val s = list[position]
            holder.tvSrNo.text = (position + 1).toString()
            holder.tvName.text = s.name
            holder.tvClassSem.text = s.classSem
            holder.tvGrNo.text = if (s.grNo.isNotEmpty()) s.grNo else "-"
            holder.tvEnrollNo.text = s.enrollmentNo
            holder.tvFingerId.text = if (s.fingerprintId >= 0) "Slot ${s.fingerprintId}" else "Unmapped"
            holder.tvAttendedCount.text = s.totalAttendedClasses.toString()
            holder.tvStatusBadge.text = "PRESENT "
        }

        override fun getItemCount(): Int = list.size
    }
}
