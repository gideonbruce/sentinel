package com.example.ml;

/**
 * Preprocesses sensor data (scaling/normalization)
 */
public class DataPreprocessor {
    // Standardization parameters (mean and std from training data)
    // You should replace these with actual values from your training data
    private static final float[] FEATURE_MEANS = {
            0.0167f, 0.0171f, 0.0197f,  // acc_x, acc_y, acc_z means
            0.0f, 0.0f, 0.0f            // gy_x, gy_y, gy_z means (approximate)
    };

    private static final float[] FEATURE_STDS = {
            0.152f, 0.152f, 0.156f,     // acc_x, acc_y, acc_z stds
            1.0f, 1.0f, 1.0f            // gy_x, gy_y, gy_z stds (approximate)
    };

    /**
     * Standardize sensor data (z-score normalization)
     * Formula: (x - mean) / std
     */
    public static float[][][] standardize(float[][][] data) {
        int timesteps = data[0].length;
        int features = data[0][0].length;

        float[][][] standardized = new float[1][timesteps][features];

        for (int t = 0; t < timesteps; t++) {
            for (int f = 0; f < features; f++) {
                standardized[0][t][f] = (data[0][t][f] - FEATURE_MEANS[f]) / FEATURE_STDS[f];
            }
        }

        return standardized;
    }

    /**
     * Update standardization parameters from training data
     */
    public static void updateParameters(float[] means, float[] stds) {
        if (means.length != 6 || stds.length != 6) {
            throw new IllegalArgumentException("Must provide 6 means and 6 stds");
        }

        System.arraycopy(means, 0, FEATURE_MEANS, 0, 6);
        System.arraycopy(stds, 0, FEATURE_STDS, 0, 6);
    }
}