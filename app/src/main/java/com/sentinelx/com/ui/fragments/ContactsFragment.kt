package com.sentinelx.com.ui.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sentinelx.com.R
import com.sentinelx.com.ui.adapter.ContactsAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class ContactsFragment : Fragment(R.layout.fragment_contacts) {

    private lateinit var adapter: ContactsAdapter
    private lateinit var rvContacts: RecyclerView
    private lateinit var sideBar: LinearLayout
    private lateinit var tvOverlay: TextView

    // Animation Job
    private var animationJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvContacts = view.findViewById(R.id.rvContacts)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        sideBar = view.findViewById(R.id.sideBar)
        tvOverlay = view.findViewById(R.id.tvFastScrollOverlay)

        // Find the Logo Container
        val logoContainer = view.findViewById<LinearLayout>(R.id.logoContainer)

        rvContacts.layoutManager = LinearLayoutManager(requireContext())
        adapter = ContactsAdapter(emptyList()) { number -> makeCall(number) }
        rvContacts.adapter = adapter

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        }

        // --- ANIMATION LOGIC START ---

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

        // --- ANIMATION LOGIC END ---

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        setupSideBar()
    }

    // --- NEW ANIMATION FUNCTIONS ---

    private fun startLogoAnimation(container: LinearLayout) {
        animationJob?.cancel()
        animationJob = lifecycleScope.launch {
            while (isActive) {
                // Reset: Make visible
                container.children.forEach { it.visibility = View.VISIBLE }
                delay(2000)

                // Disappear one by one
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

    // --- EXISTING LOGIC ---

    private fun loadContacts() {
        val contactList = ArrayList<Pair<String, String>>()
        val cursor = requireContext().contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: "Unknown"
                val num = it.getString(numIdx) ?: ""
                contactList.add(Pair(name, num))
            }
        }
        adapter.updateList(contactList.distinctBy { it.first })
    }

    private fun makeCall(number: String) {
        val intent = Intent(Intent.ACTION_CALL)
        intent.data = Uri.parse("tel:$number")
        startActivity(intent)
    }

    private fun setupSideBar() {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        sideBar.removeAllViews()

        for (char in alphabet) {
            val tv = TextView(context)
            tv.text = char.toString()
            // Updated color to match light background theme if needed,
            // but keeping gray for now to be safe with white/off-white backgrounds
            tv.setTextColor(Color.parseColor("#888888"))
            tv.textSize = 12f
            tv.gravity = Gravity.CENTER
            tv.typeface = Typeface.DEFAULT_BOLD
            tv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            sideBar.addView(tv)
        }

        sideBar.setOnTouchListener { v, event ->
            val totalHeight = v.height
            val touchY = event.y
            val singleItemHeight = totalHeight / 26f
            val index = (touchY / singleItemHeight).toInt().coerceIn(0, 25)
            val letter = alphabet[index]

            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    tvOverlay.visibility = View.VISIBLE
                    tvOverlay.text = letter.toString()
                    val overlayY = event.rawY - (v.top * 2)
                    tvOverlay.y = overlayY.coerceIn(v.top.toFloat(), (v.bottom - tvOverlay.height).toFloat())

                    scrollToLetter(letter)

                    for (i in 0 until sideBar.childCount) {
                        val tv = sideBar.getChildAt(i) as TextView
                        val viewCenterY = (i * singleItemHeight) + (singleItemHeight / 2)
                        val distance = abs(touchY - viewCenterY)

                        if (distance < 200f) {
                            val scale = 1f + (1.5f * (1 - distance / 200f))
                            tv.animate().scaleX(scale).scaleY(scale).setDuration(0).start()
                            if (scale > 1.8f) {
                                // Changed from WHITE to BLACK because bg is now light
                                tv.setTextColor(Color.BLACK)
                                tv.translationX = -60f
                            }
                        } else {
                            tv.animate().scaleX(1f).scaleY(1f).setDuration(0).start()
                            tv.setTextColor(Color.parseColor("#888888"))
                            tv.translationX = 0f
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tvOverlay.visibility = View.INVISIBLE
                    for (i in 0 until sideBar.childCount) {
                        val tv = sideBar.getChildAt(i) as TextView
                        tv.animate().scaleX(1f).scaleY(1f).translationX(0f).setDuration(200).start()
                        tv.setTextColor(Color.parseColor("#888888"))
                    }
                }
            }
            true
        }
    }

    private fun scrollToLetter(letter: Char) {
        val position = adapter.getPositionForSection(letter)
        if (position != -1) {
            (rvContacts.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopLogoAnimation()
    }
}