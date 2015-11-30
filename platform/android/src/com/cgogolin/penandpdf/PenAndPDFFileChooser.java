package com.cgogolin.penandpdf;

import java.io.File;

import android.app.Activity;
//import android.app.Fragment;
//import android.app.FragmentManager;
import android.os.Bundle;
//import android.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.content.Intent;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import java.lang.reflect.InvocationTargetException;
import android.preference.PreferenceManager;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class PenAndPDFFileChooser extends android.support.v7.app.AppCompatActivity implements RecentFilesFragment.goToDirInterface {

    private android.support.v4.app.FragmentPagerAdapter mFragmentPagerAdapter;
    private ViewPager mViewPager;

    private String mFilename = null;
    
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
                
                //Set default preferences on first start
        PreferenceManager.setDefaultValues(this, SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS, R.xml.preferences, false);

            //Infalte the layout
        setContentView(R.layout.chooser);

            //Setup the toolbar
        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

            //Setup the tabs
        final android.support.design.widget.TabLayout tabLayout = (android.support.design.widget.TabLayout) findViewById(R.id.tab_layout);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.browse));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.recent));
        tabLayout.setTabGravity(android.support.design.widget.TabLayout.GRAVITY_FILL);

            //Setup the view pager
        final ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        mFragmentPagerAdapter = new android.support.v4.app.FragmentPagerAdapter(getSupportFragmentManager()) {

                FileBrowserFragment fileBrowserFragment;
                RecentFilesFragment recentFilesFragment;
                
                @Override
                public int getCount() {
                    return 2;
                }        
                @Override
                public android.support.v4.app.Fragment getItem(int position) {
                    switch(position) {
                        case 0:
                            if(fileBrowserFragment == null)
                                fileBrowserFragment = FileBrowserFragment.newInstance(getIntent());
                            return (android.support.v4.app.Fragment)fileBrowserFragment;
                        case 1:
                            if(recentFilesFragment == null)
                                recentFilesFragment = RecentFilesFragment.newInstance(getIntent());
                            return (android.support.v4.app.Fragment)recentFilesFragment;
                        default:
                            return null;
                    }
                }
            };
        viewPager.setAdapter(mFragmentPagerAdapter);
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout)
            {
                @Override
                public void onPageSelected(int position) {
                        //When swiping between pages, select the corresponding tab.
                    tabLayout.getTabAt(position).select();
                }
            }                              
            );
        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }
 
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
 
            }
 
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
 
            }
        });
        
        //     //Create the fragment adapter
        // mFragmentPagerAdapter = new SwipeFragmentPagerAdapter(getFragmentManager(), getLayoutInflater());
        //     //Add the fragments
        // FileBrowserFragment fileBrowserFragment = FileBrowserFragment.newInstance(getIntent());
        // RecentFilesFragment recentFilesFragment = RecentFilesFragment.newInstance(getIntent());
        // mFragmentPagerAdapter.add(fileBrowserFragment);
        // mFragmentPagerAdapter.add(recentFilesFragment);
        //     //Add the fragment adapter to he view
        // mViewPager = (ViewPager) findViewById(R.id.pager);
        // mViewPager.setAdapter(mFragmentPagerAdapter);

        //     // Specify that tabs should be displayed in the action bar.
        // final android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        // actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        //     // Create a tab listener that is called when the user clicks tabs.
        // android.support.v7.app.ActionBar.TabListener tabListener = new android.support.v7.app.ActionBar.TabListener() {
        //         @Override
        //         public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
        //             mViewPager.setCurrentItem(tab.getPosition());
        //         }
        //         @Override
        //         public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {}
        //         @Override
        //         public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {}
        //     };

        
        //     // Add 2 tabs, specifying the tab's text and TabListener
        // for (int i = 0; i < mFragmentPagerAdapter.getCount(); i++) {
        //     String tabLable;
        //     switch(i)
        //     {
        //         case 0:
        //             tabLable = getString(R.string.browse);
        //             break;
        //         case 1:
        //             tabLable = getString(R.string.recent);
        //             break;
        //         default:
        //             tabLable = "unknown";
        //     }
        //     actionBar.addTab(
        //         actionBar.newTab().setText(tabLable).setTabListener(tabListener));
        // }

        // mViewPager.setOnPageChangeListener(
        //     new ViewPager.SimpleOnPageChangeListener() {
        //         @Override
        //         public void onPageSelected(int position) {
        //                 //When swiping between pages, select the corresponding tab.
        //             getActionBar().setSelectedNavigationItem(position);
        //         }
        //     });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {//Inflates the options menu
        
        MenuInflater inflater = getMenuInflater();
            //inflater.inflate(R.menu.choosepdf_menu, menu);
        inflater.inflate(R.menu.empty_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {//Handel clicks in the options menu
        switch (item.getItemId()) 
        {
            case R.id.menu_settings:
                Intent intent = new Intent(this,SettingsActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void goToDir(File dir) {
//        ((FileBrowserFragment)mFragmentPagerAdapter.getItem(0)).goToDir(dir);//Doesn't do what you would think it does
//        findFragmentByTag("android:switcher:" + R.id.pager + ":" + 0).goToDir(dir);//This is how one is supposed to do it
        
//        ((FileBrowserFragment)getFragmentByPosition(mViewPager,mFragmentPagerAdapter, 0)).goToDir(dir);

        ((FileBrowserFragment)mFragmentPagerAdapter.getItem(0)).goToDir(dir);       
        mViewPager.setCurrentItem(0);        
    }
    
    //     //Black magic :-)
    // private android.support.v4.app.Fragment getFragmentByPosition(ViewPager viewPager, FragmentPagerAdapter fragmentPagerAdapter, int position) {
    //     android.support.v4.app.Fragment fragment = getFragmentManager().findFragmentByTag("android:switcher:" + viewPager.getId() + ":"+ position);
    //     if(fragment==null) fragment = fragmentPagerAdapter.getItem(position);
    //     return fragment;
    // }

    // @Override
    // public void onBackPressed() {
    //     super.onBackPressed();
    //     overridePendingTransition(R.animator.fade_in, R.animator.exit_to_left);
    // }

    @Override
    public void onDestroy() {
        super.onDestroy();
        overridePendingTransition(R.animator.fade_in, R.animator.exit_to_left);
    }
}
