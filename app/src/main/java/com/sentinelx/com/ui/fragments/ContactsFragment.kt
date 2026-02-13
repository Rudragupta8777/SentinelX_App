package com.sentinelx.com.ui.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.sentinelx.com.R

class ContactsFragment : Fragment(R.layout.fragment_contacts) {

    private lateinit var adapter: ArrayAdapter<String>
    private val allContacts = ArrayList<String>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val listView = view.findViewById<ListView>(R.id.lvContacts)
        val searchBar = view.findViewById<EditText>(R.id.etSearch)

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, ArrayList())
        listView.adapter = adapter

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        }

        // Search Logic
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                adapter.filter.filter(s)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun loadContacts() {
        val cursor = requireContext().contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                val num = it.getString(numIndex)
                allContacts.add("$name\n$num")
            }
        }
        adapter.addAll(allContacts)
    }
}