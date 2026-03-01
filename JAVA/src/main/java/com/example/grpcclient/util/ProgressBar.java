package com.example.grpcclient.util;

import java.text.DecimalFormat;

public class ProgressBar {
    
    private static final char FILL_CHAR = '█';
    private static final char EMPTY_CHAR = '░';
    private static final char LEFT_CHAR = '║';
    private static final char RIGHT_CHAR = '║';
    private static final char UPPER_LEFT_CHAR = '┌';
    private static final char UPPER_RIGHT_CHAR = '┐';
    private static final char LOWER_LEFT_CHAR = '└';
    private static final char LOWER_RIGHT_CHAR = '┘';
    private static final char HORIZONTAL_CHAR = '─';

    // Animation characters for spinner
    private static final char[] HALF_CIRCLE_ANIMATION = {'◐', '◓', '◑', '◒'};
    
    private final boolean shouldUseEnhancedProgressBar;
    private volatile long lastProgressUpdate = 0;
    private volatile int spinnerIndex = 0;
    private final Object progressLock = new Object();
    private final DecimalFormat numberFormat = new DecimalFormat("#,###");

    public ProgressBar(boolean shouldUseEnhancedProgressBar) {
        this.shouldUseEnhancedProgressBar = shouldUseEnhancedProgressBar;
    }

    public void updateProgress(long progress, long total) {
        if (!shouldUseEnhancedProgressBar) {
            return;
        }

        // Throttle updates - only update every 10 items unless it's the final update
        if ((progress != total) && (progress - lastProgressUpdate) < 10) {
            return;
        }

        synchronized (progressLock) {
            lastProgressUpdate = progress;
            
            if (total == 0) {
                return;
            }

            int percent = (int) ((double) progress / total * 100);
            String statusString = String.format("%d%% %s / %s %s", 
                    percent, 
                    numberFormat.format(progress), 
                    numberFormat.format(total), 
                    HALF_CIRCLE_ANIMATION[spinnerIndex]);

            // Calculate console width (assume 120 if system property not available)
            int consoleWidth = getConsoleWidth();
            int barSize = Math.max(20, consoleWidth - statusString.length() - 10);
            int progressSize = (int) ((double) progress / total * barSize);

            // Update spinner
            spinnerIndex = (spinnerIndex + 1) % HALF_CIRCLE_ANIMATION.length;

            StringBuilder progressBar = new StringBuilder();

            // Top border
            progressBar.append(UPPER_LEFT_CHAR);
            progressBar.append(String.valueOf(HORIZONTAL_CHAR).repeat(barSize));
            progressBar.append(UPPER_RIGHT_CHAR);
            progressBar.append(" ".repeat(Math.max(0, consoleWidth - barSize - 2)));
            progressBar.append('\n');

            // Progress line
            progressBar.append(LEFT_CHAR);
            progressBar.append(String.valueOf(FILL_CHAR).repeat(progressSize));
            progressBar.append(String.valueOf(EMPTY_CHAR).repeat(barSize - progressSize));
            progressBar.append(String.format("%s %s", RIGHT_CHAR, statusString));
            progressBar.append(" ".repeat(Math.max(0, consoleWidth - barSize - statusString.length() - 3)));
            progressBar.append('\n');

            // Bottom border
            progressBar.append(LOWER_LEFT_CHAR);
            progressBar.append(String.valueOf(HORIZONTAL_CHAR).repeat(barSize));
            progressBar.append(LOWER_RIGHT_CHAR);
            progressBar.append(" ".repeat(Math.max(0, consoleWidth - barSize - 3)));

            // Clear previous lines and print new progress bar
            System.out.print("\r\033[2A"); // Move cursor up 2 lines
            System.out.print("\033[J");    // Clear from cursor to end of screen
            System.out.print(progressBar.toString());
            System.out.flush();

            // If this is the final update, move cursor to next line
            if (progress == total) {
                System.out.println();
            }
        }
    }

    private int getConsoleWidth() {
        // Try to get console width from system properties or environment
        try {
            String columns = System.getenv("COLUMNS");
            if (columns != null) {
                return Integer.parseInt(columns);
            }
        } catch (NumberFormatException ignored) {
        }

        // Default fallback
        return 120;
    }

    public void reset() {
        synchronized (progressLock) {
            lastProgressUpdate = 0;
            spinnerIndex = 0;
        }
    }
}