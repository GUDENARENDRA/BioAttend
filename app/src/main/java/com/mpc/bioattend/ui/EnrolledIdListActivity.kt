package com.mpc.bioattend.ui

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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import java.io.File

class EnrolledIdListActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnExportCsv: Button
    private lateinit var tvHwMemoryStatus: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnZoomReset: Button
    private lateinit var zoomableContainer: ZoomableFrameLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var rvTable: RecyclerView
    private lateinit var tvEmpty: TextView

    private lateinit var database: AppDatabase
    private var fullList: List<EnrolledDisplayItem> = emptyList()
    private var filteredList: List<EnrolledDisplayItem> = emptyList()

    data class EnrolledDisplayItem(
        val slotId: Int,
        val srNo: Long,
        val studentId: String,
        val name: String,
        val classSem: String,
        val grNo: String,
        val enrollmentNo: String,
        val attendedClasses: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enrolled_id_list)

        database = AppDatabase.getDatabase(this)
        initViews()
        loadEnrolledData()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBackEnrolledList)
        btnExportCsv = findViewById(R.id.btnExportEnrolledCsv)
        tvHwMemoryStatus = findViewById(R.id.tvHwMemoryStatus)
        etSearch = findViewById(R.id.etSearchEnrolled)
        btnZoomReset = findViewById(R.id.btnEnrolledZoomReset)
        zoomableContainer = findViewById(R.id.zoomableEnrolledContainer)
        progressBar = findViewById(R.id.progressBarEnrolledList)
        rvTable = findViewById(R.id.rvEnrolledExcelTable)
        tvEmpty = findViewById(R.id.tvEmptyEnrolledState)

        rvTable.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener { finish() }
        btnExportCsv.setOnClickListener { exportEnrolledCsv() }

        btnZoomReset.setOnClickListener {
            zoomableContainer.resetZoom()
        }

        zoomableContainer.onScaleChangedListener = { scale ->
            btnZoomReset.text = " Pinch ( ${(scale * 100).toInt()}% )"
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s?.toString()?.trim() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadEnrolledData() {
        lifecycleScope.launch {
            // 1. Instantly display Room local DB data (0ms delay)
            val students = database.studentDao().getAllStudentsList()
            val enrolledInDb = students.filter { it.fingerprintId >= 0 }.distinctBy { it.fingerprintId }.sortedBy { it.fingerprintId }

            tvHwMemoryStatus.text = "LOCAL DB ENROLLED SLOTS: ${enrolledInDb.size} Total Students"
            fullList = enrolledInDb.map { s ->
                EnrolledDisplayItem(
                    slotId = s.fingerprintId,
                    srNo = s.srNo,
                    studentId = s.studentId,
                    name = s.name,
                    classSem = s.classSem,
                    grNo = s.grNo,
                    enrollmentNo = s.enrollmentNo,
                    attendedClasses = s.totalAttendedClasses
                )
            }
            filterList(etSearch.text.toString().trim())
            progressBar.visibility = View.GONE

            // 2. Query hardware sensor memory with 1.5s max timeout in background
            try {
                val hwRes = withTimeoutOrNull(1500L) {
                    RetrofitClient.getService().getFingerprintList()
                }

                if (hwRes != null && hwRes.isSuccessful && hwRes.body()?.success == true) {
                    val usedIds = hwRes.body()?.data?.usedIds ?: emptyList()
                    val usedCount = hwRes.body()?.data?.usedCount ?: usedIds.size
                    val availCount = hwRes.body()?.data?.availableCount ?: (512 - usedCount)
                    tvHwMemoryStatus.text = "R307S HARDWARE MEMORY STATUS:\nUsed Slots: $usedCount / 512 | Free Slots: $availCount"

                    val items = mutableListOf<EnrolledDisplayItem>()
                    for (id in usedIds.distinct().sorted()) {
                        val mapped = students.firstOrNull { it.fingerprintId == id }
                        if (mapped != null) {
                            items.add(
                                EnrolledDisplayItem(
                                    slotId = id,
                                    srNo = mapped.srNo,
                                    studentId = mapped.studentId,
                                    name = mapped.name,
                                    classSem = mapped.classSem,
                                    grNo = mapped.grNo,
                                    enrollmentNo = mapped.enrollmentNo,
                                    attendedClasses = mapped.totalAttendedClasses
                                )
                            )
                        } else {
                            items.add(
                                EnrolledDisplayItem(
                                    slotId = id,
                                    srNo = -1,
                                    studentId = "UNMAPPED",
                                    name = "Hardware Slot Enrolled",
                                    classSem = "N/A",
                                    grNo = "N/A",
                                    enrollmentNo = "N/A",
                                    attendedClasses = 0
                                )
                            )
                        }
                    }
                    fullList = items
                    filterList(etSearch.text.toString().trim())
                }
            } catch (_: Exception) {}
        }
    }

    private fun filterList(query: String) {
        filteredList = if (query.isEmpty()) {
            fullList
        } else {
            fullList.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.studentId.contains(query, ignoreCase = true) ||
                it.enrollmentNo.contains(query, ignoreCase = true) ||
                it.slotId.toString().contains(query)
            }
        }

        rvTable.adapter = ExcelAdapter(filteredList)
        if (filteredList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvTable.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvTable.visibility = View.VISIBLE
        }
    }

    private fun exportEnrolledCsv() {
        if (fullList.isEmpty()) {
            Toast.makeText(this, "No enrolled records to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val header = "Sr.No,Fingerprint ID,Student Name,Class/Sem,GR.No,Enrollment No\n"
            val sb = StringBuilder(header)
            fullList.forEachIndexed { idx, item ->
                sb.append("${idx + 1},Slot ${item.slotId},\"${item.name}\",${item.classSem},${item.grNo},${item.enrollmentNo}\n")
            }

            val file = File(cacheDir, "Enrolled_Sensor_IDs_Report.csv")
            file.writeText(sb.toString())

            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Enrolled Sensor IDs Report")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Export Enrolled IDs CSV"))
        } catch (e: Exception) {
            Toast.makeText(this, "Export Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    class ExcelAdapter(private val list: List<EnrolledDisplayItem>) :
        RecyclerView.Adapter<ExcelAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvSrNo: TextView = v.findViewById(R.id.tvRowSrNo)
            val tvSlotId: TextView = v.findViewById(R.id.tvRowSlotId)
            val tvName: TextView = v.findViewById(R.id.tvRowName)
            val tvClass: TextView = v.findViewById(R.id.tvRowClass)
            val tvGrNo: TextView = v.findViewById(R.id.tvRowGrNo)
            val tvEnrollNo: TextView = v.findViewById(R.id.tvRowEnrollNo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_enrolled_excel_row, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tvSrNo.text = (position + 1).toString()
            holder.tvSlotId.text = "Slot ${item.slotId}"
            holder.tvName.text = item.name
            holder.tvClass.text = item.classSem
            holder.tvGrNo.text = if (item.grNo.isNotEmpty()) item.grNo else "-"
            holder.tvEnrollNo.text = item.enrollmentNo
        }

        override fun getItemCount(): Int = list.size
    }
}
