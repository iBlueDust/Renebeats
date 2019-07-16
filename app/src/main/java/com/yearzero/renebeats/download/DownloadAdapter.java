package com.yearzero.renebeats.download;

import android.app.Dialog;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.download.ui.DownloadDialog;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.download.ui.viewholder.BasicViewHolder;
import com.yearzero.renebeats.download.ui.viewholder.FailedViewHolder;
import com.yearzero.renebeats.download.ui.viewholder.QueueViewHolder;
import com.yearzero.renebeats.download.ui.viewholder.RunningViewHolder;
import com.yearzero.renebeats.download.ui.viewholder.SuccessViewHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Locale;

public class DownloadAdapter extends RecyclerView.Adapter<BasicViewHolder> implements DownloadService.ClientCallbacks {
    private static final String TAG = "DownloadAdapter";
    
    private Context context;
    private DownloadService service;
    private RecyclerView recycler;
    private DownloadDialog dialog;
    private FragmentManager manager;

    // TODO: Implement pause and cancel
    // TODO: Slide ViewHolders for more options

    public DownloadAdapter(Context context, DownloadService service, RecyclerView recycler, FragmentManager manager) {
        this.context = context;
        this.service = service;
        this.recycler = recycler;
        this.manager = manager;
    }

    @NonNull
    @Override
    public BasicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return DownloadController.getViewHolderByType(context, parent, viewType);
    }

    @Override
    public int getItemCount() {
        return service.getAll().size();
    }

    @Override
    public void onBindViewHolder(@NonNull BasicViewHolder holder, int position) {
        Download[] all = getServiceDownloads();

        if (position < 0 || position >= all.length) return;

        int type = getItemViewType(position, all);
        Status.Download dl = type >>> 20 >= Status.Download.values().length ? null : Status.Download.values()[type >>> 20];
        Status.Convert cv = ((type >>> 10) & 0x3FF) >= Status.Convert.values().length ? null : Status.Convert.values()[(type >>> 10) & 0x3FF];
        Boolean md = null;
        int i = type & 0x3FF;
        if (i == Boolean.TRUE.hashCode())
            md = true;
        else if (i == Boolean.FALSE.hashCode())
            md = false;

        if (!(dl == all[position].status.download || cv == all[position].status.convert || md == all[position].status.metadata)) Log.w(TAG, "ViewHolder type does not match Download type. Defaulting to follow ViewHolder type");

        InitializeViewHolder(holder, all[position]);
    }

    private void InitializeViewHolder(BasicViewHolder holder, final Download args) {
        holder.setTitle(args.title + (args.title == null || args.title.isEmpty() || args.artist == null || args.artist.isEmpty() ? "" : " - ") + args.artist);
//        holder.setIsRecyclable(true);

        if (args.status.isInvalid()) {
            FailedViewHolder n = (FailedViewHolder) holder;
            n.setStatus("Invalid download parameters. Please try again.");
        } else if (args.status.isFailed()) {
            //region FailedViewHolder
            FailedViewHolder n = (FailedViewHolder) holder;
            n.setStatus("Failed");

            if (args.exception instanceof IllegalArgumentException)
                n.setStatus("Download request is invalid");
            else if (args.exception instanceof DownloadService.ServiceException) {
                DownloadService.ServiceException e = (DownloadService.ServiceException) args.exception;
                n.setStatus(e.getMessage());

                //                //region ServiceException switch
                //                switch (e.getDownload()) {
                //                    case QUEUED:
                //                        n.setStatus("Failed while queueing for download");
                //                        break;
                //                    case RUNNING:
                //                        if (args.total > 0L) {
                //                            double c = args.current;
                //                            double t = args.total;
                //
                //                            short ci = 0;
                //                            short ti = 0;
                //
                //                            while (c >= 1000d && ci < Commons.suffix.size) {
                //                                c /= 1000d;
                //                                ci++;
                //                            }
                //
                //                            while (t >= 1000d && ti < Commons.suffix.size) {
                //                                t /= 1000d;
                //                                ti++;
                //                            }
                //
                //                            n.setStatus(String.format(Locale.ENGLISH, "Failed while downloading. %.2f%s of %.2f%s completed.", c, Commons.suffix[ci], t, Commons.suffix[ti]));
                //                        } else n.setStatus("Failed while downloading");
                //                        break;
                //                    case PAUSED:
                //                        if (args.current > 0L) {
                //                            double pc = args.current;
                //                            double pt = args.total;
                //
                //                            short pci = 0;
                //                            short pti = 0;
                //
                //                            while (pc >= 1000d && pci < Commons.suffix.size) {
                //                                pc /= 1000d;
                //                                pci++;
                //                            }
                //
                //                            while (pt >= 1000d && pti < Commons.suffix.size) {
                //                                pt /= 1000d;
                //                                pti++;
                //                            }
                //
                //                            n.setStatus(String.format(Locale.ENGLISH, "An exception occurred while download was paused. %.2f%s of %.2f%s completed.", pc, Commons.suffix[pci], pt, Commons.suffix[pti]));
                //                        } else
                //                            n.setStatus("An exception occurred while download was paused");
                //                        break;
                //                    default:
                //                        switch(((DownloadService.ServiceException) args.exception).getConvert()) {
                //                            case QUEUED:
                //                                n.setStatus("An exception occurred while queueing for conversion");
                //                                break;
                //                            case PAUSED:
                //                                n.setStatus("An exception occurred while paused before conversion");
                //                                break;
                //                            case SKIPPED:
                //                                n.setStatus("An unknown exception occurred (skipped)");
                //                                break;
                //                            case RUNNING:
                //                                double v = args.current;
                //                                short vi = 0;
                //
                //                                while (v >= 1000d && vi < Commons.suffix.size) {
                //                                    v /= 1000d;
                //                                    vi++;
                //                                }
                //
                //                                n.setStatus("Failed to convert file");
                //                                break;
                //                            default:
                //                                n.setStatus("An exception occurred and was unhandled");
                //                        }
                //                }
                //                //endregion
            }
//            n.setInfoOnClickListener(v -> {
            n.setAction(v -> {
                final Dialog info = new Dialog(context);
                info.requestWindowFeature(Window.FEATURE_NO_TITLE);
                info.setContentView(R.layout.dialog_info);

                if (args.exception == null) {
                    ((TextView) info.findViewById(R.id.payload)).setText("Unknown exception");
                    info.findViewById(R.id.save).setEnabled(false);
                } else {
                    Writer mainWriter = new StringWriter();
                    args.exception.printStackTrace(new PrintWriter(mainWriter));

                    if (args.exception instanceof DownloadService.ServiceException && ((DownloadService.ServiceException) args.exception).getPayload() != null) {
                        Writer extraWriter = new StringWriter();
                        ((DownloadService.ServiceException) args.exception).getPayload().printStackTrace(new PrintWriter(extraWriter));

                        info.findViewById(R.id.horizontalScrollView2).setVisibility(View.VISIBLE);
                        ((TextView) info.findViewById(R.id.extra)).setText(mainWriter.toString());
                    }

                    ((TextView) info.findViewById(R.id.payload)).setText(mainWriter.toString());
                }

                info.findViewById(R.id.save).setOnClickListener(v1 -> {
                    if (args.exception != null && Commons.LogException(args, args.exception))
                        Toast.makeText(context, "Failed to save log", Toast.LENGTH_LONG).show();
                });

                info.findViewById(R.id.close).setOnClickListener(v12 -> info.dismiss());
            });
            //endregion
        } else if (args.status.isCancelled())
            ((QueueViewHolder) holder).setStatus("Cancelled");
        else if (args.status.download != null) {

            try {
                //region Status Main
                switch (args.status.download) {
                    case QUEUED:
                        QueueViewHolder g = (QueueViewHolder) holder;
                        g.setStatus("Waiting for download");
                        break;
                    case RUNNING:
                        RunningViewHolder i = (RunningViewHolder) holder;
                        i.setStatus(String.format(Locale.ENGLISH, "%s of %s downloaded", Commons.FormatBytes(args.current), Commons.FormatBytes(args.total)));
                        i.setProgress((int) args.current, (int) args.total, args.indeterminate);
                        break;
                    case NETWORK_PENDING:
                        break;
                    case PAUSED:
                        RunningViewHolder m = (RunningViewHolder) holder;
                        m.setStatus("Download Paused");
                        m.setPaused(true);
                        m.setProgress((int) args.current, (int) args.total, args.indeterminate);
                        break;
                    case COMPLETE:
                        if (args.status.convert == null) break;
                        switch (args.status.convert) {
                            case QUEUED:
                                QueueViewHolder h = (QueueViewHolder) holder;
                                h.setStatus("Download Completed, waiting for conversion");
                                break;
                            case RUNNING:
                                RunningViewHolder j = (RunningViewHolder) holder;
                                
                                j.setStatus(String.format(Locale.ENGLISH, "%s converted", Commons.FormatBytes(args.size)));
                                j.setProgress((int) args.current, (int) args.total, false);
                                break;
                            case PAUSED:
                                RunningViewHolder l = (RunningViewHolder) holder;
                                l.setStatus("Paused");

                                l.setPaused(true);
                                l.setProgress(0, 0, true);
                                break;
                            case SKIPPED:
                            case COMPLETE:
                                if (args.status.metadata == null) {
                                    RunningViewHolder n = (RunningViewHolder) holder;
                                    n.setStatus("Applying metadata");
                                    n.setProgress(0, 0, true);
                                } else {
                                    SuccessViewHolder o = (SuccessViewHolder) holder;
                                    o.setStatus("SUCCESS");

                                    if (!(args.assigned == null || args.completed == null)) {
                                        String text = "Took ";

                                        long elapsed = args.completed.getTime() - args.assigned.getTime();
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
                notifyItemChanged(holder.getAdapterPosition());
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
        Download[] array = getServiceDownloads();

        int index = -1;

        for (int i = 0; i < array.length; i++) {
            if (array[i].hashCode() == args.hashCode()) {
                index = i;
                break;
            }
        }

        /*if (index < 0)
            notifyDataSetChanged();
        else*/ UpdateAtPosition(index, args);
        if (dialog != null) dialog.UpdatePartial(args);
    }

    @Override
    public void onDone(Download args, boolean successful, Exception e) {
        Download[] array = getServiceDownloads();

        int index = -1;

        for (int i = 0; i < array.length; i++) {
            if (array[i].hashCode() == args.hashCode()) {
                index = i;
                break;
            }
        }

        if (index < 0) notifyDataSetChanged();
        else notifyItemChanged(index);
        if (dialog != null) dialog.UpdatePartial(args);
    }

    @Override
    public void onWarn(Download args, String type) { }

    @Override
    public int getItemViewType(int position) {
        return getItemViewType(position, getServiceDownloads());
    }

    private int getItemViewType(int position, Download[] array) {
        if (array.length <= position || array[position] == null) {
            notifyDataSetChanged();
            return -1;
        }
        return DownloadController.getViewHolderType(array[position].status);
    }

    private Download[] getServiceDownloads() {
        service.Sanitize();
        Download[] array = service.getAll().toArray(new Download[0]);
        Arrays.sort(array, (a, b) -> a.assigned == null || b.assigned == null ? 0 : b.assigned.compareTo(a.assigned));
        return array;
    }

    private void UpdateAtPosition(int position, Download download) {
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
        if (holder instanceof BasicViewHolder) InitializeViewHolder((BasicViewHolder) holder, download);
    }
}
