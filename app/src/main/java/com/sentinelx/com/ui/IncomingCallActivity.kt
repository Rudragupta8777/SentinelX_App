package com.sentinelx.com.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.Call
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sentinelx.com.R
import com.sentinelx.com.service.SentinelCallService

class IncomingCallActivity : AppCompatActivity() {

    // Vomyra Number (Replace with your actual number)
    private val VOMYRA_BOT_NUMBER = "+919876543210"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        // 1. Get Data from Intent
        val phoneNumber = intent.getStringExtra("PHONE_NUMBER") ?: "Unknown"

        // 2. Setup UI
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvMainDisplay = findViewById<TextView>(R.id.tvNumber) // The big text
        val btnTrap = findViewById<Button>(R.id.btnTrap)
        val btnReject = findViewById<Button>(R.id.btnReject)

        // 3. RESOLVE CONTACT NAME
        val contactName = getContactName(phoneNumber)

        if (contactName != null) {
            // CASE: SAVED CONTACT (Mom, Dad, etc.)
            tvMainDisplay.text = contactName
            tvStatus.text = "Incoming Call • $phoneNumber"

            // Optional: Change color to Green/Blue if it's a known contact?
            // For now, we keep the Sentinel Red theme, but you could change background here.
        } else {
            // CASE: UNKNOWN NUMBER
            tvMainDisplay.text = phoneNumber
            tvStatus.text = "Unknown Caller • Potential Risk"
        }

        // 4. "Answer with AI" Button
        btnTrap.setOnClickListener {
            val call = SentinelCallService.currentCall
            val service = SentinelCallService.instance

            if (call != null && service != null) {
                Toast.makeText(this, "Activating AI Trap...", Toast.LENGTH_SHORT).show()
                service.activateTrapAndBridge(call, VOMYRA_BOT_NUMBER)
                finish()
            } else {
                Toast.makeText(this, "Call ended before trap could activate", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // 5. Reject Button
        btnReject.setOnClickListener {
            SentinelCallService.currentCall?.reject(false, null)
            finish()
        }
    }

    // --- HELPER FUNCTION: LOOKUP NAME ---
    private fun getContactName(phoneNumber: String): String? {
        // 1. Check Permission first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        // 2. Define the query
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        // 3. Run Query
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