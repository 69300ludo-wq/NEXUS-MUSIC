package com.nexusmusic.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean receiverRegistered;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateProgress();
            handler.postDelayed(this, 500L);
        }
    };

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                pausePlayback();
            }
        }
    };

    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        if (player == null) return;
        try {
            if (change == AudioManager.AUDIOFOCUS_LOSS) {
                pausePlayback();
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                if (player.isPlaying()) player.pause();
                view.setPlaying(false);
                view.setStatus("PAUSE AUDIO");
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                player.setVolume(0.25f, 0.25f);
            } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                player.setVolume(1f, 1f);
            }
        } catch (Exception ignored) {}
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF02040A);
        getWindow().setNavigationBarColor(0xFF02040A);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build();
        }

        view = new NexusMusicView(this);
        view.setActions(this);
        setContentView(view);
        view.setStatus("PRÊT");

        IntentFilter noisy = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(noisyReceiver, noisy, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(noisyReceiver, noisy);
        }
        receiverRegistered = true;

        handler.post(ticker);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        network.shutdownNow();
        if (receiverRegistered) {
            try { unregisterReceiver(noisyReceiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        releasePlayer();
        super.onDestroy();
    }

    @Override public void onPickLocalAudio() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, PICK_AUDIO);
        } catch (Exception e) {
            showError("Impossible d'ouvrir les fichiers", e);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_AUDIO || resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}

        playUri(uri, displayName(uri), "Fichier local");
    }

    @Override public void onTogglePlayPause() {
        if (player == null) {
            Toast.makeText(this, "Choisis d'abord une musique ou une radio.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (player.isPlaying()) {
                player.pause();
                view.setPlaying(false);
                view.setStatus("PAUSE");
            } else {
                if (!requestAudioFocus()) {
                    Toast.makeText(this, "Le téléphone refuse le focus audio.", Toast.LENGTH_LONG).show();
                    return;
                }
                player.start();
                view.setPlaying(true);
                view.setStatus("LECTURE ACTIVE");
            }
        } catch (Exception e) {
            showError("Lecture impossible", e);
        }
    }

    @Override public void onSeekTo(float fraction) {
        if (player == null) return;
        try {
            int duration = player.getDuration();
            if (duration > 0) {
                int target = (int) (duration * Math.max(0f, Math.min(1f, fraction)));
                player.seekTo(target);
            }
        } catch (Exception ignored) {}
    }

    @Override public void onBrowseRadios() {
        final EditText input = new EditText(this);
        input.setHint("Ex : FIP, France, Jazz");
        input.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle("Rechercher une radio")
                .setMessage("Recherche par nom, pays et genre.")
                .setView(input)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Rechercher", (d, w) ->
                        searchRadios(input.getText().toString().trim()))
                .show();
    }

    @Override public void onAddRadio() {
        final EditText input = new EditText(this);
        input.setHint("https://... ou http://...");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        new AlertDialog.Builder(this)
                .setTitle("Flux radio direct")
                .setView(input)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Lire", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!(url.startsWith("https://") || url.startsWith("http://"))) {
                        Toast.makeText(this, "Adresse HTTP/HTTPS invalide.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    playNetwork(url, "Radio Internet", host(url));
                })
                .show();
    }

    private void playUri(Uri uri, String title, String subtitle) {
        releasePlayer();
        view.setTitleText(title, subtitle);
        view.setStatus("PRÉPARATION…");

        try {
            MediaPlayer next = new MediaPlayer();
            configurePlayer(next, title);
            next.setDataSource(this, uri);
            player = next;
            next.prepareAsync();
        } catch (Exception e) {
            showError("Impossible de lire ce fichier", e);
        }
    }

    private void playNetwork(String url, String title, String subtitle) {
        releasePlayer();
        view.setTitleText(title, subtitle);
        view.setStatus("CONNEXION RADIO…");

        try {
            MediaPlayer next = new MediaPlayer();
            configurePlayer(next, title);
            next.setDataSource(url);
            player = next;
            next.prepareAsync();
        } catch (Exception e) {
            showError("Impossible de lire cette radio", e);
        }
    }

    private void configurePlayer(MediaPlayer p, String title) {
        p.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());

        try {
            p.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        } catch (Exception ignored) {}

        p.setOnPreparedListener(mp -> {
            if (!requestAudioFocus()) {
                view.setStatus("FOCUS AUDIO REFUSÉ");
                return;
            }
            try {
                mp.start();
                view.setPlaying(true);
                view.setStatus("LECTURE ACTIVE");
            } catch (Exception e) {
                showError("Démarrage audio impossible", e);
            }
        });

        p.setOnCompletionListener(mp -> {
            view.setPlaying(false);
            view.setStatus("TERMINÉ");
        });

        p.setOnBufferingUpdateListener((mp, percent) -> {
            if (percent > 0 && percent < 100) {
                view.setStatus("BUFFER " + percent + "%");
            }
        });

        p.setOnErrorListener((mp, what, extra) -> {
            view.setPlaying(false);
            String message = "Erreur audio Android " + what + "/" + extra;
            view.setStatus("ERREUR AUDIO");
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return true;
        });
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) return true;
        int result;
        if (Build.VERSION.SDK_INT >= 26) {
            result = audioManager.requestAudioFocus(focusRequest);
        } else {
            result = audioManager.requestAudioFocus(
                    focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest);
            } else {
                audioManager.abandonAudioFocus(focusListener);
            }
        } catch (Exception ignored) {}
    }

    private void pausePlayback() {
        if (player == null) return;
        try {
            if (player.isPlaying()) player.pause();
        } catch (Exception ignored) {}
        view.setPlaying(false);
        view.setStatus("PAUSE");
    }

    private void releasePlayer() {
        MediaPlayer old = player;
        player = null;
        if (old != null) {
            try { old.reset(); } catch (Exception ignored) {}
            try { old.release(); } catch (Exception ignored) {}
        }
        abandonAudioFocus();
        if (view != null) {
            view.setPlaying(false);
            view.setProgress(0f, 0L, 0L);
        }
    }

    private void updateProgress() {
        if (player == null) return;
        try {
            int position = player.getCurrentPosition();
            int duration = player.getDuration();
            float progress = duration > 0 ? (float) position / duration : 0f;
            view.setProgress(progress, position, duration);
        } catch (Exception ignored) {}
    }

    private void searchRadios(String query) {
        view.setStatus("RECHERCHE RADIO…");
        network.execute(() -> {
            List<Station> stations = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            String[] fields = query.isEmpty()
                    ? new String[]{"popular"}
                    : new String[]{"name", "country", "tag"};

            outer:
            for (String base : RADIO_APIS) {
                for (String field : fields) {
                    HttpURLConnection connection = null;
                    try {
                        String endpoint;
                        if ("popular".equals(field)) {
                            endpoint = base + "/json/stations/topvote/40?hidebroken=true";
                        } else {
                            endpoint = base
                                    + "/json/stations/search?hidebroken=true&order=votes&reverse=true&limit=30&"
                                    + field + "=" + Uri.encode(query);
                        }

                        connection = (HttpURLConnection) new URL(endpoint).openConnection();
                        connection.setConnectTimeout(5000);
                        connection.setReadTimeout(7000);
                        connection.setRequestProperty("User-Agent", "NEXUS-MUSIC/3.0 Android");
                        connection.setRequestProperty("Accept", "application/json");

                        if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                            continue;
                        }

                        StringBuilder body = new StringBuilder();
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                                connection.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) body.append(line);
                        }

                        JSONArray array = new JSONArray(body.toString());
                        for (int i = 0; i < array.length() && stations.size() < 30; i++) {
                            JSONObject o = array.optJSONObject(i);
                            if (o == null) continue;

                            String stream = o.optString(
                                    "url_resolved", o.optString("url", "")).trim();
                            if (!(stream.startsWith("https://") || stream.startsWith("http://"))) {
                                continue;
                            }
                            if (!seen.add(stream)) continue;

                            stations.add(new Station(
                                    o.optString("name", "Radio").trim(),
                                    stream,
                                    o.optString("country", "").trim(),
                                    o.optString("codec", "").trim(),
                                    o.optInt("bitrate", 0)));
                        }

                        if (stations.size() >= 12) break outer;
                    } catch (Exception ignored) {
                    } finally {
                        if (connection != null) connection.disconnect();
                    }
                }
            }

            runOnUiThread(() -> {
                if (stations.isEmpty()) {
                    view.setStatus("AUCUNE RADIO");
                    Toast.makeText(this,
                            "Aucune station trouvée. Essaie un autre mot ou une URL directe.",
                            Toast.LENGTH_LONG).show();
                } else {
                    showStations(stations);
                }
            });
        });
    }

    private void showStations(List<Station> stations) {
        view.setStatus("RADIOS TROUVÉES");
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

    private void showError(String prefix, Exception e) {
        view.setPlaying(false);
        view.setStatus("ERREUR");
        String detail = e.getMessage();
        String message = prefix + (detail == null || detail.isEmpty() ? "" : " : " + detail);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
                s.append(codec.toUpperCase(Locale.US));
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
