package com.yearzero.renebeats.download.ui.viewholder


import android.view.View
import android.widget.ImageView
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView

import com.yearzero.renebeats.R

open class BasicViewHolder(protected var Main: View) : RecyclerView.ViewHolder(Main) {
    protected var Title: TextView = Main.findViewById(R.id.title)
    protected var Action: ImageView = Main.findViewById(R.id.action)

    fun setTitle(title: String) {
        Title.text = title
    }

    fun setOnClickListener(listener: View.OnClickListener) {
        Main.setOnClickListener(listener)
    }

    fun setOnLongClickListener(listener: View.OnLongClickListener) {
        Main.setOnLongClickListener(listener)
    }

    fun setAction(listener: View.OnClickListener) {
        Action.setOnClickListener(listener)
    }

    companion object {
        const val LocalID = 0
    }

}
