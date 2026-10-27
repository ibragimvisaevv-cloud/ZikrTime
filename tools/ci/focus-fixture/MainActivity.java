package com.zikrtime.focusfixture;

/** No network, data or permissions: a real third-party foreground target for CI only. */
public final class MainActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.TextView text = new android.widget.TextView(this);
        text.setText("Focus fixture\nSynthetic foreground target\nNo network or personal data");
        text.setTextSize(24);
        text.setGravity(android.view.Gravity.CENTER);
        text.setTextColor(0xffdce8ec);
        text.setBackgroundColor(0xff223540);
        text.setContentDescription("focus_fixture_foreground");
        setContentView(text);
    }
}
