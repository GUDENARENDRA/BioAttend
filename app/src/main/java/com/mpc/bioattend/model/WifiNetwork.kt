package com.mpc.bioattend.model

import com.google.gson.annotations.SerializedName

data class WifiNetwork(
    @SerializedName("ssid") val ssid: String,
    @SerializedName("rssi") val rssi: Int = -50,
    @SerializedName("secure") val secure: Boolean = true
)

data class WifiScanResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("networks") val networks: List<WifiNetwork> = emptyList(),
    @SerializedName("message") val message: String? = null
)

data class WifiConnectRequest(
    @SerializedName("ssid") val ssid: String,
    @SerializedName("password") val password: String
)

data class WifiStatusResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("connected") val connected: Boolean,
    @SerializedName("ssid") val ssid: String? = null,
    @SerializedName("ip") val ip: String? = null,
    @SerializedName("message") val message: String? = null
)
