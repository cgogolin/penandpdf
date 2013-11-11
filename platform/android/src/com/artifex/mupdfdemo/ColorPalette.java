package com.artifex.mupdfdemo;

import android.graphics.Color;

public class ColorPalette {
    private final static int[ ][ ] paletteRGB = {
        {0,0,0},
        {4,70,110},
        {75,28,99},
        {52,76,5},
        {121,66,3},
        {98,4,4},

        {49,49,49},
        {0, 153, 204},
        {153,  51, 204},
        {102, 153,   0},
        {255, 136,   0},
        {204,   0,   0},

        {91,91,91},
        {51, 181, 229},
        {170, 102, 204},
        {153, 204,   0},
        {255, 187,  51},
        {255,  68,  68}
    };

    
    // private final static int[ ] paletteHEX = {
    //     0x33B5E5,
    //     0xAA66CC,
    //     0x99CC00,
    //     0xFFBB33,
    //     0xFF4444,
    //     0x0099CC,
    //     0x9933CC,
    //     0x669900,
    //     0xFF8800,
    //     0xCC0000
    // };
        
    public static float getR(int number) {
        if (number < 0 ) number = 0;
        if (number >= paletteRGB.length) number=paletteRGB.length-1;
        return paletteRGB[number][0]/255f;
    }

    public static float getG(int number) {
        if (number < 0 ) number = 0;
        if (number >= paletteRGB.length) number=paletteRGB.length-1;
        return paletteRGB[number][1]/255f;
    }

    public static float getB(int number) {
        if (number < 0 ) number = 0;
        if (number >= paletteRGB.length) number=paletteRGB.length-1;
        return paletteRGB[number][2]/255f;
    }

    public static int getHex(int number) {
        if (number < 0 ) number = 0;
        if (number >= paletteRGB.length) number=paletteRGB.length-1;
        return Color.argb(255,paletteRGB[number][0],paletteRGB[number][1],paletteRGB[number][2]);
    }
}

