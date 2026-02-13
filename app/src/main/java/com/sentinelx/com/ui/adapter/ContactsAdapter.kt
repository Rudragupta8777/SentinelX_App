package com.sentinelx.com.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.sentinelx.com.R
import java.util.Random

class ContactsAdapter(
    private var contacts: List<Pair<String, String>>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.ViewHolder>() {

    private var filteredContacts = ArrayList(contacts)
    private val colorMap = HashMap<String, Int>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvContactName)
        val tvNumber: TextView = view.findViewById(R.id.tvContactNumber)
        val tvAvatarLetter: TextView = view.findViewById(R.id.tvAvatarLetter)
        val cvAvatar: CardView = view.findViewById(R.id.cvAvatar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // FIXED: Pointing to item_contact_card instead of item_contact_search
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (name, number) = filteredContacts[position]

        holder.tvName.text = name
        holder.tvNumber.text = number

        // Avatar Logic
        val letter = if (name.isNotEmpty()) name[0].toString().uppercase() else "?"
        holder.tvAvatarLetter.text = letter
        holder.cvAvatar.setCardBackgroundColor(getAvatarColor(name))

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

    private fun getAvatarColor(key: String): Int {
        if (colorMap.containsKey(key)) return colorMap[key]!!
        val rnd = Random()
        // Generate nice pastel/dark colors
        val color = Color.argb(255, rnd.nextInt(150) + 50, rnd.nextInt(150) + 50, rnd.nextInt(150) + 50)
        colorMap[key] = color
        return color
    }
}