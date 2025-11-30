package com.liang.imagecraft;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

public class CustomImageView extends androidx.appcompat.widget.AppCompatImageView {

    private Matrix matrix = new Matrix();
    private float minScale = 0.3f;
    private float maxScale = 2.0f;

    private enum Mode {
        NONE, DRAG, ZOOM, CROP
    }

    private Mode mode = Mode.NONE;
    private PointF last = new PointF();
    private PointF start = new PointF();
    private float minScaleFactor = 0.3f;
    private float maxScaleFactor = 2.0f;
    private float[] m = new float[9];
    private ScaleGestureDetector scaleGestureDetector;
    private Context context;
    private int brightness = 0; // 亮度值，范围为-100到100，默认值为0
    private Bitmap originalBitmap = null; // 保存原始图像的引用
    // 新增：真正的原始图片（仅首次加载赋值，永不修改）
    private Bitmap baseOriginalBitmap = null;

    // 裁剪相关属性
    private boolean isCropMode = false;
    private RectF cropRect = new RectF();
    private Paint cropPaint;
    private Paint cropBorderPaint;
    private Paint cropBackgroundPaint;
    private float cropRatio = 0; // 0表示自由裁剪，其他值表示宽高比
    private PointF cropStart = new PointF();
    private PointF cropEnd = new PointF();
    private boolean isDraggingCrop = false;
    private boolean isResizingCrop = false;
    private static final int RESIZE_THRESHOLD = 20; // 调整裁剪框大小的阈值

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
        
        // 初始化裁剪相关的画笔
        initCropPaints();
    }
    
    /**
     * 初始化裁剪相关的画笔
     */
    private void initCropPaints() {
        // 裁剪区域边框画笔
        cropBorderPaint = new Paint();
        cropBorderPaint.setColor(Color.WHITE);
        cropBorderPaint.setStrokeWidth(2f);
        cropBorderPaint.setStyle(Paint.Style.STROKE);
        cropBorderPaint.setPathEffect(new DashPathEffect(new float[]{10, 5}, 0));
        cropBorderPaint.setAntiAlias(true);
        
        // 裁剪区域背景画笔（半透明黑色）
        cropBackgroundPaint = new Paint();
        cropBackgroundPaint.setColor(Color.argb(128, 0, 0, 0));
        cropBackgroundPaint.setStyle(Paint.Style.FILL);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 如果处于裁剪模式，绘制裁剪框
        if (isCropMode) {
            drawCropOverlay(canvas);
        }
    }
    
    /**
     * 绘制裁剪覆盖层
     */
    private void drawCropOverlay(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        
        // 绘制裁剪区域外的半透明背景
        Path path = new Path();
        
        // 绘制整个画布
        path.addRect(0, 0, width, height, Path.Direction.CW);
        
        // 减去裁剪区域
        path.addRect(cropRect, Path.Direction.CCW);
        
        // 填充裁剪区域外的背景
        canvas.drawPath(path, cropBackgroundPaint);
        
        // 绘制裁剪框边框
        canvas.drawRect(cropRect, cropBorderPaint);
    }
    
    /**
     * 设置裁剪模式
     */
    public void setCropMode(boolean enabled) {
        this.isCropMode = enabled;
        if (enabled) {
            // 初始化裁剪框，默认居中显示，大小为图像的80%
            initCropRect();
            mode = Mode.CROP;
        } else {
            mode = Mode.NONE;
        }
        invalidate();
    }
    
    /**
     * 初始化裁剪框
     */
    private void initCropRect() {
        int width = getWidth();
        int height = getHeight();
        float size = Math.min(width, height) * 0.8f;
        
        if (cropRatio > 0) {
            // 根据比例计算裁剪框大小
            if (cropRatio >= 1) {
                // 宽 > 高
                size = Math.min(width * 0.8f, height * 0.8f / cropRatio);
                cropRect.set(
                    (width - size) / 2,
                    (height - size / cropRatio) / 2,
                    (width + size) / 2,
                    (height + size / cropRatio) / 2
                );
            } else {
                // 高 > 宽
                size = Math.min(width * 0.8f * cropRatio, height * 0.8f);
                cropRect.set(
                    (width - size * cropRatio) / 2,
                    (height - size) / 2,
                    (width + size * cropRatio) / 2,
                    (height + size) / 2
                );
            }
        } else {
            // 自由裁剪，默认正方形
            cropRect.set(
                (width - size) / 2,
                (height - size) / 2,
                (width + size) / 2,
                (height + size) / 2
            );
        }
        
        cropStart.set(cropRect.left, cropRect.top);
        cropEnd.set(cropRect.right, cropRect.bottom);
    }
    
    /**
     * 设置裁剪比例
     * @param ratio 宽高比，0表示自由裁剪
     */
    public void setCropRatio(float ratio) {
        this.cropRatio = ratio;
        if (isCropMode) {
            initCropRect();
            invalidate();
        }
    }
    
    /**
     * 执行裁剪操作
     * @return 裁剪后的Bitmap图像
     */
    public Bitmap performCrop() {
        if (!isCropMode) return null;
        
        // 获取原始图像
        Drawable drawable = getDrawable();
        if (!(drawable instanceof BitmapDrawable)) return null;
        
        Bitmap originalBitmap = ((BitmapDrawable) drawable).getBitmap();
        
        // 计算裁剪区域相对于图像的坐标
        float[] cropPoints = new float[]{
            cropRect.left, cropRect.top,
            cropRect.right, cropRect.bottom
        };
        
        // 获取当前图像矩阵的逆矩阵，用于将屏幕坐标转换为图像坐标
        Matrix inverseMatrix = new Matrix();
        if (matrix.invert(inverseMatrix)) {
            inverseMatrix.mapPoints(cropPoints);
        }
        
        // 确保裁剪坐标在图像范围内
        int left = Math.max(0, (int) cropPoints[0]);
        int top = Math.max(0, (int) cropPoints[1]);
        int right = Math.min(originalBitmap.getWidth(), (int) cropPoints[2]);
        int bottom = Math.min(originalBitmap.getHeight(), (int) cropPoints[3]);
        
        // 计算裁剪区域的宽度和高度
        int cropWidth = right - left;
        int cropHeight = bottom - top;
        
        // 确保裁剪区域有效
        if (cropWidth <= 0 || cropHeight <= 0) {
            setCropMode(false);
            return null;
        }
        
        // 创建裁剪后的Bitmap
        Bitmap croppedBitmap = Bitmap.createBitmap(
            originalBitmap, 
            left, 
            top, 
            cropWidth, 
            cropHeight
        );
        
        // 创建新的矩阵，将裁剪后的图像居中显示
        Matrix resultMatrix = new Matrix();
        
        // 计算缩放比例，使裁剪后的图像适应屏幕
        float scaleX = getWidth() / (float) cropWidth;
        float scaleY = getHeight() / (float) cropHeight;
        float scale = Math.min(scaleX, scaleY);
        
        // 设置缩放和平移，使裁剪后的图像居中显示
        resultMatrix.postScale(scale, scale);
        resultMatrix.postTranslate(
            (getWidth() - cropWidth * scale) / 2,
            (getHeight() - cropHeight * scale) / 2
        );
        
        // 应用新矩阵
        matrix.set(resultMatrix);
        setImageMatrix(matrix);
        
        // 退出裁剪模式
        setCropMode(false);
        
        return croppedBitmap;
    }
    
    /**
     * 检查点是否在裁剪框内
     */
    private boolean isPointInCropRect(float x, float y) {
        return cropRect.contains(x, y);
    }
    
    /**
     * 检查点是否在裁剪框的边缘（用于调整大小）
     */
    private boolean isPointOnCropRectEdge(float x, float y) {
        return (
            Math.abs(x - cropRect.left) < RESIZE_THRESHOLD ||
            Math.abs(x - cropRect.right) < RESIZE_THRESHOLD ||
            Math.abs(y - cropRect.top) < RESIZE_THRESHOLD ||
            Math.abs(y - cropRect.bottom) < RESIZE_THRESHOLD
        ) && cropRect.contains(x, y);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isCropMode) {
            return handleCropTouchEvent(event);
        }
        
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
    
    /**
     * 处理裁剪模式下的触摸事件
     */
    private boolean handleCropTouchEvent(MotionEvent event) {
        // 先处理缩放手势
        boolean scaleHandled = scaleGestureDetector.onTouchEvent(event);
        
        PointF curr = new PointF(event.getX(), event.getY());

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (isPointOnCropRectEdge(curr.x, curr.y)) {
                    // 点击在裁剪框边缘，准备调整大小
                    isResizingCrop = true;
                    cropStart.set(curr);
                    cropEnd.set(cropRect.right, cropRect.bottom);
                } else if (isPointInCropRect(curr.x, curr.y)) {
                    // 点击在裁剪框内部，准备拖动
                    isDraggingCrop = true;
                    cropStart.set(curr);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isResizingCrop) {
                    // 调整裁剪框大小
                    updateCropRectSize(curr);
                } else if (isDraggingCrop) {
                    // 拖动裁剪框
                    updateCropRectPosition(curr);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                isDraggingCrop = false;
                isResizingCrop = false;
                break;
        }

        invalidate();
        return true;
    }
    
    /**
     * 更新裁剪框大小
     */
    private void updateCropRectSize(PointF curr) {
        float deltaX = curr.x - cropStart.x;
        float deltaY = curr.y - cropStart.y;
        
        float newWidth = cropRect.width() + deltaX;
        float newHeight = cropRect.height() + deltaY;
        
        // 确保裁剪框大小不小于最小限制
        if (newWidth < 50 || newHeight < 50) return;
        
        // 如果设置了比例，保持比例不变
        if (cropRatio > 0) {
            if (Math.abs(deltaX) > Math.abs(deltaY)) {
                // 以宽度变化为主
                newWidth = Math.max(50, newWidth);
                newHeight = newWidth / cropRatio;
            } else {
                // 以高度变化为主
                newHeight = Math.max(50, newHeight);
                newWidth = newHeight * cropRatio;
            }
        }
        
        // 更新裁剪框
        float centerX = cropRect.centerX();
        float centerY = cropRect.centerY();
        
        cropRect.set(
            centerX - newWidth / 2,
            centerY - newHeight / 2,
            centerX + newWidth / 2,
            centerY + newHeight / 2
        );
        
        // 确保裁剪框不超出屏幕边界
        constrainCropRect();
        
        cropStart.set(curr);
    }
    
    /**
     * 更新裁剪框位置
     */
    private void updateCropRectPosition(PointF curr) {
        float deltaX = curr.x - cropStart.x;
        float deltaY = curr.y - cropStart.y;
        
        cropRect.offset(deltaX, deltaY);
        
        // 确保裁剪框不超出屏幕边界
        constrainCropRect();
        
        cropStart.set(curr);
    }
    
    /**
     * 确保裁剪框不超出屏幕边界
     */
    private void constrainCropRect() {
        if (cropRect.left < 0) {
            cropRect.offset(-cropRect.left, 0);
        }
        if (cropRect.right > getWidth()) {
            cropRect.offset(getWidth() - cropRect.right, 0);
        }
        if (cropRect.top < 0) {
            cropRect.offset(0, -cropRect.top);
        }
        if (cropRect.bottom > getHeight()) {
            cropRect.offset(0, getHeight() - cropRect.bottom);
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mode = Mode.ZOOM;
            // 检查触摸点是否在裁剪框内，如果是则准备缩放裁剪框
            return isCropMode || true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            
            if (isCropMode) {
                // 裁剪模式下，缩放裁剪框
                scaleCropRect(detector.getFocusX(), detector.getFocusY(), scaleFactor);
            } else {
                // 非裁剪模式下，缩放图像
                
                // 创建一个新的矩阵来计算当前的完整变换
                Matrix tempMatrix = new Matrix(matrix);
                
                // 尝试应用缩放因子
                tempMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
                
                // 获取应用缩放后的矩阵值
                float[] tempValues = new float[9];
                tempMatrix.getValues(tempValues);
                
                // 计算实际的缩放比例（考虑旋转等变换）
                float scaleX = tempValues[Matrix.MSCALE_X];
                float skewX = tempValues[Matrix.MSKEW_X];
                float currentScale = (float) Math.sqrt(scaleX * scaleX + skewX * skewX);
                
                // 检查是否在缩放范围内
                if (currentScale >= minScale && currentScale <= maxScale) {
                    // 如果在范围内，应用缩放
                    matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
                }
            }
            return true;
        }
    }
    
    /**
     * 缩放裁剪框
     * @param focusX 缩放焦点X坐标
     * @param focusY 缩放焦点Y坐标
     * @param scaleFactor 缩放因子
     */
    private void scaleCropRect(float focusX, float focusY, float scaleFactor) {
        // 只有自由裁剪模式下才能缩放改变宽高比
        boolean maintainRatio = cropRatio > 0;
        
        // 计算当前裁剪框的宽高
        float currentWidth = cropRect.width();
        float currentHeight = cropRect.height();
        
        // 计算新的宽高
        float newWidth = currentWidth * scaleFactor;
        float newHeight;
        
        if (maintainRatio) {
            // 保持宽高比
            newHeight = newWidth / cropRatio;
        } else {
            // 自由裁剪，宽高都缩放
            newHeight = currentHeight * scaleFactor;
        }
        
        // 确保裁剪框大小不小于最小限制
        if (newWidth < 50 || newHeight < 50) {
            return;
        }
        
        // 计算缩放前后的中心点偏移
        float centerX = cropRect.centerX();
        float centerY = cropRect.centerY();
        
        // 计算焦点相对于裁剪框中心的偏移比例
        float focusOffsetX = (focusX - centerX) / currentWidth;
        float focusOffsetY = (focusY - centerY) / currentHeight;
        
        // 计算新的中心点（考虑焦点位置）
        float newCenterX = focusX - (focusX - centerX) * scaleFactor;
        float newCenterY = focusY - (focusY - centerY) * scaleFactor;
        
        // 更新裁剪框
        cropRect.set(
            newCenterX - newWidth / 2,
            newCenterY - newHeight / 2,
            newCenterX + newWidth / 2,
            newCenterY + newHeight / 2
        );
        
        // 确保裁剪框不超出屏幕边界
        constrainCropRect();
    }

    private float getCurrentScale() {
        matrix.getValues(m);
        // 计算实际的缩放比例（考虑旋转等变换）
        float scaleX = m[Matrix.MSCALE_X];
        float skewX = m[Matrix.MSKEW_X];
        return (float) Math.sqrt(scaleX * scaleX + skewX * skewX);
    }
    
    /**
     * 调整图像亮度
     * @param brightness 亮度值，范围为-100到100
     */
    public void adjustBrightness(int brightness) {
        this.brightness = brightness;
        applyImageEffects();
    }
    
    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        // 保存原始图像的引用
        if (bm != null) {
            this.originalBitmap = bm.copy(bm.getConfig(), true);
        }
    }
    
    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        // 保存原始图像的引用
        if (drawable instanceof BitmapDrawable) {
            Bitmap bm = ((BitmapDrawable) drawable).getBitmap();
            if (bm != null) {
                this.originalBitmap = bm.copy(bm.getConfig(), true);
            }
        }
    }
    
    /**
     * 获取当前亮度值
     * @return 当前亮度值
     */
    public int getBrightness() {
        return brightness;
    }
    
    /**
     * 应用图像效果（亮度调节）
     */
    private void applyImageEffects() {
        try {
            // 初始化真正的原始图片（仅首次加载，永不覆盖）
            if (baseOriginalBitmap == null) {
                Drawable drawable = getDrawable();
                if (drawable instanceof BitmapDrawable) {
                    Bitmap bm = ((BitmapDrawable) drawable).getBitmap();
                    if (bm != null) {
                        baseOriginalBitmap = bm.copy(bm.getConfig(), true);
                    }
                }
                if (baseOriginalBitmap == null) return;
            }

            // 始终基于真正的原始图片创建新图
            final Bitmap brightenedBitmap = Bitmap.createBitmap(baseOriginalBitmap.getWidth(), baseOriginalBitmap.getHeight(), baseOriginalBitmap.getConfig());
            Canvas canvas = new Canvas(brightenedBitmap);

            Paint paint = new Paint();

            // 亮度系数计算（保留原有逻辑）
            float brightnessScale = 1 + (brightness / 100f);
            brightnessScale = Math.max(0.1f, Math.min(1.9f, brightnessScale));

            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.set(new float[]{
                    brightnessScale, 0, 0, 0, 0,
                    0, brightnessScale, 0, 0, 0,
                    0, 0, brightnessScale, 0, 0,
                    0, 0, 0, 1, 0
            });
            paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));

            // 绘制真正的原始图片（核心：不再用被覆盖的originalBitmap）
            canvas.drawBitmap(baseOriginalBitmap, 0, 0, paint);

            post(() -> {
                try {
                    if (brightenedBitmap != null && !brightenedBitmap.isRecycled()) {
                        Matrix currentMatrix = new Matrix(matrix);
                        // 此处调用setImageBitmap会覆盖originalBitmap，但不影响baseOriginalBitmap
                        setImageBitmap(brightenedBitmap);
                        setImageMatrix(currentMatrix);
                        invalidate();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setScaleRange(float minScale, float maxScale) {
        this.minScale = minScale;
        this.maxScale = maxScale;
    }
    
    /**
     * 顺时针旋转90°
     */
    public void rotateClockwise() {
        // 旋转矩阵（顺时针90°）
        matrix.postRotate(90, getWidth() / 2, getHeight() / 2);
        setImageMatrix(matrix);
        invalidate();
    }
    
    /**
     * 逆时针旋转90°
     */
    public void rotateCounterClockwise() {
        // 旋转矩阵（逆时针90°）
        matrix.postRotate(-90, getWidth() / 2, getHeight() / 2);
        setImageMatrix(matrix);
        invalidate();
    }
    
    /**
     * 旋转180°
     */
    public void rotate180() {
        // 旋转矩阵（180°）
        matrix.postRotate(180, getWidth() / 2, getHeight() / 2);
        setImageMatrix(matrix);
        invalidate();
    }
    
    /**
     * 水平翻转
     */
    public void flipHorizontal() {
        // 计算当前缩放因子
        float currentScale = getCurrentScale();
        
        // 水平翻转矩阵
        matrix.preScale(-1, 1, getWidth() / 2, getHeight() / 2);
        setImageMatrix(matrix);
        invalidate();
    }
    
    /**
     * 垂直翻转
     */
    public void flipVertical() {
        // 计算当前缩放因子
        float currentScale = getCurrentScale();
        
        // 垂直翻转矩阵
        matrix.preScale(1, -1, getWidth() / 2, getHeight() / 2);
        setImageMatrix(matrix);
        invalidate();
    }
    
    /**
     * 获取当前显示的包含所有变换的Bitmap
     * @return 当前显示的Bitmap，不包含黑边
     */
    public Bitmap getCurrentDisplayedBitmap() {
        // 获取原始图像
        Drawable drawable = getDrawable();
        if (!(drawable instanceof BitmapDrawable)) return null;
        
        Bitmap originalBitmap = ((BitmapDrawable) drawable).getBitmap();
        
        // 创建一个与原始图像大小相同的临时Bitmap
        Bitmap tempBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas tempCanvas = new Canvas(tempBitmap);
        
        // 绘制应用了所有变换的图像
        tempCanvas.drawColor(Color.TRANSPARENT);
        tempCanvas.drawBitmap(originalBitmap, matrix, null);
        
        // 计算实际图像的边界，去除透明区域（黑边）
        int top = tempBitmap.getHeight();
        int bottom = 0;
        int left = tempBitmap.getWidth();
        int right = 0;
        
        int[] pixels = new int[tempBitmap.getWidth() * tempBitmap.getHeight()];
        tempBitmap.getPixels(pixels, 0, tempBitmap.getWidth(), 0, 0, tempBitmap.getWidth(), tempBitmap.getHeight());
        
        for (int y = 0; y < tempBitmap.getHeight(); y++) {
            for (int x = 0; x < tempBitmap.getWidth(); x++) {
                int pixel = pixels[y * tempBitmap.getWidth() + x];
                // 检查像素是否不透明（不是完全透明的）
                if ((pixel & 0xFF000000) != 0) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
        }
        
        // 如果没有找到不透明像素，返回临时Bitmap
        if (top > bottom || left > right) {
            return tempBitmap;
        }
        
        // 裁剪出实际图像区域，去除透明黑边
        int cropWidth = right - left + 1;
        int cropHeight = bottom - top + 1;
        
        Bitmap croppedBitmap = Bitmap.createBitmap(tempBitmap, left, top, cropWidth, cropHeight);
        
        // 释放临时Bitmap的内存
        tempBitmap.recycle();
        
        return croppedBitmap;
    }
}