package com.mpc.bioattend.ui

import android.text.Editable
import android.text.TextWatcher
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mpc.bioattend.R
import com.mpc.bioattend.database.AppDatabase
import com.mpc.bioattend.model.EnrollRequest
import com.mpc.bioattend.model.OledShowRequest
import com.mpc.bioattend.model.StudentEntity
import com.mpc.bioattend.network.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EnrollStudentActivity : AppCompatActivity() {

    private lateinit var tvStudentName: TextView
    private lateinit var tvStudentDetails: TextView
    private lateinit var etSensorSlotId: EditText
    private lateinit var btnStartEnrollment: Button
    private lateinit var btnCancelEnrollment: Button
    private lateinit var tvStatusMessage: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var database: AppDatabase

    private var currentSrNo: Long = -1
    private var studentObj: StudentEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enroll_student)

        database = AppDatabase.getDatabase(this)
        currentSrNo = intent.getLongExtra("srNo", -1)

        initViews()
        loadStudentData()
    }

    private fun initViews() {
        tvStudentName = findViewById(R.id.tvEnrollStudentName)
        tvStudentDetails = findViewById(R.id.tvEnrollStudentDetails)
        etSensorSlotId = findViewById(R.id.etSensorSlotId)
        btnStartEnrollment = findViewById(R.id.btnStartEnrollment)
        btnCancelEnrollment = findViewById(R.id.btnCancelEnrollment)
        tvStatusMessage = findViewById(R.id.tvEnrollStatusMessage)
        progressBar = findViewById(R.id.progressBarEnrollPage)

        btnCancelEnrollment.setOnClickListener { finish() }

        btnStartEnrollment.setOnClickListener {
            val idStr = etSensorSlotId.text.toString().trim()
            if (idStr.isEmpty()) {
                Toast.makeText(this, "Please enter a valid Sensor Slot ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val requestedId = idStr.toInt()
            studentObj?.let { s -> validateAndStartEnrollment(s, requestedId) }
        }

        etSensorSlotId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val idStr = s?.toString()?.trim() ?: ""
                if (idStr.isNotEmpty()) {
                    syncOledDisplay(idStr)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun syncOledDisplay(idStr: String) {
        lifecycleScope.launch {
            try {
                RetrofitClient.getService().showOnOled(
                    OledShowRequest(
                        title = "Enter Your ID",
                        message = "ID: $idStr"
                    )
                )
            } catch (_: Exception) {}
        }
    }

    private fun loadStudentData() {
        if (currentSrNo < 0) return
        lifecycleScope.launch {
            studentObj = database.studentDao().getStudentBySrNo(currentSrNo)
            studentObj?.let { s ->
                tvStudentName.text = s.name
                tvStudentDetails.text = "Enrollment: ${s.enrollmentNo} | ${s.classSem}"
                val defaultId = if (s.fingerprintId > 0) s.fingerprintId else s.srNo.toInt()
                etSensorSlotId.setText(defaultId.toString())
                syncOledDisplay(defaultId.toString())
            }
        }
    }

    private fun validateAndStartEnrollment(student: StudentEntity, requestedId: Int) {
        lifecycleScope.launch {
            val existingCount = database.studentDao().countFingerprintId(requestedId)
            val existingStudent = database.studentDao().getStudentByFingerprintId(requestedId)

            if ((existingCount > 0) && (existingStudent?.srNo != student.srNo)) {
                AlertDialog.Builder(this@EnrollStudentActivity, R.style.GlassDialogTheme)
                    .setTitle("DUPLICATE ID ERROR")
                    .setMessage("Fingerprint ID $requestedId is already mapped to ${existingStudent?.name} (${existingStudent?.enrollmentNo}).\n\nPlease choose a different ID.")
                    .setPositiveButton("OK") { d, _ -> d.dismiss() }
                    .show()
                return@launch
            }

            executeEsp32Enrollment(student, requestedId)
        }
    }

    private fun executeEsp32Enrollment(student: StudentEntity, requestedId: Int) {
        progressBar.visibility = View.VISIBLE
        tvStatusMessage.text = "Place finger on sensor panel for ID $requestedId..."
        btnStartEnrollment.isEnabled = false

        lifecycleScope.launch {
            try {
                val initRes = RetrofitClient.getService().enrollFingerprintWithId(EnrollRequest(requestedId))
                if (!initRes.isSuccessful) {
                    progressBar.visibility = View.GONE
                    btnStartEnrollment.isEnabled = true
                    tvStatusMessage.text = "Hardware Error: ${initRes.message()}"
                    Toast.makeText(this@EnrollStudentActivity, "Enrollment initialization failed.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Poll status until hardware capture finishes
                var isFinished = false
                var attempts = 0
                var finalResponse = initRes.body()

                while (!isFinished && attempts < 60) {
                    delay(1000)
                    attempts++
                    val statusRes = RetrofitClient.getService().getEnrollStatus()
                    if (statusRes.isSuccessful && statusRes.body() != null) {
                        val body = statusRes.body()!!
                        finalResponse = body
                        val data = body.dataDetails
                        val st = (body.status ?: data?.state ?: "").uppercase(java.util.Locale.ROOT)
                        val msg = (body.message ?: data?.message ?: "").uppercase(java.util.Locale.ROOT)

                        if (data != null && data.finished) {
                            isFinished = true
                        } else if (st.contains("SUCCESS") || st.contains("FAIL") || st.contains("ERROR") || st.contains("CANCEL") ||
                                   msg.contains("SUCCESS") || msg.contains("FAIL") || msg.contains("ERROR")) {
                            isFinished = true
                        }
                    }
                }

                progressBar.visibility = View.GONE
                btnStartEnrollment.isEnabled = true

                val returnedId = finalResponse?.effectiveFingerprintId
                val finalId = if (returnedId != null && returnedId > 0) returnedId else requestedId
                val isSuccess = finalResponse?.isEnrollSuccess == true

                if (isFinished && isSuccess && finalId > 0) {
                    val updated = student.copy(fingerprintId = finalId)
                    database.studentDao().updateStudent(updated)

                    tvStatusMessage.text = "Fingerprint ID $finalId enrolled successfully and mapped to ${student.name}!"

                    try {
                        RetrofitClient.getService().showOnOled(
                            OledShowRequest(
                                title = "ID $finalId ENROLLED",
                                message = "${student.name}"
                            )
                        )
                    } catch (_: Exception) {}

                    AlertDialog.Builder(this@EnrollStudentActivity, R.style.GlassDialogTheme)
                        .setTitle("ENROLLMENT COMPLETE")
                        .setMessage("Fingerprint ID $finalId enrolled successfully and mapped to ${student.name}!")
                        .setPositiveButton("OK") { d, _ ->
                            d.dismiss()
                            finish()
                        }
                        .show()
                } else {
                    val rawMsg = finalResponse?.dataDetails?.state ?: finalResponse?.message ?: "Fingerprint capture timeout or canceled."
                    tvStatusMessage.text = "Enrollment Failed: $rawMsg"

                    try {
                        RetrofitClient.getService().showOnOled(
                            OledShowRequest(
                                title = "ENROLL FAILED",
                                message = "Try Again"
                            )
                        )
                    } catch (_: Exception) {}

                    AlertDialog.Builder(this@EnrollStudentActivity, R.style.GlassDialogTheme)
                        .setTitle("ENROLLMENT FAILED")
                        .setMessage("Fingerprint enrollment failed on R307S sensor hardware.\n\nDetails: $rawMsg\n\nPlease place finger firmly when prompted and try again.")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                btnStartEnrollment.isEnabled = true
                tvStatusMessage.text = "Connection Error: ${e.localizedMessage}"
                Toast.makeText(this@EnrollStudentActivity, "Connection Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
