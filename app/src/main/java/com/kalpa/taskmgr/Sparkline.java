package com.kalpa.taskmgr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** 极简折线图：固定窗口 0..100，右侧为最新值 */
public class Sparkline extends View {

    private final float[] data;
    private int head = 0;      // 下一个写入位置
    private int count = 0;
    private int maxPoints;     // 最多绘制多少个点（可配置）
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path area = new Path();

    public Sparkline(Context c, int color, int capacity) {
        super(c);
        data = new float[capacity];
        maxPoints = capacity;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(1.6f));
        line.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor((color & 0x00FFFFFF) | 0x33000000);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(dp(1));
        grid.setColor(0xFF35353A);
    }

    public void push(float v) {
        data[head] = Math.max(0, Math.min(100, v));
        head = (head + 1) % data.length;
        if (count < data.length) count++;
        invalidate();
    }

    public void setMaxPoints(int n) {
        maxPoints = Math.max(2, Math.min(n, data.length));
        invalidate();
    }

    public void clear() {
        count = 0;
        head = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        // 25 / 50 / 75 网格
        for (int i = 1; i <= 3; i++) {
            float y = h - h * (i / 4f);
            c.drawLine(0, y, w, y, grid);
        }
        int drawN = Math.min(count, maxPoints);
        if (drawN < 2) return;

        int cap = data.length;
        float stepX = (float) w / (cap - 1);
        // 让最新样本贴右边界：起始索引 = head（最老样本）
        int start = (head - drawN + cap) % cap;

        path.reset();
        area.reset();
        boolean first = true;
        for (int i = 0; i < drawN; i++) {
            float v = data[(start + i) % cap];
            float x = w - (drawN - 1 - i) * stepX;
            float y = h - (v / 100f) * h;
            if (first) {
                path.moveTo(x, y);
                area.moveTo(x, h);
                area.lineTo(x, y);
                first = false;
            } else {
                path.lineTo(x, y);
                area.lineTo(x, y);
            }
        }
        area.lineTo(w, h);
        area.close();
        c.drawPath(area, fill);
        c.drawPath(path, line);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
