package com.cyroodev.cyroospace;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView myWebView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. Fullscreen Mode & Keep Screen On (Layar tidak mati saat main game)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        setContentView(R.layout.activity_main);

        // 2. Cek Izin Overlay (Agar bisa tampil di atas game lain)
        checkOverlayPermission();

        // 3. Inisialisasi WebView
        myWebView = findViewById(R.id.webview);
        WebSettings ws = myWebView.getSettings();
        
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setDatabaseEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Memastikan WebView fokus agar klik tombol lancar
        myWebView.setFocusable(true);
        myWebView.setFocusableInTouchMode(true);
        myWebView.requestFocus(View.FOCUS_DOWN);

        // Fix Bug "Tombol Tidak Bisa Dipencet"
        myWebView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP) {
                if (!v.hasFocus()) v.requestFocus();
            }
            return false;
        });

        // 4. Interface Bridge
        myWebView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        myWebView.setWebViewClient(new WebViewClient());
        myWebView.loadUrl("file:///android_asset/index.html");

        // 5. Jalankan Monitoring Hardware
        startHardwareMonitor();
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void startHardwareMonitor() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent batteryStatus = registerReceiver(null, ifilter);
                    if (batteryStatus != null) {
                        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                        float temp = ((float) batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)) / 10;

                        // Kirim data ke fungsi updateNativeData di HTML
                        myWebView.post(() -> {
                            myWebView.evaluateJavascript("updateNativeData(" + temp + "," + level + ")", null);
                        });
                    }
                } catch (Exception ignored) {}
                handler.postDelayed(this, 3000); // Update setiap 3 detik
            }
        }, 3000);
    }

    // --- INTERFACE KOMUNIKASI JAVA KE HTML ---
    public class WebAppInterface {

        @JavascriptInterface
        public void getInstalledApps() {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            for (ApplicationInfo app : apps) {
                // Filter hanya aplikasi yang diinstall user (bukan sistem murni)
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                    String name = app.loadLabel(pm).toString();
                    String pkg = app.packageName;

                    // Kirim ke fungsi displayApps di HTML
                    runOnUiThread(() -> {
                        myWebView.evaluateJavascript("displayApps('" + name.replace("'", "\\'") + "', '" + pkg + "')", null);
                    });
                }
            }
        }

        @JavascriptInterface
        public void openGame(String packageName) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                // FLAG_ACTIVITY_NEW_TASK sangat penting agar bisa buka app di atas app lain
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null); // Stop monitor saat app ditutup
    }
}
