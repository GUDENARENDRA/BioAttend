package com.mpc.bioattend.model

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("data") val data: T? = null
)
