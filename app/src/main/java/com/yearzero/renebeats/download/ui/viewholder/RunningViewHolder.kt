package com.yearzero.renebeats.download.ui.viewholder

import android.graphics.drawable.AnimationDrawable
import android.view.View
import android.widget.ProgressBar
import androidx.constraintlayout.widget.ConstraintLayout
import com.yearzero.renebeats.R

class RunningViewHolder(Main: View) : IntermediateViewHolder(Main) {

    private var Constraint: ConstraintLayout = Main.findViewById(R.id.constraint)
    private var Progress: ProgressBar = Main.findViewById(R.id.progress)

    init { updateAnimation() }

    fun setCancelListener(listener: View.OnClickListener) = setAction0(listener)

    fun setProgress(current: Int, total: Int, indeterminate: Boolean) {
        if (indeterminate)
            Progress.isIndeterminate = true
        else {
            Progress.isIndeterminate = false
            Progress.max = total
            Progress.progress = current
        }
    }

//    fun setPaused(value: Boolean) = if (value) {
//        Constraint.setBackgroundResource(R.drawable.background_layout_paused)
//        Status.text = "PAUSED"
//        Action0.setImageResource(R.drawable.ic_play_black_24dp)
//    } else {
//        Constraint.setBackgroundResource(R.drawable.background_layout_running)
//        Action0.setImageResource(R.drawable.ic_pause_black_24dp)
//        updateAnimation()
//    }

    private fun updateAnimation() {
        val draw = Constraint.background
        if (draw is AnimationDrawable) {
            draw.setEnterFadeDuration(500)
            draw.setExitFadeDuration(1000)
            draw.start()
        }
    }

    companion object {
        const val LocalID = 0x0101
    }
}
