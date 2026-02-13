package com.sentinelx.com.ui

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.View
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sentinelx.com.R
import com.sentinelx.com.service.SentinelCallService

class CallActivity : AppCompatActivity() {

    private lateinit var chronometer: Chronometer
    private lateinit var tvStatus: TextView
    private var isMuted = false
    private var isSpeakerOn = false
    private var isHold = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. WAKE UP SCREEN (Fixes issue where default dialer shows instead of this app)
        wakeUpScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        // 2. Setup UI Elements
        val tvName = findViewById<TextView>(R.id.tvCallName)
        tvStatus = findViewById<TextView>(R.id.tvCallStatus)
        chronometer = findViewById<Chronometer>(R.id.chronometer)

        val btnMute = findViewById<ImageView>(R.id.btnMute)
        val btnSpeaker = findViewById<ImageView>(R.id.btnSpeaker)
        val btnHold = findViewById<ImageView>(R.id.btnHold)

        // FIX: Use 'View' instead of 'FloatingActionButton' to prevent Crash
        val btnEndCall = findViewById<View>(R.id.btnEndCall)

        // 3. Get Call Info
        val number = intent.getStringExtra("PHONE_NUMBER") ?: "Unknown"
        tvName.text = number

        // 4. Listen for Call Updates
        SentinelCallService.callStatusCallback = { state ->
            runOnUiThread { updateStatus(state) }
        }

        // 5. Button Actions
        btnEndCall.setOnClickListener {
            SentinelCallService.currentCall?.disconnect()
            finish()
        }

        btnMute.setOnClickListener {
            isMuted = !isMuted
            btnMute.alpha = if (isMuted) 0.5f else 1.0f
            Toast.makeText(this, if (isMuted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
            // In a real app, call service.setMuted(isMuted)
        }

        btnSpeaker.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            val service = SentinelCallService.instance
            if (isSpeakerOn) {
                service?.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            } else {
                service?.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
            }
            btnSpeaker.alpha = if (isSpeakerOn) 0.5f else 1.0f
        }

        btnHold.setOnClickListener {
            val call = SentinelCallService.currentCall
            if (call != null) {
                isHold = !isHold
                if (isHold) call.hold() else call.unhold()
                btnHold.alpha = if (isHold) 0.5f else 1.0f
                tvStatus.text = if (isHold) "On Hold" else "Active"
            }
        }

        // Initial Check
        updateStatus(SentinelCallService.currentCall?.state ?: Call.STATE_NEW)
    }

    private fun updateStatus(state: Int) {
        when (state) {
            Call.STATE_ACTIVE -> {
                tvStatus.text = "Active Call"
                if (chronometer.visibility != View.VISIBLE) {
                    chronometer.visibility = View.VISIBLE
                    chronometer.base = SystemClock.elapsedRealtime()
                    chronometer.start()
                }
            }
            Call.STATE_DIALING -> tvStatus.text = "Dialing..."
            Call.STATE_CONNECTING -> tvStatus.text = "Connecting..."
            Call.STATE_DISCONNECTED -> {
                tvStatus.text = "Ended"
                chronometer.stop()
                finish()
            }
            Call.STATE_HOLDING -> tvStatus.text = "On Hold"
        }
    }

    // --- SYSTEM: Force Screen to Show ---
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

    override fun onDestroy() {
        super.onDestroy()
        SentinelCallService.callStatusCallback = null
    }
}