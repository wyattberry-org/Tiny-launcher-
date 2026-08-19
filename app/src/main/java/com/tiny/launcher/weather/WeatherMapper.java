package com.tiny.launcher.weather;

import com.tiny.launcher.R;

public class WeatherMapper {
    public static int getIconResource(int code, boolean isDay) {
        switch (code) {
            case -1: return 0;
            case 0: case 1: return isDay ? R.drawable.weather_clear : R.drawable.weather_clear_night;
            case 2: return R.drawable.weather_partly_cloudy;
            case 3: return R.drawable.weather_cloudy;
            case 45: case 48: return R.drawable.weather_fog;
            case 51: case 52: case 53: case 54: case 55: return R.drawable.weather_drizzle;
            case 56: case 57: case 66: case 67: return R.drawable.weather_sleet;
            case 61: case 62: case 63: case 64: case 65: case 80: case 81: case 82:
                return isDay ? R.drawable.weather_rain : R.drawable.weather_heavy_rain;
            case 71: case 72: case 73: case 74: case 75: case 76: case 77: case 85: case 86: return R.drawable.weather_snow;
            case 95: case 96: case 99: return isDay ? R.drawable.weather_thunder : R.drawable.weather_thunder_rain;
            default: return isDay ? R.drawable.weather_clear : R.drawable.weather_clear_night;
        }
    }
    public static String getConditionText(int code, boolean isDay) {
        switch (code) {
            case -1: return "-";
            case 0: case 1: return isDay ? "Sunny" : "Clear";
            case 2: return "Partly Cloudy"; case 3: return "Cloudy";
            case 45: return "Foggy"; case 48: return "Rime Fog";
            case 51: return "Light Drizzle"; case 53: return "Moderate Drizzle"; case 55: return "Heavy Drizzle";
            case 56: case 57: return "Freezing Drizzle";
            case 61: return "Light Rain"; case 63: return "Moderate Rain"; case 65: return "Heavy Rain";
            case 66: case 67: return "Freezing Rain";
            case 71: return "Light Snow"; case 73: return "Moderate Snow"; case 75: return "Heavy Snow"; case 77: return "Snow Grains";
            case 80: case 81: return "Rain Showers"; case 82: return "Heavy Showers";
            case 85: case 86: return "Snow Showers";
            case 95: return "Thunderstorm"; case 96: return "Hail Storm"; case 99: return "Severe Storm";
            default: return "Weather: " + code;
        }
    }
}