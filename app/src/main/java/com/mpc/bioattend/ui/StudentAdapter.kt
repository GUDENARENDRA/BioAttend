package com.mpc.bioattend.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mpc.bioattend.R
import com.mpc.bioattend.model.StudentEntity

class StudentAdapter(
    private var students: List<StudentEntity>,
    private val onEditClick: (StudentEntity) -> Unit,
    private val onDeleteClick: (StudentEntity) -> Unit
) : RecyclerView.Adapter<StudentAdapter.StudentViewHolder>() {

    class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvStudentName)
        val tvRoll: TextView = itemView.findViewById(R.id.tvStudentRoll)
        val tvDept: TextView = itemView.findViewById(R.id.tvStudentDept)
        val tvFingerprintId: TextView = itemView.findViewById(R.id.tvFingerprintBadge)
        val tvAttendance: TextView = itemView.findViewById(R.id.tvAttendanceCount)
        val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditStudent)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteStudent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = students[position]
        holder.tvName.text = student.name
        holder.tvRoll.text = "Roll: ${student.enrollmentNo}"
        holder.tvDept.text = "Dept: ${student.classSem}"
        holder.tvAttendance.text = "Attendance: ${student.totalAttendedClasses}"

        if (student.fingerprintId >= 0) {
            holder.tvFingerprintId.text = "Finger ID: ${student.fingerprintId}"
            holder.tvFingerprintId.visibility = View.VISIBLE
        } else {
            holder.tvFingerprintId.text = "No Finger Enrolled"
            holder.tvFingerprintId.visibility = View.VISIBLE
        }

        holder.btnEdit.setOnClickListener { onEditClick(student) }
        holder.btnDelete.setOnClickListener { onDeleteClick(student) }
    }

    override fun getItemCount(): Int = students.size

    fun updateStudents(newList: List<StudentEntity>) {
        this.students = newList
        notifyDataSetChanged()
    }
}
