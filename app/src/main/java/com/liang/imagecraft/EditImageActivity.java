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
import android.widget.Button;
import android.widget.Toast;

import java.util.Locale;
import java.util.Date;
import java.text.SimpleDateFormat;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
     * 设置按钮点击事件监听器
     */
    private void setupButtonListeners() {
        // 返回按钮点击事件
        btnBack.setOnClickListener(v -> showExitConfirmationDialog());
        
        // 保存按钮点击事件
        btnSave.setOnClickListener(v -> checkStoragePermissionAndSaveImage());
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
        
        // 设置按钮点击事件
        setupButtonListeners();
        
        // 恢复图片显示
        if (currentImageUri != null) {
            loadImage(currentImageUri);
        }
    }
}