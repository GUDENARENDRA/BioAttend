package com.mpc.bioattend.model

import com.google.gson.annotations.SerializedName

data class DeviceStatusResponse(
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("data") val data: DeviceData? = null
)

data class DeviceData(
    @SerializedName("sensor_ready") val sensorReady: Boolean = true,
    @SerializedName("oled_ready") val oledReady: Boolean = true,
    @SerializedName("wifi_mode") val wifiMode: String? = "AP",
    @SerializedName("wifi_ssid") val wifiSsid: String? = "BioAttend-AP",
    @SerializedName("ip") val ip: String? = "192.168.4.1",
    @SerializedName("busy") val busy: Boolean = false,
    @SerializedName("uptime_ms") val uptimeMs: Long = 0
)
