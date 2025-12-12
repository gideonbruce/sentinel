package com.example.ml;

/**
 * Container for fall detection prediction results
 */
public class FallDetectionResult {
    private final int classIndex;
    private final String className;
    private final float confidence;
    private final float[] probabilities;

    public FallDetectionResult(int classIndex, String className, float confidence, float[] probabilities) {
        this.classIndex = classIndex;
        this.className = className;
        this.confidence = confidence;
        this.probabilities = probabilities;
    }

    public int getClassIndex() { return classIndex; }
    public String getClassName() { return className; }
    public float getConfidence() { return confidence; }
    public float[] getProbabilities() { return probabilities; }

    public boolean isFall() { return classIndex == 1; } // fall is index 1

    @Override
    public String toString() {
        return String.format("Prediction: %s (%.2f%% confidence)",
                className, confidence * 100);
    }
}