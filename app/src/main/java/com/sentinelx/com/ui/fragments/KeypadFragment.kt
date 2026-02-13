package com.sentinelx.com.ui.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.sentinelx.com.R

class KeypadFragment : Fragment(R.layout.fragment_keypad) {

    private lateinit var tvNumber: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvNumber = view.findViewById(R.id.tvNumberDisplay)
        val btnCall = view.findViewById<View>(R.id.btnCall)
        val grid = view.findViewById<GridLayout>(R.id.gridKeypad)

        // 1. Handle Number Clicks
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            if (child is Button) {
                child.setOnClickListener {
                    tvNumber.append(child.text)
                }
            }
        }

        // 2. Handle Call Button
        btnCall.setOnClickListener {
            val number = tvNumber.text.toString()
            if (number.isNotEmpty()) {
                makeCall(number)
            }
        }

        val prefilledNumber = arguments?.getString("PREFILLED_NUMBER")
        if (prefilledNumber != null) {
            tvNumber.text = prefilledNumber
        }
    }

    private fun makeCall(number: String) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$number")
            startActivity(intent)
        } else {
            Toast.makeText(context, "Permission missing", Toast.LENGTH_SHORT).show()
        }
    }
}