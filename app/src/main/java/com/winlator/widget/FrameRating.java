package com.winlator.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.winlator.R;
import com.winlator.box64.Box64Utils;
import com.winlator.core.CPUStatus;
import com.winlator.core.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class FrameRating extends FrameLayout implements Runnable {
    public enum Mode {DISABLED, SIMPLE, FULL}
    private long lastTime = 0;
    private long lastFrameTime = 0;
    private short frameCount = 0;
    private float lastFPS = 0;
    private final LinearLayout fpsPanel;
    private final LinearLayout gpuPanel;
    private final LinearLayout ramPanel;
    private final LinearLayout cpuPanel;
    private Mode mode = Mode.SIMPLE;
    private ActivityManager activityManager;
    private ActivityManager.MemoryInfo memoryInfo;
    private String cpuInfo = null;
    private byte tick = 0;
    private BufferedWriter samplingWriter;
    private long samplingStartElapsed;
    private long sampledRamUsedBytes;
    private int sampledCpuMaxMHz;
    private final ArrayList<Long> frameTimes = new ArrayList<>();

    public FrameRating(Context context) {
        this(context, null);
    }

    public FrameRating(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FrameRating(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        View view = LayoutInflater.from(context).inflate(R.layout.frame_rating, this, false);
        fpsPanel = view.findViewById(R.id.LLFPSPanel);
        gpuPanel = view.findViewById(R.id.LLGPUPanel);
        ramPanel = view.findViewById(R.id.LLRAMPanel);
        cpuPanel = view.findViewById(R.id.LLCPUPanel);
        addView(view);
        setupPanels();
    }

    private void setupPanels() {
        switch (mode) {
            case DISABLED:
                fpsPanel.setVisibility(GONE);
                gpuPanel.setVisibility(GONE);
                ramPanel.setVisibility(GONE);
                cpuPanel.setVisibility(GONE);

                activityManager = null;
                memoryInfo = null;
                break;
            case SIMPLE:
                fpsPanel.setVisibility(VISIBLE);
                gpuPanel.setVisibility(GONE);
                ramPanel.setVisibility(GONE);
                cpuPanel.setVisibility(GONE);

                activityManager = null;
                memoryInfo = null;
                break;
            case FULL:
                fpsPanel.setVisibility(VISIBLE);
                gpuPanel.setVisibility(VISIBLE);
                ramPanel.setVisibility(VISIBLE);
                cpuPanel.setVisibility(VISIBLE);

                Context context = getContext();
                activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
                memoryInfo = new ActivityManager.MemoryInfo();
                break;
        }
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        Mode previousMode = this.mode;
        this.mode = mode;
        setupPanels();
        if (mode == Mode.FULL && previousMode != Mode.FULL) startSampling();
        else if (mode != Mode.FULL && previousMode == Mode.FULL) closeSampling();
    }

    public void setGPUInfo(String gpuInfo) {
        post(() -> ((TextView)gpuPanel.getChildAt(1)).setText(gpuInfo));
    }

    public synchronized void reset() {
        frameCount = 0;
        lastTime = SystemClock.elapsedRealtime();
        lastFrameTime = 0;
        lastFPS = 0;
        tick = 2;
        frameTimes.clear();
        samplingStartElapsed = lastTime;
        if (samplingWriter != null) writeSamplingLine("# window_reset,"+samplingStartElapsed);
    }

    public synchronized void update() {
        long time = SystemClock.elapsedRealtime();
        long frameTime = lastFrameTime > 0 ? time - lastFrameTime : 0;
        lastFrameTime = time;
        if (time >= lastTime + 500) {
            lastFPS = ((float)(frameCount * 1000) / (time - lastTime));
            post(this);
            lastTime = time;
            frameCount = 0;
        }

        frameCount++;
        if (samplingWriter != null) {
            if (frameTime > 0) frameTimes.add(frameTime);
            writeSamplingLine((time - samplingStartElapsed)+","+frameTime+","+lastFPS+","+sampledRamUsedBytes+","+sampledCpuMaxMHz);
        }
    }

    @Override
    public synchronized void run() {
        if (getVisibility() == GONE) setVisibility(View.VISIBLE);
        ((TextView)fpsPanel.getChildAt(1)).setText(String.format(Locale.ENGLISH, "%.1f", lastFPS));

        if (mode == Mode.FULL && ++tick >= 2) {
            tick = 0;
            activityManager.getMemoryInfo(memoryInfo);
            sampledRamUsedBytes = memoryInfo.totalMem - memoryInfo.availMem;
            String ramText = StringUtils.formatBytes(sampledRamUsedBytes, false)+"/"+StringUtils.formatBytes(memoryInfo.totalMem, false);
            ((TextView)ramPanel.getChildAt(1)).setText(ramText);

            if (cpuInfo == null) cpuInfo = "Box64 v"+Box64Utils.extractBinVersion(cpuPanel.getContext());

            short[] clockSpeeds = CPUStatus.getCurrentClockSpeeds();
            int maxClockSpeed = 0;
            for (short clockSpeed : clockSpeeds) maxClockSpeed = Math.max(maxClockSpeed, clockSpeed);
            sampledCpuMaxMHz = maxClockSpeed;
            ((TextView)cpuPanel.getChildAt(1)).setText(CPUStatus.formatClockSpeed(maxClockSpeed)+" | "+cpuInfo);
        }

        flushSampling();
    }

    public synchronized void close() {
        closeSampling();
    }

    private synchronized void startSampling() {
        if (samplingWriter != null) return;

        File parent = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Winlator/performance");
        if (!parent.isDirectory()) parent.mkdirs();

        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ENGLISH).format(new Date());
        for (int index = 0; index < 100 && samplingWriter == null; index++) {
            String suffix = index == 0 ? "" : "-"+index;
            File candidate = new File(parent, "frame-rating-"+timestamp+suffix+".csv");
            try {
                if (!candidate.createNewFile()) continue;
                samplingWriter = new BufferedWriter(new FileWriter(candidate, false));
                samplingStartElapsed = SystemClock.elapsedRealtime();
                samplingWriter.write("# winlator_frame_rating,version=1\n");
                samplingWriter.write("elapsed_ms,frame_time_ms,window_fps,ram_used_bytes,cpu_max_mhz\n");
                samplingWriter.flush();
            }
            catch (IOException error) {
                if (samplingWriter != null) {
                    try {
                        samplingWriter.close();
                    }
                    catch (IOException closeError) {
                        closeError.printStackTrace();
                    }
                    samplingWriter = null;
                }
                candidate.delete();
                error.printStackTrace();
            }
        }
    }

    private void writeSamplingLine(String line) {
        try {
            samplingWriter.write(line+"\n");
        }
        catch (IOException error) {
            closeSampling();
        }
    }

    private void flushSampling() {
        if (samplingWriter == null) return;
        try {
            samplingWriter.flush();
        }
        catch (IOException error) {
            closeSampling();
        }
    }

    private void closeSampling() {
        if (samplingWriter == null) return;
        try {
            writeSamplingSummary();
            samplingWriter.flush();
            samplingWriter.close();
        }
        catch (IOException error) {
            error.printStackTrace();
        }
        finally {
            samplingWriter = null;
        }
    }

    private void writeSamplingSummary() throws IOException {
        if (frameTimes.isEmpty()) {
            samplingWriter.write("# summary,frames=0\n");
            return;
        }

        long totalFrameTime = 0;
        for (long frameTime : frameTimes) totalFrameTime += frameTime;
        double averageFrameTime = (double)totalFrameTime / frameTimes.size();
        double averageFPS = 1000.0 / averageFrameTime;

        ArrayList<Double> frameRates = new ArrayList<>();
        for (long frameTime : frameTimes) frameRates.add(1000.0 / frameTime);
        Collections.sort(frameRates);
        int lowCount = Math.max(1, (int)Math.ceil(frameRates.size() * 0.01));
        double onePercentLowFPS = 0;
        for (int i = 0; i < lowCount; i++) onePercentLowFPS += frameRates.get(i);
        onePercentLowFPS /= lowCount;

        ArrayList<Long> sortedFrameTimes = new ArrayList<>(frameTimes);
        Collections.sort(sortedFrameTimes);
        int p99Index = Math.min(sortedFrameTimes.size()-1, (int)Math.ceil(sortedFrameTimes.size() * 0.99)-1);
        long p99FrameTime = sortedFrameTimes.get(p99Index);

        samplingWriter.write(String.format(Locale.ENGLISH, "# summary,frames=%d,average_fps=%.3f,one_percent_low_fps=%.3f,average_frame_time_ms=%.3f,p99_frame_time_ms=%d\n", frameTimes.size(), averageFPS, onePercentLowFPS, averageFrameTime, p99FrameTime));
    }
}
