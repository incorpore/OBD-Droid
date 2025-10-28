package io.github.vindecoder.recall;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Retrofit interface for NHTSA recall endpoints.
 */
public interface NHTSARecallApiService {

    /**
     * Retrieve recalls by VIN.
     */
    @GET("recalls/recallsByVehicle")
    Call<RecallResponse> getRecallsByVin(
        @Query("vin") String vin
    );

    /**
     * Retrieve recalls by make/model/year.
     */
    @GET("recalls/recallsByVehicle")
    Call<RecallResponse> getRecallsByVehicle(
        @Query("make") String make,
        @Query("model") String model,
        @Query("modelYear") String modelYear
    );

    /**
     * Retrieve recall by campaign number.
     */
    @GET("recalls/campaignNumber")
    Call<RecallResponse> getRecallByCampaign(
        @Query("campaignNumber") String campaignNumber
    );
}
