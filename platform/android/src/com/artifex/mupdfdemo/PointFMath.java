package com.artifex.mupdfdemo;

import android.graphics.PointF;

class PointFMath 
{
    public static float distance(PointF A, PointF B) {
        return new PointF(A.x - B.x, A.y - B.y).length();
    }

    public static PointF pointOnLineCircleIntersection(PointF A, PointF B, PointF C, float radius) {
            //Adapted from http://keith-hair.net/blog/2008/08/05/line-to-circle-intersection-data/
        boolean inside = false;
        boolean tangent = false;
        boolean intersects = false;
        PointF enter=null;
	PointF exit=null;
        
	float a = (B.x - A.x) * (B.x - A.x) + (B.y - A.y) * (B.y - A.y);
	float b = 2 * ((B.x - A.x) * (A.x - C.x) +(B.y - A.y) * (A.y - C.y));
	float cc = C.x * C.x + C.y * C.y + A.x * A.x + A.y * A.y - 2 * (C.x * A.x + C.y * A.y) - radius * radius;
	float deter = b * b - 4 * a * cc;
        
	if (deter <= 0 ) {
            inside = false;
	} else {
            float e = (float)Math.sqrt(deter);
            float u1 = ( - b + e ) / (2 * a );
            float u2 = ( - b - e ) / (2 * a );
            if ((u1 < 0 || u1 > 1) && (u2 < 0 || u2 > 1)) {
                if ((u1 < 0 && u2 < 0) || (u1 > 1 && u2 > 1)) {
                    inside = false;
                } else {
                    inside = true;
                }
            } else {
                if (0 <= u2 && u2 <= 1) {
                    enter=interpolate(A, B, 1 - u2);
                }
                if (0 <= u1 && u1 <= 1) {
                    exit=interpolate (A, B, 1 - u1);
                }
                intersects = true;
                if (exit != null && enter != null && exit.equals(enter))
                {
                    tangent = true;
                }			
            }
	}
	return enter;
    }

    public static PointF interpolate(PointF A, PointF B, float f) {
        return new PointF(f * A.x + (1 - f) * B.x, f * A.y + (1 - f) * B.y);
    }
}
