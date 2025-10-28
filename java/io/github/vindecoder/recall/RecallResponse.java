package io.github.vindecoder.recall;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;

/**
 * Response container for NHTSA recall API responses.
 */
public class RecallResponse {

    @SerializedName("Count")
    private int count;

    @SerializedName("Message")
    private String message;

    @SerializedName("Results")
    private List<RecallRecord> results;

    public int getCount() {
        return count;
    }

    public String getMessage() {
        return message;
    }

    public List<RecallRecord> getResults() {
        return results == null ? Collections.emptyList() : results;
    }
}
