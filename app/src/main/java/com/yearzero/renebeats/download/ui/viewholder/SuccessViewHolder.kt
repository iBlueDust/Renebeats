package com.yearzero.renebeats.download.ui.viewholder

import android.view.View
import android.widget.TextView

import com.yearzero.renebeats.R

class SuccessViewHolder(itemView: View) : IntermediateViewHolder(itemView) {

	private var Date: TextView = itemView.findViewById(R.id.date)

	fun setDate(date: String) { Date.text = date }
	fun setRetryListener(listener: View.OnClickListener) = setAction0(listener)

	companion object {
		const val LocalID = 0x0102
	}
}
