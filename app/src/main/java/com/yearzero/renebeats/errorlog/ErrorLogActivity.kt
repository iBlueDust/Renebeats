package com.yearzero.renebeats.errorlog

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yearzero.renebeats.Directories
import com.yearzero.renebeats.R
import java.util.*

class ErrorLogActivity : AppCompatActivity() {

	private lateinit var Empty: TextView
	private lateinit var List: RecyclerView
	private lateinit var adapter: ErrorLogAdapter

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_errorlog)

		findViewById<ImageButton>(R.id.home).setOnClickListener{ onBackPressed() }

		val l = Directories.logsList
		Arrays.sort(l) { a, b -> b.compareTo(a)}
		Empty = findViewById(R.id.empty)
		if (l.isEmpty()) Empty.visibility = View.VISIBLE

		List = findViewById(R.id.list)
		List.isNestedScrollingEnabled = true
		List.layoutManager = LinearLayoutManager(this)
		adapter = ErrorLogAdapter(this, supportFragmentManager, l)
		List.adapter = adapter
//        List.addOnScrollListener(object : ErrorLogAdapter.EndlessScrollListener(manager) {
//            override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView) {
//                // Triggered only when new data needs to be appended to the list
//                // Add whatever code is needed to append new items to your AdapterView
//                loadNextDataFromApi(page)
//                // or loadNextDataFromApi(totalItemsCount);
//                return true // ONLY if more data is actually being loaded; false otherwise.
//            }
//        })
	}
}
