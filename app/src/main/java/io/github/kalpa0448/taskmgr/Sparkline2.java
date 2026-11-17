package io.github.kalpa0448.taskmgr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * 双序列折线图：同一坐标系里画两条曲线，右侧为最新值。
 * 用于磁盘「读 / 写」与网络「下行 / 上行」。
 */
public class Sparkline2 extends View {

    private final float[] a, b;          // 两条序列
    private int head = 0;
    private int count = 0;
    private int maxPoints;

    private final Paint lineA = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineB = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillA = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillB = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path pathA = new Path(), pathB = new Path();
    private final Path areaA = new Path(), areaB = new Path();

    public Sparkline2(Context c, int colorA, int colorB, int capacity) {
        super(c);
        a = new float[capacity];
        b = new float[capacity];
        maxPoints = capacity;

        init(lineA, colorA);
        init(lineB, colorB);
        fillA.setStyle(Paint.Style.FILL);
        fillA.setColor((colorA & 0x00FFFFFF) | 0x22000000);
        fillB.setStyle(Paint.Style.FILL);
        fillB.setColor((colorB & 0x00FFFFFF) | 0x22000000);

        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(dp(1));
        grid.setColor(0xFF35353A);
    }

    private void init(Paint p, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(1.6f));
        p.setColor(color);
    }

    /** 同时写入两个值（0~100） */
    public void push2(float va, float vb) {
        a[head] = clamp(va);
        b[head] = clamp(vb);
        head = (head + 1) % a.length;
        if (count < a.length) count++;
        invalidate();
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(100, v));
    }

    public void setMaxPoints(int n) {
        maxPoints = Math.max(2, Math.min(n, a.length));
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

        for (int i = 1; i <= 3; i++) {
            float y = h - h * (i / 4f);
            c.drawLine(0, y, w, y, grid);
        }
        int drawN = Math.min(count, maxPoints);
        if (drawN < 2) return;

        int cap = a.length;
        float stepX = (float) w / (cap - 1);
        int start = (head - drawN + cap) % cap;

        build(pathA, areaA, a, start, drawN, cap, stepX, w, h);
        build(pathB, areaB, b, start, drawN, cap, stepX, w, h);

        c.drawPath(areaA, fillA);
        c.drawPath(areaB, fillB);
        c.drawPath(pathA, lineA);
        c.drawPath(pathB, lineB);
    }

    private void build(Path line, Path area, float[] data, int start, int drawN,
                       int cap, float stepX, int w, int h) {
        line.reset();
        area.reset();
        boolean first = true;
        for (int i = 0; i < drawN; i++) {
            float v = data[(start + i) % cap];
            float x = w - (drawN - 1 - i) * stepX;
            float y = h - (v / 100f) * h;
            if (first) {
                line.moveTo(x, y);
                area.moveTo(x, h);
                area.lineTo(x, y);
                first = false;
            } else {
                line.lineTo(x, y);
                area.lineTo(x, y);
            }
        }
        area.lineTo(w, h);
        area.close();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
