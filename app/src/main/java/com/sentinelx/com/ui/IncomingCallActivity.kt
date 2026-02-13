package com.sentinelx.com.ui

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.VideoProfile
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sentinelx.com.R
import com.sentinelx.com.service.SentinelCallService

class IncomingCallActivity : AppCompatActivity() {

    // Replace this with your actual Vomyra/AI Bot number
    private val VOMYRA_BOT_NUMBER = "+919876543210"

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Force screen to wake up (Critical for incoming calls)
        wakeUpScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        // 2. Retrieve Data from Service
        val phoneNumber = intent.getStringExtra("PHONE_NUMBER") ?: "Unknown"
        val isContact = intent.getBooleanExtra("IS_CONTACT", false)

        // 3. Bind UI Elements
        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvNumber = findViewById<TextView>(R.id.tvNumber)

        val btnTrap = findViewById<Button>(R.id.btnTrap)
        val btnReject = findViewById<Button>(R.id.btnReject)
        val btnAnswer = findViewById<Button>(R.id.btnAnswer)

        // 4. Try to resolve the Contact Name
        val contactName = getContactName(phoneNumber)

        // 5. Dynamic UI Logic (Red vs Black)
        if (isContact) {
            // --- SCENARIO A: SAVED CONTACT (Safe) ---
            // Set Dark Grey/Black Background
            rootLayout.setBackgroundColor(0xFF121212.toInt())

            tvTitle.text = "INCOMING CALL"
            tvTitle.setTextColor(0xFFFFFFFF.toInt()) // White

            tvStatus.text = "Mobile • $phoneNumber"
            tvStatus.setTextColor(0xFFB0BEC5.toInt()) // Light Grey

            // Show Name if available, otherwise Number
            tvNumber.text = contactName ?: phoneNumber

            // Button Visibility: Show "Answer", Hide "Trap"
            btnTrap.visibility = View.GONE
            btnAnswer.visibility = View.VISIBLE

        } else {
            // --- SCENARIO B: UNKNOWN / POTENTIAL SCAM (Risk) ---
            // Set Red Alert Background
            rootLayout.setBackgroundColor(0xFFB71C1C.toInt())

            tvTitle.text = "SENTINEL DETECTED RISK"
            tvTitle.setTextColor(0xFFFFFFFF.toInt())

            tvStatus.text = "High Probability of Fraud"
            tvStatus.setTextColor(0xFFFFEBEE.toInt()) // Light Red/White

            tvNumber.text = phoneNumber

            // Button Visibility: Show "Trap", Hide "Answer"
            btnTrap.visibility = View.VISIBLE
            btnAnswer.visibility = View.GONE
        }

        // 6. Button Listeners

        // OPTION 1: TRAP (For Scammers)
        btnTrap.setOnClickListener {
            answerAndTrap()
        }

        // OPTION 2: STANDARD ANSWER (For Contacts)
        btnAnswer.setOnClickListener {
            val call = SentinelCallService.currentCall
            if (call != null) {
                // Answer the call normally
                call.answer(VideoProfile.STATE_AUDIO_ONLY)

                // We finish this "Ringing" screen.
                // The Service will detect the call is now ACTIVE and launch CallActivity.
                finish()
            } else {
                finish() // Call already ended
            }
        }

        // OPTION 3: REJECT (Universal)
        btnReject.setOnClickListener {
            val call = SentinelCallService.currentCall
            call?.reject(false, null)
            finish()
        }
    }

    // --- LOGIC: Activate the AI Trap ---
    private fun answerAndTrap() {
        val call = SentinelCallService.currentCall
        val service = SentinelCallService.instance

        if (call != null && service != null) {
            Toast.makeText(this, "Activating AI Trap...", Toast.LENGTH_SHORT).show()
            // Call the special function in Service
            service.activateTrapAndBridge(call, VOMYRA_BOT_NUMBER)
            finish() // Close UI; Service handles the bridging in background
        } else {
            Toast.makeText(this, "Call unavailable", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // --- SYSTEM: Wake Up Lock Screen ---
    private fun wakeUpScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // --- HELPER: Get Contact Name ---
    private fun getContactName(phoneNumber: String): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        var name: String? = null
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                name = it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
            }
        }
        return name
    }
}