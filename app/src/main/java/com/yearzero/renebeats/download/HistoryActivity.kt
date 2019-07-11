package com.yearzero.renebeats.download

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.yearzero.renebeats.R
import org.apache.commons.lang3.ArrayUtils
import java.util.*

class HistoryActivity : AppCompatActivity() {

    companion object {
        @JvmStatic val TAG = "HistoryActivity"
    }

    private lateinit var Title: TextView
    private lateinit var Search: SearchView
    private lateinit var List: RecyclerView
    private lateinit var Swipe: SwipeRefreshLayout

    private val adapter = HistoryAdapter(this, supportFragmentManager)
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

        findViewById<ImageButton>(R.id.home).setOnClickListener{ onBackPressed() }

        Title = findViewById(R.id.title)
        Search = findViewById(R.id.search)
        List = findViewById(R.id.list)
        Swipe = findViewById(R.id.swipe)

        Search.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && (Title.visibility == View.VISIBLE) == hasFocus) {
                if (hasFocus) {
                    TransitionManager.beginDelayedTransition(findViewById(R.id.main), focusSet)
                    Title.visibility = View.GONE
                } else {
                    TransitionManager.beginDelayedTransition(findViewById(R.id.main), unfocusSet)
                    Title.visibility = View.VISIBLE
                }
            } else Title.visibility = if (hasFocus) View.GONE else View.VISIBLE
        }

//        Search.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
//            override fun onQueryTextChange(newText: String?): Boolean {
//            }
//
//            override fun onQueryTextSubmit(query: String?): Boolean {
//            }
//        })

        Swipe.setOnRefreshListener(this::refresh)
        Swipe.setColorSchemeResources(R.color.Accent, R.color.Secondary)

        List.layoutManager = LinearLayoutManager(this)
        List.adapter = adapter
        ItemTouchHelper(DownloadViewHolderController.DownloadSwipeCallback(this, adapter)).attachToRecyclerView(List)

        refresh()
    }

    private fun refresh() {
        val codes = History.listHist()
        codes.sortedWith (Comparator { a: Int, b: Int -> b - a})
        History.RetrieveTask()
                .setCallback(object: History.Callback<Int?, SparseArray<Array<HistoryLog>>> {
                    override fun onComplete(data: SparseArray<Array<HistoryLog>>) {
                        Swipe.isRefreshing = false
                        //TODO: Headers and stuff
                        val array = ArrayList<HistoryLog>()
                        for (i in 0..data.size()) {
                            val j = data.get(i)
                            if (j != null) array.addAll(j)
                        }

                        adapter.injectData(array.toArray(arrayOf()))
                    }
                    override fun onError(current: Int?, e: Exception) {
                        Swipe.isRefreshing = false
                        Log.e(TAG, "Failed to read history with code $current")
                    }
                })
                .execute(*ArrayUtils.toObject(codes))

        Swipe.isRefreshing = true
    }

    override fun onBackPressed() {
        if (Search.hasFocus()) Search.let {
            it.clearFocus()
            it.isIconified = true
        } else super.onBackPressed()
    }
}
