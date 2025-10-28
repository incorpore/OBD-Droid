package io.github.vindecoder.recall;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Service for querying the NHTSA recalls API.
 *
 * This mirrors the capabilities of the standalone recall lookup project
 * and adds simple in-memory caching to reduce duplicate network calls.
 */
public class RecallLookupService {

    private static final String TAG = "RecallLookup";
    private static final String BASE_URL = "https://api.nhtsa.gov/";

    private static RecallLookupService instance;

    private final NHTSARecallApiService apiService;
    private final Map<String, List<RecallRecord>> vinCache = new ConcurrentHashMap<>();
    private final Map<String, List<RecallRecord>> vehicleCache = new ConcurrentHashMap<>();
    private final Map<String, RecallRecord> campaignCache = new ConcurrentHashMap<>();

    /**
     * Callback for recall list results.
     */
    public interface RecallCallback {
        void onSuccess(List<RecallRecord> recalls);
        void onError(String error);
    }

    /**
     * Callback for single recall results.
     */
    public interface SingleRecallCallback {
        void onSuccess(RecallRecord recall);
        void onError(String error);
    }

    private RecallLookupService() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.apiService = retrofit.create(NHTSARecallApiService.class);
    }

    public static synchronized RecallLookupService getInstance() {
        if (instance == null) {
            instance = new RecallLookupService();
        }
        return instance;
    }

    /**
     * Fetch recalls by VIN asynchronously.
     */
    public void getRecallsByVin(String vin, RecallCallback callback) {
        if (vin == null || vin.trim().isEmpty()) {
            callback.onError("VIN cannot be empty");
            return;
        }

        final String normalizedVin = vin.trim().toUpperCase();

        if (vinCache.containsKey(normalizedVin)) {
            callback.onSuccess(vinCache.get(normalizedVin));
            return;
        }

        Call<RecallResponse> call = apiService.getRecallsByVin(normalizedVin);
        call.enqueue(new Callback<RecallResponse>() {
            @Override
            public void onResponse(Call<RecallResponse> call, Response<RecallResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<RecallRecord> recalls = immutableCopy(response.body().getResults());
                    vinCache.put(normalizedVin, recalls);
                    callback.onSuccess(recalls);
                } else {
                    callback.onError(errorFromResponse(response));
                }
            }

            @Override
            public void onFailure(Call<RecallResponse> call, Throwable t) {
                callback.onError("Recall lookup failed: " + t.getMessage());
            }
        });
    }

    /**
     * Fetch recalls by make/model/year asynchronously.
     */
    public void getRecalls(String make, String model, String modelYear, RecallCallback callback) {
        if (isBlank(make) || isBlank(model)) {
            callback.onError("Make and model are required for recall lookup");
            return;
        }

        final String cacheKey = (make + "|" + model + "|" + (modelYear != null ? modelYear : "all")).toLowerCase();

        if (vehicleCache.containsKey(cacheKey)) {
            callback.onSuccess(vehicleCache.get(cacheKey));
            return;
        }

        Call<RecallResponse> call = apiService.getRecallsByVehicle(
                make.trim(),
                model.trim(),
                modelYear != null ? modelYear.trim() : null
        );

        call.enqueue(new Callback<RecallResponse>() {
            @Override
            public void onResponse(Call<RecallResponse> call, Response<RecallResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<RecallRecord> recalls = immutableCopy(response.body().getResults());
                    vehicleCache.put(cacheKey, recalls);
                    callback.onSuccess(recalls);
                } else {
                    callback.onError(errorFromResponse(response));
                }
            }

            @Override
            public void onFailure(Call<RecallResponse> call, Throwable t) {
                callback.onError("Recall lookup failed: " + t.getMessage());
            }
        });
    }

    /**
     * Fetch a recall by NHTSA campaign number asynchronously.
     */
    public void getRecallByCampaignNumber(String campaignNumber, SingleRecallCallback callback) {
        if (isBlank(campaignNumber)) {
            callback.onError("Campaign number is required");
            return;
        }

        final String cacheKey = campaignNumber.trim().toUpperCase();

        if (campaignCache.containsKey(cacheKey)) {
            callback.onSuccess(campaignCache.get(cacheKey));
            return;
        }

        Call<RecallResponse> call = apiService.getRecallByCampaign(campaignNumber.trim());
        call.enqueue(new Callback<RecallResponse>() {
            @Override
            public void onResponse(Call<RecallResponse> call, Response<RecallResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<RecallRecord> recalls = response.body().getResults();
                    if (!recalls.isEmpty()) {
                        RecallRecord record = recalls.get(0);
                        campaignCache.put(cacheKey, record);
                        callback.onSuccess(record);
                    } else {
                        callback.onError("No recall found for campaign: " + campaignNumber);
                    }
                } else {
                    callback.onError(errorFromResponse(response));
                }
            }

            @Override
            public void onFailure(Call<RecallResponse> call, Throwable t) {
                callback.onError("Recall lookup failed: " + t.getMessage());
            }
        });
    }

    /**
     * Synchronous helper for VIN lookup.
     */
    public List<RecallRecord> getRecallsByVinBlocking(String vin) throws IOException {
        if (vin == null || vin.trim().isEmpty()) {
            return Collections.emptyList();
        }

        final String normalizedVin = vin.trim().toUpperCase();
        if (vinCache.containsKey(normalizedVin)) {
            return vinCache.get(normalizedVin);
        }

        Response<RecallResponse> response = apiService.getRecallsByVin(normalizedVin).execute();
        if (response.isSuccessful() && response.body() != null) {
            List<RecallRecord> recalls = immutableCopy(response.body().getResults());
            vinCache.put(normalizedVin, recalls);
            return recalls;
        }

        throw new IOException(errorFromResponse(response));
    }

    /**
     * Synchronous helper for make/model lookup.
     */
    public List<RecallRecord> getRecallsBlocking(String make, String model, String modelYear) throws IOException {
        if (isBlank(make) || isBlank(model)) {
            return Collections.emptyList();
        }

        final String cacheKey = (make + "|" + model + "|" + (modelYear != null ? modelYear : "all")).toLowerCase();
        if (vehicleCache.containsKey(cacheKey)) {
            return vehicleCache.get(cacheKey);
        }

        Response<RecallResponse> response = apiService.getRecallsByVehicle(
                make.trim(),
                model.trim(),
                modelYear != null ? modelYear.trim() : null
        ).execute();

        if (response.isSuccessful() && response.body() != null) {
            List<RecallRecord> recalls = immutableCopy(response.body().getResults());
            vehicleCache.put(cacheKey, recalls);
            return recalls;
        }

        throw new IOException(errorFromResponse(response));
    }

    /**
     * Clear in-memory caches.
     */
    public void clearCache() {
        vinCache.clear();
        vehicleCache.clear();
        campaignCache.clear();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private List<RecallRecord> immutableCopy(List<RecallRecord> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private String errorFromResponse(Response<?> response) {
        if (response == null) {
            return TAG + ": Empty response from NHTSA recall API";
        }
        return TAG + ": HTTP " + response.code();
    }
}
