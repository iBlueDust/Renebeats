package com.yearzero.renebeats.download

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.AsyncTask
import android.os.Bundle
import android.os.CountDownTimer
import android.os.IBinder
import android.util.SparseArray
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.yearzero.renebeats.R
import com.yearzero.renebeats.download.ui.viewholder.BasicViewHolder
import com.yearzero.renebeats.preferences.Preferences
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayList


class HistoryActivity : AppCompatActivity(), HistoryRepo.RetrieveNTask.Callback, ServiceConnection {

    //TODO: HistoryActivity now works when a download is running (TESTING NEEDED)

    companion object {
        @JvmStatic val TAG = "HistoryActivity"
    }

    private lateinit var Title: TextView
//    private lateinit var Search: SearchView
    private lateinit var List: RecyclerView
    private lateinit var Swipe: SwipeRefreshLayout
    private lateinit var Empty: TextView

    private var service: DownloadService? = null

//    private var task = History.RetrieveNTask()
    private val adapter = HistoryAdapter(this)
//    private val focusSet: TransitionSet = TransitionSet()
//    private val unfocusSet: TransitionSet = TransitionSet()

    private var array: Array<out HistoryLog> = emptyArray()
    private var locale: Locale = Locale.ENGLISH

//    enum class SectionType { Date , Month, Year }

//    init {
//        focusSet.addTransition(ChangeBounds()
//                .setDuration(100)
//                .setStartDelay(50))
//                .addTransition(Fade()
//                        .setDuration(100))
//
//        unfocusSet.addTransition(ChangeBounds()
//                .setDuration(100))
//                .addTransition(Fade()
//                        .setDuration(100)
//                        .setStartDelay(50))
//
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        locale = Preferences.getMainLocale(this)
        findViewById<ImageButton>(R.id.home).setOnClickListener{ onBackPressed() }

        Title = findViewById(R.id.title)
//        Search = findViewById(R.id.search)
        List = findViewById(R.id.list)
        Swipe = findViewById(R.id.swipe)
        Empty = findViewById(R.id.empty)

//        Search.setOnQueryTextFocusChangeListener { _, hasFocus ->
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && (Title.visibility == View.VISIBLE) == hasFocus) {
//                if (hasFocus) {
//                    TransitionManager.beginDelayedTransition(Swipe, focusSet)
//                    Title.visibility = View.GONE
//                } else {
//                    TransitionManager.beginDelayedTransition(Swipe, unfocusSet)
//                    Title.visibility = View.VISIBLE
//                }
//            } else Title.visibility = if (hasFocus) View.GONE else View.VISIBLE
//        }
//        Search.setOnQueryTextListener(object: SearchView.OnQueryTextListener{
//            override fun onQueryTextSubmit(query: String?): Boolean { return false }
//
//            override fun onQueryTextChange(newText: String?): Boolean {
//                if (newText != null && newText.isNotBlank()) {
//                    // Filter results by keyword in 'filter' field
//                    val filter = newText.toString().trim().toLowerCase(locale)
//                    array.filter{ log ->
//                        log.getFilename(getString(R.string.sym_separator))?.toLowerCase(locale)?.contains(filter)
//                                ?: getString(R.string.sym_empty).toLowerCase(locale).contains(filter)
//                    }
//                    onComplete(array)
//                }
//                return true
//            }
//        })

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

    private var fetch: HistoryRepo.RetrieveNTask? = null

    private fun refresh() {
        if (fetch?.status == AsyncTask.Status.RUNNING)
            fetch!!.cancel(true)
        fetch = HistoryRepo.RetrieveNTask().setCallback(this)
        fetch!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, 10)

        Swipe.isRefreshing = true
    }

    override fun onProgress(progress: Int, total: Int) {}
    override fun onComplete(it: Array<out HistoryLog>?) {
        Swipe.isRefreshing = true
        //TODO: Headers and stuff

        array = it ?: emptyArray()

        // Segment the History logs if it is not empty
        if (it!!.isNotEmpty()) {
            if (service == null) SegmentDataTask(this, adapter).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, *it)
            else SegmentDataTask(this, adapter, listOf(service!!.queue, service!!.running).flatten()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, *it)
        }
        else {
            Empty.visibility = View.VISIBLE
            Swipe.isRefreshing = false
        }
    }

    override fun onTimeout() {
        Snackbar.make(Swipe, getString(R.string.history_fetch_timeout), Snackbar.LENGTH_LONG).show()
        Swipe.isRefreshing = false
    }

//    override fun onBackPressed() = if (Search.hasFocus())
//            Search.let {
//                it.clearFocus()
//                it.isIconified = true
//            }
//        else super.onBackPressed()

    override fun onResume() {
        super.onResume()
        bindService(Intent(this, DownloadService::class.java), this, 0)
    }

    override fun onPause() {
        unbindService(this)
        super.onPause()
    }

    override fun onDestroy() {
        if (service != null) application.onTerminate()
        super.onDestroy()
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        this.service = (service as DownloadService.LocalBinder).service
        refresh()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

    private class SegmentDataTask private constructor(private val activity: WeakReference<HistoryActivity>, private val adapter: HistoryAdapter) :
            AsyncTask<HistoryLog, Void, List<HistorySection>>() {
        constructor(activity: HistoryActivity, adapter: HistoryAdapter) : this(WeakReference(activity), adapter)
        constructor(activity: HistoryActivity, adapter: HistoryAdapter, serviceList: List<Download>) : this(WeakReference(activity), adapter) {
            this.serviceList = serviceList
        }

        private var serviceList = emptyList<Download>()

//        companion object {
//            @JvmStatic val THRESHOLD = 10
//        }

        override fun onPreExecute() {
            val act = activity.get()
            act?.findViewById<SwipeRefreshLayout>(R.id.swipe)?.isRefreshing = false
            object : CountDownTimer(Preferences.timeout.toLong(), Preferences.timeout.toLong()) {
                override fun onTick(l: Long) {}

                override fun onFinish() {
                    if (status == Status.RUNNING) {
                        cancel()
                        val swipe = act?.findViewById<SwipeRefreshLayout>(R.id.swipe)
                        if (swipe != null) {
                            swipe.isRefreshing = false
                            Snackbar.make(swipe, act.getString(R.string.history_segment_timeout), Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }.start()
            super.onPreExecute()
        }

        override fun onPostExecute(result: List<HistorySection>) {
            adapter.clearAll()
            adapter.addSection(*result.toTypedArray())

            val act = activity.get()
            if (act != null) {
                act.findViewById<SwipeRefreshLayout>(R.id.swipe)?.isRefreshing = false
                act.findViewById<View>(R.id.empty)?.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
            }
            super.onPostExecute(result)
        }

        override fun doInBackground(vararg params: HistoryLog): List<HistorySection>? {
            val result = SparseArray<Node>()

            // Split data into year groups
//            for (log in params) {
//                val calendar = Calendar.getInstance()
//                calendar.time = log.date
//                val year = calendar.get(Calendar.YEAR)
//                val yearCode = getCode(year)
//                val node = result.get(yearCode, null)
//                if (node == null) result.append(yearCode, Node(ArrayList(listOf(log)), SectionType.Year, year= year))
//                else node.logs.add(log)
//            }
// // Check for any year groups exceeding the THRESHOLD, if so, split to month groups
//            var i = 0
//            while (i in 0 until result.size()) {
//                val node = result.valueAt(i++)
//                if (node.logs.size > THRESHOLD) {
//                    result.removeAt(i--)
//                    for (log in node.logs) {
//                        val calendar = Calendar.getInstance()
//                        calendar.time = log.date
//                        val month = calendar.get(Calendar.MONTH)
//                        val code = getCode(node.year, month)
//                        val child = result.get(code, null)
//                        if (child == null) result.append(code, Node(ArrayList(listOf(log)), SectionType.Month, year= node.year, month= month))
//                        else child.logs.add(log)
//                    }
//                }
//            }
// // Check for any month groups exceeding THRESHOLD, if so, split to date groups
//            var j = 0
//            while (j in 0 until result.size()) {
//                val node = result.valueAt(j++)
//                if (node.logs.size > THRESHOLD) {
//                    result.removeAt(j--)
//                    for (log in node.logs) {
//                        val calendar = Calendar.getInstance()
//                        calendar.time = log.date
//                        val date = calendar.get(Calendar.DATE)
//                        val code = getCode(node.year, node.month, date)
//                        val child = result.get(code, null)
//                        if (child == null) result.append(code, Node(ArrayList(listOf(log)), SectionType.Date, year= node.year, month= node.month, date= date))
//                        else child.logs.add(log)
//                    }
//                }
//            }

            // Split to day groups immediately
            mainLoop@ for (log in params) {
                for (running in serviceList)
                    if (running == log)
                        break@mainLoop

                val calendar = Calendar.getInstance()
                calendar.time = log.date
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val date = calendar.get(Calendar.DATE)
                val yearCode = getCode(year, month, date)
                val node = result.get(yearCode, null)
                if (node == null) result.append(yearCode, Node(ArrayList(listOf(log)), /*SectionType.Date,*/ year= year, month= month, date= date))
                else node.logs.add(log)
            }

            // Convert all groups into HistorySections
            val sections = ArrayList<HistorySection>()
            for (n in 0 until result.size()) {
                val context = activity.get()
                if (context != null) {
                    result.valueAt(n).logs.sortWith(Comparator { a, b -> b.assigned.compareTo(a.assigned)})
                    sections.add(HistorySection(context, result.valueAt(n)))
                }
            }
            return sections.reversed()
        }

        // Packed integer so that if sorted, the corresponding values will also be sorted by the group's collective date
        @Keep
        private fun getCode(year: Int, month: Int = 0, day: Int = 0): Int = (year shl 9) + (month * 32) + day
    }

    // Those 'groups'
    open class Node(val logs: ArrayList<HistoryLog>, /*val type: SectionType = SectionType.Date,*/ val year: Int = 0, val month: Int = 0, val date: Int = 0)

    internal class HistorySwipeCallback(private val activity: HistoryActivity, private val adapter: HistoryAdapter) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            if (viewHolder is BasicViewHolder) {
                val item = adapter.getItemAt(viewHolder.adapterPosition)
                HistoryRepo.deleteRecord(item)
                activity.refresh()
                val title = if (viewHolder.getTitle().toString().trim { it <= ' ' }.isNotEmpty()) viewHolder.getTitle().toString() else activity.getString(R.string.one_download)

                Snackbar.make(activity.window.decorView.rootView, String.format(Locale.ENGLISH, activity.getString(R.string.history_hid), title), Snackbar.LENGTH_LONG)
                        .setAction(activity.getString(R.string.undo)) {
                            HistoryRepo.record(item)
                            activity.refresh()
                            Snackbar.make(activity.window.decorView.rootView, String.format(Locale.ENGLISH, activity.getString(R.string.history_unhid), title), Snackbar.LENGTH_LONG).show()
                        }.show()
            }
        }

        override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            return if (viewHolder.adapterPosition < 0 || adapter.isItemHeader(viewHolder.adapterPosition)) 0 else ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        }
    }
}
