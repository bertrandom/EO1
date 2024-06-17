package com.dan.eo1;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Binder;
import android.os.Looper;
import android.os.StrictMode;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class MyMessageService extends Service {
    private final IBinder binder = new LocalBinder();
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    private String currentMediaUrl = "";
    private int currentPosition = 0;
    private String playlist = "";
    private List<String> mediaItems;
    private boolean slideshowpaused = false;

    @Override
    public void onCreate() {
        super.onCreate();

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Setup your server socket here
        try {
            int port = 12345;
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            isRunning = true;
            startSocketListener();
        }
        return START_STICKY;
    }

    private void startSocketListener() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (isRunning) {
                        Socket clientSocket = serverSocket.accept();
                        handleIncomingMessage(clientSocket);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void handleIncomingMessage(Socket clientSocket) {
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true); // true for auto-flush on println

            // Read the request line
            String requestLine = input.readLine();
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 3) {
                return; // Not a valid request
            }

            String method = requestParts[0];
            String path = requestParts[1];
            String version = requestParts[2];

            // Read the header lines
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while ((headerLine = input.readLine()) != null && !headerLine.isEmpty()) {
                String[] headerParts = headerLine.split(": ");
                if (headerParts.length >= 2) {
                    headers.put(headerParts[0], headerParts[1]);
                }
            }

            JSONObject jsonObject = new JSONObject();

            if ("POST".equalsIgnoreCase(method) && headers.containsKey("Content-Length")) {
                int contentLength = Integer.parseInt(headers.get("Content-Length"));
                char[] buffer = new char[contentLength];
                input.read(buffer, 0, contentLength);
                String requestBody = new String(buffer);

                try {
                    jsonObject = new JSONObject(requestBody);
                } catch (JSONException e) {
                    output.println("HTTP/1.1 400 Bad Request");
                    output.println();
                    clientSocket.close();
                    return;
                }

            }

            Handler handler = new Handler(Looper.getMainLooper());

            boolean shouldBroadcastIntent = false;
            Intent intent = new Intent("MSG_RECEIVED");

            // Respond to GET request
            if ("GET".equals(method)) {
                if ("/".equals(path)) {
                    // Response for root path

                } else if ("/resume".equals(path)) {
                    intent.putExtra("type", "resume");
                    shouldBroadcastIntent = true;
                } else if ("/status".equals(path)) {
                    output.println("HTTP/1.1 200 OK");
                    output.println("Content-Type: application/json");
                    output.println();

                    JSONObject outputJsonObject = new JSONObject();
                    outputJsonObject.put("url", currentMediaUrl);

                    JSONObject outputPlaylist = new JSONObject();
                    outputPlaylist.put("id", playlist);
                    outputPlaylist.put("currentPosition", currentPosition);
                    outputPlaylist.put("size", mediaItems.size());

                    JSONArray outputItems = new JSONArray();
                    for (String item : mediaItems) {
                        outputItems.put(item);
                    }

                    outputPlaylist.put("items", outputItems);

                    if (!slideshowpaused) {
                        outputJsonObject.put("playlist", outputPlaylist);
                    }

                    output.println(outputJsonObject.toString(4));

                    clientSocket.close();
                    return;
                } else if (path.matches("/playlist/[A-Za-z0-9_-]+")) {

                    Pattern pattern = Pattern.compile("/playlist/([A-Za-z0-9_-]+)"); // Regex pattern with capturing group
                    Matcher matcher = pattern.matcher(path);

                    if (matcher.find()) {
                        String playlistId = matcher.group(1); // Extracts the playlistId using the capturing group
                        intent.putExtra("type", "playlist");
                        intent.putExtra("playlist", playlistId);
                        shouldBroadcastIntent = true;
                    }

                } else {
                    // 404 Not Found
                    output.println("HTTP/1.1 404 Not Found");
                    output.println();
                    output.println("<html><body><h1>404 Not Found</h1></body></html>");
                    clientSocket.close();
                    return;
                }
            } else if ("POST".equals(method)) {
                if ("/image".equals(path)) {
                    if (jsonObject.has("url")) {
                        intent.putExtra("url", jsonObject.getString("url"));
                        intent.putExtra("type", "image");
                        shouldBroadcastIntent = true;
                    }
                } else if ("/video".equals(path)) {
                    if (jsonObject.has("url")) {
                        intent.putExtra("url", jsonObject.getString("url"));
                        intent.putExtra("type", "video");
                        shouldBroadcastIntent = true;
                    }
                } else if ("/brightness".equals(path)) {
                    if (jsonObject.has("brightness")) {
                         intent.putExtra("type", "brightness");
                         intent.putExtra("level", Float.parseFloat(jsonObject.getString("level")));
                         shouldBroadcastIntent = true;
                    }
                } else if ("/options".equals(path)) {
                    if (jsonObject.has("brightness") || jsonObject.has("interval") || jsonObject.has("startQuietHour") || jsonObject.has("endQuietHour")) {
                        Log.e("hi", "setting options");
                        intent.putExtra("type", "options");
                        if (jsonObject.has("brightness")) {
                         intent.putExtra("brightness", Float.parseFloat(jsonObject.getString("brightness")));
                        }
                        if (jsonObject.has("interval")) {
                            intent.putExtra("interval", jsonObject.getInt("interval"));
                        }
                        if (jsonObject.has("startQuietHour")) {
                            intent.putExtra("startQuietHour", jsonObject.getInt("startQuietHour"));
                        }
                        if (jsonObject.has("endQuietHour")) {
                            intent.putExtra("endQuietHour", jsonObject.getInt("endQuietHour"));
                        }
                        shouldBroadcastIntent = true;
                    }
                }
            } else {
                // Method Not Allowed
                output.println("HTTP/1.1 405 Method Not Allowed");
                output.println();
                clientSocket.close();
                return;
            }

            final boolean broadcastIntent = shouldBroadcastIntent;

            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (broadcastIntent) {
                        LocalBroadcastManager.getInstance(MyMessageService.this).sendBroadcast(intent);
                    }
                }
            });
            output.println("HTTP/1.1 200 OK");
            output.println("Content-Type: application/json");
            output.println();
            output.println("{\"ok\":true}");
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(MyMessageService.this, e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(MyMessageService.this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up resources and stop the service
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        isRunning = false;
    }

    public class LocalBinder extends Binder {
        MyMessageService getService() {
            // Return this instance of MyService so clients can call public methods
            return MyMessageService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setCurrentMediaUrl(String url) {
        currentMediaUrl = url;
    }

    public void setCurrentPosition(int i) {
        currentPosition = i;
    }

    public void setPlaylist(String p) {
        playlist = p;
    }

    public void setMediaItems(List<String> items) {
        mediaItems = items;
    }

    public void setSlideshowPaused(boolean sp) {
        slideshowpaused = sp;
    }

}
