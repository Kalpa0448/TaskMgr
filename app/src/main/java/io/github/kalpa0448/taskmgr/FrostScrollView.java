package io.github.kalpa0448.taskmgr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;

/**
 * 带「卡片毛玻璃」的滚动容器。
 *
 * 做法：在 dispatchDraw 里、子视图绘制之前，把整屏模糊图按每张卡片的位置裁剪着画一遍。
 * 因为这段代码属于 ScrollView 自身的绘制，滚动导致 invalidate 时必然重新执行，
 * 所以不会出现「模糊跟着卡片跑」或「滚动后延迟一秒才上模糊」的问题。
 */
public class FrostScrollView extends ScrollView {

    /** 标记：这个 View 是卡片，需要铺毛玻璃 */
    public static final int TAG_FROST = 0x0F050001;

    private Bitmap backdrop;
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final RectF card = new RectF();
    private final int[] loc = new int[2];

    public FrostScrollView(Context c) {
        super(c);
    }

    public FrostScrollView(Context c, AttributeSet a) {
        super(c, a);
    }

    public void setBackdrop(Bitmap bm) {
        backdrop = bm;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (backdrop != null) drawFrost(canvas, getChildAt(0));
        super.dispatchDraw(canvas);
    }

    private void drawFrost(Canvas canvas, View content) {
        if (content == null) return;
        getLocationInWindow(loc);
        int scrollY = getScrollY();
        drawFor(canvas, content, content, scrollY);
    }

    private void drawFor(Canvas canvas, View v, View content, int scrollY) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v.getTag(TAG_FROST) != null) {
            float radius = 11f * getResources().getDisplayMetrics().density;
            Object r = v.getTag(TAG_FROST);
            if (r instanceof Float) radius = (Float) r;
            paintCard(canvas, v, content, scrollY, radius);
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                drawFor(canvas, g.getChildAt(i), content, scrollY);
            }
        }
    }

    private void paintCard(Canvas canvas, View v, View content, int scrollY, float radius) {
        if (v.getWidth() <= 0 || v.getHeight() <= 0) return;
        cardRect(v, content, scrollY);
        // 视野外跳过
        if (card.bottom < 0 || card.top > getHeight()) return;

        path.reset();
        path.addRoundRect(card, radius, radius, Path.Direction.CW);

        int save = canvas.save();
        canvas.clipPath(path);
        // 让模糊图与窗口对齐：把画布搬到窗口原点
        canvas.translate(-loc[0], -loc[1]);
        canvas.drawBitmap(backdrop, 0f, 0f, bmpPaint);
        canvas.restoreToCount(save);
    }

    /** 累加各级 getLeft/getTop，得到卡片在「滚动容器内容坐标系」里的位置 */
    private void cardRect(View v, View content, int scrollY) {
        float x = 0, y = 0;
        View cur = v;
        while (cur != null && cur != this) {
            x += cur.getLeft();
            y += cur.getTop();
            if (cur == content) {
                x -= content.getLeft();
                y += content.getTop() - scrollY;
                break;
            }
            ViewParent p = cur.getParent();
            cur = (p instanceof View) ? (View) p : null;
        }
        rect.set(0, 0, 0, 0);
        card.set(x, y, x + v.getWidth(), y + v.getHeight());
    }
}
