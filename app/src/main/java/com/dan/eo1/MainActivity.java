package com.dan.eo1;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.squareup.picasso.Picasso;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class MainActivity extends AppCompatActivity {

    public int per_page = 500; //500 max
    public int interval = 300;
    private int currentPosition = 0;
    private Handler handler = new Handler();
    private ImageView imageView;
    private VideoView videoView;

    private String playlistsUrl = "";
    private String playlist = "";


    private int startQuietHour = -1;
    private int endQuietHour = -1;
    private List<String> mediaItems;
    private boolean isInQuietHours = false;
    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private float lastLightLevel;
    private boolean slideshowpaused = false;
    private ProgressBar progress;
    boolean screenon = true;
    boolean autobrightness = true;
    float brightnesslevel = 0.5f;
    int page = 1;
    int totalPages = 0;

    private MyMessageService mService;
    private boolean mBound = false;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MyMessageService.LocalBinder binder = (MyMessageService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;

//            int data = mService.getSomeData(); // Accessing data
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);

        File cacheDir = new File(getCacheDir(), "picasso-cache");
        if (cacheDir.exists() && cacheDir.isDirectory()) {
            for (File file : cacheDir.listFiles()) {
                file.delete();
            }
        }

        loadsettings();

        if (playlistsUrl.isEmpty() || playlist.isEmpty()) {
            showSetupDialog();
        }

        imageView = findViewById(R.id.imageView);
        videoView = findViewById(R.id.videoView);
        progress = findViewById(R.id.progressBar);

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (Math.abs(event.values[0] - lastLightLevel) >= 10.0f) {
                    adjustScreenBrightness(event.values[0]);
                }
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
            }
        };
        mSensorManager.registerListener(listener, mLightSensor, SensorManager.SENSOR_DELAY_UI);

        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, new IntentFilter("MSG_RECEIVED"));

        bindService(new Intent(this, MyMessageService.class), connection, Context.BIND_AUTO_CREATE);

        if (quietHoursCalc()) {
            isInQuietHours = true;
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.screenBrightness = 0;
            getWindow().setAttributes(params);
            videoView.setVisibility(View.GONE);
            imageView.setVisibility(View.GONE);
        }

        super.onCreate(savedInstanceState);
    }

    boolean quietHoursCalc() {
        Calendar calendar = Calendar.getInstance();
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        int normalizedStart = (startQuietHour + 24) % 24;
        int normalizedEnd = (endQuietHour + 24) % 24;
        if ((currentHour >= normalizedStart && currentHour < normalizedEnd) ||
                (normalizedStart > normalizedEnd && (currentHour >= normalizedStart || currentHour < normalizedEnd))) {
            return true;
        } else {
            return false;
        }
    }

    void loadsettings() {
        SharedPreferences settings = getSharedPreferences("prefs", MODE_PRIVATE);
        playlistsUrl = settings.getString("playlistsUrl", "");
        playlist = settings.getString("playlist", "");
        startQuietHour = settings.getInt("startQuietHour", -1);
        endQuietHour = settings.getInt("endQuietHour", -1);
        interval = settings.getInt("interval", 300);
        autobrightness = settings.getBoolean("autobrightness", true);
        brightnesslevel = settings.getFloat("brightnesslevel", 0.5f);
    }

    @Override
    protected void onResume() {
        if (!playlistsUrl.isEmpty() || !playlist.isEmpty()) {
            loadImagesFromWeb();
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
    }

    @SuppressLint("InvalidWakeLockTag")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_C) {
            showSetupDialog();
            return super.onKeyDown(keyCode, event);
        }

        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            if (!isInQuietHours) progress.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.INVISIBLE);
            videoView.setVisibility(View.INVISIBLE);
            slideshowpaused = false;
            mService.setSlideshowPaused(slideshowpaused);
            showNextImage();
            return super.onKeyDown(keyCode, event);
        }

        if (keyCode == 132 || keyCode == 134) {
            //top button pushed
            WindowManager.LayoutParams params = getWindow().getAttributes();
            if (screenon) {
                params.screenBrightness = 0;
                screenon = false;
                imageView.setVisibility(View.INVISIBLE);
                videoView.setVisibility(View.INVISIBLE);
            } else {
                params.screenBrightness = brightnesslevel;
                screenon = true;
                imageView.setVisibility(View.VISIBLE);
                videoView.setVisibility(View.VISIBLE);
            }
            getWindow().setAttributes(params);
            return super.onKeyDown(keyCode, event);
        }

        return super.onKeyDown(keyCode, event);
    }

    private void showSetupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View customLayout = getLayoutInflater().inflate(R.layout.options, null);
        builder.setView(customLayout);

        final EditText playlistsUrlEditText = customLayout.findViewById(R.id.editTextPlaylistsUrl);
        final EditText playlistEditText = customLayout.findViewById(R.id.editTextPlaylist);

        final Spinner startHourSpinner = customLayout.findViewById(R.id.startHourSpinner);
        final Spinner endHourSpinner = customLayout.findViewById(R.id.endHourSpinner);
        final Button btnLoadConfig = customLayout.findViewById(R.id.btnLoadConfig);
        final EditText editTextCustomTag = customLayout.findViewById(R.id.editTextCustomTag);
        final EditText editTextInterval = customLayout.findViewById(R.id.editTextInterval);
        final CheckBox cbAutoBrightness = customLayout.findViewById(R.id.cbBrightnessAuto);
        final SeekBar sbBrightness = customLayout.findViewById(R.id.sbBrightness);

        playlistsUrlEditText.setText(playlistsUrl);
        playlistEditText.setText(playlist);

        editTextInterval.setText(String.valueOf(interval));
        if (autobrightness) {
            cbAutoBrightness.setChecked(true);
            sbBrightness.setVisibility(View.GONE);
        }

        sbBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                brightnesslevel = i / 10f;
                WindowManager.LayoutParams params = getWindow().getAttributes();
                params.screenBrightness = brightnesslevel;
                getWindow().setAttributes(params);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        sbBrightness.setProgress((int) (brightnesslevel * 10));

        cbAutoBrightness.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                autobrightness = b;
                if (b)
                    sbBrightness.setVisibility(View.GONE);
                else
                    sbBrightness.setVisibility(View.VISIBLE);
            }
        });

        // Set up the Spinners for start and end hour
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) {
            hours[i] = String.format("%02d", i);
        }
        ArrayAdapter<String> hourAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, hours);
        hourAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        startHourSpinner.setAdapter(hourAdapter);
        if (startQuietHour != -1) startHourSpinner.setSelection(startQuietHour);
        endHourSpinner.setAdapter(hourAdapter);
        if (endQuietHour != -1) endHourSpinner.setSelection(endQuietHour);

        btnLoadConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadsDir, "config.txt");
                if (!file.exists()) {
                    Toast.makeText(MainActivity.this, "Can't find config.txt", Toast.LENGTH_SHORT).show();
                } else {
                    StringBuilder sb = new StringBuilder();
                    try {
                        BufferedReader br = new BufferedReader(new FileReader(file));
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                        br.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    playlistsUrlEditText.setText(sb.toString().split("\n")[0]);
                    playlistEditText.setText(sb.toString().split("\n")[1]);
                }
            }
        });

        builder.setTitle("Setup")
                .setCancelable(false)
                .setView(customLayout)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        playlistsUrl = playlistsUrlEditText.getText().toString().trim();

                        // Remove trailing slash from playlists URL if it exists
                        if (playlistsUrl.endsWith("/")) {
                            playlistsUrl = playlistsUrl.substring(0, playlistsUrl.length() - 1);
                        }

                        playlist = playlistEditText.getText().toString().trim();

                        startQuietHour = Integer.parseInt(startHourSpinner.getSelectedItem().toString());
                        endQuietHour = Integer.parseInt(endHourSpinner.getSelectedItem().toString());
                        interval = Integer.parseInt(editTextInterval.getText().toString().trim());
                        autobrightness = cbAutoBrightness.isChecked();

                        if (!playlistsUrl.isEmpty() && !playlist.isEmpty()) {
                            SharedPreferences settings = getSharedPreferences("prefs", MODE_PRIVATE);
                            SharedPreferences.Editor editor = settings.edit();
                            editor.putString("playlistsUrl", playlistsUrl);
                            editor.putString("playlist", playlist);
                            editor.putInt("startQuietHour", startQuietHour);
                            editor.putInt("endQuietHour", endQuietHour);
                            editor.putInt("interval", interval);
                            editor.putBoolean("autobrightness", autobrightness);
                            editor.putFloat("brightnesslevel", brightnesslevel);
                            editor.apply();

                            Toast.makeText(MainActivity.this, "Saved!  Hit 'C' to come back here later.", Toast.LENGTH_SHORT).show();

                            if (quietHoursCalc()) isInQuietHours = true; else isInQuietHours = false;

                            loadImagesFromWeb();

                            if (isInQuietHours) adjustScreenBrightness(0);
                        } else {
                            Toast.makeText(MainActivity.this, "Please enter User ID and API Key", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        builder.show();
    }

    private void startSlideshow() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showSlideshow();
                handler.postDelayed(this, 1000 * interval);
            }
        }, 1000 * interval);

        showSlideshow();
    }

    private void showSlideshow() {
        Toast.makeText(MainActivity.this, "showslideshow " + quietHoursCalc() + " " + autobrightness, Toast.LENGTH_LONG).show();
        if (quietHoursCalc()) {
            if (!isInQuietHours) {
                //entering quiet, turn off screen
                isInQuietHours = true;
                WindowManager.LayoutParams params = getWindow().getAttributes();
                params.screenBrightness = 0;
                getWindow().setAttributes(params);
                videoView.setVisibility(View.GONE);
                imageView.setVisibility(View.GONE);
            }
        } else {
            if (isInQuietHours) {
                isInQuietHours = false;
            }
            if (autobrightness) {
                adjustScreenBrightness(lastLightLevel);
            } else {
                WindowManager.LayoutParams params = getWindow().getAttributes();
                params.screenBrightness = brightnesslevel;
                getWindow().setAttributes(params);
            }
            showNextImage();
        }
    }

    public static String getMD5Hash(String input) {
        try {
            // Create MessageDigest instance for MD5
            MessageDigest md = MessageDigest.getInstance("MD5");

            // Add input bytes to digest
            md.update(input.getBytes());

            // Get the hash bytes
            byte[] byteData = md.digest();

            // Convert the byte to hex format
            StringBuilder sb = new StringBuilder();
            for (byte b : byteData) {
                sb.append(String.format("%02x", b & 0xff));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private void showNextImage() {

        if (mediaItems != null && !mediaItems.isEmpty() && slideshowpaused==false) {
            if (currentPosition >= mediaItems.size()) {
                loadImagesFromWeb(); return;
            }

            try {

                String mediaItem = mediaItems.get(currentPosition);

                if (!mediaItem.endsWith(".mp4")) {
                    videoView.setVisibility(View.INVISIBLE);
                    imageView.setVisibility(View.VISIBLE);

                    String imageUrl = mediaItem;
                    Picasso.get().load(imageUrl).fit().centerInside().into(imageView); //Picasso.get().load(imageUrl).fit().centerCrop().into(imageView);
                    progress.setVisibility(View.INVISIBLE);

                    mService.setCurrentMediaUrl(imageUrl);

                    currentPosition++;
                    mService.setCurrentPosition(currentPosition);

                } else {
                    imageView.setVisibility(View.INVISIBLE);

                    MediaController mediaController = new MediaController(this);
                    mediaController.setAnchorView(videoView);
                    mediaController.setVisibility(View.INVISIBLE);

                    videoView.setMediaController(mediaController);

                    String url = mediaItem;
                    mService.setCurrentMediaUrl(url);
                    new DownloadVideoTask().execute(url, getMD5Hash(url));
                }
            } catch (Exception ex) {
                progress.setVisibility(View.VISIBLE);
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showNextImage();
                    }
                }, 10000);
            }
        }
    }

    private void loadImagesFromWeb() {
        if (!isInQuietHours) progress.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.INVISIBLE);
        videoView.setVisibility(View.INVISIBLE);

        if (Util.isNetworkAvailable(this)) {
            Toast.makeText(MainActivity.this, "IP = " + Util.getIPAddress(), Toast.LENGTH_LONG).show();

            Intent serviceIntent = new Intent(this, MyMessageService.class);
            startService(serviceIntent);

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(playlistsUrl)
                    .client(new okhttp3.OkHttpClient())
                    .build();

            WebService apiService = retrofit.create(WebService.class);
            Call<ResponseBody> call;

            call = apiService.getPlaylist("/" + playlist + "/");

            call.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        try {
                            String htmlString = response.body().string();
                            Document document = Jsoup.parse(htmlString);

                            mService.setPlaylist(playlist);
                            mediaItems = new ArrayList<>();

                            Elements links = document.select("a[href]");
                            for (Element link : links) {
                                String href = link.attr("href");
                                if (!href.endsWith("/")) {
                                    String absoluteLinkUrl = playlistsUrl + "/" + playlist + "/" + href;
                                    mediaItems.add(absoluteLinkUrl);
                                    System.out.println(absoluteLinkUrl);
                                }
                            }

                            mService.setMediaItems(mediaItems);

                            if (mediaItems.size() == 0) {
                                System.out.println("No media items found");
                                Toast.makeText(MainActivity.this, "Failed to find items in playlist, check playlists URL or playlist name", Toast.LENGTH_SHORT).show();
                                showSetupDialog();
                                return;
                            }

                            currentPosition = 0;
                            mService.setCurrentPosition(currentPosition);
                            Collections.shuffle(mediaItems);
                            startSlideshow();

                        } catch (Exception ex) {
                            System.out.println(ex.toString());
                            Toast.makeText(MainActivity.this, "Failed to parse playlist, check playlists URL or playlist name", Toast.LENGTH_SHORT).show();
                            showSetupDialog();
                        }
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable e) {
                    Toast.makeText(MainActivity.this, "Failed to retrieve playlists", Toast.LENGTH_SHORT).show();
                    Log.e("hi", "error" + e.getMessage() + e.toString());
                }
            });
        } else {
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    loadImagesFromWeb(); // Retry loading images after delay
                }
            }, 10000);
        }
    }

    private void adjustScreenBrightness(float lightValue){
        if (autobrightness) {
            if (!isInQuietHours) {
                // Determine the desired brightness range
                float maxBrightness = 1.0f; // Maximum brightness value (0 to 1)
                float minBrightness = 0.0f; // Minimum brightness value (0 to 1)

                // Map the light sensor value (0 to 30) to the desired brightness range (0 to 1)
                float brightness = (lightValue / 30f) * (maxBrightness - minBrightness) + minBrightness;

                // Make sure brightness is within the valid range
                brightness = Math.min(Math.max(brightness, minBrightness), maxBrightness);

                // Apply the brightness setting to the screen
                WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
                layoutParams.screenBrightness = brightness;
                getWindow().setAttributes(layoutParams);
            }
        }
        lastLightLevel = lightValue;
    }

    private class DownloadVideoTask extends AsyncTask<String, Void, String> {
        private static final int CONNECTION_TIMEOUT = 15000;
        private static final int MAX_RETRIES = 3;

        @Override
        protected String doInBackground(String... params) {
            String videoUrl = params[0];
            String videoId = params[1];
            if (new File(getCacheDir(), videoId + ".mp4").exists()) {
                return new File(getCacheDir(), videoId + ".mp4").getPath();
            }

            Util.cacheCleanup(getCacheDir());

            int retryCount = 0;
            while (retryCount < MAX_RETRIES) {
                try {
                    // Download the video from the URL
                    URL url = new URL(videoUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(CONNECTION_TIMEOUT);
                    connection.connect();

                    File tempFile = new File(getCacheDir(), videoId + ".mp4");
                    FileOutputStream outputStream = new FileOutputStream(tempFile);

                    InputStream inputStream = connection.getInputStream();
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.close();
                    inputStream.close();

                    return tempFile.getPath();
                } catch (SocketTimeoutException e) {
                    retryCount++;
                } catch (IOException e) {
                    retryCount++;
                } catch (Exception e) {
                    retryCount++;
                }
            }
            return "ERR: Timeout";
        }

        @Override
        protected void onPostExecute(String file) {
            if (!file.startsWith("ERR")) {
                videoView.setVisibility(View.VISIBLE);
                imageView.setVisibility(View.INVISIBLE);
                progress.setVisibility(View.INVISIBLE);
                videoView.setVideoPath(file);

                videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
                        mediaPlayer.setLooping(true);
                        currentPosition++;
                        mService.setCurrentPosition(currentPosition);
                        videoView.start();
                    }
                });
                videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                        progress.setVisibility(View.VISIBLE);
                        currentPosition++;
                        mService.setCurrentPosition(currentPosition);

                        showNextImage();
                        return true;
                    }
                });
            } else {
                progress.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "ERR> " + file, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
         public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null) {
                if (intent.getAction().equals("MSG_RECEIVED")) {
                    String type = intent.getStringExtra("type");

                    if (type.equals("options")) {
                        Toast.makeText(MainActivity.this, "Options received   level=" + intent.getFloatExtra("brightness", 1f), Toast.LENGTH_LONG).show();

                        WindowManager.LayoutParams params = getWindow().getAttributes();
                        float incomingbrightness = intent.getFloatExtra("brightness", 1f);
                        if (incomingbrightness == -1.0f) {
                            autobrightness = true;
                            adjustScreenBrightness(lastLightLevel);
                        } else {
                            autobrightness = false;
                            brightnesslevel = incomingbrightness;
                            params.screenBrightness = incomingbrightness;
                            getWindow().setAttributes(params);
                        }

                        int incominginterval = intent.getIntExtra("interval", 300);
                        int incomingStartQuietHour = intent.getIntExtra("startQuietHour", -1);
                        int incomingEndQuietHour = intent.getIntExtra("endQuietHour", -1);

                        SharedPreferences settings = getSharedPreferences("prefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean("autobrightness", autobrightness);
                        editor.putFloat("brightnesslevel", incomingbrightness);
                        editor.putInt("interval", incominginterval);
                        editor.putInt("startQuietHour", incomingStartQuietHour);
                        editor.putInt("startQuietHour", incomingEndQuietHour);
                        editor.apply();

                        if (incominginterval != interval || incomingStartQuietHour != startQuietHour || incomingEndQuietHour != endQuietHour) {
                            interval = incominginterval;
                            startQuietHour = incomingStartQuietHour;
                            endQuietHour = incomingEndQuietHour;
                            if (quietHoursCalc()) isInQuietHours = true; else isInQuietHours = false;
                            loadImagesFromWeb();
                            if (isInQuietHours) adjustScreenBrightness(0);
                        }
                    }

                    if (type.equals("image") || type.equals("video")) {
                        progress.setVisibility(View.VISIBLE);
                        imageView.setVisibility(View.INVISIBLE);
                        videoView.setVisibility(View.INVISIBLE);

                        slideshowpaused = true;
                        mService.setSlideshowPaused(slideshowpaused);

                        if (isInQuietHours) {
                            isInQuietHours = false;
                            WindowManager.LayoutParams params = getWindow().getAttributes();
                            params.screenBrightness = brightnesslevel;
                            getWindow().setAttributes(params);
                        }

                        String url = intent.getStringExtra("url");
                        if (type.equals("image")) {
                            mService.setCurrentMediaUrl(url);
                            loadImage(url);
                            return;
                        }

                        if (type.equals("video")) {
                            mService.setCurrentMediaUrl(url);
                            loadVideo(url, getMD5Hash(url));
                            return;
                        }

                        Toast.makeText(MainActivity.this, "No source found.", Toast.LENGTH_SHORT).show();
                        slideshowpaused = false;
                        mService.setSlideshowPaused(slideshowpaused);

                        showNextImage();

                    }

                    if (type.equals("resume")) {
                        progress.setVisibility(View.VISIBLE);
                        imageView.setVisibility(View.INVISIBLE);
                        videoView.setVisibility(View.INVISIBLE);

                        if (isInQuietHours) {
                            isInQuietHours = false;
                            WindowManager.LayoutParams params = getWindow().getAttributes();
                            params.screenBrightness = brightnesslevel;
                            getWindow().setAttributes(params);
                        }

                        slideshowpaused = false;
                        mService.setSlideshowPaused(slideshowpaused);
                        showNextImage();
                    }

                    if (type.equals("playlist")) {
                        progress.setVisibility(View.VISIBLE);
                        imageView.setVisibility(View.INVISIBLE);
                        videoView.setVisibility(View.INVISIBLE);

                        playlist = intent.getStringExtra("playlist");

                        SharedPreferences settings = getSharedPreferences("prefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString("playlist", playlist);
                        editor.apply();

                        slideshowpaused = false;
                        mService.setSlideshowPaused(slideshowpaused);

                        Toast.makeText(MainActivity.this, "Playlist received   playlist=" + playlist, Toast.LENGTH_LONG).show();
                        loadImagesFromWeb();
                    }

                }
            }
        }
    };

    public void loadVideo(String url, String id) {
        MediaController mediaController = new MediaController(MainActivity.this);
        mediaController.setAnchorView(videoView);
        mediaController.setVisibility(View.INVISIBLE);

        videoView.setMediaController(mediaController);

        new DownloadVideoTask().execute(url, id);
    }

    public void loadImage(String url) {
        videoView.setVisibility(View.INVISIBLE);
        Picasso.get().load(url).fit().centerInside().into(imageView);
        progress.setVisibility(View.INVISIBLE);
        imageView.setVisibility(View.VISIBLE);
    }

}