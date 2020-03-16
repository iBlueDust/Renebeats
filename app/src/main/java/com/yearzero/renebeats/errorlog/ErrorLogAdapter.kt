package com.yearzero.renebeats.errorlog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.yearzero.renebeats.Directories
import com.yearzero.renebeats.R
import java.util.*

class ErrorLogAdapter private constructor(private val context: Context, private val manager: FragmentManager, private val items: ArrayList<String> = ArrayList()) : RecyclerView.Adapter<ErrorLogAdapter.ViewHolder>() {
	companion object {
		@JvmStatic private val TAG: String = "ErrorLogAdapter"
	}

//    lateinit var items: ArrayList<Pair<String, String?>>

//    constructor(context: Context, recycler: RecyclerView, items: List<Pair<String, String?>>) : this(context, recycler) { this.items = ArrayList(items) }

//    constructor(context: Context, recycler: RecyclerView, items: Array<String>) : this(context, recycler) {
//        this.items = ArrayList()
//        for (i in items) this.items.add(Pair(i, null))
//    }

	//    constructor(context: Context, manager: FragmentManager, items: List<String>) : this(context, manager, ArrayList<String>(items))
	constructor(context: Context, manager: FragmentManager, items: Array<String>) : this(context, manager, ArrayList<String>(items.toList()))

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_errorlog, parent, false))
	override fun getItemCount(): Int = items.size
	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//        val first = items[position].first
//        holder.setTitle(first.dropLast(4))
		holder.setTitle(items[position].dropLast(4) + " UTC")
		holder.setOnClickListener(View.OnClickListener{
			ErrorLogDialog(Directories.readLog(items[holder.adapterPosition]), null)
					.show(manager, TAG)
		})
//        holder.setExpandOnceListener(View.OnClickListener{
//            val text: String? = Directories.readLog(first)
//            items[holder.adapterPosition] = Pair(first, text)
//            holder.setPayload(text ?: "Failed to read log file")
//        })
//        holder.setShareListener(View.OnClickListener{
//            val text = items[holder.adapterPosition].second
//            if (text != null) {
//                val intent = Intent(Intent.ACTION_SEND)
//                intent.type = "text/plain"
////                intent.putExtra(Intent.EXTRA_SUBJECT, "Send Error Log")
//                intent.putExtra(Intent.EXTRA_TEXT, text)
//                context.startActivity(Intent.createChooser(intent, "Send Error Log"))
//            }
//        })
//        holder.setCopyListener(View.OnClickListener{
//            val text = items[holder.adapterPosition].second
//            if (text != null) {
//                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip = ClipData.newPlainText("error log", text)
//                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
//            }
//        })
	}

	class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

		private val Title: TextView = itemView.findViewById(R.id.title)
//        private val Arrow: ImageView = itemView.findViewById(R.id.arrow)
//        private val Collapsible: ViewGroup = itemView.findViewById(R.id.collapsible)
//        private val Payload: TextView = itemView.findViewById(R.id.payload)
//        private val Copy: ImageButton = itemView.findViewById(R.id.copy)
//        private val Share: ImageButton = itemView.findViewById(R.id.share)

//        private var collapsed = true
//            set(value) {
//                var v = View.VISIBLE
//                var r = 90F
//                if (value) {
//                    v = View.GONE
//                    r = 0F
//                }
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && field != value)
//                    TransitionManager.beginDelayedTransition(itemView.findViewById(R.id.main))
//                Collapsible.visibility = v
//                Arrow.animate().setDuration(100L
//                ).rotation(r)
//                field = value
//            }

		fun setTitle(title: String) { Title.text = title }
//        fun setPayload(payload: String) { Payload.text = payload }
//        fun setCopyListener(listener: View.OnClickListener) = Copy.setOnClickListener(listener)
//        fun setShareListener(listener: View.OnClickListener) = Share.setOnClickListener(listener)

		fun setOnClickListener(listener: View.OnClickListener) = itemView.setOnClickListener(listener)

//        fun setExpandOnceListener(listener: View.OnClickListener) {
//            val l: View.OnClickListener = View.OnClickListener {
//                listener.onClick(it)
//                collapsed = !collapsed
//            }
//            Arrow.setOnClickListener(l)
//            Title.setOnClickListener(l)
//        }
	}
}
