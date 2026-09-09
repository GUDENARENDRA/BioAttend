package com.mpc.bioattend.network

import com.mpc.bioattend.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("api/health")
    suspend fun getHealth(): Response<ApiResponse<Map<String, Any>>>

    @GET("api/device/status")
    suspend fun getDeviceStatus(): Response<DeviceStatusResponse>

    @GET("api/wifi/status")
    suspend fun getWifiStatus(): Response<WifiStatusResponse>

    @GET("api/wifi/scan")
    suspend fun scanWifi(): Response<WifiScanResponse>

    @POST("api/wifi/connect")
    suspend fun connectWifi(@Body request: WifiConnectRequest): Response<ApiResponse<Map<String, Any>>>

    @POST("api/oled/show")
    suspend fun showOnOled(@Body request: OledShowRequest): Response<ApiResponse<Map<String, Any>>>

    @GET("api/fingerprint/status")
    suspend fun getFingerprintStatus(): Response<FingerprintResponse>

    @GET("api/fingerprint/count")
    suspend fun getFingerprintCount(): Response<FingerprintResponse>

    @GET("api/fingerprint/list")
    suspend fun getFingerprintList(): Response<FingerprintListResponse>

    @POST("api/fingerprint/enroll")
    suspend fun enrollFingerprint(): Response<FingerprintResponse>

    @POST("api/fingerprint/enroll")
    suspend fun enrollFingerprintWithId(@Body request: EnrollRequest): Response<FingerprintResponse>

    @GET("api/fingerprint/enroll/status")
    suspend fun getEnrollStatus(): Response<FingerprintResponse>

    @POST("api/fingerprint/search")
    suspend fun searchFingerprint(): Response<FingerprintResponse>

    @GET("api/fingerprint/search/status")
    suspend fun getSearchStatus(): Response<FingerprintResponse>

    @DELETE("api/fingerprint/{id}")
    suspend fun deleteFingerprint(@Path("id") id: Int): Response<FingerprintResponse>

    @POST("api/fingerprint/delete/{id}")
    suspend fun deleteFingerprintPost(@Path("id") id: Int): Response<FingerprintResponse>

    @DELETE("api/fingerprint/all")
    suspend fun deleteAllFingerprints(): Response<FingerprintResponse>

    @POST("api/fingerprint/all")
    suspend fun deleteAllFingerprintsPost(): Response<FingerprintResponse>
}
