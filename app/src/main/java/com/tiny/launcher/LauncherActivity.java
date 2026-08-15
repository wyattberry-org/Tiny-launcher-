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
    private TextView clockTextView, weatherStatusTextView, weatherTempTextView, weatherRhTextView, weatherWindTextView;
    private ImageView weatherIconView, settingsGear;
    private LinearLayout topWidgetRow, sideDrawerContainer;
    private ScrollView sideDrawerContentScrollView;

    // --- App & Data Models ---
    private final List<AppModel> appList = new ArrayList<>();
    private SharedPreferences prefs;

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
    private ServerSocket webSetupServerSocket;
    private boolean isWebServerRunning = false;

    // --- Slideshow Interval Options (in Milliseconds) ---
    private final long[] SLIDESHOW_INTERVALS = {
            20000L, 30000L, 60000L, 180000L, 300000L, 600000L, 900000L, 1200000L, 1800000L
    };
    private final String[] SLIDESHOW_LABELS = {
            "20 sec", "30 sec", "1 min", "3 min", "5 min", "10 min", "15 min", "20 min", "30 min"
    };

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
        rootOverlayFrame.setBackgroundColor(Color.BLACK);

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
        mainOverlayLayout.setPadding(50, 30, 50, 30);
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
        settingsGear.setOnClickListener(v -> toggleSideDrawer(true));
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
        horizontalAppScrollView.setPadding(0, 20, 0, 20);

        horizontalAppContainer = new LinearLayout(this);
        horizontalAppContainer.setOrientation(LinearLayout.HORIZONTAL);
        horizontalAppContainer.setGravity(Gravity.BOTTOM);
        horizontalAppScrollView.addView(horizontalAppContainer);

        mainOverlayLayout.addView(horizontalAppScrollView);
        rootOverlayFrame.addView(mainOverlayLayout);

        // --- 6. Right Side Drawer Menu Container (`#1A1D24`) ---
        sideDrawerContainer = new LinearLayout(this);
        sideDrawerContainer.setOrientation(LinearLayout.VERTICAL);
        sideDrawerContainer.setBackgroundColor(Color.parseColor("#1A1D24"));
        sideDrawerContainer.setPadding(30, 40, 30, 40);

        FrameLayout.LayoutParams drawerLayoutParams = new FrameLayout.LayoutParams(
                dpToPx(340), ViewGroup.LayoutParams.MATCH_PARENT);
        drawerLayoutParams.gravity = Gravity.END;
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
        if (open) {
            buildMainMenuInDrawer();
            sideDrawerContainer.setVisibility(View.VISIBLE);
            sideDrawerContainer.animate().translationX(0f).setDuration(250).start();
        } else {
            sideDrawerContainer.animate().translationX(dpToPx(360)).setDuration(200)
                    .withEndAction(() -> sideDrawerContainer.setVisibility(View.GONE)).start();
        }
    }

    private void buildMainMenuInDrawer() {
        sideDrawerContentScrollView.removeAllViews();

        LinearLayout drawerContent = new LinearLayout(this);
        drawerContent.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText("Tiny Launcher");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(20);
        titleView.setPadding(10, 0, 0, 20);
        drawerContent.addView(titleView);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
        drawerContent.addView(divider);

        addDrawerMenuItem(drawerContent, "📱 Manage apps", () -> openManageAppsSubmenu());
        addDrawerMenuItem(drawerContent, "⌨️ Button shortcuts", () -> openButtonShortcutsSubmenu());
        addDrawerMenuItem(drawerContent, "🔒 Parental Control", () -> openParentalControlSubmenu());
        addDrawerMenuItem(drawerContent, "🖼️ Wallpaper / Slideshow", () -> openWallpaperSubmenu());
        addDrawerMenuItem(drawerContent, "⏰ Show clock", () -> openClockSubmenu());
        addDrawerMenuItem(drawerContent, "☁️ Weather", () -> openWeatherSubmenu());
        addDrawerMenuItem(drawerContent, "⚙️ System Settings", () -> startActivity(new Intent(Settings.ACTION_SETTINGS)));

        sideDrawerContentScrollView.addView(drawerContent);
    }

    private void addDrawerMenuItem(LinearLayout container, String title, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(25, 25, 25, 25);
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);

        row.addView(label);

        row.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                GradientDrawable shape = new GradientDrawable();
                shape.setColor(currentAccentColor);
                shape.setCornerRadius(12f);
                v.setBackground(shape);
            } else {
                v.setBackgroundColor(Color.TRANSPARENT);
            }
        });

        row.setOnClickListener(v -> onClick.run());
        container.addView(row);
    }

    // --- Submenus (Swapped Inside Drawer Without Window Redrawing) ---
    private void openManageAppsSubmenu() {
        sideDrawerContentScrollView.removeAllViews();

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Manage apps");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setPadding(10, 0, 0, 20);
        container.addView(title);

        Set<String> hidden = prefs.getStringSet("HiddenApps", new HashSet<>());

        if (hidden.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No hidden apps.");
            emptyText.setTextColor(Color.GRAY);
            emptyText.setPadding(20, 20, 20, 20);
            container.addView(emptyText);
        } else {
            PackageManager pm = getPackageManager();
            for (String pkg : hidden) {
                try {
                    String appName = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                    addDrawerMenuItem(container, appName, () -> {
                        new AlertDialog.Builder(this)
                                .setTitle(appName)
                                .setPositiveButton("Open", (d, w) -> {
                                    Intent i = pm.getLaunchIntentForPackage(pkg);
                                    if (i != null) startActivity(i);
                                })
                                .setNegativeButton("Unhide", (d, w) -> {
                                    Set<String> updated = new HashSet<>(hidden);
                                    updated.remove(pkg);
                                    prefs.edit().putStringSet("HiddenApps", updated).apply();
                                    loadInstalledApps();
                                    openManageAppsSubmenu();
                                }).show();
                    });
                } catch (Exception ignored) {}
            }
        }

        addDrawerMenuItem(container, "⬅️ Back", () -> buildMainMenuInDrawer());
        sideDrawerContentScrollView.addView(container);
    }

    private void openButtonShortcutsSubmenu() {
        sideDrawerContentScrollView.removeAllViews();

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Button Shortcuts");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setPadding(10, 0, 0, 20);
        container.addView(title);

        addDrawerMenuItem(container, "🔴 Red Button: " + getShortcutName("RedShortcut"), () -> pickAppForShortcut("RedShortcut"));
        addDrawerMenuItem(container, "🔵 Blue Button: " + getShortcutName("BlueShortcut"), () -> pickAppForShortcut("BlueShortcut"));
        addDrawerMenuItem(container, "🟢 Green Button: " + getShortcutName("GreenShortcut"), () -> pickAppForShortcut("GreenShortcut"));
        addDrawerMenuItem(container, "🟡 Yellow Button: " + getShortcutName("YellowShortcut"), () -> pickAppForShortcut("YellowShortcut"));
        addDrawerMenuItem(container, "⬅️ Back", () -> buildMainMenuInDrawer());

        sideDrawerContentScrollView.addView(container);
    }

    private String getShortcutName(String key) {
        String pkg = prefs.getString(key, null);
        if (pkg == null) return "Not set";
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return "Not set";
        }
    }

    private void pickAppForShortcut(String key) {
        String[] appNames = new String[appList.size()];
        for (int i = 0; i < appList.size(); i++) appNames[i] = appList.get(i).name();

        new AlertDialog.Builder(this)
                .setTitle("Select App for Shortcut")
                .setItems(appNames, (dialog, which) -> {
                    prefs.edit().putString(key, appList.get(which).packageName()).apply();
                    openButtonShortcutsSubmenu();
                }).show();
    }

    private void openParentalControlSubmenu() {
        sideDrawerContentScrollView.removeAllViews();

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Parental Control");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setPadding(10, 0, 0, 20);
        container.addView(title);

        boolean enabled = prefs.getBoolean("ParentalControlEnabled", false);
        addDrawerMenuItem(container, "🔒 Parental Control: " + (enabled ? "ON" : "OFF"), () -> {
            prefs.edit().putBoolean("ParentalControlEnabled", !enabled).apply();
            openParentalControlSubmenu();
        });

        addDrawerMenuItem(container, "🔑 Set Code", () -> {
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            new AlertDialog.Builder(this).setTitle("New 4-Digit PIN").setView(input)
                    .setPositiveButton("Save", (d, w) -> prefs.edit().putString("ParentalPin", input.getText().toString()).apply()).show();
        });

        addDrawerMenuItem(container, "⬅️ Back", () -> buildMainMenuInDrawer());
        sideDrawerContentScrollView.addView(container);
    }

    private void openWallpaperSubmenu() {
        sideDrawerContentScrollView.removeAllViews();

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Wallpaper & Slideshow");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setPadding(10, 0, 0, 20);
        container.addView(title);

        long currentInterval = prefs.getLong("SlideshowInterval", 30000L);
        int currentIndex = 1;
        for (int i = 0; i < SLIDESHOW_INTERVALS.length; i++) {
            if (SLIDESHOW_INTERVALS[i] == currentInterval) { currentIndex = i; break; }
        }
        final int index = currentIndex;

        addDrawerMenuItem(container, "⏱️ Slideshow Duration: < " + SLIDESHOW_LABELS[index] + " >", () -> {
            long nextInterval = SLIDESHOW_INTERVALS[(index + 1) % SLIDESHOW_INTERVALS.length];
            prefs.edit().putLong("SlideshowInterval", nextInterval).apply();
            startWallpaperRotation();
            openWallpaperSubmenu();
        });

        addDrawerMenuItem(container, "📂 Set Wallpaper Folder", () -> {
            final EditText input = new EditText(this);
            input.setText(prefs.getString("WallpaperFolder", "/sdcard/Pictures/Wallpapers"));
            new AlertDialog.Builder(this).setTitle("Wallpaper Path").setView(input)
                    .setPositiveButton("Save", (d, w) -> {
                        prefs.edit().putString("WallpaperFolder", input.getText().toString()).apply();
                        loadWallpapers();
                    }).show();
        });

        addDrawerMenuItem(container, "⬅️ Back", () -> buildMainMenuInDrawer());
        sideDrawerContentScrollView.addView(container);
    }

    private void openClockSubmenu() {
        sideDrawerContentScrollView.removeAllViews();

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Clock Settings");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setPadding(10, 0, 0, 20);
        container.addView(title);

        String mode = prefs.getString("ClockMode", "Full");
        addDrawerMenuItem(container, "Show Clock: " + mode, () -> {
            String nextMode = mode.equals("Full") ? "Time Only" : mode.equals("Time Only") ? "Off" : "Full";
            prefs.edit().putString("ClockMode", nextMode).apply();
            openClockSubmenu();
        });

        addDrawerMenuItem(container, "⬅️ Back", () -> buildMainMenuInDrawer());
        sideDrawerContentScrollView.addView(container);
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
        addDrawerMenuItem(container, "☁️ Weather Widget: " + (enabled ? "On" : "Off"), () -> {
            prefs.edit().putBoolean("WeatherEnabled", !enabled).apply();
            weatherSectionVisibility(enabled);
            openWeatherSubmenu();
        });

        addDrawerMenuItem(container, "📍 Location (Open-Meteo)", () -> {
            final EditText input = new EditText(this);
            input.setHint("Type City Name...");
            new AlertDialog.Builder(this).setTitle("City Search").setView(input)
                    .setPositiveButton("Search", (d, w) -> searchCityCoordinates(input.getText().toString())).show();
        });

        addDrawerMenuItem(container, "⚡ Shelly API URL", () -> {
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

        addDrawerMenuItem(container, "⬅️ Back", () -> buildMainMenuInDrawer());
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
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_PROG_RED -> { launchShortcut("RedShortcut"); return true; }
                case KeyEvent.KEYCODE_PROG_BLUE -> { launchShortcut("BlueShortcut"); return true; }
                case KeyEvent.KEYCODE_PROG_GREEN -> { launchShortcut("GreenShortcut"); return true; }
                case KeyEvent.KEYCODE_PROG_YELLOW -> { launchShortcut("YellowShortcut"); return true; }
                case KeyEvent.KEYCODE_BACK -> {
                    if (isSideDrawerOpen) { toggleSideDrawer(false); return true; }
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
        String folderPath = prefs.getString("WallpaperFolder", "/sdcard/Pictures/Wallpapers");
        File dir = new File(folderPath);

        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png"));
            if (files != null) Collections.addAll(wallpaperFiles, files);
        }

        if (!wallpaperFiles.isEmpty()) startWallpaperRotation();
    }

    private void startWallpaperRotation() {
        wallpaperRunnable = new Runnable() {
            @Override
            public void run() {
                if (wallpaperFiles.isEmpty()) return;
                File file = wallpaperFiles.get(currentWallpaperIndex);

                DisplayMetrics metrics = getResources().getDisplayMetrics();
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());

                if (bitmap != null) {
                    wallpaperSwitcher.setImageDrawable(new BitmapDrawable(getResources(), bitmap));
                    extractAccentColorFromBitmap(bitmap);
                }

                currentWallpaperIndex = (currentWallpaperIndex + 1) % wallpaperFiles.size();
                long interval = prefs.getLong("SlideshowInterval", 30000L);
                wallpaperHandler.postDelayed(this, interval);
            }
        };
        wallpaperHandler.post(wallpaperRunnable);
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
        idleHandler.postDelayed(idleRunnable, 300000L); // 5 min idle auto-hide
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
            private void renderAppBanners() {
        horizontalAppContainer.removeAllViews();
        for (int i = 0; i < appList.size(); i++) {
            final int position = i; AppModel app = appList.get(i);
            LinearLayout itemContainer = new LinearLayout(this);
            itemContainer.setOrientation(LinearLayout.VERTICAL);
            itemContainer.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-2, -2);
            itemParams.setMargins(dpToPx(6), 0, dpToPx(6), dpToPx(8));
            itemContainer.setLayoutParams(itemParams);

            android.widget.FrameLayout bannerCard = new android.widget.FrameLayout(this);
            bannerCard.setFocusable(true); bannerCard.setFocusableInTouchMode(true);
            bannerCard.setLayoutParams(new android.widget.FrameLayout.LayoutParams(dpToPx(140), dpToPx(79)));
            GradientDrawable baseShape = new GradientDrawable();
            baseShape.setColor(Color.parseColor("#CC1A1A1A"));
            baseShape.setCornerRadius(dpToPx(8));
            bannerCard.setBackground(baseShape);

            ImageView iconView = new ImageView(this);
            iconView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, -1));
            iconView.setScaleType(ImageView.ScaleType.FIT_XY);
            iconView.setImageDrawable(getCustomDrawableForPackage(app.packageName(), app.icon()));
            bannerCard.addView(iconView);

            TextView titleView = new TextView(this);
            titleView.setText(app.name()); titleView.setTextColor(Color.YELLOW);
            titleView.setTextSize(22); titleView.setTypeface(null, android.graphics.Typeface.BOLD);
            titleView.setGravity(Gravity.CENTER); titleView.setSingleLine(true);
            titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            titleView.setPadding(0, dpToPx(12), 0, 0);
            titleView.setVisibility(View.INVISIBLE);

            bannerCard.setOnFocusChangeListener((v, hasFocus) -> {
                resetIdleTimer();
                GradientDrawable shape = new GradientDrawable();
                shape.setCornerRadius(dpToPx(8));
                shape.setColor(hasFocus ? currentAccentColor : Color.parseColor("#CC1A1A1A"));
                titleView.setVisibility(hasFocus ? View.VISIBLE : View.INVISIBLE);
                v.animate().scaleX(hasFocus ? 1.08f : 1.0f).scaleY(hasFocus ? 1.08f : 1.0f).setDuration(150).start();
                v.setBackground(shape);
            });

            bannerCard.setOnClickListener(v -> {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(app.packageName());
                if (launchIntent != null) startActivity(launchIntent);
            });
            bannerCard.setOnLongClickListener(v -> { showAppOptionDialog(position); return true; });

            itemContainer.addView(bannerCard); itemContainer.addView(titleView);
            horizontalAppContainer.addView(itemContainer);
        }
    }

    private void showAppOptionDialog(int position) {
        AppModel app = appList.get(position);
        String[] options = {"🙈 Hide App", "🗑️ Uninstall App", "↔️ Move App"};

        new AlertDialog.Builder(this)
                .setTitle(app.name())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Set<String> hidden = new HashSet<>(prefs.getStringSet("HiddenApps", new HashSet<>()));
                        hidden.add(app.packageName());
                        prefs.edit().putStringSet("HiddenApps", hidden).apply();
                        loadInstalledApps();
                    } else if (which == 1) {
                        startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName())));
                    } else if (which == 2) {
                        new AlertDialog.Builder(this).setMessage("Click another app card to swap positions!").show();
                    }
                }).show();
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

}
