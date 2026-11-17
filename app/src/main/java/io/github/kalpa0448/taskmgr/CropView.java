package io.github.kalpa0448.taskmgr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 应用内裁切视图。
 * 固定裁切框（屏幕比例），图片可拖动、双指缩放、滑杆旋转。
 */
public class CropView extends View {

    private Bitmap bmp;
    private final Matrix matrix = new Matrix();
    private final Matrix baseMatrix = new Matrix();
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF crop = new RectF();
    private float rotation = 0f;
    private float minScale = 1f, curScale = 1f;

    private float lastX, lastY;
    private float downX, downY;
    private float startDist = 0f;
    private float startScale = 1f;
    private boolean twoFinger = false;

    public CropView(Context c, AttributeSet a) {
        super(c, a);
        maskPaint.setColor(0xB3000000);
        gridPaint.setColor(0x88FFFFFF);
        gridPaint.setStrokeWidth(dp(0.8f));
        edgePaint.setColor(0xFFFFFFFF);
        edgePaint.setStrokeWidth(dp(1.5f));
        edgePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setColor(0xFFFFFFFF);
        handlePaint.setStrokeWidth(dp(3f));
        handlePaint.setStrokeCap(Paint.Cap.SQUARE);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setBitmap(Bitmap b) {
        bmp = b;
        layoutCrop();
        resetTransform();
        invalidate();
    }

    private void layoutCrop() {
        // 裁切框：屏幕比例的竖长方形，居中，上下留白
        float w = getWidth(), h = getHeight();
        float screenRatio = (float) getResources().getDisplayMetrics().heightPixels
                / getResources().getDisplayMetrics().widthPixels;
        float frameH = h * 0.78f;
        float frameW = frameH / screenRatio;
        if (frameW > w * 0.92f) {
            frameW = w * 0.92f;
            frameH = frameW * screenRatio;
        }
        float left = (w - frameW) / 2f;
        float top = (h - frameH) / 2f;
        crop.set(left, top, left + frameW, top + frameH);
    }

    /** 复位：图片铺满裁切框 */
    public void resetTransform() {
        if (bmp == null || crop.isEmpty()) return;
        rotation = 0f;
        float s = Math.max(crop.width() / bmp.getWidth(), crop.height() / bmp.getHeight());
        minScale = s;
        curScale = s;
        baseMatrix.reset();
        baseMatrix.postScale(s, s);
        baseMatrix.postTranslate(
                crop.centerX() - bmp.getWidth() * s / 2f,
                crop.centerY() - bmp.getHeight() * s / 2f);
        matrix.set(baseMatrix);
        invalidate();
    }

    public void setRotationDeg(float deg) {
        rotation = deg;
        rebuild();
    }

    private void rebuild() {
        if (bmp == null) return;
        // 以裁切框中心为轴旋转，再按当前缩放重算平移
        float s = Math.max(curScale, minScale);
        matrix.set(baseMatrix);
        matrix.postRotate(rotation, crop.centerX(), crop.centerY());
        // 保证放大倍数
        matrix.postScale(s / scaleOf(baseMatrix), s / scaleOf(baseMatrix), crop.centerX(), crop.centerY());
        clamp();
        invalidate();
    }

    private float scaleOf(Matrix m) {
        float[] v = new float[9];
        m.getValues(v);
        return v[Matrix.MSCALE_X];
    }

    private void clamp() {
        if (bmp == null) return;
        RectF b = new RectF(0, 0, bmp.getWidth(), bmp.getHeight());
        matrix.mapRect(b);
        float dx = 0, dy = 0;
        if (b.left > crop.left) dx = crop.left - b.left;
        if (b.right < crop.right) dx = crop.right - b.right;
        if (b.top > crop.top) dy = crop.top - b.top;
        if (b.bottom < crop.bottom) dy = crop.bottom - b.bottom;
        if (dx != 0 || dy != 0) matrix.postTranslate(dx, dy);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layoutCrop();
        if (bmp != null) resetTransform();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bmp == null) return;
        canvas.drawBitmap(bmp, matrix, bmpPaint);

        // 框外压暗：用四条边遮罩，避免整块盖住
        canvas.drawRect(0, 0, getWidth(), crop.top, maskPaint);
        canvas.drawRect(0, crop.bottom, getWidth(), getHeight(), maskPaint);
        canvas.drawRect(0, crop.top, crop.left, crop.bottom, maskPaint);
        canvas.drawRect(crop.right, crop.top, getWidth(), crop.bottom, maskPaint);

        // 三分线
        for (int i = 1; i <= 2; i++) {
            float x = crop.left + crop.width() * i / 3f;
            float y = crop.top + crop.height() * i / 3f;
            canvas.drawLine(x, crop.top, x, crop.bottom, gridPaint);
            canvas.drawLine(crop.left, y, crop.right, y, gridPaint);
        }

        // 四角 L 形把手
        float L = dp(22);
        drawCorner(canvas, crop.left, crop.top, L, 1, 1);
        drawCorner(canvas, crop.right, crop.top, L, -1, 1);
        drawCorner(canvas, crop.left, crop.bottom, L, 1, -1);
        drawCorner(canvas, crop.right, crop.bottom, L, -1, -1);
    }

    private void drawCorner(Canvas c, float x, float y, float len, int sx, int sy) {
        c.drawLine(x, y, x + len * sx, y, handlePaint);
        c.drawLine(x, y, x, y + len * sy, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                downX = lastX;
                downY = lastY;
                twoFinger = false;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() >= 2) {
                    twoFinger = true;
                    startDist = dist(e);
                    startScale = curScale;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (twoFinger && e.getPointerCount() >= 2) {
                    float d = dist(e);
                    if (startDist > 0) {
                        float ns = startScale * (d / startDist);
                        ns = Math.max(minScale, Math.min(minScale * 6f, ns));
                        matrix.postScale(ns / curScale, ns / curScale, crop.centerX(), crop.centerY());
                        curScale = ns;
                        clamp();
                        invalidate();
                    }
                } else {
                    matrix.postTranslate(e.getX() - lastX, e.getY() - lastY);
                    clamp();
                    invalidate();
                }
                lastX = e.getX();
                lastY = e.getY();
                break;
            case MotionEvent.ACTION_POINTER_UP:
                twoFinger = false;
                lastX = e.getX(0);
                lastY = e.getY(0);
                break;
            case MotionEvent.ACTION_UP:
                break;
        }
        return true;
    }

    private float dist(MotionEvent e) {
        float dx = e.getX(0) - e.getX(1);
        float dy = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** 输出裁切结果 */
    public Bitmap renderCrop() {
        if (bmp == null || crop.isEmpty()) return null;
        int w = Math.round(crop.width());
        int h = Math.round(crop.height());
        if (w <= 0 || h <= 0) return null;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.translate(-crop.left, -crop.top);
        c.drawBitmap(bmp, matrix, bmpPaint);
        return out;
    }
}
