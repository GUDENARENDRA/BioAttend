package com.mpc.bioattend.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mpc.bioattend.R
import com.mpc.bioattend.model.TimetableEntity
import java.text.SimpleDateFormat
import java.util.*

class TimetableAdapter(
    private var slots: List<TimetableEntity>,
    private val onSlotClicked: (TimetableEntity, Boolean) -> Unit
) : RecyclerView.Adapter<TimetableAdapter.TimetableViewHolder>() {

    class TimetableViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSlotDayBadge: TextView? = itemView.findViewById(R.id.tvSlotDayBadge)
        val tvClassTime: TextView = itemView.findViewById(R.id.tvClassTime)
        val tvAttendedBadge: TextView = itemView.findViewById(R.id.tvAttendedCountBadge)
        val tvSubjectTitle: TextView = itemView.findViewById(R.id.tvSubjectTitle)
        val tvFacultyName: TextView = itemView.findViewById(R.id.tvFacultyName)
        val tvClassroom: TextView = itemView.findViewById(R.id.tvClassroom)
        val tvLockStatus: TextView = itemView.findViewById(R.id.tvLockStatus)
        val cardSlot: View = itemView.findViewById(R.id.cardTimetableSlot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimetableViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_timetable_card, parent, false)
        return TimetableViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimetableViewHolder, position: Int) {
        val slot = slots[position]
        holder.tvSlotDayBadge?.text = if (slot.dayOfWeek.length >= 3) slot.dayOfWeek.substring(0, 3).uppercase(Locale.ROOT) else slot.dayOfWeek.uppercase(Locale.ROOT)
        holder.tvClassTime.text = "${slot.startTime} - ${slot.endTime}"
        holder.tvSubjectTitle.text = if (slot.subjectCode.isNotEmpty()) "${slot.subjectCode} - ${slot.subjectName}" else slot.subjectName
        holder.tvFacultyName.text = "Faculty: ${slot.facultyName} (${slot.facultyInitials})"
        holder.tvClassroom.text = "Room: ${slot.classroom}"
        holder.tvAttendedBadge.text = "Attended: ${slot.attendedCount}"

        val isActive = isCurrentTimeInSlot(slot.startTime, slot.endTime)

        if (isActive) {
            holder.tvLockStatus.text = "TAP TO MARK ATTENDANCE (ACTIVE CLASS TIME)"
            holder.tvLockStatus.setTextColor(Color.parseColor("#00F5A0"))
            holder.cardSlot.alpha = 1.0f
        } else {
            holder.tvLockStatus.text = "ATTENDANCE LOCKED (CLASS TIME: ${slot.startTime} - ${slot.endTime})"
            holder.tvLockStatus.setTextColor(Color.parseColor("#94A3B8"))
            holder.cardSlot.alpha = 0.75f
        }

        holder.cardSlot.setOnClickListener {
            onSlotClicked(slot, isActive)
        }
    }

    override fun getItemCount(): Int = slots.size

    fun updateSlots(newSlots: List<TimetableEntity>) {
        this.slots = newSlots
        notifyDataSetChanged()
    }

    private fun isCurrentTimeInSlot(startTimeStr: String, endTimeStr: String): Boolean {
        val startMins = parseToMinutes(startTimeStr)
        val endMins = parseToMinutes(endTimeStr)
        if (startMins < 0 || endMins < 0) return true

        val istZone = TimeZone.getTimeZone("Asia/Kolkata")
        val now = Calendar.getInstance(istZone)
        val currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        return currentMins in startMins..endMins
    }

    private fun parseToMinutes(timeStr: String): Int {
        val trimmed = timeStr.trim().uppercase()
        val formats = arrayOf("hh:mm a", "h:mm a", "HH:mm", "H:mm")
        val istZone = TimeZone.getTimeZone("Asia/Kolkata")

        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US).apply { timeZone = istZone }
                val cal = Calendar.getInstance(istZone).apply { time = sdf.parse(trimmed)!! }
                return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            } catch (_: Exception) {}
        }

        try {
            val isPm = trimmed.contains("PM")
            val isAm = trimmed.contains("AM")
            val clean = trimmed.replace("AM", "").replace("PM", "").trim()
            val parts = clean.split(":")
            var hour = parts[0].toInt()
            val min = parts[1].toInt()
            if (isPm && hour < 12) hour += 12
            if (isAm && hour == 12) hour = 0
            return hour * 60 + min
        } catch (_: Exception) {
            return -1
        }
    }
}
