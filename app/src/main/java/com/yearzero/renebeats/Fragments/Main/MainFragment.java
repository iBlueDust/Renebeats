package com.yearzero.renebeats.Fragments.Main;

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.google.android.material.textfield.TextInputEditText;
import com.yearzero.renebeats.Activities.DownloadActivity;
import com.yearzero.renebeats.Activities.QueryActivity;
import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.R;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class MainFragment extends Fragment implements View.OnClickListener {

    private TextInputEditText Query;
    private ImageButton Search;

    public MainFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(new ContextThemeWrapper(getActivity(), R.style.AppTheme_NoActionBar)).inflate(R.layout.fragment_main, container, false);

        Query = view.findViewById(R.id.query);
        Search = view.findViewById(R.id.search);

        Search.setOnClickListener(this);

        return view;
    }

    @Override
    public void onClick(View v) {
        if(Query.getText().toString().trim().isEmpty()) return;

        Intent intent = new Intent(getActivity(), DownloadActivity.class);

        String url = "";
        String query = Query.getText().toString();

        if(!(query.startsWith("http://") || query.startsWith("https://") || query.contains(" ")) && query.contains(".")) url = "http://";
        if(Pattern.compile("^(https?://)?[\\w\\d]+\\.[\\w\\d]+", Pattern.CASE_INSENSITIVE).matcher(url).matches()) url += "www.";
        url += query;

        try {
            new URL(url);
            intent.putExtra(Commons.ARGS.URL, Query.getText().toString().isEmpty());
        } catch (MalformedURLException ignored) {
            intent = new Intent(getActivity(), QueryActivity.class);
            intent.putExtra(Commons.ARGS.DATA, query);
        } finally {
            startActivity(intent);
        }

    }
}
