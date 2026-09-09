package com.mpc.bioattend.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mpc.bioattend.R
import com.mpc.bioattend.database.AppDatabase
import com.mpc.bioattend.model.StudentEntity
import kotlinx.coroutines.launch

class EditStudentActivity : AppCompatActivity() {

    private lateinit var tvEditStudentPageTitle: TextView
    private lateinit var etStudentName: EditText
    private lateinit var etEnrollmentNo: EditText
    private lateinit var etClassSem: AutoCompleteTextView
    private lateinit var etGrNo: AutoCompleteTextView
    private lateinit var etFingerId: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private lateinit var database: AppDatabase
    private var editingSrNo: Long = -1L
    private var existingStudent: StudentEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_student)

        database = AppDatabase.getDatabase(this)
        editingSrNo = intent.getLongExtra("EXTRA_STUDENT_SR_NO", -1L)

        initViews()
        loadStudentData()
        setupAutoCompleteSuggestions()
    }

    private fun initViews() {
        tvEditStudentPageTitle = findViewById(R.id.tvEditStudentPageTitle)
        etStudentName = findViewById(R.id.etPageStudentName)
        etEnrollmentNo = findViewById(R.id.etPageEnrollmentNo)
        etClassSem = findViewById(R.id.etPageClassSem)
        etGrNo = findViewById(R.id.etPageGrNo)
        etFingerId = findViewById(R.id.etPageFingerId)
        btnSave = findViewById(R.id.btnSaveStudentRecord)
        btnCancel = findViewById(R.id.btnCancelStudentRecord)

        etClassSem.setOnClickListener { etClassSem.showDropDown() }
        etGrNo.setOnClickListener { etGrNo.showDropDown() }

        btnSave.setOnClickListener { saveRecord() }
        btnCancel.setOnClickListener { finish() }
    }

    private fun setupAutoCompleteSuggestions() {
        lifecycleScope.launch {
            val distinctClassSem = database.studentDao().getDistinctClassSemList()
            if (distinctClassSem.isNotEmpty()) {
                val classAdapter = ArrayAdapter(this@EditStudentActivity, android.R.layout.simple_dropdown_item_1line, distinctClassSem)
                etClassSem.setAdapter(classAdapter)
            }

            val distinctGrNo = database.studentDao().getDistinctGrNoList()
            if (distinctGrNo.isNotEmpty()) {
                val grAdapter = ArrayAdapter(this@EditStudentActivity, android.R.layout.simple_dropdown_item_1line, distinctGrNo)
                etGrNo.setAdapter(grAdapter)
            }
        }
    }

    private fun loadStudentData() {
        if (editingSrNo != -1L) {
            tvEditStudentPageTitle.text = "Edit Student Record"
            lifecycleScope.launch {
                val student = database.studentDao().getStudentBySrNo(editingSrNo)
                if (student != null) {
                    existingStudent = student
                    etStudentName.setText(student.name)
                    etEnrollmentNo.setText(student.enrollmentNo)
                    etClassSem.setText(student.classSem)
                    etGrNo.setText(student.grNo)
                    if (student.fingerprintId >= 0) {
                        etFingerId.setText(student.fingerprintId.toString())
                    }
                }
            }
        } else {
            tvEditStudentPageTitle.text = "Add Student Record"
        }
    }

    private fun saveRecord() {
        val name = etStudentName.text.toString().trim()
        val enrollmentNo = etEnrollmentNo.text.toString().trim()
        val classSem = etClassSem.text.toString().trim().ifEmpty { "Sem 7-ICT" }
        val grNo = etGrNo.text.toString().trim().ifEmpty { "GR1000" }
        val fingerIdText = etFingerId.text.toString().trim()
        val fingerId = fingerIdText.toIntOrNull() ?: -1

        if (name.isEmpty() || enrollmentNo.isEmpty()) {
            Toast.makeText(this, "Student Name and Enrollment No are required!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val curr = existingStudent
            if (curr != null) {
                val autoStudentId = if (fingerId > 0) fingerId.toString() else curr.studentId
                val updated = curr.copy(
                    studentId = autoStudentId,
                    name = name,
                    enrollmentNo = enrollmentNo,
                    classSem = classSem,
                    grNo = grNo,
                    fingerprintId = fingerId
                )
                database.studentDao().updateStudent(updated)
                Toast.makeText(this@EditStudentActivity, "Student record updated!", Toast.LENGTH_SHORT).show()
            } else {
                val count = database.studentDao().getAllStudentsList().size
                val autoStudentId = if (fingerId > 0) fingerId.toString() else (count + 1).toString()
                val newStudent = StudentEntity(
                    studentId = autoStudentId,
                    name = name,
                    classSem = classSem,
                    grNo = grNo,
                    enrollmentNo = enrollmentNo,
                    fingerprintId = fingerId,
                    totalAttendedClasses = 0
                )
                database.studentDao().insertStudent(newStudent)
                Toast.makeText(this@EditStudentActivity, "Student record created!", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
