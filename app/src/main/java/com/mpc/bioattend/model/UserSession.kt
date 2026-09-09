package com.mpc.bioattend.model

import android.content.Context
import android.content.SharedPreferences

class UserSession(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("bioattend_session", Context.MODE_PRIVATE)

    var isLoggedIn: Boolean
        get() = prefs.getBoolean("is_logged_in", false)
        set(value) = prefs.edit().putBoolean("is_logged_in", value).apply()

    var userName: String
        get() = prefs.getString("user_name", "User") ?: "User"
        set(value) = prefs.edit().putString("user_name", value).apply()

    var userEmail: String
        get() = prefs.getString("user_email", "user@email.com") ?: "user@email.com"
        set(value) = prefs.edit().putString("user_email", value).apply()

    var designation: String
        get() = prefs.getString("designation", "Faculty (ICT Dept)") ?: "Faculty (ICT Dept)"
        set(value) = prefs.edit().putString("designation", value).apply()

    var esp32Ip: String
        get() = prefs.getString("esp32_ip", "192.168.4.1") ?: "192.168.4.1"
        set(value) = prefs.edit().putString("esp32_ip", value).apply()

    val userInitials: String
        get() {
            val name = userName.trim()
            if (name.isEmpty()) return "NA"
            val parts = name.split(" ").filter { it.isNotEmpty() }
            return if (parts.size >= 2) {
                "${parts[0][0]}${parts[1][0]}".uppercase()
            } else if (parts.size == 1 && parts[0].length >= 2) {
                parts[0].substring(0, 2).uppercase()
            } else {
                parts[0][0].uppercase()
            }
        }
}
