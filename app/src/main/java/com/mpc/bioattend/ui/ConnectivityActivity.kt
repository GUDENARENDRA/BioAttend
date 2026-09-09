package com.mpc.bioattend.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mpc.bioattend.R
import com.mpc.bioattend.model.WifiConnectRequest
import com.mpc.bioattend.model.WifiNetwork
import com.mpc.bioattend.network.RetrofitClient
import kotlinx.coroutines.launch

class ConnectivityActivity : AppCompatActivity() {

    private lateinit var btnCheckDeviceStatus: Button
    private lateinit var btnScanWifi: Button
    private lateinit var btnCheckWifiStatus: Button
    private lateinit var etIpAddress: EditText
    private lateinit var btnSetIp: Button
    private lateinit var layoutWifiListContainer: View
    private lateinit var rvWifiNetworks: RecyclerView
    private lateinit var tvStatusOutput: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var wifiAdapter: WifiAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connectivity)

        initViews()
        setupRecyclerView()
        setupListeners()
        checkPhoneWifiState()
    }

    private fun initViews() {
        btnCheckDeviceStatus = findViewById(R.id.btnCheckDeviceStatus)
        btnScanWifi = findViewById(R.id.btnScanWifi)
        btnCheckWifiStatus = findViewById(R.id.btnCheckWifiStatus)
        etIpAddress = findViewById(R.id.etIpAddress)
        btnSetIp = findViewById(R.id.btnSetIp)
        layoutWifiListContainer = findViewById(R.id.layoutWifiListContainer)
        rvWifiNetworks = findViewById(R.id.rvWifiNetworks)
        tvStatusOutput = findViewById(R.id.tvStatusOutput)
        progressBar = findViewById(R.id.progressBarConnectivity)

        layoutWifiListContainer.visibility = View.GONE
        etIpAddress.setText(RetrofitClient.getBaseUrl().removePrefix("http://").removeSuffix("/"))
    }

    private fun setupRecyclerView() {
        rvWifiNetworks.layoutManager = LinearLayoutManager(this)
        wifiAdapter = WifiAdapter(emptyList()) { selectedNetwork ->
            onWifiNetworkClicked(selectedNetwork)
        }
        rvWifiNetworks.adapter = wifiAdapter
    }

    private fun setupListeners() {
        btnSetIp.setOnClickListener {
            val ip = etIpAddress.text.toString().trim()
            if (ip.isNotEmpty()) {
                RetrofitClient.setBaseIp(ip, this)
                Toast.makeText(this, "Target ESP32 IP set to $ip and saved", Toast.LENGTH_SHORT).show()
                checkDeviceStatus()
            }
        }

        btnCheckDeviceStatus.setOnClickListener { checkDeviceStatus() }
        btnScanWifi.setOnClickListener { scanWifiNetworks() }
        btnCheckWifiStatus.setOnClickListener { checkWifiStatus() }
    }

    private fun checkPhoneWifiState() {
        val wifiMgr = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        if (!wifiMgr.isWifiEnabled) {
            AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Wi-Fi is Turned OFF")
                .setMessage("BioAttend requires Wi-Fi to communicate with the ESP32 hardware over local network (avoiding mobile SIM data).\n\nWould you like to turn ON Wi-Fi now?")
                .setPositiveButton("Turn ON Wi-Fi") { _, _ ->
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun checkDeviceStatus() {
        showLoading(isLoading = true)
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getService().getDeviceStatus()
                showLoading(false)
                if ((response.isSuccessful) && (response.body() != null)) {
                    val status = response.body()!!
                    val deviceData = status.data
                    val sensorStatus = if (deviceData?.sensorReady == true) "READY" else "NOT DETECTED"
                    val oledStatus = if (deviceData?.oledReady == true) "READY" else "OFF"
                    val uptimeSec = (deviceData?.uptimeMs ?: 0) / 1000

                    val statusText = """
                        ESP32 System Core: ONLINE (${deviceData?.wifiMode ?: "AP"} Mode)
                        OLED Display: $oledStatus
                        Fingerprint Sensor: $sensorStatus
                        ESP32 IP: ${deviceData?.ip ?: "192.168.4.1"}
                        Hardware Uptime: ${uptimeSec}s
                    """.trimIndent()
                    tvStatusOutput.text = statusText
                    Toast.makeText(this@ConnectivityActivity, "Device status retrieved successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    tvStatusOutput.text = "Failed to fetch device status (HTTP ${response.code()})"
                }
            } catch (e: Exception) {
                showLoading(false)
                tvStatusOutput.text = "Error connecting to ESP32: ${e.localizedMessage}"
            }
        }
    }

    private fun scanWifiNetworks() {
        showLoading(isLoading = true)
        tvStatusOutput.text = "Scanning surrounding Wi-Fi networks..."
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getService().scanWifi()
                showLoading(false)
                if (response.isSuccessful && response.body()?.success == true) {
                    val networks = response.body()?.networks ?: emptyList()
                    if (networks.isNotEmpty()) {
                        val sortedNetworks = networks.sortedByDescending { it.rssi }
                        wifiAdapter.updateNetworks(sortedNetworks)
                        layoutWifiListContainer.visibility = View.VISIBLE
                        tvStatusOutput.text = "Found ${sortedNetworks.size} Wi-Fi network(s). Select a network to connect."
                    } else {
                        tvStatusOutput.text = "No Wi-Fi networks found."
                        layoutWifiListContainer.visibility = View.GONE
                    }
                } else {
                    tvStatusOutput.text = "Wi-Fi scan failed: ${response.body()?.message ?: "Unknown error"}"
                    layoutWifiListContainer.visibility = View.GONE
                }
            } catch (e: Exception) {
                showLoading(false)
                tvStatusOutput.text = "Scan Error: ${e.localizedMessage}"
                layoutWifiListContainer.visibility = View.GONE
            }
        }
    }

    private fun onWifiNetworkClicked(network: WifiNetwork) {
        // Wi-Fi network list MUST be closed (hidden) BEFORE showing the password popup!
        layoutWifiListContainer.visibility = View.GONE
        showWifiPasswordDialog(network.ssid)
    }

    private fun showWifiPasswordDialog(ssid: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_wifi_password, null)
        val tvSelectedSsid = dialogView.findViewById<TextView>(R.id.tvSelectedSsid)
        val etPassword = dialogView.findViewById<EditText>(R.id.etWifiPassword)

        tvSelectedSsid.text = "Connect to: $ssid"

        val dialog = AlertDialog.Builder(this, R.style.GlassDialogTheme)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<Button>(R.id.btnCancelWifiConnect).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnConfirmWifiConnect).setOnClickListener {
            val password = etPassword.text.toString().trim()
            dialog.dismiss()
            connectToWifi(ssid, password)
        }

        dialog.show()
    }

    private fun connectToWifi(ssid: String, password: String) {
        showLoading(isLoading = true)
        tvStatusOutput.text = "Connecting ESP32 to '$ssid'..."
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getService().connectWifi(WifiConnectRequest(ssid, password))
                showLoading(false)
                if (response.isSuccessful && response.body()?.success == true) {
                    val msg = "Connected successfully to '$ssid'!\n${response.body()?.message ?: ""}"
                    tvStatusOutput.text = msg
                    Toast.makeText(this@ConnectivityActivity, msg, Toast.LENGTH_LONG).show()
                } else {
                    tvStatusOutput.text = "Failed to connect to '$ssid': ${response.body()?.message ?: "Check password"}"
                }
            } catch (e: Exception) {
                showLoading(false)
                tvStatusOutput.text = "Connection Error: ${e.localizedMessage}"
            }
        }
    }

    private fun checkWifiStatus() {
        showLoading(isLoading = true)
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getService().getWifiStatus()
                showLoading(false)
                if (response.isSuccessful && response.body() != null) {
                    val status = response.body()!!
                    if (status.connected) {
                        tvStatusOutput.text = "Wi-Fi Status: Connected\nSSID: ${status.ssid ?: "N/A"}\nIP: ${status.ip ?: "N/A"}"
                    } else {
                        tvStatusOutput.text = "Wi-Fi Status: Disconnected / Access Point Mode"
                    }
                } else {
                    tvStatusOutput.text = "Unable to fetch Wi-Fi status"
                }
            } catch (e: Exception) {
                showLoading(false)
                tvStatusOutput.text = "Status Error: ${e.localizedMessage}"
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}
