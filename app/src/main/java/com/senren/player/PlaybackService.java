package com.senren.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.List;
import java.util.Random;

public class PlaybackService extends Service {

    static final String CHANNEL = "playback";
    static final String A_PREV = "com.senren.player.PREV";
    static final String A_TOGGLE = "com.senren.player.TOGGLE";
    static final String A_NEXT = "com.senren.player.NEXT";
    private static final int NID = 1;

    // status bersama (dibaca oleh PlayerView)
    static int cur = -1;
    static boolean playing = false;
    static boolean loopAll = true;
    static boolean shuf = false;
    static boolean error = false;
    static float vol = 0.8f;

    private MediaPlayer mp;
    private boolean prepared = false;
    private boolean notif = false;
    private boolean resumeOnFocus = false;
    private Runnable listener;
    private AudioManager am;
    private final Random rnd = new Random();

    public class LocalBinder extends Binder {
        PlaybackService get() {
            return PlaybackService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    private final AudioManager.OnAudioFocusChangeListener focusL = change -> {
        if (change == AudioManager.AUDIOFOCUS_LOSS) {
            pause();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            if (playing) {
                pause();
                resumeOnFocus = true;
            }
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            applyVolumeScaled(0.2f);
        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
            applyVolumeScaled(1f);
            if (resumeOnFocus) {
                resumeOnFocus = false;
                resume();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Music playback", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String a = intent.getAction();
            if (A_PREV.equals(a)) {
                prevPressed();
            } else if (A_TOGGLE.equals(a)) {
                toggle();
            } else if (A_NEXT.equals(a)) {
                step(1);
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (!playing) {
            stopFg(true);
            stopSelf();
        }
        return false;
    }

    @Override
    public void onDestroy() {
        if (mp != null) {
            try {
                mp.release();
            } catch (Exception ignored) {
            }
            mp = null;
        }
        abandonFocus();
        cur = -1;
        playing = false;
        super.onDestroy();
    }

    void setListener(Runnable r) {
        listener = r;
    }

    // ---------------------------------------------------------------- kontrol

    void play(int i) {
        List<Track> ts = TrackStore.tracks;
        if (i < 0 || i >= ts.size()) return;
        requestFocus();
        cur = i;
        playing = true;
        prepared = false;
        error = false;
        ensurePlayer();
        try {
            mp.reset();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(getApplicationContext(), Uri.parse(ts.get(i).uri));
            mp.prepareAsync();
        } catch (Exception e) {
            fail();
            return;
        }
        startFg();
        notifyChange();
    }

    void toggle() {
        if (TrackStore.tracks.isEmpty()) return;
        if (cur < 0) {
            step(1);
        } else if (!playing) {
            resume();
        } else {
            pause();
        }
    }

    void pause() {
        resumeOnFocus = false;
        playing = false;
        if (prepared && mp != null) {
            try {
                if (mp.isPlaying()) mp.pause();
            } catch (Exception ignored) {
            }
        }
        stopFg(false);
        notifyChange();
    }

    void resume() {
        requestFocus();
        playing = true;
        if (prepared && mp != null) {
            try {
                mp.start();
            } catch (Exception e) {
                fail();
                return;
            }
        }
        startFg();
        notifyChange();
    }

    void stopPlay() {
        resumeOnFocus = false;
        playing = false;
        if (prepared && mp != null) {
            try {
                if (mp.isPlaying()) mp.pause();
                mp.seekTo(0);
            } catch (Exception ignored) {
            }
        }
        abandonFocus();
        stopFg(false);
        notifyChange();
    }

    void step(int d) {
        int n = TrackStore.tracks.size();
        if (n == 0) return;
        int i;
        if (cur < 0) {
            i = 0;
        } else if (shuf && n > 1) {
            do {
                i = rnd.nextInt(n);
            } while (i == cur);
        } else {
            i = ((cur + d) % n + n) % n;
        }
        play(i);
    }

    void prevPressed() {
        if (prepared && mp != null && cur >= 0) {
            try {
                if (mp.getCurrentPosition() > 3000) {
                    mp.seekTo(0);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        step(-1);
    }

    void onRemoved(int i) {
        if (i == cur) {
            halt();
            cur = -1;
        } else if (i < cur) {
            cur--;
        }
        notifyChange();
    }

    void resetAll() {
        halt();
        cur = -1;
        notifyChange();
    }

    void applyVolume() {
        applyVolumeScaled(1f);
    }

    // ---------------------------------------------------------------- internal

    private void applyVolumeScaled(float k) {
        if (prepared && mp != null) {
            try {
                mp.setVolume(vol * k, vol * k);
            } catch (Exception ignored) {
            }
        }
    }

    private void ensurePlayer() {
        if (mp != null) return;
        mp = new MediaPlayer();
        mp.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mp.setOnPreparedListener(m -> {
            prepared = true;
            try {
                m.setVolume(vol, vol);
                if (playing) m.start();
            } catch (Exception e) {
                fail();
                return;
            }
            notifyChange();
        });
        mp.setOnCompletionListener(m -> {
            if (loopAll) {
                // b_loop_p: loop semua playlist
                step(1);
            } else {
                // b_loop_n: loop satu lagu
                try {
                    m.seekTo(0);
                    m.start();
                } catch (Exception e) {
                    if (cur >= 0) play(cur);
                }
            }
        });
        mp.setOnErrorListener((m, what, extra) -> {
            fail();
            return true;
        });
    }

    private void halt() {
        playing = false;
        prepared = false;
        resumeOnFocus = false;
        if (mp != null) {
            try {
                mp.reset();
            } catch (Exception ignored) {
            }
        }
        abandonFocus();
        stopFg(true);
    }

    private void fail() {
        playing = false;
        prepared = false;
        error = true;
        if (mp != null) {
            try {
                mp.reset();
            } catch (Exception ignored) {
            }
        }
        stopFg(true);
        notifyChange();
    }

    @SuppressWarnings("deprecation")
    private void requestFocus() {
        am.requestAudioFocus(focusL, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    @SuppressWarnings("deprecation")
    private void abandonFocus() {
        if (am != null) am.abandonAudioFocus(focusL);
    }

    private void notifyChange() {
        if (notif) updateNotification();
        if (listener != null) listener.run();
    }

    private void startFg() {
        try {
            startService(new Intent(this, PlaybackService.class));
            Notification n = buildNotification();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NID, n);
            }
            notif = true;
        } catch (Exception ignored) {
            // tetap memutar tanpa mode foreground
        }
    }

    private void stopFg(boolean remove) {
        try {
            stopForeground(remove ? Service.STOP_FOREGROUND_REMOVE : Service.STOP_FOREGROUND_DETACH);
        } catch (Exception ignored) {
        }
        if (remove) notif = false;
    }

    private void updateNotification() {
        try {
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .notify(NID, buildNotification());
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        String title = "Senren Player";
        List<Track> ts = TrackStore.tracks;
        if (cur >= 0 && cur < ts.size()) title = ts.get(cur).name;

        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent cpi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        b.setSmallIcon(R.drawable.ic_stat_note)
                .setContentTitle(title)
                .setContentText(playing ? "Playing" : "Paused")
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setContentIntent(cpi);
        b.addAction(action("Previous", A_PREV));
        b.addAction(action(playing ? "Pause" : "Play", A_TOGGLE));
        b.addAction(action("Next", A_NEXT));
        return b.build();
    }

    private Notification.Action action(String title, String act) {
        Intent i = new Intent(this, PlaybackService.class).setAction(act);
        PendingIntent pi = PendingIntent.getService(this, act.hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_stat_note), title, pi).build();
    }
}
