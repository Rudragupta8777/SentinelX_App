package com.sentinelx.com.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.sentinelx.com.R
import com.sentinelx.com.service.SentinelCallService

class IncomingCallActivity : AppCompatActivity() {
    companion object {
        private const val BOT_NUMBER = "+911204413375"
    }

    private lateinit var rootLayout: ConstraintLayout
    private lateinit var tvNumber: TextView
    private lateinit var layoutRisk: LinearLayout
    private lateinit var tvRiskStatus: TextView
    private lateinit var layoutCenterAction: LinearLayout
    private lateinit var ivCenterAction: ImageView
    private lateinit var tvCenterAction: TextView

    private var ringtone: Ringtone? = null
    private var isSafeCall = false // To track if we should Message or Trap

    override fun onCreate(savedInstanceState: Bundle?) {
        wakeUpScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        // 1. Init Views (Matches new XML)
        rootLayout = findViewById(R.id.rootLayout)
        tvNumber = findViewById(R.id.tvNumber)
        layoutRisk = findViewById(R.id.layoutRisk)
        tvRiskStatus = findViewById(R.id.tvRiskStatus)

        layoutCenterAction = findViewById(R.id.layoutCenterAction)
        ivCenterAction = findViewById(R.id.ivCenterAction)
        tvCenterAction = findViewById(R.id.tvCenterAction)

        // Note: XML uses ImageButtons now
        val btnAnswer = findViewById<ImageButton>(R.id.btnAnswer)
        val btnReject = findViewById<ImageButton>(R.id.btnReject)

        // 2. GET DATA
        val number = intent.getStringExtra("PHONE_NUMBER") ?: "Unknown"
        val action = intent.getStringExtra("ACTION") ?: "WARN"

        tvNumber.text = number

        // 3. APPLY UI THEME (Green/Red/Yellow)
        applyTheme(action)

        // 4. LISTENERS

        // --- ANSWER (Green Button) ---
        btnAnswer.setOnClickListener {
            stopRinging()
            SentinelCallService.currentCall?.answer(0)

            // Launch Call Activity explicitly (Your existing logic)
            val intent = Intent(this, CallActivity::class.java)
            intent.putExtra("PHONE_NUMBER", number)
            startActivity(intent)

            finish()
        }

        // --- REJECT (Red Button) ---
        btnReject.setOnClickListener {
            stopRinging()
            SentinelCallService.currentCall?.reject(false, null)
            finishAndRemoveTask()
        }

        // --- CENTER ACTION (AI Trap OR Message) ---
        layoutCenterAction.setOnClickListener {
            handleCenterAction(number)
        }

        // --- CALL DISCONNECT LISTENER (Your existing logic) ---
        SentinelCallService.callStatusCallback = { state ->
            if (state == Call.STATE_DISCONNECTED) {
                runOnUiThread {
                    stopRinging()
                    Toast.makeText(this, "Call Ended", Toast.LENGTH_SHORT).show()
                    finishAndRemoveTask()
                }
            }
        }

        // 5. START RINGING
        startRinging()
    }

    /**
     * Applies colors and icons based on Risk Level
     */
    private fun applyTheme(action: String) {
        when (action.uppercase()) {
            "BLOCK" -> {
                // RED THEME (High Risk)
                isSafeCall = false
                rootLayout.setBackgroundColor(Color.parseColor("#B86E64")) // Muted Red
                layoutRisk.visibility = View.VISIBLE
                tvRiskStatus.text = "High Risk"

                ivCenterAction.setImageResource(R.drawable.ic_ai)
                tvCenterAction.text = "Answer with AI"
            }
            "ALLOW" -> {
                // GREEN THEME (Safe)
                isSafeCall = true
                rootLayout.setBackgroundColor(Color.parseColor("#74B685")) // Muted Green
                layoutRisk.visibility = View.GONE

                // Show Message Icon
                ivCenterAction.setImageResource(R.drawable.ic_message)
                tvCenterAction.text = "Message"
            }
            else -> {
                // YELLOW THEME (Medium Risk - Default)
                isSafeCall = false
                rootLayout.setBackgroundColor(Color.parseColor("#E2D58B")) // Muted Yellow
                layoutRisk.visibility = View.VISIBLE
                tvRiskStatus.text = "Medium Risk"

                ivCenterAction.setImageResource(R.drawable.ic_ai)
                tvCenterAction.text = "Answer with AI"
            }
        }
    }

    private fun handleCenterAction(number: String) {
        if (isSafeCall) {
            // --- ACTION: MESSAGE ---
            stopRinging()

            // Optional: Reject call before opening SMS
            SentinelCallService.currentCall?.reject(false, "Sent Message")

            try {
                val smsIntent = Intent(Intent.ACTION_SENDTO)
                smsIntent.data = Uri.parse("smsto:$number")
                startActivity(smsIntent)
                Toast.makeText(this, "Opening Messages...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Messaging app not found", Toast.LENGTH_SHORT).show()
            }

            finishAndRemoveTask()
        } else {
            // --- ACTION: AI TRAP ---
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
    }


    private fun startRinging() {
        try {
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