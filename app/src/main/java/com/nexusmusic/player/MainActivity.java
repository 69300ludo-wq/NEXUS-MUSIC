package com.nexusmusic.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
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

    private static final String[] RADIO_APIS = {
            "https://de1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();

    private NexusMusicView view;
    private MediaPlayer player;
    private ToneGenerator tone;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (player != null) {
                try {
                    int duration = player.getDuration();
                    int position = player.getCurrentPosition();
                    float progress = duration > 0 ? (float) position / duration : 0f;
                    view.setProgress(progress, position, duration);
                } catch (Exception ignored) {}
            }
            handler.postDelayed(this, 500L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF02040A);
        getWindow().setNavigationBarColor(0xFF02040A);

        view = new NexusMusicView(this);
        view.setActions(this);
        setContentView(view);

        log("NEXUS SAFE MODE 2.1 démarré");
        log("Android " + Build.VERSION.RELEASE + " / SDK " + Build.VERSION.SDK_INT);
        log("Appareil : " + Build.MANUFACTURER + " " + Build.MODEL);
        view.setStatus("PRÊT");

        handler.post(ticker);

        Intent i = getIntent();
        if (i.getBooleanExtra("self_test_tone", false)) {
            handler.postDelayed(this::onTestSpeaker, 500L);
        } else if (i.getBooleanExtra("self_test_picker", false)) {
            handler.postDelayed(this::onPickLocalAudio, 500L);
        } else if (i.getBooleanExtra("self_test_radio", false)) {
            handler.postDelayed(() -> searchRadios("France"), 500L);
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        network.shutdownNow();
        releasePlayer();
        if (tone != null) {
            try { tone.release(); } catch (Exception ignored) {}
            tone = null;
        }
        super.onDestroy();
    }

    @Override public void onTestSpeaker() {
        try {
            if (tone != null) {
                try { tone.release(); } catch (Exception ignored) {}
            }
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
            boolean started = tone.startTone(ToneGenerator.TONE_DTMF_1, 1200);
            log("Test haut-parleur déclenché : " + started);
            android.util.Log.i("NEXUS_SAFE", "TONE_TRIGGERED " + started);
            view.setStatus(started ? "BIP ENVOYÉ" : "BIP REFUSÉ");
            if (!started) {
                Toast.makeText(this, "Android a refusé le bip système.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            fail("BIP", e);
        }
    }

    @Override public void onPickLocalAudio() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            log("Ouverture du sélecteur Android");
            startActivityForResult(intent, PICK_AUDIO);
        } catch (Exception e) {
            fail("SÉLECTEUR", e);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_AUDIO) return;

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            log("Sélection annulée");
            return;
        }

        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}

        String name = displayName(uri);
        log("Fichier : " + name);
        playUri(uri, name, "Fichier local");
    }

    @Override public void onTogglePlayPause() {
        if (player == null) {
            Toast.makeText(this, "Aucun média chargé.", Toast.LENGTH_SHORT).show();
            log("PLAY demandé sans média");
            return;
        }

        try {
            if (player.isPlaying()) {
                player.pause();
                view.setPlaying(false);
                view.setStatus("PAUSE");
                log("Pause");
            } else {
                player.start();
                view.setPlaying(true);
                view.setStatus("LECTURE ACTIVE");
                log("Lecture");
            }
        } catch (Exception e) {
            fail("PLAY/PAUSE", e);
        }
    }

    @Override public void onBrowseRadios() {
        EditText input = new EditText(this);
        input.setHint("Ex: France, Jazz, RTL, FIP");
        input.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle("Rechercher une radio")
                .setView(input)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Rechercher", (d, w) ->
                        searchRadios(input.getText().toString().trim()))
                .show();
    }

    @Override public void onAddRadio() {
        EditText input = new EditText(this);
        input.setHint("https://... ou http://...");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        new AlertDialog.Builder(this)
                .setTitle("URL radio directe")
                .setView(input)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Lire", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!(url.startsWith("https://") || url.startsWith("http://"))) {
                        Toast.makeText(this, "URL HTTP/HTTPS invalide.", Toast.LENGTH_LONG).show();
                        log("URL radio invalide");
                        return;
                    }
                    playNetwork(url, "Radio Internet", host(url));
                })
                .show();
    }

    @Override public void onCopyDiagnostic() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String text = view.getDiagnosticText();
        clipboard.setPrimaryClip(ClipData.newPlainText("NEXUS diagnostic", text));
        Toast.makeText(this, "Diagnostic copié.", Toast.LENGTH_SHORT).show();
        log("Diagnostic copié");
    }

    private void playUri(Uri uri, String title, String subtitle) {
        releasePlayer();
        view.setTitleText(title, subtitle);
        view.setStatus("PRÉPARATION…");
        log("Préparation : " + uri);

        try {
            MediaPlayer p = new MediaPlayer();
            configurePlayer(p, title);
            p.setDataSource(this, uri);
            player = p;
            p.prepareAsync();
        } catch (Exception e) {
            fail("FICHIER", e);
        }
    }

    private void playNetwork(String url, String title, String subtitle) {
        releasePlayer();
        view.setTitleText(title, subtitle);
        view.setStatus("CONNEXION…");
        log("Connexion radio : " + url);

        try {
            MediaPlayer p = new MediaPlayer();
            configurePlayer(p, title);
            p.setDataSource(url);
            player = p;
            p.prepareAsync();
        } catch (Exception e) {
            fail("RADIO", e);
        }
    }

    private void configurePlayer(MediaPlayer p, String title) {
        p.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());

        p.setOnPreparedListener(mp -> {
            log("Média préparé");
            android.util.Log.i("NEXUS_SAFE", "MEDIA_PREPARED " + title);
            try {
                mp.start();
                view.setPlaying(true);
                view.setStatus("LECTURE ACTIVE");
                log("PLAYING : " + title);
            } catch (Exception e) {
                fail("START", e);
            }
        });

        p.setOnCompletionListener(mp -> {
            view.setPlaying(false);
            view.setStatus("TERMINÉ");
            log("Fin de lecture");
        });

        p.setOnErrorListener((mp, what, extra) -> {
            view.setPlaying(false);
            String message = "MediaPlayer what=" + what + " extra=" + extra;
            view.setStatus("ERREUR AUDIO");
            log(message);
            android.util.Log.e("NEXUS_SAFE", message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return true;
        });

        p.setOnInfoListener((mp, what, extra) -> {
            log("Info MediaPlayer " + what + "/" + extra);
            return false;
        });
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

    private void searchRadios(String query) {
        view.setStatus("RECHERCHE RADIO…");
        log("Recherche : " + (query.isEmpty() ? "populaires" : query));

        network.execute(() -> {
            Exception last = null;

            for (String base : RADIO_APIS) {
                HttpURLConnection connection = null;
                try {
                    String endpoint = query.isEmpty()
                            ? base + "/json/stations/topvote/30?hidebroken=true"
                            : base + "/json/stations/search?hidebroken=true&limit=30&order=votes&reverse=true&name="
                            + Uri.encode(query);

                    connection = (HttpURLConnection) new URL(endpoint).openConnection();
                    connection.setConnectTimeout(7000);
                    connection.setReadTimeout(9000);
                    connection.setRequestProperty("User-Agent", "NEXUS-MUSIC/2.1 Android");
                    connection.setRequestProperty("Accept", "application/json");

                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) throw new Exception("HTTP " + code);

                    StringBuilder body = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) body.append(line);
                    }

                    JSONArray array = new JSONArray(body.toString());
                    List<Station> stations = new ArrayList<>();

                    for (int i = 0; i < array.length() && stations.size() < 20; i++) {
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
                fail("RADIO DIRECTORY",
                        error == null ? new Exception("aucun résultat") : error);
            });
        });
    }

    private void showStations(List<Station> stations) {
        view.setStatus("RADIOS TROUVÉES");
        log(stations.size() + " stations trouvées");
        android.util.Log.i("NEXUS_SAFE", "RADIO_PASS " + stations.size());

        String[] labels = new String[stations.size()];
        for (int i = 0; i < stations.size(); i++) labels[i] = stations.get(i).label();

        new AlertDialog.Builder(this)
                .setTitle("Radios disponibles")
                .setItems(labels, (d, which) -> {
                    Station s = stations.get(which);
                    playNetwork(s.url, s.name, s.subtitle());
                })
                .setNegativeButton("Fermer", null)
                .show();
    }

    private void fail(String area, Exception e) {
        String message = area + " : " + e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : " - " + e.getMessage());
        view.setStatus("ERREUR");
        log(message);
        android.util.Log.e("NEXUS_SAFE", message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void log(String text) {
        view.appendLog(text);
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

    private static String host(String value) {
        String host = Uri.parse(value).getHost();
        return host == null ? "Flux radio" : host;
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
