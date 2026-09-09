package com.mpc.bioattend.network

import android.content.Context
import com.mpc.bioattend.model.UserSession
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var currentBaseUrl: String = "http://192.168.4.1/"
    private var apiService: ApiService? = null

    fun init(context: Context) {
        val savedIp = UserSession(context).esp32Ip
        setBaseIp(savedIp)
    }

    fun setBaseIp(ip: String, context: Context? = null) {
        val sanitizedIp = ip.trim().removePrefix("http://").removePrefix("https://").removeSuffix("/")
        currentBaseUrl = "http://$sanitizedIp/"
        apiService = null

        context?.let {
            UserSession(it).esp32Ip = sanitizedIp
        }
    }

    fun getBaseUrl(): String = currentBaseUrl

    fun getService(): ApiService {
        if (apiService == null) {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(currentBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiService = retrofit.create(ApiService::class.java)
        }
        return apiService!!
    }
}
