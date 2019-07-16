package com.yearzero.renebeats.download.ui.viewholder

import android.graphics.drawable.AnimationDrawable
import android.view.View
import android.widget.ProgressBar
import androidx.constraintlayout.widget.ConstraintLayout
import com.yearzero.renebeats.R

class RunningViewHolder(itemView: View) : QueueViewHolder(itemView) {

    protected var Constraint: ConstraintLayout = itemView.findViewById(R.id.constraint)
    protected var Progress: ProgressBar = itemView.findViewById(R.id.progress)

    init {
        UpdateAnimation()
    }

    fun setProgress(current: Int, total: Int, indeterminate: Boolean) {
        if (indeterminate)
            Progress.isIndeterminate = true
        else {
            Progress.isIndeterminate = false
            Progress.max = total
            Progress.progress = current
        }
    }

    fun setPaused(paused: Boolean) {
        if (paused) {
            Constraint.setBackgroundResource(R.drawable.background_layout_paused)
            Status.text = "PAUSED"
        } else {
            Constraint.setBackgroundResource(R.drawable.background_layout_running)
            UpdateAnimation()
        }
    }

    protected fun UpdateAnimation() {
        val draw = Constraint.background
        if (draw is AnimationDrawable) {
            draw.setEnterFadeDuration(500)
            draw.setExitFadeDuration(1000)
            draw.start()
        }

    }

    companion object {
        val LocalID = 2
    }
}
