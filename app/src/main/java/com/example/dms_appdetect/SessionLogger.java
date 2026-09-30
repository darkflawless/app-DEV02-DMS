package com.example.dms_appdetect;

import android.content.Context;
import android.util.Log;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

/** Local, private CSV; no camera frames or audio are saved. */
public final class SessionLogger {
    private static final String TAG = "DmsSessionLogger";
    private BufferedWriter writer;

    public synchronized void start(Context context) {
        close();
        File file = new File(context.getFilesDir(),
                "dms_" + System.currentTimeMillis() + ".csv");
        try {
            writer = new BufferedWriter(new FileWriter(file));
            writer.write("config,timestamp_ms,face,eye_valid,mouth_valid,pose_valid,"
                    + "ear_left,ear_right,mar,pitch,yaw,roll,luma,state,coverage,closed_ratio,processing_ms,phase,eye_threshold\n");
        } catch (IOException e) {
            Log.e(TAG, "Cannot open session log", e);
            writer = null;
        }
    }

    public synchronized void write(FeatureSample s, DmsDecision d, long processingMs, String phase, double eyeThreshold) {
        if (writer == null) return;
        try {
            writer.write(String.format(Locale.US,
                    "%s,%d,%b,%b,%b,%b,%.4f,%.4f,%.4f,%.2f,%.2f,%.2f,%.1f,%s,%.3f,%.3f,%d,%s,%.4f%n",
                    DmsConfig.VERSION, s.timestampMs, s.facePresent, s.eyeValid,
                    s.mouthValid, s.poseValid, s.earLeft, s.earRight, s.mar,
                    s.pitch, s.yaw, s.roll, s.luminance, d.state.name(),
                    d.eyeCoverage, d.closedRatio, processingMs, phase, eyeThreshold));
        } catch (IOException e) {
            Log.e(TAG, "Cannot write session log", e);
            close();
        }
    }

    public synchronized void close() {
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException e) {
            Log.e(TAG, "Cannot close session log", e);
        }
        writer = null;
    }
}
