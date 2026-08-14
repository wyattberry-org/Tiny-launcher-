cat << 'EOF' > app/src/main/java/com/tiny/launcher/LauncherActivity.java
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LauncherActivity extends Activity {

    // --- Modern Java 17 Record for Data Model ---
    public record AppModel(String name, Drawable icon, String packageName, boolean isLeanback) {}

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
    private int currentAccentColor = Color.parseColor("#007AFF"); // Default Accent Blue

    // --- Timers & Handlers ---
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private final Handler wallpaperHandler = new Handler(Looper.getMainLooper());
    private Runnable clockRunnable, idleRunnable, wallpaperRunnable;

    // --- Wallpapers & State ---
    private final List<File> wallpaperFiles = new ArrayList<>();
    private int currentWallpaperIndex = 0;
    private int selectedMovePosition = -1;

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

        // --- 1. Root Frame Layout ---
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.BLACK);

        // --- 2. Wallpaper ImageSwitcher ---
        wallpaperSwitcher = new ImageSwitcher(this);
        wallpaperSwitcher.setLayoutParams(new LinearLayout.LayoutParams(
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

        LinearLayout mainOverlay = new LinearLayout(this);
        mainOverlay.setOrientation(LinearLayout.VERTICAL);
        mainOverlay.setPadding(50, 30, 50, 30);

        // --- 3. Top Status Row (Weather, Clock, Settings) ---
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

        settingsGear = new ImageView(this);
        settingsGear.setImageResource(android.R.drawable.ic_menu_preferences);
        settingsGear.setFocusable(true);
        settingsGear.setPadding(15, 15, 15, 15);
        settingsGear.setOnClickListener(v -> checkPinAndExecute(this::openSettingsDialog));

        topWidgetRow.addView(weatherTextView);
        topWidgetRow.addView(clockTextView);
        topWidgetRow.addView(settingsGear);
        mainOverlay.addView(topWidgetRow);

        // --- 4. Main App Grid ---
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

        // --- 5. Tile Click & Long-Press Logic ---
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            resetIdleTimer();
            if (selectedMovePosition != -1) {
                // Move App Mode
                Collections.swap(appList, selectedMovePosition, position);
                selectedMovePosition = -1;
                adapter.notifyDataSetChanged();
            } else {
                // Launch App
                AppModel app = appList.get(position);
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(app.packageName());
                if (launchIntent != null) {
                    startActivity(launchIntent);
                }
            }
        });

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

    // --- Dynamic Tile Accent Extractor ---
    private void extractAccentColorFromBitmap(Bitmap bitmap) {
        if (bitmap == null) return;
        Palette.from(bitmap).generate(palette -> {
            if (palette != null) {
                int defaultColor = Color.parseColor("#007AFF");
                currentAccentColor = palette.getVibrantColor(palette.getDominantColor(defaultColor));
                adapter.notifyDataSetChanged();
            }
        });
    }

    // --- Safe Low-RAM Wallpaper Downsampling ---
    private Bitmap decodeSampledBitmapFromFile(String filePath, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filePath, options);
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private void loadWallpapers() {
        wallpaperFiles.clear();
        String folderPath = prefs.getString("WallpaperFolder", "/sdcard/Pictures/Wallpapers");
        File dir = new File(folderPath);

        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png"));
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

                DisplayMetrics metrics = getResources().getDisplayMetrics();
                Bitmap bitmap = decodeSampledBitmapFromFile(file.getAbsolutePath(), metrics.widthPixels, metrics.heightPixels);

                if (bitmap != null) {
                    wallpaperSwitcher.setImageDrawable(new BitmapDrawable(getResources(), bitmap));
                    extractAccentColorFromBitmap(bitmap);
                }

                currentWallpaperIndex = (currentWallpaperIndex + 1) % wallpaperFiles.size();
                wallpaperHandler.postDelayed(this, 30000); // 30 sec rotation
            }
        };
        wallpaperHandler.post(wallpaperRunnable);
    }

    // --- Idle Screen Dimming Engine ---
    private void setupIdleAutoTimer() {
        idleRunnable = () -> gridView.animate().alpha(0.0f).setDuration(600).start();
        resetIdleTimer();
    }

    private void resetIdleTimer() {
        if (gridView.getAlpha() < 1.0f) {
            gridView.animate().alpha(1.0f).setDuration(200).start();
        }
        idleHandler.removeCallbacks(idleRunnable);
        idleHandler.postDelayed(idleRunnable, 30000);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        resetIdleTimer();
        return super.dispatchKeyEvent(event);
    }

    // --- Parental Control Verification ---
    private void checkPinAndExecute(Runnable onSuccess) {
        String savedPin = prefs.getString("ParentalPin", "0000");

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

    // --- Long-Press Tile Menu ---
    private void showAppContextMenu(int position) {
        AppModel app = appList.get(position);
        String[] options = {"↔️ Move App", "⚙️ App Info", "🗑️ Uninstall App", "🙈 Hide App"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Options for " + app.name());
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0 -> {
                    selectedMovePosition = position;
                    new AlertDialog.Builder(this).setMessage("Click another tile to swap positions!").show();
                }
                case 1 -> {
                    Intent infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName()));
                    startActivity(infoIntent);
                }
                case 2 -> {
                    Intent uninstIntent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName()));
                    startActivity(uninstIntent);
                }
                case 3 -> hideApp(app.packageName());
            }
        });
        builder.show();
    }

    private void hideApp(String packageName) {
        Set<String> hidden = new HashSet<>(prefs.getStringSet("HiddenApps", new HashSet<>()));
        hidden.add(packageName);
        prefs.edit().putStringSet("HiddenApps", hidden).apply();
        loadInstalledApps();
    }

    private void openSettingsDialog() {
        String[] options = {"👁️ Unhide Apps", "🔑 Change Parental PIN"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
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
            } else if (which == 1) {
                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                new AlertDialog.Builder(this).setTitle("New 4-Digit PIN").setView(input)
                        .setPositiveButton("Save", (d, w) -> prefs.edit().putString("ParentalPin", input.getText().toString()).apply()).show();
            }
        });
        builder.show();
    }

    // --- Merged App Fetcher (Leanback TV + Sideloaded Phone Apps) ---
    private void loadInstalledApps() {
        appList.clear();
        PackageManager pm = getPackageManager();
        Set<String> hidden = prefs.getStringSet("HiddenApps", new HashSet<>());

        Map<String, AppModel> discoveredApps = new LinkedHashMap<>();

        // 1. Fetch TV Leanback Launcher Apps
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

        // 2. Fetch Sideloaded Standard Apps
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
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void startLiveClock() {
        clockRunnable = () -> {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm  |  EEE, MMM d", Locale.getDefault());
            clockTextView.setText(sdf.format(new Date()));
            clockHandler.postDelayed(clockRunnable, 1000);
        };
        clockHandler.post(clockRunnable);
    }

    // --- Android 14 (API 34) Safe Broadcast Receiver Registration ---
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
        resetIdleTimer();
    }

    // --- Modern Focus Grid Adapter ---
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

                container.setOnFocusChangeListener((v, hasFocus) -> {
                    resetIdleTimer();
                    GradientDrawable drawable = new GradientDrawable();
                    drawable.setCornerRadius(16f);

                    if (hasFocus) {
                        drawable.setColor(currentAccentColor);
                        v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(150).start();
                    } else {
                        drawable.setColor(Color.parseColor("#CC1A1A1A"));
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
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

            iconView.setImageDrawable(item.icon());
            textView.setText(item.name());

            return container;
        }
    }
}
EOF
