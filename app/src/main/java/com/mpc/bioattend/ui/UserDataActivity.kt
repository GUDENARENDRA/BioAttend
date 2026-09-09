package com.mpc.bioattend.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.mpc.bioattend.network.RetrofitClient
import com.mpc.bioattend.util.ZoomableFrameLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class UserDataActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvStudentCountSummary: TextView
    private lateinit var btnAddStudent: Button
    private lateinit var btnExportExcel: Button
    private lateinit var etSearch: EditText
    private lateinit var btnZoomReset: Button
    private lateinit var zoomableContainer: ZoomableFrameLayout
    private lateinit var rvExcelStudents: RecyclerView
    private lateinit var tvEmptyState: TextView

    private lateinit var database: AppDatabase
    private var fullStudentList: List<StudentEntity> = emptyList()
    private var filteredStudentList: List<StudentEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_data)

        database = AppDatabase.getDatabase(this)

        initViews()
        observeStudents()
        notifyOledMode()
    }

    private fun notifyOledMode() {
        lifecycleScope.launch {
            try {
                RetrofitClient.getService().getDeviceStatus()
            } catch (_: Exception) {}
        }
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBackUserData)
        tvStudentCountSummary = findViewById(R.id.tvStudentCountSummary)
        btnAddStudent = findViewById(R.id.btnAddStudent)
        btnExportExcel = findViewById(R.id.btnExportExcel)
        etSearch = findViewById(R.id.etSearchUserData)
        btnZoomReset = findViewById(R.id.btnZoomReset)
        zoomableContainer = findViewById(R.id.zoomableContainer)
        rvExcelStudents = findViewById(R.id.rvExcelStudents)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        rvExcelStudents.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener { finish() }

        btnAddStudent.setOnClickListener {
            val intent = Intent(this, EditStudentActivity::class.java)
            startActivity(intent)
        }

        btnExportExcel.setOnClickListener { exportToCsvExcel() }

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

    private fun observeStudents() {
        lifecycleScope.launch {
            database.studentDao().getAllStudents().collectLatest { list ->
                fullStudentList = list
                tvStudentCountSummary.text = "Total Registered Students: ${list.size}"
                filterStudents(etSearch.text.toString().trim())
            }
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

        val adapter = ExcelStudentAdapter(
            filteredStudentList,
            onEdit = { student ->
                val intent = Intent(this@UserDataActivity, EditStudentActivity::class.java).apply {
                    putExtra("EXTRA_STUDENT_SR_NO", student.srNo)
                }
                startActivity(intent)
            },
            onDelete = { student -> confirmDeleteStudent(student) }
        )
        rvExcelStudents.adapter = adapter

        if (filteredStudentList.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            rvExcelStudents.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            rvExcelStudents.visibility = View.VISIBLE
        }
    }

    private fun confirmDeleteStudent(student: StudentEntity) {
        AlertDialog.Builder(this, R.style.GlassDialogTheme)
            .setTitle("Delete Student Record")
            .setMessage("Are you sure you want to delete ${student.name} (${student.enrollmentNo})?\n\nThis action cannot be undone.")
            .setPositiveButton("Delete") { dialog, _ ->
                lifecycleScope.launch {
                    database.studentDao().deleteStudent(student)
                    Toast.makeText(this@UserDataActivity, "Student record deleted", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun exportToCsvExcel() {
        if (fullStudentList.isEmpty()) {
            Toast.makeText(this, "No student records to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val csvHeader = "Sr.No,Fingerprint ID,Student Name,Class/Sem,GR.No,Enrollment No,Attended Classes Count\n"
            val sb = StringBuilder(csvHeader)
            fullStudentList.forEachIndexed { idx, s ->
                val fingerStr = if (s.fingerprintId > 0) "Slot ${s.fingerprintId}" else "Unmapped"
                sb.append("${idx + 1},$fingerStr,\"${s.name}\",${s.classSem},${s.grNo},${s.enrollmentNo},${s.totalAttendedClasses}\n")
            }

            val file = File(cacheDir, "BioAttend_Student_Report.csv")
            file.writeText(sb.toString())

            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "BioAttend Student Attendance Report")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Excel/CSV Report"))
        } catch (e: Exception) {
            Toast.makeText(this, "Export Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    class ExcelStudentAdapter(
        private val list: List<StudentEntity>,
        private val onEdit: (StudentEntity) -> Unit,
        private val onDelete: (StudentEntity) -> Unit
    ) : RecyclerView.Adapter<ExcelStudentAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvSrNo: TextView = v.findViewById(R.id.tvExcelSrNo)
            val tvFingerprintId: TextView = v.findViewById(R.id.tvExcelFingerprintId)
            val tvName: TextView = v.findViewById(R.id.tvExcelStudentName)
            val tvClassSem: TextView = v.findViewById(R.id.tvExcelClassSem)
            val tvGrNo: TextView = v.findViewById(R.id.tvExcelGrNo)
            val tvEnrollmentNo: TextView = v.findViewById(R.id.tvExcelEnrollmentNo)
            val tvAttendedCount: TextView = v.findViewById(R.id.tvExcelAttendedCount)
            val btnEdit: ImageButton = v.findViewById(R.id.btnEditExcelStudent)
            val btnDelete: ImageButton = v.findViewById(R.id.btnDeleteExcelStudent)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_excel_student, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val s = list[position]
            holder.tvSrNo.text = (position + 1).toString()
            holder.tvFingerprintId.text = if (s.fingerprintId > 0) "Slot ${s.fingerprintId}" else "Unmapped"
            holder.tvName.text = s.name
            holder.tvClassSem.text = s.classSem
            holder.tvGrNo.text = if (s.grNo.isNotEmpty()) s.grNo else "-"
            holder.tvEnrollmentNo.text = s.enrollmentNo
            holder.tvAttendedCount.text = s.totalAttendedClasses.toString()

            holder.btnEdit.setOnClickListener { onEdit(s) }
            holder.btnDelete.setOnClickListener { onDelete(s) }
        }

        override fun getItemCount(): Int = list.size
    }
}
