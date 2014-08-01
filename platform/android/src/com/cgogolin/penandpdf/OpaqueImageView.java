package com.cgogolin.penandpdf;

import android.content.Context;
import android.widget.ImageView;

class OpaqueImageView extends ImageView {

    public OpaqueImageView(Context context) {
        super(context);
    }

    @Override
    public boolean isOpaque() {
        return true;
    }
}
