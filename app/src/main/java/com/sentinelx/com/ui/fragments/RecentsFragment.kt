package com.sentinelx.com.ui.fragments

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.CallLog
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sentinelx.com.R
import com.sentinelx.com.data.CallLogItem
import com.sentinelx.com.data.ReportRequest
import com.sentinelx.com.network.SentinelNetwork
import com.sentinelx.com.ui.adapter.RecentsAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RecentsFragment : Fragment(R.layout.fragment_recents) {

    private lateinit var adapter: RecentsAdapter
    private lateinit var rvRecents: RecyclerView
    private var animationJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvRecents = view.findViewById(R.id.rvRecents)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val logoContainer = view.findViewById<LinearLayout>(R.id.logoContainer)

        rvRecents.layoutManager = LinearLayoutManager(requireContext())
        adapter = RecentsAdapter(emptyList()) { number -> makeCall(number) }
        rvRecents.adapter = adapter

        setupSwipeToReport()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            loadCallLog()
        }

        startLogoAnimation(logoContainer)

        etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                stopLogoAnimation()
                logoContainer.visibility = View.GONE
                showKeyboard(etSearch)
            } else {
                if (etSearch.text.isNullOrEmpty()) {
                    logoContainer.visibility = View.VISIBLE
                    startLogoAnimation(logoContainer)
                }
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { adapter.filter(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupSwipeToReport() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val position = viewHolder.adapterPosition
                val item = adapter.getItemAt(position)

                // BLOCK swipe UI for Saved Contacts (Name is not empty)
                if (item == null || item.name.isNotEmpty()) {
                    super.onChildDraw(c, recyclerView, viewHolder, 0f, dY, actionState, isCurrentlyActive)
                    return
                }

                val itemView = viewHolder.itemView
                val background = ColorDrawable(Color.parseColor("#B71C1C"))
                val icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_dialog_alert)

                if (dX < 0) {
                    background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    background.draw(c)

                    icon?.let {
                        val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                        val iconTop = itemView.top + iconMargin
                        val iconBottom = iconTop + it.intrinsicHeight
                        val iconLeft = itemView.right - iconMargin - it.intrinsicWidth
                        val iconRight = itemView.right - iconMargin
                        it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        it.setTint(Color.WHITE)
                        it.draw(c)
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val position = viewHolder.adapterPosition
                val item = adapter.getItemAt(position)
                // Disable swipe if saved contact or header
                return if (item == null || item.name.isNotEmpty()) 0 else super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = adapter.getItemAt(position)
                if (item != null) showReportDialog(item.number)
                adapter.notifyItemChanged(position)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(rvRecents)
    }

    private fun showReportDialog(scammerNumber: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_report, null)
        val tvScammer = dialogView.findViewById<TextView>(R.id.tvReportNumber)
        val etReason = dialogView.findViewById<EditText>(R.id.etReason)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmitReport)

        // Hide reporter input if you already updated the XML, or just don't use it
        tvScammer.text = "Reporting: $scammerNumber"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnSubmit.setOnClickListener {
            val reason = etReason.text.toString().trim()
            if (reason.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a reason", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // [DIRECT FETCH] No user input for number
            val reporterNumber = getMyPhoneNumber()
            submitReport(reporterNumber, scammerNumber, reason)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun submitReport(reporter: String, scammer: String, reason: String) {
        lifecycleScope.launch {
            try {
                // Ensure we don't send an empty string to the backend
                val finalReporter = if (reporter.isEmpty()) "SIM_HIDDEN" else reporter

                val request = ReportRequest(finalReporter, scammer, reason)
                val response = SentinelNetwork.api.reportScam(request)

                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), " Reported Successfully", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Report failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getMyPhoneNumber(): String {
        // Double check permissions
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) {
            return ""
        }

        try {
            val sm = requireContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subs = sm.activeSubscriptionInfoList

            // Priority 1: Subscription Manager (Modern Android)
            if (!subs.isNullOrEmpty()) {
                val num = subs[0].number
                if (!num.isNullOrEmpty()) return num
            }

            // Priority 2: Telephony Manager (Legacy Fallback)
            val tm = requireContext().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            return tm.line1Number ?: ""
        } catch (e: Exception) {
            return ""
        }
    }

    private fun startLogoAnimation(container: LinearLayout) {
        animationJob?.cancel()
        animationJob = lifecycleScope.launch {
            while (isActive) {
                container.children.forEach { it.visibility = View.VISIBLE }
                delay(2000)
                for (child in container.children) {
                    if (!isActive) break
                    child.visibility = View.INVISIBLE
                    delay(150)
                }
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
        val intent = android.content.Intent(android.content.Intent.ACTION_CALL)
        intent.data = android.net.Uri.parse("tel:$number")
        startActivity(intent)
    }
}