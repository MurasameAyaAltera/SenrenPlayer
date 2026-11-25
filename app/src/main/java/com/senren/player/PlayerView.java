package com.senren.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.OverScroller;

/**
 * Seluruh antarmuka pemutar digambar langsung dengan Canvas pada "stage" 475 x (798+E)
 * (sama seperti versi HTML), lalu diskalakan agar pas dengan layar.
 */
public class PlayerView extends View {

    private static final int INK = 0xFF2A1409;
    private static final int ACCENT = 0xFFCF4A2A;
    private static final int LINE = 0xFFE2DDD6;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int ROW = 60;
    private static final float SW = 475f;
    private static final float SH = 798f;

    private static final int M_NONE = 0, M_BTN = 1, M_LIST = 2, M_SCROLL = 3,
            M_VOL = 4, M_ADD = 5, M_CLR = 6, M_DLG = 7;

    private static final String EMPTY_TEXT =
            "Belum ada lagu. Ketuk \u201CTambah lagu\u201D untuk memilih folder musik dari perangkatmu.";

    private static class Btn {
        final String id;
        final float x, y;
        final int w;
        final boolean bottom;
        final Bitmap n, p;

        Btn(String id, float x, float y, int w, boolean bottom, Bitmap n, Bitmap p) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.bottom = bottom;
            this.n = n;
            this.p = p;
        }

        float top(float e) {
            return bottom ? y + e : y;
        }
    }

    private static class Dlg {
        String text, yes, no;
        Runnable onYes;
        StaticLayout lay;
        final RectF card = new RectF();
        final RectF yesR = new RectF();
        final RectF noR = new RectF();
    }

    private final MainActivity host;

    private final Bitmap bgT, bgM, bgSb, frT, frM, frSb, strack, vtrack, thumb, thumbDn, rowN, rowP;
    private final Btn[] btns;

    private final Paint bmpP = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp18 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp15 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp16 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint btnPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path trash = new Path();
    private final RectF tmp = new RectF();
    private final RectF tmp2 = new RectF();

    private final OverScroller scroller;

    private float scale = 1f, offX = 0f, offY = 0f, E = 0f;
    private float scrollY = 0f;

    private int mode = M_NONE;
    private Btn pressed;
    private boolean pressedInside;
    private boolean addDown, clrDown, sThumbDown, vThumbDown;
    private float downX, downY, startScroll;
    private boolean dragging, lpFired;
    private int lpRow = -1;
    private VelocityTracker vt;
    private int lastCur = -2;
    private int dlgPress = 0;
    private Dlg dlg;

    private final Runnable lpRun = new Runnable() {
        @Override
        public void run() {
            if (mode == M_LIST && !dragging && lpRow >= 0 && lpRow < TrackStore.tracks.size()) {
                lpFired = true;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                askRemove(TrackStore.tracks.get(lpRow));
            }
        }
    };

    public PlayerView(Context ctx, MainActivity host) {
        super(ctx);
        this.host = host;
        scroller = new OverScroller(ctx);

        bgT = bmp(R.drawable.bg_t);
        bgM = bmp(R.drawable.bg_m);
        bgSb = bmp(R.drawable.bg_sb);
        frT = bmp(R.drawable.fr_t);
        frM = bmp(R.drawable.fr_m);
        frSb = bmp(R.drawable.fr_sb);
        strack = bmp(R.drawable.strack);
        vtrack = bmp(R.drawable.vtrack);
        thumb = vec(R.drawable.thumb, 34, 34);
        thumbDn = vec(R.drawable.thumb_dn, 34, 34);
        rowN = vec(R.drawable.row_n, 332, 38);
        rowP = vec(R.drawable.row_p, 332, 39);

        btns = new Btn[]{
                new Btn("up5", 385, 103, 40, false, vec(R.drawable.b_up5_n, 40, 40), vec(R.drawable.b_up5_p, 40, 40)),
                new Btn("up", 385, 149, 40, false, vec(R.drawable.b_up_n, 40, 40), vec(R.drawable.b_up_p, 40, 40)),
                new Btn("dn", 385, 534, 40, true, vec(R.drawable.b_dn_n, 40, 40), vec(R.drawable.b_dn_p, 40, 40)),
                new Btn("dn5", 385, 580, 40, true, vec(R.drawable.b_dn5_n, 40, 40), vec(R.drawable.b_dn5_p, 40, 40)),
                new Btn("prev", 61, 651, 46, true, vec(R.drawable.b_prev_n, 46, 46), vec(R.drawable.b_prev_p, 46, 46)),
                new Btn("stop", 122, 651, 46, true, vec(R.drawable.b_stop_n, 46, 46), vec(R.drawable.b_stop_p, 46, 46)),
                new Btn("play", 183, 651, 46, true, vec(R.drawable.b_play_n, 46, 46), vec(R.drawable.b_play_p, 46, 46)),
                new Btn("next", 244, 651, 46, true, vec(R.drawable.b_next_n, 46, 46), vec(R.drawable.b_next_p, 46, 46)),
                new Btn("loop", 315, 654, 40, true, vec(R.drawable.b_loop_n, 40, 40), vec(R.drawable.b_loop_p, 40, 40)),
                new Btn("shuf", 372, 654, 40, true, vec(R.drawable.b_shuf_n, 40, 40), vec(R.drawable.b_shuf_p, 40, 40)),
        };

        Typeface ref = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
        Typeface bold;
        try {
            bold = Typeface.createFromAsset(ctx.getAssets(), "fonts/SourceHanSansJP-Bold.otf");
        } catch (Exception e) {
            bold = ref;
        }
        tp18.setTypeface(bold);
        tp18.setTextSize(18f);
        tp15.setTypeface(bold);
        tp15.setTextSize(15f);
        tp16.setTypeface(bold);
        tp16.setTextSize(16f);
        btnPaint.setTypeface(bold);
        btnPaint.setTextSize(15f);
        btnPaint.setTextAlign(Paint.Align.CENTER);

        // koreksi posisi vertikal: font baru punya ascent lebih tinggi dari font bawaan
        cor18 = ascentGap(tp18, ref);
        cor15 = ascentGap(tp15, ref);
        cor16 = ascentGap(tp16, ref);

        stroke.setStyle(Paint.Style.STROKE);
        fill.setStyle(Paint.Style.FILL);

        // ikon tempat sampah (viewBox 24x24)
        trash.moveTo(4, 7);
        trash.lineTo(20, 7);
        trash.moveTo(10, 11);
        trash.lineTo(10, 17);
        trash.moveTo(14, 11);
        trash.lineTo(14, 17);
        trash.moveTo(6, 7);
        trash.lineTo(7, 19);
        trash.arcTo(new RectF(7, 17, 11, 21), 180, -90);
        trash.lineTo(15, 21);
        trash.arcTo(new RectF(13, 17, 17, 21), 90, -90);
        trash.lineTo(18, 7);
        trash.moveTo(9, 7);
        trash.lineTo(9, 4);
        trash.lineTo(15, 4);
        trash.lineTo(15, 7);
    }

    private float cor18, cor15, cor16;

    private static float ascentGap(TextPaint p, Typeface ref) {
        TextPaint r = new TextPaint(p);
        r.setTypeface(ref);
        return -p.getFontMetrics().ascent - (-r.getFontMetrics().ascent);
    }

    /** Bitmap vektor dirender VK kali ukuran desain supaya tetap tajam saat stage diskalakan. */
    private static final int VK = 3;

    /** Render drawable vektor (XML) ke Bitmap berukuran (w x h) * VK. w/h = ukuran desain dalam px stage. */
    private Bitmap vec(int id, int w, int h) {
        Drawable d = getResources().getDrawable(id, getContext().getTheme());
        Bitmap b = Bitmap.createBitmap(w * VK, h * VK, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        d.setBounds(0, 0, w * VK, h * VK);
        d.draw(cv);
        return b;
    }

    private Bitmap bmp(int id) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        return BitmapFactory.decodeResource(getResources(), id, o);
    }

    // ---------------------------------------------------------------- API untuk Activity / Service

    void refresh() {
        scrollY = clampS(scrollY);
        invalidate();
    }

    void onServiceChanged() {
        if (PlaybackService.cur != lastCur) {
            lastCur = PlaybackService.cur;
            if (lastCur >= 0) reveal(lastCur);
        }
        if (PlaybackService.error) {
            PlaybackService.error = false;
            notice("Lagu tidak dapat diputar. File mungkin sudah dipindahkan atau dihapus.");
        }
        invalidate();
    }

    boolean dialogOpen() {
        return dlg != null;
    }

    void closeDialog() {
        dlg = null;
        invalidate();
    }

    void notice(String text) {
        ask(text, "OK", null, null);
    }

    private void ask(String text, String yes, String no, Runnable onYes) {
        Dlg d = new Dlg();
        d.text = text;
        d.yes = yes;
        d.no = no;
        d.onYes = onYes;
        dlg = d;
        invalidate();
    }

    private void askRemove(final Track t) {
        ask("Hapus \u201C" + t.name + "\u201D dari daftar?", "Hapus", "Batal", new Runnable() {
            @Override
            public void run() {
                int i = TrackStore.tracks.indexOf(t);
                if (i >= 0) host.removeTrack(i);
            }
        });
    }

    private void askClear() {
        ask("Kosongkan seluruh daftar lagu (" + TrackStore.tracks.size() + " lagu)?",
                "Kosongkan", "Batal", new Runnable() {
                    @Override
                    public void run() {
                        host.clearAll();
                    }
                });
    }

    // ---------------------------------------------------------------- ukuran & skala

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float s = Math.min(w / SW, 1.6f);
        float e = Math.max(0f, (float) Math.floor(h / s - SH));
        if (h / s < SH) {
            s = h / SH;
            e = 0f;
        }
        scale = s;
        E = e;
        offX = (w - SW * s) / 2f;
        offY = (h - (SH + E) * s) / 2f;
        scrollY = clampS(scrollY);
    }

    private int fillerCount() {
        int n = TrackStore.tracks.size();
        int rows = (int) Math.floor((540f + E) / ROW);
        return Math.max(0, rows - (n == 0 ? 2 : n));
    }

    private float contentHeight() {
        int n = TrackStore.tracks.size();
        return (n == 0 ? 120 : n * ROW) + fillerCount() * ROW;
    }

    private float maxScroll() {
        return Math.max(0f, contentHeight() - (540f + E));
    }

    private float clampS(float v) {
        return Math.max(0f, Math.min(maxScroll(), v));
    }

    private void smoothBy(int dy) {
        int from = (int) scrollY;
        int to = (int) clampS(from + dy);
        if (to == from) return;
        scroller.startScroll(0, from, 0, to - from, 250);
        invalidate();
    }

    private void reveal(int i) {
        float t = i * ROW;
        float viewH = 540f + E;
        if (t < scrollY) {
            smoothBy((int) (t - scrollY));
        } else if (t + ROW > scrollY + viewH) {
            smoothBy((int) (t + ROW - viewH - scrollY));
        }
    }

    // ---------------------------------------------------------------- menggambar

    private RectF rf(float l, float t, float r, float b) {
        tmp.set(l, t, r, b);
        return tmp;
    }

    private void tiles(Canvas c, Bitmap b, float l, float t, float w, float h) {
        c.save();
        c.clipRect(l, t, l + w, t + h);
        for (float yy = t; yy < t + h; yy += 60f) {
            c.drawBitmap(b, null, rf(l, yy, l + w, yy + 60f), bmpP);
        }
        c.restore();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (scroller.computeScrollOffset()) {
            scrollY = clampS(scroller.getCurrY());
            postInvalidateOnAnimation();
        }
        c.drawColor(0xFFECE6E0);
        c.save();
        c.translate(offX, offY);
        c.scale(scale, scale);
        float e = E;
        c.clipRect(0, 0, SW, SH + e);
        fill.setColor(0xFFEBE5DF);
        c.drawRect(0, 0, SW, SH + e, fill);

        // latar
        c.drawBitmap(bgT, null, rf(0, 0, 475, 586), bmpP);
        tiles(c, bgM, 0, 584, 475, e + 2);
        c.drawBitmap(bgSb, null, rf(0, 584 + e, 475, 584 + e + 214), bmpP);

        drawList(c);

        // bingkai
        c.drawBitmap(frT, null, rf(30, 81, 452, 81 + 505), bmpP);
        tiles(c, frM, 30, 81 + 503, 422, e + 2);
        c.drawBitmap(frSb, null, rf(30, 81 + 503 + e, 452, 81 + 503 + e + 191), bmpP);

        drawTopBar(c);

        // trek scrollbar & volume
        c.drawBitmap(strack, null, rf(402, 216, 408, 216 + 291 + e), bmpP);
        c.drawBitmap(vtrack, null, rf(68, 735 + e, 408, 741 + e), bmpP);

        // tombol
        for (Btn b : btns) {
            boolean on = ("play".equals(b.id) && PlaybackService.playing)
                    || ("shuf".equals(b.id) && PlaybackService.shuf)
                    || ("loop".equals(b.id) && PlaybackService.loopAll)
                    || (pressed == b && pressedInside);
            float t = b.top(e);
            c.drawBitmap(on ? b.p : b.n, null, rf(b.x, t, b.x + b.w, t + b.w), bmpP);
        }

        // thumb
        c.drawBitmap(sThumbDown ? thumbDn : thumb, null,
                rf(388, thumbTop(), 388 + 34, thumbTop() + 34), bmpP);
        float vl = vThumbLeft();
        c.drawBitmap(vThumbDown ? thumbDn : thumb, null,
                rf(vl, 721 + e, vl + 34, 721 + e + 34), bmpP);

        drawDialog(c);
        c.restore();
    }

    private void ensureLayout(Track t) {
        if (t.layout != null) return;
        StaticLayout l = makeLayout(t.name, tp18, 284, 22f, 3);
        t.small = false;
        if (l.getLineCount() > 2) {
            l = makeLayout(t.name, tp15, 284, 18f, 3);
            t.small = true;
        }
        t.layout = l;
    }

    private StaticLayout makeLayout(CharSequence text, TextPaint p, int w, float lineH, int maxLines) {
        Paint.FontMetrics fm = p.getFontMetrics();
        float extra = lineH - (fm.descent - fm.ascent);
        return StaticLayout.Builder.obtain(text, 0, text.length(), p, w)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(extra, 1f)
                .setIncludePad(false)
                .setMaxLines(maxLines)
                .build();
    }

    private void drawList(Canvas c) {
        float viewH = 540f + E;
        int n = TrackStore.tracks.size();
        c.save();
        c.clipRect(42, 92, 42 + 332, 92 + viewH);
        c.translate(42, 92 - scrollY);

        stroke.setColor(LINE);
        stroke.setStrokeWidth(1f);

        float base;
        if (n == 0) {
            base = 120;
            ensureEmptyLayout();
            c.save();
            c.translate(18, 14 - cor15);
            tp15.setColor(INK);
            emptyLayout.draw(c);
            c.restore();
            fill.setColor(LINE);
            c.drawRect(0, 119, 332, 120, fill);
        } else {
            base = n * ROW;
            int first = Math.max(0, (int) (scrollY / ROW));
            int last = Math.min(n - 1, (int) ((scrollY + viewH) / ROW));
            for (int i = first; i <= last; i++) {
                Track t = TrackStore.tracks.get(i);
                float top = i * ROW;
                boolean isCur = i == PlaybackService.cur;
                boolean on = isCur && PlaybackService.playing;
                Bitmap rb = on ? rowP : rowN;
                c.drawBitmap(rb, null, rf(0, top + 21, 332, top + 21 + rb.getHeight() / VK), bmpP);

                ensureLayout(t);
                TextPaint tp = t.small ? tp15 : tp18;
                tp.setColor(isCur ? ACCENT : INK);
                float th = t.layout.getHeight();
                c.save();
                c.clipRect(38, top, 322, top + ROW);
                c.translate(38, top + (ROW - th) / 2f - (t.small ? cor15 : cor18));
                t.layout.draw(c);
                c.restore();
            }
        }
        int fc = fillerCount();
        fill.setColor(LINE);
        for (int k = 0; k < fc; k++) {
            float top = base + k * ROW;
            c.drawRect(0, top + ROW - 1, 332, top + ROW, fill);
        }
        c.restore();
    }

    private StaticLayout emptyLayout;

    private void ensureEmptyLayout() {
        if (emptyLayout == null) {
            emptyLayout = makeLayout(EMPTY_TEXT, tp15, 296, 24f, 6);
        }
    }

    private float addW() {
        return btnPaint.measureText("+ Tambah lagu") + 35f;
    }

    private void drawPill(Canvas c, RectF r, String label, boolean filled) {
        float rad = r.height() / 2f;
        if (filled) {
            fill.setColor(ACCENT);
            c.drawRoundRect(r, rad, rad, fill);
        }
        stroke.setColor(ACCENT);
        stroke.setStrokeWidth(1.5f);
        tmp2.set(r.left + 0.75f, r.top + 0.75f, r.right - 0.75f, r.bottom - 0.75f);
        c.drawRoundRect(tmp2, rad - 0.75f, rad - 0.75f, stroke);
        btnPaint.setColor(filled ? WHITE : ACCENT);
        Paint.FontMetrics fm = btnPaint.getFontMetrics();
        c.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, btnPaint);
    }

    private void drawTopBar(Canvas c) {
        float aw = addW();
        RectF a = new RectF(290, 22, 290 + aw, 54);
        drawPill(c, a, "+ Tambah lagu", addDown);

        if (!TrackStore.tracks.isEmpty()) {
            float cx = 244 + 16, cy = 22 + 16;
            if (clrDown) {
                fill.setColor(ACCENT);
                c.drawCircle(cx, cy, 16, fill);
            }
            stroke.setColor(ACCENT);
            stroke.setStrokeWidth(1.5f);
            c.drawCircle(cx, cy, 15.25f, stroke);

            c.save();
            c.translate(cx - 8, cy - 8);
            c.scale(16f / 24f, 16f / 24f);
            stroke.setColor(clrDown ? WHITE : ACCENT);
            stroke.setStrokeWidth(1.8f);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            c.drawPath(trash, stroke);
            stroke.setStrokeCap(Paint.Cap.BUTT);
            stroke.setStrokeJoin(Paint.Join.MITER);
            c.restore();
        }
    }

    private void drawDialog(Canvas c) {
        Dlg d = dlg;
        if (d == null) return;
        float H = SH + E;
        fill.setColor(0x802A1409);
        c.drawRect(0, 0, SW, H, fill);

        if (d.lay == null) d.lay = makeLayout(d.text, tp16, 351, 24.8f, 12);
        float th = d.lay.getHeight();
        float cw = 395f, ch = th + 94f, cx = 40f, cy = (H - ch) / 2f;
        d.card.set(cx, cy, cx + cw, cy + ch);

        fill.setColor(0xFFF7F3EE);
        c.drawRoundRect(d.card, 14, 14, fill);
        stroke.setColor(ACCENT);
        stroke.setStrokeWidth(2f);
        c.drawRoundRect(cx + 1, cy + 1, cx + cw - 1, cy + ch - 1, 13, 13, stroke);

        tp16.setColor(INK);
        c.save();
        c.translate(cx + 22, cy + 24 - cor16);
        d.lay.draw(c);
        c.restore();

        float by = cy + 2 + 22 + th + 18;
        float right = cx + cw - 2 - 20;
        float yw = btnPaint.measureText(d.yes) + 39f;
        d.yesR.set(right - yw, by, right, by + 34);
        drawPill(c, d.yesR, d.yes, true);
        if (d.no != null) {
            float nw = btnPaint.measureText(d.no) + 39f;
            d.noR.set(d.yesR.left - 10 - nw, by, d.yesR.left - 10, by + 34);
            drawPill(c, d.noR, d.no, false);
        } else {
            d.noR.setEmpty();
        }
    }

    // ---------------------------------------------------------------- sentuhan

    private static boolean in(float x, float y, float l, float t, float w, float h) {
        return x >= l && x <= l + w && y >= t && y <= t + h;
    }

    private float thumbTop() {
        float m = maxScroll();
        float r = m > 0 ? scrollY / m : 0f;
        return 216f + r * (291f + E) - 17f;
    }

    private float vThumbLeft() {
        return 68f + PlaybackService.vol * 340f - 17f;
    }

    private void setScrollFrom(float y) {
        float r = Math.max(0f, Math.min(1f, (y - 216f) / (291f + E)));
        scrollY = clampS(r * maxScroll());
        invalidate();
    }

    private void setVolFrom(float x) {
        float v = Math.max(0f, Math.min(1f, (x - 68f) / 340f));
        PlaybackService.vol = v;
        if (host.svc != null) host.svc.applyVolume();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = (e.getX() - offX) / scale;
        float y = (e.getY() - offY) / scale;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return down(e, x, y);
            case MotionEvent.ACTION_MOVE:
                move(e, x, y);
                return true;
            case MotionEvent.ACTION_UP:
                up(e, x, y, true);
                return true;
            case MotionEvent.ACTION_CANCEL:
                up(e, x, y, false);
                return true;
            default:
                return true;
        }
    }

    private int dlgHit(float x, float y) {
        Dlg d = dlg;
        if (d == null) return 3;
        if (d.yesR.contains(x, y)) return 1;
        if (!d.noR.isEmpty() && d.noR.contains(x, y)) return 2;
        if (!d.card.contains(x, y)) return 3;
        return 0;
    }

    private boolean down(MotionEvent e, float x, float y) {
        mode = M_NONE;
        pressed = null;
        pressedInside = false;
        addDown = clrDown = sThumbDown = vThumbDown = false;
        dragging = false;
        lpFired = false;
        lpRow = -1;
        scroller.forceFinished(true);

        if (dlg != null) {
            mode = M_DLG;
            dlgPress = dlgHit(x, y);
            return true;
        }
        if (vt != null) vt.recycle();
        vt = VelocityTracker.obtain();
        vt.addMovement(e);

        float ex = E;
        if (in(x, y, 388, thumbTop(), 34, 34)) {
            mode = M_SCROLL;
            sThumbDown = true;
            setScrollFrom(y);
            return true;
        }
        if (in(x, y, vThumbLeft(), 721 + ex, 34, 34)) {
            mode = M_VOL;
            vThumbDown = true;
            setVolFrom(x);
            return true;
        }
        for (Btn b : btns) {
            if (in(x, y, b.x, b.top(ex), b.w, b.w)) {
                mode = M_BTN;
                pressed = b;
                pressedInside = true;
                invalidate();
                return true;
            }
        }
        if (in(x, y, 290, 22, addW(), 32)) {
            mode = M_ADD;
            addDown = true;
            invalidate();
            return true;
        }
        if (!TrackStore.tracks.isEmpty() && in(x, y, 244, 22, 32, 32)) {
            mode = M_CLR;
            clrDown = true;
            invalidate();
            return true;
        }
        if (in(x, y, 388, 216, 34, 291 + ex)) {
            mode = M_SCROLL;
            sThumbDown = true;
            setScrollFrom(y);
            return true;
        }
        if (in(x, y, 68, 735 + ex - 14, 340, 34)) {
            mode = M_VOL;
            vThumbDown = true;
            setVolFrom(x);
            return true;
        }
        if (in(x, y, 42, 92, 332, 540 + ex)) {
            mode = M_LIST;
            downX = x;
            downY = y;
            startScroll = scrollY;
            int i = (int) Math.floor((y - 92 + scrollY) / ROW);
            lpRow = (i >= 0 && i < TrackStore.tracks.size()) ? i : -1;
            if (lpRow >= 0) postDelayed(lpRun, 550);
            return true;
        }
        return true;
    }

    private void move(MotionEvent e, float x, float y) {
        if (vt != null) vt.addMovement(e);
        switch (mode) {
            case M_BTN:
                pressedInside = in(x, y, pressed.x, pressed.top(E), pressed.w, pressed.w);
                invalidate();
                break;
            case M_ADD:
                addDown = in(x, y, 290, 22, addW(), 32);
                invalidate();
                break;
            case M_CLR:
                clrDown = in(x, y, 244, 22, 32, 32);
                invalidate();
                break;
            case M_SCROLL:
                setScrollFrom(y);
                break;
            case M_VOL:
                setVolFrom(x);
                break;
            case M_LIST:
                if (!dragging) {
                    if (Math.abs(y - downY) > 10f) {
                        dragging = true;
                        removeCallbacks(lpRun);
                    } else if (Math.abs(x - downX) > 10f) {
                        removeCallbacks(lpRun);
                        lpRow = -1;
                    }
                }
                if (dragging) {
                    scrollY = clampS(startScroll - (y - downY));
                    invalidate();
                }
                break;
            default:
                break;
        }
    }

    private void up(MotionEvent e, float x, float y, boolean ok) {
        removeCallbacks(lpRun);
        if (vt != null) vt.addMovement(e);
        switch (mode) {
            case M_BTN: {
                Btn b = pressed;
                boolean fire = ok && pressedInside;
                pressed = null;
                pressedInside = false;
                if (fire && b != null) onBtn(b.id);
                break;
            }
            case M_ADD: {
                boolean f = ok && addDown;
                addDown = false;
                if (f) host.pickFolder();
                break;
            }
            case M_CLR: {
                boolean f = ok && clrDown;
                clrDown = false;
                if (f && !TrackStore.tracks.isEmpty()) askClear();
                break;
            }
            case M_SCROLL:
                sThumbDown = false;
                break;
            case M_VOL:
                vThumbDown = false;
                break;
            case M_LIST:
                if (ok) {
                    if (dragging) {
                        if (vt != null) {
                            vt.computeCurrentVelocity(1000);
                            float vy = vt.getYVelocity() / scale;
                            scroller.fling(0, (int) scrollY, 0, (int) (-vy), 0, 0, 0, (int) maxScroll());
                        }
                    } else if (!lpFired && lpRow >= 0) {
                        if (host.svc != null) host.svc.play(lpRow);
                    }
                }
                break;
            case M_DLG: {
                Dlg d = dlg;
                if (ok && d != null && dlgHit(x, y) == dlgPress) {
                    int h = dlgPress;
                    if (h == 1) {
                        dlg = null;
                        if (d.onYes != null) d.onYes.run();
                    } else if (h == 2 || h == 3) {
                        dlg = null;
                    }
                }
                break;
            }
            default:
                break;
        }
        if (vt != null) {
            vt.recycle();
            vt = null;
        }
        mode = M_NONE;
        invalidate();
    }

    private void onBtn(String id) {
        PlaybackService s = host.svc;
        switch (id) {
            case "up5":
                smoothBy(-5 * ROW);
                break;
            case "up":
                smoothBy(-ROW);
                break;
            case "dn":
                smoothBy(ROW);
                break;
            case "dn5":
                smoothBy(5 * ROW);
                break;
            case "prev":
                if (s != null) s.prevPressed();
                break;
            case "stop":
                if (s != null) s.stopPlay();
                break;
            case "next":
                if (s != null) s.step(1);
                break;
            case "play":
                if (TrackStore.tracks.isEmpty()) {
                    host.pickFolder();
                } else if (s != null) {
                    s.toggle();
                }
                break;
            case "loop":
                PlaybackService.loopAll = !PlaybackService.loopAll;
                break;
            case "shuf":
                PlaybackService.shuf = !PlaybackService.shuf;
                break;
            default:
                break;
        }
        invalidate();
    }
}
