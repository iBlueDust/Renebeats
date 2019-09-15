package com.yearzero.renebeats.download

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yearzero.renebeats.R
import com.yearzero.renebeats.download.ui.viewholder.BasicViewHolder
import kotlin.math.max

class HistoryAdapter(private val context: Context, private val sections: ArrayList<HistorySection>) : RecyclerView.Adapter<BasicViewHolder>() {
    constructor(context: Context) : this(context, emptyArray<HistorySection>())
    constructor(context: Context, sections: Array<HistorySection>) : this(context, ArrayList(sections.toList()))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicViewHolder {
        val type = getItemType(viewType)
        return if (isItemHeader(type)) { // Means header
            BasicViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_header, parent, false))
        } else sections[type.first].onCreateViewHolder(parent, sections[type.first].getItemViewType(type.second))
    }

    override fun getItemCount(): Int {
        var master = 0
        sections.forEach { i -> master += i.getItemCount() + 1 }
        return master
    }

    override fun onBindViewHolder(holder: BasicViewHolder, position: Int) {
        val type = getItemType(position)
        // Means header
        if (type.first == -1) holder.setTitle(sections[type.second].name)
        else sections[type.first].onBindViewHolder(holder, type.second)
    }

    override fun getItemViewType(position: Int): Int = position

    private fun getItemType(position: Int) : Pair<Int, Int> {
        if (position < 0) throw ArrayIndexOutOfBoundsException(position)
        var current = 0
        var i = 0
        while (sections[i].getItemCount() + current < position) {
            current += max(0, sections[i++].getItemCount()) + 1
        }

        return if (position == current) Pair(-1, i) // HEADER -> first: -1 (indicator)  ; second: section in array
        else Pair(i, position - current - 1)        // ITEM   -> first: section in array; second: item in array
    }

    internal fun isItemHeader(position: Int): Boolean = isItemHeader(getItemType(position))

    private fun isItemHeader(type: Pair<Int, Int>): Boolean = type.first == -1

    fun clearAll() { sections.clear() }

    fun addSection(vararg section: HistorySection) {
        sections.addAll(section)
        notifyDataSetChanged()
    }
    fun getItemAt(position: Int): HistoryLog? {
        val type = getItemType(position)
        return if (isItemHeader(type)) null
        else sections[type.first].getItemAt(type.second)
    }
}