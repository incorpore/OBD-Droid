package com.obddroid.utils;

import android.content.Context;

import io.github.vindecoder.nhtsa.VehicleData;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for interacting with OpenAI's API to analyze fault codes.
 */
public class OpenAiService
{
    private static final Logger log = Logger.getLogger(OpenAiService.class.getName());
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String OPENAI_TTS_URL = "https://api.openai.com/v1/audio/speech";
    private static final String DEFAULT_MODEL = "gpt-3.5-turbo";
    private static final String DEFAULT_TTS_MODEL = "tts-1";
    private static final String DEFAULT_VOICE = "alloy";  // Options: alloy, echo, fable, onyx, nova, shimmer
    private static final int TIMEOUT_MS = 30000;

    private final SecurePreferences securePreferences;

    public OpenAiService(Context context)
    {
        this.securePreferences = new SecurePreferences(context);
    }

    /**
     * Checks if the OpenAI API key is configured.
     */
    public boolean isApiKeyConfigured()
    {
        return securePreferences.hasOpenAiApiKey();
    }

    /**
     * Analyzes a fault code using OpenAI's API.
     *
     * @param faultCode The OBD-II fault code (e.g., "P0301")
     * @param description The fault code description
     * @param vehicleData Optional vehicle data for better context (can be null)
     * @return The AI analysis response
     * @throws Exception if the API call fails
     */
    public String analyzeFaultCode(String faultCode, String description, VehicleData vehicleData) throws Exception
    {
        String apiKey = securePreferences.getOpenAiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty())
        {
            throw new Exception("OpenAI API key not configured. Please set it in settings.");
        }

        ChatMessage system = new ChatMessage("system",
            "You are an automotive diagnostic expert. Provide practical, concise advice.");
        ChatMessage user = new ChatMessage("user", buildPrompt(faultCode, description, vehicleData));
        return completeChat(apiKey, Collections.singletonList(system),
            Collections.singletonList(user), DEFAULT_MODEL, 500, 0.7d);
    }

    /**
     * Builds the prompt for OpenAI to analyze the fault code.
     */
    private String buildPrompt(String faultCode, String description, VehicleData vehicleData)
    {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an automotive diagnostic expert. Analyze the following OBD-II fault code:\n\n");

        // Add fault code information
        prompt.append("Code: ").append(faultCode).append("\n");
        prompt.append("Description: ").append(description).append("\n\n");

        // Add vehicle context if available
        if (vehicleData != null)
        {
            prompt.append("Vehicle Information:\n");

            if (vehicleData.modelYear != null && !vehicleData.modelYear.isEmpty())
            {
                prompt.append("- Year: ").append(vehicleData.modelYear).append("\n");
            }

            if (vehicleData.make != null && !vehicleData.make.isEmpty())
            {
                prompt.append("- Make: ").append(vehicleData.make).append("\n");
            }

            if (vehicleData.model != null && !vehicleData.model.isEmpty())
            {
                prompt.append("- Model: ").append(vehicleData.model).append("\n");
            }

            if (vehicleData.trim != null && !vehicleData.trim.isEmpty() && !vehicleData.trim.equals("Not Applicable"))
            {
                prompt.append("- Trim: ").append(vehicleData.trim).append("\n");
            }

            String engineDesc = buildEngineDescription(vehicleData);
            if (!engineDesc.isEmpty())
            {
                prompt.append("- Engine: ").append(engineDesc).append("\n");
            }

            if (vehicleData.driveType != null && !vehicleData.driveType.isEmpty())
            {
                prompt.append("- Drive Type: ").append(vehicleData.driveType).append("\n");
            }

            if (vehicleData.transmissionStyle != null && !vehicleData.transmissionStyle.isEmpty())
            {
                prompt.append("- Transmission: ").append(vehicleData.transmissionStyle).append("\n");
            }

            prompt.append("\n");
        }

        prompt.append("Please provide:\n");
        prompt.append("1. A brief explanation of what this code means");
        if (vehicleData != null)
        {
            prompt.append(" for this specific vehicle");
        }
        prompt.append("\n");
        prompt.append("2. Common causes of this fault");
        if (vehicleData != null)
        {
            prompt.append(" on this make/model");
        }
        prompt.append("\n");
        prompt.append("3. Recommended diagnostic steps\n");
        prompt.append("4. Severity level (Low/Medium/High/Critical)\n\n");
        prompt.append("Keep the response concise and practical for a vehicle owner or mechanic.");

        return prompt.toString();
    }

    /**
     * Builds a description of the engine from vehicle data.
     */
    private String buildEngineDescription(VehicleData vehicleData)
    {
        StringBuilder engine = new StringBuilder();

        if (vehicleData.displacementL != null && !vehicleData.displacementL.isEmpty())
        {
            engine.append(vehicleData.displacementL).append("L");
        }

        if (vehicleData.engineCylinders != null && !vehicleData.engineCylinders.isEmpty())
        {
            if (engine.length() > 0) engine.append(" ");
            engine.append(vehicleData.engineCylinders).append("-cylinder");
        }

        if (vehicleData.fuelTypePrimary != null && !vehicleData.fuelTypePrimary.isEmpty()
            && !vehicleData.fuelTypePrimary.equals("Not Applicable"))
        {
            if (engine.length() > 0) engine.append(" ");
            engine.append(vehicleData.fuelTypePrimary);
        }

        return engine.toString();
    }

    /**
     * Makes the actual API call to OpenAI.
     */
    private String callOpenAiApi(String apiKey, String prompt) throws Exception
    {
        HttpURLConnection connection = null;
        try
        {
            URL url = new URL(OPENAI_API_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            // Build request JSON
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", DEFAULT_MODEL);
            requestBody.put("max_tokens", 500);
            requestBody.put("temperature", 0.7);

            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);
            messages.put(message);
            requestBody.put("messages", messages);

            // Send request
            try (OutputStream os = connection.getOutputStream())
            {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read response
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK)
            {
                String errorMessage = readErrorStream(connection);
                log.severe("OpenAI API error: " + responseCode + " - " + errorMessage);
                throw new Exception("OpenAI API error: " + errorMessage);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = br.readLine()) != null)
                {
                    response.append(line);
                }
            }

            // Parse response
            JSONObject jsonResponse = new JSONObject(response.toString());
            return extractContentFromResponse(jsonResponse);
        }
        catch (Exception e)
        {
            log.log(Level.SEVERE, "Failed to call OpenAI API", e);
            throw e;
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }
    }

    private String extractContentFromResponse(JSONObject jsonResponse) throws Exception {
        JSONArray choices = jsonResponse.getJSONArray("choices");
        if (choices.length() > 0)
        {
            JSONObject choice = choices.getJSONObject(0);
            JSONObject messageObj = choice.getJSONObject("message");
            return messageObj.getString("content");
        }
        throw new Exception("No response from OpenAI API");
    }

    public String completeChat(String apiKey,
                               List<ChatMessage> systemMessages,
                               List<ChatMessage> conversation,
                               String model,
                               int maxTokens,
                               double temperature) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(OPENAI_API_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            JSONArray messages = new JSONArray();
            if (systemMessages != null) {
                for (ChatMessage msg : systemMessages) {
                    messages.put(msg.toJson());
                }
            }
            if (conversation != null) {
                for (ChatMessage msg : conversation) {
                    messages.put(msg.toJson());
                }
            }

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model == null ? DEFAULT_MODEL : model);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", temperature);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK)
            {
                String errorMessage = readErrorStream(connection);
                log.severe("OpenAI API error: " + responseCode + " - " + errorMessage);
                throw new Exception("OpenAI API error: " + errorMessage);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = br.readLine()) != null)
                {
                    response.append(line);
                }
            }

            JSONObject jsonResponse = new JSONObject(response.toString());
            return extractContentFromResponse(jsonResponse);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public String completeChat(List<ChatMessage> messages,
                               int maxTokens,
                               double temperature) throws Exception {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages cannot be empty");
        }
        String apiKey = securePreferences.getOpenAiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("OpenAI API key not configured. Please set it in settings.");
        }
        return completeChat(apiKey, Collections.emptyList(), messages, DEFAULT_MODEL, maxTokens, temperature);
    }

    public static final class ChatMessage {
        private final String role;
        private final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }

        JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("role", role);
            obj.put("content", content);
            return obj;
        }
    }

    /**
     * Reads the error stream from a failed HTTP request.
     */
    private String readErrorStream(HttpURLConnection connection)
    {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8)))
        {
            StringBuilder error = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null)
            {
                error.append(line);
            }
            return error.toString();
        }
        catch (Exception e)
        {
            return "Unknown error";
        }
    }

    /**
     * Converts text to speech using OpenAI's TTS API.
     * Returns audio data as byte array (MP3 format).
     *
     * @param text The text to convert to speech
     * @param voice The voice to use (alloy, echo, fable, onyx, nova, shimmer)
     * @return Audio data as byte array in MP3 format
     * @throws Exception if the API call fails
     */
    public byte[] textToSpeech(String text, String voice) throws Exception {
        String apiKey = securePreferences.getOpenAiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("OpenAI API key not configured. Please set it in settings.");
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(OPENAI_TTS_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            // Build request JSON
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", DEFAULT_TTS_MODEL);
            requestBody.put("input", text);
            requestBody.put("voice", voice != null ? voice : DEFAULT_VOICE);
            requestBody.put("response_format", "mp3");
            requestBody.put("speed", 1.0);

            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check response
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorMessage = readErrorStream(connection);
                log.severe("OpenAI TTS API error: " + responseCode + " - " + errorMessage);
                throw new Exception("TTS API error: " + errorMessage);
            }

            // Read audio data
            java.io.InputStream inputStream = connection.getInputStream();
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            buffer.flush();

            return buffer.toByteArray();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to call OpenAI TTS API", e);
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Converts text to speech using the default voice.
     */
    public byte[] textToSpeech(String text) throws Exception {
        return textToSpeech(text, DEFAULT_VOICE);
    }
}
