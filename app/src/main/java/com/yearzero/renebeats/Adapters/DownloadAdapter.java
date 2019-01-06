package com.yearzero.renebeats.Adapters;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.Download;
import com.yearzero.renebeats.DownloadDialog;
import com.yearzero.renebeats.DownloadService;
import com.yearzero.renebeats.R;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.BasicViewHolder> implements DownloadService.ClientCallbacks {
    private static final String TAG = "DownloadAdapter";
    
    private Context context;
    private DownloadService service;
    private RecyclerView recycler;

    public DownloadAdapter(Context context, DownloadService service, RecyclerView recycler) {
        this.context = context;
        this.service = service;
        this.recycler = recycler;
    }

    @NonNull
    @Override
    public BasicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int vht = getViewHolderType(Download.statusUnpack(viewType));

        switch (vht) {
            case QueueViewHolder.LocalID:
                return new QueueViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_queue, parent, false));
            case RunningViewHolder.LocalID:
                return new RunningViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_running, parent, false));
            case SuccessViewHolder.LocalID:
                return new SuccessViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_success, parent, false));
            case FailedViewHolder.LocalID:
                return new FailedViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_failed, parent, false));
            default:
                return new BasicViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_basic, parent, false));
        }
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
        Download.DownloadStatus dl = type >>> 20 >= Download.DownloadStatus.values().length ? null : Download.DownloadStatus.values()[type >>> 20];
        Download.ConvertStatus cv = ((type >>> 10) & 0x3FF) >= Download.ConvertStatus.values().length ? null : Download.ConvertStatus.values()[(type >>> 10) & 0x3FF];
        Boolean md = null;
        int i = type & 0x3FF;
        if (i == Boolean.TRUE.hashCode())
            md = true;
        else if (i == Boolean.FALSE.hashCode())
            md = false;

        if (!(dl == all[position].downloadStatus || cv == all[position].convertStatus || md == all[position].metadataSuccess)) Log.w(TAG, "ViewHolder type does not match Download type. Defaulting to follow ViewHolder type");

        InitializeViewHolder(holder, all[position], type);
    }

    private void InitializeViewHolder(BasicViewHolder holder, Download args) {
        InitializeViewHolder(holder, args, args.statusPack());
    }

    private void InitializeViewHolder(BasicViewHolder holder, final Download args, int status) {
        holder.setTitle(args.title + (args.title == null || args.title.isEmpty() || args.artist == null || args.artist.isEmpty() ? "" : " - ") + args.artist);
//        holder.setIsRecyclable(true);

        Download pack = Download.statusUnpack(status);

        if (pack.isFailed()) {
            //region FailedViewHolder
            FailedViewHolder n = (FailedViewHolder) holder;
            n.setStatus("Failed");

            if (args.exception instanceof IllegalArgumentException)
                n.setStatus("Download request is invalid");
            else if (args.exception instanceof DownloadService.ServiceException) {
                DownloadService.ServiceException e = (DownloadService.ServiceException) args.exception;
                //region ServiceException switch
                switch (e.getDownload()) {
                    case QUEUED:
                        n.setStatus("Failed while queueing for download");
                        break;
                    case RUNNING:
                        if (args.total > 0L) {
                            double c = args.current;
                            double t = args.total;

                            short ci = 0;
                            short ti = 0;

                            while (c >= 1000d && ci < Commons.suffix.length) {
                                c /= 1000d;
                                ci++;
                            }

                            while (t >= 1000d && ti < Commons.suffix.length) {
                                t /= 1000d;
                                ti++;
                            }

                            n.setStatus(String.format(Locale.ENGLISH, "Failed while downloading. %.2f%s of %.2f%s completed.", c, Commons.suffix[ci], t, Commons.suffix[ti]));
                        } else n.setStatus("Failed while downloading");
                        break;
                    case PAUSED:
                        if (args.current > 0L) {
                            double pc = args.current;
                            double pt = args.total;

                            short pci = 0;
                            short pti = 0;

                            while (pc >= 1000d && pci < Commons.suffix.length) {
                                pc /= 1000d;
                                pci++;
                            }

                            while (pt >= 1000d && pti < Commons.suffix.length) {
                                pt /= 1000d;
                                pti++;
                            }

                            n.setStatus(String.format(Locale.ENGLISH, "An exception occurred while download was paused. %.2f%s of %.2f%s completed.", pc, Commons.suffix[pci], pt, Commons.suffix[pti]));
                        } else
                            n.setStatus("An exception occurred while download was paused");
                        break;
                    default:
                        switch(((DownloadService.ServiceException) args.exception).getConversion()) {
                            case QUEUED:
                                n.setStatus("An exception occurred while queueing for conversion");
                                break;
                            case PAUSED:
                                n.setStatus("An exception occurred while paused before conversion");
                                break;
                            case SKIPPED:
                                n.setStatus("An unknown exception occurred (skipped)");
                                break;
                            case RUNNING:
                                double v = args.current;
                                short vi = 0;

                                while (v >= 1000d && vi < Commons.suffix.length) {
                                    v /= 1000d;
                                    vi++;
                                }

                                n.setStatus("Failed to convert file");
                                break;
                            default:
                                n.setStatus("An exception occurred and was unhandled");
                        }
                }
                //endregion

                n.setInfoOnClickListener(v -> {
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
            }
            //endregion
        } else if (pack.isCancelled())
            ((QueueViewHolder) holder).setStatus("Cancelled");
        else if (pack.downloadStatus != null) {

            try {
                //region Status Main
                switch (pack.downloadStatus) {
                    case QUEUED:
                        QueueViewHolder g = (QueueViewHolder) holder;
                        g.setStatus("Waiting for download");
                        break;
                    case RUNNING:
                        RunningViewHolder i = (RunningViewHolder) holder;

                        double dc = args.current;
                        double dt = args.total;
                        short dci = 0;
                        short dti = 0;
                        
                        while (dc >= 1000d && dci < Commons.suffix.length) {
                            dc /= 1000d;
                            dci++;
                        }

                        while (dt >= 1000d && dti < Commons.suffix.length) {
                            dt /= 1000d;
                            dti++;
                        }

                        i.setStatus(String.format(Locale.ENGLISH, "%.2f %s of %.2f %s downloaded", dc, Commons.suffix[dci], dt, Commons.suffix[dti]));
                        i.setProgress(args.current, args.total, args.indeterminate);
                        break;
                    case PAUSED:
                        RunningViewHolder m = (RunningViewHolder) holder;
                        m.setStatus("Download Paused");
                        m.setPaused(true);
                        m.setProgress(args.current, args.total, args.indeterminate);
                        break;
                    case COMPLETE:
                        if (pack.convertStatus == null) break;
                        switch (pack.convertStatus) {
                            case QUEUED:
                                QueueViewHolder h = (QueueViewHolder) holder;
                                h.setStatus("Download Completed, waiting for conversion");
                                break;
                            case RUNNING:
                                RunningViewHolder j = (RunningViewHolder) holder;
                                
                                double cc = args.current;
                                short cci = 0;

                                while (cc >= 1000d && cci < Commons.suffix.length) {
                                    cc /= 1000d;
                                    cci++;
                                }
                                
                                j.setStatus(String.format(Locale.ENGLISH, "%.2f %s converted", cc, Commons.suffix[cci]));
                                j.setProgress(0, 0, true);
                                break;
                            case PAUSED:
                                RunningViewHolder l = (RunningViewHolder) holder;
                                l.setStatus("Paused");
                                l.setPaused(true);
                                l.setProgress(0, 0, true);
                                break;
                            case SKIPPED:
                            case COMPLETE:
                                if (pack.metadataSuccess == null) {
                                    RunningViewHolder n = (RunningViewHolder) holder;
                                    n.setStatus("Applying metadata");
                                    n.setProgress(0, 0, true);
                                } else {
                                    SuccessViewHolder o = (SuccessViewHolder) holder;
                                    o.setStatus("SUCCESS");

                                    Calendar ass = Calendar.getInstance();
                                    ass.setTime(args.getCompleteDate());

                                    Calendar yesterday = Calendar.getInstance();
                                    yesterday.setTimeInMillis(System.currentTimeMillis());
                                    yesterday.add(Calendar.DAY_OF_YEAR, -1);

                                    if (DateUtils.isToday(args.getCompleteDate().getTime()))
                                        o.setDate("Assigned at " + new SimpleDateFormat("hh:mm:ss tt", Locale.ENGLISH).format(args.getCompleteDate()));
                                    else if (yesterday.get(Calendar.YEAR) == ass.get(Calendar.YEAR) && yesterday.get(Calendar.DAY_OF_YEAR) == ass.get(Calendar.DAY_OF_YEAR))
                                        o.setDate("Assigned yesterday");
                                    else
                                        o.setDate("Assigned on " + new SimpleDateFormat("EEE, d MMMM yyyy", Locale.ENGLISH).format(args.getCompleteDate()));
                                }
                        }

                }
                //endregion
            } catch (ClassCastException e) {
                Log.e(TAG, "Class cast exception");
                notifyItemChanged(holder.getAdapterPosition());
            }
        }

        holder.setOnClickListener(v -> new DownloadDialog(context)
                .setDownload(args)
                .show());

        holder.setOnLongClickListener(v -> {
            new DownloadDialog(context)
                    .setDownload(args)
                    .setSecret(true)
                    .show();
            return true;
        });
    }

    @Override
    public void onProgress(Download args, long progress, long max, boolean indeterminate) {
        Download[] array = getServiceDownloads();

        int index = -1;

        for (int i = 0; i < array.length; i++) {
            if (array[i].hashCode() == args.hashCode()) {
                index = i;
                break;
            }
        }

        if (index < 0)
            notifyDataSetChanged();
        else UpdateAtPosition(index, args);
    }

    @Override
    public void onDone(Download args, boolean successful, Exception e) {
//        Download[] array = getServiceDownloads();
//
//        int index = -1;
//
//        for (int i = 0; i < array.length; i++) {
//            if (array[i].hashCode() == args.hashCode()) {
//                index = i;
//                break;
//            }
//        }
//
//        if (index < 0) notifyDataSetChanged();
//        else notifyItemChanged(index);
        notifyDataSetChanged();
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
        return array[position].statusPack();
    }

    private int getViewHolderType(Download pack) {
        if (pack.isFailed())
            return FailedViewHolder.LocalID;
        else if (pack.downloadStatus == Download.DownloadStatus.COMPLETE && (pack.convertStatus == Download.ConvertStatus.COMPLETE || pack.convertStatus == Download.ConvertStatus.SKIPPED))
            return pack.metadataSuccess == null ? RunningViewHolder.LocalID : SuccessViewHolder.LocalID;
        else if (pack.isCancelled() || pack.isQueued())
            return QueueViewHolder.LocalID;
        else if (pack.downloadStatus == Download.DownloadStatus.RUNNING || pack.downloadStatus == Download.DownloadStatus.PAUSED ||
                pack.convertStatus == Download.ConvertStatus.RUNNING || pack.convertStatus == Download.ConvertStatus.PAUSED)
            return RunningViewHolder.LocalID;
        else return BasicViewHolder.LocalID;
    }

    public Download[] getServiceDownloads() {
        service.Sanitize();
        Download[] array = service.getAll().toArray(new Download[0]);
        Arrays.sort(array, (a, b) -> a.assigned == null || b.assigned == null ? 0 : a.assigned.compareTo(b.assigned));
        return array;
    }

    public void UpdateAtPosition(int position, Download download) {
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
        if (holder instanceof BasicViewHolder) InitializeViewHolder((BasicViewHolder) holder, download);
    }

    static class BasicViewHolder extends RecyclerView.ViewHolder {
        static final int LocalID = 0;

        protected View Main;
        protected TextView Title;
        protected ImageView Action;

        BasicViewHolder(View itemView) {
            super(itemView);
            Main = itemView;
            Title = itemView.findViewById(R.id.title);
            Action = itemView.findViewById(R.id.action);
        }

        void setTitle(String title) {
            Title.setText(title);
        }

        void setOnClickListener(View.OnClickListener listener) {
            Main.setOnClickListener(listener);
        }

        void setOnLongClickListener(View.OnLongClickListener listener) {
            Main.setOnLongClickListener(listener);
        }

        void setAction(View.OnClickListener listener) {
            Action.setOnClickListener(listener);
        }

    }

    static class QueueViewHolder extends BasicViewHolder {
        static final int LocalID = 1;

        protected TextView Status;

        QueueViewHolder(View itemView) {
            super(itemView);

            Status = itemView.findViewById(R.id.status);
        }

        void setStatus(String status) {
            Status.setText(status);
        }
    }

    static class RunningViewHolder extends QueueViewHolder {
        static final int LocalID = 2;

        protected ConstraintLayout Constraint;
        protected ProgressBar Progress;

        RunningViewHolder(View itemView) {
            super(itemView);

            Constraint = itemView.findViewById(R.id.constraint);
            Progress = itemView.findViewById(R.id.progress);
            UpdateAnimation();
        }

        void setProgress(int current, int total, boolean indeterminate) {
            if (indeterminate) Progress.setIndeterminate(true);
            else {
                Progress.setIndeterminate(false);
                Progress.setMax(total);
                Progress.setProgress(current);
            }
        }

        void setPaused(boolean paused) {
            if (paused) {
                Constraint.setBackgroundResource(R.drawable.background_layout_paused);
                Status.setText("PAUSED");
            } else {
                Constraint.setBackgroundResource(R.drawable.background_layout_running_0);
                UpdateAnimation();
            }
        }

        protected void UpdateAnimation() {
            Drawable draw = Constraint.getBackground();
            if (draw instanceof AnimationDrawable) {
                AnimationDrawable anim = (AnimationDrawable) draw;
                anim.setEnterFadeDuration(500);
                anim.setExitFadeDuration(1000);
                anim.start();
            }

        }
    }

    static class SuccessViewHolder extends QueueViewHolder {
        static final int LocalID = 3;

        protected TextView Date;

        SuccessViewHolder(View itemView) {
            super(itemView);

            Date = itemView.findViewById(R.id.date);
        }

        void setDate(String date) {
            Date.setText(date);
        }
    }

    static class FailedViewHolder extends QueueViewHolder {
        static final int LocalID = 4;

        protected ImageView Info;

        FailedViewHolder(View itemView) {
            super(itemView);

            Info = itemView.findViewById(R.id.info);
        }

        void setInfoOnClickListener(View.OnClickListener listener) {
            Info.setOnClickListener(listener);
        }
    }
}
