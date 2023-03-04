package com.yearzero.renebeats.download.ui.viewholder


import android.view.View
import android.widget.ImageView
import android.widget.TextView

import com.yearzero.renebeats.R

open class IntermediateViewHolder(Main: View) : BasicViewHolder(Main) {
	protected val statusView: TextView = Main.findViewById(R.id.status)
	protected var action0View: ImageView = Main.findViewById(R.id.action0)

	open fun setStatus(text: String) { statusView.text = text }
	protected open fun setAction0(listener: View.OnClickListener) =
		action0View.setOnClickListener(listener)

	companion object {
		const val LocalID = 0x0100
	}
}
