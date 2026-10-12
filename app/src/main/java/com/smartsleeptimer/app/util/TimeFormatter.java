package com.smartsleeptimer.app.util;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Utility functions for formatting and calculating timer durations.
 */
public final class TimeFormatter {

    private TimeFormatter() {
        // Utility class
    }

    /**
     * Converts hours and minutes into total milliseconds.
     */
    public static long toMillis(int hours, int minutes) {
        if (hours < 0 || minutes < 0) {
            throw new IllegalArgumentException("Hours and minutes must be non-negative");
        }
        return TimeUnit.HOURS.toMillis(hours) + TimeUnit.MINUTES.toMillis(minutes);
    }

    /**
     * Formats milliseconds into a display string.
     * Returns "HH:MM:SS" if duration >= 1 hour, otherwise "MM:SS".
     *
     * @param millis remaining time in milliseconds
     * @return formatted time string (e.g., "00:30", "15:00", "01:30:00")
     */
    public static String formatTime(long millis) {
        if (millis < 0) {
            millis = 0;
        }

        // Round up to nearest whole second so that e.g. 950ms displays as 1s, and only 0 displays as 0s
        long totalSeconds = (millis + 999) / 1000;

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    /**
     * Generates a friendly summary label for a duration.
     * E.g. "30 sec", "45 min", "1 hr", "1 hr 30 min".
     */
    public static String formatDurationLabel(long millis) {
        if (millis < 1000) {
            return "0s";
        }
        long totalSeconds = millis / 1000;
        if (totalSeconds < 60) {
            return totalSeconds + " sec";
        }

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        if (hours > 0 && minutes > 0) {
            return hours + "h " + minutes + "m";
        } else if (hours > 0) {
            return hours + (hours == 1 ? " hour" : " hours");
        } else {
            return minutes + " min";
        }
    }
}
