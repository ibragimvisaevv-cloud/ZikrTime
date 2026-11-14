package com.zikrtime.focusfixture;

import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;

/** No network, data or permissions: a real third-party foreground target for CI only. */
public final class MainActivity extends android.app.Activity {
    private AudioFocusRequest focusRequest;

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

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || focusRequest != null) return;
        String mode = getIntent().getStringExtra("audio_focus");
        int gain;
        int usage;
        if ("duck".equals(mode)) {
            gain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
            usage = AudioAttributes.USAGE_NOTIFICATION;
        } else if ("loss".equals(mode)) {
            gain = AudioManager.AUDIOFOCUS_GAIN;
            usage = AudioAttributes.USAGE_MEDIA;
        } else {
            return;
        }
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(usage).build();
        focusRequest = new AudioFocusRequest.Builder(gain).setAudioAttributes(attributes).build();
        int result = getSystemService(AudioManager.class).requestAudioFocus(focusRequest);
        android.util.Log.i("FocusFixture", "audio_focus=" + mode + " granted="
            + (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED));
    }

    @Override protected void onDestroy() {
        if (focusRequest != null) getSystemService(AudioManager.class).abandonAudioFocusRequest(focusRequest);
        super.onDestroy();
    }
}
