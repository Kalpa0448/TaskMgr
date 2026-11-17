package io.github.kalpa0448.taskmgr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

/**
 * 带「卡片毛玻璃」的滚动容器。
 *
 * 关键点：
 * 1. 在 dispatchDraw 里、子视图之前绘制，随滚动帧同步重绘，不存在缓存延迟；
 * 2. 用 BitmapShader + drawRoundRect 画，不用 clipPath，避免硬件加速下出现条纹；
 * 3. 用 offsetDescendantRectToMyCoords 换算卡片位置，自动带上滚动偏移。
 */
public class FrostScrollView extends ScrollView {

    /** 标记：这个 View 是卡片，需要铺毛玻璃（值为圆角半径 px） */
    public static final int TAG_FROST = 0x0F050001;

    private Bitmap backdrop;
    private BitmapShader shader;
    private final Matrix shaderMatrix = new Matrix();
    private final Paint frostPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect tmp = new Rect();
    private final RectF cardRect = new RectF();
    private final int[] loc = new int[2];
    private int winX, winY;      // 本容器在窗口中的位置（滚动时不变，缓存下来避免滞后）

    public FrostScrollView(Context c) {
        super(c);
        frostPaint.setFilterBitmap(true);
        frostPaint.setDither(true);
    }

    public FrostScrollView(Context c, AttributeSet a) {
        super(c, a);
        frostPaint.setFilterBitmap(true);
        frostPaint.setDither(true);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        getLocationInWindow(loc);
        winX = loc[0];
        winY = loc[1];
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getLocationInWindow(loc);
        winX = loc[0];
        winY = loc[1];
    }

    public void setBackdrop(Bitmap bm) {
        backdrop = bm;
        shader = (bm == null) ? null : new BitmapShader(bm, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        frostPaint.setShader(shader);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (backdrop != null && shader != null) {
            // 位图与窗口对齐：用缓存的窗口位置，避免滚动时取到上一帧的值
            shaderMatrix.setTranslate(-winX, -winY);
            shader.setLocalMatrix(shaderMatrix);

            View content = getChildAt(0);
            if (content != null) drawCards(canvas, content);
        }
        super.dispatchDraw(canvas);
    }

    private void drawCards(Canvas canvas, View v) {
        if (v.getVisibility() != View.VISIBLE) return;

        Object tag = v.getTag(TAG_FROST);
        if (tag instanceof Float && v.getWidth() > 0 && v.getHeight() > 0) {
            // 用系统方法换算到本容器坐标（自带滚动偏移）
            tmp.set(0, 0, v.getWidth(), v.getHeight());
            try {
                offsetDescendantRectToMyCoords(v, tmp);
            } catch (Throwable ignored) {
                return;
            }
            if (tmp.bottom > 0 && tmp.top < getHeight()) {
                cardRect.set(tmp);
                canvas.drawRoundRect(cardRect, (Float) tag, (Float) tag, frostPaint);
            }
        }

        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) drawCards(canvas, g.getChildAt(i));
        }
    }
}
