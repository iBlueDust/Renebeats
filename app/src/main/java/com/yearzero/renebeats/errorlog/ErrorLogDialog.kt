package com.yearzero.renebeats.errorlog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.gmail.tylerfilla.widget.panview.PanView
import com.yearzero.renebeats.Commons
import com.yearzero.renebeats.R
import com.yearzero.renebeats.download.DownloadService
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*

class ErrorLogDialog() : DialogFragment() {

	private var contents: String? = null
	private var throwable: Throwable? = null

	private lateinit var Pan0: PanView
	//    private lateinit var Pan1: PanView
	private lateinit var Payload: TextView
//    private lateinit var Extra: TextView

	private lateinit var Close: Button
	private lateinit var Save: Button
	private lateinit var Copy: ImageButton
	private lateinit var Share: ImageButton

	private val loc_contents = "renebeats/errorlogdialog contents"
	private val loc_throwable = "renebeats/errorlogdialog throwable"
	private val loc_saved = "renebeats/errorlogdialog saved"

	private var init = false
	private var saved = false
		set(value) {
			if (init) {
				if (value) {
					Save.isEnabled = false
					Save.visibility = View.INVISIBLE
					Share.visibility = View.VISIBLE
					Copy.visibility = View.VISIBLE
				} else {
					Save.isEnabled = true
					Save.visibility = View.VISIBLE
					Share.visibility = View.GONE
					Copy.visibility = View.GONE
				}
			}
			field = value
		}

	constructor(contents: String?, throwable: Throwable?) : this() {
		this.contents = contents
		this.throwable = throwable
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		super.onCreate(savedInstanceState)
		val view = inflater.inflate(R.layout.dialog_info, container, false)

		Pan0 = view.findViewById(R.id.pan0)
//        Pan1 = view.findViewById(R.id.pan1)
		Payload = view.findViewById(R.id.payload)
//        Extra = view.findViewById(R.id.extra)

		Close = view.findViewById(R.id.close)
		Save = view.findViewById(R.id.save)
		Copy = view.findViewById(R.id.copy)
		Share = view.findViewById(R.id.share)

		Close.setOnClickListener { dismiss() }

		Copy.setOnClickListener {
			val manager: ClipboardManager = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
			manager.primaryClip = ClipData.newPlainText("error log", Payload.text)
			Toast.makeText(context, getString(R.string.clipboard_copy), Toast.LENGTH_SHORT).show()
		}
		Share.setOnClickListener {
			val intent = Intent(Intent.ACTION_SEND)
			intent.type = "text/plain"
//                intent.putExtra(Intent.EXTRA_SUBJECT, "Send Error Log")
			intent.putExtra(Intent.EXTRA_TEXT, Payload.text)
			context?.startActivity(Intent.createChooser(intent, "Send Error Log"))
		}

		init = true
		onViewStateRestored(savedInstanceState)
		update()
		return view
	}

	private fun update() {
		if (contents == null) {
//            Pan1.visibility = View.GONE
			saved = false
			if (throwable == null) {
				Payload.text = getString(R.string.dialog_errorlog_exception_unknown)
				Save.isEnabled = false
			} else {
				Save.isEnabled = true
				Save.setOnClickListener {
					if (!saved) {
						val re = Commons.LogExceptionReturn(throwable)
						if (re == null) //                            Pan1.visibility = View.GONE
						{
							Toast.makeText(context, getString(R.string.dialog_errorlog_save_failed), Toast.LENGTH_LONG).show()
						} else {
							Toast.makeText(context, getString(R.string.dialog_errorlog_save), Toast.LENGTH_SHORT).show()
							Payload.text = re
//                            Pan1.visibility = View.GONE
							saved = true
						}
					}
					Save.isEnabled = false
				}

				val writer = StringWriter()
				throwable!!.printStackTrace(PrintWriter(writer))
				Payload.text = writer.toString()

				if (throwable is DownloadService.ServiceException && (throwable as DownloadService.ServiceException).payload != null) {
					val extra = StringWriter()
					(throwable as DownloadService.ServiceException).payload.printStackTrace(PrintWriter(extra))
//                    Pan1.visibility = View.VISIBLE
//                    Extra.text = extra.toString()
					Payload.text = String.format(Locale.ENGLISH, "%s\n\n============| PAYLOAD |============\n\n%s", Payload.text.toString(), extra)
				}
			}
		} else {
			saved = true
			Save.isEnabled = true
			Payload.text = contents
//            Pan1.visibility = View.GONE
			Save.visibility = View.INVISIBLE
			Share.visibility = View.VISIBLE
			Copy.visibility = View.VISIBLE
		}
	}

	override fun onSaveInstanceState(outState: Bundle) {
		outState.putString(loc_contents, contents)
		outState.putSerializable(loc_throwable, throwable)
		outState.putSerializable(loc_saved, saved)
		super.onSaveInstanceState(outState)
	}

	override fun onViewStateRestored(savedInstanceState: Bundle?) {
		super.onViewStateRestored(savedInstanceState)
		if (savedInstanceState != null) {
			contents = savedInstanceState.getString(loc_contents)
			throwable = if (savedInstanceState.getSerializable(loc_throwable) is Throwable)
				savedInstanceState.getSerializable(loc_throwable) as Throwable? else null
			saved = savedInstanceState.getBoolean(loc_saved)
		}
	}
}