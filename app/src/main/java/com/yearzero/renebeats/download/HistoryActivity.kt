package com.yearzero.renebeats.download

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
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
import com.google.android.material.snackbar.Snackbar
import com.yearzero.renebeats.R
import com.yearzero.renebeats.download.ui.viewholder.BasicViewHolder
import java.util.*


class HistoryActivity : AppCompatActivity(), History.RetrieveNTask.Callback {

    companion object {
        @JvmStatic val TAG = "HistoryActivity"
    }

    private lateinit var Title: TextView
    private lateinit var Search: SearchView
    private lateinit var List: RecyclerView
    private lateinit var Swipe: SwipeRefreshLayout
    private lateinit var Empty: TextView

//    private var task = History.RetrieveNTask()
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
        Empty = findViewById(R.id.empty)

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
        ItemTouchHelper(HistorySwipeCallback(this, adapter)).attachToRecyclerView(List)

        refresh()
    }

    private fun refresh() {
        History.RetrieveNTask().setCallback (this).execute(10)
        Swipe.isRefreshing = true
    }

    override fun onProgress(progress: Int, total: Int) {}
    override fun onComplete(it: Array<out HistoryLog>?) {
        Swipe.isRefreshing = false
        //TODO: Headers and stuff
        val array = ArrayList<HistoryLog>()
        if (it != null && it.isNotEmpty()) {
            for (i in it) array.add(i)
            Empty.visibility = View.GONE
        } else Empty.visibility = View.VISIBLE
        adapter.injectData(array.toArray(arrayOf()))
    }

    override fun onBackPressed() {
        if (Search.hasFocus()) Search.let {
            it.clearFocus()
            it.isIconified = true
        } else super.onBackPressed()
    }

    internal class HistorySwipeCallback(private val activity: Activity, private val adapter: RecyclerView.Adapter<BasicViewHolder>) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            if (adapter is DownloadAdapter) {
                val downloadAdapter = adapter as HistoryAdapter
                downloadAdapter.blacklistAt(viewHolder.adapterPosition)
                val title = if (viewHolder is BasicViewHolder && viewHolder.getTitle().toString().trim { it <= ' ' }.isNotEmpty()) viewHolder.getTitle().toString() else activity.getString(R.string.one_download)

                AlertDialog.Builder(activity)
                        .setTitle(R.string.history_delete)
                        .setMessage(R.string.history_delete_msg)
                        .setPositiveButton(R.string.history_delete_positive) { _, _ ->
                            Snackbar.make(activity.window.decorView.rootView, if(downloadAdapter.deleteAt(viewHolder.adapterPosition)) String.format(Locale.ENGLISH, activity.getString(R.string.history_deleted), title) else "Failed to delete", Snackbar.LENGTH_LONG).show()
                        }
                //TODO: Resume here
                Snackbar.make(activity.window.decorView.rootView, String.format(Locale.ENGLISH, activity.getString(R.string.history_hid), title), Snackbar.LENGTH_LONG)
                        .setAction(activity.getString(R.string.undo)) {
                            downloadAdapter.unBlacklistAt(viewHolder.adapterPosition)
                            Snackbar.make(activity.window.decorView.rootView, String.format(Locale.ENGLISH, activity.getString(R.string.history_unhid), title), Snackbar.LENGTH_LONG).show()
                        }.show()
            }
        }
    }
}
