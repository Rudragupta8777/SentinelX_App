package com.sentinelx.com.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.core.app.ActivityCompat
import com.sentinelx.com.ui.IncomingCallActivity

class SentinelCallService : InCallService() {

    // Singleton to share the Call object with the UI
    companion object {
        var currentCall: Call? = null
        var instance: SentinelCallService? = null
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        instance = this

        // 1. Detect Incoming Call
        if (call.details.callDirection == Call.Details.DIRECTION_INCOMING) {
            currentCall = call

            // 2. Launch your Red Overlay UI
            val intent = Intent(this, IncomingCallActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Pass the phone number to the UI
            val handle = call.details.handle
            val number = handle?.schemeSpecificPart ?: "Unknown"
            intent.putExtra("PHONE_NUMBER", number)

            startActivity(intent)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (currentCall == call) {
            currentCall = null
        }
    }

    /**
     * THE TRAP LOGIC:
     * 1. Answer Scammer
     * 2. Dial Vomyra Bot
     * 3. Merge them
     */
    fun activateTrapAndBridge(scammerCall: Call, botNumber: String) {
        // Step 1: Answer the Scammer (if not already answered)
        if (scammerCall.state == Call.STATE_RINGING) {
            scammerCall.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        // Step 2: Get TelecomManager to place the new call
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val uri = Uri.fromParts("tel", botNumber, null)
        val extras = Bundle()
        extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)

        // Check PERMISSION before placing call (Required by Android)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {

            // Place the outgoing call to the Bot
            // Note: InCallService cannot return the new Call object directly from placeCall.
            // We have to wait for onCallAdded to fire again for the NEW outgoing call.
            telecomManager.placeCall(uri, extras)

            // We set a flag or listener to know the next call added is our Bot Call
            // For this hackathon, we will handle the merge in 'onCallAdded' or by tracking calls.
            bridgeWhenReady(scammerCall)
        }
    }

    private fun bridgeWhenReady(scammerCall: Call) {
        // Since placeCall is async, we can't get the 'botCall' object immediately here.
        // In a real app, we would track this in onCallAdded.
        // However, for the 'Call.Callback' approach you wanted:

        // We will attach a listener to the SCAMMER call to wait for the conference opportunity
        // or simply wait for the user to see the UI update.
    }


}