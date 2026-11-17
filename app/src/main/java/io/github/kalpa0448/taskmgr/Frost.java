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

/**
 * 毛玻璃卡片背景。
 *
 * 原理：拿一张「整屏模糊图」，按卡片在窗口里的位置取样绘制，
 * 再裁成圆角 + 叠一层半透明色。这样卡片区域看起来就像是
 * 把背后的画面做了模糊，而卡片之外仍然是清晰的背景图。
 *
 * 滚动时卡片位置会变，所以每次 draw 都重新取一次坐标。
 */
public class Frost extends Drawable {

    private final Bitmap backdrop;
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

        // 宿主在窗口中的位置（含滚动偏移）
        host.getLocationInWindow(loc);

        // 1) 画背后那片模糊图，按窗口坐标对齐
        int save = canvas.save();
        canvas.clipPath(path);
        canvas.translate(b.left - loc[0], b.top - loc[1]);
        canvas.drawBitmap(backdrop, 0f, 0f, bmpPaint);
        canvas.restoreToCount(save);

        // 2) 叠半透明底色，保证文字可读
        int save2 = canvas.save();
        canvas.clipPath(path);
        canvas.drawRect(rect, tintPaint);
        canvas.restoreToCount(save2);
    }

    public void setTint(int color) {
        tintPaint.setColor(color);
        invalidateSelf();
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
