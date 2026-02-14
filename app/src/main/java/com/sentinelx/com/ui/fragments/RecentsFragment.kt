package com.sentinelx.com.ui.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sentinelx.com.R
import com.sentinelx.com.data.CallLogItem
import com.sentinelx.com.ui.adapter.RecentsAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RecentsFragment : Fragment(R.layout.fragment_recents) {

    private lateinit var adapter: RecentsAdapter
    private var animationJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvRecents = view.findViewById<RecyclerView>(R.id.rvRecents)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val logoContainer = view.findViewById<LinearLayout>(R.id.logoContainer)

        // --- RECYCLER VIEW SETUP ---
        rvRecents.layoutManager = LinearLayoutManager(requireContext())
        adapter = RecentsAdapter(emptyList()) { number -> makeCall(number) }
        rvRecents.adapter = adapter

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            loadCallLog()
        }

        // --- SEARCH & ANIMATION LOGIC ---

        // 1. Start animation initially
        startLogoAnimation(logoContainer)

        // 2. Focus Listener: Hide logo when user clicks to type
        etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // User clicked search: Stop anim, Hide logo, Show keyboard
                stopLogoAnimation()
                logoContainer.visibility = View.GONE
                showKeyboard(etSearch)
            } else {
                // Lost focus: If empty, show logo again
                if (etSearch.text.isNullOrEmpty()) {
                    logoContainer.visibility = View.VISIBLE
                    startLogoAnimation(logoContainer)
                }
            }
        }

        // 3. Text Listener: Filter list
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s.toString())

                // Extra check: if user clears text but stays focused, keep logo hidden.
                // If user clears text and closes keyboard (loses focus), logic is handled in OnFocusChange.
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun startLogoAnimation(container: LinearLayout) {
        // Cancel any existing job to prevent duplicates
        animationJob?.cancel()

        animationJob = lifecycleScope.launch {
            while (isActive) {
                // Step 1: Reset - Make all letters VISIBLE
                container.children.forEach { it.visibility = View.VISIBLE }

                // Show full text for 2 seconds
                delay(2000)

                // Step 2: Disappear one by one (Left to Right)
                for (child in container.children) {
                    if (!isActive) break
                    child.visibility = View.INVISIBLE
                    delay(150) // Speed of disappearance
                }

                // Step 3: Wait a bit before restarting loop
                delay(1000)
            }
        }
    }

    private fun stopLogoAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    private fun showKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun loadCallLog() {
        // ... (Keep your existing loadCallLog code here) ...
        val logs = ArrayList<CallLogItem>()
        val cursor = requireContext().contentResolver.query(
            CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC"
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
        adapter.updateData(logs)
    }

    private fun makeCall(number: String) {
        val intent = Intent(Intent.ACTION_CALL)
        intent.data = Uri.parse("tel:$number")
        startActivity(intent)
    }
}