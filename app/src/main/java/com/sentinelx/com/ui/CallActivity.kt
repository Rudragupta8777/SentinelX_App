package com.sentinelx.com.ui

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.telecom.Call
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Chronometer
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sentinelx.com.MainActivity
import com.sentinelx.com.R
import com.sentinelx.com.service.SentinelCallService
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallActivity : AppCompatActivity() {

    private lateinit var rootLayout: ConstraintLayout
    private lateinit var chronometer: Chronometer
    private lateinit var tvStatus: TextView
    private lateinit var tvName: TextView
    private lateinit var tvPhoneNumber: TextView

    // State Flags
    private var isMuted = false
    private var isSpeakerOn = false
    private var isHold = false
    private var isRecording = false
    private var isTimerRunning = false

    // Recorder
    private var mediaRecorder: MediaRecorder? = null
    private var recordFile: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        wakeUpScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        // 1. Init Views
        rootLayout = findViewById(R.id.rootLayout) // Add ID to root layout in XML if missing
        tvName = findViewById(R.id.tvCallName)
        tvPhoneNumber = findViewById(R.id.tvPhoneNumber)
        tvStatus = findViewById(R.id.tvCallStatus)
        chronometer = findViewById(R.id.chronometer)

        val btnMute = findViewById<ImageView>(R.id.btnMute)
        val btnKeypad = findViewById<ImageView>(R.id.btnKeypad)
        val btnSpeaker = findViewById<ImageView>(R.id.btnSpeaker)
        val btnRecord = findViewById<ImageView>(R.id.btnRecord)
        val btnHold = findViewById<ImageView>(R.id.btnHold)
        val btnAddCall = findViewById<ImageView>(R.id.btnAddCall)
        val btnEndCall = findViewById<View>(R.id.btnEndCall)

        // 2. Load Data
        val number = intent.getStringExtra("PHONE_NUMBER") ?: "Unknown"
        val action = intent.getStringExtra("ACTION") ?: "WARN"

        setupContactDisplay(number)

        // [FIX] Apply Background Color based on Risk
        applyTheme(action)

        // 3. Status Listener
        SentinelCallService.callStatusCallback = { state ->
            runOnUiThread { updateStatus(state) }
        }

        // Initial Status Check
        val initialState = SentinelCallService.currentCall?.state ?: Call.STATE_DIALING
        updateStatus(initialState)

        // 4. Listeners (Same as before)
        btnEndCall.setOnClickListener {
            stopRecording()
            SentinelCallService.currentCall?.disconnect()
            finish()
        }

        // ... (Keep other listeners for Mute, Speaker, etc. unchanged) ...
        btnMute.setOnClickListener {
            isMuted = !isMuted
            SentinelCallService.instance?.toggleMute(isMuted)
            updateButtonState(btnMute, isMuted)
        }
        btnSpeaker.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            SentinelCallService.instance?.toggleSpeaker(isSpeakerOn)
            updateButtonState(btnSpeaker, isSpeakerOn)
        }
        btnHold.setOnClickListener {
            val call = SentinelCallService.currentCall
            if (call != null) {
                isHold = !isHold
                if (isHold) call.hold() else call.unhold()
                updateButtonState(btnHold, isHold)
                tvStatus.text = if (isHold) "On Hold" else ""

                // Pause/Resume Chronometer logic if needed, or just hide
                if(isHold) {
                    chronometer.visibility = View.INVISIBLE
                } else {
                    chronometer.visibility = View.VISIBLE
                }
            }
        }
        btnKeypad.setOnClickListener { showKeypadDialog() }
        btnRecord.setOnClickListener {
            // ... (Keep existing record logic) ...
            if (isRecording) {
                stopRecording()
                Toast.makeText(this, "Recording Saved", Toast.LENGTH_SHORT).show()
                updateButtonState(btnRecord, false)
            } else {
                if (checkPermissions()) {
                    startRecording()
                    Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show()
                    updateButtonState(btnRecord, true)
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
                }
            }
        }
        btnAddCall.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
    }

    private fun applyTheme(action: String) {
        when (action.uppercase()) {
            "BLOCK" -> rootLayout.setBackgroundColor(Color.parseColor("#B86E64")) // Red
            "ALLOW" -> rootLayout.setBackgroundColor(Color.parseColor("#74B685")) // Green
            else -> rootLayout.setBackgroundColor(Color.parseColor("#E2D58B"))    // Yellow
        }
    }

    private fun updateStatus(state: Int) {
        when (state) {
            Call.STATE_NEW, Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                tvStatus.text = "Calling..."
                tvStatus.visibility = View.VISIBLE
                chronometer.visibility = View.GONE
            }
            Call.STATE_ACTIVE -> {
                tvStatus.visibility = View.GONE
                chronometer.visibility = View.VISIBLE

                if (!isTimerRunning) {
                    chronometer.base = SystemClock.elapsedRealtime()
                    chronometer.start()
                    isTimerRunning = true
                }
            }
            Call.STATE_DISCONNECTED -> {
                tvStatus.text = "Call Ended"
                tvStatus.visibility = View.VISIBLE
                chronometer.stop()
                stopRecording()
                finish()
            }
            Call.STATE_HOLDING -> {
                tvStatus.text = "On Hold"
                tvStatus.visibility = View.VISIBLE
            }
        }
    }

    // ... (Keep setupContactDisplay, getContactName, updateButtonState, startRecording, stopRecording, checkPermissions, showKeypadDialog, wakeUpScreen logic exactly same) ...

    private fun setupContactDisplay(number: String) {
        val name = getContactName(number)
        if (name != null) {
            tvName.text = name
            tvPhoneNumber.text = "Mobile $number"
            tvPhoneNumber.visibility = View.VISIBLE
        } else {
            tvName.text = number
            tvPhoneNumber.visibility = View.GONE
        }
    }

    private fun getContactName(phoneNumber: String): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val cursor = contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
        cursor?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun updateButtonState(view: ImageView, isActive: Boolean) {
        if (isActive) {
            view.background.setTint(Color.WHITE)
            view.imageTintList = ColorStateList.valueOf(Color.BLACK)
        } else {
            view.setBackgroundResource(R.drawable.bg_circle_action_btn)
            view.imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
    }

    private fun startRecording() {
        try {
            val fileName = "Call_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".3gp"
            val file = File(getExternalFilesDir(null), fileName)
            recordFile = file.absolutePath
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(recordFile)
                prepare()
                start()
            }
            isRecording = true
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Recording Failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun showKeypadDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(R.layout.dialog_keypad)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundResource(android.R.color.transparent)
        val tvDisplay = dialog.findViewById<TextView>(R.id.tvKeypadInput)
        val grid = dialog.findViewById<GridLayout>(R.id.keypadGrid)
        if (grid != null) {
            for (i in 0 until grid.childCount) {
                val child = grid.getChildAt(i)
                if (child is Button) {
                    child.setOnClickListener {
                        val text = child.text.toString()
                        val char = text[0]
                        SentinelCallService.instance?.playDtmfTone(char)
                        tvDisplay?.append(text)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun wakeUpScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }
}