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
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import androidx.palette.graphics.Palette;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LauncherActivity extends Activity {

    // --- UI Controls ---
    private ImageSwitcher wallpaperSwitcher;
    private GridView gridView;
    private TextView clockTextView, weatherTextView;
    private ImageView settingsGear;
    private LinearLayout topWidgetRow, rootLayout;

    // --- App & Data Models ---
    private final List<AppModel> appList = new ArrayList<>();
    private AppAdapter adapter;
    private SharedPreferences prefs;

    // --- Dynamic Theming ---
    private int currentAccentColor = Color.parseColor("#007AFF"); // Default TV Blue

    // --- Timers & Handlers ---
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private final Handler wallpaperHandler = new Handler(Looper.getMainLooper());
    private Runnable clockRunnable, idleRunnable, wallpaperRunnable;

    // --- Wallpapers & State ---
    private final List<File> wallpaperFiles = new ArrayList<>();
    private int currentWallpaperIndex = 0;
    private int selectedMovePosition = -1; // For moving app tiles

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

        // --- 1. Root Frame (Layout Stack) ---
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.BLACK);

        // --- 2. Wallpaper ImageSwitcher (With Slide Animation) ---
        wallpaperSwitcher = new ImageSwitcher(this);
        wallpaperSwitcher.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wallpaperSwitcher.setFactory(new ViewSwitcher.ViewFactory() {
            @Override
            public View makeView() {
                ImageView iv = new ImageView(LauncherActivity.this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setLayoutParams(new ImageSwitcher.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                return iv;
            }
        });

        // Set Slide-In Animations
        wallpaperSwitcher.setInAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left));
        wallpaperSwitcher.setOutAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right));

        // Container Layout on top of Wallpaper
        LinearLayout mainOverlay = new LinearLayout(this);
        mainOverlay.setOrientation(LinearLayout.VERTICAL);
        mainOverlay.setPadding(50, 30, 50, 30);

        // --- 3. Single-Row Top Widget (Weather, Clock, Date, Settings Gear) ---
        topWidgetRow = new LinearLayout(this);
        topWidgetRow.setOrientation(LinearLayout.HORIZONTAL);
        topWidgetRow.setGravity(Gravity.CENTER_VERTICAL);
        topWidgetRow.setPadding(0, 0, 0, 30);

        weatherTextView = new TextView(this);
        weatherTextView.setText("☀️ 22°C Clear");
        weatherTextView.setTextSize(20);
        weatherTextView.setTextColor(Color.WHITE);

        clockTextView = new TextView(this);
        clockTextView.setTextSize(24);
        clockTextView.setTextColor(Color.WHITE);
        clockTextView.setPadding(40, 0, 0, 0);

        LinearLayout.LayoutParams flexParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        clockTextView.setLayoutParams(flexParams);

        // Settings Gear Icon
        settingsGear = new ImageView(this);
        settingsGear.setImageResource(android.R.drawable.ic_menu_preferences);
        settingsGear.setFocusable(true);
        settingsGear.setPadding(15, 15, 15, 15);
        settingsGear.setOnClickListener(v -> checkPinAndExecute(this::openSettingsDialog));

        topWidgetRow.addView(weatherTextView);
        topWidgetRow.addView(clockTextView);
        topWidgetRow.addView(settingsGear);
        mainOverlay.addView(topWidgetRow);

        // --- 4. Main App Grid View ---
        gridView = new GridView(this);
        gridView.setNumColumns(4);
        gridView.setHorizontalSpacing(30);
        gridView.setVerticalSpacing(30);
        gridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        mainOverlay.addView(gridView);

        rootLayout.addView(mainOverlay);
        setContentView(rootLayout);

        adapter = new AppAdapter(this, appList);
        gridView.setAdapter(adapter);

        // --- 5. Tile Click & Long-Press Handlers ---
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            resetIdleTimer();
            if (selectedMovePosition != -1) {
                // Move App Mode
                Collections.swap(appList, selectedMovePosition, position);
                saveAppOrder();
                selectedMovePosition = -1;
                adapter.notifyDataSetChanged();
            } else {
                // Launch App
                AppModel app = appList.get(position);
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(app.packageName);
                if (launchIntent != null) startActivity(launchIntent);
            }
        });

        // Long-Press App Tile (Parental PIN Protected)
        gridView.setOnItemLongClickListener((parent, view, position, id) -> {
            resetIdleTimer();
            checkPinAndExecute(() -> showAppContextMenu(position));
            return true;
        });

        loadWallpapers();
        loadInstalledApps();
        registerPackageReceiver();
        startLiveClock();
        setupIdleAutoTimer();
    }

    // --- Dynamic Tile Accent Color Extractor (Palette) ---
    private void extractAccentColorFromBitmap(Bitmap bitmap) {
        if (bitmap == null) return;
        Palette.from(bitmap).generate(palette -> {
            if (palette != null) {
                int defaultColor = Color.parseColor("#007AFF");
                currentAccentColor = palette.getVibrantColor(palette.getDominantColor(defaultColor));
                adapter.notifyDataSetChanged(); // Refresh tile outlines with new dynamic accent color
            }
        });
    }

    // --- Wallpaper Rotation & Folder Engine ---
    private void loadWallpapers() {
        wallpaperFiles.clear();
        String folderPath = prefs.getString("WallpaperFolder", "/sdcard/Pictures/Wallpapers");
        File dir = new File(folderPath);

        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg") || name.endsWith(".png"));
            if (files != null) Collections.addAll(wallpaperFiles, files);
        }

        if (!wallpaperFiles.isEmpty()) {
            startWallpaperRotation();
        }
    }

    private void startWallpaperRotation() {
        wallpaperRunnable = new Runnable() {
            @Override
            public void run() {
                if (wallpaperFiles.isEmpty()) return;
                File file = wallpaperFiles.get(currentWallpaperIndex);
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());

                if (bitmap != null) {
                    wallpaperSwitcher.setImageDrawable(new BitmapDrawable(getResources(), bitmap));
                    extractAccentColorFromBitmap(bitmap); // Extract dynamic accent color for app tiles
                }

                currentWallpaperIndex = (currentWallpaperIndex + 1) % wallpaperFiles.size();
                wallpaperHandler.postDelayed(this, 30000); // Rotate every 30 seconds
            }
        };
        wallpaperHandler.post(wallpaperRunnable);
    }

    // --- Idle Auto-Hide UI Mode ---
    private void setupIdleAutoTimer() {
        idleRunnable = () -> gridView.animate().alpha(0.0f).setDuration(600).start(); // Hide app grid when idle
        resetIdleTimer();
    }

    private void resetIdleTimer() {
        if (gridView.getAlpha() < 1.0f) {
            gridView.animate().alpha(1.0f).setDuration(200).start(); // Show app grid when remote key pressed
        }
        idleHandler.removeCallbacks(idleRunnable);
        idleHandler.postDelayed(idleRunnable, 30000); // 30 seconds idle timeout
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        resetIdleTimer(); // Any remote button press wakes up the UI
        return super.dispatchKeyEvent(event);
    }

    // --- Parental Control PIN Check ---
    private void checkPinAndExecute(Runnable onSuccess) {
        String savedPin = prefs.getString("ParentalPin", "0000"); // Default PIN: 0000

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔒 Parental Control");
        builder.setMessage("Enter 4-Digit PIN:");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            if (input.getText().toString().equals(savedPin)) {
                onSuccess.run();
            } else {
                new AlertDialog.Builder(this).setMessage("❌ Incorrect PIN!").show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    // --- Long-Press Context Menu ---
    private void showAppContextMenu(int position) {
        AppModel app = appList.get(position);
        String[] options = {"↔️ Move App", "⚙️ App Info", "🗑️ Uninstall App", "🙈 Hide App"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Options for " + app.name);
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // Move App
                    selectedMovePosition = position;
                    new AlertDialog.Builder(this).setMessage("Click another tile to swap positions!").show();
                    break;
                case 1: // App Info
                    Intent infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName));
                    startActivity(infoIntent);
                    break;
                case 2: // Uninstall App
                    Intent uninstIntent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName));
                    startActivity(uninstIntent);
                    break;
                case 3: // Hide App
                    hideApp(app.packageName);
                    break;
            }
        });
        builder.show();
    }

    private void hideApp(String packageName) {
        Set<String> hidden = new HashSet<>(prefs.getStringSet("HiddenApps", new HashSet<>()));
        hidden.add(packageName);
        prefs.edit().putStringSet("HiddenApps", hidden).apply();
        loadInstalledApps(); // Refresh grid
    }

    // --- Settings & Unhide Dialog ---
    private void openSettingsDialog() {
        String[] options = {"👁️ Unhide Apps", "🔑 Change Parental PIN", "📂 Set Wallpaper Folder"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) { // Unhide Apps
                Set<String> hidden = prefs.getStringSet("HiddenApps", new HashSet<>());
                if (hidden.isEmpty()) {
                    new AlertDialog.Builder(this).setMessage("No hidden apps!").show();
                } else {
                    String[] hiddenArray = hidden.toArray(new String[0]);
                    AlertDialog.Builder unhideBuilder = new AlertDialog.Builder(this);
                    unhideBuilder.setTitle("Select App to Unhide");
                    unhideBuilder.setItems(hiddenArray, (d, w) -> {
                        Set<String> updated = new HashSet<>(hidden);
                        updated.remove(hiddenArray[w]);
                        prefs.edit().putStringSet("HiddenApps", updated).apply();
                        loadInstalledApps();
                    });
                    unhideBuilder.show();
                }
            } else if (which == 1) { // Change PIN
                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                new AlertDialog.Builder(this).setTitle("New 4-Digit PIN").setView(input)
                        .setPositiveButton("Save", (d, w) -> prefs.edit().putString("ParentalPin", input.getText().toString()).apply()).show();
            }
        });
        builder.show();
    }

    // --- Load TV Apps (Excluding Hidden Ones) ---
    private void loadInstalledApps() {
        appList.clear();
        PackageManager pm = getPackageManager();
        Set<String> hidden = prefs.getStringSet("HiddenApps", new HashSet<>());

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(mainIntent, 0);

        if (activities.isEmpty()) {
            mainIntent.removeCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            activities = pm.queryIntentActivities(mainIntent, 0);
        }

        for (ResolveInfo ri : activities) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hidden.contains(pkg)) continue;

            String name = ri.loadLabel(pm).toString();
            Drawable icon = ri.loadIcon(pm);
            appList.add(new AppModel(name, icon, pkg));
        }

        adapter.notifyDataSetChanged();
    }

    private void startLiveClock() {
        clockRunnable = () -> {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm  |  EEE, MMM d", Locale.getDefault());
            clockTextView.setText(sdf.format(new Date()));
            clockHandler.postDelayed(clockRunnable, 1000);
        };
        clockHandler.post(clockRunnable);
    }

    private void registerPackageReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacks(clockRunnable);
        idleHandler.removeCallbacks(idleRunnable);
        wallpaperHandler.removeCallbacks(wallpaperRunnable);
        try { unregisterReceiver(packageReceiver); } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        resetIdleTimer(); // Intercept back button
    }

    // --- Inner Models & Adapter ---
    private static class AppModel {
        final String name;
        final Drawable icon;
        final String packageName;
        AppModel(String name, Drawable icon, String packageName) {
            this.name = name; this.icon = icon; this.packageName = packageName;
        }
    }

    private class AppAdapter extends BaseAdapter {
        private final Context context;
        private final List<AppModel> list;

        AppAdapter(Context context, List<AppModel> list) {
            this.context = context; this.list = list;
        }

        @Override public int getCount() { return list.size(); }
        @Override public Object getItem(int position) { return list.get(position); }
        @Override public Long getItemId(int position) { return (long) position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppModel item = list.get(position);
            LinearLayout container = (LinearLayout) convertView;

            if (container == null) {
                container = new LinearLayout(context);
                container.setOrientation(LinearLayout.VERTICAL);
                container.setGravity(Gravity.CENTER);
                container.setPadding(20, 20, 20, 20);
                container.setFocusable(true);
                container.setFocusableInTouchMode(true);

                // Dynamic Accent Color Focus Listener
                container.setOnFocusChangeListener((v, hasFocus) -> {
                    resetIdleTimer();
                    GradientDrawable drawable = new GradientDrawable();
                    drawable.setCornerRadius(16f);

                    if (hasFocus) {
                        drawable.setColor(currentAccentColor); // Dynamic accent from active wallpaper!
                        v.setScaleX(1.12f);
                        v.setScaleY(1.12f);
                    } else {
                        drawable.setColor(Color.parseColor("#CC1A1A1A")); // Translucent dark
                        v.setScaleX(1.0f);
                        v.setScaleY(1.0f);
                    }
                    v.setBackground(drawable);
                });

                ImageView iconView = new ImageView(context);
                iconView.setLayoutParams(new LinearLayout.LayoutParams(120, 120));

                TextView textView = new TextView(context);
                textView.setTextColor(Color.WHITE);
                textView.setTextSize(14);
                textView.setGravity(Gravity.CENTER);
                textView.setPadding(0, 10, 0, 0);

                container.addView(iconView);
                container.addView(textView);
            }

            ImageView iconView = (ImageView) container.getChildAt(0);
            TextView textView = (TextView) container.getChildAt(1);

            iconView.setImageDrawable(item.icon);
            textView.setText(item.name);

            return container;
        }
    }
}
