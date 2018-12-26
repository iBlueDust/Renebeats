package com.yearzero.renebeats.Activities;

import android.content.pm.PackageManager;
import android.os.Bundle;

import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.Fragments.Main.DownloadFragment;
import com.yearzero.renebeats.Fragments.Main.MainFragment;
import com.yearzero.renebeats.R;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity {

    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        mViewPager = findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.setCurrentItem(getIntent().getIntExtra(Commons.ARGS.INDEX, 1));

        try {
            String[] perms = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
            if (!EasyPermissions.hasPermissions(this, perms))
                EasyPermissions.requestPermissions(this,
                        "This app requires the following permissions. Without them, some functionality will be lost",
                        Commons.PERM_REQUEST,
                        perms
                );

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new DownloadFragment();
                default:
                    return new MainFragment();
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
