package com.sentinelx.com.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sentinelx.com.R

class ContactsAdapter(
    private var contacts: List<Pair<String, String>>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.ViewHolder>() {

    private var filteredContacts = ArrayList(contacts)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvContactName)
        val tvNumber: TextView = view.findViewById(R.id.tvContactNumber)

        // Avatar Views
        val ivAvatar: View = view.findViewById(R.id.ivAvatar)
        val tvInitials: TextView = view.findViewById(R.id.tvInitials)
        val ivUnknownUser: ImageView = view.findViewById(R.id.ivUnknownUser)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (name, number) = filteredContacts[position]

        holder.tvName.text = name
        holder.tvNumber.text = number

        // --- AVATAR LOGIC (Grey Only) ---
        if (name.isNotEmpty()) {
            // Show Initials
            val letter = name[0].toString().uppercase()
            holder.tvInitials.text = letter

            holder.tvInitials.visibility = View.VISIBLE
            holder.ivUnknownUser.visibility = View.GONE
        } else {
            // Show Icon
            holder.tvInitials.visibility = View.GONE
            holder.ivUnknownUser.visibility = View.VISIBLE
        }

        // REMOVED: holder.ivAvatar.background.setTint(...)
        // It will now stay Grey (#666666) as defined in xml

        holder.itemView.setOnClickListener { onItemClick(number) }
    }

    override fun getItemCount() = filteredContacts.size

    fun filter(query: String) {
        filteredContacts = if (query.isEmpty()) {
            ArrayList(contacts)
        } else {
            val result = ArrayList<Pair<String, String>>()
            for (item in contacts) {
                if (item.first.contains(query, ignoreCase = true) || item.second.contains(query)) {
                    result.add(item)
                }
            }
            result
        }
        notifyDataSetChanged()
    }

    fun updateList(newList: List<Pair<String, String>>) {
        contacts = newList
        filter("")
    }

    fun getPositionForSection(letter: Char): Int {
        for (i in filteredContacts.indices) {
            if (filteredContacts[i].first.startsWith(letter, ignoreCase = true)) {
                return i
            }
        }
        return -1
    }
}