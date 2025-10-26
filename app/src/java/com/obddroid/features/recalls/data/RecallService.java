package com.obddroid.features.recalls.data;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.obddroid.features.recalls.model.RecallSearchResult;

import java.util.List;

import io.github.recalllookup.android.RecallLookupAndroid;
import io.github.recalllookup.core.RecallRecord;
import com.obddroid.utils.EnhancedVINDecoder;
import com.obddroid.utils.VehicleData;

/**
 * Service for performing recall searches using NHTSA APIs.
 *
 * Two-step process:
 * 1. Decode VIN to get vehicle make/model/year (nhtsa-vin-decoder)
 * 2. Lookup recalls using vehicle information (nhtsa-recall-lookup)
 *
 * @author Wal33D
 */
public class RecallService {

    private static final String TAG = "RecallService";

    private final EnhancedVINDecoder vinDecoder;
    private final RecallLookupAndroid recallLookup;

    /**
     * Callback interface for recall search results
     */
    public interface RecallSearchCallback {
        void onSearchStarted();
        void onVinDecoded(VehicleData vehicleData);
        void onSearchCompleted(RecallSearchResult result);
        void onSearchFailed(String error);
    }

    public RecallService(Context context) {
        this.vinDecoder = new EnhancedVINDecoder(context);
        this.recallLookup = new RecallLookupAndroid(context);
        Log.d(TAG, "RecallService initialized");
    }

    /**
     * Perform a recall search for the given VIN.
     *
     * This is an asynchronous operation that:
     * 1. Validates the VIN
     * 2. Decodes the VIN to get vehicle data
     * 3. Looks up recalls for that vehicle
     * 4. Returns results via callback
     *
     * @param vin      The 17-character VIN to search
     * @param callback Callback for search results
     */
    public void searchRecallsByVin(String vin, RecallSearchCallback callback) {
        Log.d(TAG, "searchRecallsByVin called with VIN: " + vin);

        // Validate VIN
        if (TextUtils.isEmpty(vin)) {
            Log.e(TAG, "VIN is empty");
            callback.onSearchFailed("No VIN provided");
            return;
        }

        if (vin.length() != 17) {
            Log.e(TAG, "Invalid VIN length: " + vin.length());
            callback.onSearchFailed("Invalid VIN (must be 17 characters)");
            return;
        }

        callback.onSearchStarted();

        // Step 1: Decode VIN to get vehicle data
        Log.d(TAG, "Step 1: Decoding VIN to get vehicle data");
        vinDecoder.decodeAsync(vin, new EnhancedVINDecoder.DecodeCallback() {
            @Override
            public void onSuccess(VehicleData vehicleData) {
                Log.d(TAG, "VIN decode success");
                Log.d(TAG, "VehicleData: " + (vehicleData != null ?
                    "Make=" + vehicleData.getMake() + ", Model=" + vehicleData.getModel() +
                    ", Year=" + vehicleData.getModelYear() : "null"));

                if (vehicleData == null || vehicleData.getMake() == null || vehicleData.getModel() == null) {
                    callback.onSearchFailed("Unable to decode vehicle information from VIN");
                    return;
                }

                callback.onVinDecoded(vehicleData);

                // Step 2: Lookup recalls using make/model/year
                Log.d(TAG, "Step 2: Looking up recalls for " + vehicleData.getMake() +
                    " " + vehicleData.getModel() + " " + vehicleData.getModelYear());

                recallLookup.getRecalls(
                    vehicleData.getMake(),
                    vehicleData.getModel(),
                    vehicleData.getModelYear(),
                    new RecallLookupAndroid.RecallCallback() {
                        @Override
                        public void onSuccess(List<RecallRecord> recalls) {
                            Log.d(TAG, "Recall lookup success - found " +
                                (recalls != null ? recalls.size() : 0) + " recalls");

                            RecallSearchResult result = new RecallSearchResult(vin, vehicleData, recalls);
                            callback.onSearchCompleted(result);
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Recall lookup error: " + error);
                            callback.onSearchFailed(error != null ? error :
                                "Failed to search recalls. Please try again.");
                        }
                    });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "VIN decode error: " + error);
                callback.onSearchFailed(error != null ? error :
                    "Failed to decode VIN. Please check vehicle connection.");
            }
        });
    }

    /**
     * Cancel any ongoing search operations (if supported by underlying libraries)
     */
    public void cancelSearch() {
        // Note: Current libraries don't support cancellation
        Log.d(TAG, "cancelSearch called (not implemented by underlying libraries)");
    }
}
