package io.github.kalpa0448.taskmgr;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;

/**
 * 卡片毛玻璃（卡片锚定版）。
 *
 * 取样锚点用卡片在**内容坐标系**里的位置（不含滚动量），
 * 所以图案是「长在卡片上」的：卡片滚到哪，图案跟到哪，完全同步。
 *
 * 因为结果不随滚动变化，显示列表缓存不会造成滞后或错位。
 */
public class Frost extends Drawable {

    private final View host;
    private final float radius;
    private final BitmapShader shader;
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();

    public Frost(Bitmap backdrop, View host, float radius, int tintColor) {
        this.host = host;
        this.radius = radius;
        this.shader = new BitmapShader(backdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(shader);
        paint.setDither(true);
        paint.setAlpha(255);
        this.tintColor = tintColor;
    }

    private final int tintColor;

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;

        // 卡片在内容坐标中的位置（逐级累加 left/top，不含 scroll）
        int x = 0, y = 0;
        View cur = host;
        while (cur != null) {
            x += cur.getLeft();
            y += cur.getTop();
            ViewParent p = cur.getParent();
            cur = (p instanceof View) ? (View) p : null;
        }

        matrix.setTranslate(x - b.left, y - b.top);
        shader.setLocalMatrix(matrix);

        rect.set(b);
        canvas.drawRoundRect(rect, radius, radius, paint);

        // 叠半透明底色，保证文字可读
        paint.setShader(null);
        paint.setColor(tintColor);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setShader(shader);
    }

    @Override
    public void setAlpha(int alpha) { }

    @Override
    public void setColorFilter(ColorFilter cf) { }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
