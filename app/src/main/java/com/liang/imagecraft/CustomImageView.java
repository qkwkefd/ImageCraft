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
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.List;

public class CustomImageView extends androidx.appcompat.widget.AppCompatImageView {

    private Matrix matrix = new Matrix();
    private float minScale = 0.3f;
    private float maxScale = 2.0f;

    private enum Mode {
        NONE, DRAG, ZOOM, CROP
    }
    
    /**
     * 获取包含所有修改的完整尺寸Bitmap
     * @return 完整尺寸的处理后Bitmap
     */
    public Bitmap getFullSizeProcessedBitmap() {
        if (baseOriginalBitmap == null) return null;
        
        try {
            // 创建一个与当前显示图片大小相同的临时Bitmap
            Bitmap processedBitmap = Bitmap.createBitmap(baseOriginalBitmap.getWidth(), baseOriginalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(processedBitmap);
            
            // 应用所有图像效果（亮度、对比度、滤镜）
            Paint paint = new Paint();
            
            // 亮度系数计算
            float brightnessScale = 1 + (brightness / 100f);
            brightnessScale = Math.max(0.1f, Math.min(1.9f, brightnessScale));
            
            // 对比度系数计算（范围-50到150转换为0.5到2.5的比例）
            float contrastScale = 1 + (contrast / 100f);
            contrastScale = Math.max(0.5f, Math.min(2.5f, contrastScale));
            
            // 1. 创建亮度和对比度的颜色矩阵
            ColorMatrix brightnessContrastMatrix = new ColorMatrix();
            brightnessContrastMatrix.set(new float[]{
                    brightnessScale * contrastScale, 0, 0, 0, (1-contrastScale)*128 * brightnessScale,
                    0, brightnessScale * contrastScale, 0, 0, (1-contrastScale)*128 * brightnessScale,
                    0, 0, brightnessScale * contrastScale, 0, (1-contrastScale)*128 * brightnessScale,
                    0, 0, 0, 1, 0
            });
            
            // 2. 获取当前滤镜的颜色矩阵
            ColorMatrix filterMatrix = getFilterColorMatrix();
            
            // 3. 合并颜色矩阵：先应用亮度对比度，再应用滤镜
            ColorMatrix combinedMatrix = new ColorMatrix();
            combinedMatrix.postConcat(brightnessContrastMatrix);
            combinedMatrix.postConcat(filterMatrix);
            
            // 4. 应用合并后的颜色矩阵
            paint.setColorFilter(new ColorMatrixColorFilter(combinedMatrix));
            
            // 绘制当前显示的图像（已包含旋转、裁剪和固定的贴纸效果）
            canvas.drawBitmap(baseOriginalBitmap, 0, 0, paint);
            
            // 如果有文字元素，绘制它们
            if (!textElements.isEmpty()) {
                // 计算当前视图与baseOriginalBitmap的比例
                float scaleX = (float) baseOriginalBitmap.getWidth() / getWidth();
                float scaleY = (float) baseOriginalBitmap.getHeight() / getHeight();
                
                Paint textPaint = new Paint();
                textPaint.setAntiAlias(true);
                
                for (TextElement textElement : textElements) {
                    canvas.save();
                    
                    // 转换文字位置到baseOriginalBitmap坐标
                    float origX = textElement.position.x * scaleX;
                    float origY = textElement.position.y * scaleY;
                    
                    // 转换文字大小和旋转角度
                    textPaint.setTextSize(textElement.textSize * scaleX);
                    textPaint.setColor(textElement.textColor);
                    
                    canvas.rotate(textElement.rotation, origX, origY);
                    canvas.scale(textElement.scale, textElement.scale, origX, origY);
                    
                    drawTextWithLineBreak(canvas, textElement.text, origX, origY, textPaint);
                    
                    canvas.restore();
                }
            }
            
            // 如果有未固定的贴纸元素（仍在编辑中），绘制它们
            if (!stickerElements.isEmpty()) {
                // 根据原始图片与当前视图的比例调整贴纸位置和大小
                float scaleX = (float) baseOriginalBitmap.getWidth() / getWidth();
                float scaleY = (float) baseOriginalBitmap.getHeight() / getHeight();
                
                // 初始化贴纸绘制画笔
                Paint stickerPaint = new Paint();
                stickerPaint.setAntiAlias(true);
                stickerPaint.setFilterBitmap(true);
                
                for (StickerElement stickerElement : stickerElements) {
                    // 获取贴纸资源
                    Drawable stickerDrawable = context.getResources().getDrawable(stickerElement.stickerResourceId);
                    if (stickerDrawable == null) continue;
                    
                    // 转换为Bitmap
                    Bitmap stickerBitmap;
                    if (stickerDrawable instanceof BitmapDrawable) {
                        stickerBitmap = ((BitmapDrawable) stickerDrawable).getBitmap();
                    } else {
                        // 处理非Bitmap的Drawable
                        stickerBitmap = Bitmap.createBitmap(
                                stickerDrawable.getIntrinsicWidth(),
                                stickerDrawable.getIntrinsicHeight(),
                                Bitmap.Config.ARGB_8888
                        );
                        Canvas tempCanvas = new Canvas(stickerBitmap);
                        stickerDrawable.setBounds(0, 0, tempCanvas.getWidth(), tempCanvas.getHeight());
                        stickerDrawable.draw(tempCanvas);
                    }
                    
                    // 保存当前画布状态
                    canvas.save();
                    
                    // 转换贴纸位置到baseOriginalBitmap坐标
                    float origX = stickerElement.position.x * scaleX;
                    float origY = stickerElement.position.y * scaleY;
                    
                    // 应用变换：平移、旋转、缩放
                    canvas.translate(origX, origY);
                    canvas.rotate(stickerElement.rotation, 0, 0);
                    
                    // 计算基于固定尺寸的缩放
                    float fixedScaleOnView = stickerElement.scaledFixedSize / Math.max(stickerElement.originalWidth, stickerElement.originalHeight);
                    float origScale = fixedScaleOnView * scaleX; // 转换到原始图片坐标的缩放
                    canvas.scale(origScale, origScale, 0, 0);
                    
                    // 设置透明度
                    stickerPaint.setAlpha(stickerElement.alpha);
                    
                    // 确保绘制的贴纸尺寸与原始尺寸一致
                    float targetWidth = stickerElement.originalWidth;
                    float targetHeight = stickerElement.originalHeight;
                    
                    // 如果当前bitmap尺寸与目标尺寸不一致，需要进行缩放
                    if (stickerBitmap.getWidth() != targetWidth || stickerBitmap.getHeight() != targetHeight) {
                        // 计算缩放比例
                        float scaleXLocal = targetWidth / stickerBitmap.getWidth();
                        float scaleYLocal = targetHeight / stickerBitmap.getHeight();
                        
                        // 保存当前画布状态
                        canvas.save();
                        
                        // 缩放到目标尺寸
                        canvas.scale(scaleXLocal, scaleYLocal, 0, 0);
                        
                        // 计算绘制位置（居中）
                        int x = -stickerBitmap.getWidth() / 2;
                        int y = -stickerBitmap.getHeight() / 2;
                        
                        // 绘制贴纸
                        canvas.drawBitmap(stickerBitmap, x, y, stickerPaint);
                        
                        // 恢复画布缩放状态
                        canvas.restore();
                    } else {
                        // 尺寸一致，直接绘制
                        int x = -stickerBitmap.getWidth() / 2;
                        int y = -stickerBitmap.getHeight() / 2;
                        canvas.drawBitmap(stickerBitmap, x, y, stickerPaint);
                    }
                    
                    // 恢复画布状态
                    canvas.restore();
                }
            }
            
            return processedBitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
    private int contrast = 0; // 对比度值，范围为-50到150
    private Bitmap originalBitmap = null; // 保存原始图像的引用
    // 新增：真正的原始图片（仅首次加载赋值，永不修改）
    private Bitmap baseOriginalBitmap = null;
    
    // 滤镜类型枚举
    public enum Filter {
        ORIGINAL, // 原图
        BLACK_WHITE, // 黑白
        VINTAGE, // 复古
        FRESH, // 清新
        WARM, // 暖色调
        COLD // 冷色调
    }
    
    private Filter currentFilter = Filter.ORIGINAL; // 当前选中的滤镜，默认为原图

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
    
    // 文字工具相关属性
    private boolean isTextMode = false;
    private List<TextElement> textElements = new ArrayList<>();
    private TextElement selectedTextElement = null;
    private Paint textBackgroundPaint;
    private Paint textBorderPaint;
    private Paint rotateHandlePaint;
    private static final int HANDLE_RADIUS = 20; // 旋转手柄的半径
    private static final int TOUCH_TOLERANCE = 30; // 触摸容差
    private static final int ROTATE_THRESHOLD = 40; // 旋转手柄的触摸阈值
    
    // 文字选择状态变化的回调接口
    public interface OnTextSelectionChangeListener {
        void onTextSelected(TextElement textElement);
        void onTextDeselected();
    }
    
    private OnTextSelectionChangeListener onTextSelectionChangeListener;
    
    // 设置文字选择状态变化的监听器
    public void setOnTextSelectionChangeListener(OnTextSelectionChangeListener listener) {
        this.onTextSelectionChangeListener = listener;
    }
    
    // 文字操作模式
    private enum TextMode {
        NONE,
        DRAG,
        SCALE,
        ROTATE,
        RESIZE
    }
    
    // 调整大小的角点枚举
    private enum ResizeCorner {
        NONE,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }
    
    // 贴纸元素类
    // 贴纸状态变化监听器接口
    public interface OnStickerStateChangeListener {
        void onStickerAdded();
        void onStickerSelected();
    }

    private OnStickerStateChangeListener stickerStateChangeListener;

    // 设置贴纸状态变化监听器
    public void setOnStickerStateChangeListener(OnStickerStateChangeListener listener) {
        this.stickerStateChangeListener = listener;
    }

    // 定义贴纸的固定基准尺寸常量
    private static final float STICKER_FIXED_BASE_SIZE = 150.0f; // 固定基准尺寸（像素）
    
    private static class StickerElement {
        int stickerResourceId; // 贴纸资源ID
        PointF position; // 贴纸位置
        float scale; // 贴纸缩放比例
        float rotation; // 贴纸旋转角度
        int alpha; // 贴纸透明度
        float originalWidth; // 原始宽度
        float originalHeight; // 原始高度
        float fixedBaseSize; // 固定基准尺寸
        float scaledFixedSize; // 缩放后的固定尺寸
        
        public StickerElement(int stickerResourceId, PointF position, Context context) {
            this.stickerResourceId = stickerResourceId;
            this.position = position;
            this.scale = 1.0f;
            this.rotation = 0.0f;
            this.alpha = 255;
            this.fixedBaseSize = STICKER_FIXED_BASE_SIZE;
            
            // 获取贴纸原始尺寸
            Drawable drawable = context.getResources().getDrawable(stickerResourceId);
            if (drawable != null) {
                if (drawable instanceof BitmapDrawable) {
                    // 如果是BitmapDrawable，直接获取Bitmap的实际宽高
                    Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                    this.originalWidth = bitmap.getWidth();
                    this.originalHeight = bitmap.getHeight();
                } else {
                    // 对于非Bitmap的Drawable，创建临时Bitmap获取实际尺寸
                    Bitmap tempBitmap = Bitmap.createBitmap(
                            drawable.getIntrinsicWidth(),
                            drawable.getIntrinsicHeight(),
                            Bitmap.Config.ARGB_8888
                    );
                    Canvas canvas = new Canvas(tempBitmap);
                    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                    drawable.draw(canvas);
                    
                    // 使用临时Bitmap的实际宽高
                    this.originalWidth = tempBitmap.getWidth();
                    this.originalHeight = tempBitmap.getHeight();
                    
                    // 释放临时Bitmap资源
                    if (!tempBitmap.isRecycled()) {
                        tempBitmap.recycle();
                    }
                }
            } else {
                this.originalWidth = 100;
                this.originalHeight = 100;
            }
            
            // 计算缩放后的固定尺寸
                updateScaledFixedSize();
            }
            
            /**
             * 更新缩放后的固定尺寸
             */
            public void updateScaledFixedSize() {
                // 计算缩放后的固定尺寸
                this.scaledFixedSize = this.fixedBaseSize * this.scale;
            }
            
            /**
             * 设置缩放比例并更新缩放后的固定尺寸
             */
            public void setScale(float scale) {
                this.scale = scale;
                updateScaledFixedSize();
            }
        
        // 获取贴纸的边界矩形
        public RectF getBounds() {
            float scaledWidth = originalWidth * scale;
            float scaledHeight = originalHeight * scale;
            
            return new RectF(
                position.x - scaledWidth / 2,
                position.y - scaledHeight / 2,
                position.x + scaledWidth / 2,
                position.y + scaledHeight / 2
            );
        }
        
        // 获取旋转后的边界矩形
        public RectF getRotatedBounds() {
            RectF bounds = getBounds();
            
            // 计算旋转后的四个角点
            PointF[] points = new PointF[4];
            points[0] = new PointF(bounds.left, bounds.top);
            points[1] = new PointF(bounds.right, bounds.top);
            points[2] = new PointF(bounds.right, bounds.bottom);
            points[3] = new PointF(bounds.left, bounds.bottom);
            
            // 应用旋转
            for (int i = 0; i < 4; i++) {
                points[i] = rotatePoint(points[i], position, rotation);
            }
            
            // 计算旋转后的边界
            float left = Math.min(Math.min(points[0].x, points[1].x), Math.min(points[2].x, points[3].x));
            float top = Math.min(Math.min(points[0].y, points[1].y), Math.min(points[2].y, points[3].y));
            float right = Math.max(Math.max(points[0].x, points[1].x), Math.max(points[2].x, points[3].x));
            float bottom = Math.max(Math.max(points[0].y, points[1].y), Math.max(points[2].y, points[3].y));
            
            return new RectF(left, top, right, bottom);
        }
        
        // 获取左上角位置
        public PointF getTopLeftCorner() {
            RectF bounds = getBounds();
            return rotatePoint(new PointF(bounds.left, bounds.top), position, rotation);
        }
        
        // 获取右上角位置
        public PointF getTopRightCorner() {
            RectF bounds = getBounds();
            return rotatePoint(new PointF(bounds.right, bounds.top), position, rotation);
        }
        
        // 获取右下角位置
        public PointF getBottomRightCorner() {
            RectF bounds = getBounds();
            return rotatePoint(new PointF(bounds.right, bounds.bottom), position, rotation);
        }
        
        // 获取左下角位置
        public PointF getBottomLeftCorner() {
            RectF bounds = getBounds();
            return rotatePoint(new PointF(bounds.left, bounds.bottom), position, rotation);
        }
        
        // 获取旋转手柄的位置
        public PointF getRotateHandlePosition() {
            // 使用未旋转的边界计算手柄距离，确保手柄始终位于贴纸上方中心
            float scaledWidth = originalWidth * scale;
            float scaledHeight = originalHeight * scale;
            // 与drawStickerSelection方法保持一致，使用最大边长+80像素
            float handleDistance = (Math.max(scaledWidth, scaledHeight) / 2) + 80;
            float radians = (float) Math.toRadians(rotation + 90);
            
            return new PointF(
                position.x + (float) Math.cos(radians) * handleDistance,
                position.y + (float) Math.sin(radians) * handleDistance
            );
        }
        
        // 辅助方法：旋转点
        private PointF rotatePoint(PointF point, PointF center, float angle) {
            double radians = Math.toRadians(angle);
            float cos = (float) Math.cos(radians);
            float sin = (float) Math.sin(radians);
            
            float x = point.x - center.x;
            float y = point.y - center.y;
            
            float rotatedX = x * cos - y * sin;
            float rotatedY = x * sin + y * cos;
            
            return new PointF(rotatedX + center.x, rotatedY + center.y);
        }
    }
    
    // 贴纸相关属性
    private List<StickerElement> stickerElements = new ArrayList<>();
    private StickerElement selectedStickerElement = null;
    private Paint stickerPaint;
    
    // 贴纸操作模式
    private enum StickerMode {
        NONE,
        DRAG,
        SCALE,
        ROTATE
    }
    
    private StickerMode stickerMode = StickerMode.NONE;
    private PointF stickerLastTouchPoint;
    private float initialStickerScale;
    private float initialAngle;
    
    // 贴纸操作常量
    private TextMode textMode = TextMode.NONE;
    private PointF textStart = new PointF();
    private float lastRotateAngle = 0;
    private float lastScale = 1.0f;
    private ResizeCorner currentResizeCorner = ResizeCorner.NONE;
    private float lastResizeDistance = 0;
    
    // 文字元素类
    public class TextElement {
        public String text;
        public PointF position;
        public float rotation = 0;
        public float scale = 1.0f;
        public int textColor = Color.WHITE;
        public float textSize = 60; // 字体初始大小增大两倍（从30改为60）
        public String fontName = "simsun"; // 初始字体设置为宋体
        public float maxWidth = 400; // 文本框最大宽度，可根据需要调整
        public int alpha = 255; // 透明度，范围0-255，默认不透明
        
        public TextElement(String text, PointF position) {
            this.text = text;
            this.position = position;
        }
        
        // 获取文字的边界矩形
        public RectF getBounds(Paint paint) {
            paint.setTextSize(textSize * scale);
            
            // 计算换行后的文字边界
            float maxLineWidth = maxWidth * scale;
            String[] lines = wrapText(text, maxLineWidth, paint);
            
            float textWidth = maxLineWidth; // 使用maxWidth作为文本框宽度
            float lineHeight = -paint.ascent() + paint.descent();
            float textHeight = lineHeight * lines.length;
            
            // 计算基线偏移量：ascent是负数，descent是正数
            // 文字的实际顶部位置是baseline + ascent，底部是baseline + descent
            float ascent = paint.ascent() * scale;
            float descent = paint.descent() * scale;
            
            // 调整文本框的边界，使虚线框向上且向左移动
            float verticalOffset = (descent - ascent) * 0.25f;  // 向上调整25%（比之前多5%）
            float horizontalOffset = maxLineWidth * 0.04f;     // 向左调整4%
            
            return new RectF(
                position.x - maxLineWidth / 2 - horizontalOffset, // 左边界（向左移动）
                position.y - textHeight / 2 - verticalOffset,     // 上边界（向上移动）
                position.x + maxLineWidth / 2 - horizontalOffset, // 右边界（同时向左移动，保持宽度不变）
                position.y + textHeight / 2 - verticalOffset      // 下边界（同时向上移动，保持高度不变）
            );
        }
        
        // 获取调整大小的角点位置
        public PointF getTopLeftCorner(Paint paint) {
            RectF bounds = getBounds(paint);
            return new PointF(bounds.left, bounds.top);
        }
        
        public PointF getTopRightCorner(Paint paint) {
            RectF bounds = getBounds(paint);
            return new PointF(bounds.right, bounds.top);
        }
        
        public PointF getBottomLeftCorner(Paint paint) {
            RectF bounds = getBounds(paint);
            return new PointF(bounds.left, bounds.bottom);
        }
        
        public PointF getBottomRightCorner(Paint paint) {
            RectF bounds = getBounds(paint);
            return new PointF(bounds.right, bounds.bottom);
        }
        
        // 根据调整大小的角点更新文字框大小
        public void resize(ResizeCorner corner, float newWidth, float newHeight) {
            // 更新maxWidth（实际宽度），保持textSize不变
            float oldWidth = maxWidth * scale;
            float oldHeight = getBounds(new Paint()).height();
            
            // 计算新的scale值，保持文字大小相对不变
            float scaleX = newWidth / oldWidth;
            scale *= scaleX;
            
            // 更新textSize，确保文字大小与框大小成正比
            textSize = Math.max(20, Math.min(120, textSize * scaleX));
        }
        
        // 获取旋转手柄的位置
        public PointF getRotateHandlePosition() {
            RectF bounds = getBounds(new Paint());
            float handleDistance = Math.max(bounds.width(), bounds.height()) / 2 + ROTATE_THRESHOLD;
            float radians = (float) Math.toRadians(rotation + 90);
            
            return new PointF(
                position.x + (float) Math.cos(radians) * handleDistance,
                position.y + (float) Math.sin(radians) * handleDistance
            );
        }
    }

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
        
        // 初始化文字相关的画笔
        initTextPaints();
    }
    
    /**
     * 初始化文字相关的画笔
     */
    private void initTextPaints() {
        // 文字背景画笔（半透明黑色）
        textBackgroundPaint = new Paint();
        textBackgroundPaint.setColor(Color.argb(50, 0, 0, 0));
        textBackgroundPaint.setStyle(Paint.Style.FILL);
        
        // 文字边框画笔
        textBorderPaint = new Paint();
        textBorderPaint.setColor(Color.WHITE);
        textBorderPaint.setStrokeWidth(2f);
        textBorderPaint.setStyle(Paint.Style.STROKE);
        textBorderPaint.setPathEffect(new DashPathEffect(new float[]{5, 5}, 0));
        textBorderPaint.setAntiAlias(true);
        
        // 旋转手柄画笔
        rotateHandlePaint = new Paint();
        rotateHandlePaint.setColor(Color.CYAN);
        rotateHandlePaint.setStyle(Paint.Style.FILL);
        rotateHandlePaint.setAntiAlias(true);
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
        
        // 绘制所有文字元素
        drawTextElements(canvas);
        
        // 绘制所有贴纸元素
        drawStickerElements(canvas);
    }
    
    /**
     * 绘制所有文字元素
     */
    private void drawTextElements(Canvas canvas) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(60); // 字体初始大小增大两倍（从30改为60）
        
        for (TextElement textElement : textElements) {
            boolean isSelected = textElement == selectedTextElement;
            
            // 保存当前画布状态
            canvas.save();
            
            // 应用旋转和缩放变换
            canvas.rotate(textElement.rotation, textElement.position.x, textElement.position.y);
            canvas.scale(textElement.scale, textElement.scale, textElement.position.x, textElement.position.y);
            
            // 设置文字颜色和透明度
            paint.setColor(textElement.textColor);
            paint.setAlpha(textElement.alpha);
            paint.setTextSize(textElement.textSize);
            
            // 设置字体
            Typeface typeface;
            switch (textElement.fontName) {
                case "simsun":
                    typeface = Typeface.create("simsun", Typeface.NORMAL);
                    break;
                case "simhei":
                    typeface = Typeface.create("simhei", Typeface.NORMAL);
                    break;
                case "microsoft yahei":
                    typeface = Typeface.create("microsoft yahei", Typeface.NORMAL);
                    break;
                case "kaiti":
                    typeface = Typeface.create("kaiti", Typeface.NORMAL);
                    break;
                case "dengxian":
                    typeface = Typeface.create("dengxian", Typeface.NORMAL);
                    break;
                default: // 默认宋体
                    typeface = Typeface.create("simsun", Typeface.NORMAL);
                    break;
            }
            paint.setTypeface(typeface);
            
            // 绘制文字（支持换行）
            drawTextWithLineBreak(canvas, textElement.text, textElement.position.x, textElement.position.y, paint);
            
            // 恢复画布状态
            canvas.restore();
            
            // 如果是选中的文字元素，绘制边框和旋转手柄
            if (isSelected) {
                drawTextSelection(canvas, textElement, paint);
            }
        }
    }
    
    /**
     * 绘制所有贴纸元素
     */
    private void drawStickerElements(Canvas canvas) {
        if (stickerElements.isEmpty()) return;
        
        // 初始化贴纸绘制画笔
        if (stickerPaint == null) {
            stickerPaint = new Paint();
            stickerPaint.setAntiAlias(true);
            stickerPaint.setFilterBitmap(true);
        }
        
        for (StickerElement stickerElement : stickerElements) {
            // 获取贴纸资源
            Drawable stickerDrawable = context.getResources().getDrawable(stickerElement.stickerResourceId);
            if (stickerDrawable == null) continue;
            
            // 转换为Bitmap
            Bitmap stickerBitmap;
            if (stickerDrawable instanceof BitmapDrawable) {
                stickerBitmap = ((BitmapDrawable) stickerDrawable).getBitmap();
            } else {
                // 处理非Bitmap的Drawable
                stickerBitmap = Bitmap.createBitmap(
                        stickerDrawable.getIntrinsicWidth(),
                        stickerDrawable.getIntrinsicHeight(),
                        Bitmap.Config.ARGB_8888
                );
                Canvas tempCanvas = new Canvas(stickerBitmap);
                stickerDrawable.setBounds(0, 0, tempCanvas.getWidth(), tempCanvas.getHeight());
                stickerDrawable.draw(tempCanvas);
            }
            
            // 保存当前画布状态
            canvas.save();
            
            // 应用变换：平移、旋转、缩放
            canvas.translate(stickerElement.position.x, stickerElement.position.y);
            canvas.rotate(stickerElement.rotation, 0, 0);
            canvas.scale(stickerElement.scale, stickerElement.scale, 0, 0);
            
            // 设置透明度
            stickerPaint.setAlpha(stickerElement.alpha);
            
            // 确保绘制的贴纸尺寸与虚线框计算的原始尺寸一致
            float targetWidth = stickerElement.originalWidth;
            float targetHeight = stickerElement.originalHeight;
            
            // 如果当前bitmap尺寸与目标尺寸不一致，需要进行缩放
            if (stickerBitmap.getWidth() != targetWidth || stickerBitmap.getHeight() != targetHeight) {
                // 计算缩放比例
                float scaleX = targetWidth / stickerBitmap.getWidth();
                float scaleY = targetHeight / stickerBitmap.getHeight();
                
                // 保存当前画布状态
                canvas.save();
                
                // 缩放到目标尺寸
                canvas.scale(scaleX, scaleY, 0, 0);
                
                // 计算绘制位置（居中）
                int x = -stickerBitmap.getWidth() / 2;
                int y = -stickerBitmap.getHeight() / 2;
                
                // 绘制贴纸
                canvas.drawBitmap(stickerBitmap, x, y, stickerPaint);
                
                // 恢复画布缩放状态
                canvas.restore();
            } else {
                // 尺寸一致，直接绘制
                int x = -stickerBitmap.getWidth() / 2;
                int y = -stickerBitmap.getHeight() / 2;
                canvas.drawBitmap(stickerBitmap, x, y, stickerPaint);
            }
            
            // 恢复画布状态
            canvas.restore();
        }
        
        // 绘制选中贴纸的边框和控制点
        if (selectedStickerElement != null) {
            drawStickerSelection(canvas, selectedStickerElement);
        }
    }
    
    // 绘制选中贴纸的边框和控制点
    private void drawStickerSelection(Canvas canvas, StickerElement sticker) {
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));
        
        // 计算相对于贴纸中心的边界矩形（未旋转、未缩放）
        float scaledWidth = sticker.originalWidth * sticker.scale;
        float scaledHeight = sticker.originalHeight * sticker.scale;
        RectF bounds = new RectF(
            -scaledWidth / 2, -scaledHeight / 2,
            scaledWidth / 2, scaledHeight / 2
        );
        
        // 保存画布状态
        canvas.save();
        
        // 平移到贴纸中心
        canvas.translate(sticker.position.x, sticker.position.y);
        
        // 旋转画布到贴纸的旋转角度
        canvas.rotate(sticker.rotation, 0, 0);
        
        // 绘制虚线框
        canvas.drawRect(bounds, paint);
        
        // 绘制四个角点
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLUE);
        
        final int CORNER_SIZE = 30;
        // 绘制左上角
        canvas.drawRect(bounds.left, bounds.top, 
                bounds.left + CORNER_SIZE, bounds.top + CORNER_SIZE, paint);
        // 绘制右上角
        canvas.drawRect(bounds.right - CORNER_SIZE, bounds.top, 
                bounds.right, bounds.top + CORNER_SIZE, paint);
        // 绘制右下角
        canvas.drawRect(bounds.right - CORNER_SIZE, bounds.bottom - CORNER_SIZE, 
                bounds.right, bounds.bottom, paint);
        // 绘制左下角
        canvas.drawRect(bounds.left, bounds.bottom - CORNER_SIZE, 
                bounds.left + CORNER_SIZE, bounds.bottom, paint);
        
        // 绘制旋转手柄
        paint.setColor(Color.RED);
        // 增加旋转锚点与中心的距离，使旋转更慢、更精确
        float handleDistance = (Math.max(scaledWidth, scaledHeight) / 2) + 80; // 增加距离到80像素
        // 不需要再加上sticker.rotation，因为画布已经旋转过了
        PointF rotateHandle = new PointF(
            (float) (Math.cos(Math.toRadians(90)) * handleDistance),
            (float) (Math.sin(Math.toRadians(90)) * handleDistance)
        );
        canvas.drawCircle(rotateHandle.x, rotateHandle.y, CORNER_SIZE / 2, paint);
        
        // 绘制旋转手柄连接线
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(2);
        paint.setPathEffect(null);
        canvas.drawLine(0, 0, rotateHandle.x, rotateHandle.y, paint);
        
        // 恢复画布状态
        canvas.restore();
    }
    
    /**
     * 添加贴纸元素
     */
    public void addStickerElement(int stickerResourceId) {
        // 默认将贴纸添加到视图中心
        PointF center = new PointF(getWidth() / 2f, getHeight() / 2f);
        StickerElement newSticker = new StickerElement(stickerResourceId, center, context);
        
        // 计算合适的缩放比例，使贴纸以固定尺寸显示
        float scale = STICKER_FIXED_BASE_SIZE / Math.max(newSticker.originalWidth, newSticker.originalHeight);
        newSticker.setScale(scale);
        
        stickerElements.add(newSticker);
        
        // 选中新添加的贴纸
        selectedStickerElement = newSticker;
        
        // 通知监听器贴纸已添加
        if (stickerStateChangeListener != null) {
            stickerStateChangeListener.onStickerAdded();
        }
        
        // 重绘视图
        invalidate();
    }
    
    /**
     * 获取当前选中的贴纸元素
     */
    public StickerElement getSelectedStickerElement() {
        return selectedStickerElement;
    }
    
    /**
     * 删除当前选中的贴纸元素
     */
    public void removeSelectedStickerElement() {
        if (selectedStickerElement != null) {
            stickerElements.remove(selectedStickerElement);
            selectedStickerElement = null;
            invalidate();
        }
    }
    
    /**
     * 确认所有贴纸元素（将它们固定到图像上）
     */
    public void confirmStickerElements() {
        if (stickerElements.isEmpty() || baseOriginalBitmap == null) {
            selectedStickerElement = null;
            invalidate();
            return;
        }
        
        try {
            // 1. 保存当前所有效果参数
            int currentBrightness = brightness;
            int currentContrast = contrast;
            Filter tempFilter = currentFilter;
            
            // 2. 创建一个与原始图片大小相同的临时Bitmap
            Bitmap processedBitmap = Bitmap.createBitmap(baseOriginalBitmap.getWidth(), baseOriginalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(processedBitmap);
            
            // 3. 绘制原始图片
            Paint paint = new Paint();
            canvas.drawBitmap(baseOriginalBitmap, 0, 0, paint);
            
            // 4. 计算视图与原始图片的比例
            float scaleX = (float) baseOriginalBitmap.getWidth() / getWidth();
            float scaleY = (float) baseOriginalBitmap.getHeight() / getHeight();
            
            // 5. 初始化贴纸绘制画笔
            Paint stickerPaint = new Paint();
            stickerPaint.setAntiAlias(true);
            stickerPaint.setFilterBitmap(true);
            
            // 6. 绘制所有贴纸元素到临时Bitmap
            for (StickerElement stickerElement : stickerElements) {
                // 获取贴纸资源
                Drawable stickerDrawable = context.getResources().getDrawable(stickerElement.stickerResourceId);
                if (stickerDrawable == null) continue;
                
                // 转换为Bitmap
                Bitmap stickerBitmap;
                if (stickerDrawable instanceof BitmapDrawable) {
                    stickerBitmap = ((BitmapDrawable) stickerDrawable).getBitmap();
                } else {
                    // 处理非Bitmap的Drawable
                    stickerBitmap = Bitmap.createBitmap(
                            stickerDrawable.getIntrinsicWidth(),
                            stickerDrawable.getIntrinsicHeight(),
                            Bitmap.Config.ARGB_8888
                    );
                    Canvas tempCanvas = new Canvas(stickerBitmap);
                    stickerDrawable.setBounds(0, 0, tempCanvas.getWidth(), tempCanvas.getHeight());
                    stickerDrawable.draw(tempCanvas);
                }
                
                // 保存当前画布状态
                canvas.save();
                
                // 转换贴纸位置到原始图片坐标
                float origX = stickerElement.position.x * scaleX;
                float origY = stickerElement.position.y * scaleY;
                
                // 应用变换：平移、旋转、缩放
                canvas.translate(origX, origY);
                canvas.rotate(stickerElement.rotation, 0, 0);
                
                // 贴纸缩放需要考虑视图到原始图片的缩放
                float origScale = stickerElement.scale * scaleX; // 假设X和Y方向缩放比例相同
                canvas.scale(origScale, origScale, 0, 0);
                
                // 设置透明度
                stickerPaint.setAlpha(stickerElement.alpha);
                
                // 使用固定尺寸计算绘制大小
                float scaleRatio = stickerElement.scaledFixedSize / Math.max(stickerElement.originalWidth, stickerElement.originalHeight);
                float targetWidth = stickerElement.originalWidth * scaleRatio;
                float targetHeight = stickerElement.originalHeight * scaleRatio;
                
                // 如果当前bitmap尺寸与目标尺寸不一致，需要进行缩放
                if (stickerBitmap.getWidth() != targetWidth || stickerBitmap.getHeight() != targetHeight) {
                    // 计算缩放比例
                    float scaleXLocal = targetWidth / stickerBitmap.getWidth();
                    float scaleYLocal = targetHeight / stickerBitmap.getHeight();
                    
                    // 保存当前画布状态
                    canvas.save();
                    
                    // 缩放到目标尺寸
                    canvas.scale(scaleXLocal, scaleYLocal, 0, 0);
                    
                    // 计算绘制位置（居中）
                    int x = -stickerBitmap.getWidth() / 2;
                    int y = -stickerBitmap.getHeight() / 2;
                    
                    // 绘制贴纸
                    canvas.drawBitmap(stickerBitmap, x, y, stickerPaint);
                    
                    // 恢复画布缩放状态
                    canvas.restore();
                } else {
                    // 尺寸一致，直接绘制
                    int x = -stickerBitmap.getWidth() / 2;
                    int y = -stickerBitmap.getHeight() / 2;
                    canvas.drawBitmap(stickerBitmap, x, y, stickerPaint);
                }
                
                // 恢复画布状态
                canvas.restore();
            }
            
            // 7. 更新baseOriginalBitmap为包含贴纸的新Bitmap
            if (baseOriginalBitmap != null && !baseOriginalBitmap.isRecycled()) {
                baseOriginalBitmap.recycle();
            }
            baseOriginalBitmap = processedBitmap;
            
            // 8. 重新应用所有图像效果
            // 重置效果参数（否则applyImageEffects不会重新应用）
            brightness = 0;
            contrast = 0;
            currentFilter = Filter.ORIGINAL;
            
            // 重新应用效果
            brightness = currentBrightness;
            contrast = currentContrast;
            this.currentFilter = tempFilter;
            applyImageEffects();
            
            // 9. 清空贴纸元素列表，使其无法再被选中和编辑
            stickerElements.clear();
            selectedStickerElement = null;
            
            // 10. 重新绘制视图
            invalidate();
        } catch (Exception e) {
            e.printStackTrace();
            // 如果出现错误，至少清除选中状态
            selectedStickerElement = null;
            invalidate();
        }
    }
    
    /**
     * 处理贴纸模式下的触摸事件
     */
    private boolean handleStickerTouchEvent(MotionEvent event) {
        PointF curr = new PointF(event.getX(), event.getY());
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 检查是否点击了旋转手柄
                if (selectedStickerElement != null) {
                    PointF handlePos = selectedStickerElement.getRotateHandlePosition();
                    if (getDistance(curr, handlePos) <= ROTATE_THRESHOLD) {
                        stickerMode = StickerMode.ROTATE;
                        lastRotateAngle = calculateAngle(selectedStickerElement.position, curr);
                        return true;
                    }
                    
                    // 检查是否点击了缩放角点
                    PointF[] corners = new PointF[]{
                        selectedStickerElement.getTopLeftCorner(),
                        selectedStickerElement.getTopRightCorner(),
                        selectedStickerElement.getBottomRightCorner(),
                        selectedStickerElement.getBottomLeftCorner()
                    };
                    
                    final int CORNER_SIZE = 30;
                    for (int i = 0; i < corners.length; i++) {
                        PointF corner = corners[i];
                        if (Math.abs(curr.x - corner.x) <= CORNER_SIZE && Math.abs(curr.y - corner.y) <= CORNER_SIZE) {
                            stickerMode = StickerMode.SCALE;
                            initialStickerScale = selectedStickerElement.scale;
                            // 记录点击时的初始距离（贴纸中心到当前点击位置的距离）
                            lastResizeDistance = getDistance(selectedStickerElement.position, curr);
                            return true;
                        }
                    }
                }
                
                // 检查是否点击了贴纸元素
                StickerElement clickedSticker = findStickerElementAtPosition(curr);
                if (clickedSticker != null) {
                    selectedStickerElement = clickedSticker;
                    stickerMode = StickerMode.DRAG;
                    stickerLastTouchPoint = new PointF(curr.x, curr.y);
                    
                    // 通知监听器贴纸已选中
                    if (stickerStateChangeListener != null) {
                        stickerStateChangeListener.onStickerSelected();
                    }
                    
                    invalidate();
                    return true;
                } else {
                    // 点击空白处，取消选中
                    if (selectedStickerElement != null) {
                        selectedStickerElement = null;
                        stickerMode = StickerMode.NONE;
                        invalidate();
                        return true;
                    }
                }
                break;
                
            case MotionEvent.ACTION_MOVE:
                if (selectedStickerElement == null) break;
                
                switch (stickerMode) {
                    case DRAG:
                        // 拖动贴纸
                        float deltaX = curr.x - stickerLastTouchPoint.x;
                        float deltaY = curr.y - stickerLastTouchPoint.y;
                        selectedStickerElement.position.x += deltaX;
                        selectedStickerElement.position.y += deltaY;
                        stickerLastTouchPoint = new PointF(curr.x, curr.y);
                        invalidate();
                        return true;
                        
                    case SCALE:
                        // 缩放贴纸
                        float currentAngle = calculateAngle(selectedStickerElement.position, curr);
                        float angleDiff = currentAngle - lastRotateAngle;
                        
                        // 计算当前点到中心点的距离
                        float currentDistance = getDistance(selectedStickerElement.position, curr);
                        
                        // 计算缩放比例，考虑角度变化
                        float scaleFactor = currentDistance / lastResizeDistance;
                        float newScale = initialStickerScale * scaleFactor;
                        
                        // 限制缩放范围
                        newScale = Math.max(0.1f, Math.min(5.0f, newScale));
                        selectedStickerElement.setScale(newScale);
                        invalidate();
                        return true;
                        
                    case ROTATE:
                        // 旋转贴纸
                        float newAngle = calculateAngle(selectedStickerElement.position, curr);
                        float rotationDiff = newAngle - lastRotateAngle;
                        selectedStickerElement.rotation += rotationDiff;
                        lastRotateAngle = newAngle;
                        invalidate();
                        return true;
                }
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                stickerMode = StickerMode.NONE;
                break;
        }
        
        return false;
    }
    
    /**
     * 在指定位置查找贴纸元素
     */
    private StickerElement findStickerElementAtPosition(PointF position) {
        // 从后往前检查，确保最新添加的贴纸优先被选中
        for (int i = stickerElements.size() - 1; i >= 0; i--) {
            StickerElement sticker = stickerElements.get(i);
            RectF bounds = sticker.getRotatedBounds();
            if (bounds.contains(position.x, position.y)) {
                return sticker;
            }
        }
        return null;
    }
    
    /**
     * 计算两点之间的距离
     */
    private float getDistance(PointF p1, PointF p2) {
        return (float) Math.sqrt(Math.pow(p2.x - p1.x, 2) + Math.pow(p2.y - p1.y, 2));
    }
    
    /**
     * 计算两点之间的角度
     */
    private float calculateAngle(PointF center, PointF point) {
        return (float) Math.toDegrees(Math.atan2(point.y - center.y, point.x - center.x));
    }
    
    /**
     * 根据指定宽度自动换行文字
     */
    private String[] wrapText(String text, float maxWidth, Paint paint) {
        ArrayList<String> lines = new ArrayList<>();
        String[] paragraphs = text.split("\\n");
        
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            
            String[] words = paragraph.split(" ");
            StringBuilder currentLine = new StringBuilder(words[0]);
            
            for (int i = 1; i < words.length; i++) {
                String testLine = currentLine.toString() + " " + words[i];
                float testLineWidth = paint.measureText(testLine);
                
                if (testLineWidth <= maxWidth) {
                    currentLine.append(" " + words[i]);
                } else {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(words[i]);
                }
            }
            lines.add(currentLine.toString());
        }
        
        return lines.toArray(new String[0]);
    }
    
    /**
     * 绘制支持换行的文字
     */
    private void drawTextWithLineBreak(Canvas canvas, String text, float x, float y, Paint paint) {
        // 获取当前文字元素的maxWidth
        float maxLineWidth = 0;
        if (selectedTextElement != null && text.equals(selectedTextElement.text)) {
            maxLineWidth = selectedTextElement.maxWidth * selectedTextElement.scale;
        } else {
            // 查找当前绘制的文字元素
            for (TextElement element : textElements) {
                if (element.text.equals(text) && element.position.x == x && element.position.y == y) {
                    maxLineWidth = element.maxWidth * element.scale;
                    break;
                }
            }
        }
        
        // 如果找不到对应的文字元素，使用默认宽度
        if (maxLineWidth == 0) {
            maxLineWidth = 400;
        }
        
        // 自动换行处理
        String[] lines = wrapText(text, maxLineWidth, paint);
        float lineHeight = -paint.ascent() + paint.descent();
        float startY = y - (lines.length - 1) * lineHeight / 2;
        
        // 左对齐绘制文字
        for (int i = 0; i < lines.length; i++) {
            // 左对齐：不减去文字宽度的一半
            float lineX = x - maxLineWidth / 2; // 左对齐到文本框左侧
            float lineY = startY + i * lineHeight;
            canvas.drawText(lines[i], lineX, lineY, paint);
        }
    }
    
    /**
     * 绘制文字选中状态（边框、旋转手柄和调整大小手柄）
     */
    private void drawTextSelection(Canvas canvas, TextElement textElement, Paint paint) {
        // 获取文字边界
        RectF bounds = textElement.getBounds(paint);
        
        // 保存当前画布状态
        canvas.save();
        
        // 应用旋转变换，使虚线框随文字一起旋转
        canvas.rotate(textElement.rotation, textElement.position.x, textElement.position.y);
        
        // 绘制背景
        canvas.drawRect(bounds, textBackgroundPaint);
        
        // 绘制边框
        canvas.drawRect(bounds, textBorderPaint);
        
        // 绘制四个角的调整大小手柄
        paint.setColor(Color.CYAN);
        paint.setStyle(Paint.Style.FILL);
        
        // 左上角
        canvas.drawCircle(bounds.left, bounds.top, HANDLE_RADIUS, paint);
        // 右上角
        canvas.drawCircle(bounds.right, bounds.top, HANDLE_RADIUS, paint);
        // 左下角
        canvas.drawCircle(bounds.left, bounds.bottom, HANDLE_RADIUS, paint);
        // 右下角
        canvas.drawCircle(bounds.right, bounds.bottom, HANDLE_RADIUS, paint);
        
        // 恢复画布状态
        canvas.restore();
        
        // 获取旋转手柄位置
        PointF handlePos = textElement.getRotateHandlePosition();
        
        // 绘制旋转手柄连接线
        paint.setColor(Color.CYAN);
        paint.setStrokeWidth(2f);
        canvas.drawLine(textElement.position.x, textElement.position.y, handlePos.x, handlePos.y, paint);
        
        // 绘制旋转手柄（圆形）
        canvas.drawCircle(handlePos.x, handlePos.y, HANDLE_RADIUS, rotateHandlePaint);
        
        // 绘制旋转符号
        paint.setColor(Color.WHITE);
        paint.setTextSize(16);
        canvas.drawText("↻", handlePos.x - 8, handlePos.y + 6, paint);
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
        
        // 绘制四个角的拖动标记点
        float cornerSize = 20f;  // 角标记的大小
        Paint cornerPaint = new Paint();
        cornerPaint.setColor(Color.WHITE);
        cornerPaint.setStyle(Paint.Style.FILL);
        cornerPaint.setStrokeWidth(2f);
        
        // 左上角标记点
        canvas.drawRect(
            cropRect.left - cornerSize / 2,
            cropRect.top - cornerSize / 2,
            cropRect.left + cornerSize / 2,
            cropRect.top + cornerSize / 2,
            cornerPaint
        );
        
        // 右上角标记点
        canvas.drawRect(
            cropRect.right - cornerSize / 2,
            cropRect.top - cornerSize / 2,
            cropRect.right + cornerSize / 2,
            cropRect.top + cornerSize / 2,
            cornerPaint
        );
        
        // 左下角标记点
        canvas.drawRect(
            cropRect.left - cornerSize / 2,
            cropRect.bottom - cornerSize / 2,
            cropRect.left + cornerSize / 2,
            cropRect.bottom + cornerSize / 2,
            cornerPaint
        );
        
        // 右下角标记点
        canvas.drawRect(
            cropRect.right - cornerSize / 2,
            cropRect.bottom - cornerSize / 2,
            cropRect.right + cornerSize / 2,
            cropRect.bottom + cornerSize / 2,
            cornerPaint
        );
        
        // 为标记点添加黑色边框，使其更醒目
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setColor(Color.BLACK);
        cornerPaint.setStrokeWidth(3f);
        
        // 左上角边框
        canvas.drawRect(
            cropRect.left - cornerSize / 2,
            cropRect.top - cornerSize / 2,
            cropRect.left + cornerSize / 2,
            cropRect.top + cornerSize / 2,
            cornerPaint
        );
        
        // 右上角边框
        canvas.drawRect(
            cropRect.right - cornerSize / 2,
            cropRect.top - cornerSize / 2,
            cropRect.right + cornerSize / 2,
            cropRect.top + cornerSize / 2,
            cornerPaint
        );
        
        // 左下角边框
        canvas.drawRect(
            cropRect.left - cornerSize / 2,
            cropRect.bottom - cornerSize / 2,
            cropRect.left + cornerSize / 2,
            cropRect.bottom + cornerSize / 2,
            cornerPaint
        );
        
        // 右下角边框
        canvas.drawRect(
            cropRect.right - cornerSize / 2,
            cropRect.bottom - cornerSize / 2,
            cropRect.right + cornerSize / 2,
            cropRect.bottom + cornerSize / 2,
            cornerPaint
        );
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
     * 更新baseOriginalBitmap为当前显示的图像
     */
    private void updateBaseOriginalBitmap() {
        try {
            // 获取当前显示的图像及其变换矩阵
            Drawable drawable = getDrawable();
            if (!(drawable instanceof BitmapDrawable)) return;
            
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            Bitmap currentBitmap = bitmapDrawable.getBitmap();
            
            // 创建一个与当前Bitmap大小相同的新Bitmap，应用当前的变换矩阵
            Bitmap processedBitmap = Bitmap.createBitmap(
                currentBitmap.getWidth(), 
                currentBitmap.getHeight(), 
                Bitmap.Config.ARGB_8888
            );
            
            Canvas canvas = new Canvas(processedBitmap);
            
            // 应用变换矩阵，确保所有变换都被正确应用
            canvas.drawBitmap(currentBitmap, matrix, null);
            
            // 更新baseOriginalBitmap
            if (baseOriginalBitmap != null && !baseOriginalBitmap.isRecycled()) {
                baseOriginalBitmap.recycle();
            }
            baseOriginalBitmap = processedBitmap;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 设置文字模式
     */
    public void setTextMode(boolean enabled) {
        this.isTextMode = enabled;
        if (!enabled) {
            if (selectedTextElement != null) {
                selectedTextElement = null;
                if (onTextSelectionChangeListener != null) {
                    onTextSelectionChangeListener.onTextDeselected();
                }
            }
            textMode = TextMode.NONE;
        }
        invalidate();
    }
    
    /**
     * 检查是否处于文字模式
     */
    public boolean isTextMode() {
        return isTextMode;
    }
    
    /**
     * 添加文字元素（指定位置）
     */
    public void addTextElement(String text, PointF position) {
        TextElement textElement = new TextElement(text, position);
        textElements.add(textElement);
        selectedTextElement = textElement;
        if (onTextSelectionChangeListener != null) {
            onTextSelectionChangeListener.onTextSelected(textElement);
        }
        invalidate();
    }
    
    /**
     * 添加文字元素（默认位置居中）
     */
    public void addTextElement(String text) {
        // 默认位置在图像中心
        PointF position = new PointF(getWidth() / 2f, getHeight() / 2f);
        addTextElement(text, position);
    }
    
    /**
     * 移除选中的文字元素
     */
    public void removeSelectedTextElement() {
        if (selectedTextElement != null) {
            textElements.remove(selectedTextElement);
            selectedTextElement = null;
            if (onTextSelectionChangeListener != null) {
                onTextSelectionChangeListener.onTextDeselected();
            }
            invalidate();
        }
    }
    
    /**
     * 更新选中文字元素的内容
     */
    public void updateSelectedTextContent(String text) {
        if (selectedTextElement != null) {
            selectedTextElement.text = text;
            invalidate();
        }
    }
    
    /**
     * 更新选中文字元素的字体
     */
    public void updateSelectedTextFont(String fontName) {
        if (selectedTextElement != null) {
            selectedTextElement.fontName = fontName;
            invalidate();
        }
    }
    
    /**
     * 更新选中文字元素的字号大小
     */
    public void updateSelectedTextSize(float textSize) {
        if (selectedTextElement != null) {
            selectedTextElement.textSize = textSize;
            invalidate();
        }
    }
    
    /**
     * 更新选中文字元素的透明度
     */
    public void updateSelectedTextAlpha(int alpha) {
        if (selectedTextElement != null) {
            selectedTextElement.alpha = alpha;
            invalidate();
        }
    }
    
    /**
     * 更新选中文字元素的颜色
     */
    public void updateSelectedTextColor(int color) {
        if (selectedTextElement != null) {
            selectedTextElement.textColor = color;
            invalidate();
        }
    }
    
    /**
     * 获取选中文字元素的颜色
     */
    public int getSelectedTextColor() {
        if (selectedTextElement != null) {
            return selectedTextElement.textColor;
        }
        return Color.WHITE; // 默认白色
    }
    
    /**
     * 获取选中文字元素的透明度
     */
    public int getSelectedTextAlpha() {
        if (selectedTextElement != null) {
            return selectedTextElement.alpha;
        }
        return 255; // 默认不透明
    }
    
    /**
     * 获取选中文字元素的内容
     */
    public String getSelectedTextContent() {
        if (selectedTextElement != null) {
            return selectedTextElement.text;
        }
        return null;
    }
    
    /**
     * 获取选中的文字元素
     */
    public TextElement getSelectedTextElement() {
        return selectedTextElement;
    }
    
    /**
     * 设置选中的文字元素
     */
    public void setSelectedTextElement(TextElement textElement) {
        if (selectedTextElement != textElement) {
            selectedTextElement = textElement;
            if (onTextSelectionChangeListener != null) {
                if (textElement != null) {
                    onTextSelectionChangeListener.onTextSelected(textElement);
                } else {
                    onTextSelectionChangeListener.onTextDeselected();
                }
            }
            invalidate();
        }
    }
    
    /**
     * 确认所有文字元素（将它们固定到图像上）
     */
    public void confirmTextElements() {
        if (textElements.isEmpty()) return;
        
        // 获取原始图像
        Drawable drawable = getDrawable();
        if (!(drawable instanceof BitmapDrawable)) return;
        
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        if (bitmap == null) return;
        
        // 创建新的Bitmap并绘制所有文字
        Bitmap resultBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
        Canvas canvas = new Canvas(resultBitmap);
        
        // 绘制原始图像
        canvas.drawBitmap(bitmap, 0, 0, null);
        
        // 绘制所有文字元素
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        
        // 获取当前图像的缩放比例
        float currentScale = getCurrentScale();
        
        // 获取当前图像矩阵的逆矩阵，用于将屏幕坐标转换为图像坐标
        Matrix inverseMatrix = new Matrix();
        if (!matrix.invert(inverseMatrix)) {
            // 如果无法获取逆矩阵，使用单位矩阵
            inverseMatrix = new Matrix();
        }
        
        for (TextElement textElement : textElements) {
            // 保存当前画布状态
            canvas.save();
            
            // 将屏幕坐标转换为图像坐标
            float[] points = {textElement.position.x, textElement.position.y};
            inverseMatrix.mapPoints(points);
            
            // 应用旋转和缩放变换（使用转换后的坐标）
            // 这里只应用旋转，不应用缩放，因为我们会在文字大小中考虑整体缩放
            canvas.rotate(textElement.rotation, points[0], points[1]);
            
            // 设置文字属性
            paint.setColor(textElement.textColor);
            // 设置文字透明度
            paint.setAlpha(textElement.alpha);
            // 计算最终文字大小：原始大小 * 文字元素的缩放比例
            // 不再乘以当前图像的缩放比例，这样文字会保持与图像的相对大小
            paint.setTextSize(textElement.textSize * textElement.scale);
            
            // 设置字体
            Typeface typeface;
            switch (textElement.fontName) {
                case "simsun":
                    typeface = Typeface.create("simsun", Typeface.NORMAL);
                    break;
                case "simhei":
                    typeface = Typeface.create("simhei", Typeface.NORMAL);
                    break;
                case "microsoft yahei":
                    typeface = Typeface.create("microsoft yahei", Typeface.NORMAL);
                    break;
                case "kaiti":
                    typeface = Typeface.create("kaiti", Typeface.NORMAL);
                    break;
                case "dengxian":
                    typeface = Typeface.create("dengxian", Typeface.NORMAL);
                    break;
                default: // 默认宋体
                    typeface = Typeface.create("simsun", Typeface.NORMAL);
                    break;
            }
            paint.setTypeface(typeface);
            
            // 绘制文字（支持换行）
            drawTextWithLineBreak(canvas, textElement.text, points[0], points[1], paint);
            
            // 恢复画布状态
            canvas.restore();
        }
        
        // 清空文字元素列表
        textElements.clear();
        selectedTextElement = null;
        
        // 更新图像
        setImageBitmap(resultBitmap);
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
        
        // 更新矩阵和图像
        matrix.set(resultMatrix);
        setImageMatrix(matrix);
        invalidate();
        
        // 重置裁剪模式
        setCropMode(false);
        
        // 保存当前矩阵
        Matrix currentMatrix = new Matrix(matrix);
        
        // 直接将裁剪后的Bitmap设置为baseOriginalBitmap
        if (baseOriginalBitmap != null && !baseOriginalBitmap.isRecycled()) {
            baseOriginalBitmap.recycle();
        }
        baseOriginalBitmap = croppedBitmap;
        
        // 更新ImageView的显示图像
        setImageBitmap(croppedBitmap);
        
        // 恢复矩阵
        matrix.set(currentMatrix);
        setImageMatrix(matrix);
        
        // 回收原始图像，删除原图片
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
        }
        
        // 重新绘制视图
        invalidate();
        
        return croppedBitmap;
    }
    
    /**
     * 检查点是否在裁剪框内
     */
    private boolean isPointInCropRect(float x, float y) {
        return cropRect.contains(x, y);
    }
    
    /**
     * 检查点是否在裁剪框的角上（用于调整大小）
     * @param x 触摸点的x坐标
     * @param y 触摸点的y坐标
     * @return 如果在角上返回true，否则返回false
     */
    private boolean isPointOnCropRectEdge(float x, float y) {
        // 只检测四个角，不需要额外检查是否在裁剪框内
        boolean isNearTopLeft = Math.abs(x - cropRect.left) < RESIZE_THRESHOLD && Math.abs(y - cropRect.top) < RESIZE_THRESHOLD;
        boolean isNearTopRight = Math.abs(x - cropRect.right) < RESIZE_THRESHOLD && Math.abs(y - cropRect.top) < RESIZE_THRESHOLD;
        boolean isNearBottomLeft = Math.abs(x - cropRect.left) < RESIZE_THRESHOLD && Math.abs(y - cropRect.bottom) < RESIZE_THRESHOLD;
        boolean isNearBottomRight = Math.abs(x - cropRect.right) < RESIZE_THRESHOLD && Math.abs(y - cropRect.bottom) < RESIZE_THRESHOLD;
        
        return isNearTopLeft || isNearTopRight || isNearBottomLeft || isNearBottomRight;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 如果是文字模式，优先处理文字触摸事件
        if (isTextMode) {
            if (handleTextTouchEvent(event)) {
                return true;
            }
        }
        
        // 如果不是文字模式，检查是否需要处理贴纸触摸事件
        if (!isTextMode && handleStickerTouchEvent(event)) {
            return true;
        }
        
        if (isCropMode) {
            return handleCropTouchEvent(event);
        }
        
        // 记录当前矩阵值用于计算缩放比例变化
        float[] prevMatrixValues = new float[9];
        matrix.getValues(prevMatrixValues);
        float prevScale = (float) Math.sqrt(prevMatrixValues[Matrix.MSCALE_X] * prevMatrixValues[Matrix.MSCALE_X] + 
                                           prevMatrixValues[Matrix.MSKEW_X] * prevMatrixValues[Matrix.MSKEW_X]);
        
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
                    
                    // 移动图片时同步移动所有文字
                    for (TextElement textElement : textElements) {
                        textElement.position.x += deltaX;
                        textElement.position.y += deltaY;
                    }
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

        // 计算缩放后的矩阵值
        float[] currMatrixValues = new float[9];
        matrix.getValues(currMatrixValues);
        float currScale = (float) Math.sqrt(currMatrixValues[Matrix.MSCALE_X] * currMatrixValues[Matrix.MSCALE_X] + 
                                           currMatrixValues[Matrix.MSKEW_X] * currMatrixValues[Matrix.MSKEW_X]);
        
        // 如果缩放比例发生变化，同步缩放所有文字
        if (Math.abs(currScale - prevScale) > 0.01f) {
            float scaleFactor = currScale / prevScale;
            for (TextElement textElement : textElements) {
                // 只在非文字模式下同步缩放文字
                if (!isTextMode) {
                    textElement.position.x = (textElement.position.x - currMatrixValues[Matrix.MTRANS_X]) / scaleFactor + currMatrixValues[Matrix.MTRANS_X];
                    textElement.position.y = (textElement.position.y - currMatrixValues[Matrix.MTRANS_Y]) / scaleFactor + currMatrixValues[Matrix.MTRANS_Y];
                }
            }
        }

        setImageMatrix(matrix);
        invalidate();
        return true;
    }
    
    /**
     * 检查点是否在调整大小的角点上
     */
    private ResizeCorner isPointOnResizeCorner(float x, float y, TextElement textElement) {
        Paint paint = new Paint();
        
        // 检查左上角
        PointF topLeft = textElement.getTopLeftCorner(paint);
        if (getDistance(new PointF(x, y), topLeft) <= HANDLE_RADIUS) {
            return ResizeCorner.TOP_LEFT;
        }
        
        // 检查右上角
        PointF topRight = textElement.getTopRightCorner(paint);
        if (getDistance(new PointF(x, y), topRight) <= HANDLE_RADIUS) {
            return ResizeCorner.TOP_RIGHT;
        }
        
        // 检查左下角
        PointF bottomLeft = textElement.getBottomLeftCorner(paint);
        if (getDistance(new PointF(x, y), bottomLeft) <= HANDLE_RADIUS) {
            return ResizeCorner.BOTTOM_LEFT;
        }
        
        // 检查右下角
        PointF bottomRight = textElement.getBottomRightCorner(paint);
        if (getDistance(new PointF(x, y), bottomRight) <= HANDLE_RADIUS) {
            return ResizeCorner.BOTTOM_RIGHT;
        }
        
        return ResizeCorner.NONE;
    }
    
    /**
     * 处理文字模式下的触摸事件
     */
    private boolean handleTextTouchEvent(MotionEvent event) {
        PointF curr = new PointF(event.getX(), event.getY());
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 检查是否点击了旋转手柄
                if (selectedTextElement != null) {
                    PointF handlePos = selectedTextElement.getRotateHandlePosition();
                    float distance = getDistance(curr, handlePos);
                    if (distance <= ROTATE_THRESHOLD) {
                        textMode = TextMode.ROTATE;
                        lastRotateAngle = calculateAngle(selectedTextElement.position, curr);
                        return true;
                    }
                    
                    // 检查是否点击了调整大小的角点
                    ResizeCorner resizeCorner = isPointOnResizeCorner(curr.x, curr.y, selectedTextElement);
                    if (resizeCorner != ResizeCorner.NONE) {
                        textMode = TextMode.RESIZE;
                        currentResizeCorner = resizeCorner;
                        textStart.set(curr);
                        RectF bounds = selectedTextElement.getBounds(new Paint());
                        lastResizeDistance = getDistance(selectedTextElement.position, curr);
                        return true;
                    }
                }
                
                // 检查是否点击了文字元素
                TextElement clickedText = findTextElementAtPosition(curr);
                if (clickedText != null) {
                    setSelectedTextElement(clickedText);
                    textMode = TextMode.DRAG;
                    textStart.set(curr);
                    return true;
                } else {
                    // 点击空白处，取消选中
                    setSelectedTextElement(null);
                }
                break;
                
            case MotionEvent.ACTION_MOVE:
                if (selectedTextElement != null) {
                    switch (textMode) {
                        case DRAG:
                            // 拖动文字
                            float deltaX = curr.x - textStart.x;
                            float deltaY = curr.y - textStart.y;
                            selectedTextElement.position.x += deltaX;
                            selectedTextElement.position.y += deltaY;
                            textStart.set(curr);
                            break;
                            
                        case ROTATE:
                            // 旋转文字
                            float currentAngle = calculateAngle(selectedTextElement.position, curr);
                            float deltaAngle = currentAngle - lastRotateAngle;
                            selectedTextElement.rotation += deltaAngle;
                            lastRotateAngle = currentAngle;
                            break;
                            
                        case RESIZE:
                            // 调整文字框大小
                            RectF bounds = selectedTextElement.getBounds(new Paint());
                            float newDistance = getDistance(selectedTextElement.position, curr);
                            float scaleFactor = newDistance / lastResizeDistance;
                            
                            // 计算新的宽度和高度
                            float newWidth = bounds.width() * scaleFactor;
                            float newHeight = bounds.height() * scaleFactor;
                            
                            // 应用调整大小
                            selectedTextElement.resize(currentResizeCorner, newWidth, newHeight);
                            lastResizeDistance = newDistance;
                            break;
                    }
                    invalidate();
                    return true;
                }
                break;
                
            case MotionEvent.ACTION_UP:
                if (selectedTextElement != null && textMode == TextMode.DRAG) {
                    // 检查是否是单击（移动距离很小）
                    float deltaX = curr.x - textStart.x;
                    float deltaY = curr.y - textStart.y;
                    float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                    if (distance < 5) { // 如果移动距离小于5像素，认为是单击
                        performClick(); // 触发点击事件，打开编辑对话框
                    }
                }
                textMode = TextMode.NONE;
                currentResizeCorner = ResizeCorner.NONE;
                break;
        }
        
        return false;
    }
    
    /**
     * 查找指定位置的文字元素
     */
    private TextElement findTextElementAtPosition(PointF position) {
        for (int i = textElements.size() - 1; i >= 0; i--) {
            TextElement textElement = textElements.get(i);
            RectF bounds = textElement.getBounds(new Paint());
            if (bounds.contains(position.x, position.y)) {
                return textElement;
            }
        }
        return null;
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
                // 点击时，先判断是否在四个角上
                boolean isTopLeft = Math.abs(curr.x - cropRect.left) < RESIZE_THRESHOLD && Math.abs(curr.y - cropRect.top) < RESIZE_THRESHOLD;
                boolean isTopRight = Math.abs(curr.x - cropRect.right) < RESIZE_THRESHOLD && Math.abs(curr.y - cropRect.top) < RESIZE_THRESHOLD;
                boolean isBottomLeft = Math.abs(curr.x - cropRect.left) < RESIZE_THRESHOLD && Math.abs(curr.y - cropRect.bottom) < RESIZE_THRESHOLD;
                boolean isBottomRight = Math.abs(curr.x - cropRect.right) < RESIZE_THRESHOLD && Math.abs(curr.y - cropRect.bottom) < RESIZE_THRESHOLD;
                
                if (isTopLeft || isTopRight || isBottomLeft || isBottomRight) {
                    // 点击在裁剪框角落，准备调整大小
                    isResizingCrop = true;
                    cropStart.set(curr);
                    
                    // 根据点击的角落确定要调整的角
                    if (isTopLeft) {
                        // 左上角
                        cropEnd.set(cropRect.right, cropRect.bottom);
                    } else if (isTopRight) {
                        // 右上角
                        cropEnd.set(cropRect.left, cropRect.bottom);
                    } else if (isBottomLeft) {
                        // 左下角
                        cropEnd.set(cropRect.right, cropRect.top);
                    } else if (isBottomRight) {
                        // 右下角
                        cropEnd.set(cropRect.left, cropRect.top);
                    }
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
        // 计算鼠标移动的距离
        float deltaX = curr.x - cropStart.x;
        float deltaY = curr.y - cropStart.y;
        
        // 计算新的宽高和位置
        float newLeft = cropRect.left;
        float newTop = cropRect.top;
        float newRight = cropRect.right;
        float newBottom = cropRect.bottom;
        
        // 最小宽度和高度限制
        float minWidth = 50f;
        float minHeight = 50f;
        
        // 根据cropEnd的位置确定拖动的是哪个角，并调整裁剪框大小
        if (cropEnd.x == cropRect.right && cropEnd.y == cropRect.bottom) {
            // 拖动左上角
            newLeft += deltaX;
            newTop += deltaY;
        } else if (cropEnd.x == cropRect.left && cropEnd.y == cropRect.bottom) {
            // 拖动右上角
            newRight += deltaX;
            newTop += deltaY;
        } else if (cropEnd.x == cropRect.right && cropEnd.y == cropRect.top) {
            // 拖动左下角
            newLeft += deltaX;
            newBottom += deltaY;
        } else {
            // 拖动右下角
            newRight += deltaX;
            newBottom += deltaY;
        }
        
        // 确保裁剪框的宽高不小于最小限制
        float newWidth = Math.max(minWidth, newRight - newLeft);
        float newHeight = Math.max(minHeight, newBottom - newTop);
        
        // 如果设置了比例，保持比例不变
        if (cropRatio > 0) {
            // 计算新的宽高，保持比例
            if (cropRatio >= 1) {
                // 宽 > 高
                newHeight = newWidth / cropRatio;
            } else {
                // 高 > 宽
                newWidth = newHeight * cropRatio;
            }
            
            // 确保不小于最小限制
            newWidth = Math.max(minWidth, newWidth);
            newHeight = Math.max(minHeight, newHeight);
            
            // 根据拖动的角调整位置，保持其他三个角的相对位置
            if (cropEnd.x == cropRect.right && cropEnd.y == cropRect.bottom) {
                // 拖动左上角
                newRight = newLeft + newWidth;
                newBottom = newTop + newHeight;
            } else if (cropEnd.x == cropRect.left && cropEnd.y == cropRect.bottom) {
                // 拖动右上角
                newLeft = newRight - newWidth;
                newBottom = newTop + newHeight;
            } else if (cropEnd.x == cropRect.right && cropEnd.y == cropRect.top) {
                // 拖动左下角
                newRight = newLeft + newWidth;
                newTop = newBottom - newHeight;
            } else {
                // 拖动右下角
                newLeft = newRight - newWidth;
                newTop = newBottom - newHeight;
            }
        }
        
        // 设置新的裁剪框
        cropRect.set(newLeft, newTop, newRight, newBottom);
        
        // 确保裁剪框不超出屏幕边界
        constrainCropRect();
        
        // 更新cropStart为当前位置
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
            if (isCropMode) {
                mode = Mode.ZOOM;
                return true;
            } else if (isTextMode && selectedTextElement != null) {
                // 检查触摸点是否在选中的文字元素内
                PointF focusPoint = new PointF(detector.getFocusX(), detector.getFocusY());
                RectF bounds = selectedTextElement.getBounds(new Paint());
                if (bounds.contains(focusPoint.x, focusPoint.y)) {
                    textMode = TextMode.SCALE;
                    lastScale = selectedTextElement.scale;
                    return true;
                }
            }
            
            mode = Mode.ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            
            if (isCropMode) {
                // 裁剪模式下，缩放裁剪框
                scaleCropRect(detector.getFocusX(), detector.getFocusY(), scaleFactor);
            } else if (isTextMode && selectedTextElement != null && textMode == TextMode.SCALE) {
                // 文字模式下，缩放文字
                selectedTextElement.scale = lastScale * scaleFactor;
                // 限制缩放范围
                selectedTextElement.scale = Math.max(0.5f, Math.min(3.0f, selectedTextElement.scale));
                invalidate();
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
                    
                    // 同步缩放所有文字（仅在非文字模式下）
                    if (!isTextMode) {
                        for (TextElement textElement : textElements) {
                            // 调整文字位置，使其相对于图片保持一致
                            textElement.position.x = (textElement.position.x - tempValues[Matrix.MTRANS_X]) / scaleFactor + tempValues[Matrix.MTRANS_X];
                            textElement.position.y = (textElement.position.y - tempValues[Matrix.MTRANS_Y]) / scaleFactor + tempValues[Matrix.MTRANS_Y];
                        }
                    }
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
        
        // 计算新的中心点（考虑焦点位置，保持偏移比例不变）
        float newCenterX = focusX - (focusX - centerX) * (newWidth / currentWidth);
        float newCenterY = focusY - (focusY - centerY) * (newHeight / currentHeight);
        
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
            // 初始化真正的原始图片（仅首次加载，永不覆盖）
            if (baseOriginalBitmap == null) {
                baseOriginalBitmap = bm.copy(bm.getConfig(), true);
            }
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
                // 初始化真正的原始图片（仅首次加载，永不覆盖）
                if (baseOriginalBitmap == null) {
                    baseOriginalBitmap = bm.copy(bm.getConfig(), true);
                }
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
     * 调整图像对比度
     * @param contrast 对比度值，范围为-50到150
     */
    public void adjustContrast(int contrast) {
        this.contrast = contrast;
        applyImageEffects();
    }
    
    /**
     * 获取当前对比度值
     * @return 当前对比度值
     */
    public int getContrast() {
        return contrast;
    }
    
    /**
     * 设置滤镜效果
     * @param filter 滤镜类型
     */
    public void setFilter(Filter filter) {
        this.currentFilter = filter;
        applyImageEffects();
    }
    
    /**
     * 获取当前滤镜效果
     * @return 当前滤镜类型
     */
    public Filter getCurrentFilter() {
        return currentFilter;
    }
    
    /**
     * 更新baseOriginalBitmap为当前显示的完整图像，并删除之前的图像
     * 用于每次初始创建图片编辑页面时调用
     */
    public void updateBaseOriginalBitmapFromCurrentDisplay() {
        try {
            // 获取当前显示的图像
            Drawable drawable = getDrawable();
            if (!(drawable instanceof BitmapDrawable)) return;
            
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            Bitmap currentDisplayBitmap = bitmapDrawable.getBitmap();
            
            if (currentDisplayBitmap != null) {
                // 回收之前的图像
                if (originalBitmap != null && !originalBitmap.isRecycled()) {
                    originalBitmap.recycle();
                    originalBitmap = null;
                }
                
                if (baseOriginalBitmap != null && !baseOriginalBitmap.isRecycled()) {
                    baseOriginalBitmap.recycle();
                    baseOriginalBitmap = null;
                }
                
                // 更新为当前显示的完整图像
                originalBitmap = currentDisplayBitmap.copy(currentDisplayBitmap.getConfig(), true);
                baseOriginalBitmap = currentDisplayBitmap.copy(currentDisplayBitmap.getConfig(), true);
                
                // 重绘视图
                invalidate();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 根据当前滤镜获取对应的颜色矩阵
     * @return 滤镜对应的颜色矩阵
     */
    private ColorMatrix getFilterColorMatrix() {
        ColorMatrix matrix = new ColorMatrix();
        
        switch (currentFilter) {
            case ORIGINAL:
                // 原图，不做任何滤镜处理
                break;
            case BLACK_WHITE:
                // 黑白滤镜
                matrix.setSaturation(0);
                break;
            case VINTAGE:
                // 复古滤镜
                // 降低饱和度，增加暖色调
                matrix.setSaturation(0.3f);
                float[] vintageMatrix = {
                    1.0f, 0.0f, 0.0f, 0.0f, 10,
                    0.0f, 0.95f, 0.0f, 0.0f, 10,
                    0.0f, 0.0f, 0.8f, 0.0f, 0,
                    0.0f, 0.0f, 0.0f, 1.0f, 0
                };
                matrix.postConcat(new ColorMatrix(vintageMatrix));
                break;
            case FRESH:
                // 清新滤镜
                // 增加亮度和对比度，调整色调
                float[] freshMatrix = {
                    1.1f, 0.0f, 0.0f, 0.0f, 5,
                    0.0f, 1.1f, 0.0f, 0.0f, 10,
                    0.0f, 0.0f, 1.2f, 0.0f, 10,
                    0.0f, 0.0f, 0.0f, 1.0f, 0
                };
                matrix.set(freshMatrix);
                break;
            case WARM:
                // 暖色调滤镜
                float[] warmMatrix = {
                    1.0f, 0.0f, 0.0f, 0.0f, 20,
                    0.0f, 0.9f, 0.0f, 0.0f, 10,
                    0.0f, 0.0f, 0.7f, 0.0f, 0,
                    0.0f, 0.0f, 0.0f, 1.0f, 0
                };
                matrix.set(warmMatrix);
                break;
            case COLD:
                // 冷色调滤镜
                float[] coldMatrix = {
                    0.8f, 0.0f, 0.0f, 0.0f, 0,
                    0.0f, 0.9f, 0.0f, 0.0f, 0,
                    0.0f, 0.0f, 1.2f, 0.0f, 20,
                    0.0f, 0.0f, 0.0f, 1.0f, 0
                };
                matrix.set(coldMatrix);
                break;
        }
        
        return matrix;
    }
    
    /**
     * 应用图像效果（亮度和对比度调节，滤镜效果）
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
            final Bitmap processedBitmap = Bitmap.createBitmap(baseOriginalBitmap.getWidth(), baseOriginalBitmap.getHeight(), baseOriginalBitmap.getConfig());
            Canvas canvas = new Canvas(processedBitmap);

            Paint paint = new Paint();

            // 亮度系数计算
            float brightnessScale = 1 + (brightness / 100f);
            brightnessScale = Math.max(0.1f, Math.min(1.9f, brightnessScale));
            
            // 对比度系数计算（范围-50到150转换为0.5到2.5的比例）
            float contrastScale = 1 + (contrast / 100f);
            contrastScale = Math.max(0.5f, Math.min(2.5f, contrastScale));

            // 1. 创建亮度和对比度的颜色矩阵
            ColorMatrix brightnessContrastMatrix = new ColorMatrix();
            // 先应用对比度，再应用亮度
            // 对比度矩阵: [contrast, 0, 0, 0, (1-contrast)*128]
            // 亮度矩阵: [brightness, 0, 0, 0, (brightness-1)*128]
            // 合并后的矩阵
            brightnessContrastMatrix.set(new float[]{
                    brightnessScale * contrastScale, 0, 0, 0, (1-contrastScale)*128 * brightnessScale,
                    0, brightnessScale * contrastScale, 0, 0, (1-contrastScale)*128 * brightnessScale,
                    0, 0, brightnessScale * contrastScale, 0, (1-contrastScale)*128 * brightnessScale,
                    0, 0, 0, 1, 0
            });
            
            // 2. 获取当前滤镜的颜色矩阵
            ColorMatrix filterMatrix = getFilterColorMatrix();
            
            // 3. 合并颜色矩阵：先应用亮度对比度，再应用滤镜
            ColorMatrix combinedMatrix = new ColorMatrix();
            combinedMatrix.postConcat(brightnessContrastMatrix);
            combinedMatrix.postConcat(filterMatrix);
            
            // 4. 应用合并后的颜色矩阵
            paint.setColorFilter(new ColorMatrixColorFilter(combinedMatrix));

            // 绘制真正的原始图片（核心：不再用被覆盖的originalBitmap）
            canvas.drawBitmap(baseOriginalBitmap, 0, 0, paint);

            post(() -> {
                try {
                    if (processedBitmap != null && !processedBitmap.isRecycled()) {
                        Matrix currentMatrix = new Matrix(matrix);
                        // 此处调用setImageBitmap会覆盖originalBitmap，但不影响baseOriginalBitmap
                        setImageBitmap(processedBitmap);
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
        
        // 更新baseOriginalBitmap
        updateBaseOriginalBitmap();
    }
    
    /**
     * 逆时针旋转90°
     */
    public void rotateCounterClockwise() {
        // 旋转矩阵（逆时针90°）
        matrix.postRotate(-90, getWidth() / 2, getHeight() / 2);
        setImageMatrix(matrix);
        invalidate();
        
        // 更新baseOriginalBitmap
        updateBaseOriginalBitmap();
    }
    
    /**
     * 旋转180°
     */
    public void rotate180() {
        // 旋转矩阵（180°）
        matrix.postRotate(180, getWidth() / 2, getHeight() / 2);
        setImageMatrix(matrix);
        invalidate();
        
        // 更新baseOriginalBitmap
        updateBaseOriginalBitmap();
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
        
        // 更新baseOriginalBitmap，确保保存时包含翻转效果
        updateBaseOriginalBitmap();
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
        
        // 更新baseOriginalBitmap，确保保存时包含翻转效果
        updateBaseOriginalBitmap();
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
        
        // 如果有文字元素，绘制它们
        if (!textElements.isEmpty()) {
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            
            for (TextElement textElement : textElements) {
                tempCanvas.save();
                tempCanvas.rotate(textElement.rotation, textElement.position.x, textElement.position.y);
                tempCanvas.scale(textElement.scale, textElement.scale, textElement.position.x, textElement.position.y);
                
                paint.setColor(textElement.textColor);
                paint.setTextSize(textElement.textSize);
                
                drawTextWithLineBreak(tempCanvas, textElement.text, textElement.position.x, textElement.position.y, paint);
                
                tempCanvas.restore();
            }
        }
        
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