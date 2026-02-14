package com.sentinelx.com.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sentinelx.com.R
import com.sentinelx.com.service.SentinelCallService

class IncomingCallActivity : AppCompatActivity() {
    companion object {
        private const val BOT_NUMBER = "+911204413375"
    }
    private lateinit var rootLayout: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvNumber: TextView
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        wakeUpScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        // Init Views
        rootLayout = findViewById(R.id.rootLayout)
        tvNumber = findViewById(R.id.tvNumber)
        tvStatus = findViewById(R.id.tvStatus)
        val ivIcon = findViewById<ImageView>(R.id.ivIcon)

        // Buttons
        val btnAnswer = findViewById<Button>(R.id.btnAnswer)
        val btnReject = findViewById<Button>(R.id.btnReject)
        val btnTrap = findViewById<Button>(R.id.btnTrap)

        // 1. GET DATA
        val number = intent.getStringExtra("PHONE_NUMBER") ?: "Unknown"
        val action = intent.getStringExtra("ACTION") ?: "WARN"
        val message = intent.getStringExtra("MESSAGE") ?: "Incoming Call"

        tvNumber.text = number
        tvStatus.text = message

        // 2. SET UI COLOR
        when (action.uppercase()) {
            "BLOCK" -> {
                rootLayout.setBackgroundColor(Color.parseColor("#B71C1C")) // RED
                ivIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                btnTrap.visibility = View.VISIBLE
            }
            "ALLOW" -> {
                rootLayout.setBackgroundColor(Color.parseColor("#2E7D32")) // GREEN
                ivIcon.setImageResource(android.R.drawable.sym_action_call)
                btnTrap.visibility = View.GONE
            }
            else -> {
                rootLayout.setBackgroundColor(Color.parseColor("#FBC02D")) // YELLOW
                ivIcon.setImageResource(android.R.drawable.stat_sys_warning)
                btnTrap.visibility = View.VISIBLE
            }
        }

        // 3. LISTENERS
        btnAnswer.setOnClickListener {
            stopRinging()
            SentinelCallService.currentCall?.answer(0)

            // Launch Call Activity explicitly
            val intent = Intent(this, CallActivity::class.java)
            intent.putExtra("PHONE_NUMBER", number)
            startActivity(intent)

            finish()
        }

        btnReject.setOnClickListener {
            stopRinging()
            SentinelCallService.currentCall?.reject(false, null)
            finishAndRemoveTask()
        }

        btnTrap.setOnClickListener {
            stopRinging()
            val service = SentinelCallService.instance
            val currentCall = SentinelCallService.currentCall

            if (service != null && currentCall != null) {
                Toast.makeText(this, "🛡️ Activating AI Shield...", Toast.LENGTH_SHORT).show()
                service.activateTrapAndBridge(currentCall, BOT_NUMBER)
                finishAndRemoveTask()
            } else {
                Toast.makeText(this, "Error: Call not active", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // 4. [FIX] LISTEN FOR CALL DISCONNECT (Caller cuts the call)
        SentinelCallService.callStatusCallback = { state ->
            if (state == Call.STATE_DISCONNECTED) {
                runOnUiThread {
                    stopRinging()
                    Toast.makeText(this, "Call Ended", Toast.LENGTH_SHORT).show()
                    finishAndRemoveTask()
                }
            }
        }

        // 5. START RINGING (Only start now, after UI is ready)
        startRinging()
    }

    private fun startRinging() {
        try {
            // [FIX] Double Ringtone Check: Ensure we don't play if already playing
            if (ringtone?.isPlaying == true) return

            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            ringtone?.audioAttributes = audioAttributes
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRinging() {
        try {
            if (ringtone != null && ringtone!!.isPlaying) {
                ringtone?.stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRinging()
    }

    override fun onPause() {
        super.onPause()
        // Safety: If app goes background, ensure we don't leave zombie ringtones
        if (isFinishing) stopRinging()
    }

    private fun wakeUpScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}