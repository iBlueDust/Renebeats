package com.yearzero.renebeats.download;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.yearzero.renebeats.InternalArgs;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.preferences.Preferences;

import java.util.ArrayList;
import java.util.Collection;

import jp.wasabeef.picasso.transformations.RoundedCornersTransformation;

public class QueryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private ArrayList<Query> queries;
    private DisplayMetrics metrics;

    public QueryAdapter(Context context, ArrayList<Query> queries) {
        this.context = context;
        this.queries = queries;
        metrics = context.getResources().getDisplayMetrics();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 0) {
            return new ShimmerViewHolder(LayoutInflater.from(context).inflate(metrics.widthPixels / metrics.density > 400f ? R.layout.layout_query_shimmer_large : R.layout.layout_query_shimmer, parent, false));
        }
        return new ViewHolder(LayoutInflater.from(context).inflate(metrics.widthPixels / metrics.density > 400f ? R.layout.layout_query_large : R.layout.layout_query, parent, false));
    }

    @Override
    public int getItemCount() {
        return queries.size();
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder h, int position) {
        Query query = queries.get(position);

        if (h instanceof ViewHolder) {
            ViewHolder holder = (ViewHolder) h;
            if (query.getThumbnail(Query.ThumbnailQuality.MaxRes) != null) {
                holder.setThumbnail(query.getThumbnail(metrics.widthPixels / metrics.density <
                        400f ? Preferences.getQueryImageLarge() : Preferences.getQueryImage()));
            }

            holder.setTitle(query.getTitle());
            holder.setAuthor(query.getArtist());

            holder.setOnClickListener(v -> {
                int index = holder.getAdapterPosition();
                if (index < 0 || index >= queries.size()) return;

                Intent intent = new Intent(context, DownloadActivity.class);
                intent.putExtra(InternalArgs.DATA, queries.get(index));
                context.startActivity(intent);
            });
        }
    }

    void resetList(Collection<Query> list){
        queries.clear();
        queries.addAll(list);
        notifyDataSetChanged();
    }

    void resetList() {
        queries.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return queries.get(position) == null ? 0 : 1;
    }

    private class ViewHolder extends RecyclerView.ViewHolder{

        private View Main;
        private ImageView Thumbnail;
        private TextView Title, Author;

        protected ViewHolder(View itemView) {
            super(itemView);

            Main = itemView;
            Thumbnail = itemView.findViewById(R.id.thumbnail);
            Title = itemView.findViewById(R.id.title);
            Author = itemView.findViewById(R.id.author);
        }

        protected void setOnClickListener(View.OnClickListener listener){
            Main.setOnClickListener(listener);
            Thumbnail.setOnClickListener(listener);
            Title.setOnClickListener(listener);
            Author.setOnClickListener(listener);
        }

        protected void setThumbnail(String url){
            if (url != null)
                Picasso.get()
                        .load(url)
                        .placeholder(R.drawable.shimmer_rounded)
                        .transform(new RoundedCornersTransformation((int) context.getResources().getDimension(R.dimen.thumbnail_radius), 0))
                        .centerCrop()
                        .fit()
                        .into(Thumbnail);
        }

        protected void setThumbnail(Uri image){
            if (image != null) Thumbnail.setImageURI(image);
            //            ShimmerThumbnail.stopShimmer();
            //            ShimmerThumbnail.setVisibility(View.GONE);
        }

        protected void setTitle(String title){
            if(title != null) Title.setText(title);
        }

        protected void setAuthor(String author){
            if(author != null) Author.setText(author);
        }
    }

    private class ShimmerViewHolder extends RecyclerView.ViewHolder{

        public ShimmerViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
