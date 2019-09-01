package com.example.application.musicdownloader;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.application.musicdownloader.api.APIClientInstance;
import com.example.application.musicdownloader.api.server.ServerData;
import com.example.application.musicdownloader.api.server.ServerDataService;
import com.example.application.musicdownloader.api.youtube.YouTubeData;
import com.example.application.musicdownloader.api.youtube.YouTubeDataService;
import com.example.application.musicdownloader.query.Encoding;
import com.example.application.musicdownloader.query.Quality;
import com.example.application.musicdownloader.query.Query;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.io.File;
import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MUSICDL";
    private EditText inputText;
    private TextView statusTextView, progressPercent;
    private ProgressBar spinningProgress, progressBar;
    private Query query;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getIntent().getExtras() != null) {
            Log.d(TAG, "From notification: " + getIntent().getExtras());
        }

        Log.i(TAG, "Starting main activity");
        inputText = findViewById(R.id.input_text);
        statusTextView = findViewById(R.id.status_text);
        spinningProgress = findViewById(R.id.spinning_progress);
        progressBar = findViewById(R.id.progress_bar);
        progressPercent = findViewById(R.id.progress_percent);

        FirebaseRemoteConfig firebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        firebaseRemoteConfig.setDefaultsAsync(R.xml.remote_config);
        firebaseRemoteConfig.activate();
        firebaseRemoteConfig.fetch();

        inputText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                try {
                    buttonClicked(findViewById(R.id.search_button));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            }
            return false;
        });
    }

    private void setStatus(String msg, int color) {
        statusTextView.setTextColor(color);
        statusTextView.setText(msg);
    }

    public void buttonClicked(final View view) {
        Log.i(TAG, "Download button clicked");

        if (this.getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }

        setStatus("", Color.BLACK);
        query = new Query();
        query.setSearch(inputText.getText().toString());

        if (query.getSearch().isEmpty()) {
            Toast.makeText(getApplicationContext(),
                    "Search Field is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Spinner format_spinner = findViewById(R.id.format_spinner);
        Spinner quality_spinner = findViewById(R.id.quality_spinner);

        String quality = quality_spinner.getSelectedItem().toString();
        String format = format_spinner.getSelectedItem().toString();

        if (quality.equals(getString(R.string.high_quality))) {
            query.setQuality(Quality.high);
        } else {
            query.setQuality(Quality.low);
        }

        if (format.equals(getString(R.string.audio))) {
            query.setEncoding(Encoding.mp3);
        } else {
            query.setEncoding(Encoding.mp4);
        }

        if (!hasNetwork()) {
            Toast.makeText(getApplicationContext(), "Network connection not found",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Log.i(TAG, "Query: " + query);
        checkStoragePermission();
    }

    public void clearButton(View view) {
        inputText.setText("");
        setStatus("", Color.BLACK);
        spinningProgress.setVisibility(View.GONE);
    }

    private boolean hasNetwork() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23) { // if android version >= 6.0
            if (ContextCompat.checkSelfPermission(
                    getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        1);
            } else {
                searchQuery();
            }
        } else {
            searchQuery();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Write permission granted");
                    searchQuery();
                } else {
                    Log.i(TAG, "Write permission denied");
                    Toast.makeText(MainActivity.this, "Permission denied to read your External storage",
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    void searchQuery() {
        setStatus("Searching", Color.BLACK);
        spinningProgress.setVisibility(View.VISIBLE);

        YouTubeDataService service = APIClientInstance.getYouTubeRetrofitInstance().create(YouTubeDataService.class);
        Call<YouTubeData> call = service.getYouTubeData("snippet",
                query.getSearch(),
                "video",
                "1",
                APIClientInstance.YOUTUBE_API_KEY);

        call.enqueue(new Callback<YouTubeData>() {
            @Override
            public void onResponse(Call<YouTubeData> call, Response<YouTubeData> response) {
                Log.i(TAG, "Obtained YouTube response");
                if (!response.isSuccessful() || response.body() == null) {
                    spinningProgress.setVisibility(View.GONE);
                    setStatus("Oh, snap! Search failed", Color.BLACK);
                    Log.d(TAG, "YouTube error: " + response.message());
                } else {
                    showResponse(response.body());
                }
            }

            @Override
            public void onFailure(Call<YouTubeData> call, Throwable t) {
                spinningProgress.setVisibility(View.GONE);
                setStatus("Oh, snap! Search failed", Color.BLACK);
                Log.d(TAG, "YouTube error: " + t.getMessage());
            }
        });
    }

    private void showResponse(final YouTubeData data) {
        spinningProgress.setVisibility(View.GONE);
        setStatus("", Color.BLACK);

        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.activity_dialog, null);
        TextView titleTextView = view.findViewById(R.id.title);
        ImageView imageView = view.findViewById(R.id.thumbnail);

        Log.i(TAG, "Set information for confirmation dialog");
        titleTextView.setText(data.getTitle());
        setThumbnail(imageView, data.getThumbnailURL());

        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    getDownloadLink(data);
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    setStatus("Cancelled", Color.BLACK);
                    break;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view)
                .setTitle("Confirmation Alert")
                .setMessage("Download is about to start\nAre you sure you want to download ?")
                .setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("No", dialogClickListener)
                .setCancelable(false)
                .show();
    }

    private void setThumbnail(final ImageView imageView, String thumbnailUrl) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(thumbnailUrl).build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.d(TAG, "Thumbnail: " + e.getMessage());
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                try {
                    runOnUiThread(() -> imageView.setImageBitmap(BitmapFactory.decodeStream(response.body().byteStream())));
                } catch (Exception e) {
                    Log.d(TAG, "Thumbnail onResponse: " + e.getMessage());
                    // pass
                }
            }
        });
    }

    private void getDownloadLink(final YouTubeData data) {
        spinningProgress.setVisibility(View.VISIBLE);
        setStatus("Converting...", Color.BLACK);

        ServerDataService service = APIClientInstance.getServerRetrofitInstance().create(ServerDataService.class);
        Call<ServerData> call = service.getDownloadLink(data.getId(),
                query.getEncoding().toString(),
                query.getQuality().toString());

        call.enqueue(new Callback<ServerData>() {
            @Override
            public void onResponse(Call<ServerData> call, Response<ServerData> response) {
                spinningProgress.setVisibility(View.GONE);
                Log.i(TAG, "Obtained server response");

                if (!response.isSuccessful() || response.body() == null) {
                    Log.d(TAG, "Server error: " + response.message());
                    setStatus("Oh, snap! Conversion failed", Color.BLACK);
                } else {
                    String downloadLink = response.body().getDownloadLink();
                    Log.d(TAG, "Download link: " + downloadLink);
                    downloadFile(downloadLink, data.getTitle());
                }
            }

            @Override
            public void onFailure(Call<ServerData> call, Throwable t) {
                spinningProgress.setVisibility(View.GONE);
                Log.d(TAG, "Server error: " + t.getMessage());
                setStatus("Oh, snap! Conversion failed.", Color.BLACK);
            }
        });
    }

    private void downloadFile(String downloadLink, String fileName) {
        if (downloadLink == null) {
            setStatus("Failed to get download link", Color.BLACK);
            return;
        }

        /*setStatus("Opening link in browser...", Color.BLACK);
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadLink));
        startActivity(browserIntent); */

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(downloadLink).build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.d(TAG, "Download: " + e.getMessage());
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    setStatus("Download failed", Color.BLACK);
                    return;
                }

                runOnUiThread(() -> {
                    setStatus("Downloading...", Color.BLACK);
                    progressBar.setVisibility(View.VISIBLE);
                    progressPercent.setVisibility(View.VISIBLE);
                });

                ResponseBody body = response.body();
                BufferedSource source = body.source();
                long contentLength = body.contentLength();
                File downloadPath = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS),
                        fileName + "." + query.getEncoding().toString());
                BufferedSink sink = Okio.buffer(Okio.sink(downloadPath));
                Buffer sinkBuffer = sink.buffer();

                long totalBytesRead = 0;
                int bufferSize = 8 * 1024;

                for (long bytesRead; (bytesRead = source.read(sinkBuffer, bufferSize)) != -1; ) {
                    sink.emit();
                    totalBytesRead += bytesRead;
                    int progress = (int) Math.ceil((totalBytesRead * 100) / contentLength);
                    runOnUiThread(() -> {
                        progressBar.setProgress(progress);
                        progressPercent.setText(String.format("%d%%", progress));
                    });
                }
                sink.flush();
                sink.close();
                source.close();
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(downloadPath)));

                runOnUiThread(() -> {
                    setStatus("Download completed", Color.BLACK);
                    progressPercent.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                });
                Log.d(TAG, "Download completed");
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public void onAboutActivity(MenuItem menuItem) {
        Log.d(TAG, "Open about activity");
        Intent aboutActivityIntent = new Intent(MainActivity.this, AboutActivity.class);
        startActivity(aboutActivityIntent);
    }

}