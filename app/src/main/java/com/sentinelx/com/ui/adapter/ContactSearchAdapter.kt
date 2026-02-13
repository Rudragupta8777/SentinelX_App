package com.sentinelx.com.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sentinelx.com.R

class ContactSearchAdapter(
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<ContactSearchAdapter.ViewHolder>() {
    private val contacts = ArrayList<Pair<String, String>>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvContactName)
        val tvNumber: TextView = view.findViewById(R.id.tvContactNumber)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (name, number) = contacts[position]
        holder.tvName.text = name
        holder.tvNumber.text = number

        holder.itemView.setOnClickListener {
            onItemClick(number)
        }
    }

    override fun getItemCount() = contacts.size

    fun updateData(newData: List<Pair<String, String>>) {
        contacts.clear()
        contacts.addAll(newData)
        notifyDataSetChanged()
    }
}