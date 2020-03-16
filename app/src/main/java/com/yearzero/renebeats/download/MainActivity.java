package com.yearzero.renebeats.download;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.youtube.model.SearchResult;
import com.google.common.io.BaseEncoding;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import com.tonyodev.fetch2.NetworkType;
import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.InternalArgs;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.download.ui.viewholder.BasicViewHolder;
import com.yearzero.renebeats.preferences.PreferenceActivity;
import com.yearzero.renebeats.preferences.Preferences;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements ServiceConnection, DownloadService.ClientCallbacks, View.OnClickListener, View.OnKeyListener, YoutubeQueryTask.Callbacks {
	private static final String TAG = "MainActivity";

	//TOOD: Prompt user on next start after unhandled error to share or copy

	private SlidingUpPanelLayout SlideUp;

	private TextInputEditText Search;
	private ImageButton QueryBtn, SettingsBtn, HistoryBtn;

	private View ErrorCard;
	private ImageView ErrorImg;
	private TextView ErrorTitle, ErrorMsg;
	private MaterialButton ErrorAction;

	private ImageView ScrollImg;
	private TextView InfoTitle;
	//    private Button InfoAction;

	private RecyclerView List;
	private DownloadAdapter adapter;
	private DownloadService service;

	private ImageButton Refresh, Dismiss;
	private RecyclerView QueryList;
	private ImageView OfflineImg;
	private TextView OfflineMsg, Title;
	private Button OfflineAction;

	private QueryAdapter queryAdapter;
	private LinearLayoutManager manager;

	private static List<SearchResult> queries;
	private static String query;

	// Update UI if the network profile has changed
	private BroadcastReceiver WifiListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (!Commons.isWifiConnected(context) && !Preferences.getMobiledata() && Commons.getDownloadNetworkType() != NetworkType.ALL) {
				ErrorCard.setVisibility(View.VISIBLE);
				ErrorImg.setImageResource(R.drawable.ic_no_wifi_black_96dp);
				ErrorTitle.setText(getString(R.string.no_wifi));
				ErrorMsg.setText(R.string.error_wifi_msg);
				ErrorAction.setText(R.string.error_wifi_action);
				ErrorAction.setOnClickListener(v -> {
					Commons.setDownloadNetworkType(true);
					ErrorCard.setVisibility(View.GONE);
				});

				//                OfflineAction.setText(R.string.retry);
				//                OfflineImg.setImageResource(R.drawable.ic_no_wifi_black_96dp);
				//                OfflineMsg.setText(R.string.no_internet);
				//                OfflineAction.setVisibility(View.VISIBLE);
				//                OfflineImg.setVisibility(View.VISIBLE);
				//                OfflineMsg.setVisibility(View.VISIBLE);
				//                OfflineAction.setOnClickListener(v -> Query());
			} else ErrorCard.setVisibility(View.GONE);

			queryAdapter.resetList(Collections.nCopies(Preferences.getQuery_amount(), null));
			if (query != null && !query.isEmpty())
				new YoutubeQueryTask(MainActivity.this, getPackageName())
						.setTimeout(Preferences.getTimeout())
						.setSignature(getSignature(getPackageManager(), getPackageName()))
						.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, query);
		}
	};
	private boolean WifiListenerRegistered;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//        if (getIntent() != null && getIntent().getBooleanExtra(InternalArgs.EXIT, false)) finish();

		setContentView(R.layout.activity_main);

		SlideUp = findViewById(R.id.slide_up);

		Search = findViewById(R.id.search);
		SettingsBtn = findViewById(R.id.settings);
		HistoryBtn = findViewById(R.id.history);
		QueryBtn = findViewById(R.id.query);

		ErrorCard = findViewById(R.id.error_card);
		ErrorImg = findViewById(R.id.error_img);
		ErrorTitle = findViewById(R.id.error_title);
		ErrorMsg = findViewById(R.id.error_msg);
		ErrorAction = findViewById(R.id.error_action);

		ScrollImg = findViewById(R.id.scroll_img);
		InfoTitle = findViewById(R.id.info_title);
		//        InfoAction = findViewById(R.id.info_action);

		List = findViewById(R.id.list);

		Refresh = findViewById(R.id.refresh);
		Dismiss = findViewById(R.id.dismiss);
		QueryList = findViewById(R.id.query_list);
		Title = findViewById(R.id.title);

		OfflineImg = findViewById(R.id.offline_img);
		OfflineMsg = findViewById(R.id.offline_msg);
		OfflineAction = findViewById(R.id.offline_action);

		queryAdapter = new QueryAdapter(this, new ArrayList<>());

		manager = new LinearLayoutManager(this);

		QueryList.setLayoutManager(new LinearLayoutManager(this));
		QueryList.setAdapter(queryAdapter);

		try {
			String[] perms = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
			if (!EasyPermissions.hasPermissions(this, perms))
				EasyPermissions.requestPermissions(this,
						getString(R.string.permission_rationale),
						Commons.PERM_REQUEST,
						perms
				);
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}

		Dismiss.setOnClickListener(view -> {
			SlideUp.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
			queries = null;
			query = null;
		});

		Search.setOnClickListener(view -> {
			if (SlideUp.getPanelState() == SlidingUpPanelLayout.PanelState.ANCHORED || SlideUp.getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED)
				SlideUp.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
		});
		Search.setOnKeyListener(this);
		QueryBtn.setOnClickListener(this);

		int index = getIntent().getIntExtra(InternalArgs.INDEX, -1);
		if (index > 0 && List != null && manager.findFirstCompletelyVisibleItemPosition() <= index && manager.findLastCompletelyVisibleItemPosition() >= index) {
			RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(this) {
				@Override
				protected int getVerticalSnapPreference() {
					return LinearSmoothScroller.SNAP_TO_START;
				}
			};
			smoothScroller.setTargetPosition(index);
			manager.startSmoothScroll(smoothScroller);
		}

		HistoryBtn.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, HistoryActivity.class)));
		SettingsBtn.setOnClickListener(v -> startActivity(new Intent(this, PreferenceActivity.class)));

		Search.requestFocus();
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

		if (queries != null && query != null) {
			Title.setText(query);
			Refresh.setOnClickListener(view -> Query());
			onComplete(queries);
		}

		UpdateInfo();
	}

	private void Query() {
		Query(query);
	}

	private void Query(String query) {
		OfflineSetVisibility(false);
		MainActivity.query = query;
		queries = null;

		WifiListener.onReceive(this, null);

		Title.setText(query);
		Refresh.setOnClickListener(view -> Query());
		if (QueryList.getLayoutManager() != null) QueryList.getLayoutManager().scrollToPosition(0);
	}

	private void UpdateInfo() {
		if (service != null) {
			List<Download> queued = service.getQueue();

			int waiting = 0;
			for (Download que : queued) {
				if (que.getStatus().getDownload() == Status.Download.NETWORK_PENDING) waiting++;
			}

			if (waiting > 0) {
				InfoTitle.setText(String.format(Commons.getLocale(), getString(R.string.main_header_waiting), waiting, waiting == 1 ? getString(R.string.main_header_is) : getString(R.string.main_header_are)));
				return;
			}

			int running = service.getRunning().size();
			if (running > 0) {
				InfoTitle.setText(String.format(Locale.ENGLISH, getString(R.string.main_header_running), running, running == 1 ? getString(R.string.main_header_is) : getString(R.string.main_header_are)));
				return;
			}

			int completed = service.getCompleted().size();
			if (completed > 0) {
				InfoTitle.setText(String.format(Locale.ENGLISH, getString(R.string.main_header_completed), completed, completed == 1 ? getString(R.string.main_header_has) : getString(R.string.main_header_have)));
				return;
			}

			if (queued.size() > 0) {
				InfoTitle.setText(String.format(Locale.ENGLISH, getString(R.string.main_header_queued), queued.size(), queued.size() == 1 ? getString(R.string.main_header_is) : getString(R.string.main_header_are)));
				return;
			}
		}
		InfoTitle.setText(getString(R.string.main_header_empty));
	}

	public void onComplete(java.util.List<SearchResult> results) {
		queries = results;
		OfflineSetVisibility(false);

		if (results == null) {
			OfflineSetVisibility(true);
			OfflineImg.setImageResource(R.drawable.ic_cloud_off_secondarydark_96dp);
			OfflineMsg.setText(R.string.main_error_connection_msg);
			OfflineAction.setText(R.string.retry);
			OfflineAction.setOnClickListener(v -> Query());
			queryAdapter.resetList();
		} else if (results.size() <= 0) {
			OfflineImg.setImageResource(R.drawable.ic_search_lightgray_96dp);
			OfflineMsg.setText(R.string.main_error_empty);
			OfflineAction.setText(R.string.back);
			OfflineAction.setOnClickListener(v -> onBackPressed());
			queryAdapter.resetList();
		} else queryAdapter.resetList(Query.castListXML(results));
	}

	public void onError(Exception e) {
		if (e instanceof GoogleJsonResponseException) {
			OfflineImg.setImageResource(R.drawable.ic_error_gray_24dp);
			OfflineMsg.setText(R.string.main_error_quota);
			OfflineAction.setText(R.string.main_error_quota_action);
			OfflineAction.setOnClickListener(v -> {
				try {
					// try starting the app if it is installed
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:")));
				} catch (ActivityNotFoundException ex) {
					// else, open the website instead
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.youtube.com")));
				}
			});
			OfflineSetVisibility(true);
		}
	}

	public void onTimeout() {
		queryAdapter.resetList();
		OfflineImg.setImageResource(R.drawable.ic_timer_lightgray_96dp);
		OfflineMsg.setText(R.string.main_error_timeout);
		OfflineAction.setText(R.string.retry);
		OfflineAction.setOnClickListener(v -> Query(query));

		OfflineSetVisibility(true);
	}

	private void OfflineSetVisibility(boolean visible) {
		int visibility = visible ? View.VISIBLE : View.GONE;

		OfflineImg.setVisibility(visibility);
		OfflineMsg.setVisibility(visibility);
		OfflineAction.setVisibility(visibility);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	@Override
	protected void onDestroy() {
		if (service != null) {
			if (service.removeCallbacks(this)) Log.i(TAG, "Service callback has been removed");
			else Log.e(TAG, "Failed to remove service callback");

			getApplication().onTerminate();
		}
		//        unbindService(this);
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		unbindService(this);
		if (WifiListenerRegistered) {
			WifiListenerRegistered = false;
			unregisterReceiver(WifiListener);
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		bindService(new Intent(this, DownloadService.class), this, 0);

		if (!WifiListenerRegistered) {
			WifiListenerRegistered = true;
			registerReceiver(WifiListener, new IntentFilter("android.net.wifi.STATE_CHANGE"));
		}
	}

	@Override
	public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
		service = ((DownloadService.LocalBinder) iBinder).getService();
		service.addCallbacks(this);
		adapter = new DownloadAdapter(this, service, List, getSupportFragmentManager());
		List.setLayoutManager(manager);
		List.setAdapter(adapter);
		ScrollImg.setVisibility(View.VISIBLE);
		//        int index = getIntent().getIntExtra(InternalArgs.INDEX, -1);
		//        if (index > 0 && manager.findFirstCompletelyVisibleItemPosition() <= index && manager.findLastCompletelyVisibleItemPosition() >= index) {
		//            RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(this) {
		//                @Override
		//                protected int getVerticalSnapPreference() {
		//                    return LinearSmoothScroller.SNAP_TO_START;
		//                }
		//            };
		//            smoothScroller.setTargetPosition(index);
		//            manager.startSmoothScroll(smoothScroller);
		//        }


		//        if (getIntent() != null) {
		//            int scrollIndex = getIntent().getIntExtra(InternalArgs.INDEX, -1);
		//            if (scrollIndex >= 0 && scrollIndex < adapter.getItemCount()) {
		//                float y = List.getY() + List.getChildAt(scrollIndex).getY();
		//                ((NestedScrollView) findViewById(R.id.main)).smoothScrollTo(0, (int) y);
		////                manager.scrollToPositionWithOffset(scrollIndex, 20);
		////                List.scrollToPosition(scrollIndex);
		//            }
		//        }
	}

	@Override
	public void onServiceDisconnected(ComponentName componentName) {
		service.removeCallbacks(this);
		service = null;
		if (adapter.getItemCount() > 0) ScrollImg.setVisibility(View.VISIBLE);
		Log.w(TAG, "Service has been disconnected");
	}

	@Override
	public void onProgress(Download args, long progress, long max, long size, boolean indeterminate) {
		adapter.onProgress(args, progress, max, size, indeterminate);
		UpdateInfo();
	}

	@Override
	public void onDone(Download args, boolean successful, Exception e) {
		adapter.onDone(args, successful, e);
		UpdateInfo();
	}

	@Override
	public void onWarn(Download args, String type) {
		adapter.onWarn(args, type);
		Log.w(TAG, '\'' + args.getTitle() + '\'' + ": " + type);
		Snackbar.make(findViewById(R.id.main), type, Snackbar.LENGTH_LONG).show();
	}

	@Override
	public void onClick(View view) {
		if(Search.getText() == null || Search.getText().toString().trim().isEmpty()) return;

		InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
		View v = getCurrentFocus();
		if (v == null) {
			v = new View(this);
		}
		imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

		String url = "";
		String query = Search.getText().toString();

		if(!(query.startsWith("http://") || query.startsWith("https://") || query.contains(" ")) && query.contains(".")) url = "https://";
		if(Pattern.compile("[\\w\\d]+\\.[\\w\\d]+", Pattern.CASE_INSENSITIVE).matcher(url).matches()) url += "www.";
		url += query;

		Matcher matcher = Pattern.compile("^https?://.*(?:youtu.be/|v/|u/\\w/|embed/|watch\\?v=)([^#&?]*).*$", Pattern.CASE_INSENSITIVE).matcher(url);
		if (matcher.matches()){
			Intent intent = new Intent(this, DownloadActivity.class);
			intent.putExtra(InternalArgs.DATA, new Query(matcher.group(1)));
			startActivity(intent);
		} else {
			Query(query);
			SlideUp.setPanelState(SlidingUpPanelLayout.PanelState.ANCHORED);
		}
	}

	@Override
	public void onBackPressed() {
		if (SlideUp != null && (SlideUp.getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED || SlideUp.getPanelState() == SlidingUpPanelLayout.PanelState.ANCHORED))
			SlideUp.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
	}

	@Override
	public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
		if (keyEvent.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_ENTER) {
			onClick(Search);
			return true;
		}
		return false;
	}

	//    @Override
	//    protected void onPause() {
	//        super.onPause();
	//        Shimmer.stopShimmer();
	//    }
	//
	//    @Override
	//    protected void onResume() {
	//        super.onResume();
	//        if (querying) Shimmer.startShimmer();
	//    }

	public static String getSignature(@NonNull PackageManager pm, @NonNull String packageName) {
		try {
			PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
			if (packageInfo == null
					|| packageInfo.signatures == null
					|| packageInfo.signatures.length == 0
					|| packageInfo.signatures[0] == null) {
				return null;
			}

			byte[] signature = packageInfo.signatures[0].toByteArray();
			MessageDigest md = MessageDigest.getInstance("SHA1");
			byte[] digest = md.digest(signature);

			return BaseEncoding.base16().lowerCase().encode(digest);
		} catch (NoSuchAlgorithmException | PackageManager.NameNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	//TODO: Use this
	static class DownloadSwipeCallback extends ItemTouchHelper.SimpleCallback {
		private Activity activity;
		private RecyclerView.Adapter<BasicViewHolder> adapter;

		DownloadSwipeCallback(Activity activity, RecyclerView.Adapter<BasicViewHolder> adapter) {
			super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
			this.activity = activity;
			this.adapter = adapter;
		}

		@Override
		public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
			return false;
		}

		public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
			if (adapter instanceof DownloadAdapter){
				DownloadAdapter downloadAdapter = (DownloadAdapter) adapter;
				downloadAdapter.blacklistAt(viewHolder.getAdapterPosition());
				String title = viewHolder instanceof BasicViewHolder && !((BasicViewHolder) viewHolder).getTitle().toString().trim().isEmpty() ? ((BasicViewHolder) viewHolder).getTitle().toString() : "a download";

				Snackbar.make(activity.getWindow().getDecorView().getRootView(), "Hid " + title, Snackbar.LENGTH_LONG)
						.setAction("Undo", a -> {
							downloadAdapter.unBlacklistAt(viewHolder.getAdapterPosition());

							Snackbar.make(activity.getWindow().getDecorView().getRootView(), "Unhid " + title, Snackbar.LENGTH_LONG)
									.show();
						}).show();
			}
		}
	}
}
