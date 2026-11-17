package io.github.kalpa0448.taskmgr;

import android.content.Context;
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
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

/**
 * 带「卡片毛玻璃」的滚动容器。
 *
 * 关键：毛玻璃画在**背景层**里。
 * View 的绘制顺序是「先画背景（视口坐标，不随滚动移动）→ 再平移画布画内容」，
 * 所以放在背景层里的模糊图天然固定在屏幕上，卡片滚过时只是透过不同区域看它。
 *
 * 之前放在 dispatchDraw 里是错的：那时画布已经被平移了 -scrollY，
 * 结果模糊跟着内容一起滚。
 */
public class FrostScrollView extends ScrollView {

    /** 标记：这个 View 是卡片，值为圆角半径(px) */
    public static final int TAG_FROST = 0x0F050001;

    private FrostBg frostBg;

    public FrostScrollView(Context c) {
        super(c);
    }

    public FrostScrollView(Context c, AttributeSet a) {
        super(c, a);
    }

    /** 传入整屏模糊图；传 null 表示关闭 */
    public void setFrostBackdrop(Bitmap bm) {
        if (bm == null) {
            if (frostBg != null) {
                frostBg = null;
                setBackground(null);
            }
            return;
        }
        frostBg = new FrostBg(this, bm);
        setBackground(frostBg);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        // 滚动时卡片位置变化，需要重画背景层
        if (frostBg != null) invalidate();
    }

    /** 只负责「把模糊图铺在卡片矩形上」的背景 */
    private static class FrostBg extends Drawable {

        private final FrostScrollView host;
        private final Bitmap backdrop;
        private final BitmapShader shader;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Matrix matrix = new Matrix();
        private final RectF rect = new RectF();
        private final Rect node = new Rect();
        private final int[] loc = new int[2];

        FrostBg(FrostScrollView host, Bitmap bm) {
            this.host = host;
            this.backdrop = bm;
            this.shader = new BitmapShader(bm, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            paint.setShader(shader);
            paint.setDither(true);
        }

        @Override
        public void draw(Canvas canvas) {
            // 背景层画布没有滚动偏移，直接用视口坐标即可
            host.getLocationInWindow(loc);
            // 关键：纵向额外加上 scrollY
            // → 取样锚点跟着内容一起走，卡片和模糊是一体的（而不是钉在屏幕上）
            matrix.setTranslate(-loc[0], -loc[1] + host.getScrollY());
            shader.setLocalMatrix(matrix);

            View content = host.getChildAt(0);
            if (content != null) drawCards(canvas, content);
        }

        private void drawCards(Canvas canvas, View v) {
            if (v.getVisibility() != View.VISIBLE) return;

            Object tag = v.getTag(TAG_FROST);
            if (tag instanceof Float && v.getWidth() > 0 && v.getHeight() > 0) {
                node.set(0, 0, v.getWidth(), v.getHeight());
                // offsetDescendantRectToMyCoords 已包含滚动量，不要再手动加
                host.offsetDescendantRectToMyCoords(v, node);
                if (node.bottom > 0 && node.top < host.getHeight()
                        && node.right > 0 && node.left < host.getWidth()) {
                    rect.set(node);
                    float r = (Float) tag;
                    canvas.drawRoundRect(rect, r, r, paint);
                }
            }

            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) drawCards(canvas, g.getChildAt(i));
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
