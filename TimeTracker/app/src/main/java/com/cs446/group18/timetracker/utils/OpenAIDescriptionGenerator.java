package com.cs446.group18.timetracker.utils;

import android.os.AsyncTask;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.cs446.group18.timetracker.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class OpenAIDescriptionGenerator {

    public interface DescriptionCallback {
        void onResult(@NonNull String description);
    }

    public static void generateDescription(@NonNull String eventType,
                                           @NonNull DescriptionCallback callback) {
        new GenerateDescriptionTask(eventType, callback).execute();
    }

    private static class GenerateDescriptionTask extends AsyncTask<Void, Void, String> {
        private final String eventType;
        private final DescriptionCallback callback;

        private GenerateDescriptionTask(String eventType, DescriptionCallback callback) {
            this.eventType = eventType;
            this.callback = callback;
        }

        @Override
        protected String doInBackground(Void... voids) {
            String apiKey = BuildConfig.OPENAI_API_KEY;
            if (TextUtils.isEmpty(apiKey) || "null".equalsIgnoreCase(apiKey)) {
                return buildFallbackDescription();
            }

            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://api.openai.com/v1/chat/completions");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);

                JSONObject payload = buildRequestPayload();

                OutputStream outputStream = connection.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
                writer.write(payload.toString());
                writer.flush();
                writer.close();
                outputStream.close();

                int responseCode = connection.getResponseCode();
                InputStream responseStream = responseCode >= HttpURLConnection.HTTP_OK
                        && responseCode < HttpURLConnection.HTTP_MULT_CHOICE
                        ? connection.getInputStream() : connection.getErrorStream();

                if (responseStream == null) {
                    return buildFallbackDescription();
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                reader.close();

                return parseResponse(responseBuilder.toString());
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return buildFallbackDescription();
        }

        @Override
        protected void onPostExecute(String description) {
            String finalDescription = TextUtils.isEmpty(description)
                    ? buildFallbackDescription()
                    : description;
            callback.onResult(finalDescription);
        }

        private JSONObject buildRequestPayload() throws JSONException {
            JSONObject payload = new JSONObject();
            payload.put("model", "gpt-3.5-turbo");

            JSONArray messages = new JSONArray();

            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You create concise, friendly descriptions for events in a personal time tracker.");
            messages.put(systemMessage);

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", String.format(Locale.US,
                    "Write a short description (maximum one sentence) for the time tracking event type \"%s\".",
                    eventType));
            messages.put(userMessage);

            payload.put("messages", messages);
            payload.put("max_tokens", 60);
            payload.put("temperature", 0.7);

            return payload;
        }

        private String parseResponse(String rawResponse) throws JSONException {
            if (TextUtils.isEmpty(rawResponse)) {
                return buildFallbackDescription();
            }

            JSONObject response = new JSONObject(rawResponse);
            JSONArray choices = response.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject firstChoice = choices.optJSONObject(0);
                if (firstChoice != null) {
                    JSONObject message = firstChoice.optJSONObject("message");
                    if (message != null) {
                        String content = message.optString("content", "").trim();
                        if (!TextUtils.isEmpty(content)) {
                            return content.replaceAll("\\s+", " ").trim();
                        }
                    }
                    String legacyContent = firstChoice.optString("text", "").trim();
                    if (!TextUtils.isEmpty(legacyContent)) {
                        return legacyContent.replaceAll("\\s+", " ").trim();
                    }
                }
            }
            return buildFallbackDescription();
        }

        private String buildFallbackDescription() {
            return String.format(Locale.US, "Time dedicated to %s.", eventType);
        }
    }
}
