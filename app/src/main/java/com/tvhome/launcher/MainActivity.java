package com.tvhome.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    // ===== AYARLAR (istedigin gibi degistir) =====
    // Bu uygulamalar en basta, bu sirayla gorunur (paket adlari).
    private static final String[] PINNED = {
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.digiturk.iq.mobil"
    };
    // Bu uygulamalar hic gorunmez (paket adlari).
    private static final String[] HIDDEN = {};
    // false yaparsan sadece TV'ye uygun (leanback) uygulamalar listelenir.
    private static final boolean SHOW_NON_TV_APPS = true;

    // Arka plan secenekleri: her biri 3 renk (kose, orta, kose).
    private static final int[][] BG_PRESETS = {
            {0xFF0B1020, 0xFF1B2A49, 0xFF0B1020},
            {0xFF000000, 0xFF0D0D0D, 0xFF000000},
            {0xFF140B25, 0xFF3B1F66, 0xFF140B25},
            {0xFF06150F, 0xFF12402E, 0xFF06150F},
            {0xFF1A0A0A, 0xFF5A1818, 0xFF1A0A0A},
            {0xFF1B1030, 0xFF8A3A5C, 0xFF251232}
    };
    private static final String[] BG_NAMES = {
            "Gece mavisi", "Siyah", "Mor", "Yesil", "Kirmizi", "Gun batimi"
    };
    // Ilk satirin ekrandaki dikey yeri: ust bosluk / alt bosluk orani.
    // TOP_WEIGHT'i buyutursan satir asagi iner, kucultursen yukari cikar.
    private static final float TOP_WEIGHT = 3f;
    private static final float BOTTOM_WEIGHT = 2f;
    // =============================================

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int COLS = 5;
    private int tileW = 150; // dp
    private int tileH = 84; // dp

    private FrameLayout root;
    private ImageView bgImage;
    private boolean hasWallpaper;
    private int bgIndex;
    private String bgName = "";

    private GridLayout row;
    private ScrollView scroller;
    private final List<View> topButtons = new ArrayList<>();
    private View wifiBtn;
    private TextView hint;
    private String movingPkg = null;
    private final List<String> currentOrder = new ArrayList<>();

    private static class AppItem {
        ResolveInfo ri;
        String label;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
        updateNetworkIcon();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMove();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        stopMove();
        if (scroller != null) {
            scroller.smoothScrollTo(0, 0);
        }
        if (row != null && row.getChildCount() > 0) {
            row.getChildAt(0).requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        // Ana ekranda geri tusu bir sey yapmaz.
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (movingPkg != null) {
            int k = e.getKeyCode();
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            boolean up = e.getAction() == KeyEvent.ACTION_UP;
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (down) moveBy(-1);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (down) moveBy(1);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_UP) {
                if (down) moveBy(-COLS);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (down) moveBy(COLS);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                    || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_BACK) {
                if (up) stopMove();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    private List<String> readList(String key) {
        String v = prefs().getString(key, "");
        List<String> out = new ArrayList<>();
        if (v != null && !v.isEmpty()) {
            for (String p : v.split(",")) {
                if (!p.isEmpty()) out.add(p);
            }
        }
        return out;
    }

    private void writeList(String key, List<String> list) {
        prefs().edit().putString(key, TextUtils.join(",", list)).apply();
    }

    private View findTile(String pkg) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            if (pkg.equals(c.getTag())) return c;
        }
        return null;
    }

    private void startMove(String pkg) {
        movingPkg = pkg;
        if (hint != null) hint.setVisibility(View.VISIBLE);
        View t = findTile(pkg);
        if (t != null) t.requestFocus();
    }

    private void stopMove() {
        movingPkg = null;
        if (hint != null) hint.setVisibility(View.GONE);
    }

    private void moveBy(int delta) {
        int i = currentOrder.indexOf(movingPkg);
        if (i < 0) return;
        int j = Math.max(0, Math.min(currentOrder.size() - 1, i + delta));
        if (j == i) return;
        String p = currentOrder.remove(i);
        currentOrder.add(j, p);
        writeList("order", currentOrder);
        loadApps();
    }

    private void moveToFront(String pkg) {
        stopMove();
        currentOrder.remove(pkg);
        currentOrder.add(0, pkg);
        writeList("order", currentOrder);
        loadApps();
    }

    private void hidePkg(String pkg) {
        List<String> h = readList("hidden");
        if (!h.contains(pkg)) h.add(pkg);
        writeList("hidden", h);
        loadApps();
        Toast.makeText(this, "Gizlendi. Geri getirmek i\u00e7in uzun bas > Gizlenenleri geri getir",
                Toast.LENGTH_LONG).show();
    }

    private void showTileMenu(final String pkg, String label) {
        final String[] opts = {
                "Ta\u015f\u0131 (y\u00f6n tu\u015flar\u0131yla)",
                "Ba\u015fa al",
                "Gizle",
                "Gizlenenleri geri getir",
                "S\u0131ralamay\u0131 s\u0131f\u0131rla"
        };
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setItems(opts, (d, which) -> {
                    switch (which) {
                        case 0:
                            startMove(pkg);
                            break;
                        case 1:
                            moveToFront(pkg);
                            break;
                        case 2:
                            hidePkg(pkg);
                            break;
                        case 3:
                            prefs().edit().remove("hidden").apply();
                            loadApps();
                            break;
                        default:
                            prefs().edit().remove("order").apply();
                            loadApps();
                            break;
                    }
                })
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildUi() {
        root = new FrameLayout(this);

        int screenDp = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        tileW = (screenDp - 128) / COLS - 20;
        tileW = Math.max(110, Math.min(tileW, 230));
        tileH = tileW * 9 / 16;

        int wp = getResources().getIdentifier("wallpaper", "drawable", getPackageName());
        hasWallpaper = wp != 0;
        if (hasWallpaper) {
            bgImage = new ImageView(this);
            bgImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            bgImage.setImageResource(wp);
            root.addView(bgImage, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        bgIndex = prefs().getInt("bg", 0);
        applyBackground();

        View dim = new View(this);
        dim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x22000000, 0x00000000, 0xCC000000}));
        root.addView(dim, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(64), dp(32), dp(48), dp(24));
        root.addView(content, new FrameLayout.LayoutParams(MATCH, MATCH));

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(60);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        content.addView(clock, new LinearLayout.LayoutParams(WRAP, WRAP));

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextColor(0xCCFFFFFF);
        date.setTextSize(22);
        content.addView(date, new LinearLayout.LayoutParams(WRAP, WRAP));

        View topSpacer = new View(this);
        content.addView(topSpacer, new LinearLayout.LayoutParams(MATCH, 0, TOP_WEIGHT));

        // Sadece 1 satirlik pencere: ilk 5 uygulama gorunur, asagi inince siradaki satir gelir.
        scroller = new ScrollView(this) {
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
                return 0; // kaydirmayi satir satir biz yapiyoruz
            }
        };
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);

        row = new GridLayout(this);
        row.setColumnCount(COLS);
        row.setClipChildren(false);
        row.setPadding(dp(8), dp(12), dp(8), 0);
        scroller.addView(row, new FrameLayout.LayoutParams(MATCH, WRAP));
        content.addView(scroller, new LinearLayout.LayoutParams(MATCH, dp(tileH + 60)));

        View bottomSpacer = new View(this);
        content.addView(bottomSpacer, new LinearLayout.LayoutParams(MATCH, 0, BOTTOM_WEIGHT));

        // Sag ustteki hizli ayar butonlari
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.TOP);
        FrameLayout.LayoutParams tbp = new FrameLayout.LayoutParams(
                WRAP, WRAP, Gravity.TOP | Gravity.END);
        tbp.topMargin = dp(32);
        tbp.rightMargin = dp(48);
        root.addView(topBar, tbp);

        wifiBtn = makeTopButton(R.drawable.ic_wifi, "Internet",
                () -> openSettingsScreen(Settings.ACTION_WIFI_SETTINGS));
        View btBtn = makeTopButton(R.drawable.ic_bluetooth, "Bluetooth",
                () -> openSettingsScreen(Settings.ACTION_BLUETOOTH_SETTINGS));
        View bgBtn = makeTopButton(R.drawable.ic_image, "Arka plan",
                () -> nextBackground());
        View setBtn = makeTopButton(R.drawable.ic_settings, "Ayarlar",
                () -> openSettingsScreen(Settings.ACTION_SETTINGS));
        topButtons.add(wifiBtn);
        topButtons.add(btBtn);
        topButtons.add(bgBtn);
        topButtons.add(setBtn);
        for (View b : topButtons) {
            topBar.addView(b, new LinearLayout.LayoutParams(WRAP, WRAP));
        }

        hint = new TextView(this);
        hint.setText("Ta\u015f\u0131ma modu: y\u00f6n tu\u015flar\u0131yla ta\u015f\u0131, OK ile bitir");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(16);
        hint.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(0xCC000000);
        hb.setCornerRadius(dp(20));
        hint.setBackground(hb);
        hint.setVisibility(View.GONE);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hp.bottomMargin = dp(16);
        root.addView(hint, hp);

        setContentView(root);
    }

    private void applyBackground() {
        int count = BG_PRESETS.length + (hasWallpaper ? 1 : 0);
        if (bgIndex < 0 || bgIndex >= count) {
            bgIndex = 0;
        }
        boolean showWallpaper = hasWallpaper && bgIndex == 0;
        if (bgImage != null) {
            bgImage.setVisibility(showWallpaper ? View.VISIBLE : View.GONE);
        }
        int pi = hasWallpaper ? bgIndex - 1 : bgIndex;
        if (pi < 0) {
            pi = 0;
        }
        root.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, BG_PRESETS[pi]));
        bgName = showWallpaper ? "Duvar kagidi" : BG_NAMES[pi];
    }

    private void nextBackground() {
        bgIndex++;
        applyBackground();
        prefs().edit().putInt("bg", bgIndex).apply();
        Toast.makeText(this, bgName, Toast.LENGTH_SHORT).show();
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private void updateNetworkIcon() {
        if (wifiBtn == null) {
            return;
        }
        View icon = wifiBtn.findViewWithTag("icon");
        if (icon != null) {
            icon.setAlpha(isOnline() ? 1f : 0.35f);
        }
    }

    private void openSettingsScreen(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                Toast.makeText(this, "Acilamadi", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private LinearLayout makeTopButton(int iconRes, String label, final Runnable action) {
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setFocusable(true);
        col.setClickable(true);
        col.setId(View.generateViewId());
        col.setPadding(dp(10), dp(6), dp(10), dp(6));

        final GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(0x22FFFFFF);

        final FrameLayout circle = new FrameLayout(this);
        circle.setBackground(circleBg);

        ImageView icon = new ImageView(this);
        icon.setTag("icon");
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            icon.setImageResource(iconRes);
        } catch (Exception e) {
            // ikon yuklenemezse bos kalir, buton yine calisir
        }
        circle.addView(icon, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        col.addView(circle, new LinearLayout.LayoutParams(dp(48), dp(48)));

        final TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        tv.setAlpha(0f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        tlp.topMargin = dp(8);
        col.addView(tv, tlp);

        col.setOnFocusChangeListener((v, hasFocus) -> {
            float s = hasFocus ? 1.15f : 1f;
            circle.animate().scaleX(s).scaleY(s).setDuration(120).start();
            tv.animate().alpha(hasFocus ? 1f : 0f).setDuration(120).start();
            circleBg.setColor(hasFocus ? 0x55FFFFFF : 0x22FFFFFF);
            if (hasFocus) {
                circleBg.setStroke(dp(2), Color.WHITE);
            } else {
                circleBg.setStroke(0, Color.TRANSPARENT);
            }
        });
        col.setOnClickListener(v -> action.run());
        return col;
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Set<String> hidden = new HashSet<>(Arrays.asList(HIDDEN));
        hidden.addAll(readList("hidden"));
        Set<String> seen = new HashSet<>();
        List<AppItem> items = new ArrayList<>();

        List<String> cats = new ArrayList<>();
        cats.add(Intent.CATEGORY_LEANBACK_LAUNCHER);
        if (SHOW_NON_TV_APPS) {
            cats.add(Intent.CATEGORY_LAUNCHER);
        }
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || hidden.contains(pkg)) {
                    continue;
                }
                if (!seen.add(pkg)) {
                    continue;
                }
                AppItem it = new AppItem();
                it.ri = ri;
                it.label = String.valueOf(ri.loadLabel(pm));
                items.add(it);
            }
        }

        final List<String> pinned = Arrays.asList(PINNED);
        Collections.sort(items, (a, b) -> {
            int ia = pinned.indexOf(a.ri.activityInfo.packageName);
            int ib = pinned.indexOf(b.ri.activityInfo.packageName);
            if (ia >= 0 || ib >= 0) {
                if (ia < 0) return 1;
                if (ib < 0) return -1;
                return ia - ib;
            }
            return a.label.compareToIgnoreCase(b.label);
        });

        List<String> saved = readList("order");
        List<AppItem> ordered = new ArrayList<>();
        for (String sp : saved) {
            for (AppItem it : items) {
                if (it.ri.activityInfo.packageName.equals(sp)) {
                    ordered.add(it);
                    break;
                }
            }
        }
        for (AppItem it : items) {
            if (!ordered.contains(it)) {
                ordered.add(it);
            }
        }
        currentOrder.clear();
        for (AppItem it : ordered) {
            currentOrder.add(it.ri.activityInfo.packageName);
        }

        row.removeAllViews();
        for (AppItem it : ordered) {
            ResolveInfo ri = it.ri;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            boolean isBanner = banner != null;
            Drawable art = isBanner ? banner : ri.loadIcon(pm);
            Intent li = new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            View tile = makeTile(art, isBanner, it.label, li, ri.activityInfo.packageName);
            tile.setId(View.generateViewId());
            tile.setTag(ri.activityInfo.packageName);
            if (row.getChildCount() < COLS) {
                tile.setNextFocusUpId(wifiBtn.getId());
            }
            row.addView(tile);
        }

        row.post(() -> {
            if (row.getChildCount() > 0) {
                int h = row.getChildAt(0).getHeight();
                if (h > 0) {
                    ViewGroup.LayoutParams lp = scroller.getLayoutParams();
                    int want = h + dp(12);
                    if (lp.height != want) {
                        lp.height = want;
                        scroller.setLayoutParams(lp);
                    }
                }
            }
        });

        if (row.getChildCount() > 0) {
            View first = row.getChildAt(0);
            for (View b : topButtons) {
                b.setNextFocusDownId(first.getId());
            }
            View target = movingPkg != null ? findTile(movingPkg) : null;
            if (target == null) {
                target = first;
            }
            target.requestFocus();
        }
    }

    private void snapToRow(View tile) {
        if (scroller == null || row == null) return;
        int target = tile.getTop() - row.getPaddingTop();
        if (target < 0) target = 0;
        scroller.smoothScrollTo(0, target);
    }

    private View makeTile(Drawable art, boolean isBanner, final String label, final Intent launchIntent, final String pkg) {
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setFocusable(true);
        col.setClickable(true);
        col.setPadding(dp(10), dp(8), dp(10), dp(8));

        final GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0x33FFFFFF);
        cardBg.setCornerRadius(dp(14));

        final FrameLayout card = new FrameLayout(this);
        card.setBackground(cardBg);
        card.setClipToOutline(true);

        ImageView img = new ImageView(this);
        img.setImageDrawable(art);
        if (isBanner) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(MATCH, MATCH));
        } else {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int is = dp(tileH * 6 / 10);
            card.addView(img, new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        }
        col.addView(card, new LinearLayout.LayoutParams(dp(tileW), dp(tileH)));

        final TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        tv.setAlpha(0f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(tileW), WRAP);
        tlp.topMargin = dp(12);
        col.addView(tv, tlp);

        col.setOnFocusChangeListener((v, hasFocus) -> {
            float s = hasFocus ? 1.12f : 1f;
            col.setTranslationZ(hasFocus ? dp(12) : 0f);
            if (hasFocus) {
                scroller.post(() -> snapToRow(col));
            }
            card.animate().scaleX(s).scaleY(s).setDuration(120).start();
            tv.animate().alpha(hasFocus ? 1f : 0f).setDuration(120).start();
            cardBg.setColor(hasFocus ? 0x66FFFFFF : 0x33FFFFFF);
            if (hasFocus) {
                cardBg.setStroke(dp(3), Color.WHITE);
            } else {
                cardBg.setStroke(0, Color.TRANSPARENT);
            }
        });

        col.setOnLongClickListener(v -> {
            showTileMenu(pkg, label);
            return true;
        });

        col.setOnClickListener(v -> {
            try {
                startActivity(launchIntent);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Acilamadi", Toast.LENGTH_SHORT).show();
            }
        });

        return col;
    }
}
