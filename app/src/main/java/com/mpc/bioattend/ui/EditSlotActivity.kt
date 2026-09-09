package com.mpc.bioattend.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mpc.bioattend.R
import com.mpc.bioattend.database.AppDatabase
import com.mpc.bioattend.model.TimetableEntity
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class EditSlotActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var spinnerDay: Spinner
    private lateinit var etStartTime: EditText
    private lateinit var spinnerStartAmPm: Spinner
    private lateinit var etEndTime: EditText
    private lateinit var spinnerEndAmPm: Spinner
    private lateinit var etSubjectCode: AutoCompleteTextView
    private lateinit var etSubjectName: AutoCompleteTextView
    private lateinit var etFacultyInitials: EditText
    private lateinit var etFacultyName: AutoCompleteTextView
    private lateinit var etClassroom: AutoCompleteTextView
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private lateinit var database: AppDatabase
    private var slotId: Long = -1L
    private var existingSlot: TimetableEntity? = null
    private val daysList = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val amPmList = listOf("AM", "PM")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_slot)

        database = AppDatabase.getDatabase(this)
        slotId = intent.getLongExtra("EXTRA_SLOT_ID", -1L)

        initViews()
        loadSlotData()
        setupAutoCompleteSuggestions()
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tvEditSlotPageTitle)
        spinnerDay = findViewById(R.id.spinnerPageDay)
        etStartTime = findViewById(R.id.etPageStartTime)
        spinnerStartAmPm = findViewById(R.id.spinnerStartAmPm)
        etEndTime = findViewById(R.id.etPageEndTime)
        spinnerEndAmPm = findViewById(R.id.spinnerEndAmPm)
        etSubjectCode = findViewById(R.id.etPageSubjectCode)
        etSubjectName = findViewById(R.id.etPageSubjectName)
        etFacultyInitials = findViewById(R.id.etPageFacultyInitials)
        etFacultyName = findViewById(R.id.etPageFacultyName)
        etClassroom = findViewById(R.id.etPageClassroom)
        btnSave = findViewById(R.id.btnSaveSlotRecord)
        btnCancel = findViewById(R.id.btnCancelSlotRecord)

        etSubjectCode.setOnClickListener { etSubjectCode.showDropDown() }
        etSubjectName.setOnClickListener { etSubjectName.showDropDown() }
        etFacultyName.setOnClickListener { etFacultyName.showDropDown() }
        etClassroom.setOnClickListener { etClassroom.showDropDown() }

        val dayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, daysList)
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDay.adapter = dayAdapter

        val amPmAdapter1 = ArrayAdapter(this, android.R.layout.simple_spinner_item, amPmList)
        amPmAdapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStartAmPm.adapter = amPmAdapter1

        val amPmAdapter2 = ArrayAdapter(this, android.R.layout.simple_spinner_item, amPmList)
        amPmAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEndAmPm.adapter = amPmAdapter2

        // TimePickerDialog helpers
        etStartTime.setOnClickListener { showTimePicker(etStartTime, spinnerStartAmPm) }
        etEndTime.setOnClickListener { showTimePicker(etEndTime, spinnerEndAmPm) }

        btnSave.setOnClickListener { saveSlot() }
        btnCancel.setOnClickListener { finish() }
    }

    private fun setupAutoCompleteSuggestions() {
        lifecycleScope.launch {
            val dao = database.timetableDao()

            val codes = dao.getDistinctSubjectCodes()
            if (codes.isNotEmpty()) {
                etSubjectCode.setAdapter(ArrayAdapter(this@EditSlotActivity, android.R.layout.simple_dropdown_item_1line, codes))
            }

            val names = dao.getDistinctSubjectNames()
            if (names.isNotEmpty()) {
                etSubjectName.setAdapter(ArrayAdapter(this@EditSlotActivity, android.R.layout.simple_dropdown_item_1line, names))
            }

            val faculties = dao.getDistinctFacultyNames()
            if (faculties.isNotEmpty()) {
                etFacultyName.setAdapter(ArrayAdapter(this@EditSlotActivity, android.R.layout.simple_dropdown_item_1line, faculties))
            }

            val rooms = dao.getDistinctClassrooms()
            if (rooms.isNotEmpty()) {
                etClassroom.setAdapter(ArrayAdapter(this@EditSlotActivity, android.R.layout.simple_dropdown_item_1line, rooms))
            }
        }
    }

    private fun showTimePicker(targetEditText: EditText, targetSpinner: Spinner) {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        val timePicker = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val formattedMinute = String.format(Locale.getDefault(), "%02d", selectedMinute)
            if (selectedHour >= 12) {
                val displayHour = if (selectedHour == 12) 12 else selectedHour - 12
                val formattedHour = String.format(Locale.getDefault(), "%02d", displayHour)
                targetEditText.setText("$formattedHour:$formattedMinute")
                targetSpinner.setSelection(1) // PM
            } else {
                val displayHour = if (selectedHour == 0) 12 else selectedHour
                val formattedHour = String.format(Locale.getDefault(), "%02d", displayHour)
                targetEditText.setText("$formattedHour:$formattedMinute")
                targetSpinner.setSelection(0) // AM
            }
        }, hour, minute, false)
        timePicker.show()
    }

    private fun loadSlotData() {
        if (slotId != -1L) {
            tvTitle.text = "Edit Timetable Slot"
            lifecycleScope.launch {
                val slot = database.timetableDao().getSlotById(slotId)
                if (slot != null) {
                    existingSlot = slot
                    val dayIndex = daysList.indexOf(slot.dayOfWeek).coerceAtLeast(0)
                    spinnerDay.setSelection(dayIndex)

                    populateTimeString(slot.startTime, etStartTime, spinnerStartAmPm)
                    populateTimeString(slot.endTime, etEndTime, spinnerEndAmPm)

                    etSubjectCode.setText(slot.subjectCode)
                    etSubjectName.setText(slot.subjectName)
                    etFacultyInitials.setText(slot.facultyInitials)
                    etFacultyName.setText(slot.facultyName)
                    etClassroom.setText(slot.classroom)
                }
            }
        } else {
            tvTitle.text = "Add Timetable Slot"
        }
    }

    private fun populateTimeString(fullTimeStr: String, targetEt: EditText, targetSpinner: Spinner) {
        val upper = fullTimeStr.trim().uppercase(Locale.ROOT)
        if (upper.contains("PM")) {
            targetSpinner.setSelection(1)
            targetEt.setText(upper.replace("PM", "").trim())
        } else if (upper.contains("AM")) {
            targetSpinner.setSelection(0)
            targetEt.setText(upper.replace("AM", "").trim())
        } else {
            targetSpinner.setSelection(0)
            targetEt.setText(fullTimeStr.trim())
        }
    }

    private fun saveSlot() {
        val dayOfWeek = spinnerDay.selectedItem.toString()
        val startTimeInput = etStartTime.text.toString().trim()
        val startAmPm = spinnerStartAmPm.selectedItem.toString()
        val endTimeInput = etEndTime.text.toString().trim()
        val endAmPm = spinnerEndAmPm.selectedItem.toString()

        val subjectCode = etSubjectCode.text.toString().trim()
        val subjectName = etSubjectName.text.toString().trim()
        val facultyInitials = etFacultyInitials.text.toString().trim()
        val facultyName = etFacultyName.text.toString().trim()
        val classroom = etClassroom.text.toString().trim()

        if (subjectCode.isEmpty() || startTimeInput.isEmpty() || endTimeInput.isEmpty()) {
            Toast.makeText(this, "Subject Code, Start Time, and End Time are required!", Toast.LENGTH_SHORT).show()
            return
        }

        val startTimeFormatted = "$startTimeInput $startAmPm"
        val endTimeFormatted = "$endTimeInput $endAmPm"

        lifecycleScope.launch {
            val curr = existingSlot
            if (curr != null) {
                val updated = curr.copy(
                    dayOfWeek = dayOfWeek,
                    startTime = startTimeFormatted,
                    endTime = endTimeFormatted,
                    subjectCode = subjectCode,
                    subjectName = subjectName,
                    facultyInitials = facultyInitials,
                    facultyName = facultyName,
                    classroom = classroom
                )
                database.timetableDao().updateSlot(updated)
                Toast.makeText(this@EditSlotActivity, "Slot updated!", Toast.LENGTH_SHORT).show()
            } else {
                val newSlot = TimetableEntity(
                    dayOfWeek = dayOfWeek,
                    startTime = startTimeFormatted,
                    endTime = endTimeFormatted,
                    subjectCode = subjectCode,
                    subjectName = subjectName,
                    facultyInitials = facultyInitials,
                    facultyName = facultyName,
                    classroom = classroom
                )
                database.timetableDao().insertSlot(newSlot)
                Toast.makeText(this@EditSlotActivity, "Slot created!", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
