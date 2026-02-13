package com.sentinelx.com.ui.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.provider.CallLog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.sentinelx.com.R
import java.util.Date

class RecentsFragment : Fragment(R.layout.fragment_recents) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val listView = view.findViewById<ListView>(R.id.lvRecents)

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            loadCallLog(listView)
        }
    }

    private fun loadCallLog(listView: ListView) {
        val logs = ArrayList<String>()
        val cursor = requireContext().contentResolver.query(
            CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC"
        )

        cursor?.use {
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)

            while (it.moveToNext()) {
                val number = it.getString(numberIndex)
                val type = it.getInt(typeIndex)
                val direction = if (type == CallLog.Calls.INCOMING_TYPE) "Incoming" else "Outgoing"
                logs.add("$number\n$direction")
            }
        }

        // Using simple layout for speed
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, logs)
        listView.adapter = adapter
    }
}