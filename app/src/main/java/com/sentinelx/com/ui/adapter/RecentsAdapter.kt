package com.sentinelx.com.ui.adapter

import android.graphics.Color
import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sentinelx.com.R
import com.sentinelx.com.data.CallLogItem
import com.sentinelx.com.data.RecentsItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecentsAdapter(
    private var allCalls: List<CallLogItem>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var displayList = ArrayList<RecentsItem>()
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    private val TYPE_HEADER = 0
    private val TYPE_ITEM = 1

    init {
        groupData(allCalls)
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvHeader)
    }

    class CallViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvInfo: TextView = view.findViewById(R.id.tvInfo)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val ivType: ImageView = view.findViewById(R.id.ivCallType)

        // Avatar Views
        val tvInitials: TextView = view.findViewById(R.id.tvInitials)
        val ivUnknownUser: ImageView = view.findViewById(R.id.ivUnknownUser)
        val btnCallAction: ImageView = view.findViewById(R.id.btnCallAction)
    }

    override fun getItemViewType(position: Int): Int {
        return if (displayList[position] is RecentsItem.Header) TYPE_HEADER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_call, parent, false)
            CallViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = displayList[position]

        if (holder is HeaderViewHolder && item is RecentsItem.Header) {
            holder.tvTitle.text = item.title
        } else if (holder is CallViewHolder && item is RecentsItem.Log) {
            val call = item.data

            // --- AVATAR LOGIC (Grey Only) ---
            if (call.name.isNotEmpty()) {
                holder.tvName.text = call.name
                val initial = call.name.first().toString().uppercase()
                holder.tvInitials.text = initial

                holder.tvInitials.visibility = View.VISIBLE
                holder.ivUnknownUser.visibility = View.GONE
            } else {
                holder.tvName.text = call.number
                holder.tvInitials.visibility = View.GONE
                holder.ivUnknownUser.visibility = View.VISIBLE
            }

            // REMOVED: Any background tint setting here.
            // It will use @drawable/bg_circle_avatar (#666666) by default.

            holder.tvTime.text = timeFormat.format(Date(call.date))

            when (call.type) {
                CallLog.Calls.INCOMING_TYPE -> {
                    holder.ivType.setImageResource(R.drawable.ic_incomming)
                    holder.ivType.setColorFilter(Color.parseColor("#4CAF50"))
                    holder.tvInfo.text = "Incoming"
                }
                CallLog.Calls.OUTGOING_TYPE -> {
                    holder.ivType.setImageResource(R.drawable.ic_outgoing)
                    holder.ivType.setColorFilter(Color.parseColor("#2196F3"))
                    holder.tvInfo.text = "Outgoing"
                }
                CallLog.Calls.MISSED_TYPE -> {
                    holder.ivType.setImageResource(R.drawable.ic_missed)
                    holder.ivType.setColorFilter(Color.parseColor("#F44336"))
                    holder.tvInfo.text = "Missed"
                    holder.tvName.setTextColor(Color.parseColor("#F44336"))
                }
                else -> {
                    holder.ivType.setImageResource(R.drawable.ic_incomming)
                    holder.tvInfo.text = "Unknown"
                }
            }

            holder.btnCallAction.setOnClickListener {
                onItemClick(call.number)
            }
        }
    }

    override fun getItemCount() = displayList.size

    fun filter(query: String) {
        if (query.isEmpty()) groupData(allCalls)
        else {
            val filtered = allCalls.filter { it.number.contains(query) || it.name.contains(query, true) }
            groupData(filtered)
        }
        notifyDataSetChanged()
    }

    fun updateData(newData: List<CallLogItem>) {
        allCalls = newData
        filter("")
    }

    private fun groupData(list: List<CallLogItem>) {
        displayList.clear()
        val today = ArrayList<CallLogItem>()
        val yesterday = ArrayList<CallLogItem>()
        val older = ArrayList<CallLogItem>()
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L

        for (call in list) {
            val diff = now - call.date
            when {
                diff < oneDay -> today.add(call)
                diff < (2 * oneDay) -> yesterday.add(call)
                else -> older.add(call)
            }
        }

        if (today.isNotEmpty()) {
            displayList.add(RecentsItem.Header("Today"))
            today.forEach { displayList.add(RecentsItem.Log(it)) }
        }
        if (yesterday.isNotEmpty()) {
            displayList.add(RecentsItem.Header("Yesterday"))
            yesterday.forEach { displayList.add(RecentsItem.Log(it)) }
        }
        if (older.isNotEmpty()) {
            displayList.add(RecentsItem.Header("Older"))
            older.forEach { displayList.add(RecentsItem.Log(it)) }
        }
    }
}