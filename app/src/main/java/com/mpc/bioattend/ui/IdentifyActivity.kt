package com.mpc.bioattend.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mpc.bioattend.R
import com.mpc.bioattend.database.AppDatabase
import com.mpc.bioattend.model.StudentEntity
import com.mpc.bioattend.network.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IdentifyActivity : AppCompatActivity() {

    private lateinit var btnScanFingerprint: Button
    private lateinit var tvStatusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutMatchResult: View
    private lateinit var tvMatchName: TextView
    private lateinit var tvMatchRoll: TextView
    private lateinit var tvMatchDept: TextView
    private lateinit var tvMatchFingerId: TextView
    private lateinit var tvMatchAttendance: TextView

    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_identify)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        btnScanFingerprint = findViewById(R.id.btnStartScanFingerprint)
        tvStatusText = findViewById(R.id.tvIdentifyStatusText)
        progressBar = findViewById(R.id.progressBarIdentify)
        layoutMatchResult = findViewById(R.id.layoutMatchResult)
        tvMatchName = findViewById(R.id.tvMatchName)
        tvMatchRoll = findViewById(R.id.tvMatchRoll)
        tvMatchDept = findViewById(R.id.tvMatchDept)
        tvMatchFingerId = findViewById(R.id.tvMatchFingerId)
        tvMatchAttendance = findViewById(R.id.tvMatchAttendance)

        layoutMatchResult.visibility = View.GONE
    }

    private fun setupListeners() {
        btnScanFingerprint.setOnClickListener {
            performFingerprintSearch()
        }
    }

    private fun performFingerprintSearch() {
        if (isScanning) return
        isScanning = true
        progressBar.visibility = View.VISIBLE
        layoutMatchResult.visibility = View.GONE
        btnScanFingerprint.isEnabled = false
        tvStatusText.text = " Place registered finger on R307S sensor..."

        lifecycleScope.launch {
            try {
                // Initiate search on ESP32 hardware
                val initRes = RetrofitClient.getService().searchFingerprint()
                if (!initRes.isSuccessful && initRes.code() != 409) {
                    tvStatusText.text = " Hardware Error: ${initRes.message()}"
                    return@launch
                }

                var isMatched = false
                var attempts = 0
                val maxAttempts = 100 // 100 * 200ms = 20s timeout

                while (!isMatched && attempts < maxAttempts) {
                    delay(200) // Fast 200ms polling for instant detection
                    attempts++
                    val remainingSeconds = (maxAttempts - attempts) / 5
                    tvStatusText.text = "Place registered finger on sensor...\nScanning... (${remainingSeconds}s)"

                    // Poll status endpoint
                    val response = RetrofitClient.getService().getSearchStatus()
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        if (body.isTrueMatch) {
                            val fingerId = body.effectiveFingerprintId ?: -1
                            if (fingerId > 0) {
                                isMatched = true
                                handleMatchedFingerprint(fingerId)
                                break
                            }
                        }
                    }
                }

                if (!isMatched) {
                    tvStatusText.text = "NO MATCH FOUND\nFingerprint not recognized in database."
                }
            } catch (e: Exception) {
                tvStatusText.text = "Connection Error: ${e.localizedMessage}"
            } finally {
                isScanning = false
                progressBar.visibility = View.GONE
                btnScanFingerprint.isEnabled = true
            }
        }
    }

    private suspend fun handleMatchedFingerprint(fingerId: Int) {
        val studentDao = AppDatabase.getDatabase(this).studentDao()

        studentDao.markAttendanceByFingerprint(fingerId)
        val student = studentDao.getStudentByFingerprintId(fingerId)

        if (student != null) {
            displayStudentMatch(student)
            try {
                RetrofitClient.getService().showOnOled(
                    com.mpc.bioattend.model.OledShowRequest(
                        title = "ID $fingerId PRESENT",
                        message = "${student.name}"
                    )
                )
            } catch (_: Exception) {}
        } else {
            tvStatusText.text = " Fingerprint ID $fingerId matched on hardware, but no student profile is linked in local app database!"
            Toast.makeText(this, "Finger ID $fingerId matched (unlinked profile)", Toast.LENGTH_LONG).show()
            try {
                RetrofitClient.getService().showOnOled(
                    com.mpc.bioattend.model.OledShowRequest(
                        title = "ID $fingerId PRESENT",
                        message = "Unmapped Profile"
                    )
                )
            } catch (_: Exception) {}
        }
    }

    private fun displayStudentMatch(student: StudentEntity) {
        tvStatusText.text = " MATCH FOUND & ATTENDANCE MARKED!"
        tvMatchName.text = student.name
        tvMatchRoll.text = "Enrollment: ${student.enrollmentNo}"
        tvMatchDept.text = "Class/Sem: ${student.classSem}"
        tvMatchFingerId.text = "Fingerprint ID: ${student.fingerprintId}"
        tvMatchAttendance.text = "Total Attendance Logged: ${student.totalAttendedClasses}"

        layoutMatchResult.visibility = View.VISIBLE
        Toast.makeText(this, "Attendance marked for ${student.name}!", Toast.LENGTH_SHORT).show()
    }
}
