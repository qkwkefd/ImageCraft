package com.liang.imagecraft;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

public class CustomImageView extends androidx.appcompat.widget.AppCompatImageView {

    private Matrix matrix = new Matrix();
    private float minScale = 0.5f;
    private float maxScale = 2.0f;

    private enum Mode {
        NONE, DRAG, ZOOM
    }

    private Mode mode = Mode.NONE;
    private PointF last = new PointF();
    private PointF start = new PointF();
    private float minScaleFactor = 0.5f;
    private float maxScaleFactor = 2.0f;
    private float[] m = new float[9];
    private ScaleGestureDetector scaleGestureDetector;
    private Context context;

    public CustomImageView(Context context) {
        super(context);
        sharedConstructing(context);
    }

    public CustomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        sharedConstructing(context);
    }

    private void sharedConstructing(Context context) {
        super.setClickable(true);
        this.context = context;
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        matrix.setTranslate(1f, 1f);
        setImageMatrix(matrix);
        setScaleType(ScaleType.MATRIX);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);

        PointF curr = new PointF(event.getX(), event.getY());

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                last.set(curr);
                start.set(last);
                mode = Mode.DRAG;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mode == Mode.DRAG) {
                    float deltaX = curr.x - last.x;
                    float deltaY = curr.y - last.y;
                    matrix.postTranslate(deltaX, deltaY);
                    last.set(curr.x, curr.y);
                }
                break;

            case MotionEvent.ACTION_UP:
                mode = Mode.NONE;
                int xDiff = (int) Math.abs(curr.x - start.x);
                int yDiff = (int) Math.abs(curr.y - start.y);
                if (xDiff < 3 && yDiff < 3) {
                    performClick();
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                mode = Mode.NONE;
                break;
        }

        setImageMatrix(matrix);
        invalidate();
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mode = Mode.ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float origScale = getCurrentScale();
            
            // Apply scale factor limits
            if (origScale * scaleFactor < minScale) {
                scaleFactor = minScale / origScale;
            } else if (origScale * scaleFactor > maxScale) {
                scaleFactor = maxScale / origScale;
            }
            
            matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            return true;
        }
    }

    private float getCurrentScale() {
        matrix.getValues(m);
        return m[Matrix.MSCALE_X];
    }

    public void setScaleRange(float minScale, float maxScale) {
        this.minScale = minScale;
        this.maxScale = maxScale;
    }
}