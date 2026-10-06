package com.nexusmusic.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements NexusMusicView.Actions {
    private static final int PICK_AUDIO = 4101;
    private static final String PREFS = "nexus_music";
    private static final String LAST_URL = "last_radio_url";
    private static final String LAST_NAME = "last_radio_name";
    private static final String LAST_SUB = "last_radio_subtitle";
    private static final String[] RADIO_APIS = {
            "https://de1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();

    private NexusMusicView view;
    private ExoPlayer player;
    private SharedPreferences prefs;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateProgress();
            handler.postDelayed(this, 400L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        Window window = getWindow();
        window.setStatusBarColor(0xFF02040A);
        window.setNavigationBarColor(0xFF02040A);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        view = new NexusMusicView(this);
        view.setActions(this);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(view, new android.widget.FrameLayout.LayoutParams(-1, -2));
        setContentView(scroll);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(attributes, true)
                .build();
        player.setHandleAudioBecomingNoisy(true);
        player.setWakeMode(C.WAKE_MODE_LOCAL);
        player.addListener(playerListener);

        view.setStatus("AUDIO CORE READY");
        handler.post(ticker);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        network.shutdownNow();
        if (player != null) {
            player.removeListener(playerListener);
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onIsPlayingChanged(boolean playing) {
            view.setPlaying(playing);
            if (playing) view.setStatus("LECTURE ACTIVE");
        }

        @Override public void onMediaMetadataChanged(MediaMetadata metadata) {
            if (metadata.title != null) view.setTrackTitle(metadata.title.toString());
            if (metadata.artist != null) view.setTrackSubtitle(metadata.artist.toString());
        }

        @Override public void onTracksChanged(Tracks tracks) {
            updateSignal(tracks);
        }

        @Override public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_BUFFERING) view.setStatus("BUFFERING…");
            else if (state == Player.STATE_READY) view.setStatus("AUDIO CORE READY");
            else if (state == Player.STATE_ENDED) view.setStatus("LECTURE TERMINÉE");
        }

        @Override public void onPlayerError(PlaybackException error) {
            view.setStatus("ERREUR AUDIO");
            Toast.makeText(MainActivity.this,
                    "Lecture impossible : " + error.getErrorCodeName(),
                    Toast.LENGTH_LONG).show();
        }
    };

    private void updateProgress() {
        if (player == null) return;
        long pos = Math.max(0L, player.getCurrentPosition());
        long dur = player.getDuration();
        float progress = dur > 0L && dur != C.TIME_UNSET
                ? Math.min(1f, (float) pos / (float) dur) : 0f;
        view.setProgress(progress, pos, dur == C.TIME_UNSET ? 0L : dur);
    }

    private void updateSignal(Tracks tracks) {
        String source = "Format en attente";
        outer:
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSelected(i)) continue;
                source = describeFormat(group.getTrackFormat(i));
                break outer;
            }
        }
        view.setSignalDiagnostics(source, describeRoute());
    }

    private static String describeFormat(Format f) {
        String mime = f.sampleMimeType == null ? "AUDIO" : f.sampleMimeType;
        int slash = mime.indexOf('/');
        if (slash >= 0) mime = mime.substring(slash + 1);
        StringBuilder s = new StringBuilder(mime.toUpperCase(Locale.US));
        if (f.sampleRate > 0) {
            s.append(" • ");
            if (f.sampleRate % 1000 == 0) s.append(f.sampleRate / 1000).append(" kHz");
            else s.append(String.format(Locale.US, "%.1f kHz", f.sampleRate / 1000f));
        }
        if (f.channelCount > 0) s.append(" • ").append(f.channelCount).append(" ch");
        return s.toString();
    }

    private String describeRoute() {
        if (Build.VERSION.SDK_INT < 33) return "Android Audio";
        AudioManager manager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (manager == null) return "Android Audio";
        try {
            android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            List<AudioDeviceInfo> devices = manager.getAudioDevicesForAttributes(attrs);
            if (devices.isEmpty()) return "Android Audio";
            AudioDeviceInfo d = devices.get(0);
            String type;
            switch (d.getType()) {
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: type = "Bluetooth"; break;
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET: type = "USB DAC"; break;
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_WIRED_HEADSET: type = "Casque filaire"; break;
                case AudioDeviceInfo.TYPE_HDMI:
                case AudioDeviceInfo.TYPE_HDMI_ARC: type = "HDMI"; break;
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: type = "Haut-parleur"; break;
                default: type = "Sortie audio";
            }
            CharSequence product = d.getProductName();
            return product == null || product.length() == 0
                    ? type : type + " • " + product;
        } catch (RuntimeException e) {
            return "Android Audio";
        }
    }

    @Override public void onPickLocalAudio() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_AUDIO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_AUDIO || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {}
        play(uri, displayName(uri), "Fichier local");
        view.showHome();
    }

    @Override public void onTogglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        else player.play();
    }

    @Override public void onSeekTo(float fraction) {
        if (player == null) return;
        long duration = player.getDuration();
        if (duration > 0L && duration != C.TIME_UNSET) {
            player.seekTo((long) (duration * Math.max(0f, Math.min(1f, fraction))));
        }
    }

    @Override public void onTestAudio() {
        view.setStatus("TEST AUDIO…");
        network.execute(() -> {
            try {
                File file = new File(getCacheDir(), "nexus-audio-test.wav");
                writeTestWav(file);
                runOnUiThread(() -> {
                    play(Uri.fromFile(file), "NEXUS Audio Test",
                            "440 Hz • PCM 16-bit / 44.1 kHz");
                    view.showHome();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    view.setStatus("ERREUR TEST AUDIO");
                    Toast.makeText(this,
                            "Impossible de créer le test audio.",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override public void onBrowseRadios() {
        EditText input = new EditText(this);
        input.setHint("Nom, pays ou style : jazz, France, ambient…");
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Explorer les radios du monde")
                .setMessage("Laisse vide pour les radios populaires.")
                .setView(input)
                .setNegativeButton("Pays",
                        (d, w) -> searchRadios(input.getText().toString().trim(), "country"))
                .setNeutralButton("Genre",
                        (d, w) -> searchRadios(input.getText().toString().trim(), "tag"))
                .setPositiveButton("Station",
                        (d, w) -> searchRadios(input.getText().toString().trim(), "name"))
                .show();
    }

    @Override public void onAddRadio() {
        EditText input = new EditText(this);
        input.setHint("https://… ou http://…");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(prefs.getString(LAST_URL, ""));
        new AlertDialog.Builder(this)
                .setTitle("Ajouter une radio")
                .setView(input)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Lire", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!(url.startsWith("https://") || url.startsWith("http://"))) {
                        Toast.makeText(this,
                                "Entre un flux radio HTTP ou HTTPS direct.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    String sub = host(url);
                    saveRadio(url, "Radio Internet", sub);
                    play(Uri.parse(url), "Radio Internet", sub);
                    view.showHome();
                })
                .show();
    }

    @Override public void onResumeLastRadio() {
        String url = prefs.getString(LAST_URL, "");
        if (url.isEmpty()) {
            onAddRadio();
            return;
        }
        play(Uri.parse(url),
                prefs.getString(LAST_NAME, "Radio Internet"),
                prefs.getString(LAST_SUB, host(url)));
        view.showHome();
    }

    @Override public boolean hasSavedRadio() {
        return !prefs.getString(LAST_URL, "").isEmpty();
    }

    private void searchRadios(String query, String field) {
        view.setStatus("RECHERCHE RADIO…");
        network.execute(() -> {
            for (String base : RADIO_APIS) {
                HttpURLConnection connection = null;
                try {
                    String endpoint = query.isEmpty()
                            ? base + "/json/stations/topvote/50?hidebroken=true"
                            : base + "/json/stations/search?hidebroken=true&order=votes&reverse=true&limit=50&"
                            + field + "=" + Uri.encode(query);
                    connection = (HttpURLConnection) new URL(endpoint).openConnection();
                    connection.setConnectTimeout(6500);
                    connection.setReadTimeout(8500);
                    connection.setRequestProperty("User-Agent", "NEXUS-MUSIC/1.1 Android");
                    connection.setRequestProperty("Accept", "application/json");
                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) continue;

                    StringBuilder body = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) body.append(line);
                    }

                    JSONArray array = new JSONArray(body.toString());
                    List<Station> stations = new ArrayList<>();
                    for (int i = 0; i < array.length() && stations.size() < 30; i++) {
                        JSONObject o = array.optJSONObject(i);
                        if (o == null) continue;
                        String url = o.optString("url_resolved",
                                o.optString("url", "")).trim();
                        if (!(url.startsWith("https://") || url.startsWith("http://"))) continue;
                        stations.add(new Station(
                                o.optString("name", "Radio").trim(),
                                url,
                                o.optString("country", "").trim(),
                                o.optString("codec", "").trim(),
                                o.optInt("bitrate", 0)));
                    }
                    if (!stations.isEmpty()) {
                        runOnUiThread(() -> showStations(stations));
                        return;
                    }
                } catch (Exception ignored) {
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }

            runOnUiThread(() -> {
                view.setStatus("RADIO DIRECTORY OFFLINE");
                Toast.makeText(this,
                        "Annuaire radio indisponible. Utilise une URL directe ou réessaie.",
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void showStations(List<Station> stations) {
        String[] labels = new String[stations.size()];
        for (int i = 0; i < stations.size(); i++) labels[i] = stations.get(i).label();
        new AlertDialog.Builder(this)
                .setTitle("Radios disponibles")
                .setItems(labels, (d, which) -> {
                    Station s = stations.get(which);
                    saveRadio(s.url, s.name, s.subtitle());
                    play(Uri.parse(s.url), s.name, s.subtitle());
                    view.showHome();
                })
                .setNegativeButton("Fermer", null)
                .show();
    }

    private void saveRadio(String url, String name, String subtitle) {
        prefs.edit()
                .putString(LAST_URL, url)
                .putString(LAST_NAME, name)
                .putString(LAST_SUB, subtitle)
                .apply();
    }

    private void play(Uri uri, String title, String subtitle) {
        if (player == null) return;
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(subtitle)
                .build();
        MediaItem item = new MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build();
        player.setMediaItem(item);
        player.prepare();
        player.play();
        view.setTrackTitle(title);
        view.setTrackSubtitle(subtitle);
        view.setStatus("ANALYSE DU SIGNAL…");
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && cursor.getString(index) != null) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {}
        return "Titre local";
    }

    private static String host(String value) {
        String host = Uri.parse(value).getHost();
        return host == null ? "Flux radio" : host;
    }

    private static void writeTestWav(File file) throws IOException {
        final int sampleRate = 44100;
        final int channels = 2;
        final int bits = 16;
        final int seconds = 5;
        final int frames = sampleRate * seconds;
        final int blockAlign = channels * bits / 8;
        final int dataSize = frames * blockAlign;
        final int byteRate = sampleRate * blockAlign;

        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[]{'R','I','F','F'});
            writeIntLE(out, 36 + dataSize);
            out.write(new byte[]{'W','A','V','E','f','m','t',' '});
            writeIntLE(out, 16);
            writeShortLE(out, 1);
            writeShortLE(out, channels);
            writeIntLE(out, sampleRate);
            writeIntLE(out, byteRate);
            writeShortLE(out, blockAlign);
            writeShortLE(out, bits);
            out.write(new byte[]{'d','a','t','a'});
            writeIntLE(out, dataSize);

            for (int i = 0; i < frames; i++) {
                double envelope = Math.min(1.0, i / 2205.0)
                        * Math.min(1.0, (frames - i) / 2205.0);
                short sample = (short) (Math.sin(
                        2.0 * Math.PI * 440.0 * i / sampleRate)
                        * 0.22 * envelope * Short.MAX_VALUE);
                writeShortLE(out, sample);
                writeShortLE(out, sample);
            }
        }
    }

    private static void writeIntLE(FileOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void writeShortLE(FileOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private static final class Station {
        final String name;
        final String url;
        final String country;
        final String codec;
        final int bitrate;

        Station(String name, String url, String country, String codec, int bitrate) {
            this.name = name.isEmpty() ? "Radio" : name;
            this.url = url;
            this.country = country;
            this.codec = codec;
            this.bitrate = bitrate;
        }

        String subtitle() {
            StringBuilder b = new StringBuilder();
            if (!country.isEmpty()) b.append(country);
            if (!codec.isEmpty()) {
                if (b.length() > 0) b.append(" • ");
                b.append(codec);
            }
            if (bitrate > 0) {
                if (b.length() > 0) b.append(" • ");
                b.append(bitrate).append(" kb/s");
            }
            return b.length() == 0 ? "Radio Internet" : b.toString();
        }

        String label() {
            return name + "\n" + subtitle();
        }
    }
}
