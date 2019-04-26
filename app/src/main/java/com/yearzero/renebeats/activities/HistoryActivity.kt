package com.yearzero.renebeats.activities

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.yearzero.renebeats.R

class HistoryActivity : AppCompatActivity() {

    private lateinit var Title: TextView
    private lateinit var Search: SearchView
    private lateinit var List: RecyclerView

    private val focusSet: TransitionSet = TransitionSet()
    private val unfocusSet: TransitionSet = TransitionSet()

    init {
        focusSet.addTransition(ChangeBounds()
                        .setDuration(100)
                        .setStartDelay(50))
                .addTransition(Fade()
                        .setDuration(100))

        unfocusSet.addTransition(ChangeBounds()
                        .setDuration(100))
                .addTransition(Fade()
                        .setDuration(100)
                        .setStartDelay(50))

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        Title = findViewById(R.id.title)
        Search = findViewById(R.id.search)
        List = findViewById(R.id.list)

        Search.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && (Title.visibility == View.VISIBLE) == hasFocus) {
                if (hasFocus) {
                    TransitionManager.beginDelayedTransition(findViewById(R.id.main), focusSet)
                    Title.visibility = View.GONE
                } else {
                    TransitionManager.beginDelayedTransition(findViewById(R.id.main), unfocusSet)
                    Title.visibility = View.VISIBLE
                }
            }
        }

//        Search.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
//            override fun onQueryTextChange(newText: String?): Boolean {
//            }
//
//            override fun onQueryTextSubmit(query: String?): Boolean {
//            }
//        })
    }

    override fun onBackPressed() {
        if (Search.hasFocus()) Search.let {
            it.clearFocus()
            it.isIconified = true
        } else super.onBackPressed()
    }
}
