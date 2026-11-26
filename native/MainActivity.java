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
    private int ime = 0;

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
            // The keyboard no longer resizes the page: tell it how tall the keyboard is instead.
            ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
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
                + "s.setProperty('--sal','%.1fpx');s.setProperty('--sar','%.1fpx');"
                + "var r=document.documentElement,k=%.1f;r.dataset.nativeKb='1';"
                + "if(k>80){s.setProperty('--kb',k+'px');r.classList.add('kbup');}else{s.removeProperty('--kb');r.classList.remove('kbup');}"
                + "if(k>80){var a=document.activeElement;if(a&&a.scrollIntoView)setTimeout(function(){a.scrollIntoView({block:'nearest',behavior:'smooth'});},120);}})()",
            bars.top / density,
            bars.bottom / density,
            bars.left / density,
            bars.right / density,
            Math.max(0, ime - bars.bottom) / density
        );
        WebView web = getBridge().getWebView();
        web.post(() -> web.evaluateJavascript(js, null));
    }
}
