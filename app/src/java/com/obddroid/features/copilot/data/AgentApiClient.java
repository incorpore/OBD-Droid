package com.obddroid.features.copilot.data;

import android.content.Context;
import android.util.Log;

import com.obddroid.utils.SecurePreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client for OpenAI Assistants/Agents API.
 * Handles Assistants, Threads, Runs, and Messages.
 *
 * API Documentation: https://platform.openai.com/docs/api-reference/assistants
 */
public class AgentApiClient {

    private static final String TAG = "AgentApiClient";

    // API Endpoints
    private static final String BASE_URL = "https://api.openai.com/v1";
    private static final String ASSISTANTS_ENDPOINT = BASE_URL + "/assistants";
    private static final String THREADS_ENDPOINT = BASE_URL + "/threads";
    private static final String FILES_ENDPOINT = BASE_URL + "/files";

    private static final int TIMEOUT_MS = 30000;
    private static final String OPENAI_BETA_HEADER = "assistants=v2";

    private final SecurePreferences securePreferences;

    public AgentApiClient(Context context) {
        this.securePreferences = new SecurePreferences(context);
    }

    // ========== ASSISTANT MANAGEMENT ==========

    /**
     * Create or retrieve the OBD-Droid assistant.
     * Creates a new assistant if assistantId is null, otherwise retrieves existing.
     */
    public Assistant createOrRetrieveAssistant(String assistantId, String name, String instructions,
                                               List<Tool> tools) throws Exception {
        if (assistantId != null) {
            try {
                return retrieveAssistant(assistantId);
            } catch (Exception e) {
                Log.w(TAG, "Failed to retrieve assistant " + assistantId + ", creating new", e);
            }
        }
        return createAssistant(name, instructions, tools);
    }

    /**
     * Create a new assistant with specified configuration.
     */
    public Assistant createAssistant(String name, String instructions, List<Tool> tools) throws Exception {
        String apiKey = getApiKey();

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "gpt-4o");  // Latest model with vision + function calling
        requestBody.put("name", name);
        requestBody.put("instructions", instructions);

        if (tools != null && !tools.isEmpty()) {
            JSONArray toolsArray = new JSONArray();
            for (Tool tool : tools) {
                toolsArray.put(tool.toJson());
            }
            requestBody.put("tools", toolsArray);
        }

        JSONObject response = post(ASSISTANTS_ENDPOINT, apiKey, requestBody);
        return Assistant.fromJson(response);
    }

    /**
     * Retrieve an existing assistant by ID.
     */
    public Assistant retrieveAssistant(String assistantId) throws Exception {
        String apiKey = getApiKey();
        String url = ASSISTANTS_ENDPOINT + "/" + assistantId;
        JSONObject response = get(url, apiKey);
        return Assistant.fromJson(response);
    }

    // ========== THREAD MANAGEMENT ==========

    /**
     * Create a new conversation thread.
     */
    public Thread createThread(JSONObject metadata) throws Exception {
        String apiKey = getApiKey();

        JSONObject requestBody = new JSONObject();
        if (metadata != null) {
            requestBody.put("metadata", metadata);
        }

        JSONObject response = post(THREADS_ENDPOINT, apiKey, requestBody);
        return Thread.fromJson(response);
    }

    /**
     * Retrieve an existing thread by ID.
     */
    public Thread retrieveThread(String threadId) throws Exception {
        String apiKey = getApiKey();
        String url = THREADS_ENDPOINT + "/" + threadId;
        JSONObject response = get(url, apiKey);
        return Thread.fromJson(response);
    }

    /**
     * Delete a thread and all its messages.
     */
    public void deleteThread(String threadId) throws Exception {
        String apiKey = getApiKey();
        String url = THREADS_ENDPOINT + "/" + threadId;
        delete(url, apiKey);
    }

    // ========== MESSAGE MANAGEMENT ==========

    /**
     * Add a message to a thread.
     */
    public Message createMessage(String threadId, String role, String content) throws Exception {
        String apiKey = getApiKey();
        String url = THREADS_ENDPOINT + "/" + threadId + "/messages";

        JSONObject requestBody = new JSONObject();
        requestBody.put("role", role);
        requestBody.put("content", content);

        JSONObject response = post(url, apiKey, requestBody);
        return Message.fromJson(response);
    }

    /**
     * List messages in a thread (newest first).
     */
    public List<Message> listMessages(String threadId, int limit) throws Exception {
        String apiKey = getApiKey();
        String url = THREADS_ENDPOINT + "/" + threadId + "/messages?limit=" + limit + "&order=desc";

        JSONObject response = get(url, apiKey);
        JSONArray data = response.getJSONArray("data");

        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            messages.add(Message.fromJson(data.getJSONObject(i)));
        }
        return messages;
    }

    // ========== RUN MANAGEMENT ==========

    /**
     * Create a run (execute the assistant on the thread).
     */
    public Run createRun(String threadId, String assistantId) throws Exception {
        String apiKey = getApiKey();
        String url = THREADS_ENDPOINT + "/" + threadId + "/runs";

        JSONObject requestBody = new JSONObject();
        requestBody.put("assistant_id", assistantId);

        JSONObject response = post(url, apiKey, requestBody);
        return Run.fromJson(response);
    }

    /**
     * Retrieve the current status of a run.
     */
    public Run retrieveRun(String threadId, String runId) throws Exception {
        String apiKey = getApiKey();
        String url = THREADS_ENDPOINT + "/" + threadId + "/runs/" + runId;

        JSONObject response = get(url, apiKey);
        return Run.fromJson(response);
    }

    /**
     * Submit tool outputs to continue a run that requires action.
     */
    public Run submitToolOutputs(String threadId, String runId, List<ToolOutput> toolOutputs) throws Exception {
        String apiKey = getApiKey();
        String url = THREADS_ENDPOINT + "/" + threadId + "/runs/" + runId + "/submit_tool_outputs";

        JSONObject requestBody = new JSONObject();
        JSONArray outputsArray = new JSONArray();
        for (ToolOutput output : toolOutputs) {
            outputsArray.put(output.toJson());
        }
        requestBody.put("tool_outputs", outputsArray);

        JSONObject response = post(url, apiKey, requestBody);
        return Run.fromJson(response);
    }

    /**
     * Cancel a running run.
     */
    public void cancelRun(String threadId, String runId) throws Exception {
        String apiKey = getApiKey();
        String url = THREADS_ENDPOINT + "/" + threadId + "/runs/" + runId + "/cancel";
        post(url, apiKey, new JSONObject());
    }

    // ========== HTTP HELPERS ==========

    private JSONObject get(String endpoint, String apiKey) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("OpenAI-Beta", OPENAI_BETA_HEADER);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            return readResponse(connection);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private JSONObject post(String endpoint, String apiKey, JSONObject body) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("OpenAI-Beta", OPENAI_BETA_HEADER);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            return readResponse(connection);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void delete(String endpoint, String apiKey) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("OpenAI-Beta", OPENAI_BETA_HEADER);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            readResponse(connection);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private JSONObject readResponse(HttpURLConnection connection) throws Exception {
        int responseCode = connection.getResponseCode();

        if (responseCode >= 400) {
            String error = readStream(connection.getErrorStream());
            Log.e(TAG, "API error (" + responseCode + "): " + error);
            throw new Exception("API error: " + error);
        }

        String response = readStream(connection.getInputStream());
        return new JSONObject(response);
    }

    private String readStream(java.io.InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    // ========== FILE MANAGEMENT ==========

    /**
     * Upload a file to OpenAI for use with Assistants.
     * @param file File to upload
     * @param purpose Purpose of the file (e.g., "assistants")
     * @return File ID
     */
    public String uploadFile(java.io.File file, String purpose) throws Exception {
        String apiKey = getApiKey();
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();

        HttpURLConnection connection = null;
        try {
            URL url = new URL(FILES_ENDPOINT);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            // Build multipart request
            try (OutputStream os = connection.getOutputStream()) {
                // Write purpose field
                os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Disposition: form-data; name=\"purpose\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                os.write((purpose + "\r\n").getBytes(StandardCharsets.UTF_8));

                // Write file field
                os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Type: application/json\r\n\r\n").getBytes(StandardCharsets.UTF_8));

                // Write file contents
                java.io.FileInputStream fis = new java.io.FileInputStream(file);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                fis.close();

                // End boundary
                os.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                String errorBody = readStream(connection.getErrorStream());
                throw new Exception("File upload failed (HTTP " + responseCode + "): " + errorBody);
            }

            String response = readStream(connection.getInputStream());
            JSONObject jsonResponse = new JSONObject(response);
            return jsonResponse.getString("id");

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Attach a file to an Assistant for retrieval.
     */
    public void attachFileToAssistant(String assistantId, String fileId) throws Exception {
        String apiKey = getApiKey();
        String url = ASSISTANTS_ENDPOINT + "/" + assistantId;

        // Update assistant to include file_search tool and file
        JSONObject requestBody = new JSONObject();

        // Add file_search tool
        JSONArray tools = new JSONArray();
        tools.put(new JSONObject().put("type", "file_search"));
        requestBody.put("tools", tools);

        // Add file to tool_resources
        JSONObject toolResources = new JSONObject();
        JSONObject fileSearch = new JSONObject();
        JSONArray vectorStores = new JSONArray();
        JSONObject vectorStore = new JSONObject();
        JSONArray fileIds = new JSONArray();
        fileIds.put(fileId);
        vectorStore.put("file_ids", fileIds);
        vectorStores.put(vectorStore);
        fileSearch.put("vector_stores", vectorStores);
        toolResources.put("file_search", fileSearch);
        requestBody.put("tool_resources", toolResources);

        JSONObject response = patch(url, apiKey, requestBody);
        Log.i(TAG, "Attached file " + fileId + " to assistant " + assistantId);
    }

    private JSONObject patch(String urlString, String apiKey, JSONObject requestBody) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");  // Android doesn't support PATCH, use POST with X-HTTP-Method-Override
            connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("OpenAI-Beta", OPENAI_BETA_HEADER);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                String errorBody = readStream(connection.getErrorStream());
                throw new Exception("PATCH failed (HTTP " + responseCode + "): " + errorBody);
            }

            return readResponse(connection);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String getApiKey() throws Exception {
        String apiKey = securePreferences.getOpenAiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("OpenAI API key not configured");
        }
        return apiKey;
    }

    // ========== DATA MODELS ==========

    public static class Assistant {
        public final String id;
        public final String name;
        public final String model;
        public final String instructions;

        public Assistant(String id, String name, String model, String instructions) {
            this.id = id;
            this.name = name;
            this.model = model;
            this.instructions = instructions;
        }

        static Assistant fromJson(JSONObject json) throws JSONException {
            return new Assistant(
                json.getString("id"),
                json.optString("name"),
                json.getString("model"),
                json.optString("instructions")
            );
        }
    }

    public static class Thread {
        public final String id;
        public final long createdAt;
        public final JSONObject metadata;

        public Thread(String id, long createdAt, JSONObject metadata) {
            this.id = id;
            this.createdAt = createdAt;
            this.metadata = metadata;
        }

        static Thread fromJson(JSONObject json) throws JSONException {
            return new Thread(
                json.getString("id"),
                json.getLong("created_at"),
                json.optJSONObject("metadata")
            );
        }
    }

    public static class Message {
        public final String id;
        public final String role;
        public final String content;
        public final long createdAt;

        public Message(String id, String role, String content, long createdAt) {
            this.id = id;
            this.role = role;
            this.content = content;
            this.createdAt = createdAt;
        }

        static Message fromJson(JSONObject json) throws JSONException {
            // Extract text content from content array
            String textContent = "";
            JSONArray contentArray = json.getJSONArray("content");
            if (contentArray.length() > 0) {
                JSONObject firstContent = contentArray.getJSONObject(0);
                if ("text".equals(firstContent.getString("type"))) {
                    textContent = firstContent.getJSONObject("text").getString("value");
                }
            }

            return new Message(
                json.getString("id"),
                json.getString("role"),
                textContent,
                json.getLong("created_at")
            );
        }
    }

    public static class Run {
        public final String id;
        public final String status;
        public final String threadId;
        public final String assistantId;
        public final JSONObject requiredAction;

        public Run(String id, String status, String threadId, String assistantId, JSONObject requiredAction) {
            this.id = id;
            this.status = status;
            this.threadId = threadId;
            this.assistantId = assistantId;
            this.requiredAction = requiredAction;
        }

        public boolean isCompleted() {
            return "completed".equals(status);
        }

        public boolean isFailed() {
            return "failed".equals(status) || "cancelled".equals(status) || "expired".equals(status);
        }

        public boolean requiresAction() {
            return "requires_action".equals(status);
        }

        public boolean isInProgress() {
            return "queued".equals(status) || "in_progress".equals(status);
        }

        static Run fromJson(JSONObject json) throws JSONException {
            return new Run(
                json.getString("id"),
                json.getString("status"),
                json.getString("thread_id"),
                json.getString("assistant_id"),
                json.optJSONObject("required_action")
            );
        }
    }

    public static class Tool {
        public final String type;
        public final JSONObject function;

        public Tool(String type, JSONObject function) {
            this.type = type;
            this.function = function;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("type", type);
            if (function != null) {
                obj.put("function", function);
            }
            return obj;
        }
    }

    public static class ToolOutput {
        public final String toolCallId;
        public final String output;

        public ToolOutput(String toolCallId, String output) {
            this.toolCallId = toolCallId;
            this.output = output;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("tool_call_id", toolCallId);
            obj.put("output", output);
            return obj;
        }
    }
}
