package io.github.kalpa0448.taskmgr;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** 应用内裁切背景 */
public class CropActivity extends Activity {

    private CropView cropView;
    private TextView degLabel;
    private SeekBar degBar;
    private Bitmap source;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF101012);
        getWindow().setNavigationBarColor(0xFF101012);

        Uri uri = getIntent().getData();
        source = decode(uri);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101012);

        // ---- 顶栏 ----
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(10), dp(10), dp(10));

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        close.setTextColor(0xFFEDEDED);
        close.setPadding(dp(14), dp(6), dp(14), dp(6));
        close.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        top.addView(close);

        TextView title = new TextView(this);
        title.setText(T.s("裁切背景"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTextColor(0xFFEDEDED);
        title.setPadding(dp(10), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView reset = new TextView(this);
        reset.setText("⟳");
        reset.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        reset.setTextColor(0xFFEDEDED);
        reset.setPadding(dp(14), dp(4), dp(14), dp(4));
        reset.setOnClickListener(v -> {
            if (cropView != null) cropView.resetTransform();
            if (degBar != null) degBar.setProgress(0);
        });
        top.addView(reset);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        // ---- 裁切区 ----
        FrameLayout holder = new FrameLayout(this);
        cropView = new CropView(this, null);
        holder.addView(cropView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(holder, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---- 底部：旋转 + 应用 ----
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(18), dp(10), dp(18), dp(18));

        LinearLayout rotRow = new LinearLayout(this);
        rotRow.setOrientation(LinearLayout.HORIZONTAL);
        rotRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView rotLabel = new TextView(this);
        rotLabel.setText(T.s("旋转角度"));
        rotLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        rotLabel.setTextColor(0xFFB9B9B9);
        rotRow.addView(rotLabel);

        degBar = new SeekBar(this);
        degBar.setMax(360);
        degBar.setProgress(0);
        degBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                degLabel.setText(p + "°");
                if (cropView != null) cropView.setRotationDeg(p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        rotRow.addView(degBar, new LinearLayout.LayoutParams(0, -2, 1f));

        degLabel = new TextView(this);
        degLabel.setText("0°");
        degLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        degLabel.setTextColor(0xFFEDEDED);
        degLabel.setPadding(dp(8), 0, 0, 0);
        rotRow.addView(degLabel);
        bottom.addView(rotRow);

        Button apply = new Button(this);
        apply.setText("✓  " + T.s("应用"));
        apply.setAllCaps(false);
        apply.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        apply.setTextColor(0xFF101012);
        apply.setTypeface(Typeface.DEFAULT_BOLD);
        apply.setBackground(round(0xFFB9C6FF, dp(24)));
        apply.setOnClickListener(v -> apply());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(46));
        alp.topMargin = dp(6);
        bottom.addView(apply, alp);

        root.addView(bottom, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);

        if (source != null) cropView.setBitmap(source);
        else {
            Toast.makeText(this, T.s("读取失败"), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private android.graphics.drawable.GradientDrawable round(int color, float r) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(r);
        return d;
    }

    private void apply() {
        try {
            Bitmap out = cropView.renderCrop();
            if (out == null) {
                Toast.makeText(this, T.s("导出失败"), Toast.LENGTH_SHORT).show();
                return;
            }
            File dst = new File(getFilesDir(), "bg.png");
            try (FileOutputStream fos = new FileOutputStream(dst)) {
                out.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            out.recycle();
            setResult(RESULT_OK);
            finish();
        } catch (Throwable t) {
            Toast.makeText(this, T.s("导出失败") + t, Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap decode(Uri uri) {
        try {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, probe);
            }
            int scale = 1;
            while (probe.outWidth / scale > 2200) scale *= 2;
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = Math.max(1, scale);
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, opt);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
