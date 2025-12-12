package com.example.ml;

import java.util.List;

/**
 * Represents a window of sensor data
 */
public class SensorWindow {
    private final List<float[]> accelerometerData; // Each float[] is [x, y, z]
    private final List<float[]> gyroscopeData;     // Each float[] is [x, y, z]

    public SensorWindow(List<float[]> accelerometerData, List<float[]> gyroscopeData) {
        this.accelerometerData = accelerometerData;
        this.gyroscopeData = gyroscopeData;
    }

    public List<float[]> getAccelerometerData() { return accelerometerData; }
    public List<float[]> getGyroscopeData() { return gyroscopeData; }

    /**
     * Converting to model input format [1][timesteps][6]
     * Order: acc_x, acc_y, acc_z, gy_x, gy_y, gy_z
     */
    public float[][][] toModelInput() {
        int timesteps = accelerometerData.size();
        float[][][] input = new float[1][timesteps][6];

        for (int t = 0; t < timesteps; t++) {
            float[] acc = accelerometerData.get(t);
            float[] gyro = gyroscopeData.get(t);

            input[0][t][0] = acc[0];  // acc_x
            input[0][t][1] = acc[1];  // acc_y
            input[0][t][2] = acc[2];  // acc_z
            input[0][t][3] = gyro[0]; // gy_x
            input[0][t][4] = gyro[1]; // gy_y
            input[0][t][5] = gyro[2]; // gy_z
        }

        return input;
    }
}