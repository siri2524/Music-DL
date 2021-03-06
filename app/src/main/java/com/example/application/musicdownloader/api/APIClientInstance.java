package com.example.application.musicdownloader.api;

import android.util.Log;

import com.example.application.musicdownloader.MainActivity;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class APIClientInstance {
    private static final String YOUTUBE_URL = "https://www.googleapis.com/youtube/v3/";
    private static final String GITHUB_URL = "https://api.github.com/repos/CaptainDaVinci/Music-DL/releases/";
    public static String YOUTUBE_API_KEY ;
    private static String SERVER_URL;
    private static Retrofit youTubeRetrofit, serverRetrofit, githubRetrofit;

    public static Retrofit getYouTubeRetrofitInstance() {
        YOUTUBE_API_KEY = FirebaseRemoteConfig.getInstance().getString("YOUTUBE_API_KEY");
        Log.d(MainActivity.TAG, "YouTube API key " + YOUTUBE_API_KEY);
        if (youTubeRetrofit == null) {
            youTubeRetrofit = new Retrofit.Builder()
                    .baseUrl(YOUTUBE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return youTubeRetrofit;
    }

    public static Retrofit getServerRetrofitInstance() {
        SERVER_URL = FirebaseRemoteConfig.getInstance().getString("SERVER_URL");
        Log.d(MainActivity.TAG, "Server URL " + SERVER_URL);
        if (serverRetrofit == null) {
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(600, TimeUnit.SECONDS)
                    .readTimeout(600, TimeUnit.SECONDS)
                    .build();

            serverRetrofit = new Retrofit.Builder()
                    .baseUrl(SERVER_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return serverRetrofit;
    }

    public static Retrofit getGithubRetrofitInstance() {
        if (githubRetrofit == null) {
            githubRetrofit = new Retrofit.Builder()
                    .baseUrl(GITHUB_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return githubRetrofit;
    }
}
