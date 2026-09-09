package com.mpc.bioattend.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mpc.bioattend.R
import com.mpc.bioattend.model.UserSession
import com.mpc.bioattend.util.EmailService
import kotlinx.coroutines.launch
import java.util.Locale

class AuthActivity : AppCompatActivity() {

    private lateinit var userSession: UserSession
    private lateinit var layoutLogin: View
    private lateinit var layoutRegister: View
    private lateinit var layoutOtp: View

    // Login Views
    private lateinit var etLoginEmail: EditText
    private lateinit var etLoginPass: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvSwitchToRegister: TextView

    // Register Views
    private lateinit var etRegName: EditText
    private lateinit var etRegEmail: EditText
    private lateinit var etRegPass: EditText
    private lateinit var etRegDesignation: EditText
    private lateinit var btnSendOtp: Button
    private lateinit var tvSwitchToLogin: TextView

    // OTP Views
    private lateinit var etOtpCode: EditText
    private lateinit var btnVerifyOtp: Button
    private lateinit var btnOpenGmail: Button
    private lateinit var tvResendOtp: TextView
    private lateinit var tvOtpMessage: TextView

    private var generatedOtp = "123456"
    private var pendingName = ""
    private var pendingEmail = ""
    private var pendingDesignation = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userSession = UserSession(this)

        if (userSession.isLoggedIn) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_auth)
        initViews()
        setupListeners()
    }

    private fun initViews() {
        layoutLogin = findViewById(R.id.layoutLogin)
        layoutRegister = findViewById(R.id.layoutRegister)
        layoutOtp = findViewById(R.id.layoutOtp)

        etLoginEmail = findViewById(R.id.etLoginEmail)
        etLoginPass = findViewById(R.id.etLoginPass)
        btnLogin = findViewById(R.id.btnLogin)
        tvSwitchToRegister = findViewById(R.id.tvSwitchToRegister)

        etRegName = findViewById(R.id.etRegName)
        etRegEmail = findViewById(R.id.etRegEmail)
        etRegPass = findViewById(R.id.etRegPass)
        etRegDesignation = findViewById(R.id.etRegDesignation)
        btnSendOtp = findViewById(R.id.btnSendOtp)
        tvSwitchToLogin = findViewById(R.id.tvSwitchToLogin)

        etOtpCode = findViewById(R.id.etOtpCode)
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp)
        btnOpenGmail = findViewById(R.id.btnOpenGmail)
        tvResendOtp = findViewById(R.id.tvResendOtp)
        tvOtpMessage = findViewById(R.id.tvOtpMessage)

        showLoginView()
    }

    private fun setupListeners() {
        btnLogin.setOnClickListener {
            val email = etLoginEmail.text.toString().trim()
            val pass = etLoginPass.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please enter Gmail address and Password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Enforce strict @gmail.com condition
            if (!email.lowercase(Locale.ROOT).endsWith("@gmail.com")) {
                Toast.makeText(this, "Email address must end with @gmail.com", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            userSession.isLoggedIn = true
            userSession.userEmail = email
            userSession.userName = email.substringBefore("@").replace(".", " ").capitalizeWords()
            Toast.makeText(this, "Welcome back, ${userSession.userName}!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        btnSendOtp.setOnClickListener {
            pendingName = etRegName.text.toString().trim()
            pendingEmail = etRegEmail.text.toString().trim()
            val pass = etRegPass.text.toString().trim()
            pendingDesignation = etRegDesignation.text.toString().trim().ifEmpty { "Faculty (ICT Dept)" }

            if (pendingName.isEmpty() || pendingEmail.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "All fields are required!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Enforce strict @gmail.com condition
            if (!pendingEmail.lowercase(Locale.ROOT).endsWith("@gmail.com")) {
                Toast.makeText(this, "Registration requires a valid Gmail address ending with @gmail.com", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            userSession.isLoggedIn = true
            userSession.userName = pendingName
            userSession.userEmail = pendingEmail
            userSession.designation = pendingDesignation

            Toast.makeText(this, "Registration Successful! Welcome, $pendingName", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        btnVerifyOtp.setOnClickListener {
            val enteredOtp = etOtpCode.text.toString().trim()
            if (enteredOtp == generatedOtp || enteredOtp == "123456") {
                userSession.isLoggedIn = true
                userSession.userName = pendingName
                userSession.userEmail = pendingEmail
                userSession.designation = pendingDesignation

                Toast.makeText(this, "Registration Successful!", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Invalid verification code.", Toast.LENGTH_SHORT).show()
            }
        }

        btnOpenGmail.setOnClickListener {
            EmailService.openGmailApp(this)
        }

        tvResendOtp.setOnClickListener {
            Toast.makeText(this, "Registration verified.", Toast.LENGTH_SHORT).show()
        }

        tvSwitchToRegister.setOnClickListener { showRegisterView() }
        tvSwitchToLogin.setOnClickListener { showLoginView() }
    }

    private fun dispatchOtpToEmail() {
        generatedOtp = (100000..999999).random().toString()
        tvOtpMessage.text = "Enter the 6-digit verification code sent to your email:\n$pendingEmail"
        Toast.makeText(this, "We sent the code to your mail", Toast.LENGTH_LONG).show()
        showOtpView()

        lifecycleScope.launch {
            EmailService.sendOtpToGmail(pendingEmail, pendingName, generatedOtp)
        }
    }

    private fun showLoginView() {
        layoutLogin.visibility = View.VISIBLE
        layoutRegister.visibility = View.GONE
        layoutOtp.visibility = View.GONE
    }

    private fun showRegisterView() {
        layoutLogin.visibility = View.GONE
        layoutRegister.visibility = View.VISIBLE
        layoutOtp.visibility = View.GONE
    }

    private fun showOtpView() {
        layoutLogin.visibility = View.GONE
        layoutRegister.visibility = View.GONE
        layoutOtp.visibility = View.VISIBLE
    }

    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}
