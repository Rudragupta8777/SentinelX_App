package com.sentinelx.com.ui.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sentinelx.com.R
import com.sentinelx.com.data.CallLogItem
import com.sentinelx.com.ui.adapter.RecentsAdapter

class RecentsFragment : Fragment(R.layout.fragment_recents) {

    private lateinit var adapter: RecentsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Find Views (Updated IDs)
        val rvRecents = view.findViewById<RecyclerView>(R.id.rvRecents)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)

        // 2. Setup RecyclerView
        rvRecents.layoutManager = LinearLayoutManager(requireContext())

        // Initialize Adapter with empty list first
        adapter = RecentsAdapter(emptyList()) { number ->
            makeCall(number)
        }
        rvRecents.adapter = adapter

        // 3. Check Permissions & Load Data
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            loadCallLog()
        }

        // 4. Search Listener
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun loadCallLog() {
        val logs = ArrayList<CallLogItem>()

        // Query the Call Log Database
        val cursor = requireContext().contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            null,
            null,
            CallLog.Calls.DATE + " DESC"
        )

        cursor?.use {
            val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
            val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)

            while (it.moveToNext()) {
                val number = it.getString(numberIdx) ?: "Unknown"
                val type = it.getInt(typeIdx)
                val date = it.getLong(dateIdx)
                val duration = it.getLong(durIdx)
                val name = it.getString(nameIdx) ?: ""

                logs.add(CallLogItem(name, number, type, date, duration))
            }
        }

        // Pass data to adapter (It handles the "Today/Yesterday" grouping internally)
        adapter.updateData(logs)
    }

    private fun makeCall(number: String) {
        val intent = Intent(Intent.ACTION_CALL)
        intent.data = Uri.parse("tel:$number")
        startActivity(intent)
    }
}