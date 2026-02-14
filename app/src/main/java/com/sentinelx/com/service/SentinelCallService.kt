package com.sentinelx.com.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sentinelx.com.data.CheckCallRequest
import com.sentinelx.com.network.SentinelNetwork
import com.sentinelx.com.ui.IncomingCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class SentinelCallService : InCallService() {

    companion object {
        private const val TAG = "SentinelService"
        // --- [FIX] DEFINED MISSING CONSTANT ---
        private const val BACKEND_TIMEOUT_MS = 3000L // 3 Seconds

        var currentCall: Call? = null
        var instance: SentinelCallService? = null
        var callStatusCallback: ((Int) -> Unit)? = null
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        instance = this
        currentCall = call

        Log.d(TAG, "onCallAdded() - Call Detected")

        // 1. Register Callback for Lifecycle
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_DISCONNECTED) {
                    currentCall = null
                }
                callStatusCallback?.invoke(state)
            }
        })

        // 2. Only handle RINGING calls (Incoming)
        if (call.state == Call.STATE_RINGING) {
            handleIncomingCall(call)
        }
    }

    private fun handleIncomingCall(call: Call) {
        // 1. HOLD EVERYTHING (Silence System)
        setMuted(true)

        val number = getNumber(call)
        Log.d(TAG, "Incoming Number: $number")

//        // 2. Check Local Contacts (Fast Pass)
//        if (isContactSaved(this, number)) {
//            Log.d(TAG, "Known Contact - Skipping Scan")
//            launchIncomingUI(number, "ALLOW", "Saved Contact")
//            return
//        }

        Log.d(TAG, "Unknown Number - Starting Background Scan...")

        // 3. Start Backend Scan
        CoroutineScope(Dispatchers.IO).launch {
            // Default Fallbacks
            var action = "WARN"
            var message = "Scanning..."

            try {
                // --- [FIX] Using the Defined Constant ---
                withTimeout(BACKEND_TIMEOUT_MS) {
                    // --- [FIX] Using the Network function we added ---
                    val correlationId = SentinelNetwork.generateCorrelationId()
                    Log.d(TAG, "TraceID: $correlationId")

                    val request = CheckCallRequest(number)
                    val response = SentinelNetwork.api.checkCall(correlationId, request)

                    action = response.action
                    message = response.uiMessage
                    Log.d(TAG, "Scan Result: $action")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scan Failed: ${e.message}")
                message = "Offline / Timeout"
            }

            // 4. Launch UI on Main Thread
            withContext(Dispatchers.Main) {
                launchIncomingUI(number, action, message)
            }
        }
    }

    private fun launchIncomingUI(number: String, action: String, message: String) {
        Log.d(TAG, "Launching UI: $action")

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

    private fun getNumber(call: Call): String {
        return call.details.handle?.schemeSpecificPart ?: "Unknown"
    }

    private fun isContactSaved(context: Context, number: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)

        cursor?.use {
            if (it.moveToFirst()) return true
        }
        return false
    }

    // --- Audio Control ---
    fun toggleMute(isMuted: Boolean) = setMuted(isMuted)
    fun toggleSpeaker(isSpeaker: Boolean) = setAudioRoute(if (isSpeaker) android.telecom.CallAudioState.ROUTE_SPEAKER else android.telecom.CallAudioState.ROUTE_EARPIECE)

    // --- Bot Logic ---
    fun activateTrapAndBridge(scammerCall: Call, botNumber: String) {
        if (scammerCall.state == Call.STATE_RINGING) scammerCall.answer(VideoProfile.STATE_AUDIO_ONLY)

        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val uri = Uri.fromParts("tel", botNumber, null)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            telecomManager.placeCall(uri, null)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (calls.size >= 2) calls[0].conference(calls[1])
            }, 2000)
        }
    }

    fun playDtmfTone(digit: Char) {
        currentCall?.playDtmfTone(digit)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ currentCall?.stopDtmfTone() }, 200)
    }
}