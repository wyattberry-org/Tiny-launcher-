package com.tiny.launcher.weather;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.net.*;
import java.util.Enumeration;

public class WeatherConfigServer {
    private static ServerSocket serverSocket;
    private static volatile boolean isRunning = false;
    public interface OnSaveListener { void onSaved(String url); }

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) return inetAddress.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public static synchronized void start(Context context, OnSaveListener listener) {
        stop(); isRunning = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(8080));
                while (isRunning && !serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(3000);
                    new Thread(() -> handleClient(context, socket, listener)).start();
                }
            } catch (Exception ignored) {}
        }).start();
    }

    public static synchronized void stop() {
        isRunning = false;
        try { if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close(); } catch (Exception ignored) {}
        serverSocket = null;
    }

    private static void handleClient(Context context, Socket socket, OnSaveListener listener) {
        try (Socket s = socket; BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream())); OutputStream out = s.getOutputStream()) {
            String line = in.readLine(); if (line == null) return;
            SharedPreferences prefs = context.getSharedPreferences("BareLauncherPrefs", Context.MODE_PRIVATE);
            if (line.contains("GET /save?")) {
                SharedPreferences.Editor editor = prefs.edit();
                String sUrl = getParam(line, "shelly="), pUrl = getParam(line, "provider=");
                if (sUrl != null) editor.putString("weather_shelly_api", sUrl);
                if (pUrl != null) editor.putString("weather_provider_api", pUrl);
                editor.apply();
                if (listener != null) listener.onSaved(sUrl != null ? sUrl : pUrl);
                String resp = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n" +
                    "<html><body style=\"background:#111;color:#00ff66;font-family:sans-serif;text-align:center;padding:50px;\">" +
                    "<h2>Success!</h2><p style=\"color:#fff;\">Weather API URLs updated.</p></body></html>";
                out.write(resp.getBytes("UTF-8")); stop(); return;
            }
            String currS = prefs.getString("weather_shelly_api", "");
            String currP = prefs.getString("weather_provider_api", "");
            String html = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n" +
                "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<style>body{background:#111;color:#fff;font-family:sans-serif;padding:20px;}" +
                "input,button{width:100%;padding:14px;margin:8px 0;font-size:15px;border-radius:8px;box-sizing:border-box;border:1px solid #333;background:#222;color:#fff;}" +
                "button{background:#007aff;color:#fff;border:none;font-weight:bold;margin-top:16px;}</style></head><body>" +
                "<h2>Tiny Launcher Weather Setup</h2><form action=\"/save\" method=\"GET\">" +
                "<label>Shelly Cloud API URL:</label>" +
                "<input type=\"text\" name=\"shelly\" value=\"" + currS + "\" placeholder=\"https://shelly-...\">" +
                "<label>Weather Provider API URL (Open-Meteo):</label>" +
                "<input type=\"text\" name=\"provider\" value=\"" + currP + "\" placeholder=\"https://api.open-meteo.com/...\">" +
                "<button type=\"submit\">Save All Settings</button></form></body></html>";
            out.write(html.getBytes("UTF-8"));
        } catch (Exception ignored) {}
    }

    private static String getParam(String line, String key) {
        int start = line.indexOf(key); if (start == -1) return null;
        int valStart = start + key.length(), end = line.indexOf("&", valStart);
        if (end == -1) end = line.indexOf(" ", valStart);
        if (end == -1) end = line.length();
        try { return URLDecoder.decode(line.substring(valStart, end), "UTF-8").trim(); } catch (Exception e) { return null; }
    }
}
