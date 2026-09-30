package com.example.dms_appdetect;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class FaceFeatureExtractorTest {
    @Test
    public void pitchSignMatchesObservedUpAndDownMovements() {
        float[] matrix = new float[16];
        matrix[0] = 1f;
        matrix[5] = 0.8660254f;
        matrix[10] = 0.8660254f;
        matrix[15] = 1f;

        matrix[6] = -0.5f;
        matrix[9] = 0.5f;
        assertEquals(30.0, FaceFeatureExtractor.pitchDegreesFromMatrix(matrix), 0.1);

        matrix[6] = 0.5f;
        matrix[9] = -0.5f;
        assertEquals(-30.0, FaceFeatureExtractor.pitchDegreesFromMatrix(matrix), 0.1);
    }
}