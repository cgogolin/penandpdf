package com.artifex.mupdfdemo;

import android.graphics.RectF;

public class SearchTaskResult {
	public final String txt;
	public final int   pageNumber;
	public final RectF searchBoxes[];
        public final int direction;
	static private SearchTaskResult singleton;

    SearchTaskResult(String _txt, int _pageNumber, RectF _searchBoxes[], int _direction) {
		txt = _txt;
		pageNumber = _pageNumber;
		searchBoxes = _searchBoxes;
                direction = _direction;
	}

	static public SearchTaskResult get() {
		return singleton;
	}

	static public void set(SearchTaskResult r) {
		singleton = r;
	}
}
