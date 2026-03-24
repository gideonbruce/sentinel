package com.example.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Wrapper class for TensorFlow Lite fall detection model
 * Handles model loading, inference, and quantization/dequantization
 */
public class FallDetectionModel {
    private static final String TAG = "FallDetectionModel";
    private static final String MODEL_PATH = "model_int8_quantized.tflite";

    // Model constants
    private static final int TIMESTEPS = 160;
    private static final int NUM_FEATURES = 6; // acc_x, acc_y, acc_z, gy_x, gy_y, gy_z
    private static final int NUM_CLASSES = 4; // idle, fall, step, motion

    // Class labels
    public static final String[] CLASS_LABELS = {"idle", "fall", "step", "motion"};

    // TFLite interpreter
    private Interpreter tflite;

    // Quantization parameters (obtained from quantization script)
    private float inputScale;
    private int inputZeroPoint;
    private float outputScale;
    private int outputZeroPoint;

    /**
     * Constructor - loads the model
     */
    public FallDetectionModel(Context context) throws IOException {
        MappedByteBuffer modelBuffer = loadModelFile(context);

        // Configure interpreter options
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4); // 4 threads for better performance
        options.setUseNNAPI(false); //using Android Neural Networks API if available

        tflite = new Interpreter(modelBuffer, options);

        // Extract quantization parameters
        extractQuantizationParams();

        Log.i(TAG, "Model loaded successfully");
        Log.i(TAG, "Input scale: " + inputScale + ", zero point: " + inputZeroPoint);
        Log.i(TAG, "Output scale: " + outputScale + ", zero point: " + outputZeroPoint);
    }

    /**
     * Loading the TFLite model from assets
     */
    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Extracting quantization parameters from the model
     */
    private void extractQuantizationParams() {
        // Input quantization params
        inputScale = tflite.getInputTensor(0).quantizationParams().getScale();
        inputZeroPoint = tflite.getInputTensor(0).quantizationParams().getZeroPoint();

        // Output quantization params
        outputScale = tflite.getOutputTensor(0).quantizationParams().getScale();
        outputZeroPoint = tflite.getOutputTensor(0).quantizationParams().getZeroPoint();
    }

    /**
     * Run inference on sensor data
     * @param sensorData 3D array [timesteps][features] - preprocessed and scaled
     * @return FallDetectionResult containing prediction and confidence
     */
    public FallDetectionResult predict(float[][][] sensorData) {
        // Quantize input
        byte[][][] inputBuffer = quantizeInput(sensorData);

        // Prepare output buffer
        byte[][] outputBuffer = new byte[1][NUM_CLASSES];

        // Run inference
        tflite.run(inputBuffer, outputBuffer);

        // Dequantize output
        float[] probabilities = dequantizeOutput(outputBuffer[0]);

        // Find the class with highest probability
        int predictedClass = 0;
        float maxProb = probabilities[0];
        for (int i = 1; i < NUM_CLASSES; i++) {
            if (probabilities[i] > maxProb) {
                maxProb = probabilities[i];
                predictedClass = i;
            }
        }

        return new FallDetectionResult(
                predictedClass,
                CLASS_LABELS[predictedClass],
                maxProb,
                probabilities
        );
    }

    /**
     * Quantize float input to int8
     */
    private byte[][][] quantizeInput(float[][][] input) {
        byte[][][] quantized = new byte[1][TIMESTEPS][NUM_FEATURES];

        for (int t = 0; t < TIMESTEPS; t++) {
            for (int f = 0; f < NUM_FEATURES; f++) {
                float value = input[0][t][f];
                int quantizedValue = (int) Math.round(value / inputScale + inputZeroPoint);
                quantizedValue = Math.max(-128, Math.min(127, quantizedValue));
                quantized[0][t][f] = (byte) quantizedValue;
            }
        }

        return quantized;
    }

    /**
     * Dequantize int8 output to float
     */
    private float[] dequantizeOutput(byte[] output) {
        float[] dequantized = new float[NUM_CLASSES];

        for (int i = 0; i < NUM_CLASSES; i++) {
            int quantizedValue = output[i] & 0xFF; // Convert to unsigned
            dequantized[i] = (quantizedValue - outputZeroPoint) * outputScale;
        }

        // Apply softmax to get probabilities
        return softmax(dequantized);
    }

    /**
     * Apply softmax activation
     */
    private float[] softmax(float[] logits) {
        float[] exp = new float[logits.length];
        float sum = 0.0f;

        // Find max for numerical stability
        float max = logits[0];
        for (float logit : logits) {
            if (logit > max) max = logit;
        }

        // Compute exp and sum
        for (int i = 0; i < logits.length; i++) {
            exp[i] = (float) Math.exp(logits[i] - max);
            sum += exp[i];
        }

        // Normalize
        for (int i = 0; i < exp.length; i++) {
            exp[i] /= sum;
        }

        return exp;
    }

    /**
     * Clean up resources
     */
    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
    }

    // Getters
    public static int getTimesteps() { return TIMESTEPS; }
    public static int getNumFeatures() { return NUM_FEATURES; }
}

