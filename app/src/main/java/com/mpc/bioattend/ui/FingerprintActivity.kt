package com.mpc.bioattend.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mpc.bioattend.R
import com.mpc.bioattend.network.RetrofitClient
import kotlinx.coroutines.launch

class FingerprintActivity : AppCompatActivity() {

    private lateinit var btnEnrollPage: Button
    private lateinit var btnIdentifyPage: Button
    private lateinit var btnCheckCount: Button
    private lateinit var btnDeleteSingle: Button
    private lateinit var btnDeleteAll: Button
    private lateinit var tvOutput: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fingerprint)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        btnEnrollPage = findViewById(R.id.btnEnrollPage)
        btnIdentifyPage = findViewById(R.id.btnIdentifyPage)
        btnCheckCount = findViewById(R.id.btnCheckCount)
        btnDeleteSingle = findViewById(R.id.btnDeleteSingle)
        btnDeleteAll = findViewById(R.id.btnDeleteAll)
        tvOutput = findViewById(R.id.tvFingerprintOutput)
    }

    private fun setupListeners() {
        btnEnrollPage.setOnClickListener {
            startActivity(Intent(this, EnrollActivity::class.java))
        }

        btnIdentifyPage.setOnClickListener {
            startActivity(Intent(this, IdentifyActivity::class.java))
        }

        btnCheckCount.setOnClickListener {
            checkFingerprintCount()
        }

        btnDeleteSingle.setOnClickListener {
            showDeleteSingleDialog()
        }

        btnDeleteAll.setOnClickListener {
            confirmDeleteAll()
        }
    }

    private fun checkFingerprintCount() {
        tvOutput.text = "Checking fingerprint count on R307S sensor..."
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getService().getFingerprintCount()
                if (response.isSuccessful && response.body()?.success == true) {
                    val count = response.body()?.count ?: 0
                    tvOutput.text = " Total Fingerprints Stored on ESP32/R307S: $count"
                } else {
                    tvOutput.text = " Failed to retrieve count: ${response.body()?.message ?: "Unknown error"}"
                }
            } catch (e: Exception) {
                tvOutput.text = " Error: ${e.localizedMessage}"
            }
        }
    }

    private fun showDeleteSingleDialog() {
        val et = EditText(this).apply {
            hint = "Enter Fingerprint ID to delete (e.g. 1)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        AlertDialog.Builder(this, R.style.GlassDialogTheme)
            .setTitle("Delete Single Fingerprint")
            .setView(et)
            .setPositiveButton("Delete") { dialog, _ ->
                val idStr = et.text.toString().trim()
                if (idStr.isNotEmpty()) {
                    deleteSingleFingerprint(idStr.toInt())
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun deleteSingleFingerprint(id: Int) {
        tvOutput.text = "Deleting Fingerprint ID $id..."
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getService().deleteFingerprint(id)
                if (response.isSuccessful && response.body()?.success == true) {
                    tvOutput.text = " Fingerprint ID $id deleted successfully from R307S memory."
                    Toast.makeText(this@FingerprintActivity, "Deleted ID $id", Toast.LENGTH_SHORT).show()
                } else {
                    tvOutput.text = " Failed to delete Fingerprint ID $id: ${response.body()?.message ?: "Not found"}"
                }
            } catch (e: Exception) {
                tvOutput.text = " Error: ${e.localizedMessage}"
            }
        }
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this, R.style.GlassDialogTheme)
            .setTitle(" Delete ALL Fingerprints?")
            .setMessage("This will wipe all stored fingerprint templates from the R307S sensor. Proceed?")
            .setPositiveButton("WIPE ALL") { dialog, _ ->
                deleteAllFingerprints()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun deleteAllFingerprints() {
        tvOutput.text = "Wiping all fingerprints from R307S database..."
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getService().deleteAllFingerprints()
                if (response.isSuccessful && response.body()?.success == true) {
                    tvOutput.text = " All fingerprint records successfully wiped."
                    Toast.makeText(this@FingerprintActivity, "All fingerprints deleted", Toast.LENGTH_SHORT).show()
                } else {
                    tvOutput.text = " Failed to wipe fingerprints: ${response.body()?.message ?: "Error"}"
                }
            } catch (e: Exception) {
                tvOutput.text = " Error: ${e.localizedMessage}"
            }
        }
    }
}
