package com.tiny.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.format.Formatter;
import android.util.DisplayMetrics;

import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LauncherActivity extends Activity {

    // --- Pure Java 17 Record ---
    public record AppModel(String name, Drawable icon, String packageName, boolean isLeanback) {}

    // --- UI Controls ---
    private FrameLayout rootOverlayFrame;
    private ImageSwitcher wallpaperSwitcher;
    private HorizontalScrollView horizontalAppScrollView;
    private LinearLayout horizontalAppContainer;
    private TextView drawerTitleView;
    private Runnable drawerBackAction = null;
    private TextView clockTextView, weatherStatusTextView, weatherTempTextView, weatherRhTextView, weatherWindTextView;
    private ImageView weatherIconView, settingsGear;
    private LinearLayout topWidgetRow, sideDrawerContainer;
    private ScrollView sideDrawerContentScrollView;

    // --- App & Data Models ---
    private final List<AppModel> appList = new ArrayList<>();
    private SharedPreferences prefs;
    private boolean isMoveMode = false;
    private boolean isAnimatingMove = false;
    private int moveSourcePosition = -1;

    // --- Dynamic Theming ---
    private int currentAccentColor = Color.parseColor("#007AFF");

    // --- Timers & Handlers ---
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private final Handler wallpaperHandler = new Handler(Looper.getMainLooper());
    private final Handler weatherHandler = new Handler(Looper.getMainLooper());
    private Runnable clockRunnable, idleRunnable, wallpaperRunnable, weatherRunnable;

    // --- Wallpapers & State ---
    private final List<File> wallpaperFiles = new ArrayList<>();
    private int currentWallpaperIndex = 0;
    private boolean isSideDrawerOpen = false;
    private boolean isInSubmenu = false;
    private int lastMainMenuIdx = 0;
    private final String[] PASSCODE_PRESETS = {"►", "► ► ◄", "▲ ▲ ◄", "▲ ► ▼", "◄ ◄ ►", "▼ ▼ ▲"};
    private String shortcutPickerKey = null;
    private ServerSocket webSetupServerSocket;
    private boolean isWebServerRunning = false;

    // --- Slideshow Interval Options (in Milliseconds) ---
    private final long[] SLIDESHOW_INTERVALS = {15000L, 30000L, 60000L, 300000L, 600000L, 1200000L, 1800000L, 0L};
    private final long[] IDLE_TIMEOUT_MS = {120000L, 300000L, 60000L, 0L};
    private final String[] IDLE_TIMEOUT_LABELS = {"2min", "5min", "10min", "off"};
    private final String[] SLIDESHOW_LABELS = {"15sec", "30sec", "1min", "5min", "10min", "20min", "30min", "off"};

    // Receiver to auto-refresh app grid on install/uninstall
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadInstalledApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("BareLauncherPrefs", MODE_PRIVATE);

        // --- 1. Root Overlay Frame ---
        rootOverlayFrame = new FrameLayout(this);
        rootOverlayFrame.setClipChildren(false);
        rootOverlayFrame.setClipToPadding(false);
        rootOverlayFrame.setBackgroundColor(Color.parseColor("#2B2200"));

        // --- 2. Wallpaper ImageSwitcher ---
        wallpaperSwitcher = new ImageSwitcher(this);
        wallpaperSwitcher.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wallpaperSwitcher.setFactory(() -> {
            ImageView iv = new ImageView(LauncherActivity.this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setLayoutParams(new ImageSwitcher.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return iv;
        });

        wallpaperSwitcher.setInAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left));
        wallpaperSwitcher.setOutAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right));
        rootOverlayFrame.addView(wallpaperSwitcher);

        // --- 3. Main Content Overlay ---
        LinearLayout mainOverlayLayout = new LinearLayout(this);
        mainOverlayLayout.setOrientation(LinearLayout.VERTICAL);
        mainOverlayLayout.setPadding(50, 30, 50, 0);
        mainOverlayLayout.setClipChildren(false);
        mainOverlayLayout.setClipToPadding(false);
        mainOverlayLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // --- 4. Top Status Header (Weather + Clock + Gear) ---
        topWidgetRow = new LinearLayout(this);
        topWidgetRow.setOrientation(LinearLayout.HORIZONTAL);
        topWidgetRow.setGravity(Gravity.CENTER_VERTICAL);
        topWidgetRow.setPadding(0, 0, 0, 20);

        // Weather Section (Left)
        LinearLayout weatherSection = new LinearLayout(this);
        weatherSection.setOrientation(LinearLayout.HORIZONTAL);
        weatherSection.setGravity(Gravity.CENTER_VERTICAL);

        weatherIconView = new ImageView(this);
        weatherIconView.setLayoutParams(new LinearLayout.LayoutParams(64, 64));
        weatherIconView.setImageResource(android.R.drawable.ic_menu_compass);

        LinearLayout weatherTextGroup = new LinearLayout(this);
        weatherTextGroup.setOrientation(LinearLayout.VERTICAL);
        weatherTextGroup.setPadding(15, 0, 30, 0);

        weatherStatusTextView = new TextView(this);
        weatherStatusTextView.setText("Clear");
        weatherStatusTextView.setTextColor(Color.WHITE);
        weatherStatusTextView.setTextSize(16);

        weatherWindTextView = new TextView(this);
        weatherWindTextView.setText("Wind: 1 m/s  Gusts: 3 m/s");
        weatherWindTextView.setTextColor(Color.LTGRAY);
        weatherWindTextView.setTextSize(12);

        weatherTextGroup.addView(weatherStatusTextView);
        weatherTextGroup.addView(weatherWindTextView);

        weatherTempTextView = new TextView(this);
        weatherTempTextView.setText("21° Temp");
        weatherTempTextView.setTextColor(Color.WHITE);
        weatherTempTextView.setTextSize(22);
        weatherTempTextView.setPadding(20, 0, 20, 0);

        weatherRhTextView = new TextView(this);
        weatherRhTextView.setText("51 RH");
        weatherRhTextView.setTextColor(Color.WHITE);
        weatherRhTextView.setTextSize(22);
        weatherRhTextView.setPadding(20, 0, 20, 0);

        weatherSection.addView(weatherIconView);
        weatherSection.addView(weatherTextGroup);
        weatherSection.addView(weatherTempTextView);
        weatherSection.addView(weatherRhTextView);

        // Clock Section (Center Flex)
        clockTextView = new TextView(this);
        clockTextView.setTextSize(22);
        clockTextView.setTextColor(Color.WHITE);
        clockTextView.setGravity(Gravity.END);
        clockTextView.setPadding(0, 0, 30, 0);
        LinearLayout.LayoutParams flexClockParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        clockTextView.setLayoutParams(flexClockParams);

        // Settings Gear Icon (Right)
        settingsGear = new ImageView(this);
        settingsGear.setImageResource(android.R.drawable.ic_menu_preferences);
        settingsGear.setFocusable(true);
        settingsGear.setPadding(15, 15, 15, 15);
        settingsGear.setOnClickListener(v -> {
            if (prefs.getBoolean("ParentalControlEnabled", false)) {
                verifyParentalPasscode(() -> toggleSideDrawer(true));
            } else {
                toggleSideDrawer(true);
            }
        });
        settingsGear.setOnLongClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
            return true;
        });

        topWidgetRow.addView(weatherSection);
        topWidgetRow.addView(clockTextView);
        topWidgetRow.addView(settingsGear);
        mainOverlayLayout.addView(topWidgetRow);

        // Space filler between header and bottom app row
        View spaceFiller = new View(this);
        LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        spaceFiller.setLayoutParams(spaceParams);
        mainOverlayLayout.addView(spaceFiller);

        // --- 5. Bottom Horizontal Widescreen App Row ---
        horizontalAppScrollView = new HorizontalScrollView(this);
        horizontalAppScrollView.setHorizontalScrollBarEnabled(false);
        horizontalAppScrollView.setClipToPadding(false);
        horizontalAppScrollView.setPadding(0, dpToPx(16), 0, dpToPx(4));

        horizontalAppContainer = new LinearLayout(this);
        horizontalAppContainer.setOrientation(LinearLayout.HORIZONTAL);
        horizontalAppContainer.setGravity(Gravity.BOTTOM);
        horizontalAppScrollView.addView(horizontalAppContainer);

        mainOverlayLayout.addView(horizontalAppScrollView);
        rootOverlayFrame.addView(mainOverlayLayout);

        // --- 6. Right Side Drawer Menu Container (`#1A1D24`) ---
        sideDrawerContainer = new LinearLayout(this);
        sideDrawerContainer.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable drawerBg = new GradientDrawable();
        drawerBg.setColor(Color.parseColor("#1A1D24"));
        float r16 = dpToPx(16);
        drawerBg.setCornerRadii(new float[]{ r16, r16, 0f, 0f, 0f, 0f, 0f, 0f });
        sideDrawerContainer.setBackground(drawerBg);
        sideDrawerContainer.setPadding(dpToPx(20), dpToPx(24), dpToPx(20), 0);

        FrameLayout.LayoutParams drawerLayoutParams = new FrameLayout.LayoutParams(
                dpToPx(340), ViewGroup.LayoutParams.MATCH_PARENT);
        drawerLayoutParams.gravity = Gravity.END | Gravity.TOP;
        drawerLayoutParams.topMargin = dpToPx(76);
        drawerLayoutParams.bottomMargin = 0;
        sideDrawerContainer.setLayoutParams(drawerLayoutParams);
        sideDrawerContainer.setVisibility(View.GONE);

        sideDrawerContentScrollView = new ScrollView(this);
        sideDrawerContentScrollView.setVerticalScrollBarEnabled(false);
        sideDrawerContainer.addView(sideDrawerContentScrollView);

        rootOverlayFrame.addView(sideDrawerContainer);

        setContentView(rootOverlayFrame);

        loadWallpapers();
        loadInstalledApps();
        registerPackageReceiver();
        startLiveClock();
        setupIdleAutoTimer();
        startWeatherEngine();
    }

    // --- Side Drawer Switcher (Zero Redrawing / Zero Flickering) ---
    private void toggleSideDrawer(boolean open) {
    isSideDrawerOpen = open;
    if (horizontalAppScrollView != null) {
        horizontalAppScrollView.setDescendantFocusability(
            open ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }
        if (open) {
            if (!isInSubmenu) {
                lastMainMenuIdx = 0;
                buildMainMenuInDrawer();
            }
            sideDrawerContainer.setVisibility(View.VISIBLE);
            sideDrawerContainer.animate().translationX(0f).setDuration(250).start();
        } else {
            sideDrawerContainer.animate().translationX(dpToPx(360)).setDuration(200)
                    .withEndAction(() -> sideDrawerContainer.setVisibility(View.GONE)).start();
        }
    }

    private void buildMainMenuInDrawer() {
        isInSubmenu = false;
        drawerBackAction = null;
        shortcutPickerKey = null;
        sideDrawerContainer.removeAllViews();

        drawerTitleView = new TextView(this);
        drawerTitleView.setText("Tiny Launcher");
        drawerTitleView.setTextColor(Color.WHITE);
        drawerTitleView.setTextSize(18);
        drawerTitleView.setPadding(dpToPx(12), 0, 0, dpToPx(16));
        sideDrawerContainer.addView(drawerTitleView);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
        sideDrawerContainer.addView(divider);

        View topSpacer = new View(this);
        topSpacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(8)));
        sideDrawerContainer.addView(topSpacer);

        sideDrawerContentScrollView = new ScrollView(this);
        sideDrawerContentScrollView.setVerticalScrollBarEnabled(false);
        sideDrawerContentScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout drawerContent = new LinearLayout(this);
        drawerContent.setOrientation(LinearLayout.VERTICAL);

        addDrawerMenuItem(drawerContent, "⧉", "Manage apps", () -> { lastMainMenuIdx = 0; openManageAppsSubmenu(); });
        addDrawerMenuItem(drawerContent, "⌨", "Button shortcuts", () -> { lastMainMenuIdx = 1; openButtonShortcutsSubmenu(); });
        addDrawerMenuItem(drawerContent, "⚿", "Parental control", () -> { lastMainMenuIdx = 2; openParentalControlSubmenu(); });
        addDrawerMenuItem(drawerContent, "⧈", "Wallpaper/slideshow", () -> { lastMainMenuIdx = 3; openWallpaperSubmenu(); });
        addDrawerMenuItem(drawerContent, "◷", "Clock menu", () -> { lastMainMenuIdx = 4; openClockSubmenu(); });
        addDrawerMenuItem(drawerContent, "◫", "Tile menu", () -> { lastMainMenuIdx = 5; openTileSettingsSubmenu(); });
        addDrawerMenuItem(drawerContent, "☼", "Weather menu", () -> { lastMainMenuIdx = 6; openWeatherSubmenu(); });
        addDrawerMenuItem(drawerContent, "⚙", "System settings", () -> startActivity(new Intent(Settings.ACTION_SETTINGS)));
        addDrawerMenuItem(drawerContent, "ℹ", "About", () -> { lastMainMenuIdx = 8; openAboutSubmenu(); });
        sideDrawerContentScrollView.addView(drawerContent);
        sideDrawerContainer.addView(sideDrawerContentScrollView);
        drawerContent.post(() -> { if (drawerContent.getChildCount() > lastMainMenuIdx) drawerContent.getChildAt(lastMainMenuIdx).requestFocus(); });
        }

    private void addDrawerMenuItem(LinearLayout container, String title, Runnable onClick) {
        addDrawerMenuItem(container, "", title, onClick);
    }

    private void addDrawerMenuItem(LinearLayout container, String symbol, String title, Runnable onClick) {
        addDrawerMenuItem(container, symbol, Color.parseColor("#5A5E6B"), title, onClick);
    }

    private void addDrawerMenuItem(LinearLayout container, String symbol, int symbolColor, String title, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);

        if (!symbol.isEmpty()) {
            TextView symbolView = new TextView(this);
            symbolView.setText(symbol);
            symbolView.setTextColor(symbolColor);
            symbolView.setTextSize(16);
            symbolView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            symbolView.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(28), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(symbolView);
        }

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

        row.addView(label);

        row.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                GradientDrawable shape = new GradientDrawable();
                shape.setColor(Color.parseColor("#333842"));
                shape.setCornerRadius(dpToPx(8));
                v.setBackground(shape);
            } else {
                v.setBackgroundColor(Color.TRANSPARENT);
            }
        });

        row.setOnClickListener(v -> onClick.run());
        container.addView(row);
    }

    private void applyTileRowPosition() {
        int posDp = prefs.getInt("TileRowPosition", 0);
        if (horizontalAppScrollView != null) horizontalAppScrollView.setTranslationY(dpToPx(-posDp));
    }

    private void applyTileStyles() {
        int W = prefs.getInt("TileSize", 160), H = W * 9 / 16, D = prefs.getInt("TileCornerRadius", 30);
        int R = dpToPx((int) Math.round((H / 2.0f) * (D / 90.0f)));
        int txtS = prefs.getInt("TileTextSize", 14), txtP = prefs.getInt("TileTextPosition", 0);
        if (horizontalAppContainer == null) return;
        for (int i = 0; i < horizontalAppContainer.getChildCount(); i++) {
            View c = horizontalAppContainer.getChildAt(i);
            if (c instanceof LinearLayout) {
                LinearLayout ic = (LinearLayout) c;
                if (ic.getChildCount() > 0 && ic.getChildAt(0) instanceof FrameLayout) {
                    FrameLayout bc = (FrameLayout) ic.getChildAt(0);
                    bc.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(W), dpToPx(H)));
                    GradientDrawable s = new GradientDrawable(); s.setColor(Color.parseColor("#FF1A1A1A"));
                    s.setCornerRadius(R); bc.setBackground(s);
                }
                if (ic.getChildCount() > 1 && ic.getChildAt(1) instanceof TextView) {
                    TextView tv = (TextView) ic.getChildAt(1);
                    tv.setTextSize(txtS); tv.setTranslationY(dpToPx(txtP));
                }
            }
        }
    }

    private void openTileSettingsSubmenu() {
        isInSubmenu = true; drawerBackAction = () -> buildMainMenuInDrawer();
        sideDrawerContentScrollView.removeAllViews();
        if (drawerTitleView != null) drawerTitleView.setText("Tile menu");
        LinearLayout container = new LinearLayout(this); container.setOrientation(LinearLayout.VERTICAL);
        addTileMenuRow(container, "◠", "Tiles corner radius", "TileCornerRadius", 30, 0, 90, 5, "°", this::applyTileStyles);
        addTileMenuRow(container, "◫", "Tile size", "TileSize", 160, 80, 200, 10, "dp", this::applyTileStyles);
        addTileMenuRow(container, "↕", "Row position", "TileRowPosition", 0, -150, 150, 10, "dp", this::applyTileRowPosition);
        addTileMenuRow(container, "Aa", "Text size", "TileTextSize", 14, 10, 24, 1, "sp", this::applyTileStyles);
        addTileMenuRow(container, "⇕", "Text position", "TileTextPosition", 0, -150, 100, 2, "dp", this::applyTileStyles);
        sideDrawerContentScrollView.addView(container);
        container.post(() -> { if (container.getChildCount() > 0) container.getChildAt(0).requestFocus(); });
    }
    private void addTileMenuRow(LinearLayout c, String sym, String title, String key, int def, int min, int max, int step, String unit, Runnable onChg) {
        int val = prefs.getInt(key, def); boolean isS = key.equals("TileRowPosition") || key.equals("TileTextPosition");
        View row = addDrawerStatusItem(c, sym, title, (isS && val > 0 ? "+" : "") + val + unit, null); if (row == null) return;
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2)); int id = View.generateViewId(); row.setId(id); row.setNextFocusLeftId(id); row.setNextFocusRightId(id); row.setOnClickListener(null);
        row.setOnKeyListener((v, kCode, evt) -> {
            if (evt.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (kCode == KeyEvent.KEYCODE_DPAD_RIGHT || kCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                int cVal = prefs.getInt(key, def), nVal = (kCode == KeyEvent.KEYCODE_DPAD_RIGHT) ? Math.min(max, cVal + step) : Math.max(min, cVal - step);
                prefs.edit().putInt(key, nVal).apply();
                TextView tv = row.findViewById(1001); if (tv != null) tv.setText((isS && nVal > 0 ? "+" : "") + nVal + unit);
                if (onChg != null) onChg.run(); return true;
            }
            return false;
        });
    }

    private void openAboutSubmenu() {
        sideDrawerContentScrollView.removeAllViews();

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("About");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setPadding(dpToPx(12), 0, 0, dpToPx(16));
        container.addView(title);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
        container.addView(divider);

        TextView infoText = new TextView(this);
        infoText.setText("Tiny Launcher v1.0\nPure Java 17 Android TV Launcher\nZero External Dependencies");
        infoText.setTextColor(Color.LTGRAY);
        infoText.setTextSize(14);
        infoText.setPadding(dpToPx(12), dpToPx(16), dpToPx(12), dpToPx(16));
        container.addView(infoText);

        
        sideDrawerContentScrollView.addView(container);
    }

    private void addDrawerAppItem(LinearLayout container, Drawable iconDrawable, String title, java.util.function.Consumer<View> onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);

        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(iconDrawable);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(22), dpToPx(22)));
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout iconFrame = new LinearLayout(this);
        iconFrame.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        iconFrame.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(28), ViewGroup.LayoutParams.WRAP_CONTENT));
        iconFrame.addView(iconView);

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

        row.addView(iconFrame);
        row.addView(label);

        row.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                GradientDrawable shape = new GradientDrawable();
                shape.setColor(Color.parseColor("#333842"));
                shape.setCornerRadius(dpToPx(8));
                v.setBackground(shape);
            } else {
                v.setBackgroundColor(Color.TRANSPARENT);
            }
        });

        row.setOnClickListener(v -> onClick.accept(v));
        container.addView(row);
    }

    private void showHiddenAppOptionMenu(String pkg, String appName, View anchorView) {
        LinearLayout menuView = new LinearLayout(this);
        menuView.setOrientation(LinearLayout.VERTICAL);
        menuView.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1A1D24"));
        bg.setCornerRadius(dpToPx(12));
        menuView.setBackground(bg);

        TextView titleView = new TextView(this);
        titleView.setText(appName);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(15);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, dpToPx(4), 0, dpToPx(10));
        menuView.addView(titleView);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
        menuView.addView(divider);

        android.widget.PopupWindow popup = new android.widget.PopupWindow(
                menuView, dpToPx(180), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(0);
        popup.setOutsideTouchable(true);

        addPopupMenuItem(menuView, "►", "Open App", () -> {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) startActivity(i);
        }, popup);

        addPopupMenuItem(menuView, "⧉", "Unhide App", () -> {
            Set<String> hidden = new HashSet<>(prefs.getStringSet("HiddenApps", new HashSet<>()));
            hidden.remove(pkg);
            prefs.edit().putStringSet("HiddenApps", hidden).apply();
            loadInstalledApps();
            openManageAppsSubmenu();
        }, popup);

        popup.showAtLocation(anchorView, Gravity.CENTER, 0, 0);
    }

    private void openManageAppsSubmenu() {
        isInSubmenu = true;
        sideDrawerContainer.removeAllViews();

        TextView titleView = new TextView(this);
        titleView.setText("Manage apps");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(18);
        titleView.setPadding(dpToPx(12), 0, 0, dpToPx(16));
        sideDrawerContainer.addView(titleView);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
        sideDrawerContainer.addView(divider);

        View topSpacer = new View(this);
        topSpacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(8)));
        sideDrawerContainer.addView(topSpacer);

        sideDrawerContentScrollView = new ScrollView(this);
        sideDrawerContentScrollView.setVerticalScrollBarEnabled(false);
        sideDrawerContentScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        Set<String> hidden = prefs.getStringSet("HiddenApps", new HashSet<>());

        if (hidden.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No hidden apps.");
            emptyText.setTextColor(Color.GRAY);
            emptyText.setTextSize(14);
            emptyText.setPadding(dpToPx(12), dpToPx(16), dpToPx(12), dpToPx(16));
            container.addView(emptyText);
        } else {
            PackageManager pm = getPackageManager();
            for (String pkg : hidden) {
                try {
                    String appName = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                    Drawable appIcon = pm.getApplicationIcon(pkg);
                    addDrawerAppItem(container, appIcon, appName, (anchor) -> showHiddenAppOptionMenu(pkg, appName, anchor));
                } catch (Exception ignored) {}
            }
        }

        sideDrawerContentScrollView.addView(container);
        sideDrawerContainer.addView(sideDrawerContentScrollView);
    }

    private void openButtonShortcutsSubmenu() {
        isInSubmenu = true;
        shortcutPickerKey = null;
        sideDrawerContainer.removeAllViews();

        TextView titleView = new TextView(this);
        titleView.setText("Button shortcuts");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(18);
        titleView.setPadding(dpToPx(12), 0, 0, dpToPx(16));
        sideDrawerContainer.addView(titleView);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
        sideDrawerContainer.addView(divider);

        View topSpacer = new View(this);
        topSpacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(8)));
        sideDrawerContainer.addView(topSpacer);

        sideDrawerContentScrollView = new ScrollView(this);
        sideDrawerContentScrollView.setVerticalScrollBarEnabled(false);
        sideDrawerContentScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        addDrawerMenuItem(container, "●", Color.parseColor("#FF3B30"), "Red: " + getShortcutName("RedShortcut"), () -> openAppPickerForShortcut("RedShortcut"));
        addDrawerMenuItem(container, "●", Color.parseColor("#007AFF"), "Blue: " + getShortcutName("BlueShortcut"), () -> openAppPickerForShortcut("BlueShortcut"));
        addDrawerMenuItem(container, "●", Color.parseColor("#34C759"), "Green: " + getShortcutName("GreenShortcut"), () -> openAppPickerForShortcut("GreenShortcut"));
        addDrawerMenuItem(container, "●", Color.parseColor("#FFCC00"), "Yellow: " + getShortcutName("YellowShortcut"), () -> openAppPickerForShortcut("YellowShortcut"));

        sideDrawerContentScrollView.addView(container);
        sideDrawerContainer.addView(sideDrawerContentScrollView);
    }

    private String getShortcutName(String key) {
        String pkg = prefs.getString(key, null);
        if (pkg == null || pkg.isEmpty()) return "Not assigned";
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return "Not assigned";
        }
    }

    private void openAppPickerForShortcut(String key) {
        shortcutPickerKey = key;
        sideDrawerContainer.removeAllViews();

        TextView titleView = new TextView(this);
        titleView.setText("Select App");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(18);
        titleView.setPadding(dpToPx(12), 0, 0, dpToPx(16));
        sideDrawerContainer.addView(titleView);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
        sideDrawerContainer.addView(divider);

        View topSpacer = new View(this);
        topSpacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(8)));
        sideDrawerContainer.addView(topSpacer);

        sideDrawerContentScrollView = new ScrollView(this);
        sideDrawerContentScrollView.setVerticalScrollBarEnabled(false);
        sideDrawerContentScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        addDrawerMenuItem(container, "Ø", Color.parseColor("#5A5E6B"), "Not assigned", () -> {
            prefs.edit().remove(key).apply();
            openButtonShortcutsSubmenu();
        });

        for (AppModel app : appList) {
            addDrawerAppItem(container, app.icon(), app.name(), (v) -> {
                prefs.edit().putString(key, app.packageName()).apply();
                openButtonShortcutsSubmenu();
            });
        }

        sideDrawerContentScrollView.addView(container);
        sideDrawerContainer.addView(sideDrawerContentScrollView);
    }

    private void verifyParentalPasscode(Runnable onSuccess) {
        int index = prefs.getInt("PasscodeIndex", 1);
        String requiredCode = PASSCODE_PRESETS[index];

        LinearLayout menuView = new LinearLayout(this);
        menuView.setOrientation(LinearLayout.VERTICAL);
        menuView.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1A1D24"));
        bg.setCornerRadius(dpToPx(12));
        menuView.setBackground(bg);

        TextView titleView = new TextView(this);
        titleView.setText("Parental Guard");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(16);
        titleView.setGravity(Gravity.CENTER);
        menuView.addView(titleView);

        TextView codeView = new TextView(this);
        codeView.setText(requiredCode);
        codeView.setTextColor(Color.parseColor("#007AFF"));
        codeView.setTextSize(22);
        codeView.setGravity(Gravity.CENTER);
        codeView.setPadding(0, dpToPx(12), 0, dpToPx(12));
        menuView.addView(codeView);

        android.widget.PopupWindow popup = new android.widget.PopupWindow(
                menuView, dpToPx(220), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(0);

        final StringBuilder entered = new StringBuilder();
        menuView.setFocusable(true);
        menuView.setFocusableInTouchMode(true);
        menuView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                String keyStr = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? "►" :
                                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? "◄" :
                                keyCode == KeyEvent.KEYCODE_DPAD_UP ? "▲" :
                                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? "▼" : "";
                if (!keyStr.isEmpty()) {
                    if (entered.length() > 0) entered.append(" ");
                    entered.append(keyStr);
                    if (entered.toString().equals(requiredCode)) {
                        popup.dismiss();
                        onSuccess.run();
                    } else if (!requiredCode.startsWith(entered.toString())) {
                        entered.setLength(0);
                    }
                    return true;
                }
            }
            return false;
        });

        popup.showAtLocation(rootOverlayFrame, Gravity.CENTER, 0, 0);
    }

    private View addDrawerStatusItem(LinearLayout container, String symbol, String title, String statusText, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);

        TextView symbolView = new TextView(this);
        symbolView.setText(symbol);
        symbolView.setTextColor(Color.parseColor("#5A5E6B"));
        symbolView.setTextSize(16);
        symbolView.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(28), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView statusView = new TextView(this);
        statusView.setId(1001);
        statusView.setText(statusText);
        statusView.setTextColor(Color.LTGRAY);
        statusView.setTextSize(14);

        row.addView(symbolView);
        row.addView(label);
        row.addView(statusView);

        row.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(hasFocus ? Color.parseColor("#333842") : Color.TRANSPARENT);
            shape.setCornerRadius(dpToPx(8));
            v.setBackground(shape);
        });

        if (onClick != null) {
            row.setOnClickListener(v -> onClick.run());
        }
        container.addView(row);
        return row;
    }

    private void openParentalControlSubmenu() {
        isInSubmenu = true;
        sideDrawerContainer.removeAllViews();

        TextView titleView = new TextView(this);
        titleView.setText("Parental control");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(18);
        titleView.setPadding(dpToPx(12), 0, 0, dpToPx(16));
        sideDrawerContainer.addView(titleView);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
        sideDrawerContainer.addView(divider);

        View topSpacer = new View(this);
        topSpacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(8)));
        sideDrawerContainer.addView(topSpacer);

        sideDrawerContentScrollView = new ScrollView(this);
        sideDrawerContentScrollView.setVerticalScrollBarEnabled(false);
        sideDrawerContentScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        boolean enabled = prefs.getBoolean("ParentalControlEnabled", false);
        View row1 = addDrawerStatusItem(container, "⚿", "Parental control", enabled ? "ON" : "OFF", null);
        row1.setOnClickListener(v -> {
            boolean cur = prefs.getBoolean("ParentalControlEnabled", false);
            boolean next = !cur;
            prefs.edit().putBoolean("ParentalControlEnabled", next).apply();
            TextView tv = row1.findViewById(1001);
            if (tv != null) tv.setText(next ? "ON" : "OFF");
        });

        int passIndex = prefs.getInt("PasscodeIndex", 1);
        View row2 = addDrawerStatusItem(container, "🔑", "Passcode", PASSCODE_PRESETS[passIndex], null);
        row2.setOnClickListener(v -> {
            int idx = prefs.getInt("PasscodeIndex", 1);
            int nextIndex = (idx + 1) % PASSCODE_PRESETS.length;
            prefs.edit().putInt("PasscodeIndex", nextIndex).apply();
            TextView tv = row2.findViewById(1001);
            if (tv != null) tv.setText(PASSCODE_PRESETS[nextIndex]);
        });

        sideDrawerContentScrollView.addView(container);
        sideDrawerContainer.addView(sideDrawerContentScrollView);
    }

        private void openSetWallpaperSubmenu() {
        isInSubmenu = true;
        drawerBackAction = () -> openWallpaperSubmenu();
        sideDrawerContentScrollView.removeAllViews();
        if (drawerTitleView != null) drawerTitleView.setText("Set wallpaper");
        LinearLayout container = new LinearLayout(this); container.setOrientation(LinearLayout.VERTICAL);

        String[][] explorers = {
            {"Cx File Explorer", "com.cxinventor.file.explorer"},
            {"X-plore", "com.lonelycatgames.Xplore"},
            {"Solid Explorer", "pl.solidexplorer2"},
            {"FX File Explorer", "nextapp.fx"},
            {"Total Commander", "com.ghisler.android.TotalCommander"},
            {"System Files", "com.google.android.documentsui"},
            {"Native Explorer", "com.android.documentsui"}
        };

        boolean found = false;
        android.content.pm.PackageManager pm = getPackageManager();
        for (String[] exp : explorers) {
            String name = exp[0]; String pkg = exp[1];
            try {
                pm.getPackageInfo(pkg, 0);
                found = true;
                addDrawerMenuItem(container, "⧉", Color.parseColor("#5A5E6B"), name, () -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        intent.setPackage(pkg);
                        startActivityForResult(intent, 1001);
                    } catch (Exception e) {
                        try {
                            Intent fb = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            fb.setType("image/*");
                            fb.setPackage(pkg);
                            startActivityForResult(fb, 1001);
                        } catch (Exception ignored) {}
                    }
                });
            } catch (Exception ignored) {}
        }

        if (!found) {
            TextView info = new TextView(this);
            info.setText("No file explorer installed.\n" + "Please install Cx File Explorer or X-plore.");
            info.setTextColor(Color.LTGRAY); info.setTextSize(14);
            info.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
            container.addView(info);
        }

        sideDrawerContentScrollView.addView(container);
    }

                    private int countWallpapersInFolder(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles((d, name) -> {
            String n = name.toLowerCase();
            return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
        });
        return files != null ? files.length : 0;
    }

    private View addDrawerFolderItem(LinearLayout container, String title, int count, Runnable onClick) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10)); row.setFocusable(true); row.setFocusableInTouchMode(true);
        TextView sym = new TextView(this); sym.setText("⧉"); sym.setTextColor(Color.parseColor("#5A5E6B")); sym.setTextSize(16);
        sym.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(28), -2)); row.addView(sym);
        TextView lbl = new TextView(this); lbl.setText(title); lbl.setTextColor(Color.WHITE); lbl.setTextSize(14);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f)); row.addView(lbl);
        TextView cnt = new TextView(this); cnt.setText(String.valueOf(count)); cnt.setTextColor(Color.parseColor("#8E8E93")); cnt.setTextSize(13);
        cnt.setPadding(dpToPx(8), 0, dpToPx(4), 0); row.addView(cnt);
        row.setOnFocusChangeListener((v, h) -> {
            if (h) { GradientDrawable s = new GradientDrawable(); s.setColor(Color.parseColor("#333842")); s.setCornerRadius(dpToPx(8)); v.setBackground(s); }
            else v.setBackgroundColor(Color.TRANSPARENT);
        });
        row.setOnClickListener(v -> onClick.run()); container.addView(row); return row;
    }

        private void openSlideshowFolderSubmenu() {
        isInSubmenu = true; drawerBackAction = () -> openWallpaperSubmenu();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.READ_MEDIA_IMAGES") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.READ_MEDIA_IMAGES"}, 2001);
            }
        }
        sideDrawerContentScrollView.removeAllViews();
        if (drawerTitleView != null) drawerTitleView.setText("Slideshow folder");
        LinearLayout container = new LinearLayout(this); container.setOrientation(LinearLayout.VERTICAL);

        addDrawerMenuItem(container, "⧉", Color.parseColor("#5A5E6B"), "System Folder Picker", () -> {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, 1002);
            } catch (Exception ignored) {}
        });

        java.util.List<File> folders = new java.util.ArrayList<>();
        File root = new File("/storage/emulated/0"); File pics = new File(root, "Pictures");
        if (pics.exists() && pics.isDirectory()) {
            File[] subs = pics.listFiles(File::isDirectory);
            if (subs != null) {
                for (File s : subs) {
                    if (s.getName().startsWith(".")) continue;
                    if (s.getName().equalsIgnoreCase("wallpapers") || s.getName().equalsIgnoreCase("wallpaper")) folders.add(0, s);
                    else folders.add(s);
                }
            }
            folders.add(pics);
        }
        File dl = new File(root, "Download"); if (dl.exists() && !folders.contains(dl)) folders.add(dl);
        File dcim = new File(root, "DCIM"); if (dcim.exists() && !folders.contains(dcim)) folders.add(dcim);

        for (File f : folders) {
            if (f.getName().startsWith(".")) continue;
            String label = f.getName();
            if (f.getParentFile() != null && !f.getParentFile().getName().equals("0")) label = f.getParentFile().getName() + " / " + f.getName();
            final String target = f.getAbsolutePath();
            int c = countWallpapersInFolder(f);
            addDrawerFolderItem(container, label, c, () -> {
                try { getFileStreamPath("custom_wallpaper.jpg").delete(); } catch (Exception ignored) {}
                prefs.edit().putString("WallpaperFolder", target).apply();
                loadWallpapers(); startWallpaperRotation(); openWallpaperSubmenu();
            });
        }

        sideDrawerContentScrollView.addView(container);
        container.post(() -> { if (container.getChildCount() > 0) container.getChildAt(0).requestFocus(); });
    }

    private void openWallpaperSubmenu() { openWallpaperSubmenu(0); }

    private void openWallpaperSubmenu(int focusIdx) {
        isInSubmenu = true;
        drawerBackAction = () -> buildMainMenuInDrawer();
        sideDrawerContentScrollView.removeAllViews();
        LinearLayout container = new LinearLayout(this); container.setOrientation(LinearLayout.VERTICAL);
        if (drawerTitleView != null) drawerTitleView.setText("Wallpaper/slideshow");
        addDrawerMenuItem(container, "⧈", "Set wallpaper", () -> openSetWallpaperSubmenu());
        addDrawerMenuItem(container, "⎘", "Slideshow folder", () -> openSlideshowFolderSubmenu());
        addSlideshowDurationMenuItem(container);
        addHideUiIdleMenuItem(container);
        addChangeEachMenuItem(container);
        sideDrawerContentScrollView.addView(container);
        container.post(() -> { if (container.getChildCount() > focusIdx) container.getChildAt(focusIdx).requestFocus(); });
    }

    
    private void addSlideshowDurationMenuItem(LinearLayout container) {
        long curInt = prefs.getLong("SlideshowInterval", 30000L); int idx = 0;
        for (int i = 0; i < SLIDESHOW_INTERVALS.length; i++) { if (SLIDESHOW_INTERVALS[i] == curInt) { idx = i; break; } }
        View row = addDrawerStatusItem(container, "⏱", "Slideshow duration", SLIDESHOW_LABELS[idx], null);
        if (row != null) {
            row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            int id = View.generateViewId(); row.setId(id); row.setNextFocusLeftId(id); row.setNextFocusRightId(id);
            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    long c = prefs.getLong("SlideshowInterval", 30000L); int cIdx = 0;
                    for (int i = 0; i < SLIDESHOW_INTERVALS.length; i++) { if (SLIDESHOW_INTERVALS[i] == c) { cIdx = i; break; } }
                    int nIdx = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) ? (cIdx + 1) % SLIDESHOW_LABELS.length : (cIdx - 1 + SLIDESHOW_LABELS.length) % SLIDESHOW_LABELS.length;
                    prefs.edit().putLong("SlideshowInterval", SLIDESHOW_INTERVALS[nIdx]).apply();
                    TextView tv = row.findViewById(1001); if (tv != null) tv.setText(SLIDESHOW_LABELS[nIdx]);
                    startWallpaperRotation(); return true;
                }
                return false;
            });
        }
    }

    
    private void addHideUiIdleMenuItem(LinearLayout container) {
        long current = prefs.getLong("IdleTimeout", 300000L); int idx = 1;
        for (int i = 0; i < IDLE_TIMEOUT_MS.length; i++) { if (IDLE_TIMEOUT_MS[i] == current) { idx = i; break; } }
        View row = addDrawerStatusItem(container, "⧇", "Hide UI when idle", IDLE_TIMEOUT_LABELS[idx], null);
        if (row != null) {
            row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            int id = View.generateViewId(); row.setId(id); row.setNextFocusLeftId(id); row.setNextFocusRightId(id);
            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    long c = prefs.getLong("IdleTimeout", 300000L); int cIdx = 0;
                    for (int i = 0; i < IDLE_TIMEOUT_MS.length; i++) { if (IDLE_TIMEOUT_MS[i] == c) { cIdx = i; break; } }
                    int nIdx = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) ? (cIdx + 1) % IDLE_TIMEOUT_LABELS.length : (cIdx - 1 + IDLE_TIMEOUT_LABELS.length) % IDLE_TIMEOUT_LABELS.length;
                    prefs.edit().putLong("IdleTimeout", IDLE_TIMEOUT_MS[nIdx]).apply();
                    TextView tv = row.findViewById(1001); if (tv != null) tv.setText(IDLE_TIMEOUT_LABELS[nIdx]);
                    resetIdleTimer(); return true;
                }
                return false;
            });
        }
    }


    
        private void addChangeEachMenuItem(LinearLayout container) {
        boolean chgRst = prefs.getBoolean("ChangeEachRestart", false);
        View row = addDrawerStatusItem(container, "↻", "Change each restart", chgRst ? "On" : "Off", null);
        if (row != null) {
            row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            int id = View.generateViewId(); row.setId(id); row.setNextFocusLeftId(id); row.setNextFocusRightId(id);
            row.setOnClickListener(null);
            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    boolean cur = prefs.getBoolean("ChangeEachRestart", false);
                    boolean next = !cur;
                    prefs.edit().putBoolean("ChangeEachRestart", next).apply();
                    TextView tv = row.findViewById(1001); if (tv != null) tv.setText(next ? "On" : "Off");
                    return true;
                }
                return false;
            });
        }
    }

    private void openClockSubmenu() {
    isInSubmenu = true;
    drawerBackAction = () -> buildMainMenuInDrawer();
    sideDrawerContentScrollView.removeAllViews();
    if (drawerTitleView != null) drawerTitleView.setText("Clock menu");
    LinearLayout container = new LinearLayout(this);
    container.setOrientation(LinearLayout.VERTICAL);
    String mode = prefs.getString("ClockMode", "Full");
    View row = addDrawerStatusItem(container, "◷", "Visibility", mode, null);
    if (row != null) {
        row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        int id = View.generateViewId(); row.setId(id); row.setNextFocusLeftId(id); row.setNextFocusRightId(id);
        row.setOnClickListener(null);
        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                String cur = prefs.getString("ClockMode", "Full");
                boolean n = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT);
                String nm = n ? (cur.equals("Full") ? "Time Only" : cur.equals("Time Only") ? "Off" : "Full") : (cur.equals("Full") ? "Off" : cur.equals("Off") ? "Time Only" : "Full");
                prefs.edit().putString("ClockMode", nm).apply();
                TextView tv = row.findViewById(1001); if (tv != null) tv.setText(nm);
                return true;
            }
            return false;
        });
    }
    sideDrawerContentScrollView.addView(container);
    container.post(() -> { if (container.getChildCount() > 0) container.getChildAt(0).requestFocus(); });
}

    private void openWeatherSubmenu() {
        sideDrawerContentScrollView.removeAllViews();

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Weather Settings");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setPadding(10, 0, 0, 20);
        container.addView(title);

        boolean enabled = prefs.getBoolean("WeatherEnabled", true);
        View weatherRow = addDrawerStatusItem(container, "☁️", "Weather Widget", enabled ? "On" : "Off", null);
        weatherRow.setOnClickListener(v -> {
            boolean cur = prefs.getBoolean("WeatherEnabled", true);
            boolean next = !cur;
            prefs.edit().putBoolean("WeatherEnabled", next).apply();
            weatherSectionVisibility(next);
            TextView tv = weatherRow.findViewById(1001);
            if (tv != null) tv.setText(next ? "On" : "Off");
        });

        addDrawerMenuItem(container, "📍", "Location (Open-Meteo)", () -> {
            final EditText input = new EditText(this);
            input.setHint("Type City Name...");
            new AlertDialog.Builder(this).setTitle("City Search").setView(input)
                    .setPositiveButton("Search", (d, w) -> searchCityCoordinates(input.getText().toString())).show();
        });

        addDrawerMenuItem(container, "⚡", "Shelly API URL", () -> {
            final EditText input = new EditText(this);
            input.setText(prefs.getString("ShellyApiUrl", ""));
            new AlertDialog.Builder(this).setTitle("Shelly Cloud Endpoint").setView(input)
                    .setPositiveButton("Save", (d, w) -> {
                        prefs.edit().putString("ShellyApiUrl", input.getText().toString()).apply();
                        fetchWeatherData();
                    }).show();
        });

        addDrawerMenuItem(container, "📱 Web Setup (iPhone)", () -> launchiPhoneWebSetupDialog());
        addDrawerMenuItem(container, "ℹ️ Info", () -> new AlertDialog.Builder(this)
                .setTitle("Weather Info")
                .setMessage("Weather status and wind are powered by Open-Meteo API. Temperature & Humidity can be linked to your local Shelly Cloud API.")
                .setPositiveButton("OK", null).show());

        
        sideDrawerContentScrollView.addView(container);
    }

    private void weatherSectionVisibility(boolean visible) {
        weatherIconView.setVisibility(visible ? View.VISIBLE : View.GONE);
        weatherStatusTextView.setVisibility(visible ? View.VISIBLE : View.GONE);
        weatherWindTextView.setVisibility(visible ? View.VISIBLE : View.GONE);
        weatherTempTextView.setVisibility(visible ? View.VISIBLE : View.GONE);
        weatherRhTextView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // --- iPhone Web Setup (Embedded Java Server + TV Canvas QR Code) ---
    private void launchiPhoneWebSetupDialog() {
        startEmbeddedWebServer();

        String ipAddress = getWifiIpAddress();
        String webUrl = "http://" + ipAddress + ":8080";

        LinearLayout dialogView = new LinearLayout(this);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        dialogView.setGravity(Gravity.CENTER);
        dialogView.setPadding(30, 30, 30, 30);

        TextView infoText = new TextView(this);
        infoText.setText("1. Connect iPhone to same Wi-Fi\n2. Open Safari and go to:\n\n" + webUrl + "\n");
        infoText.setTextColor(Color.WHITE);
        infoText.setTextSize(16);

        ImageView qrImageView = new ImageView(this);
        qrImageView.setLayoutParams(new LinearLayout.LayoutParams(250, 250));
        qrImageView.setImageBitmap(generateSimpleQrBitmap(webUrl));

        dialogView.addView(infoText);
        dialogView.addView(qrImageView);

        new AlertDialog.Builder(this)
                .setTitle("📱 iPhone Web Setup")
                .setView(dialogView)
                .setPositiveButton("Close", (d, w) -> stopEmbeddedWebServer())
                .show();
    }

    private String getWifiIpAddress() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            return Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        } catch (Exception e) {
            return "192.168.1.100";
        }
    }

    private void startEmbeddedWebServer() {
        if (isWebServerRunning) return;
        isWebServerRunning = true;

        new Thread(() -> {
            try {
                webSetupServerSocket = new ServerSocket(8080);
                while (isWebServerRunning) {
                    Socket socket = webSetupServerSocket.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    OutputStream out = socket.getOutputStream();

                    String line = in.readLine();
                    if (line != null && line.contains("POST")) {
                        StringBuilder body = new StringBuilder();
                        while (in.ready()) body.append((char) in.read());

                        String data = body.toString();
                        if (data.contains("shelly=")) {
                            String shellyUrl = extractFormParam(data, "shelly");
                            prefs.edit().putString("ShellyApiUrl", shellyUrl).apply();
                            weatherHandler.post(this::fetchWeatherData);
                        }
                    }

                    String html = "<html><body style='font-family:sans-serif;padding:20px;'>"
                            + "<h2>Tiny Launcher TV Setup</h2>"
                            + "<form method='POST'>"
                            + "Shelly API URL:<br><input style='width:100%;padding:10px;' name='shelly' placeholder='http://shelly-api...'><br><br>"
                            + "<input style='padding:10px 20px;background:#007AFF;color:#fff;border:none;' type='submit' value='Save to TV'>"
                            + "</form></body></html>";

                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: " + html.length() + "\r\n\r\n" + html).getBytes());
                    out.flush();
                    socket.close();
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void stopEmbeddedWebServer() {
        isWebServerRunning = false;
        try {
            if (webSetupServerSocket != null) webSetupServerSocket.close();
        } catch (Exception ignored) {}
    }

    private String extractFormParam(String body, String paramName) {
        try {
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=");
                if (kv.length == 2 && kv[0].equals(paramName)) {
                    return java.net.URLDecoder.decode(kv[1], "UTF-8");
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private Bitmap generateSimpleQrBitmap(String text) {
        Bitmap bmp = Bitmap.createBitmap(250, 250, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);

        // Simple placeholder QR grid visual on Canvas
        int margin = 20;
        int size = 210;
        canvas.drawRect(margin, margin, margin + 60, margin + 60, paint);
        canvas.drawRect(margin + size - 60, margin, margin + size, margin + 60, paint);
        canvas.drawRect(margin, margin + size - 60, margin + 60, margin + size, paint);

        return bmp;
    }

    // --- Weather API Engine (Open-Meteo + Shelly API) ---
    private void startWeatherEngine() {
        weatherRunnable = new Runnable() {
            @Override
            public void run() {
                fetchWeatherData();
                weatherHandler.postDelayed(this, 600000L); // 10 min refresh
            }
        };
        weatherHandler.post(weatherRunnable);
    }

    private void searchCityCoordinates(String cityName) {
        new Thread(() -> {
            try {
                URL url = new URL("https://geocoding-api.open-meteo.com/v1/search?name=" + cityName + "&count=1");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) json.append(line);

                JSONObject res = new JSONObject(json.toString()).getJSONArray("results").getJSONObject(0);
                double lat = res.getDouble("latitude");
                double lon = res.getDouble("longitude");

                prefs.edit().putFloat("Lat", (float) lat).putFloat("Lon", (float) lon).apply();
                runOnUiThread(this::fetchWeatherData);
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this).setMessage("City not found!").show());
            }
        }).start();
    }

    private void fetchWeatherData() {
        new Thread(() -> {
            try {
                float lat = prefs.getFloat("Lat", 52.52f);
                float lon = prefs.getFloat("Lon", 13.41f);

                URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current_weather=true");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) json.append(line);

                JSONObject cw = new JSONObject(json.toString()).getJSONObject("current_weather");
                double wind = cw.getDouble("windspeed");
                int code = cw.getInt("weathercode");

                String statusStr = code == 0 ? "Clear" : code < 3 ? "Partly Cloudy" : "Cloudy/Rain";

                // Shelly API Fetch (if configured)
                String shellyUrlStr = prefs.getString("ShellyApiUrl", "");
                String tempStr = "21° Temp";
                String rhStr = "51 RH";

                if (!shellyUrlStr.isEmpty()) {
                    try {
                        HttpURLConnection sConn = (HttpURLConnection) new URL(shellyUrlStr).openConnection();
                        BufferedReader sReader = new BufferedReader(new InputStreamReader(sConn.getInputStream()));
                        StringBuilder sJson = new StringBuilder();
                        while ((line = sReader.readLine()) != null) sJson.append(line);
                        JSONObject sObj = new JSONObject(sJson.toString());
                        if (sObj.has("tmp")) tempStr = sObj.getInt("tmp") + "° Temp";
                        if (sObj.has("hum")) rhStr = sObj.getInt("hum") + " RH";
                    } catch (Exception ignored) {}
                }

                String finalTemp = tempStr;
                String finalRh = rhStr;

                runOnUiThread(() -> {
                    weatherStatusTextView.setText(statusStr);
                    weatherWindTextView.setText("Wind: " + (int) wind + " m/s");
                    weatherTempTextView.setText(finalTemp);
                    weatherRhTextView.setText(finalRh);
                });
            } catch (Exception ignored) {}
        }).start();
    }

    // --- Remote Color Button Shortcuts (Red, Blue, Green, Yellow) ---
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
    resetIdleTimer();
    if (event.getAction() == KeyEvent.ACTION_DOWN && isSideDrawerOpen) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            View f = getCurrentFocus();
            if (f != null && isViewInsideContainer(sideDrawerContainer, f)) {
                f.dispatchKeyEvent(event);
                return true;
            }
        }
    }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_PROG_RED -> { launchShortcut("RedShortcut"); return true; }
                case KeyEvent.KEYCODE_PROG_BLUE -> { launchShortcut("BlueShortcut"); return true; }
                case KeyEvent.KEYCODE_PROG_GREEN -> { launchShortcut("GreenShortcut"); return true; }
                case KeyEvent.KEYCODE_PROG_YELLOW -> { launchShortcut("YellowShortcut"); return true; }
                case KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (isSideDrawerOpen) {
                        View f = getCurrentFocus();
                        if (!isViewInsideContainer(sideDrawerContainer, f)) {
                            return true;
                        }
                    }
                }
                case KeyEvent.KEYCODE_BACK -> {
                    if (isSideDrawerOpen) {
                        if (shortcutPickerKey != null) {
                            shortcutPickerKey = null;
                            openButtonShortcutsSubmenu();
                        } else if (isInSubmenu && drawerBackAction != null) {
                            drawerBackAction.run();
                        } else if (isInSubmenu) {
                            buildMainMenuInDrawer();
                        } else {
                            toggleSideDrawer(false);
                        }
                        return true;
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void launchShortcut(String key) {
        String pkg = prefs.getString(key, null);
        if (pkg != null) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) startActivity(i);
        }
    }

    // --- Dynamic Accent & Wallpapers ---
    private void extractAccentColorFromBitmap(Bitmap bitmap) {
        if (bitmap == null) return;
        new Thread(() -> {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int maxSatPixel = Color.parseColor("#007AFF");
            float maxSat = -1f;

            for (int x = 0; x < width; x += Math.max(1, width / 20)) {
                for (int y = 0; y < height; y += Math.max(1, height / 20)) {
                    int pixel = bitmap.getPixel(x, y);
                    float[] hsv = new float[3];
                    Color.colorToHSV(pixel, hsv);
                    if (hsv[1] > maxSat && hsv[2] > 0.3f && hsv[2] < 0.9f) {
                        maxSat = hsv[1];
                        maxSatPixel = pixel;
                    }
                }
            }

            int finalColor = maxSatPixel;
            runOnUiThread(() -> {
                currentAccentColor = finalColor;
                
        renderAppBanners();
            });
        }).start();
    }

    private void loadWallpapers() {
        wallpaperFiles.clear();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.READ_MEDIA_IMAGES") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        String folderPath = prefs.getString("WallpaperFolder", "/sdcard/Pictures/Wallpapers");
        File dir = new File(folderPath);
        if (!dir.exists() && folderPath.contains("sdcard")) dir = new File(folderPath.replace("/sdcard", "/storage/emulated/0"));
        if (!dir.exists()) dir = new File("/storage/emulated/0/Pictures/wallpapers");
        if (!dir.exists()) dir = new File("/storage/emulated/0/Pictures/Wallpapers");
        if (!dir.exists()) {
            File p = new File("/storage/emulated/0/Pictures");
            if (p.exists() && p.isDirectory()) {
                File[] subs = p.listFiles(File::isDirectory);
                if (subs != null) { for (File s : subs) { if (s.getName().equalsIgnoreCase("wallpapers")) { dir = s; break; } } }
            }
        }
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> {
                String n = name.toLowerCase();
                return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
            });
            if (files != null) Collections.addAll(wallpaperFiles, files);
        }
        if (!wallpaperFiles.isEmpty()) startWallpaperRotation();
    }

                @Override
    protected void onResume() {
        applyTileRowPosition();
        super.onResume();
        if (prefs.getBoolean("ChangeEachRestart", false) && !wallpaperFiles.isEmpty()) {
            int last = prefs.getInt("LastWallpaperIndex", 0);
            currentWallpaperIndex = (last + 10) % wallpaperFiles.size();
            prefs.edit().putInt("LastWallpaperIndex", currentWallpaperIndex).apply();
            File file = wallpaperFiles.get(currentWallpaperIndex);
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null && wallpaperSwitcher != null) {
                wallpaperSwitcher.setImageDrawable(new BitmapDrawable(getResources(), bitmap));
                extractAccentColorFromBitmap(bitmap);
            }
        }
    }

    private void startWallpaperRotation() {
        wallpaperHandler.removeCallbacks(wallpaperRunnable);
        if (prefs.getBoolean("ChangeEachRestart", false) && !wallpaperFiles.isEmpty()) {
            int last = prefs.getInt("LastWallpaperIndex", 0);
            currentWallpaperIndex = (last + 10) % wallpaperFiles.size();
        }
        wallpaperRunnable = new Runnable() {
            @Override public void run() {
                if (wallpaperFiles.isEmpty()) return;
                currentWallpaperIndex = currentWallpaperIndex % wallpaperFiles.size();
                File file = wallpaperFiles.get(currentWallpaperIndex);
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bitmap != null) {
                    wallpaperSwitcher.setImageDrawable(new BitmapDrawable(getResources(), bitmap));
                    extractAccentColorFromBitmap(bitmap);
                }
                prefs.edit().putInt("LastWallpaperIndex", currentWallpaperIndex).apply();
                currentWallpaperIndex = (currentWallpaperIndex + 1) % wallpaperFiles.size();
                long interval = prefs.getLong("SlideshowInterval", 30000L);
                if (interval > 0) wallpaperHandler.postDelayed(this, interval);
            }
        };
        long interval = prefs.getLong("SlideshowInterval", 30000L);
        if (interval > 0) wallpaperHandler.post(wallpaperRunnable);
    }

    private void setupIdleAutoTimer() {
        idleRunnable = () -> horizontalAppScrollView.animate().alpha(0.0f).setDuration(600).start();
        resetIdleTimer();
    }

        private void resetIdleTimer() {
        if (horizontalAppScrollView.getAlpha() < 1.0f) {
            horizontalAppScrollView.animate().alpha(1.0f).setDuration(200).start();
        }
        idleHandler.removeCallbacks(idleRunnable);
        long timeout = prefs.getLong("IdleTimeout", 300000L);
        if (timeout > 0) {
            idleHandler.postDelayed(idleRunnable, timeout);
        }
    }

    private void startLiveClock() {
        clockRunnable = () -> {
            String mode = prefs.getString("ClockMode", "Full");
            if (mode.equals("Off")) {
                clockTextView.setText("");
            } else {
                String pattern = mode.equals("Time Only") ? "HH:mm" : "EEE, d MMM  HH:mm";
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
                clockTextView.setText(sdf.format(new Date()));
            }
            clockHandler.postDelayed(clockRunnable, 1000);
        };
        clockHandler.post(clockRunnable);
    }

    // --- Unlimited Horizontal TV App Banners ---
                    private void glideMoveTile(int fromIdx, int toIdx) {
        if (isAnimatingMove) return;
        if (fromIdx < 0 || fromIdx >= horizontalAppContainer.getChildCount()) return;
        if (toIdx < 0 || toIdx >= horizontalAppContainer.getChildCount()) return;

        isAnimatingMove = true;
        View itemA = horizontalAppContainer.getChildAt(fromIdx);
        View itemB = horizontalAppContainer.getChildAt(toIdx);

        int shiftX = dpToPx(160 + 12);
        int deltaX = (toIdx > fromIdx) ? shiftX : -shiftX;

        itemA.setElevation(0);
        itemB.setElevation(0);

        if (horizontalAppScrollView != null) {
            int[] locA = new int[2];
            itemA.getLocationOnScreen(locA);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            if (deltaX > 0 && locA[0] + dpToPx(160) > screenWidth - dpToPx(120)) {
                horizontalAppScrollView.smoothScrollBy(deltaX, 0);
            } else if (deltaX < 0 && locA[0] < dpToPx(120)) {
                horizontalAppScrollView.smoothScrollBy(deltaX, 0);
            }
        }

        itemB.animate()
            .translationX(-deltaX)
            .setDuration(300)
            .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
            .start();

        itemA.animate()
            .translationX(deltaX)
            .setDuration(300)
            .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction(() -> {
                itemA.setTranslationX(0);
                itemB.setTranslationX(0);

                horizontalAppContainer.removeView(itemA);
                horizontalAppContainer.addView(itemA, toIdx);

                AppModel temp = appList.get(fromIdx);
                appList.set(fromIdx, appList.get(toIdx));
                appList.set(toIdx, temp);

                moveSourcePosition = toIdx;
                isAnimatingMove = false;

                if (itemA instanceof ViewGroup) {
                    View card = ((ViewGroup) itemA).getChildAt(0);
                    if (card != null) card.requestFocus();
                }
            })
            .start();
    }

    private void renderAppBanners() {
        horizontalAppContainer.removeAllViews();
        horizontalAppContainer.setClipChildren(false);
        horizontalAppContainer.setClipToPadding(false);
        if (horizontalAppScrollView != null) {
            horizontalAppScrollView.setClipChildren(false);
            horizontalAppScrollView.setClipToPadding(false);
        }
        for (int i = 0; i < appList.size(); i++) {
            final int position = i; AppModel app = appList.get(i);
            LinearLayout itemContainer = new LinearLayout(this);
            itemContainer.setOrientation(LinearLayout.VERTICAL);
            itemContainer.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-2, -2);
            itemParams.setMargins(dpToPx(6), 0, dpToPx(6), dpToPx(4));
            itemContainer.setLayoutParams(itemParams);

            android.widget.FrameLayout bannerCard = new android.widget.FrameLayout(this);
            bannerCard.setFocusable(true); bannerCard.setFocusableInTouchMode(true);
            int tW = prefs.getInt("TileSize", 160), tH = tW * 9 / 16, tD = prefs.getInt("TileCornerRadius", 30);
            int tR = dpToPx((int) Math.round((tH / 2.0f) * (tD / 90.0f)));
            bannerCard.setLayoutParams(new android.widget.FrameLayout.LayoutParams(dpToPx(tW), dpToPx(tH)));
            GradientDrawable baseShape = new GradientDrawable(); baseShape.setColor(Color.parseColor("#FF1A1A1A"));
            baseShape.setCornerRadius(tR);
            bannerCard.setBackground(baseShape);
            bannerCard.setClipToOutline(true);

            ImageView iconView = new ImageView(this);
            iconView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, -1));
            iconView.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iconView.setImageDrawable(getCustomDrawableForPackage(app.packageName(), app.icon()));
            bannerCard.addView(iconView);

            TextView titleView = new TextView(this);
            titleView.setText(app.name()); titleView.setTextColor(Color.WHITE);
            int txtS = prefs.getInt("TileTextSize", 14), txtP = prefs.getInt("TileTextPosition", 0);
            titleView.setTextSize(txtS); titleView.setTranslationY(dpToPx(txtP)); titleView.setGravity(Gravity.CENTER);
            titleView.setSingleLine(true); titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            titleView.setPadding(0, dpToPx(28), 0, 0);
            titleView.setVisibility(View.INVISIBLE);

            itemContainer.setClipChildren(false);
            itemContainer.setClipToPadding(false);

            bannerCard.setOnFocusChangeListener((v, hasFocus) -> {
                resetIdleTimer();
                titleView.setVisibility(hasFocus ? View.VISIBLE : View.INVISIBLE);
                v.animate().scaleX(hasFocus ? 1.25f : 1.0f).scaleY(hasFocus ? 1.25f : 1.0f).setDuration(150).start();
                float elevation = 0f;
                itemContainer.setElevation(elevation);
                v.setElevation(elevation);
            });

            bannerCard.setOnClickListener(v -> {
                if (isMoveMode) {
                    if (moveSourcePosition != -1 && moveSourcePosition != position) {
                        AppModel sourceApp = appList.remove(moveSourcePosition);
                        appList.add(position, sourceApp);
                        saveCustomAppOrder();
                        
        renderAppBanners();
                        android.widget.Toast.makeText(this, "App position updated", android.widget.Toast.LENGTH_SHORT).show();
                    }
                    isMoveMode = false;
                    moveSourcePosition = -1;
                    return;
                }
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(app.packageName());
                if (launchIntent != null) startActivity(launchIntent);
            });
                        bannerCard.setOnKeyListener((v, keyCode, event) -> {
                if (isMoveMode && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (!isAnimatingMove && moveSourcePosition < appList.size() - 1) {
                            glideMoveTile(moveSourcePosition, moveSourcePosition + 1);
                        }
                        return true;
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (!isAnimatingMove && moveSourcePosition > 0) {
                            glideMoveTile(moveSourcePosition, moveSourcePosition - 1);
                        }
                        return true;
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER || keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                        if (!isAnimatingMove) {
                            isMoveMode = false;
                            saveCustomAppOrder();
                            moveSourcePosition = -1;
                            android.widget.Toast.makeText(this, "Tile position saved", android.widget.Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    }
                }
                return false;
            });

            bannerCard.setOnLongClickListener(v -> {
                if (prefs.getBoolean("ParentalControlEnabled", false)) {
                    verifyParentalPasscode(() -> showAppOptionDialog(position, v));
                } else {
                    showAppOptionDialog(position, v);
                }
                return true;
            });

            itemContainer.addView(bannerCard); itemContainer.addView(titleView);
            horizontalAppContainer.addView(itemContainer);
        }
    }

        private void addPopupMenuItem(LinearLayout container, String iconSymbol, String labelText, Runnable onClick, android.widget.PopupWindow popup) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);

        // Column 1: Left-aligned symbol in a fixed 22dp column
        TextView iconView = new TextView(this);
        iconView.setText(iconSymbol);
        iconView.setTextColor(Color.parseColor("#5A5E6B"));
        iconView.setTextSize(13);
        iconView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(22), ViewGroup.LayoutParams.WRAP_CONTENT));

        // Column 2: Left-aligned item name starting at a fixed offset
        TextView labelView = new TextView(this);
        labelView.setText(labelText);
        labelView.setTextColor(Color.WHITE);
        labelView.setTextSize(13);
        labelView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        labelView.setSingleLine(true);
        labelView.setEllipsize(android.text.TextUtils.TruncateAt.END);

        row.addView(iconView);
        row.addView(labelView);

        row.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable focusShape = new GradientDrawable();
            focusShape.setCornerRadius(dpToPx(8));
            focusShape.setColor(hasFocus ? Color.parseColor("#333842") : Color.TRANSPARENT);
            v.setBackground(focusShape);
        });

        row.setOnClickListener(v -> {
            popup.dismiss();
            onClick.run();
        });

        container.addView(row);
    }

            private void showAppOptionDialog(int position, View anchorView) {
        AppModel app = appList.get(position);

        LinearLayout menuView = new LinearLayout(this);
        menuView.setOrientation(LinearLayout.VERTICAL);
        menuView.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1A1D24"));
        bg.setCornerRadius(dpToPx(12));

        menuView.setBackground(bg);

        android.widget.PopupWindow popup = new android.widget.PopupWindow(
                menuView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(0);
        popup.setOutsideTouchable(true);

        addPopupMenuItem(menuView, "Ø", "Hide App", () -> {
            Set<String> hidden = new HashSet<>(prefs.getStringSet("HiddenApps", new HashSet<>()));
            hidden.add(app.packageName());
            prefs.edit().putStringSet("HiddenApps", hidden).apply();
            loadInstalledApps();
        }, popup);

        addPopupMenuItem(menuView, "i", "App Info", () -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + app.packageName()));
            startActivity(intent);
        }, popup);

        addPopupMenuItem(menuView, "×", "Uninstall App", () -> {
            Intent unIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + app.packageName()));
            unIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            startActivity(unIntent);
        }, popup);

        addPopupMenuItem(menuView, "⧉", "Replace Banner", () -> {
            openManageAppsSubmenu();
            toggleSideDrawer(true);
        }, popup);

        addPopupMenuItem(menuView, "↔", "Move App", () -> {
            isMoveMode = true;
            moveSourcePosition = position;
            android.widget.Toast.makeText(this, "Move Mode: Use D-pad arrows to glide tile", android.widget.Toast.LENGTH_LONG).show();
        }, popup);

        menuView.measure(
            View.MeasureSpec.makeMeasureSpec(dpToPx(180), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int popupWidth = menuView.getMeasuredWidth();
        int popupHeight = menuView.getMeasuredHeight();

        int[] location = new int[2];
        anchorView.getLocationOnScreen(location);

        float scaledWidth = anchorView.getWidth() * anchorView.getScaleX();
        float visualCenterX = location[0] + (scaledWidth / 2f);
        float visualTopY = location[1];

        int popupX = Math.round(visualCenterX - (popupWidth / 2f));
        int popupY = Math.round(visualTopY - popupHeight - dpToPx(8));

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        if (popupX < dpToPx(12)) {
            popupX = dpToPx(12);
        } else if (popupX + popupWidth > screenWidth - dpToPx(12)) {
            popupX = screenWidth - dpToPx(12) - popupWidth;
        }

        popup.showAtLocation(anchorView, Gravity.NO_GRAVITY, popupX, popupY);
    }

        private void saveCustomAppOrder() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < appList.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(appList.get(i).packageName());
        }
        prefs.edit().putString("CustomAppOrder", sb.toString()).apply();
    }

    private void loadInstalledApps() {
        appList.clear();
        PackageManager pm = getPackageManager();
        Set<String> hidden = prefs.getStringSet("HiddenApps", new HashSet<>());

        Map<String, AppModel> discoveredApps = new LinkedHashMap<>();

        Intent tvIntent = new Intent(Intent.ACTION_MAIN, null);
        tvIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> tvActivities = pm.queryIntentActivities(tvIntent, 0);

        for (ResolveInfo ri : tvActivities) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hidden.contains(pkg)) continue;
            String name = ri.loadLabel(pm).toString();
            Drawable icon = ri.loadIcon(pm);
            discoveredApps.put(pkg, new AppModel(name, icon, pkg, true));
        }

        Intent standardIntent = new Intent(Intent.ACTION_MAIN, null);
        standardIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> standardActivities = pm.queryIntentActivities(standardIntent, 0);

        for (ResolveInfo ri : standardActivities) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hidden.contains(pkg) || discoveredApps.containsKey(pkg)) continue;
            String name = ri.loadLabel(pm).toString();
            Drawable icon = ri.loadIcon(pm);
            discoveredApps.put(pkg, new AppModel(name, icon, pkg, false));
        }

        appList.addAll(discoveredApps.values());
        
        renderAppBanners();
    }

    private void registerPackageReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(packageReceiver, filter);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopEmbeddedWebServer();
        clockHandler.removeCallbacks(clockRunnable);
        idleHandler.removeCallbacks(idleRunnable);
        wallpaperHandler.removeCallbacks(wallpaperRunnable);
        weatherHandler.removeCallbacks(weatherRunnable);
        try { unregisterReceiver(packageReceiver); } catch (Exception ignored) {}
    }

    private android.graphics.drawable.Drawable getCustomDrawableForPackage(String pkg, android.graphics.drawable.Drawable fallback) {
        if (pkg == null) return fallback;
        String resName = null;
        String p = pkg.toLowerCase();

        if (p.contains("youtube.tv") || p.contains("smarttube")) resName = "ic_youtube_tv";
        else if (p.contains("youtube.kids")) resName = "ic_youtube_kids";
        else if (p.contains("youtube")) resName = "ic_youtube";
        else if (p.contains("kodi")) resName = "ic_kodi";
        else if (p.contains("stremio")) resName = "ic_stremio";
        else if (p.contains("spotify")) resName = "ic_spotify";
        else if (p.contains("tizentube")) resName = "ic_tizentube";
        else if (p.contains("tivitime") || p.contains("tivi")) resName = "ic_tivitime";
        else if (p.contains("streamflix")) resName = "ic_streamflix";
        else if (p.contains("nova")) resName = "ic_nova_tv";
        else if (p.contains("nuvio")) resName = "ic_nuvio";
        else if (p.contains("weyd")) resName = "ic_weyd";
        else if (p.contains("aptoide")) resName = "ic_aptoide_tv";
        else if (p.contains("aurora")) resName = "ic_aurora_store";
        else if (p.contains("play.store") || p.contains("vending")) resName = "ic_google_play_store";
        else if (p.contains("cxinventor") || p.contains("cxfile")) resName = "ic_cx_file_explorer";
        else if (p.contains("solidexplorer")) resName = "ic_solid_explorer";
        else if (p.contains("estrongs") || p.contains("esfile")) resName = "ic_es_file_explorer";
        else if (p.contains("xplore") || p.contains("lonelycat")) resName = "ic_xplore";
        else if (p.contains("downloader")) resName = "ic_downloader";
        else if (p.contains("buttonmapper")) resName = "ic_button_mapper";
        else if (p.contains("projectivy")) resName = "ic_projectivy_launcher";
        else if (p.contains("settings")) resName = "ic_settings_icon";
        else if (p.contains("photos") || p.contains("gallery")) resName = "ic_google_photos";
        else if (p.contains("synology")) resName = "ic_synology_photos";
        else if (p.contains("immich")) resName = "ic_immich";
        else if (p.contains("mxtech.videoplayer.pro")) resName = "ic_mx_player_pro";
        else if (p.contains("mxtech.videoplayer")) resName = "ic_mx_player_tv";
        else if (p.contains("nplayer")) resName = "ic_n_player";
        else if (p.contains("nvplayer")) resName = "ic_nv_player";

        if (resName != null) {
            int resId = getResources().getIdentifier(resName, "drawable", getPackageName());
            if (resId != 0) {
                try {
                    return getDrawable(resId);
                } catch (Exception e) {
                    // Fallback to default
                }
            }
        }
        return fallback;
    }


    private void loadCustomWallpaper() {
        java.io.File file = getFileStreamPath("custom_wallpaper.jpg");
        if (file.exists() && wallpaperSwitcher != null) {
            android.graphics.drawable.Drawable d = android.graphics.drawable.Drawable.createFromPath(file.getAbsolutePath());
            if (d != null) wallpaperSwitcher.setImageDrawable(d);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1002 && resultCode == RESULT_OK && data != null && data.getData() != null) { android.net.Uri uri = data.getData(); try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {} String p = uri.getPath(); if (p != null && p.contains(":")) { String[] parts = p.split(":"); if (parts.length > 1) p = "/sdcard/" + parts[1]; } prefs.edit().putString("WallpaperFolder", p != null ? p : uri.toString()).apply(); loadWallpapers(); startWallpaperRotation(); }
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (java.io.InputStream in = getContentResolver().openInputStream(data.getData());
                 java.io.OutputStream out = openFileOutput("custom_wallpaper.jpg", MODE_PRIVATE)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                loadCustomWallpaper();
            } catch (Exception ignored) {}
        }
    }

    private boolean isViewInsideContainer(View container, View view) {
        if (view == null || container == null) return false;
        android.view.ViewParent p = view.getParent();
        while (p != null) {
            if (p == container) return true;
            p = p.getParent();
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2001 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (isSideDrawerOpen && isInSubmenu) openSlideshowFolderSubmenu();
            else loadWallpapers();
        }
    }
}
