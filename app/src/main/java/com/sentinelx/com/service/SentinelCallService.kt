package com.sentinelx.com.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sentinelx.com.data.CheckCallRequest
import com.sentinelx.com.network.SentinelNetwork
import com.sentinelx.com.ui.CallActivity
import com.sentinelx.com.ui.IncomingCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class SentinelCallService : InCallService() {

    companion object {
        private const val TAG = "SentinelService"
        private const val BACKEND_TIMEOUT_MS = 3000L

        var currentCall: Call? = null
        var instance: SentinelCallService? = null
        var callStatusCallback: ((Int) -> Unit)? = null
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        instance = this
        currentCall = call

        Log.d(TAG, "onCallAdded() - Call Detected")

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_DISCONNECTED) {
                    currentCall = null
                }

                // --- [FIX] LAUNCH CALL SCREEN AUTOMATICALLY ---
                // This handles AI Trap answer and external answers (headsets)
                if (state == Call.STATE_ACTIVE) {
                    val intent = Intent(this@SentinelCallService, CallActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    intent.putExtra("PHONE_NUMBER", getNumber(call))
                    startActivity(intent)
                }

                callStatusCallback?.invoke(state)
            }
        })

        if (call.state == Call.STATE_RINGING) {
            handleIncomingCall(call)
        }
    }

    private fun handleIncomingCall(call: Call) {
        setMuted(true)
        val number = getNumber(call)

        // [Fast Pass Check - Uncomment when ready]
        if (isContactSaved(this, number)) {
            launchIncomingUI(number, "ALLOW", "Saved Contact")
            return
        }


        Log.d(TAG, "Unknown Number - Starting Background Scan...")

        CoroutineScope(Dispatchers.IO).launch {
            var action = "WARN"
            var message = "Scanning..."

            try {
                withTimeout(BACKEND_TIMEOUT_MS) {
                    val correlationId = SentinelNetwork.generateCorrelationId()
                    val request = CheckCallRequest(number)
                    val response = SentinelNetwork.api.checkCall(correlationId, request)
                    action = response.action
                    message = response.uiMessage
                }
            } catch (e: Exception) {
                message = "Offline / Timeout"
            }

            withContext(Dispatchers.Main) {
                launchIncomingUI(number, action, message)
            }
        }
    }

    private fun launchIncomingUI(number: String, action: String, message: String) {
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("PHONE_NUMBER", number)
            putExtra("ACTION", action)
            putExtra("MESSAGE", message)
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        currentCall = null
    }

    // --- Helpers ---
    private fun getNumber(call: Call): String = call.details.handle?.schemeSpecificPart ?: "Unknown"

    private fun isContactSaved(context: Context, number: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
        cursor?.use { if (it.moveToFirst()) return true }
        return false
    }

    fun toggleMute(isMuted: Boolean) = setMuted(isMuted)
    fun toggleSpeaker(isSpeaker: Boolean) = setAudioRoute(if (isSpeaker) android.telecom.CallAudioState.ROUTE_SPEAKER else android.telecom.CallAudioState.ROUTE_EARPIECE)
    fun playDtmfTone(digit: Char) {
        currentCall?.playDtmfTone(digit)
        Handler(Looper.getMainLooper()).postDelayed({ currentCall?.stopDtmfTone() }, 200)
    }

    fun activateTrapAndBridge(scammerCall: Call, botNumber: String) {
        Log.d(TAG, "Step 1: Answering Scammer Call")

        if (scammerCall.state == Call.STATE_RINGING) {
            // This answer() will trigger onStateChanged -> STATE_ACTIVE
            // which will launch CallActivity via the code added above.
            scammerCall.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            dialBot(scammerCall, botNumber)
        }, 1500)
    }

    private fun dialBot(scammerCall: Call, botNumber: String) {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val cleanNumber = botNumber.replace(" ", "").trim()
        val uri = Uri.fromParts("tel", cleanNumber, null)
        val extras = Bundle()
        extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)

        if (scammerCall.details.accountHandle != null) {
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, scammerCall.details.accountHandle)
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Step 2: Dialing Bot ($cleanNumber)")
            telecomManager.placeCall(uri, extras)
            monitorCallsForMerge()
        }
    }

    private fun monitorCallsForMerge() {
        val handler = Handler(Looper.getMainLooper())

        val checkRunnable = object : Runnable {
            var attempts = 0
            override fun run() {
                attempts++
                val calls = calls
                if (calls.size >= 2) {
                    val call1 = calls[0]
                    val call2 = calls[1]

                    if (call2.state == Call.STATE_ACTIVE) {
                        Log.d(TAG, "Step 3: Bot Answered! MERGING...")
                        call1.conference(call2)
                        call2.conference(call1)

                        Log.d(TAG, "Step 4: Muting User Microphone")
                        setMuted(true)
                        Toast.makeText(applicationContext, "🔴 Trap Active: Mic Muted", Toast.LENGTH_LONG).show()
                        return
                    }
                }
                if (attempts < 15) {
                    handler.postDelayed(this, 1000)
                } else {
                    Toast.makeText(applicationContext, "Bot didn't answer", Toast.LENGTH_SHORT).show()
                }
            }
        }
        handler.post(checkRunnable)
    }
}