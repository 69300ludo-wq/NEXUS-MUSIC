package com.nexusmusic.player;

import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Looper;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(AndroidJUnit4.class)
public class AudioPlaybackTest {
    @Test
    public void decodesAndPlaysRealLocalWav() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File wav = new File(app.getCacheDir(), "nexus-sine-test.wav");
        makeSineWav(wav, 44100, 4);
        ListenableFuture<MediaController> connection = null;
        try {
            SessionToken token = new SessionToken(app, new ComponentName(app, PlaybackService.class));
            connection = new MediaController.Builder(app, token)
                    .setApplicationLooper(Looper.getMainLooper())
                    .buildAsync();
            MediaController player = connection.get(20, TimeUnit.SECONDS);

            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                MediaMetadata metadata = new MediaMetadata.Builder()
                        .setTitle("NEXUS test 440 Hz").build();
                player.setMediaItem(new MediaItem.Builder()
                        .setUri(Uri.fromFile(wav)).setMediaMetadata(metadata).build());
                player.prepare();
                player.play();
            });

            AtomicBoolean ready = new AtomicBoolean(false);
            AtomicLong position = new AtomicLong();
            long deadline = System.currentTimeMillis() + 14000;
            while (System.currentTimeMillis() < deadline) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    ready.set(ready.get() || player.getPlaybackState() == Player.STATE_READY);
                    position.set(Math.max(position.get(), player.getCurrentPosition()));
                });
                if (ready.get() && position.get() > 250) break;
                Thread.sleep(200);
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                player.pause();
                player.stop();
            });
            assertTrue("ExoPlayer never reached STATE_READY", ready.get());
            assertTrue("Playback timeline did not advance", position.get() > 250);
        } finally {
            if (connection != null) {
                final ListenableFuture<MediaController> pending = connection;
                InstrumentationRegistry.getInstrumentation().runOnMainSync(
                        () -> MediaController.releaseFuture(pending));
            }
            wav.delete();
        }
    }

    private static void makeSineWav(File target, int sampleRate, int seconds) throws IOException {
        int samples = sampleRate * seconds;
        int bytes = samples * 2;
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            out.write(new byte[]{'R','I','F','F'});
            intLE(out, 36 + bytes);
            out.write(new byte[]{'W','A','V','E','f','m','t',' '});
            intLE(out, 16);
            shortLE(out, 1);
            shortLE(out, 1);
            intLE(out, sampleRate);
            intLE(out, sampleRate * 2);
            shortLE(out, 2);
            shortLE(out, 16);
            out.write(new byte[]{'d','a','t','a'});
            intLE(out, bytes);
            for (int i = 0; i < samples; i++) {
                short v = (short)(Math.sin(i * 2.0 * Math.PI * 440 / sampleRate) * 7000);
                shortLE(out, v);
            }
        }
    }

    private static void intLE(BufferedOutputStream out, int v) throws IOException {
        out.write(v & 255);
        out.write((v >>> 8) & 255);
        out.write((v >>> 16) & 255);
        out.write((v >>> 24) & 255);
    }

    private static void shortLE(BufferedOutputStream out, int v) throws IOException {
        out.write(v & 255);
        out.write((v >>> 8) & 255);
    }
}
