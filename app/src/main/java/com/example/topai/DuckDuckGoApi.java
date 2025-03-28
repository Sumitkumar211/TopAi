package com.example.topai;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface DuckDuckGoApi {
    @GET("/")
    Call<DuckDuckGoResponse> getInstantAnswer(
            @Query("q") String query,
            @Query("format") String format
    );
}
