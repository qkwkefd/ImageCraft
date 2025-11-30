package com.liang.imagecraft;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_READ_MEDIA_IMAGES = 100;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    
    private Button btnImportGallery;
    private Button btnTakePhoto;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private Uri photoUri; // 用于存储拍摄照片的URI
    private static final String KEY_PHOTO_URI = "photo_uri"; // 用于保存实例状态的键

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 从实例状态中恢复photoUri（如果存在）
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_PHOTO_URI)) {
            String photoUriString = savedInstanceState.getString(KEY_PHOTO_URI);
            if (photoUriString != null) {
                photoUri = Uri.parse(photoUriString);
            }
        }

        // 初始化UI组件
        btnImportGallery = findViewById(R.id.btn_import_gallery);
        btnTakePhoto = findViewById(R.id.btn_take_photo);

        // 设置相册选择启动器
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri selectedImage = result.getData().getData();
                if (selectedImage != null) {
                    // 跳转到图片编辑界面
                    Intent intent = new Intent(MainActivity.this, EditImageActivity.class);
                    intent.putExtra("image_uri", selectedImage.toString());
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "未选择图片", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 设置相机拍摄启动器
        cameraLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                if (photoUri != null) {
                    // 跳转到图片编辑界面
                    Intent intent = new Intent(MainActivity.this, EditImageActivity.class);
                    intent.putExtra("image_uri", photoUri.toString());
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "拍摄照片失败", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 设置相册导入按钮点击事件
        btnImportGallery.setOnClickListener(v -> checkAndRequestGalleryPermission());
        
        // 设置相机拍摄按钮点击事件
        btnTakePhoto.setOnClickListener(v -> checkAndRequestCameraPermission());
    }

    // 检查并请求相册权限
    private void checkAndRequestGalleryPermission() {
        boolean hasPermission;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13及以上使用READ_MEDIA_IMAGES权限
            hasPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 12及以下使用READ_EXTERNAL_STORAGE权限
            hasPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }

        if (hasPermission) {
            // 已有权限，打开相册
            openGallery();
        } else {
            // 无权限，请求权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_READ_MEDIA_IMAGES);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_MEDIA_IMAGES);
            }
        }
    }

    // 打开相册
    private void openGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            galleryLauncher.launch(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "打开相册失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 检查并请求相机权限
    private void checkAndRequestCameraPermission() {
        boolean hasCameraPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        
        if (hasCameraPermission) {
            // 已有权限，检查设备是否有相机
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                // 有相机，启动相机拍摄
                openCamera();
            } else {
                // 无相机
                Toast.makeText(this, "设备没有相机", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 无权限，请求权限
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }
    
    // 启动相机拍摄
    private void openCamera() {
        try {
            // 创建一个临时文件来存储拍摄的照片
            File photoFile = createImageFile();
            if (photoFile != null) {
                // 使用FileProvider创建内容URI
                photoUri = FileProvider.getUriForFile(
                        this,
                        "com.liang.imagecraft.fileprovider",
                        photoFile
                );
                
                // 启动相机应用
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                cameraLauncher.launch(intent);
            } else {
                Toast.makeText(this, "创建照片文件失败", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "启动相机失败", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "发生未知错误", Toast.LENGTH_SHORT).show();
        }
    }
    
    // 创建一个临时文件来存储拍摄的照片
    private File createImageFile() throws IOException {
        // 创建一个唯一的文件名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        
        // 获取外部存储的图片目录
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        
        // 创建临时文件
        File image = File.createTempFile(
                imageFileName,  // 文件名前缀
                ".jpg",         // 文件名后缀
                storageDir       // 存储目录
        );
        
        return image;
    }

    // 保存实例状态，用于横竖屏切换时恢复数据
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存photoUri
        if (photoUri != null) {
            outState.putString(KEY_PHOTO_URI, photoUri.toString());
        }
    }
    
    // 处理权限请求结果
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_READ_MEDIA_IMAGES) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予，打开相册
                openGallery();
            } else {
                // 权限被拒绝
                Toast.makeText(this, "需要相册权限才能导入图片", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予，检查设备是否有相机
                if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                    // 有相机，启动相机拍摄
                    openCamera();
                } else {
                    // 无相机
                    Toast.makeText(this, "设备没有相机", Toast.LENGTH_SHORT).show();
                }
            } else {
                // 权限被拒绝
                Toast.makeText(this, "需要相机权限才能拍摄照片", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
