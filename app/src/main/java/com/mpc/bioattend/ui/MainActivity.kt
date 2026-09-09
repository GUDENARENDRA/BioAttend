package com.mpc.bioattend.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.mpc.bioattend.R
import com.mpc.bioattend.database.AppDatabase
import com.mpc.bioattend.model.TimetableEntity
import com.mpc.bioattend.model.UserSession
import com.mpc.bioattend.network.RetrofitClient
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var userSession: UserSession
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var btnUserAvatar: Button
    private lateinit var tvConnectionState: TextView
    private lateinit var viewStatusDot: View
    private lateinit var tvCurrentDay: TextView
    private lateinit var btnManageTimetable: Button
    private lateinit var rvTimetableCards: RecyclerView
    private lateinit var tvNoClassesMessage: TextView

    private lateinit var timetableAdapter: TimetableAdapter
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userSession = UserSession(this)

        if (!userSession.isLoggedIn) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        database = AppDatabase.getDatabase(this)

        initViews()
        setupDrawer()
        setupRecyclerView()
        observeTodayTimetable()
        testPing()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        btnUserAvatar = findViewById(R.id.btnUserAvatar)
        tvConnectionState = findViewById(R.id.tvConnectionState)
        viewStatusDot = findViewById(R.id.viewStatusDot)
        tvCurrentDay = findViewById(R.id.tvCurrentDay)
        btnManageTimetable = findViewById(R.id.btnManageTimetable)
        rvTimetableCards = findViewById(R.id.rvTimetableCards)
        tvNoClassesMessage = findViewById(R.id.tvNoClassesMessage)

        // Set top-left avatar initials
        btnUserAvatar.text = userSession.userInitials

        val daySdf = SimpleDateFormat("EEEE", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
        val dayName = daySdf.format(Date()).uppercase()
        tvCurrentDay.text = "Schedule: $dayName (Sem 7 - ICT)"

        btnUserAvatar.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        btnManageTimetable.setOnClickListener {
            startActivity(Intent(this, TimetableActivity::class.java))
        }
    }

    private fun setupDrawer() {
        navigationView.setNavigationItemSelectedListener(this)

        // Bind profile footer in drawer
        val tvDrawerAvatar = findViewById<TextView>(R.id.tvDrawerAvatar)
        val tvDrawerUserName = findViewById<TextView>(R.id.tvDrawerUserName)
        val tvDrawerDesignation = findViewById<TextView>(R.id.tvDrawerDesignation)
        val tvDrawerEmail = findViewById<TextView>(R.id.tvDrawerEmail)

        tvDrawerAvatar.text = userSession.userInitials
        tvDrawerUserName.text = userSession.userName
        tvDrawerDesignation.text = userSession.designation
        tvDrawerEmail.text = userSession.userEmail
    }

    private fun setupRecyclerView() {
        rvTimetableCards.layoutManager = LinearLayoutManager(this)
        timetableAdapter = TimetableAdapter(emptyList()) { slot, isActive ->
            onTimetableSlotClicked(slot, isActive)
        }
        rvTimetableCards.adapter = timetableAdapter
    }

    private fun observeTodayTimetable() {
        val shortSdf = SimpleDateFormat("EEE", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
        val currentDayShort = shortSdf.format(Date()).uppercase()
        val queryDay = currentDayShort

        lifecycleScope.launch {
            database.timetableDao().getSlotsForDay(queryDay).collectLatest { slots ->
                timetableAdapter.updateSlots(slots)
                if (slots.isEmpty()) {
                    tvNoClassesMessage.visibility = View.VISIBLE
                    rvTimetableCards.visibility = View.GONE
                } else {
                    tvNoClassesMessage.visibility = View.GONE
                    rvTimetableCards.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun onTimetableSlotClicked(slot: TimetableEntity, isActive: Boolean) {
        val intent = Intent(this, ClassAttendanceActivity::class.java).apply {
            putExtra("slotId", slot.id)
            putExtra("subjectCode", slot.subjectCode)
            putExtra("subjectName", slot.subjectName)
            putExtra("startTime", slot.startTime)
            putExtra("endTime", slot.endTime)
            putExtra("classroom", slot.classroom)
            putExtra("facultyName", slot.facultyName)
            putExtra("isActive", isActive)
        }
        startActivity(intent)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        drawerLayout.closeDrawer(GravityCompat.START)
        when (item.itemId) {
            R.id.nav_connectivity -> startActivity(Intent(this, ConnectivityActivity::class.java))
            R.id.nav_user_data -> startActivity(Intent(this, UserDataActivity::class.java))
            R.id.nav_timetable -> startActivity(Intent(this, TimetableActivity::class.java))
            R.id.nav_enrollment_manager -> startActivity(Intent(this, EnrollmentManagerActivity::class.java))
            R.id.nav_enrolled_id_list -> startActivity(Intent(this, EnrolledIdListActivity::class.java))
            R.id.nav_logout -> {
                userSession.isLoggedIn = false
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
            }
        }
        return true
    }

    private fun showEnrolledIdListDialog() {
        lifecycleScope.launch {
            val students = database.studentDao().getAllStudentsList()
            val enrolledInDb = students.filter { it.fingerprintId >= 0 }.distinctBy { it.fingerprintId }.sortedBy { it.fingerprintId }

            var usedIds = enrolledInDb.map { it.fingerprintId }.distinct().sorted()
            var usedCount = usedIds.size
            var availCount = 512 - usedCount
            var isHwSynced = false

            try {
                val hwRes = withTimeoutOrNull(1500L) {
                    RetrofitClient.getService().getFingerprintList()
                }

                if (hwRes != null && hwRes.isSuccessful && hwRes.body()?.success == true) {
                    usedIds = hwRes.body()?.data?.usedIds ?: usedIds
                    usedCount = hwRes.body()?.data?.usedCount ?: usedIds.size
                    availCount = hwRes.body()?.data?.availableCount ?: (512 - usedCount)
                    isHwSynced = true
                }
            } catch (_: Exception) {}

            val sb = StringBuilder()
            if (isHwSynced) {
                sb.append("R307S HARDWARE MEMORY STATUS:\n")
                sb.append("Used Slots: $usedCount / 512 | Free: $availCount\n\n")
            } else {
                sb.append("LOCAL DATABASE ENROLLED SLOTS:\n")
                sb.append("Total Enrolled: $usedCount Students\n\n")
            }

            if (usedIds.isEmpty()) {
                sb.append("No active fingerprint IDs stored in memory.")
            } else {
                sb.append("ENROLLED SENSOR IDS LIST:\n\n")
                for (id in usedIds.distinct().sorted()) {
                    val mappedStudent = students.firstOrNull { it.fingerprintId == id }
                    if (mappedStudent != null) {
                        sb.append("- Sr.No: ${mappedStudent.srNo} | Finger ID: Slot $id | Name: ${mappedStudent.name} | Class: ${mappedStudent.classSem} | GR: ${mappedStudent.grNo} | Enroll: ${mappedStudent.enrollmentNo}\n\n")
                    } else {
                        sb.append("- Finger ID: Slot $id - Hardware Enrolled (Unmapped)\n\n")
                    }
                }
            }

            AlertDialog.Builder(this@MainActivity, R.style.GlassDialogTheme)
                .setTitle(if (isHwSynced) "R307S ENROLLED SENSOR IDS" else "ENROLLED SENSOR IDS (LOCAL)")
                .setMessage(sb.toString().trimEnd())
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        RetrofitClient.init(this)
        testPing()
    }

    private fun testPing() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getService().getDeviceStatus()
                if (response.isSuccessful && response.body() != null) {
                    tvConnectionState.text = "Online"
                    tvConnectionState.setTextColor("#00F5A0".toColorInt())
                    viewStatusDot.setBackgroundResource(R.drawable.bg_button_emerald)
                } else {
                    tvConnectionState.text = "Reachable"
                    tvConnectionState.setTextColor("#FFB800".toColorInt())
                    viewStatusDot.setBackgroundResource(R.drawable.bg_button_emerald)
                }
            } catch (_: Exception) {
                tvConnectionState.text = "Offline"
                tvConnectionState.setTextColor("#FF4B4B".toColorInt())
            }
        }
    }
}
