package com.liang.imagecraft;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.AppCompatButton;

import java.util.Locale;
import java.util.Date;
import java.text.SimpleDateFormat;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class EditImageActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_PERMISSION = 300;
    
    private CustomImageView imagePreview;
    private Button btnBack;
    private Button btnSave;
    private Uri currentImageUri; // 保存当前图片URI，用于横竖屏切换时恢复
    private LinearLayout secondaryToolbar; // 新的工具栏区域
    
    // 工具栏按钮
    private AppCompatButton btnCrop;
    private AppCompatButton btnRotate;
    private AppCompatButton btnBrightness;
    private AppCompatButton btnContrast;
    private AppCompatButton btnText;
    
    // 当前选中的按钮
    private AppCompatButton currentSelectedButton = null;
    
    // 按钮背景色常量
    private static final int BUTTON_BACKGROUND_NORMAL = R.color.button_background;
    private static final int BUTTON_BACKGROUND_SELECTED = R.color.button_cyan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_image);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.edit_image_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 初始化UI组件
        imagePreview = findViewById(R.id.image_preview);
        btnBack = findViewById(R.id.btn_back);
        btnSave = findViewById(R.id.btn_save);
        
        // 初始化工具栏按钮
        initToolbarButtons();
        
        // 设置按钮点击事件
        setupButtonListeners();

        // 检查是否有保存的实例状态（用于横竖屏切换时恢复图片）
        if (savedInstanceState != null && savedInstanceState.containsKey("current_image_uri")) {
            // 从实例状态中恢复图片URI
            String imageUriString = savedInstanceState.getString("current_image_uri");
            if (imageUriString != null) {
                currentImageUri = Uri.parse(imageUriString);
                loadImage(currentImageUri);
            }
        } else {
            // 获取传入的图片URI（首次启动时）
            Intent intent = getIntent();
            if (intent != null && intent.hasExtra("image_uri")) {
                try {
                    String imageUriString = intent.getStringExtra("image_uri");
                    currentImageUri = Uri.parse(imageUriString);
                    // 显示图片
                    loadImage(currentImageUri);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "加载图片失败", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "未找到图片", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 保存实例状态，用于横竖屏切换时恢复数据
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存当前图片URI
        if (currentImageUri != null) {
            outState.putString("current_image_uri", currentImageUri.toString());
        }
    }

    /**
     * 加载图片的辅助方法
     */
    private void loadImage(Uri uri) {
        try {
            imagePreview.setImageURI(uri);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "加载图片失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 初始化工具栏按钮
     */
    private void initToolbarButtons() {
        try {
            btnCrop = findViewById(R.id.btn_crop);
            btnRotate = findViewById(R.id.btn_rotate);
            btnBrightness = findViewById(R.id.btn_brightness);
            btnContrast = findViewById(R.id.btn_contrast);
            btnText = findViewById(R.id.btn_text);
            // 获取新工具栏引用
            secondaryToolbar = findViewById(R.id.secondary_toolbar);
        } catch (Exception e) {
            // 忽略可能的空指针异常，某些布局可能不包含所有按钮
            e.printStackTrace();
        }
    }
    
    /**
     * 设置按钮点击事件监听器
     */
    private void setupButtonListeners() {
        // 返回按钮点击事件
        btnBack.setOnClickListener(v -> showExitConfirmationDialog());
        
        // 保存/确认按钮点击事件
        btnSave.setOnClickListener(v -> handleSaveButtonClick());
        
        // 工具栏按钮点击事件
        setupToolbarButtonListeners();
    }
    
    /**
     * 设置工具栏按钮点击事件监听器
     */
    private void setupToolbarButtonListeners() {
        // 为每个工具栏按钮设置点击事件
        if (btnCrop != null) {
            btnCrop.setOnClickListener(v -> toggleButtonSelection(btnCrop));
        }
        if (btnRotate != null) {
            btnRotate.setOnClickListener(v -> toggleButtonSelection(btnRotate));
        }
        if (btnBrightness != null) {
            btnBrightness.setOnClickListener(v -> toggleButtonSelection(btnBrightness));
        }
        if (btnContrast != null) {
            btnContrast.setOnClickListener(v -> toggleButtonSelection(btnContrast));
        }
        if (btnText != null) {
            btnText.setOnClickListener(v -> toggleButtonSelection(btnText));
        }
    }
    
    /**
     * 切换按钮选中状态
     */
    private void toggleButtonSelection(AppCompatButton button) {
        // 如果点击的是当前选中的按钮，则取消选中并隐藏新工具栏
        if (currentSelectedButton == button) {
            // 检查是否是裁剪按钮，如果是，先退出裁剪模式
            if (button == btnCrop) {
                imagePreview.setCropMode(false);
                // 将确认按钮改回保存按钮
                btnSave.setText("保存");
            }
            
            setButtonUnselected(button);
            currentSelectedButton = null;
            hideSecondaryToolbar();
        } else {
            // 如果之前有选中的按钮，则取消选中
            if (currentSelectedButton != null) {
                setButtonUnselected(currentSelectedButton);
                // 如果之前是裁剪模式，退出裁剪模式
                if (currentSelectedButton == btnCrop) {
                    imagePreview.setCropMode(false);
                    // 将确认按钮改回保存按钮
                    btnSave.setText("保存");
                }
            }
            // 选中新按钮
            setButtonSelected(button);
            currentSelectedButton = button;
            // 显示新工具栏并根据按钮类型设置内容
            showSecondaryToolbar(button);
        }
    }
    
    /**
     * 显示新工具栏并根据按钮类型设置内容
     */
    private void showSecondaryToolbar(AppCompatButton button) {
        if (secondaryToolbar != null) {
            secondaryToolbar.setVisibility(View.VISIBLE);
            // 清空当前内容
            secondaryToolbar.removeAllViews();
            
            // 根据不同按钮设置不同内容
            if (button == btnCrop) {
                createCropToolbar();
                // 进入裁剪模式
                imagePreview.setCropMode(true);
                // 将保存按钮改为确认按钮
                btnSave.setText("确认");
            } else if (button == btnRotate) {
                TextView textView = new TextView(this);
                textView.setText("旋转工具");
                textView.setTextColor(Color.WHITE);
                textView.setGravity(Gravity.CENTER);
                textView.setPadding(4, 4, 4, 4);
                addViewToSecondaryToolbar(textView);
            } else if (button == btnBrightness) {
                TextView textView = new TextView(this);
                textView.setText("亮度工具");
                textView.setTextColor(Color.WHITE);
                textView.setGravity(Gravity.CENTER);
                textView.setPadding(4, 4, 4, 4);
                addViewToSecondaryToolbar(textView);
            } else if (button == btnContrast) {
                TextView textView = new TextView(this);
                textView.setText("对比度工具");
                textView.setTextColor(Color.WHITE);
                textView.setGravity(Gravity.CENTER);
                textView.setPadding(4, 4, 4, 4);
                addViewToSecondaryToolbar(textView);
            } else if (button == btnText) {
                TextView textView = new TextView(this);
                textView.setText("文字工具");
                textView.setTextColor(Color.WHITE);
                textView.setGravity(Gravity.CENTER);
                textView.setPadding(4, 4, 4, 4);
                addViewToSecondaryToolbar(textView);
            }
        }
    }
    
    /**
     * 创建裁剪工具栏
     */
    private void createCropToolbar() {
        // 设置裁剪工具栏的方向
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        secondaryToolbar.setOrientation(isPortrait ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        
        // 创建裁剪比例按钮
        String[] cropOptions = {"自由", "1:1", "4:3", "16:9", "3:4", "9:16"};
        float[] cropRatios = {0, 1.0f, 4.0f/3.0f, 16.0f/9.0f, 3.0f/4.0f, 9.0f/16.0f};
        
        for (int i = 0; i < cropOptions.length; i++) {
            final String option = cropOptions[i];
            final float ratio = cropRatios[i];
            
            AppCompatButton cropButton = new AppCompatButton(this);
            cropButton.setText(option);
            cropButton.setTextColor(Color.WHITE);
            cropButton.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL));
            cropButton.setTextSize(12);
            cropButton.setPadding(4, 4, 4, 4);
            
            // 设置布局参数
            LinearLayout.LayoutParams params;
            if (isPortrait) {
                params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            } else {
                params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
            }
            params.setMargins(2, 2, 2, 2);
            cropButton.setLayoutParams(params);
            
            // 设置点击事件
            cropButton.setOnClickListener(v -> {
                // 设置裁剪比例
                imagePreview.setCropRatio(ratio);
                
                // 更新按钮状态
                for (int j = 0; j < secondaryToolbar.getChildCount(); j++) {
                    View child = secondaryToolbar.getChildAt(j);
                    if (child instanceof AppCompatButton) {
                        ((AppCompatButton) child).setBackgroundTintList(
                            getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL)
                        );
                    }
                }
                cropButton.setBackgroundTintList(
                    getResources().getColorStateList(BUTTON_BACKGROUND_SELECTED)
                );
            });
            
            // 添加到工具栏
            secondaryToolbar.addView(cropButton);
            
            // 默认选中自由裁剪
            if (i == 0) {
                cropButton.setBackgroundTintList(
                    getResources().getColorStateList(BUTTON_BACKGROUND_SELECTED)
                );
            }
        }
    }
    
    /**
     * 将视图添加到次级工具栏
     */
    private void addViewToSecondaryToolbar(View view) {
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        
        if (isPortrait) {
            view.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        } else {
            view.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        }
        
        secondaryToolbar.addView(view);
    }
    
    /**
     * 隐藏新工具栏
     */
    private void hideSecondaryToolbar() {
        if (secondaryToolbar != null) {
            secondaryToolbar.setVisibility(View.GONE);
        }
        
        // 如果当前是裁剪模式，退出裁剪模式
        if (imagePreview != null && currentSelectedButton == btnCrop) {
            imagePreview.setCropMode(false);
            // 将确认按钮改回保存按钮
            btnSave.setText("保存");
        }
    }
    
    /**
     * 设置按钮为选中状态
     */
    private void setButtonSelected(AppCompatButton button) {
        button.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_SELECTED));
        button.setSelected(true);
    }
    
    /**
     * 设置按钮为未选中状态
     */
    private void setButtonUnselected(AppCompatButton button) {
        button.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL));
        button.setSelected(false);
    }
    
    /**
     * 显示退出确认对话框
     */
    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
            .setTitle("退出编辑")
            .setMessage("图片还未保存，确认退出编辑页面吗？")
            .setPositiveButton("确定", (dialog, which) -> {
                // 确认退出，返回主页面
                finish();
            })
            .setNegativeButton("取消", (dialog, which) -> {
                // 取消退出，关闭对话框
                dialog.dismiss();
            })
            .show();
    }
    
    /**
     * 设置触摸监听器
     */
    private void setupTouchListeners() {
        // 触摸监听器已在CustomImageView中实现，此处无需额外设置
    }
    
    /**
     * 检查存储权限并保存图片
     */
    private void checkStoragePermissionAndSaveImage() {
        boolean hasPermission;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13及以上不需要WRITE_EXTERNAL_STORAGE权限
            hasPermission = true;
        } else {
            // Android 12及以下需要WRITE_EXTERNAL_STORAGE权限
            hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        
        if (hasPermission) {
            // 已有权限，保存图片
            saveImageToGallery();
        } else {
            // 无权限，请求权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
        }
    }
    
    /**
     * 添加水印到图片并保存到相册
     */
    private void saveImageToGallery() {
        try {
            if (currentImageUri == null) {
                Toast.makeText(this, "没有可保存的图片", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 直接从URI加载原始图片，避免包含黑边
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), currentImageUri);
            
            // 添加水印
            Bitmap bitmapWithWatermark = addWatermark(bitmap);
            
            // 保存到相册
            String savedImagePath = saveBitmapToGallery(bitmapWithWatermark);
            
            if (savedImagePath != null) {
                // 显示保存成功提示及文件地址
                String message = "图片保存成功\n保存位置：" + savedImagePath;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                
                // 保存成功后返回主页面
                finish();
            } else {
                Toast.makeText(this, "图片保存失败", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "保存图片时发生IO错误", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "保存图片失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 处理保存/确认按钮点击事件
     */
    private void handleSaveButtonClick() {
        if (currentSelectedButton == btnCrop && btnSave.getText().equals("确认")) {
            // 当前是裁剪模式，执行裁剪操作
            Bitmap croppedBitmap = imagePreview.performCrop();
            
            if (croppedBitmap != null) {
                try {
                    // 将裁剪后的Bitmap保存到临时文件
                    File cacheDir = getCacheDir();
                    File tempFile = new File(cacheDir, "cropped_image_" + System.currentTimeMillis() + ".jpg");
                    
                    // 压缩Bitmap到文件
                    FileOutputStream outputStream = new FileOutputStream(tempFile);
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    outputStream.close();
                    
                    // 获取临时文件的URI
                    Uri croppedImageUri = FileProvider.getUriForFile(
                        this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        tempFile
                    );
                    
                    // 创建新的EditImageActivity意图
                    Intent intent = new Intent(this, EditImageActivity.class);
                    intent.putExtra("image_uri", croppedImageUri.toString());
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    
                    // 启动新的编辑页面
                    startActivity(intent);
                    
                    // 销毁当前页面
                    finish();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "裁剪图片失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    
                    // 如果出错，退出裁剪模式
                    toggleButtonSelection(btnCrop);
                    btnSave.setText("保存");
                }
            } else {
                // 裁剪失败，退出裁剪模式
                toggleButtonSelection(btnCrop);
                btnSave.setText("保存");
                Toast.makeText(this, "裁剪失败：无效的裁剪区域", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 正常保存图片
            checkStoragePermissionAndSaveImage();
        }
    }
    
    /**
     * 添加水印到图片
     */
    private Bitmap addWatermark(Bitmap bitmap) {
        // 创建一个可编辑的Bitmap副本
        Bitmap resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(resultBitmap);
        
        // 设置水印文本属性
        String watermarkText = "训练营";
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setTextSize(60);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setAntiAlias(true);
        paint.setAlpha(150); // 半透明效果
        
        // 计算水印位置（右下角）
        float x = resultBitmap.getWidth() - paint.measureText(watermarkText) - 50;
        float y = resultBitmap.getHeight() - 50;
        
        // 绘制水印
        canvas.drawText(watermarkText, x, y, paint);
        
        return resultBitmap;
    }
    
    /**
     * 保存Bitmap到相册
     */
    private String saveBitmapToGallery(Bitmap bitmap) throws IOException {
        String savedImagePath = null;
        
        // 创建文件名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        
        OutputStream outputStream;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上使用MediaStore
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, imageFileName + ".jpg");
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "ImageCraft");
            
            // 插入图片到MediaStore
            Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            
            if (imageUri != null) {
                outputStream = getContentResolver().openOutputStream(imageUri);
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    outputStream.close();
                    savedImagePath = imageUri.toString();
                }
            }
        } else {
            // Android 9及以下使用传统方法
            File imagesDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ImageCraft");
            if (!imagesDir.exists()) {
                imagesDir.mkdirs();
            }
            
            File imageFile = new File(imagesDir, imageFileName + ".jpg");
            outputStream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            outputStream.close();
            
            // 添加到MediaStore
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.Images.Media.DATA, imageFile.getAbsolutePath());
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            
            savedImagePath = imageFile.getAbsolutePath();
        }
        
        return savedImagePath;
    }
    
    /**
     * 处理权限请求结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予，保存图片
                saveImageToGallery();
            } else {
                // 权限被拒绝
                Toast.makeText(this, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    /**
     * 处理配置变化（如屏幕旋转）
     * 当在AndroidManifest.xml中设置了android:configChanges属性后，
     * 屏幕旋转时不会重新创建Activity，而是调用此方法
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // 重新设置布局和WindowInsets
        setContentView(R.layout.activity_edit_image);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.edit_image_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // 重新初始化UI组件
        imagePreview = findViewById(R.id.image_preview);
        btnBack = findViewById(R.id.btn_back);
        btnSave = findViewById(R.id.btn_save);
        
        // 重新初始化工具栏按钮
        initToolbarButtons();
        
        // 设置按钮点击事件
        setupButtonListeners();
        
        // 恢复图片显示
        if (currentImageUri != null) {
            loadImage(currentImageUri);
        }
    }
}