package com.yearzero.renebeats;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yearzero.renebeats.R;

public class DependencyFragment extends Fragment {

    private View view;
    private RecyclerView List;

    public DependencyFragment() { }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_dependency, container, false);

        List = view.findViewById(R.id.list);

        List.setLayoutManager(new LinearLayoutManager(getContext()));
        List.setAdapter(new Adapter(getContext(), getResources().getStringArray(R.array.dependencies)));

        return view;
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {

        private Context context;
        private String[] array;

        Adapter(Context context, String[] array) {
            this.context = context;
            this.array = array;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_dependencies, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.setText(array[position]);
        }

        @Override
        public int getItemCount() {
            return array.length;
        }
    }

    private static class ViewHolder extends RecyclerView.ViewHolder {

        private TextView text;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.text);
        }

        void setText(String text) {
            this.text.setText(text);
        }
    }
}
