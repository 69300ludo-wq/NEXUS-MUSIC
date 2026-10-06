package com.nexusmusic.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

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

    private NexusMusicView view;
    private MediaPlayer player;
    private SharedPreferences prefs;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateProgress();
            handler.postDelayed(this, 500L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF02040A);
        getWindow().setNavigationBarColor(0xFF02040A);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        view = new NexusMusicView(this);
        view.setActions(this);
        setContentView(view);

        view.setStatus("PRÊT");
        view.appendLog("NEXUS CORE 2.0 démarré");
        android.util.Log.i("NEXUS_RECOVERY", "STARTED");
        handler.post(ticker);

        Intent launchIntent = getIntent();
        if (launchIntent.getBooleanExtra("self_test_audio", false)) {
            handler.postDelayed(this::onTestAudio, 500L);
        } else if (launchIntent.getBooleanExtra("self_test_picker", false)) {
            handler.postDelayed(this::onPickLocalAudio, 500L);
        } else if (launchIntent.getBooleanExtra("self_test_radio", false)) {
            handler.postDelayed(() -> searchRadios("France", "name"), 500L);
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        network.shutdownNow();
        releasePlayer();
        super.onDestroy();
    }

    @Override public void onTestAudio() {
        view.setStatus("CRÉATION DU TEST AUDIO…");
        view.appendLog("Création d'un WAV PCM 44.1 kHz");
        network.execute(() -> {
            try {
                File file = new File(getCacheDir(), "nexus-test.wav");
                writeTestWav(file, 8);
                runOnUiThread(() -> playFileUri(Uri.fromFile(file),
                        "NEXUS AUDIO TEST", "PCM 16-bit / 44.1 kHz"));
            } catch (Exception e) {
                runOnUiThread(() -> fail("TEST AUDIO", e));
            }
        });
    }

    @Override public void onPickLocalAudio() {
        view.appendLog("Ouverture du sélecteur Android");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_AUDIO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_AUDIO || resultCode != RESULT_OK || data == null) {
            if (requestCode == PICK_AUDIO) view.appendLog("Sélection annulée");
            return;
        }

        Uri uri = data.getData();
        if (uri == null) {
            view.appendLog("Aucun fichier reçu");
            return;
        }

        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}

        String name = displayName(uri);
        view.appendLog("Fichier sélectionné : " + name);
        playFileUri(uri, name, "Fichier local");
    }

    @Override public void onTogglePlayPause() {
        if (player == null) {
            view.appendLog("Aucun média chargé");
            Toast.makeText(this, "Choisis une musique ou lance le test audio.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (player.isPlaying()) {
                player.pause();
                view.setPlaying(false);
                view.setStatus("PAUSE");
                view.appendLog("Pause");
            } else {
                player.start();
                view.setPlaying(true);
                view.setStatus("LECTURE ACTIVE");
                view.appendLog("Lecture reprise");
            }
        } catch (Exception e) {
            fail("PLAY/PAUSE", e);
        }
    }

    @Override public void onSeekTo(float fraction) {
        if (player == null) return;
        try {
            int duration = player.getDuration();
            if (duration > 0) player.seekTo((int) (duration * clamp(fraction)));
        } catch (Exception e) {
            fail("SEEK", e);
        }
    }

    @Override public void onBrowseRadios() {
        final EditText input = new EditText(this);
        input.setHint("Station, pays ou genre");
        input.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle("Rechercher une radio")
                .setView(input)
                .setNegativeButton("Annuler", null)
                .setNeutralButton("Genre", (d, w) ->
                        searchRadios(input.getText().toString().trim(), "tag"))
                .setPositiveButton("Station", (d, w) ->
                        searchRadios(input.getText().toString().trim(), "name"))
                .show();
    }

    @Override public void onAddRadio() {
        final EditText input = new EditText(this);
        input.setHint("https://... ou http://...");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(prefs.getString(LAST_URL, ""));

        new AlertDialog.Builder(this)
                .setTitle("URL radio directe")
                .setView(input)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Lire", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!(url.startsWith("https://") || url.startsWith("http://"))) {
                        Toast.makeText(this, "URL HTTP/HTTPS invalide.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    saveRadio(url, "Radio Internet", host(url));
                    playNetworkUrl(url, "Radio Internet", host(url));
                })
                .show();
    }

    @Override public void onResumeLastRadio() {
        String url = prefs.getString(LAST_URL, "");
        if (url.isEmpty()) {
            onAddRadio();
            return;
        }
        playNetworkUrl(url,
                prefs.getString(LAST_NAME, "Radio Internet"),
                prefs.getString(LAST_SUB, host(url)));
    }

    @Override public boolean hasSavedRadio() {
        return !prefs.getString(LAST_URL, "").isEmpty();
    }

    private void playFileUri(Uri uri, String title, String subtitle) {
        releasePlayer();
        view.setTitleText(title, subtitle);
        view.setStatus("PRÉPARATION…");
        view.appendLog("Préparation fichier : " + uri);

        try {
            MediaPlayer p = new MediaPlayer();
            configurePlayer(p, title, subtitle);
            if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
                p.setDataSource(uri.getPath());
            } else {
                p.setDataSource(this, uri);
            }
            player = p;
            p.prepareAsync();
        } catch (Exception e) {
            fail("FICHIER", e);
        }
    }

    private void playNetworkUrl(String url, String title, String subtitle) {
        releasePlayer();
        view.setTitleText(title, subtitle);
        view.setStatus("CONNEXION RADIO…");
        view.appendLog("Connexion : " + url);

        try {
            MediaPlayer p = new MediaPlayer();
            configurePlayer(p, title, subtitle);
            p.setDataSource(url);
            player = p;
            p.prepareAsync();
        } catch (Exception e) {
            fail("RADIO", e);
        }
    }

    private void configurePlayer(MediaPlayer p, String title, String subtitle) {
        p.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());

        p.setOnPreparedListener(mp -> {
            android.util.Log.i("NEXUS_RECOVERY", "AUDIO_PREPARED " + title);
            try {
                mp.start();
                android.util.Log.i("NEXUS_NATIVE_AUDIO", "PLAYING " + title);
                if ("NEXUS AUDIO TEST".equals(title)) {
                    android.util.Log.i("NEXUS_RECOVERY", "AUDIO_PASS");
                }
                view.setPlaying(true);
                view.setStatus("LECTURE ACTIVE");
                view.appendLog("PLAYING : " + title);
            } catch (Exception e) {
                fail("START", e);
            }
        });

        p.setOnCompletionListener(mp -> {
            view.setPlaying(false);
            view.setStatus("TERMINÉ");
            view.appendLog("Fin de lecture");
        });

        p.setOnBufferingUpdateListener((mp, percent) -> {
            if (percent > 0 && percent < 100) {
                view.setStatus("BUFFER " + percent + "%");
            }
        });

        p.setOnErrorListener((mp, what, extra) -> {
            view.setPlaying(false);
            String message = "MediaPlayer erreur what=" + what + " extra=" + extra;
            view.setStatus("ERREUR AUDIO");
            view.appendLog(message);
            android.util.Log.e("NEXUS_RECOVERY", "PLAYER_ERROR " + message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return true;
        });

        p.setOnInfoListener((mp, what, extra) -> {
            view.appendLog("Info player : " + what + "/" + extra);
            return false;
        });
    }

    private void updateProgress() {
        if (player == null) {
            view.setProgress(0f, 0, 0);
            return;
        }

        try {
            int pos = player.getCurrentPosition();
            int dur = player.getDuration();
            float progress = dur > 0 ? (float) pos / (float) dur : 0f;
            view.setProgress(progress, pos, dur);
        } catch (Exception ignored) {}
    }

    private void releasePlayer() {
        MediaPlayer old = player;
        player = null;
        if (old != null) {
            try { old.reset(); } catch (Exception ignored) {}
            try { old.release(); } catch (Exception ignored) {}
        }
        if (view != null) view.setPlaying(false);
    }

    private void searchRadios(String query, String field) {
        view.setStatus("RECHERCHE RADIO…");
        view.appendLog("Recherche radio : " + (query.isEmpty() ? "populaires" : query));

        network.execute(() -> {
            Exception last = null;

            for (String base : RADIO_APIS) {
                HttpURLConnection connection = null;
                try {
                    String endpoint = query.isEmpty()
                            ? base + "/json/stations/topvote/50?hidebroken=true"
                            : base + "/json/stations/search?hidebroken=true&order=votes&reverse=true&limit=50&"
                            + field + "=" + Uri.encode(query);

                    connection = (HttpURLConnection) new URL(endpoint).openConnection();
                    connection.setConnectTimeout(7000);
                    connection.setReadTimeout(9000);
                    connection.setRequestProperty("User-Agent", "NEXUS-MUSIC/2.0 Android");
                    connection.setRequestProperty("Accept", "application/json");

                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) throw new IOException("HTTP " + code);

                    StringBuilder body = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) body.append(line);
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
                } catch (Exception e) {
                    last = e;
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }

            Exception error = last;
            runOnUiThread(() -> {
                view.setStatus("RADIO INDISPONIBLE");
                view.appendLog("Radio Browser erreur : "
                        + (error == null ? "aucune station" : error.getClass().getSimpleName()));
                Toast.makeText(this,
                        "Annuaire radio indisponible. Essaie une URL directe.",
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void showStations(List<Station> stations) {
        view.setStatus("RADIOS TROUVÉES");
        view.appendLog(stations.size() + " radios trouvées");
        android.util.Log.i("NEXUS_RECOVERY", "RADIO_DIRECTORY_PASS " + stations.size());

        String[] labels = new String[stations.size()];
        for (int i = 0; i < stations.size(); i++) labels[i] = stations.get(i).label();

        new AlertDialog.Builder(this)
                .setTitle("Radios disponibles")
                .setItems(labels, (d, which) -> {
                    Station s = stations.get(which);
                    saveRadio(s.url, s.name, s.subtitle());
                    playNetworkUrl(s.url, s.name, s.subtitle());
                })
                .setNegativeButton("Fermer", null)
                .show();
    }

    private void fail(String area, Exception e) {
        String message = area + " : " + e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : " - " + e.getMessage());
        view.setStatus("ERREUR");
        view.appendLog(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void saveRadio(String url, String name, String subtitle) {
        prefs.edit()
                .putString(LAST_URL, url)
                .putString(LAST_NAME, name)
                .putString(LAST_SUB, subtitle)
                .apply();
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.isEmpty()) return value;
                }
            }
        } catch (Exception ignored) {}
        return "Musique locale";
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static String host(String value) {
        String host = Uri.parse(value).getHost();
        return host == null ? "Flux radio" : host;
    }

    private static void writeTestWav(File file, int seconds) throws IOException {
        int sampleRate = 44100;
        int channels = 2;
        int bits = 16;
        int frames = sampleRate * seconds;
        int blockAlign = channels * bits / 8;
        int dataSize = frames * blockAlign;
        int byteRate = sampleRate * blockAlign;

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
                double fadeIn = Math.min(1.0, i / 2205.0);
                double fadeOut = Math.min(1.0, (frames - i) / 2205.0);
                double envelope = fadeIn * fadeOut;
                short sample = (short) (Math.sin(
                        2.0 * Math.PI * 440.0 * i / sampleRate)
                        * 0.20 * envelope * Short.MAX_VALUE);
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
            StringBuilder s = new StringBuilder();
            if (!country.isEmpty()) s.append(country);
            if (!codec.isEmpty()) {
                if (s.length() > 0) s.append(" • ");
                s.append(codec);
            }
            if (bitrate > 0) {
                if (s.length() > 0) s.append(" • ");
                s.append(bitrate).append(" kb/s");
            }
            return s.length() == 0 ? "Radio Internet" : s.toString();
        }

        String label() {
            return name + "\n" + subtitle();
        }
    }
}
