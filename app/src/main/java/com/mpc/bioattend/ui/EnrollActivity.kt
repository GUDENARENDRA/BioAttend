package com.mpc.bioattend.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mpc.bioattend.R
import com.mpc.bioattend.database.AppDatabase
import com.mpc.bioattend.model.StudentEntity
import com.mpc.bioattend.network.RetrofitClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EnrollActivity : AppCompatActivity() {

    private lateinit var spinnerStudents: Spinner
    private lateinit var btnStartEnroll: Button
    private lateinit var tvInstruction: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    private var studentsList: List<StudentEntity> = emptyList()
    private var selectedStudent: StudentEntity? = null
    private var isEnrolling: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enroll)

        initViews()
        loadStudents()
    }

    private fun initViews() {
        spinnerStudents = findViewById(R.id.spinnerStudents)
        btnStartEnroll = findViewById(R.id.btnStartEnroll)
        tvInstruction = findViewById(R.id.tvEnrollInstruction)
        tvStatus = findViewById(R.id.tvEnrollStatus)
        progressBar = findViewById(R.id.progressBarEnroll)

        btnStartEnroll.setOnClickListener {
            if (selectedStudent == null) {
                Toast.makeText(this, "Please select or create a student first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startEnrollmentProcess()
        }
    }

    private fun loadStudents() {
        lifecycleScope.launch {
            val list = AppDatabase.getDatabase(this@EnrollActivity).studentDao().getAllStudents().first()
            studentsList = list
            if (list.isEmpty()) {
                tvInstruction.text = " No students found. Please add a student in 'User Data' first."
                btnStartEnroll.isEnabled = false
            } else {
                val adapter = ArrayAdapter(
                    this@EnrollActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    list.map { "${it.name} (${it.enrollmentNo})" }
                )
                spinnerStudents.adapter = adapter
                spinnerStudents.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        selectedStudent = list[position]
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
        }
    }

    private fun startEnrollmentProcess() {
        if (isEnrolling) return
        isEnrolling = true
        progressBar.visibility = View.VISIBLE
        btnStartEnroll.isEnabled = false
        tvInstruction.text = " Place finger on the R307S sensor..."
        tvStatus.text = "Initiating enrollment command on ESP32..."

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getService().enrollFingerprint()
                val body = response.body()
                val isSuccess = response.isSuccessful && body != null && body.isEnrollSuccess
                val fingerId = body?.effectiveFingerprintId ?: -1

                if (isSuccess && fingerId > 0 && selectedStudent != null) {
                    val updatedStudent = selectedStudent!!.copy(fingerprintId = fingerId)
                    AppDatabase.getDatabase(this@EnrollActivity).studentDao().updateStudent(updatedStudent)

                    tvInstruction.text = " ENROLLMENT COMPLETE!"
                    tvStatus.text = " Fingerprint ID $fingerId registered to ${selectedStudent!!.name} (${selectedStudent!!.enrollmentNo})"
                    Toast.makeText(this@EnrollActivity, "Fingerprint enrolled with ID $fingerId!", Toast.LENGTH_LONG).show()
                } else {
                    tvInstruction.text = " Enrollment Failed"
                    tvStatus.text = response.body()?.message ?: "Fingerprint capture failed on sensor"
                }
            } catch (e: Exception) {
                tvInstruction.text = " Connection Error"
                tvStatus.text = e.localizedMessage
            } finally {
                isEnrolling = false
                progressBar.visibility = View.GONE
                btnStartEnroll.isEnabled = true
            }
        }
    }
}
