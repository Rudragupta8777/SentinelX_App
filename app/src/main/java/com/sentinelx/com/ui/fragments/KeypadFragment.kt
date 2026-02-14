package com.sentinelx.com.ui.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sentinelx.com.R
import com.sentinelx.com.ui.adapter.ContactSearchAdapter

class KeypadFragment : Fragment(R.layout.fragment_keypad) {

    private lateinit var tvNumber: TextView
    private lateinit var searchAdapter: ContactSearchAdapter
    private lateinit var toneGenerator: ToneGenerator

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Initialize Views
        tvNumber = view.findViewById(R.id.tvNumberDisplay)
        val grid = view.findViewById<GridLayout>(R.id.gridKeypad)
        val btnBackspace = view.findViewById<ImageButton>(R.id.btnBackspace)
        val rvSearch = view.findViewById<RecyclerView>(R.id.rvContactSearch)
        // Note: btnCall is now an ImageView inside the container
        val btnCall = view.findViewById<ImageView>(R.id.btnCall)

        // 2. Initialize Tone Generator
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Setup RecyclerView
        searchAdapter = ContactSearchAdapter { clickedNumber ->
            tvNumber.text = clickedNumber
            makeCall(clickedNumber)
        }
        rvSearch.layoutManager = LinearLayoutManager(requireContext())
        rvSearch.adapter = searchAdapter

        // 4. Handle Grid Clicks (Supports AppCompatButton)
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            // Check for AppCompatButton (or standard Button)
            if (child is TextView) { // Both Button and AppCompatButton inherit TextView
                child.setOnClickListener {
                    val char = child.text.toString()
                    playTone(char[0])
                    tvNumber.append(char)
                    searchContacts(tvNumber.text.toString())
                }
            }
        }

        // 5. Backspace Logic
        btnBackspace.setOnClickListener {
            val currentText = tvNumber.text.toString()
            if (currentText.isNotEmpty()) {
                playTone('1') // Tiny beep
                tvNumber.text = currentText.substring(0, currentText.length - 1)
                searchContacts(tvNumber.text.toString())
            }
        }

        btnBackspace.setOnLongClickListener {
            tvNumber.text = ""
            searchContacts("")
            true
        }

        // 6. Call Button Logic
        btnCall.setOnClickListener {
            val number = tvNumber.text.toString()
            if (number.isNotEmpty()) {
                makeCall(number)
            } else {
                Toast.makeText(requireContext(), "Enter a number", Toast.LENGTH_SHORT).show()
            }
        }

        // 7. Handle Arguments (Deep links)
        val prefilledNumber = arguments?.getString("PREFILLED_NUMBER")
        if (prefilledNumber != null) {
            tvNumber.text = prefilledNumber
            searchContacts(prefilledNumber)
        }
    }

    private fun playTone(digit: Char) {
        val toneType = when (digit) {
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '0' -> ToneGenerator.TONE_DTMF_0
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> ToneGenerator.TONE_PROP_BEEP
        }
        toneGenerator.startTone(toneType, 150)
    }

    private fun searchContacts(query: String) {
        if (query.isEmpty()) {
            searchAdapter.updateData(emptyList())
            return
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // Simple search query logic
        val results = ArrayList<Pair<String, String>>()
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            val cursor = requireContext().contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val name = if (nameIdx != -1) it.getString(nameIdx) else "Unknown"
                    val number = if (numIdx != -1) it.getString(numIdx) else ""
                    results.add(Pair(name, number))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        searchAdapter.updateData(results)
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

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            toneGenerator.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}