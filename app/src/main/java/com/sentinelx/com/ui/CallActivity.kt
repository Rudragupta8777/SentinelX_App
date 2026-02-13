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

    private lateinit var chronometer: Chronometer
    private lateinit var tvStatus: TextView
    private lateinit var tvName: TextView
    private lateinit var tvPhoneNumber: TextView

    // State Flags
    private var isMuted = false
    private var isSpeakerOn = false
    private var isHold = false
    private var isRecording = false

    // Recorder
    private var mediaRecorder: MediaRecorder? = null
    private var recordFile: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        wakeUpScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        // UI Setup
        tvName = findViewById(R.id.tvCallName)
        tvPhoneNumber = findViewById(R.id.tvPhoneNumber)
        tvStatus = findViewById(R.id.tvCallStatus)
        chronometer = findViewById(R.id.chronometer)

        // Buttons
        val btnMute = findViewById<ImageView>(R.id.btnMute)
        val btnKeypad = findViewById<ImageView>(R.id.btnKeypad)
        val btnSpeaker = findViewById<ImageView>(R.id.btnSpeaker)
        val btnRecord = findViewById<ImageView>(R.id.btnRecord)
        val btnHold = findViewById<ImageView>(R.id.btnHold)
        val btnAddCall = findViewById<ImageView>(R.id.btnAddCall)
        val btnEndCall = findViewById<View>(R.id.btnEndCall)

        // --- GET DATA & SET DISPLAY ---
        val number = intent.getStringExtra("PHONE_NUMBER") ?: "Unknown"
        setupContactDisplay(number)

        // 1. END CALL
        btnEndCall.setOnClickListener {
            stopRecording()
            SentinelCallService.currentCall?.disconnect()
            finish()
        }

        // 2. MUTE
        btnMute.setOnClickListener {
            isMuted = !isMuted
            SentinelCallService.instance?.toggleMute(isMuted)
            updateButtonState(btnMute, isMuted, R.drawable.ic_unmute, R.drawable.ic_mute)
            Toast.makeText(this, if (isMuted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
        }

        // 3. SPEAKER
        btnSpeaker.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            SentinelCallService.instance?.toggleSpeaker(isSpeakerOn)
            updateButtonState(btnSpeaker, isSpeakerOn, R.drawable.ic_unspeaker, R.drawable.ic_speaker)
        }

        // 4. HOLD
        btnHold.setOnClickListener {
            val call = SentinelCallService.currentCall
            if (call != null) {
                isHold = !isHold
                if (isHold) call.hold() else call.unhold()
                updateButtonState(btnHold, isHold, R.drawable.ic_unhold, R.drawable.ic_hold)
                tvStatus.text = if (isHold) "On Hold" else "Active"
            }
        }

        // 5. KEYPAD
        btnKeypad.setOnClickListener {
            showKeypadDialog()
        }

        // 6. RECORD
        btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
                Toast.makeText(this, "Recording Saved", Toast.LENGTH_LONG).show()
                updateButtonState(btnRecord, false, R.drawable.ic_unrecord, R.drawable.ic_record)
            } else {
                if (checkPermissions()) {
                    startRecording()
                    Toast.makeText(this, "Recording Started...", Toast.LENGTH_SHORT).show()
                    updateButtonState(btnRecord, true, R.drawable.ic_unrecord, R.drawable.ic_record)
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
                }
            }
        }

        // 7. ADD CALL
        btnAddCall.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }

        // Status Listener
        SentinelCallService.callStatusCallback = { state ->
            runOnUiThread { updateStatus(state) }
        }
        updateStatus(SentinelCallService.currentCall?.state ?: Call.STATE_NEW)
    }

    // --- LOGIC: SHOW NAME VS NUMBER ---
    private fun setupContactDisplay(number: String) {
        val name = getContactName(number)

        if (name != null) {
            // CASE 1: SAVED CONTACT
            tvName.text = name                // Big Name
            tvPhoneNumber.text = "Mobile $number" // Small Number
            tvPhoneNumber.visibility = View.VISIBLE
        } else {
            // CASE 2: UNKNOWN NUMBER
            tvName.text = number              // Big Number
            tvPhoneNumber.visibility = View.GONE   // Hide small text
        }
    }

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

    // --- HELPER: BUTTON STATE ---
    private fun updateButtonState(view: ImageView, isActive: Boolean, activeIconRes: Int, inactiveIconRes: Int) {
        if (isActive) {
            view.background.setTint(Color.WHITE)
            view.imageTintList = ColorStateList.valueOf(Color.BLACK)
            view.setImageResource(activeIconRes)
        } else {
            view.background.setTint(Color.parseColor("#333333"))
            view.imageTintList = ColorStateList.valueOf(Color.WHITE)
            view.setImageResource(inactiveIconRes)
        }
    }

    // --- RECORDING ---
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    // --- KEYPAD ---
    private fun showKeypadDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(R.layout.dialog_keypad)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

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

    // --- STATUS UPDATES ---
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
            Call.STATE_DIALING -> tvStatus.text = "Calling..."
            Call.STATE_CONNECTING -> tvStatus.text = "Connecting..."
            Call.STATE_DISCONNECTED -> {
                tvStatus.text = "Call Ended"
                stopRecording()
                finish()
            }
            Call.STATE_HOLDING -> tvStatus.text = "On Hold"
        }
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