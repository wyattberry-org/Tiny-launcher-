package com.tiny.launcher.weather;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;

public class WeatherWidget extends LinearLayout {
    private TextView tvCondition, tvTemp, tvRh, tvWindSpeed, tvWindGusts;
    private ImageView ivIcon;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> currentFuture;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private double lastValidTemp = 0.0, lastValidRh = 0.0;
    private double cachedWindSpeed = 0.0, cachedWindGusts = 0.0;
    private boolean hasValidData = false, cachedIsDay = true;
    private int cachedWeatherCode = -1, consecutiveFailures = 0;
    private long lastOpenMeteoFetchTime = 0;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    public WeatherWidget(Context c) { super(c); init(c); }
    public WeatherWidget(Context c, AttributeSet a) { super(c, a); init(c); }

    private String getShellyUrl() { return getContext().getSharedPreferences("BareLauncherPrefs", Context.MODE_PRIVATE).getString("weather_shelly_api", "").trim(); }
    

    private String getWeatherUrl() {
        return getContext().getSharedPreferences("BareLauncherPrefs", Context.MODE_PRIVATE).getString("weather_provider_api", "").trim();
    }

    private View createDivider(Context c) {
        View div = new View(c);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(1), dp(36));
        p.gravity = Gravity.CENTER_VERTICAL;
        p.setMarginStart(dp(16)); p.setMarginEnd(dp(16));
        div.setLayoutParams(p); div.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        return div;
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(8), dp(8), dp(8), dp(8)); setFocusable(false); setClickable(false);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(-2, -2);
        centerLp.gravity = Gravity.CENTER_VERTICAL;
        LinearLayout col1 = new LinearLayout(context); col1.setOrientation(VERTICAL); col1.setGravity(Gravity.CENTER_HORIZONTAL); col1.setTranslationY(-dp(4));
        ivIcon = new ImageView(context); ivIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(73), dp(73))); ivIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tvCondition = new TextView(context); tvCondition.setLayoutParams(new LinearLayout.LayoutParams(-2, -2)); tvCondition.setTextColor(Color.WHITE); tvCondition.setTextSize(14); tvCondition.setGravity(Gravity.CENTER); tvCondition.setMaxLines(2); tvCondition.setSingleLine(false);
        col1.addView(ivIcon); col1.addView(tvCondition); addView(col1, centerLp);
        addView(createDivider(context));
        LinearLayout col2 = new LinearLayout(context); col2.setOrientation(VERTICAL); col2.setGravity(Gravity.CENTER_HORIZONTAL);
        tvTemp = new TextView(context); tvTemp.setTextColor(Color.WHITE); tvTemp.setTextSize(28); tvTemp.setTypeface(null, android.graphics.Typeface.BOLD); tvTemp.setGravity(Gravity.CENTER);
        TextView lblT = new TextView(context); lblT.setText("Temp"); lblT.setTextColor(Color.parseColor("#B0B0B0")); lblT.setTextSize(12); lblT.setGravity(Gravity.CENTER);
        col2.addView(tvTemp); col2.addView(lblT); addView(col2, centerLp);
        addView(createDivider(context));
        LinearLayout col3 = new LinearLayout(context); col3.setOrientation(VERTICAL); col3.setGravity(Gravity.CENTER_HORIZONTAL);
        tvRh = new TextView(context); tvRh.setTextColor(Color.WHITE); tvRh.setTextSize(28); tvRh.setTypeface(null, android.graphics.Typeface.BOLD); tvRh.setGravity(Gravity.CENTER);
        TextView lblRh = new TextView(context); lblRh.setText("RH"); lblRh.setTextColor(Color.parseColor("#B0B0B0")); lblRh.setTextSize(12); lblRh.setGravity(Gravity.CENTER);
        col3.addView(tvRh); col3.addView(lblRh); addView(col3, centerLp);
        addView(createDivider(context));
        LinearLayout col4 = new LinearLayout(context); col4.setOrientation(VERTICAL); col4.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        tvWindSpeed = new TextView(context); tvWindSpeed.setTextColor(Color.WHITE); tvWindSpeed.setTextSize(14);
        tvWindGusts = new TextView(context); tvWindGusts.setTextColor(Color.WHITE); tvWindGusts.setTextSize(14);
        col4.addView(tvWindSpeed); col4.addView(tvWindGusts); addView(col4, centerLp);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (getVisibility() == VISIBLE) startPolling(); }
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); stopPolling(); }
    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) startPolling(); else stopPolling();
    }
    public synchronized void forceRefresh() { lastOpenMeteoFetchTime = 0; if (getVisibility() == VISIBLE) startPolling(); }
    private synchronized void startPolling() {
        stopPolling();
        if (getVisibility() != VISIBLE) return;
        consecutiveFailures = 0;
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.execute(this::pollTask);
    }
    private synchronized void stopPolling() {
        mainHandler.removeCallbacksAndMessages(null);
        if (currentFuture != null) { currentFuture.cancel(true); currentFuture = null; }
        if (executor != null) { if (!executor.isShutdown()) executor.shutdownNow(); executor = null; }
    }
    private void pollTask() {
        if (getVisibility() != VISIBLE) { stopPolling(); return; }
        boolean success = false;
        double temp = lastValidTemp, rh = lastValidRh;
        HttpURLConnection sConn = null;
        try {
            String sUrlStr = getShellyUrl();
            if (sUrlStr.isEmpty()) throw new Exception("No Shelly");
            sConn = (HttpURLConnection) new URL(sUrlStr).openConnection();
            sConn.setRequestProperty("User-Agent", "Mozilla/5.0");
            sConn.setConnectTimeout(5000);
            sConn.setReadTimeout(5000);
            if (sConn.getResponseCode() == 200) {
                BufferedReader r = new BufferedReader(new InputStreamReader(sConn.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                JSONObject root = new JSONObject(sb.toString());
                JSONObject data = root.optJSONObject("data");
                if (data != null && data.has("device_status")) {
                    JSONObject dev = data.getJSONObject("device_status");
                    if (dev.has("temperature:0")) {
                        JSONObject t = dev.optJSONObject("temperature:0");
                        JSONObject h = dev.optJSONObject("humidity:0");
                        if (t != null) temp = t.optDouble("tC", temp);
                        if (h != null) rh = h.optDouble("rh", rh);
                    } else if (dev.has("tmp")) {
                        JSONObject t = dev.optJSONObject("tmp");
                        JSONObject h = dev.optJSONObject("hum");
                        if (t != null) temp = t.optDouble("value", temp);
                        if (h != null) rh = h.optDouble("value", rh);
                    }
                    success = true;
                }
            }
        } catch (Exception ignored) {} finally { if (sConn != null) sConn.disconnect(); }
        if (success) {
            lastValidTemp = temp; lastValidRh = rh; hasValidData = true; consecutiveFailures = 0;
        } else { consecutiveFailures++; }
        long currentTime = System.currentTimeMillis();
        if (!hasValidData || cachedWeatherCode == -1 || lastOpenMeteoFetchTime == 0 || currentTime - lastOpenMeteoFetchTime >= 15 * 60 * 1000) {
            HttpURLConnection conn = null;
            try {
                String wUrlStr = getWeatherUrl();
                if (wUrlStr.isEmpty()) throw new Exception("No Weather");
                conn = (HttpURLConnection) new URL(wUrlStr).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    JSONObject root = new JSONObject(sb.toString());
                    JSONObject current = root.optJSONObject("current");
                    if (current == null) current = root.optJSONObject("current_weather");
                    if (current != null) {
                        cachedWeatherCode = current.optInt("weather_code", current.optInt("weathercode", cachedWeatherCode));
                        cachedIsDay = current.optInt("is_day", 1) == 1;
                        cachedWindSpeed = current.optDouble("wind_speed_10m", current.optDouble("windspeed", cachedWindSpeed));
                        cachedWindGusts = current.optDouble("wind_gusts_10m", current.optDouble("windgusts", cachedWindGusts));
                        if (!success) {
                            if (current.has("temperature_2m")) { lastValidTemp = current.optDouble("temperature_2m", lastValidTemp); hasValidData = true; }
                            else if (current.has("temperature")) { lastValidTemp = current.optDouble("temperature", lastValidTemp); hasValidData = true; }
                            if (current.has("relative_humidity_2m")) { lastValidRh = current.optDouble("relative_humidity_2m", lastValidRh); hasValidData = true; }
                            else if (current.has("humidity")) { lastValidRh = current.optDouble("humidity", lastValidRh); hasValidData = true; }
                        } else { hasValidData = true; }
                    }
                    lastOpenMeteoFetchTime = currentTime;
                }
            } catch (Exception ignored) {} finally { if (conn != null) conn.disconnect(); }
        }
        int iconResId = WeatherMapper.getIconResource(cachedWeatherCode, cachedIsDay);
        String condText = WeatherMapper.getConditionText(cachedWeatherCode, cachedIsDay).replace(" ", "\n");
        final int fTemp = (int) Math.round(lastValidTemp), fRh = (int) Math.round(lastValidRh);
        final int fSpeed = (int) Math.round(cachedWindSpeed), fGusts = (int) Math.round(cachedWindGusts);
        final int fIcon = iconResId;
        final boolean show = hasValidData;
        mainHandler.post(() -> {
            if (tvCondition != null) tvCondition.setText(show ? condText : "--");
            if (tvTemp != null) tvTemp.setText(show ? String.valueOf(fTemp) : "--");
            if (tvRh != null) tvRh.setText(show ? String.valueOf(fRh) : "--");
            if (tvWindSpeed != null) tvWindSpeed.setText(show ? "Wind: " + fSpeed + " m/s" : "Wind: --");
            if (tvWindGusts != null) tvWindGusts.setText(show ? "Gusts: " + fGusts + " m/s" : "Gusts: --");
            if (ivIcon != null) {
                if (fIcon != 0) { ivIcon.setImageResource(fIcon); ivIcon.setVisibility(VISIBLE); }
                else ivIcon.setVisibility(GONE);
            }
        });
        long delay = success ? 300000L : ((consecutiveFailures * 3000L) <= 30000L ? 3000L : 300000L);
        if (executor != null && !executor.isShutdown()) currentFuture = executor.schedule(this::pollTask, delay, TimeUnit.MILLISECONDS);
    }
}
