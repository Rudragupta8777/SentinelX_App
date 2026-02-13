package com.sentinelx.com.ui

import android.os.Bundle
import android.telecom.Call
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sentinelx.com.R
import com.sentinelx.com.service.SentinelCallService

class IncomingCallActivity : AppCompatActivity() {

    // Vomyra Number (Replace with your actual number)
    private val VOMYRA_BOT_NUMBER = "+919876543210"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        // 1. Get Data
        val phoneNumber = intent.getStringExtra("PHONE_NUMBER")

        // 2. Setup UI (Simulate API Risk Check here for now)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnTrap = findViewById<Button>(R.id.btnTrap)
        val btnReject = findViewById<Button>(R.id.btnReject)

        tvStatus.text = "WARNING: HIGH RISK CALL DETECTED\n$phoneNumber"

        // 3. "Answer with AI" Button
        btnTrap.setOnClickListener {
            val call = SentinelCallService.currentCall
            val service = SentinelCallService.instance

            if (call != null && service != null) {
                Toast.makeText(this, "Activating AI Trap...", Toast.LENGTH_SHORT).show()

                // TRIGGER THE TRAP
                service.activateTrapAndBridge(call, VOMYRA_BOT_NUMBER)

                // Close UI (The call continues in background)
                finish()
            } else {
                Toast.makeText(this, "Call ended before trap could activate", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // 4. Reject Button
        btnReject.setOnClickListener {
            SentinelCallService.currentCall?.reject(false, null)
            finish()
        }
    }
}