package com.mpc.bioattend.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mpc.bioattend.R
import com.mpc.bioattend.database.AppDatabase
import com.mpc.bioattend.model.EnrollRequest
import com.mpc.bioattend.model.StudentEntity
import com.mpc.bioattend.network.RetrofitClient
import com.mpc.bioattend.util.ZoomableFrameLayout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EnrollmentManagerActivity : AppCompatActivity() {

    private lateinit var btnSortEnrolledId: Button
    private lateinit var btnWipeAllFingerprints: Button
    private lateinit var etSearch: EditText
    private lateinit var btnZoomReset: Button
    private lateinit var zoomableContainer: ZoomableFrameLayout
    private lateinit var rvEnrollmentList: RecyclerView
    private lateinit var tvEmptyEnrollment: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var database: AppDatabase

    private var isSortedByFingerprintAsc = true
    private var observeJob: Job? = null
    private var fullStudentList: List<StudentEntity> = emptyList()
    private var filteredStudentList: List<StudentEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enrollment_manager)

        database = AppDatabase.getDatabase(this)

        initViews()
        observeStudents()
    }

    private fun initViews() {
        btnSortEnrolledId = findViewById(R.id.btnSortEnrolledId)
        btnWipeAllFingerprints = findViewById(R.id.btnWipeAllFingerprints)
        etSearch = findViewById(R.id.etSearchEnrollment)
        btnZoomReset = findViewById(R.id.btnEnrollZoomReset)
        zoomableContainer = findViewById(R.id.zoomableEnrollmentContainer)
        rvEnrollmentList = findViewById(R.id.rvEnrollmentList)
        tvEmptyEnrollment = findViewById(R.id.tvEmptyEnrollment)
        progressBar = findViewById(R.id.progressBarEnrollmentManager)

        rvEnrollmentList.layoutManager = LinearLayoutManager(this)

        updateSortButtonUi()

        btnSortEnrolledId.setOnClickListener {
            isSortedByFingerprintAsc = !isSortedByFingerprintAsc
            updateSortButtonUi()
            observeStudents()
        }

        btnWipeAllFingerprints.setOnClickListener {
            confirmWipeAll()
        }

        btnZoomReset.setOnClickListener {
            zoomableContainer.resetZoom()
        }

        zoomableContainer.onScaleChangedListener = { scale ->
            btnZoomReset.text = " Pinch ( ${(scale * 100).toInt()}% )"
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterStudents(s?.toString()?.trim() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updateSortButtonUi() {
        if (isSortedByFingerprintAsc) {
            btnSortEnrolledId.text = "Sort: Sensor ID (ASC)"
            btnSortEnrolledId.setBackgroundResource(R.drawable.bg_button_cyan)
            btnSortEnrolledId.setTextColor("#070A14".toColorInt())
        } else {
            btnSortEnrolledId.text = "Sort: Default"
            btnSortEnrolledId.setBackgroundResource(R.drawable.bg_glass_card)
            btnSortEnrolledId.setTextColor("#00F2FE".toColorInt())
        }
    }

    private fun observeStudents() {
        observeJob?.cancel()
        observeJob = lifecycleScope.launch {
            val flow = database.studentDao().getAllStudents()

            flow.collectLatest { list ->
                fullStudentList = if (isSortedByFingerprintAsc) {
                    list.sortedWith(Comparator { s1, s2 ->
                        val id1 = s1.fingerprintId
                        val id2 = s2.fingerprintId
                        when {
                            id1 >= 0 && id2 >= 0 -> id1.compareTo(id2)
                            id1 >= 0 -> -1
                            id2 >= 0 -> 1
                            else -> s1.srNo.compareTo(s2.srNo)
                        }
                    })
                } else {
                    list
                }

                filterStudents(etSearch.text.toString().trim())
            }
        }

        // Asynchronously check hardware sensor list with 1.5s timeout without blocking UI
        lifecycleScope.launch {
            try {
                withTimeoutOrNull(1500L) {
                    RetrofitClient.getService().getFingerprintList()
                }
            } catch (_: Exception) {}
        }
    }

    private fun filterStudents(query: String) {
        filteredStudentList = if (query.isEmpty()) {
            fullStudentList
        } else {
            fullStudentList.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.studentId.contains(query, ignoreCase = true) ||
                it.enrollmentNo.contains(query, ignoreCase = true) ||
                it.grNo.contains(query, ignoreCase = true) ||
                it.classSem.contains(query, ignoreCase = true) ||
                it.fingerprintId.toString().contains(query)
            }
        }

        val adapter = EnrollmentAdapter(
            filteredStudentList,
            onEnrollClick = { student -> promptEnrollStudent(student) },
            onDeleteClick = { student -> confirmDeleteSingleFingerprint(student) }
        )
        rvEnrollmentList.adapter = adapter

        if (filteredStudentList.isEmpty()) {
            tvEmptyEnrollment.visibility = View.VISIBLE
            rvEnrollmentList.visibility = View.GONE
        } else {
            tvEmptyEnrollment.visibility = View.GONE
            rvEnrollmentList.visibility = View.VISIBLE
        }
    }

    private fun promptEnrollStudent(student: StudentEntity) {
        val intent = Intent(this, EnrollStudentActivity::class.java).apply {
            putExtra("srNo", student.srNo)
        }
        startActivity(intent)
    }

    private fun confirmDeleteSingleFingerprint(student: StudentEntity) {
        if (student.fingerprintId < 0) return

        AlertDialog.Builder(this, R.style.GlassDialogTheme)
            .setTitle("SECOND CONFIRMATION: Delete Fingerprint?")
            .setMessage("Are you sure you want to delete Fingerprint ID ${student.fingerprintId} for ${student.name}?\n\nThis will un-map the student and delete the template from ESP32 R307S memory.")
            .setPositiveButton("YES, DELETE ID") { dialog, _ ->
                deleteFingerprint(student)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun deleteFingerprint(student: StudentEntity) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                if (student.fingerprintId >= 0) {
                    val res = RetrofitClient.getService().deleteFingerprint(student.fingerprintId)
                    if (!res.isSuccessful) {
                        RetrofitClient.getService().deleteFingerprintPost(student.fingerprintId)
                    }
                }
            } catch (e: Exception) {
                try {
                    if (student.fingerprintId >= 0) {
                        RetrofitClient.getService().deleteFingerprintPost(student.fingerprintId)
                    }
                } catch (_: Exception) {}
            } finally {
                progressBar.visibility = View.GONE
                val updated = student.copy(fingerprintId = -1)
                database.studentDao().updateStudent(updated)
                Toast.makeText(this@EnrollmentManagerActivity, "Fingerprint ID ${student.fingerprintId} deleted and unlinked", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmWipeAll() {
        AlertDialog.Builder(this, R.style.GlassDialogTheme)
            .setTitle("SECOND CONFIRMATION: WIPE ALL FINGERPRINTS?")
            .setMessage("Are you completely sure you want to wipe ALL biometric templates from the R307S sensor hardware?\n\nAll student fingerprint mappings will be cleared.")
            .setPositiveButton("YES, WIPE ALL MEMORY") { dialog, _ ->
                wipeAll()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun wipeAll() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val res = RetrofitClient.getService().deleteAllFingerprints()
                if (!res.isSuccessful) {
                    RetrofitClient.getService().deleteAllFingerprintsPost()
                }
            } catch (e: Exception) {
                try {
                    RetrofitClient.getService().deleteAllFingerprintsPost()
                } catch (_: Exception) {}
            } finally {
                progressBar.visibility = View.GONE
                database.studentDao().resetAllFingerprintIds()
                Toast.makeText(this@EnrollmentManagerActivity, "All biometric memory wiped from sensor and app database!", Toast.LENGTH_LONG).show()
            }
        }
    }

    class EnrollmentAdapter(
        private val list: List<StudentEntity>,
        private val onEnrollClick: (StudentEntity) -> Unit,
        private val onDeleteClick: (StudentEntity) -> Unit
    ) : RecyclerView.Adapter<EnrollmentAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvSrNo: TextView = v.findViewById(R.id.tvRowSrNo)
            val tvName: TextView = v.findViewById(R.id.tvRowStudentName)
            val tvClassSem: TextView = v.findViewById(R.id.tvRowClassSem)
            val tvGrNo: TextView = v.findViewById(R.id.tvRowGrNo)
            val tvEnrollment: TextView = v.findViewById(R.id.tvRowEnrollmentNo)
            val tvFingerId: TextView = v.findViewById(R.id.tvRowFingerId)
            val tvStatusBadge: TextView = v.findViewById(R.id.tvRowEnrollStatus)
            val btnEnroll: Button = v.findViewById(R.id.btnEnrollFinger)
            val btnDeleteSingleFinger: Button = v.findViewById(R.id.btnDeleteSingleFinger)
            val cardView: View = v.findViewById(R.id.cardEnrollmentItem)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_enrollment_excel_row, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val s = list[position]
            holder.tvSrNo.text = (position + 1).toString()
            holder.tvName.text = s.name
            holder.tvClassSem.text = s.classSem
            holder.tvGrNo.text = if (s.grNo.isNotEmpty()) s.grNo else "-"
            holder.tvEnrollment.text = s.enrollmentNo
            holder.tvFingerId.text = if (s.fingerprintId > 0) "Slot ${s.fingerprintId}" else "Unmapped"

            if (s.fingerprintId > 0) {
                holder.tvStatusBadge.text = "ENROLLED "
                holder.tvStatusBadge.setTextColor("#00F5A0".toColorInt())
                holder.btnEnroll.text = "Re-Enroll"
                holder.btnDeleteSingleFinger.visibility = View.VISIBLE
            } else {
                holder.tvStatusBadge.text = "NOT ENROLLED"
                holder.tvStatusBadge.setTextColor("#FF4B4B".toColorInt())
                holder.btnEnroll.text = "Enroll Now"
                holder.btnDeleteSingleFinger.visibility = View.GONE
            }

            holder.btnEnroll.setOnClickListener { onEnrollClick(s) }
            holder.btnDeleteSingleFinger.setOnClickListener { onDeleteClick(s) }
        }

        override fun getItemCount(): Int = list.size
    }
}
