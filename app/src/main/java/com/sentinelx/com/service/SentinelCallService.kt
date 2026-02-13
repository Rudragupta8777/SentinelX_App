package com.sentinelx.com.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sentinelx.com.ui.CallActivity
import com.sentinelx.com.ui.IncomingCallActivity

class SentinelCallService : InCallService() {

    companion object {
        var currentCall: Call? = null
        var instance: SentinelCallService? = null
        var callStatusCallback: ((Int) -> Unit)? = null
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        instance = this
        currentCall = call

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                callStatusCallback?.invoke(state)

                if (state == Call.STATE_ACTIVE) {
                    launchCallScreen(call)
                    checkForMergeOpportunity()
                }

                // Cleanup on disconnect
                if (state == Call.STATE_DISCONNECTED) {
                    if (currentCall == call) currentCall = null
                }
            }
        })

        // 2. INITIAL SCREEN DECISION
        val number = getNumber(call)

        if (call.state == Call.STATE_RINGING) {
            // INCOMING -> Show Red/Black Incoming Screen
            val isSavedContact = isContactSaved(this, number)

            val intent = Intent(this, IncomingCallActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("PHONE_NUMBER", number)
            intent.putExtra("IS_CONTACT", isSavedContact)
            startActivity(intent)

        } else if (call.state == Call.STATE_DIALING || call.state == Call.STATE_CONNECTING) {
            // OUTGOING -> Show Ongoing Call Screen immediately
            launchCallScreen(call)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (currentCall == call) currentCall = null
        callStatusCallback?.invoke(Call.STATE_DISCONNECTED)
    }

    // --- HELPER: LAUNCH THE CALL ACTIVITY ---
    private fun launchCallScreen(call: Call) {
        val intent = Intent(this, CallActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("PHONE_NUMBER", getNumber(call))
        startActivity(intent)
    }

    private fun getNumber(call: Call): String {
        return call.details.handle?.schemeSpecificPart ?: "Unknown"
    }

    // --- HELPER: CHECK IF CONTACT IS SAVED ---
    private fun isContactSaved(context: Context, number: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        var isContact = false
        val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) isContact = true
        }
        return isContact
    }

    // --- TRAP & MERGE LOGIC ---
    fun activateTrapAndBridge(scammerCall: Call, botNumber: String) {
        // 1. Answer the Scammer
        if (scammerCall.state == Call.STATE_RINGING) {
            scammerCall.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        // 2. Dial the Bot (This creates a NEW call, triggering onCallAdded again)
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val uri = Uri.fromParts("tel", botNumber, null)
        val extras = Bundle()
        extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            telecomManager.placeCall(uri, extras)
        }
    }

    fun playDtmfTone(digit: Char) {
        currentCall?.playDtmfTone(digit)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            currentCall?.stopDtmfTone()
        }, 200)
    }

    // 2. MUTE SUPPORT
    fun toggleMute(isMuted: Boolean) {
        setMuted(isMuted)
    }

    fun toggleSpeaker(isSpeaker: Boolean) {
        val route = if (isSpeaker) android.telecom.CallAudioState.ROUTE_SPEAKER
        else android.telecom.CallAudioState.ROUTE_EARPIECE
        setAudioRoute(route)
    }

    private fun checkForMergeOpportunity() {
        if (calls.size >= 2) {
            val call1 = calls[0]
            val call2 = calls[1]
            if ((call1.state == Call.STATE_ACTIVE || call1.state == Call.STATE_HOLDING) &&
                (call2.state == Call.STATE_ACTIVE || call2.state == Call.STATE_HOLDING)) {

                call1.conference(call2)
                call2.conference(call1)
            }
        }
    }
}