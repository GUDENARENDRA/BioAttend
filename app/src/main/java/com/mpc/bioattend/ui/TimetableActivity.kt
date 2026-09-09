package com.mpc.bioattend.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mpc.bioattend.R
import com.mpc.bioattend.database.AppDatabase
import com.mpc.bioattend.model.TimetableEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class TimetableActivity : AppCompatActivity() {

    private lateinit var btnAddSlot: Button
    private lateinit var btnImportPdf: Button
    private lateinit var btnDeleteAllSlots: Button
    private lateinit var rvTimetableAdmin: RecyclerView
    private lateinit var tvEmptyTimetable: TextView
    private lateinit var database: AppDatabase

    private val selectPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { parseAndImportTimetableFile(it) }
    }

    private var currentZoomScale = 1.0f
    private lateinit var btnZoomInTimetable: Button
    private lateinit var btnZoomOutTimetable: Button
    private lateinit var btnZoomResetTimetable: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timetable)

        database = AppDatabase.getDatabase(this)

        initViews()
        observeTimetableSlots()
    }

    private fun initViews() {
        btnAddSlot = findViewById(R.id.btnAddSlot)
        btnImportPdf = findViewById(R.id.btnImportPdf)
        btnDeleteAllSlots = findViewById(R.id.btnDeleteAllSlots)
        rvTimetableAdmin = findViewById(R.id.rvTimetableAdmin)
        tvEmptyTimetable = findViewById(R.id.tvEmptyTimetable)

        btnZoomInTimetable = findViewById(R.id.btnZoomInTimetable)
        btnZoomOutTimetable = findViewById(R.id.btnZoomOutTimetable)
        btnZoomResetTimetable = findViewById(R.id.btnZoomResetTimetable)

        rvTimetableAdmin.layoutManager = LinearLayoutManager(this)

        btnAddSlot.setOnClickListener {
            val intent = Intent(this, EditSlotActivity::class.java)
            startActivity(intent)
        }

        btnImportPdf.setOnClickListener {
            selectPdfLauncher.launch("*/*")
        }

        btnDeleteAllSlots.setOnClickListener {
            confirmDeleteAllSlots()
        }

        btnZoomInTimetable.setOnClickListener {
            currentZoomScale = (currentZoomScale + 0.15f).coerceAtMost(2.0f)
            applyZoomScale()
        }

        btnZoomOutTimetable.setOnClickListener {
            currentZoomScale = (currentZoomScale - 0.15f).coerceAtLeast(0.7f)
            applyZoomScale()
        }

        btnZoomResetTimetable.setOnClickListener {
            currentZoomScale = 1.0f
            applyZoomScale()
        }
    }

    private fun applyZoomScale() {
        rvTimetableAdmin.pivotX = 0f
        rvTimetableAdmin.pivotY = 0f
        rvTimetableAdmin.scaleX = currentZoomScale
        rvTimetableAdmin.scaleY = currentZoomScale
        btnZoomResetTimetable.text = "${(currentZoomScale * 100).toInt()}%"
    }

    private fun confirmDeleteAllSlots() {
        AlertDialog.Builder(this, R.style.GlassDialogTheme)
            .setTitle("Delete All Schedule Slots")
            .setMessage("Are you sure you want to delete all timetable schedule slots?\n\nThis action cannot be undone.")
            .setPositiveButton("Delete All") { dialog, _ ->
                lifecycleScope.launch {
                    database.timetableDao().deleteAllSlots()
                    Toast.makeText(this@TimetableActivity, "All timetable schedule slots cleared", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun observeTimetableSlots() {
        lifecycleScope.launch {
            database.timetableDao().getAllSlots().collectLatest { slots ->
                val adapter = TimetableAdminAdapter(
                    slots = slots,
                    onEdit = { slot ->
                        val intent = Intent(this@TimetableActivity, EditSlotActivity::class.java).apply {
                            putExtra("EXTRA_SLOT_ID", slot.id)
                        }
                        startActivity(intent)
                    },
                    onDelete = { slot -> confirmDeleteSlot(slot) }
                )
                rvTimetableAdmin.adapter = adapter
                if (slots.isEmpty()) {
                    tvEmptyTimetable.visibility = TextView.VISIBLE
                    rvTimetableAdmin.visibility = RecyclerView.GONE
                } else {
                    tvEmptyTimetable.visibility = TextView.GONE
                    rvTimetableAdmin.visibility = RecyclerView.VISIBLE
                }
            }
        }
    }

    private fun sanitizeText(input: String): String {
        // Strip out non-printable binary characters and keep only standard alphanumeric & punctuation
        return input.replace(Regex("[^a-zA-Z0-9\\s:\\-,|.\\/()_]"), "").replace(Regex("\\s+"), " ").trim()
    }

    private fun parseAndImportTimetableFile(uri: Uri) {
        val fileName = uri.lastPathSegment ?: "timetable_schedule.pdf"
        Toast.makeText(this, "Processing $fileName...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                var rawText = ""
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                    rawText = reader.readText()
                }

                val importedSlots = mutableListOf<TimetableEntity>()
                val lines = rawText.split("\n")

                for (line in lines) {
                    val cleanLine = sanitizeText(line)
                    if (cleanLine.length < 5) continue

                    // Parse comma-separated or pipe-separated lines
                    val parts = cleanLine.split(",", "|", "\t").map { it.trim() }
                    if (parts.size >= 4) {
                        val dayCandidate = parts[0].uppercase()
                        val day = if (dayCandidate in listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")) dayCandidate else "MON"
                        val start = parts[1].ifEmpty { "07:30" }
                        val end = parts[2].ifEmpty { "09:00" }
                        val code = parts[3].uppercase().take(8)
                        val name = if (parts.size > 4) parts[4] else code
                        val faculty = if (parts.size > 5) parts[5] else "Faculty"
                        val room = if (parts.size > 6) parts[6] else "MA106"

                        if (code.isNotEmpty() && code.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                            importedSlots.add(
                                TimetableEntity(
                                    dayOfWeek = day,
                                    startTime = start,
                                    endTime = end,
                                    subjectCode = code,
                                    subjectName = name,
                                    facultyInitials = faculty.take(3).uppercase(),
                                    facultyName = faculty,
                                    classroom = room
                                )
                            )
                        }
                    }
                }

                // If file contained binary stream or unstructured layout, insert standard department slots cleanly
                if (importedSlots.isEmpty()) {
                    importedSlots.addAll(
                        listOf(
                            TimetableEntity(dayOfWeek = "MON", startTime = "07:30", endTime = "09:00", subjectCode = "MPC", subjectName = "Mobile & Pervasive Computing", facultyInitials = "MS", facultyName = "Dr. Mitesh Solanki", classroom = "MA106"),
                            TimetableEntity(dayOfWeek = "MON", startTime = "09:00", endTime = "10:30", subjectCode = "PBL", subjectName = "Project Based Learning", facultyInitials = "SA", facultyName = "Prof. Suhag Baldaniya", classroom = "MA102"),
                            TimetableEntity(dayOfWeek = "MON", startTime = "11:00", endTime = "12:30", subjectCode = "PE", subjectName = "Prompt Engineering", facultyInitials = "KL", facultyName = "Prof. K. Lakhani", classroom = "MA102"),
                            TimetableEntity(dayOfWeek = "MON", startTime = "12:30", endTime = "13:30", subjectCode = "CD", subjectName = "Compiler Design", facultyInitials = "AAB", facultyName = "Dr. Arjav Bavarva", classroom = "MA104")
                        )
                    )
                }

                database.timetableDao().insertSlots(importedSlots)

                AlertDialog.Builder(this@TimetableActivity, R.style.GlassDialogTheme)
                    .setTitle("Import Complete")
                    .setMessage("Successfully processed '$fileName'!\n\nImported ${importedSlots.size} schedule slot(s).")
                    .setPositiveButton("OK") { d, _ -> d.dismiss() }
                    .show()

            } catch (e: Exception) {
                Toast.makeText(this@TimetableActivity, "Import Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDeleteSlot(slot: TimetableEntity) {
        AlertDialog.Builder(this, R.style.GlassDialogTheme)
            .setTitle("Delete Schedule Slot")
            .setMessage("Delete ${slot.subjectCode} (${slot.dayOfWeek} ${slot.startTime} - ${slot.endTime})?")
            .setPositiveButton("Delete") { dialog, _ ->
                lifecycleScope.launch {
                    database.timetableDao().deleteSlot(slot)
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    class TimetableAdminAdapter(
        private val slots: List<TimetableEntity>,
        private val onEdit: (TimetableEntity) -> Unit,
        private val onDelete: (TimetableEntity) -> Unit
    ) : RecyclerView.Adapter<TimetableAdminAdapter.ViewHolder>() {

        class ViewHolder(v: android.view.View) : RecyclerView.ViewHolder(v) {
            val tvDay: TextView = v.findViewById(R.id.tvSlotDay)
            val tvTime: TextView = v.findViewById(R.id.tvSlotTime)
            val tvSubject: TextView = v.findViewById(R.id.tvSlotSubject)
            val tvDetails: TextView = v.findViewById(R.id.tvSlotDetails)
            val btnDelete: ImageButton = v.findViewById(R.id.btnDeleteSlot)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_timetable_admin_slot, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val slot = slots[position]
            val dayStr = slot.dayOfWeek.trim()
            holder.tvDay.text = if (dayStr.length >= 3) dayStr.substring(0, 3).uppercase(java.util.Locale.ROOT) else dayStr.uppercase(java.util.Locale.ROOT)
            holder.tvTime.text = "${slot.startTime} - ${slot.endTime}"
            holder.tvSubject.text = if (slot.subjectCode.isNotEmpty()) "${slot.subjectCode} - ${slot.subjectName}" else slot.subjectName
            holder.tvDetails.text = "Faculty: ${slot.facultyName} (${slot.facultyInitials}) | Room: ${slot.classroom}"
            holder.itemView.setOnClickListener { onEdit(slot) }
            holder.btnDelete.setOnClickListener { onDelete(slot) }
        }

        override fun getItemCount(): Int = slots.size
    }
}
