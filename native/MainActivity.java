package com.neal.app;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.WebViewListener;
import java.util.Locale;

/** Draws the app behind transparent status and navigation bars, and tells the page how tall they are. */
public class MainActivity extends BridgeActivity {

    private Insets bars = Insets.NONE;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NealBarsPlugin.class);
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attrs);
        }

        WebView web = getBridge().getWebView();
        ViewCompat.setOnApplyWindowInsetsListener(web, (view, insets) -> {
            bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // Keep text fields above the keyboard, which no longer resizes the page by itself.
            int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            view.setPadding(0, 0, 0, keyboard);
            sendInsets();
            return insets;
        });
        getBridge().addWebViewListener(new WebViewListener() {
            @Override
            public void onPageLoaded(WebView webView) {
                sendInsets();
            }
        });
        ViewCompat.requestApplyInsets(web);
    }

    private void sendInsets() {
        float density = getResources().getDisplayMetrics().density;
        String js = String.format(
            Locale.US,
            "(function(){var s=document.documentElement.style;"
                + "s.setProperty('--sat','%.1fpx');s.setProperty('--sab','%.1fpx');"
                + "s.setProperty('--sal','%.1fpx');s.setProperty('--sar','%.1fpx');})()",
            bars.top / density,
            bars.bottom / density,
            bars.left / density,
            bars.right / density
        );
        WebView web = getBridge().getWebView();
        web.post(() -> web.evaluateJavascript(js, null));
    }
}
