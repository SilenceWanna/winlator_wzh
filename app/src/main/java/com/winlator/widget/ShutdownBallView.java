package com.winlator.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.winlator.R;
import com.winlator.core.UnitUtils;
import com.winlator.math.Mathf;

public class ShutdownBallView extends FrameLayout {
    private final SharedPreferences preferences;
    private boolean restoreSavedPosition = true;
    private short lastX = 0;
    private short lastY = 0;
    private Runnable onClickCallback;

    public ShutdownBallView(Context context) {
        this(context, null);
    }

    public ShutdownBallView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ShutdownBallView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ShutdownBallView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View contentView = LayoutInflater.from(context).inflate(R.layout.shutdown_ball_view, this, false);

        final float dragThreshold = UnitUtils.dpToPx(8);
        final PointF startPoint = new PointF();
        final boolean[] dragging = {false};

        contentView.findViewById(R.id.BTShutdown).setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startPoint.x = event.getX();
                    startPoint.y = event.getY();
                    dragging[0] = false;
                    break;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getX() - startPoint.x;
                    float dy = event.getY() - startPoint.y;
                    if (!dragging[0] && Math.hypot(dx, dy) > dragThreshold) dragging[0] = true;
                    if (dragging[0]) movePanel(getX() + dx, getY() + dy);
                    break;
                }
                case MotionEvent.ACTION_UP:
                    if (dragging[0]) {
                        if (lastX > 0 && lastY > 0) preferences.edit().putString("shutdown_ball_view", lastX + "|" + lastY).apply();
                    }
                    else if (onClickCallback != null) {
                        onClickCallback.run();
                    }
                    lastX = 0;
                    lastY = 0;
                    dragging[0] = false;
                    break;
            }
            return true;
        });

        addView(contentView);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (restoreSavedPosition) {
            float x = 1e6f;
            float y = 1e6f;

            String config = preferences.getString("shutdown_ball_view", null);
            if (config != null) {
                try {
                    String[] parts = config.split("\\|");
                    x = Short.parseShort(parts[0]);
                    y = Short.parseShort(parts[1]);
                }
                catch (NumberFormatException e) {}
            }

            movePanel(x, y);
            restoreSavedPosition = false;
        }
    }

    private void movePanel(float x, float y) {
        final int padding = (int)UnitUtils.dpToPx(8);
        ViewGroup parent = (ViewGroup)getParent();
        if (parent == null) return;
        int width = getWidth();
        int height = getHeight();
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        x = Mathf.clamp(x, padding, parentWidth - padding - width);
        y = Mathf.clamp(y, padding, parentHeight - padding - height);
        setX(x);
        setY(y);
        lastX = (short)x;
        lastY = (short)y;
    }

    public void setOnClickCallback(Runnable onClickCallback) {
        this.onClickCallback = onClickCallback;
    }
}
