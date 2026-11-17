package io.github.kalpa0448.taskmgr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;

/**
 * 卡片毛玻璃：只模糊卡片覆盖的那块区域，卡片之外保持清晰。
 * 每次绘制按宿主在窗口中的位置重新取样，滚动时也能对齐。
 */
public class Frost extends Drawable {

    private final Bitmap backdrop;   // 整屏模糊图
    private final View host;
    private final float radius;
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final int[] loc = new int[2];

    public Frost(Bitmap backdrop, View host, float radius, int tintColor) {
        this.backdrop = backdrop;
        this.host = host;
        this.radius = radius;
        this.tintPaint.setColor(tintColor);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty() || backdrop == null) return;

        rect.set(b);
        path.reset();
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);

        windowPos(host, loc);

        int save = canvas.save();
        canvas.clipPath(path);
        canvas.translate(b.left - loc[0], b.top - loc[1]);
        canvas.drawBitmap(backdrop, 0f, 0f, bmpPaint);
        canvas.restoreToCount(save);

        int save2 = canvas.save();
        canvas.clipPath(path);
        canvas.drawRect(rect, tintPaint);
        canvas.restoreToCount(save2);
    }

    /** 逐级累加 left/top 与滚动量，得到相对窗口的稳定坐标 */
    private static void windowPos(View v, int[] out) {
        int x = 0, y = 0;
        View cur = v;
        while (cur != null) {
            x += cur.getLeft() - cur.getScrollX();
            y += cur.getTop() - cur.getScrollY();
            ViewParent p = cur.getParent();
            cur = (p instanceof View) ? (View) p : null;
        }
        int[] root = new int[2];
        v.getRootView().getLocationInWindow(root);
        out[0] = x + root[0];
        out[1] = y + root[1];
    }

    @Override
    public void setAlpha(int alpha) {
        bmpPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        bmpPaint.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
