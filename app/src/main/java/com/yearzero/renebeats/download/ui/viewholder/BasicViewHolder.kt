package com.yearzero.renebeats.download.ui.viewholder


import android.view.View
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView

import com.yearzero.renebeats.R

open class BasicViewHolder(private val Main: View) : RecyclerView.ViewHolder(Main) {
    protected val Title: TextView = Main.findViewById(R.id.title)

    open fun setTitle(text: String) { Title.text = text }
    open fun getTitle(): CharSequence = Title.text

    open fun setOnClickListener(listener: View.OnClickListener) = Main.setOnClickListener(listener)
    open fun setOnLongClickListener(listener: View.OnLongClickListener) = Main.setOnLongClickListener(listener)

    companion object {
        const val LocalID = 0
    }
}
