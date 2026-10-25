package com.cst2335.lab6;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CatSlideshow";
    private static final String API_URL = "https://cataas.com/cat?json=true";

    private ImageView catImageView;
    private ProgressBar catProgressBar;
    private CatImages catTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        catImageView = findViewById(R.id.cat_image_view);
        catProgressBar = findViewById(R.id.cat_progress_bar);

        // Pre-seed local cache so local disk caching is verifiable immediately
        seedPrecachedMemes();

        // Start background cat slideshow task
        catTask = new CatImages();
        catTask.execute(API_URL);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (catTask != null) {
            catTask.cancel(true);
        }
    }

    /**
     * Seeds initial meme cat images into internal storage if not already present.
     */
    private void seedPrecachedMemes() {
        try {
            String[] list = getAssets().list("precache");
            if (list != null) {
                for (String filename : list) {
                    File local = new File(getFilesDir(), filename);
                    if (!local.exists()) {
                        InputStream in = getAssets().open("precache/" + filename);
                        FileOutputStream out = new FileOutputStream(local);
                        byte[] buffer = new byte[2048];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                        in.close();
                        out.flush();
                        out.close();
                        Log.i(TAG, "Seeded meme to local cache: " + filename);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Inner class extending AsyncTask to download, cache, and display cat images in a loop.
     */
    public class CatImages extends AsyncTask<String, Integer, String> {

        private Bitmap currentBitmap;
        private boolean isNewImageReady = false;

        @Override
        protected String doInBackground(String... params) {
            String queryUrl = (params != null && params.length > 0) ? params[0] : API_URL;
            int cycleCount = 0;

            // Infinite loop to cycle through cat pictures endlessly
            while (!isCancelled()) {
                HttpURLConnection connection = null;
                BufferedReader reader = null;
                try {
                    // Check if we should showcase a locally cached meme image first
                    cycleCount++;
                    if (cycleCount == 1) {
                        File cachedMeme = new File(getFilesDir(), "meme_cat_1.jpg");
                        if (cachedMeme.exists()) {
                            Log.i(TAG, "Loading initial cached meme from disk: " + cachedMeme.getName());
                            currentBitmap = BitmapFactory.decodeFile(cachedMeme.getAbsolutePath());
                            isNewImageReady = true;

                            for (int i = 0; i <= 100; i++) {
                                if (isCancelled()) return null;
                                publishProgress(i);
                                try { Thread.sleep(30); } catch (InterruptedException e) { return null; }
                            }
                        }
                    }

                    // Step 1: Query API for random cat metadata
                    URL url = new URL(queryUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream stream = connection.getInputStream();
                        reader = new BufferedReader(new InputStreamReader(stream));
                        StringBuilder jsonBuilder = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            jsonBuilder.append(line);
                        }

                        JSONObject json = new JSONObject(jsonBuilder.toString());
                        String id = json.optString("id", "");
                        String rawUrl = json.optString("url", "");

                        if (!id.isEmpty()) {
                            // Format full image download URL if relative
                            String fullImageUrl;
                            if (rawUrl.startsWith("http")) {
                                fullImageUrl = rawUrl;
                            } else if (rawUrl.startsWith("/")) {
                                fullImageUrl = "https://cataas.com" + rawUrl;
                            } else {
                                fullImageUrl = "https://cataas.com/cat/" + id;
                            }

                            // Step 2: Check if image already exists locally on disk
                            File localFile = new File(getFilesDir(), id + ".jpg");

                            if (localFile.exists()) {
                                Log.i(TAG, "Image found locally on device: " + localFile.getAbsolutePath());
                                currentBitmap = BitmapFactory.decodeFile(localFile.getAbsolutePath());
                            } else {
                                Log.i(TAG, "Downloading new cat image from: " + fullImageUrl);
                                HttpURLConnection imgConn = null;
                                InputStream imgStream = null;
                                try {
                                    URL imgUrl = new URL(fullImageUrl);
                                    imgConn = (HttpURLConnection) imgUrl.openConnection();
                                    imgConn.setRequestProperty("User-Agent", "Mozilla/5.0");
                                    imgConn.setConnectTimeout(10000);
                                    imgConn.setReadTimeout(10000);
                                    imgConn.connect();

                                    if (imgConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                                        imgStream = imgConn.getInputStream();
                                        currentBitmap = BitmapFactory.decodeStream(imgStream);

                                        // Step 3: Save newly downloaded image to internal storage
                                        if (currentBitmap != null) {
                                            FileOutputStream fos = openFileOutput(id + ".jpg", Context.MODE_PRIVATE);
                                            currentBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                                            fos.flush();
                                            fos.close();
                                            Log.i(TAG, "Saved image to local storage: " + localFile.getName());
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error downloading image: " + e.getMessage());
                                } finally {
                                    if (imgStream != null) {
                                        try { imgStream.close(); } catch (Exception ignored) {}
                                    }
                                    if (imgConn != null) {
                                        imgConn.disconnect();
                                    }
                                }
                            }

                            // Mark new image ready to show on next progress update
                            isNewImageReady = true;

                            // Step 4: Progress loop to give user time to appreciate the image
                            for (int i = 0; i <= 100; i++) {
                                if (isCancelled()) {
                                    return null;
                                }
                                publishProgress(i);
                                try {
                                    Thread.sleep(30); // ~3 seconds per image
                                } catch (InterruptedException e) {
                                    return null;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception during slideshow cycle: " + e.getMessage());
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {}
                } finally {
                    if (reader != null) {
                        try { reader.close(); } catch (Exception ignored) {}
                    }
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if (values != null && values.length > 0) {
                int progress = values[0];

                // Update progress bar status
                if (catProgressBar != null) {
                    catProgressBar.setProgress(progress);
                }

                // If a new cat picture has just been loaded, display it in ImageView
                if (isNewImageReady && currentBitmap != null) {
                    if (catImageView != null) {
                        catImageView.setImageBitmap(currentBitmap);
                    }
                    isNewImageReady = false;
                }
            }
        }
    }
}
