package com.liang.imagecraft;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.text.Editable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.res.ColorStateList;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.Locale;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.text.SimpleDateFormat;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.content.res.Configuration;

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
    private View secondaryToolbarScroll; // 副工具栏滚动视图（根据屏幕方向自动转换为HorizontalScrollView或ScrollView）
    
    // 工具栏按钮
    private AppCompatButton btnCrop;
    private AppCompatButton btnRotate;
    private AppCompatButton btnBrightness;
    private AppCompatButton btnContrast;
    private AppCompatButton btnText;
    private AppCompatButton btnFont;
    private AppCompatButton btnColor;
    private AppCompatButton btnFilter;
    private AppCompatButton btnSticker;
    
    // 当前选中的按钮
    private AppCompatButton currentSelectedButton = null;
    
    // 按钮背景色资源ID
    private static final int BUTTON_BACKGROUND_NORMAL = R.color.button_background;
    private static final int BUTTON_BACKGROUND_SELECTED = R.color.button_cyan;
    private static final int BUTTON_BACKGROUND_CONFIRM = R.color.button_purple;
    
    // 主题切换相关
    private SwitchCompat btnThemeSwitch;
    private int currentThemeMode = AppCompatDelegate.MODE_NIGHT_YES; // 默认深色模式
    private SharedPreferences sharedPreferences;
    
    // 文字编辑输入框相关
    private EditText textEditInput;
    private LinearLayout textEditContainer;
    private Object selectedTextElement; // 保存当前选中的文本元素引用

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences("ImageCraftPrefs", MODE_PRIVATE);
        currentThemeMode = sharedPreferences.getInt("themeMode", AppCompatDelegate.MODE_NIGHT_YES);
        
        // 设置应用的夜间模式
        AppCompatDelegate.setDefaultNightMode(currentThemeMode);
        
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_image);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.edit_image_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 已在方法开始处初始化SharedPreferences，不需要重复初始化
        
        // 初始化UI组件
        imagePreview = findViewById(R.id.image_preview);
        btnBack = findViewById(R.id.btn_back);
        btnSave = findViewById(R.id.btn_save);
        btnThemeSwitch = findViewById(R.id.btn_theme_switch);
        
        // 设置主题开关状态
        btnThemeSwitch.setChecked(currentThemeMode == AppCompatDelegate.MODE_NIGHT_NO); // 开关状态：选中表示日间模式
        
        // 设置贴纸状态变化监听器
        imagePreview.setOnStickerStateChangeListener(new CustomImageView.OnStickerStateChangeListener() {
            @Override
            public void onStickerAdded() {
                // 贴纸添加时，选中贴纸按钮并显示副工具栏
                if (currentSelectedButton != btnSticker) {
                    if (currentSelectedButton != null) {
                        setButtonUnselected(currentSelectedButton);
                    }
                    setButtonSelected(btnSticker);
                    currentSelectedButton = btnSticker;
                    showSecondaryToolbar(btnSticker);
                }
            }
            
            @Override
            public void onStickerSelected() {
                // 贴纸选中时，选中贴纸按钮并显示副工具栏
                if (currentSelectedButton != btnSticker) {
                    if (currentSelectedButton != null) {
                        setButtonUnselected(currentSelectedButton);
                    }
                    setButtonSelected(btnSticker);
                    currentSelectedButton = btnSticker;
                    showSecondaryToolbar(btnSticker);
                }
            }
        });
        
        // 初始化工具栏按钮
        initToolbarButtons();
        
        // 设置按钮点击事件
        setupButtonListeners();
        
        // 设置主题切换监听器
        setupThemeSwitchListener();
        
        // 初始化文字编辑输入框
        initTextEditInput();

        // 检查是否有保存的实例状态（用于横竖屏切换时恢复图片）
        if (savedInstanceState != null && savedInstanceState.containsKey("current_image_uri")) {
            // 从实例状态中恢复图片URI
            String imageUriString = savedInstanceState.getString("current_image_uri");
            if (imageUriString != null) {
                currentImageUri = Uri.parse(imageUriString);
                loadImage(currentImageUri);
                // 更新baseOriginalBitmap为当前显示的完整图像，并删除之前的图像
                imagePreview.updateBaseOriginalBitmapFromCurrentDisplay();
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
                    // 更新baseOriginalBitmap为当前显示的完整图像，并删除之前的图像
                    imagePreview.updateBaseOriginalBitmapFromCurrentDisplay();
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
     * 更新文字编辑输入框的位置
     */
    private void updateTextEditInputPosition(int orientation) {
        if (textEditContainer != null) {
            ConstraintLayout.LayoutParams containerParams = (ConstraintLayout.LayoutParams) textEditContainer.getLayoutParams();
            
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // 横屏模式：紧贴屏幕下侧
                containerParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                containerParams.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
            } else {
                // 竖屏模式：紧贴副工具栏上方
                containerParams.bottomToTop = R.id.secondary_toolbar_scroll;
                containerParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
            }
            
            textEditContainer.setLayoutParams(containerParams);
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
            btnFilter = findViewById(R.id.btn_filter);
            btnSticker = findViewById(R.id.btn_sticker);
            // 获取新工具栏引用
            secondaryToolbar = findViewById(R.id.secondary_toolbar);
            // 获取滚动视图引用
            secondaryToolbarScroll = findViewById(R.id.secondary_toolbar_scroll);
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
        
        // 设置文字点击监听器
        setupTextClickListener();
    }
    
    /**
     * 设置主题切换监听器
     */
    private void setupThemeSwitchListener() {
        btnThemeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 切换主题模式
            currentThemeMode = isChecked ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
            
            // 保存主题状态到SharedPreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt("themeMode", currentThemeMode);
            editor.apply();
            
            // 设置应用的夜间模式
            AppCompatDelegate.setDefaultNightMode(currentThemeMode);
            
            // 重启Activity以应用新主题
            recreate();
        });
    }
    
    /**
     * 初始化文字编辑输入框
     */
    private void initTextEditInput() {
        // 获取主布局
        ConstraintLayout mainLayout = findViewById(R.id.edit_image_layout);
        
        // 创建输入框容器
        textEditContainer = new LinearLayout(this);
        ConstraintLayout.LayoutParams containerParams = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );
        
        // 根据屏幕方向设置输入框位置
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 横屏模式：紧贴屏幕下侧
            containerParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        } else {
            // 竖屏模式：紧贴副工具栏上方
            containerParams.bottomToTop = R.id.secondary_toolbar_scroll;
        }
        
        containerParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
        containerParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
        textEditContainer.setLayoutParams(containerParams);
        textEditContainer.setBackgroundColor(Color.parseColor("#333333"));
        textEditContainer.setPadding(16, 8, 16, 8);
        textEditContainer.setVisibility(View.GONE);
        textEditContainer.setOrientation(LinearLayout.HORIZONTAL);
        
        // 创建输入框
        textEditInput = new EditText(this);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
        );
        textEditInput.setLayoutParams(inputParams);
        textEditInput.setTextColor(Color.WHITE);
        textEditInput.setBackgroundColor(Color.parseColor("#555555"));
        textEditInput.setPadding(12, 8, 12, 8);
        textEditInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        textEditInput.setSingleLine(false);
        textEditInput.setMaxLines(5);
        
        // 添加输入框到容器
        textEditContainer.addView(textEditInput);
        
        // 添加容器到主布局
        mainLayout.addView(textEditContainer);
        
        // 设置输入框点击事件，点击时弹出键盘
        textEditInput.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                textEditInput.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(textEditInput, InputMethodManager.SHOW_IMPLICIT);
            }
            return false;
        });
        
        // 添加文本变化监听器，实时同步更新图片上的文字内容
        textEditInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 文本变化前的回调，不需要处理
            }
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 文本变化中的回调，实时更新图片上的文字内容
                if (selectedTextElement != null && imagePreview != null) {
                    String newText = s.toString();
                    imagePreview.updateSelectedTextContent(newText);
                    imagePreview.invalidate();
                }
            }
            
            @Override
            public void afterTextChanged(Editable s) {
                // 文本变化后的回调，不需要处理
            }
        });
        
        // 设置文字选择状态变化监听器
        if (imagePreview != null) {
            imagePreview.setOnTextSelectionChangeListener(new CustomImageView.OnTextSelectionChangeListener() {
                @Override
                public void onTextSelected(CustomImageView.TextElement textElement) {
                    // 文字被选中时，显示文本输入栏并填充内容
                    selectedTextElement = textElement;
                    showTextEditInput(textElement.text);
                }
                
                @Override
                public void onTextDeselected() {
                    // 文字取消选中时，隐藏文本输入栏
                    selectedTextElement = null;
                    hideTextEditInput();
                }
            });
        }
    }
    
    /**
     * 显示文字编辑输入框
     */
    private void showTextEditInput(String initialText) {
        if (textEditInput != null && textEditContainer != null) {
            textEditInput.setText(initialText);
            textEditInput.setSelection(initialText.length());
            textEditContainer.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 隐藏文字编辑输入框
     */
    private void hideTextEditInput() {
        if (textEditContainer != null) {
            textEditContainer.setVisibility(View.GONE);
            // 隐藏键盘
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(textEditInput.getWindowToken(), 0);
        }
    }
    
    /**
     * 设置文字点击监听器，用于编辑文字内容
     */
    private void setupTextClickListener() {
        // 为CustomImageView添加点击事件，用于编辑文字
        imagePreview.setOnClickListener(v -> {
            if (imagePreview.isTextMode()) {
                // 获取选中的文本内容和元素
                selectedTextElement = imagePreview.getSelectedTextElement();
                if (selectedTextElement != null) {
                    // 获取选中的文字内容
                    String currentText = imagePreview.getSelectedTextContent();
                    if (currentText != null) {
                        // 显示输入框而不是对话框
                        showTextEditInput(currentText);
                    }
                }
            }
        });
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
        if (btnFilter != null) {
            btnFilter.setOnClickListener(v -> toggleButtonSelection(btnFilter));
        }
        if (btnSticker != null) {
            btnSticker.setOnClickListener(v -> toggleButtonSelection(btnSticker));
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
            } else if (button == btnText) {
                // 如果是文字按钮，退出文字编辑模式
                imagePreview.setTextMode(false);
            } else if (button == btnSticker) {
                // 如果是贴纸按钮，退出贴纸编辑模式
                imagePreview.setStickerMode(false);
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
                } else if (currentSelectedButton == btnText) {
                    // 如果之前是文字模式，退出文字编辑模式
                    imagePreview.setTextMode(false);
                } else if (currentSelectedButton == btnSticker) {
                    // 如果之前是贴纸模式，退出贴纸编辑模式
                    imagePreview.setStickerMode(false);
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
            // 显示滚动视图
            if (secondaryToolbarScroll != null) {
                secondaryToolbarScroll.setVisibility(View.VISIBLE);
            }
            // 清空当前内容
            secondaryToolbar.removeAllViews();
            
            // 根据不同按钮设置不同内容
            if (button == btnCrop) {
                createCropToolbar();
                // 进入裁剪模式
                imagePreview.setCropMode(true);
            } else if (button == btnRotate) {
                createRotateToolbar();
            } else if (button == btnText) {
                createTextToolbar();
                // 进入文字编辑模式
                imagePreview.setTextMode(true);
                // 添加一个默认文字元素
                imagePreview.addTextElement("点击编辑文字");
            } else if (button == btnFilter) {
                createFilterToolbar();
            } else if (button == btnSticker) {
                createStickerToolbar();
                // 进入贴纸编辑模式
                imagePreview.setStickerMode(true);
            } else if (button == btnBrightness) {
                // 设置亮度工具栏的方向
                boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
                secondaryToolbar.setOrientation(isPortrait ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

                // 创建亮度标题
                TextView brightnessTitle = new TextView(this);
                brightnessTitle.setText("亮度调整");
                brightnessTitle.setTextColor(Color.WHITE);
                brightnessTitle.setGravity(Gravity.CENTER);
                brightnessTitle.setPadding(8, 4, 8, 4);
                brightnessTitle.setTextSize(14);

                // 获取当前亮度值
                int currentBrightness = imagePreview.getBrightness();

                // 创建滑块组件
                SeekBar brightnessSeekBar = new SeekBar(this);
                brightnessSeekBar.setMax(200); // 0-200对应-100到100的亮度范围
                brightnessSeekBar.setProgress(currentBrightness + 100); // 设置为当前亮度值（对应进度100+brightness）
                brightnessSeekBar.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
                brightnessSeekBar.setProgressTintList(ColorStateList.valueOf(Color.CYAN));

                // 创建亮度值显示
                TextView brightnessValue = new TextView(this);
                brightnessValue.setText(String.valueOf(currentBrightness));
                brightnessValue.setTextColor(Color.WHITE);
                brightnessValue.setGravity(Gravity.CENTER);
                brightnessValue.setPadding(8, 4, 8, 4);
                brightnessValue.setTextSize(14);

                // 设置滑块监听器
                brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) {
                            int brightness = progress - 100;
                            imagePreview.adjustBrightness(brightness);
                            brightnessValue.setText(String.valueOf(brightness));
                        }
                    }
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        // 触摸结束后，确保滑块状态正确更新
                        seekBar.setPressed(false);
                        seekBar.invalidate();
                    }
                });

                // 根据屏幕方向添加组件
                if (isPortrait) {
                    // 竖屏模式：水平排列
                    secondaryToolbar.addView(brightnessTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

                    // 创建滑块父容器
                    LinearLayout seekBarContainer = new LinearLayout(this);
                    seekBarContainer.setOrientation(LinearLayout.HORIZONTAL);
                    // 使用dp转换而不是硬编码像素值
                    int containerWidth = dp2px(this, 250); // 250dp宽度
                    int containerHeight = dp2px(this, 80); // 80dp高度
                    seekBarContainer.setLayoutParams(new LinearLayout.LayoutParams(containerWidth, containerHeight));
                    seekBarContainer.setGravity(Gravity.CENTER);
                    seekBarContainer.setPadding(dp2px(this, 8), dp2px(this, 8), dp2px(this, 8), dp2px(this, 8));

                    // 将滑块添加到父容器
                    LinearLayout.LayoutParams seekBarParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    seekBarParams.gravity = Gravity.CENTER;
                    brightnessSeekBar.setLayoutParams(seekBarParams);

                    // 确保滑块可点击和获取焦点
                    brightnessSeekBar.setClickable(true);
                    brightnessSeekBar.setFocusable(true);
                    brightnessSeekBar.setFocusableInTouchMode(true);

                    
                    // 强制设置滑块的可触摸区域
                    brightnessSeekBar.setPadding(dp2px(this, 10), dp2px(this, 10), dp2px(this, 10), dp2px(this, 10));
                    
                    // 添加触摸事件监听器，确保触摸事件被正确处理
                    brightnessSeekBar.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            // 请求父容器不要拦截触摸事件
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            // 当触摸结束时，允许父容器重新拦截触摸事件
                            if (event.getAction() == MotionEvent.ACTION_UP || 
                                event.getAction() == MotionEvent.ACTION_CANCEL) {
                                v.getParent().requestDisallowInterceptTouchEvent(false);
                            }
                            return false; // 让SeekBar自己处理触摸事件
                        }
                    });

                    seekBarContainer.addView(brightnessSeekBar);

                    // 将父容器添加到副工具栏
                    secondaryToolbar.addView(seekBarContainer);
                    secondaryToolbar.addView(brightnessValue, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
                } else {
                    // 横屏：垂直布局

                    int parentHeightPx = 600;  // 父容器真实高度
                    int seekbarLengthPx = 600; // 滑块视觉长度 = 宽度（因为旋转了）

                    secondaryToolbar.addView(brightnessTitle, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
                    ));

                    // 父容器固定高度600
                    LinearLayout seekBarContainer = new LinearLayout(this);
                    seekBarContainer.setOrientation(LinearLayout.VERTICAL);
                    seekBarContainer.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            parentHeightPx
                    ));
                    seekBarContainer.setGravity(Gravity.CENTER);

                    LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                            seekbarLengthPx,
                            LinearLayout.LayoutParams.MATCH_PARENT
                    );
                    seekParams.gravity = Gravity.CENTER;

                    brightnessSeekBar.setLayoutParams(seekParams);

                    brightnessSeekBar.setRotation(270);
                    brightnessSeekBar.post(() -> {
                        brightnessSeekBar.setPivotX(brightnessSeekBar.getWidth() / 2f);
                        brightnessSeekBar.setPivotY(brightnessSeekBar.getHeight() / 2f);
                    });

                    seekBarContainer.addView(brightnessSeekBar);
                    secondaryToolbar.addView(seekBarContainer);

                    secondaryToolbar.addView(brightnessValue, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
                    ));
                }


            } else if (button == btnContrast) {

                // 设置方向：竖屏水平排列 / 横屏垂直排列
                boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
                secondaryToolbar.setOrientation(isPortrait ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

                // 标题（完全复制亮度样式）
                TextView contrastTitle = new TextView(this);
                contrastTitle.setText("对比度调整");
                contrastTitle.setTextColor(Color.WHITE);
                contrastTitle.setGravity(Gravity.CENTER);
                contrastTitle.setPadding(8, 4, 8, 4);

                // 当前对比度
                int currentContrast = imagePreview.getContrast();

                // 滑块
                SeekBar contrastSeekBar = new SeekBar(this);
                contrastSeekBar.setMax(200); // -50 到 150
                contrastSeekBar.setProgress(currentContrast + 50); // 50 + contrast
                contrastSeekBar.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
                contrastSeekBar.setProgressTintList(ColorStateList.valueOf(Color.CYAN));

                // 对比度数值显示
                TextView contrastValue = new TextView(this);
                contrastValue.setText(String.valueOf(currentContrast));
                contrastValue.setTextColor(Color.WHITE);
                contrastValue.setGravity(Gravity.CENTER);
                contrastValue.setPadding(8, 4, 8, 4);
                contrastValue.setTextSize(14);

                // 监听（保留逻辑）
                contrastSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) {
                            int contrast = progress - 50;
                            imagePreview.adjustContrast(contrast);
                            contrastValue.setText(String.valueOf(contrast));
                        }
                    }
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        // 触摸结束后，确保滑块状态正确更新
                        seekBar.setPressed(false);
                        seekBar.invalidate();
                    }
                });

                if (isPortrait) {
                    // === 竖屏排列 ===
                    secondaryToolbar.addView(contrastTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
                    
                    // 创建滑块父容器
                    LinearLayout seekBarContainer = new LinearLayout(this);
                    seekBarContainer.setOrientation(LinearLayout.HORIZONTAL);
                    // 使用dp转换而不是硬编码像素值
                    int containerWidth = dp2px(this, 250); // 250dp宽度
                    int containerHeight = dp2px(this, 80); // 80dp高度
                    seekBarContainer.setLayoutParams(new LinearLayout.LayoutParams(containerWidth, containerHeight));
                    seekBarContainer.setGravity(Gravity.CENTER);
                    seekBarContainer.setPadding(dp2px(this, 8), dp2px(this, 8), dp2px(this, 8), dp2px(this, 8));
                    
                    // 将滑块添加到父容器
                    LinearLayout.LayoutParams seekBarParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    seekBarParams.gravity = Gravity.CENTER;
                    contrastSeekBar.setLayoutParams(seekBarParams);

                    // 确保滑块可点击和获取焦点
                    contrastSeekBar.setClickable(true);
                    contrastSeekBar.setFocusable(true);
                    contrastSeekBar.setFocusableInTouchMode(true);
                    
                    
                    // 强制设置滑块的可触摸区域
                    contrastSeekBar.setPadding(dp2px(this, 10), dp2px(this, 10), dp2px(this, 10), dp2px(this, 10));
                    
                    // 添加触摸事件监听器，确保触摸事件被正确处理
                    contrastSeekBar.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            // 请求父容器不要拦截触摸事件
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            // 当触摸结束时，允许父容器重新拦截触摸事件
                            if (event.getAction() == MotionEvent.ACTION_UP || 
                                event.getAction() == MotionEvent.ACTION_CANCEL) {
                                v.getParent().requestDisallowInterceptTouchEvent(false);
                            }
                            return false; // 让SeekBar自己处理触摸事件
                        }
                    });

                    seekBarContainer.addView(contrastSeekBar);
                    
                    // 将父容器添加到副工具栏
                    secondaryToolbar.addView(seekBarContainer);
                    secondaryToolbar.addView(contrastValue, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

                } else {
                    // === 横屏排列 ===
                    secondaryToolbar.addView(contrastTitle, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
                    ));

                    int parentHeightPx = 600;   // 父容器长度
                    int seekBarLengthPx = 600;  // 滑块视觉长度

                    // 滑块父容器固定高度 600px
                    LinearLayout seekBarContainer = new LinearLayout(this);
                    seekBarContainer.setOrientation(LinearLayout.VERTICAL);
                    seekBarContainer.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            parentHeightPx
                    ));
                    seekBarContainer.setGravity(Gravity.CENTER);
                    seekBarContainer.setPadding(0, 16, 0, 16);

                    
                    LinearLayout.LayoutParams seekBarParams = new LinearLayout.LayoutParams(
                            seekBarLengthPx,
                            LinearLayout.LayoutParams.MATCH_PARENT // 厚度方向填满
                    );
                    seekBarParams.gravity = Gravity.CENTER;

                    contrastSeekBar.setLayoutParams(seekBarParams);
                    contrastSeekBar.setRotation(270);
                    contrastSeekBar.post(() -> {
                        contrastSeekBar.setPivotX(contrastSeekBar.getWidth() / 2f);
                        contrastSeekBar.setPivotY(contrastSeekBar.getHeight() / 2f);
                    });

                    seekBarContainer.addView(contrastSeekBar);
                    secondaryToolbar.addView(seekBarContainer);

                    secondaryToolbar.addView(contrastValue, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
                    ));
                }

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
    // 补充：dp转px工具方法（放在Activity里）
    public static int dp2px(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
    /**
     * 创建贴纸工具栏
     */
    private void createStickerToolbar() {
        // 设置方向：竖屏水平排列 / 横屏垂直排列
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        secondaryToolbar.setOrientation(isPortrait ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        
        // 设置按钮尺寸为正方形（48dp）
        int buttonSize = (int) (48 * getResources().getDisplayMetrics().density);
        
        // 创建删除按钮
        AppCompatButton btnDelete = new AppCompatButton(this);
        btnDelete.setText("删除");
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL));
        btnDelete.setOnClickListener(v -> {
            // 移除当前选中的贴纸
            if (imagePreview != null) {
                imagePreview.removeSelectedStickerElement();
            }
        });
        
        // 创建确认按钮
        AppCompatButton btnConfirm = new AppCompatButton(this);
        btnConfirm.setText("确认");
        btnConfirm.setTextColor(Color.WHITE);
        btnConfirm.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL));
        btnConfirm.setOnClickListener(v -> {
            // 确认贴纸，将它们固定到图片上
            if (imagePreview != null) {
                imagePreview.confirmStickerElements();
                hideSecondaryToolbar();
                // 取消按钮选中状态
                if (currentSelectedButton != null) {
                    setButtonUnselected(currentSelectedButton);
                    currentSelectedButton = null;
                }
            }
        });
        
        // 创建滚动容器，支持横向或纵向滚动
        LinearLayout stickerContainer = new LinearLayout(this);
        stickerContainer.setOrientation(isPortrait ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        stickerContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.MATCH_PARENT));
        stickerContainer.setGravity(Gravity.CENTER);
        stickerContainer.setPadding(16, 16, 16, 16);
        stickerContainer.setHorizontalScrollBarEnabled(isPortrait);
        stickerContainer.setVerticalScrollBarEnabled(!isPortrait);
        
        // 获取贴纸资源
        int[] stickerIds = getStickerResourceIds();
        String[] stickerNames = getStickerNames();
        
        // 添加贴纸项
        for (int i = 0; i < stickerIds.length; i++) {
            LinearLayout stickerItem = new LinearLayout(this);
            stickerItem.setOrientation(LinearLayout.VERTICAL);
            stickerItem.setLayoutParams(new LinearLayout.LayoutParams(
                    dp2px(this, 80), 
                    dp2px(this, 100)));
            stickerItem.setGravity(Gravity.CENTER);
            stickerItem.setPadding(8, 8, 8, 8);
            
            // 创建贴纸图像视图
            ImageView stickerImage = new ImageView(this);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                    dp2px(this, 60), 
                    dp2px(this, 60));
            stickerImage.setLayoutParams(imageParams);
            stickerImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            stickerImage.setImageResource(stickerIds[i]);
            
            // 创建贴纸上文字视图
            TextView stickerText = new TextView(this);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            stickerText.setLayoutParams(textParams);
            stickerText.setText(stickerNames[i]);
            stickerText.setTextColor(Color.WHITE);
            stickerText.setTextSize(12);
            stickerText.setGravity(Gravity.CENTER);
            stickerText.setMaxLines(2);
            stickerText.setEllipsize(TextUtils.TruncateAt.END);
            
            // 添加点击事件
            final int stickerId = stickerIds[i];
            stickerItem.setOnClickListener(v -> {
                // 添加贴纸到图片上
                imagePreview.addStickerElement(stickerId);
                // 保持副工具栏展开状态和按钮选中状态
                
            });
            
            // 将图像和文字添加到贴纸项
            stickerItem.addView(stickerImage);
            stickerItem.addView(stickerText);
            
            // 将贴纸项添加到容器
            stickerContainer.addView(stickerItem);
        }
        
        // 添加按钮到工具栏
        if (isPortrait) {
            // 竖屏模式：删除和确认按钮放在最左侧
            // 删除按钮使用固定尺寸
            secondaryToolbar.addView(btnDelete, new LinearLayout.LayoutParams(buttonSize, LinearLayout.LayoutParams.MATCH_PARENT));
            // 确认按钮使用固定尺寸
            secondaryToolbar.addView(btnConfirm, new LinearLayout.LayoutParams(buttonSize, LinearLayout.LayoutParams.MATCH_PARENT));
            // 贴纸容器使用剩余空间
            secondaryToolbar.addView(stickerContainer);
        } else {
            // 横屏模式：删除和确认按钮放在最上侧
            // 删除按钮使用固定尺寸
            secondaryToolbar.addView(btnDelete, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, buttonSize));
            // 确认按钮使用固定尺寸
            secondaryToolbar.addView(btnConfirm, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, buttonSize));
            // 贴纸容器使用剩余空间
            secondaryToolbar.addView(stickerContainer);
        }
    }
    
    /**
     * 获取贴纸资源ID数组
     */
    private int[] getStickerResourceIds() {
        // 映射贴纸资源ID
        return new int[] {
            R.drawable.ok,
            R.drawable.crying_emoji,
            R.drawable.cute_poop,
            R.drawable.douyin_logo,
            R.drawable.flame_icon,
            R.drawable.kitchen_knife,
            R.drawable.laughing_crying_emoji,
            R.drawable.pixel_sunglasses,
            R.drawable.pyramid,
            R.drawable.red_arrow,
            R.drawable.speech_bubble,
            R.drawable.sun_icon,
            R.drawable.telephone_icon,
            R.drawable.thumbs_up,
            R.drawable.warning_sign
        };
    }
    
    /**
     * 获取贴纸名称数组
     */
    private String[] getStickerNames() {
        // 手动添加贴纸名称（去掉后缀）
        return new String[] {
            "ok",
            "crying_emoji",
            "cute_poop",
            "douyin_logo",
            "flame_icon",
            "kitchen_knife",
            "laughing_crying_emoji",
            "pixel_sunglasses",
            "pyramid",
            "red_arrow",
            "speech_bubble",
            "sun_icon",
            "telephone_icon",
            "thumbs_up",
            "warning_sign"
        };
    }
    
    /**
     * 创建滤镜工具栏
     */
    private void createFilterToolbar() {
        // 设置方向：竖屏水平排列 / 横屏垂直排列
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        secondaryToolbar.setOrientation(isPortrait ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        
        // 滤镜选项数组
        String[] filterOptions = {"原图", "黑白", "复古", "清新", "暖色调", "冷色调"};
        
        // 创建滤镜类型与按钮文本的映射
        Map<String, CustomImageView.Filter> filterMap = new HashMap<>();
        filterMap.put("原图", CustomImageView.Filter.ORIGINAL);
        filterMap.put("黑白", CustomImageView.Filter.BLACK_WHITE);
        filterMap.put("复古", CustomImageView.Filter.VINTAGE);
        filterMap.put("清新", CustomImageView.Filter.FRESH);
        filterMap.put("暖色调", CustomImageView.Filter.WARM);
        filterMap.put("冷色调", CustomImageView.Filter.COLD);
        
        for (String filter : filterOptions) {
            AppCompatButton filterButton = new AppCompatButton(this);
            filterButton.setText(filter);
            filterButton.setTextColor(Color.WHITE);
            filterButton.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL));
            filterButton.setTextSize(12);
            filterButton.setPadding(4, 4, 4, 4);
            
            // 设置布局参数
            LinearLayout.LayoutParams params;
            if (isPortrait) {
                params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            } else {
                params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
            }
            params.setMargins(2, 2, 2, 2);
            
            // 获取当前滤镜对应的枚举类型
            CustomImageView.Filter filterType = filterMap.get(filter);
            
            // 添加点击事件监听器
            filterButton.setOnClickListener(v -> {
                // 设置对应的滤镜效果
                if (filterType != null) {
                    imagePreview.setFilter(filterType);
                }
                
                // 更新按钮状态
                for (int j = 0; j < secondaryToolbar.getChildCount(); j++) {
                    View child = secondaryToolbar.getChildAt(j);
                    if (child instanceof AppCompatButton) {
                        ((AppCompatButton) child).setBackgroundTintList(
                            getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL)
                        );
                    }
                }
                filterButton.setBackgroundTintList(
                    getResources().getColorStateList(BUTTON_BACKGROUND_SELECTED)
                );
            });
            
            // 添加到副工具栏
            secondaryToolbar.addView(filterButton, params);
            
            // 默认选中"原图"滤镜
            if (filter.equals("原图")) {
                filterButton.setBackgroundTintList(
                    getResources().getColorStateList(BUTTON_BACKGROUND_SELECTED)
                );
            }
        }
    }
    
    /**
     * 创建旋转工具栏
     */
    private void createRotateToolbar() {
        // 设置旋转工具栏的方向
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        secondaryToolbar.setOrientation(isPortrait ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        
        // 创建旋转和翻转按钮
        String[] rotateOptions = {"↶ 逆时针90°", "↷ 顺时针90°", "↻ 旋转180°", "⇄ 水平翻转", "⇅ 垂直翻转"};
        int[] rotateActions = {0, 1, 2, 3, 4}; // 0:逆时针90°, 1:顺时针90°, 2:旋转180°, 3:水平翻转, 4:垂直翻转
        
        for (int i = 0; i < rotateOptions.length; i++) {
            final String option = rotateOptions[i];
            final int action = rotateActions[i];
            
            AppCompatButton rotateButton = new AppCompatButton(this);
            rotateButton.setText(option);
            rotateButton.setTextColor(Color.WHITE);
            rotateButton.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL));
            rotateButton.setTextSize(12);
            rotateButton.setPadding(4, 4, 4, 4);
            
            // 设置布局参数
            LinearLayout.LayoutParams params;
            if (isPortrait) {
                params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            } else {
                params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
            }
            
            // 添加间距
            params.setMargins(2, 2, 2, 2);
            rotateButton.setLayoutParams(params);
            
            // 设置点击事件
            rotateButton.setOnClickListener(v -> {
                // 防止按钮状态改变
                v.setPressed(false);
                
                // 执行对应的旋转或翻转操作
                switch (action) {
                    case 0:
                        // 逆时针旋转90°
                        imagePreview.rotateCounterClockwise();
                        break;
                    case 1:
                        // 顺时针旋转90°
                        imagePreview.rotateClockwise();
                        break;
                    case 2:
                        // 旋转180°
                        imagePreview.rotate180();
                        break;
                    case 3:
                        // 水平翻转
                        imagePreview.flipHorizontal();
                        break;
                    case 4:
                        // 垂直翻转
                        imagePreview.flipVertical();
                        break;
                }
            });
            
            // 添加到工具栏
            secondaryToolbar.addView(rotateButton);
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
        
        // 添加确认裁剪按钮
        AppCompatButton confirmCropButton = new AppCompatButton(this);
        confirmCropButton.setText("确认裁剪");
        confirmCropButton.setTextColor(Color.WHITE);
        confirmCropButton.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_CONFIRM));
        confirmCropButton.setTextSize(14);
        confirmCropButton.setPadding(8, 4, 8, 4);
        
        // 设置确认裁剪按钮的布局参数
        LinearLayout.LayoutParams confirmParams;
        if (isPortrait) {
            confirmParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f);
        } else {
            confirmParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.5f);
        }
        confirmParams.setMargins(8, 8, 8, 8);
        confirmCropButton.setLayoutParams(confirmParams);
        
        // 设置确认裁剪按钮的点击事件
        confirmCropButton.setOnClickListener(v -> {
            // 执行裁剪操作
            Bitmap croppedBitmap = imagePreview.performCrop();
            
            if (croppedBitmap != null) {
                // 裁剪成功，退出裁剪模式
                toggleButtonSelection(btnCrop);
                Toast.makeText(this, "裁剪成功，图片已更新到编辑区", Toast.LENGTH_SHORT).show();
            } else {
                // 裁剪失败，退出裁剪模式
                toggleButtonSelection(btnCrop);
                Toast.makeText(this, "裁剪失败：无效的裁剪区域", Toast.LENGTH_SHORT).show();
            }
        });
        
        // 添加确认裁剪按钮到副工具栏
        secondaryToolbar.addView(confirmCropButton);
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
        if (secondaryToolbarScroll != null) {
            secondaryToolbarScroll.setVisibility(View.GONE);
        }
        
        // 如果当前是裁剪模式，退出裁剪模式
        if (imagePreview != null && currentSelectedButton == btnCrop) {
            imagePreview.setCropMode(false);
            // 将确认按钮改回保存按钮
            btnSave.setText("保存");
        } else if (imagePreview != null && currentSelectedButton == btnText) {
            // 如果当前是文字编辑模式，退出文字编辑模式
            imagePreview.setTextMode(false);
        }
    }
    
    /**
     * 创建文字工具的工具栏
     */
    private void createTextToolbar() {
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        
        // 设置工具栏方向
        secondaryToolbar.setOrientation(isPortrait ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        
        // 设置按钮尺寸为正方形（48dp）
        int buttonSize = (int) (48 * getResources().getDisplayMetrics().density);
        
        // 创建删除按钮
        AppCompatButton btnDelete = new AppCompatButton(this);
        btnDelete.setText("删除");
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL));
        btnDelete.setOnClickListener(v -> {
            // 移除当前选中的文本框及内部文字
            if (imagePreview != null && imagePreview.getSelectedTextElement() != null) {
                imagePreview.removeSelectedTextElement();
                hideTextEditInput();
            }
        });
        
        // 创建确认按钮
        AppCompatButton btnConfirm = new AppCompatButton(this);
        btnConfirm.setText("确认");
        btnConfirm.setTextColor(Color.WHITE);
        btnConfirm.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL));
        btnConfirm.setOnClickListener(v -> {
            // 确认文字，将其固定到图片上
            if (textEditInput != null && textEditContainer.getVisibility() == View.VISIBLE) {
                String newText = textEditInput.getText().toString();
                if (!newText.isEmpty()) {
                    // 更新选中的文本内容
                    imagePreview.updateSelectedTextContent(newText);
                    // 立即刷新视图，确保文本内容同步更新
                    imagePreview.invalidate();
                    // 清空选中的文本元素引用
                    selectedTextElement = null;
                }
                hideTextEditInput();
            }
            imagePreview.confirmTextElements();
            toggleButtonSelection(btnText);
        });
        
        // 初始化字体按钮
        btnFont = new AppCompatButton(this);
        btnFont.setText("宋体"); // 初始显示宋体
        btnFont.setTextColor(Color.WHITE);
        btnFont.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL));
        
        // 初始化颜色按钮
        btnColor = new AppCompatButton(this);
        btnColor.setText("白色"); // 初始显示默认颜色名称
        btnColor.setTextColor(Color.WHITE); // 初始文字颜色为默认颜色
        btnColor.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL));
        
        // 16种常用颜色和对应的颜色名称
        final int[] colorOptions = {
            Color.parseColor("#FFFFFF"), // 白色
            Color.parseColor("#000000"), // 黑色
            Color.parseColor("#FF0000"), // 红色
            Color.parseColor("#00FF00"), // 绿色
            Color.parseColor("#0000FF"), // 蓝色
            Color.parseColor("#FFFF00"), // 黄色
            Color.parseColor("#FF00FF"), // 紫色
            Color.parseColor("#00FFFF"), // 青色
            Color.parseColor("#FFA500"), // 橙色
            Color.parseColor("#800080"), // 深紫色
            Color.parseColor("#008000"), // 深绿色
            Color.parseColor("#000080"), // 深蓝色
            Color.parseColor("#808000"), // 橄榄色
            Color.parseColor("#800000"), // 深红色
            Color.parseColor("#808080"), // 灰色
            Color.parseColor("#C0C0C0")  // 银色
        };
        final String[] colorNames = {
            "白色",
            "黑色",
            "红色",
            "绿色",
            "蓝色",
            "黄色",
            "紫色",
            "青色",
            "橙色",
            "深紫色",
            "深绿色",
            "深蓝色",
            "橄榄色",
            "深红色",
            "灰色",
            "银色"
        };
        
        // 设置颜色按钮点击事件
        btnColor.setOnClickListener(v -> {
            // 创建颜色选择对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(EditImageActivity.this);
            builder.setTitle("选择颜色");
            
            // 创建颜色选择网格
            LinearLayout[] rows = new LinearLayout[4];
            LinearLayout colorGrid = new LinearLayout(EditImageActivity.this);
            colorGrid.setOrientation(LinearLayout.VERTICAL);
            colorGrid.setPadding(20, 20, 20, 20);
            
            for (int i = 0; i < 4; i++) {
                rows[i] = new LinearLayout(EditImageActivity.this);
                rows[i].setOrientation(LinearLayout.HORIZONTAL);
                rows[i].setGravity(Gravity.CENTER);
                rows[i].setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                
                for (int j = 0; j < 4; j++) {
                    final int index = i * 4 + j;
                    View colorView = new View(EditImageActivity.this);
                    colorView.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
                    colorView.setBackgroundColor(colorOptions[index]);
                    colorView.setPadding(10, 10, 10, 10);
                    colorView.setClickable(true);
                    colorView.setOnClickListener(view -> {
                        // 更新选中文字的颜色
                        if (imagePreview != null) {
                            imagePreview.updateSelectedTextColor(colorOptions[index]);
                            // 更新按钮文本为当前颜色名称
                            btnColor.setText(colorNames[index]);
                            // 设置按钮文字颜色与当前选择的颜色相同
                            btnColor.setTextColor(colorOptions[index]);
                        }
                    });
                    rows[i].addView(colorView);
                }
                colorGrid.addView(rows[i]);
            }
            
            builder.setView(colorGrid);
            builder.setPositiveButton("确定", null);
            builder.show();
        });
        
        // 获取当前选中文字的颜色（如果有）
        if (imagePreview != null && imagePreview.getSelectedTextElement() != null) {
            int currentColor = imagePreview.getSelectedTextColor();
            
            // 查找当前颜色对应的名称
            String currentColorName = "白色"; // 默认颜色名称
            for (int i = 0; i < colorOptions.length; i++) {
                if (colorOptions[i] == currentColor) {
                    currentColorName = colorNames[i];
                    break;
                }
            }
            
            // 更新按钮文本为当前颜色名称
            btnColor.setText(currentColorName);
            // 设置按钮文字颜色与当前选择的颜色相同
            btnColor.setTextColor(currentColor);
        }
        
        // 创建字体选项数组
        final String[] fontOptions = {"simsun", "simhei", "microsoft yahei", "kaiti", "dengxian"};
        final String[] fontDisplayNames = {"宋体", "黑体", "微软雅黑", "楷体", "等线"};
        
        // 设置字体按钮点击事件
        btnFont.setOnClickListener(v -> {
            // 创建上拉列表对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(EditImageActivity.this);
            builder.setTitle("选择字体");
            builder.setItems(fontDisplayNames, (dialog, which) -> {
                // 更新选中文字的字体
                if (imagePreview != null) {
                    imagePreview.updateSelectedTextFont(fontOptions[which]);
                    // 更新字体按钮文本为当前选择的字体
                    btnFont.setText(fontDisplayNames[which]);
                }
            });
            builder.show();
        });
        
        // 获取当前选中文字的字体（如果有）
        if (imagePreview != null && imagePreview.getSelectedTextElement() != null) {
            String currentFont = imagePreview.getSelectedTextElement().fontName;
            // 更新字体按钮文本
            for (int i = 0; i < fontOptions.length; i++) {
                if (fontOptions[i].equals(currentFont)) {
                    btnFont.setText(fontDisplayNames[i]);
                    break;
                }
            }
        }
        
        // 创建字号选择区
        LinearLayout fontSizeContainer = new LinearLayout(this);
        fontSizeContainer.setOrientation(isPortrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        fontSizeContainer.setGravity(Gravity.CENTER);
        fontSizeContainer.setPadding(8, 8, 8, 8);
        
        // 字号标题
        TextView fontSizeTitle = new TextView(this);
        fontSizeTitle.setText(isPortrait ? "字号" : "字\n号");
        fontSizeTitle.setTextColor(Color.WHITE);
        fontSizeTitle.setGravity(Gravity.CENTER);
        fontSizeTitle.setTextSize(12);
        fontSizeTitle.setLayoutParams(isPortrait ? 
            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1) : 
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        fontSizeContainer.addView(fontSizeTitle);
        
        // 字号选择框（包含加减按钮和数值显示）
        LinearLayout fontSizeSelector = new LinearLayout(this);
        fontSizeSelector.setOrientation(isPortrait ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        fontSizeSelector.setGravity(Gravity.CENTER);
        fontSizeSelector.setLayoutParams(isPortrait ? 
            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 2) :
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2));
        
        // 减号按钮
        AppCompatButton btnDecrease = new AppCompatButton(this);
        btnDecrease.setText("-");
        btnDecrease.setTextColor(Color.WHITE);
        btnDecrease.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL));
        btnDecrease.setGravity(Gravity.CENTER);
        btnDecrease.setLayoutParams(isPortrait ? 
            new LinearLayout.LayoutParams(buttonSize*2/3, LinearLayout.LayoutParams.MATCH_PARENT) : 
            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, buttonSize*2/3));
        
        // 字号显示
        TextView fontSizeDisplay = new TextView(this);
        fontSizeDisplay.setTextColor(Color.WHITE);
        fontSizeDisplay.setGravity(Gravity.CENTER);
        fontSizeDisplay.setTextSize(14);
        fontSizeDisplay.setMinWidth(40);
        fontSizeDisplay.setLayoutParams(isPortrait ? 
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.8f) : 
            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.8f));
        
        // 加号按钮
        AppCompatButton btnIncrease = new AppCompatButton(this);
        btnIncrease.setText("+");
        btnIncrease.setTextColor(Color.WHITE);
        btnIncrease.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL));
        btnIncrease.setGravity(Gravity.CENTER);
        btnIncrease.setLayoutParams(isPortrait ? 
            new LinearLayout.LayoutParams(buttonSize*2/3, LinearLayout.LayoutParams.MATCH_PARENT) : 
            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, buttonSize*2/3));
        
        // 获取当前选中文字的字号（如果有）
        int currentFontSize = 16; // 默认值
        if (imagePreview != null && imagePreview.getSelectedTextElement() != null) {
            currentFontSize = Math.round(imagePreview.getSelectedTextElement().textSize / 3.75f); // 将60/3.75=16转换为12-36范围
        }
        fontSizeDisplay.setText(String.valueOf(currentFontSize));
        
        // 加减按钮点击事件
        btnDecrease.setOnClickListener(v -> {
            int currentSize = Integer.parseInt(fontSizeDisplay.getText().toString());
            if (currentSize > 12) {
                currentSize--;
                fontSizeDisplay.setText(String.valueOf(currentSize));
                if (imagePreview != null && imagePreview.getSelectedTextElement() != null) {
                    imagePreview.updateSelectedTextSize(currentSize * 3.75f);
                }
            }
        });
        
        btnIncrease.setOnClickListener(v -> {
            int currentSize = Integer.parseInt(fontSizeDisplay.getText().toString());
            if (currentSize < 36) {
                currentSize++;
                fontSizeDisplay.setText(String.valueOf(currentSize));
                if (imagePreview != null && imagePreview.getSelectedTextElement() != null) {
                    imagePreview.updateSelectedTextSize(currentSize * 3.75f);
                }
            }
        });
        
        // 添加控件到选择器
        fontSizeSelector.addView(btnDecrease);
        fontSizeSelector.addView(fontSizeDisplay);
        fontSizeSelector.addView(btnIncrease);
        
        // 添加选择器到容器
        fontSizeContainer.addView(fontSizeSelector);
        
        // 创建透明度选择区
        LinearLayout alphaContainer = new LinearLayout(this);
        alphaContainer.setOrientation(isPortrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        alphaContainer.setGravity(Gravity.CENTER);
        alphaContainer.setPadding(8, 8, 8, 8);
        
        // 透明度标题
        TextView alphaTitle = new TextView(this);
        alphaTitle.setText(isPortrait ? "透明度" : "透\n明\n度");
        alphaTitle.setTextColor(Color.WHITE);
        alphaTitle.setGravity(Gravity.CENTER);
        alphaTitle.setTextSize(12);
        alphaTitle.setLayoutParams(isPortrait ? 
            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1) : 
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        alphaContainer.addView(alphaTitle);
        
        // 透明度选择框（包含加减按钮和数值显示）
        LinearLayout alphaSelector = new LinearLayout(this);
        alphaSelector.setOrientation(isPortrait ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        alphaSelector.setGravity(Gravity.CENTER);
        alphaSelector.setLayoutParams(isPortrait ? 
            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 2) :
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2));
        
        // 减号按钮
        AppCompatButton btnDecreaseAlpha = new AppCompatButton(this);
        btnDecreaseAlpha.setText("-");
        btnDecreaseAlpha.setTextColor(Color.WHITE);
        btnDecreaseAlpha.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL));
        btnDecreaseAlpha.setGravity(Gravity.CENTER);
        btnDecreaseAlpha.setLayoutParams(isPortrait ? 
            new LinearLayout.LayoutParams(buttonSize*2/3, LinearLayout.LayoutParams.MATCH_PARENT) : 
            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, buttonSize*2/3));
        
        // 透明度显示
        TextView alphaDisplay = new TextView(this);
        alphaDisplay.setTextColor(Color.WHITE);
        alphaDisplay.setGravity(Gravity.CENTER);
        alphaDisplay.setTextSize(14);
        alphaDisplay.setMinWidth(40);
        alphaDisplay.setLayoutParams(isPortrait ? 
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.8f) : 
            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.8f));
        
        // 加号按钮
        AppCompatButton btnIncreaseAlpha = new AppCompatButton(this);
        btnIncreaseAlpha.setText("+");
        btnIncreaseAlpha.setTextColor(Color.WHITE);
        btnIncreaseAlpha.setBackgroundTintList(getResources().getColorStateList(BUTTON_BACKGROUND_NORMAL));
        btnIncreaseAlpha.setGravity(Gravity.CENTER);
        btnIncreaseAlpha.setLayoutParams(isPortrait ? 
            new LinearLayout.LayoutParams(buttonSize*2/3, LinearLayout.LayoutParams.MATCH_PARENT) : 
            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, buttonSize*2/3));
        
        // 获取当前选中文字的透明度（如果有）
        int currentAlpha = 100; // 默认值100%
        if (imagePreview != null && imagePreview.getSelectedTextElement() != null) {
            currentAlpha = Math.round((float)imagePreview.getSelectedTextAlpha() / 255 * 100); // 将0-255转换为0-100范围
        }
        alphaDisplay.setText(String.valueOf(currentAlpha) + "%");
        
        // 加减按钮点击事件
        btnDecreaseAlpha.setOnClickListener(v -> {
            int currentValue = Integer.parseInt(alphaDisplay.getText().toString().replace("%", ""));
            if (currentValue > 50) {
                currentValue--;
                alphaDisplay.setText(String.valueOf(currentValue) + "%");
                if (imagePreview != null && imagePreview.getSelectedTextElement() != null) {
                    imagePreview.updateSelectedTextAlpha(Math.round((float)currentValue / 100 * 255));
                }
            }
        });
        
        btnIncreaseAlpha.setOnClickListener(v -> {
            int currentValue = Integer.parseInt(alphaDisplay.getText().toString().replace("%", ""));
            if (currentValue < 100) {
                currentValue++;
                alphaDisplay.setText(String.valueOf(currentValue) + "%");
                if (imagePreview != null && imagePreview.getSelectedTextElement() != null) {
                    imagePreview.updateSelectedTextAlpha(Math.round((float)currentValue / 100 * 255));
                }
            }
        });
        
        // 添加控件到选择器
        alphaSelector.addView(btnDecreaseAlpha);
        alphaSelector.addView(alphaDisplay);
        alphaSelector.addView(btnIncreaseAlpha);
        
        // 添加选择器到容器
        alphaContainer.addView(alphaSelector);
        
        // 添加按钮到工具栏
        if (isPortrait) {
            // 竖屏模式：删除和确认按钮放在最左侧
            // 删除按钮使用固定尺寸
            secondaryToolbar.addView(btnDelete, new LinearLayout.LayoutParams(buttonSize, LinearLayout.LayoutParams.MATCH_PARENT));
            // 确认按钮使用固定尺寸
            secondaryToolbar.addView(btnConfirm, new LinearLayout.LayoutParams(buttonSize, LinearLayout.LayoutParams.MATCH_PARENT));
            // 字体按钮使用权重布局
            secondaryToolbar.addView(btnFont, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
            // 颜色按钮使用权重布局
            secondaryToolbar.addView(btnColor, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
            // 字号选择区
            secondaryToolbar.addView(fontSizeContainer, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f));
            // 透明度选择区
            secondaryToolbar.addView(alphaContainer, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f));
        } else {
            // 横屏模式：删除和确认按钮放在最上侧
            // 删除按钮使用固定尺寸
            secondaryToolbar.addView(btnDelete, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, buttonSize));
            // 确认按钮使用固定尺寸
            secondaryToolbar.addView(btnConfirm, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, buttonSize));
            // 字体按钮使用权重布局
            secondaryToolbar.addView(btnFont, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
            // 颜色按钮使用权重布局
            secondaryToolbar.addView(btnColor, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
            // 字号选择区
            secondaryToolbar.addView(fontSizeContainer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.5f));
            // 透明度选择区
            secondaryToolbar.addView(alphaContainer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.5f));
            // 添加一个高度为100像素的占位区域
            View placeholder = new View(this);
            secondaryToolbar.addView(placeholder, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 100));
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
            // 获取包含所有修改的完整尺寸图片
            Bitmap currentBitmap = imagePreview.getFullSizeProcessedBitmap();
            
            if (currentBitmap == null) {
                Toast.makeText(this, "获取图片失败", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 添加水印
            Bitmap bitmapWithWatermark = addWatermark(currentBitmap);
            
            // 保存到相册
            String savedImagePath = saveBitmapToGallery(bitmapWithWatermark);
            
            if (savedImagePath != null) {
                // 显示保存成功提示及文件地址
                String message = "图片保存成功\n保存位置：" + savedImagePath;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                
                // 更新编辑区图片为固定贴纸后的图像
                // 首先固定所有贴纸元素到图片上
                imagePreview.confirmStickerElements();
                
                // 保存成功后返回主页面
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
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
        // 保存图片到本地
        checkStoragePermissionAndSaveImage();
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
        
        // 重新初始化文字编辑输入框
        initTextEditInput();
        
        // 设置按钮点击事件
        setupButtonListeners();
        
        // 更新文字输入框位置
        updateTextEditInputPosition(newConfig.orientation);
        
        // 恢复图片显示
        if (currentImageUri != null) {
            loadImage(currentImageUri);
            // 更新baseOriginalBitmap为当前显示的完整图像，并删除之前的图像
            imagePreview.updateBaseOriginalBitmapFromCurrentDisplay();
        }
    }
}