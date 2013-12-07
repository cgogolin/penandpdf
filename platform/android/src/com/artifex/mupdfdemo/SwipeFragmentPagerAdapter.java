package com.artifex.mupdfdemo;

import android.app.Fragment;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import java.util.List;
import java.util.ArrayList;
import android.app.FragmentTransaction;
import android.app.FragmentManager;

public class SwipeFragmentPagerAdapter extends FragmentPagerAdapter { //Extends a slightly modified version of FragmentPagerAdapter

    private LayoutInflater mInflater;
    private List<Fragment> pages = new ArrayList<Fragment>();
    
    public SwipeFragmentPagerAdapter(FragmentManager fragmentManager, LayoutInflater inflater) {
        super(fragmentManager);
        mInflater = inflater;
    }

    public void add(Fragment fragment) {
        pages.add(fragment);
    }
    
    @Override
    public Fragment getItem(int position) {
        return pages.get(position);
    }
    
    
    // @Override
    // public Object instantiateItem (ViewGroup container, int position) {
    //     // FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();

    //     // fragmentTransaction.add(container, pages.get(position));
    //     // fragmentTransaction.commit();
    //     View view = pages.get(position).onCreateView(mInflater, container, null);
    //     container.addView(view);
    //     return view;
    // }

    // @Override
    // public void destroyItem (ViewGroup container, int position, Object object) {
    //     container.removeView((View) object);
    // }
    
    @Override
    public int getCount() {
        return pages.size();
    }

    // @Override
    // public boolean isViewFromObject(View view, Object object) {
    //     return view.equals(object);
    // }
    
    // @Override
    // public CharSequence getPageTitle(int position) {
    //     return "OBJECT " + (position + 1);
    // }
}
