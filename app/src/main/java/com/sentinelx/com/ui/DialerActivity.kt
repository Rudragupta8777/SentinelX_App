package com.sentinelx.com.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class DialerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Just finish immediately.
        // We don't actually want to let the user dial out from here for the demo.
        // Or you can add a simple keypad if you have time.
    }
}