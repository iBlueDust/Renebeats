package com.yearzero.renebeats.Adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.yearzero.renebeats.Activities.DownloadActivity;
import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.Query;
import com.yearzero.renebeats.R;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class QueryAdapter extends RecyclerView.Adapter<QueryAdapter.ViewHolder> {

    public Context context;
    public ArrayList<Query> queries;

    public QueryAdapter(Context context, ArrayList<Query> queries) {
        this.context = context;
        this.queries = queries;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_query, parent, false));
    }

    @Override
    public int getItemCount() {
        return queries.size();
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        Query query = queries.get(position);

        if(query.thumbmap != null) holder.setThumbnail(query.thumbmap);
        else if(query.getThumbnail(Query.ThumbnailQuality.MaxRes) != null) holder.setThumbnail(query.getThumbnail(Commons.Pref.queryImage));

        holder.setTitle(query.title);
        holder.setAuthor(query.artist);

        holder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int index = holder.getAdapterPosition();
                if(index < 0 || index >= queries.size()) return;

                Intent intent = new Intent(context, DownloadActivity.class);
                intent.putExtra(Commons.ARGS.DATA, queries.get(index));
                context.startActivity(intent);
            }
        });

    }

    public void resetList(List<Query> list){
        queries.clear();
        queries.addAll(list);
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder{

        private View Main;
        private ImageView Thumbnail;
        private TextView Title, Author;

        public ViewHolder(View itemView) {
            super(itemView);

            Main = itemView;
            Thumbnail = itemView.findViewById(R.id.thumbnail);
            Title = itemView.findViewById(R.id.title);
            Author = itemView.findViewById(R.id.artist);

        }

        public void setOnClickListener(View.OnClickListener listener){
            Main.setOnClickListener(listener);
            Thumbnail.setOnClickListener(listener);
            Title.setOnClickListener(listener);
            Author.setOnClickListener(listener);
        }

        public void setThumbnail(String url){
            Picasso.get().load(url).into(Thumbnail);
        }

        public void setThumbnail(Uri image){
            Thumbnail.setImageURI(image);
        }

        public void setTitle(String title){
            if(title != null) Title.setText(title);
        }

        public void setAuthor(String author){
            if(author != null) Author.setText(author);
        }

    }
}
