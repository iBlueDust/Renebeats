package com.yearzero.renebeats.download;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.InternalArgs;
import com.yearzero.renebeats.download.ui.DownloadDialog;
import com.yearzero.renebeats.download.ui.viewholder.BasicViewHolder;
import com.yearzero.renebeats.download.ui.viewholder.FailedViewHolder;
import com.yearzero.renebeats.download.ui.viewholder.QueueViewHolder;
import com.yearzero.renebeats.download.ui.viewholder.RunningViewHolder;
import com.yearzero.renebeats.download.ui.viewholder.SuccessViewHolder;
import com.yearzero.renebeats.errorlog.ErrorLogDialog;
import com.yearzero.renebeats.preferences.Preferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class DownloadAdapter extends RecyclerView.Adapter<BasicViewHolder> implements DownloadService.ClientCallbacks {
    private static final String TAG = "DownloadAdapter";

    private Context context;
    private DownloadService service;
    private RecyclerView recycler;
    private DownloadDialog dialog;
    private FragmentManager manager;
    private ArrayList<Integer> blacklist = new ArrayList<>();

    // TODO: Slide ViewHolders for more options

    DownloadAdapter(Context context, DownloadService service, RecyclerView recycler, FragmentManager manager) {
        this.context = context;
        this.service = service;
        this.recycler = recycler;
        this.manager = manager;
        service.Sanitize();
    }

    @NonNull
    @Override
    public BasicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return DownloadViewHolderController.getViewHolderByType(context, parent, viewType);
    }

    @Override
    public int getItemCount() {
        return getServiceDownloads().size();
    }

    @Override
    public void onBindViewHolder(@NonNull BasicViewHolder holder, int position) {
        List<Download> list = getServiceDownloads();
        if (position < 0 || position >= list.size()) return;

        int type = getItemViewType(position, list);
        Status.Download dl = type >>> 20 >= Status.Download.values().length ? null : Status.Download.values()[type >>> 20];
        Status.Convert cv = ((type >>> 10) & 0x3FF) >= Status.Convert.values().length ? null : Status.Convert.values()[(type >>> 10) & 0x3FF];
        Boolean md = null;
        int i = type & 0x3FF;
        if (i == Boolean.TRUE.hashCode())
            md = true;
        else if (i == Boolean.FALSE.hashCode())
            md = false;

        if (!(dl == list.get(position).getStatus().getDownload() || cv == list.get(position).getStatus().getConvert() || md == list.get(position).getStatus().getMetadata()))
            Log.w(TAG, "ViewHolder type does not match Download type. Defaulting to follow ViewHolder type");

        InitializeViewHolder(holder, list.get(position));
    }

    private void InitializeViewHolder(BasicViewHolder holder, final Download args) {
        holder.setTitle(args.getFilename() == null ? "N/A" : args.getFilename());
//        holder.setIsRecyclable(true);

        //TODO: Invalid has never been tested
        if (args.getStatus().isInvalid()) {
            FailedViewHolder n = (FailedViewHolder) holder;
            n.setStatus("Invalid download parameters. Please try again.");
        } else if (args.getStatus().isFailed()) {
            //region FailedViewHolder
            FailedViewHolder n = (FailedViewHolder) holder;
            n.setStatus("Failed");

            if (args.getException() instanceof IllegalArgumentException)
                n.setStatus("Download request is invalid");
            else if (args.getException() instanceof DownloadService.ServiceException) {
                DownloadService.ServiceException e = (DownloadService.ServiceException) args.getException();
                n.setStatus(e.getMessage());
            }

            //TODO: Instead of switch statements based on status, what if it was based on ViewHolder type?

            n.setInfoListener(v -> {
                ErrorLogDialog dialog;
                if (Preferences.getAlways_log_failed()) {
                    String name = args.getException() == null ? null : Commons.LogExceptionReturn(args, args.getException());

                    if (name == null) {
                        Toast.makeText(context, "Failed to autolog download error", Toast.LENGTH_LONG).show();
                        return;
                    } else dialog = new ErrorLogDialog(name, null);
                } else dialog = new ErrorLogDialog(null, args.getException());

                dialog.show(manager, TAG);
            });

            n.setRetryListener(v -> {
                Intent intent = new Intent(context, DownloadActivity.class);
                intent.putExtra(InternalArgs.DATA, args);
                context.startActivity(intent);
            });
            //endregion
        } else if (args.getStatus().isCancelled()) {
            if (holder instanceof FailedViewHolder) {
                FailedViewHolder h = (FailedViewHolder) holder;
                h.setStatus("Cancelled");
                h.setInfoVisible(false);
                h.setRetryListener(v -> {
                    Intent intent = new Intent(context, DownloadActivity.class);
                    intent.putExtra(InternalArgs.DATA, args);
                    context.startActivity(intent);
                });
            } else {
                notifyItemChanged(holder.getAdapterPosition());
                Log.w(TAG, "Cancelled case got a non-FailedViewHolder");
            }
        } else if (args.getStatus().getDownload() != null) {
            try {
                //region Status Main
                switch (args.getStatus().getDownload()) {
                    case QUEUED:
                    case NETWORK_PENDING:
                        QueueViewHolder g = (QueueViewHolder) holder;
                        g.setStatus("Waiting for download");
                        break;
                    case RUNNING:
                        RunningViewHolder i = (RunningViewHolder) holder;
                        i.setStatus(String.format(Locale.ENGLISH, "%s of %s downloaded", Commons.FormatBytes(args.getCurrent()), Commons.FormatBytes(args.getTotal())));
                        i.setProgress((int) args.getCurrent(), (int) args.getTotal(), args.isIndeterminate());
                        i.setCancelListener(v -> service.cancel(args.getId()));
                        break;
//                    case NETWORK_PENDING:
//                        break;
//                    case PAUSED:
//                        RunningViewHolder m = (RunningViewHolder) holder;
//                        m.setStatus("Download Paused");
//                        m.setPause(true);
//                        m.setProgress((int) args.current, (int) args.total, args.indeterminate);
//                        break;
                    case COMPLETE:
                        if (args.getStatus().getConvert() == null) break;
                        switch (args.getStatus().getConvert()) {
                            case QUEUED:
                                QueueViewHolder h = (QueueViewHolder) holder;
                                h.setStatus("Download Completed, waiting for conversion");
                                break;
                            case RUNNING:
                                RunningViewHolder j = (RunningViewHolder) holder;
                                j.setCancelListener(v -> service.cancel(args.getId()));
                                j.setStatus(String.format(Locale.ENGLISH, "%s converted", Commons.FormatBytes(args.getSize())));
                                j.setProgress((int) args.getCurrent(), (int) args.getTotal(), false);
                                break;
//                            case PAUSED:
//                                RunningViewHolder l = (RunningViewHolder) holder;
//                                l.setStatus("Paused");
//
//                                l.setPause(true);
//                                l.setProgress(0, 0, true);
//                                break;
                            case SKIPPED:
                            case COMPLETE:
                                if (args.getStatus().getMetadata() == null) {
                                    RunningViewHolder n = (RunningViewHolder) holder;
                                    n.setCancelListener(v -> service.cancel(args.getId()));
                                    n.setStatus("Applying metadata");
                                    n.setProgress(0, 0, true);
                                } else {
                                    SuccessViewHolder o = (SuccessViewHolder) holder;
                                    o.setStatus("SUCCESS");

                                    if (!(args.getAssigned() == null || args.getCompleteDate() == null)) {
                                        String text = "Took ";

                                        long elapsed = args.getCompleteDate().getTime() - args.getAssigned().getTime();
                                        short hour = (short) (elapsed / 3600_000);
                                        short minute = (short) ((elapsed / 60_000) % 60);
                                        short second = (short) ((elapsed / 1000) % 60);

                                        if (hour > 0) {
                                            text += hour + "h ";
                                            if (minute < 10) text += "0";
                                            text += minute + "m ";
                                            if (second < 10) text += "0";
                                        } else if (minute > 0) {
                                            text += minute + "m ";
                                            if (second < 10) text += "0";
                                        }
                                        text += second + "s";
                                        o.setDate(text);
                                    }

                                    o.setRetryListener(v -> {
                                        Intent intent = new Intent(context, DownloadActivity.class);
                                        intent.putExtra(InternalArgs.DATA, args);
                                        context.startActivity(intent);
                                    });
//                                    Calendar ass = Calendar.getInstance();
//                                    ass.setTime(args.getCompleteDate());
//
//                                    Calendar yesterday = Calendar.getInstance();
//                                    yesterday.setTimeInMillis(System.currentTimeMillis());
//                                    yesterday.add(Calendar.DAY_OF_YEAR, -1);
//
//                                    if (DateUtils.isToday(args.getCompleteDate().getTime()))
//                                        o.setDate("Assigned at " + new SimpleDateFormat("hh:mm:ss a", Locale.ENGLISH).format(args.getCompleteDate()));
//                                    else if (yesterday.get(Calendar.YEAR) == ass.get(Calendar.YEAR) && yesterday.get(Calendar.DAY_OF_YEAR) == ass.get(Calendar.DAY_OF_YEAR))
//                                        o.setDate("Assigned yesterday");
//                                    else
//                                        o.setDate("Assigned on " + new SimpleDateFormat("EEE, d MMMM yyyy", Locale.ENGLISH).format(args.getCompleteDate()));
                                }
                        }
                    case CANCELLED:
                        break;
                    case FAILED:
                        break;
                }
                //endregion
            } catch (ClassCastException e) {
                Log.e(TAG, "Class cast exception");
                recycler.post(() -> notifyItemChanged(holder.getAdapterPosition(), null));
            }
        }

        holder.setOnClickListener(v -> {
            dialog = new DownloadDialog()
                    .setDownload(args);
            dialog.show(manager, TAG);
        });

        holder.setOnLongClickListener(v -> {
            dialog = new DownloadDialog()
                    .setDownload(args)
                    .setSecret(true);
            dialog.show(manager, TAG);
            return true;
        });
    }

    @Override
    public void onProgress(Download args, long progress, long max, long size, boolean indeterminate) {
        int index = getServiceDownloads().indexOf(args);
        /*if (index < 0)
            notifyDataSetChanged();
        else*/ //UpdateAtPosition(index, args);
        if (index >= 0) {
            if (Looper.getMainLooper().getThread().equals(Thread.currentThread()))
                UpdateAtPosition(index, args);
            else Log.e(TAG, "Non-UI Thread detected");
        } else Log.w(TAG, "onProgress indexOf returned -1");
        if (dialog != null) dialog.UpdatePartial(args);
    }

    @Override
    public void onDone(Download args, boolean successful, Exception e) {
        int index = getServiceDownloads().indexOf(args);

        if (index < 0) notifyDataSetChanged();
        else notifyItemChanged(index);
        if (dialog != null) dialog.UpdatePartial(args);
    }

    @Override
    public void onWarn(Download args, String type) {
        Log.w(TAG, type);
    }

    @Override
    public int getItemViewType(int position) {
        return getItemViewType(position, getServiceDownloads());
    }

    private int getItemViewType(int position, List<Download> array) {
        if (array.size() <= position || array.get(position) == null) {
            notifyDataSetChanged();
            return -1;
        }
        return DownloadViewHolderController.getViewHolderType(array.get(position).getStatus());
    }

    private List<Download> getServiceDownloads() {
//        service.Sanitize();
        ArrayList<Download> list = new ArrayList<>(Arrays.asList(service.getAll()));
        for (int i = 0; i < list.size(); i++)
            if (blacklist.contains(list.get(i).getId()))
                list.remove(i--);
        Collections.sort(list, (a, b) -> a.getAssigned() == null || b.getAssigned() == null ? 0 : b.getAssigned().compareTo(a.getAssigned()));
        return list;
    }

    void blacklistAt(int index) {
        blacklist.add(getServiceDownloads().get(index).getId());
        notifyItemRemoved(index);
    }

    void unBlacklistAt(int index) {
        blacklist.remove((Integer) getServiceDownloads().get(index).getId());
        notifyItemInserted(index);
    }

    private void UpdateAtPosition(int position, Download download) {
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
        if (holder instanceof BasicViewHolder) InitializeViewHolder((BasicViewHolder) holder, download);
    }
}
