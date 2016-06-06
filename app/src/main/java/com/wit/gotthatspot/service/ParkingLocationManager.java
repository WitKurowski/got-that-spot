package com.wit.gotthatspot.service;

import android.location.Location;

import com.wit.gotthatspot.exception.ServerException;
import com.wit.gotthatspot.model.ParkingLocation;

import java.io.IOException;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public class ParkingLocationManager {
	private static final ParkingLocationManager PARKING_LOCATION_MANAGER =
			new ParkingLocationManager();
	private final ParkingSpotRetrofitManager parkingSpotRetrofitManager;

	public static ParkingLocationManager getInstance() {
		return ParkingLocationManager.PARKING_LOCATION_MANAGER;
	}

	private ParkingLocationManager() {
		final Retrofit.Builder retrofitBuilder = new Retrofit.Builder();

		retrofitBuilder.baseUrl("http://ridecellparking.herokuapp.com");
		retrofitBuilder.addConverterFactory(GsonConverterFactory.create());

		final Retrofit retrofit = retrofitBuilder.build();

		this.parkingSpotRetrofitManager = retrofit.create(ParkingSpotRetrofitManager.class);
	}

	public List<ParkingLocation> get(final Location location) throws IOException, ServerException {
		final double latitude = location.getLatitude();
		final double longitude = location.getLongitude();
		final Call<List<ParkingLocation>> call =
				this.parkingSpotRetrofitManager.get(latitude, longitude);
		final Response<List<ParkingLocation>> response = call.execute();
		final List<ParkingLocation> parkingLocations;
		final boolean successful = response.isSuccessful();

		if (successful) {
			parkingLocations = response.body();
		} else {
			final String message = response.message();

			throw new ServerException(message);
		}

		return parkingLocations;
	}

	public ParkingLocation reserve(final ParkingLocation parkingLocation, final int numberOfMinutes)
			throws IOException, ServerException {
		final long id = parkingLocation.getId();
		final Call<ParkingLocation> call =
				this.parkingSpotRetrofitManager.reserve(id, numberOfMinutes);
		final Response<ParkingLocation> response = call.execute();
		final boolean successful = response.isSuccessful();
		final ParkingLocation updatedParkingLocation;

		if (successful) {
			updatedParkingLocation = response.body();
		} else {
			final String message = response.message();

			throw new ServerException(message);
		}

		return updatedParkingLocation;
	}

	private interface ParkingSpotRetrofitManager {
		@GET("/api/v1/parkinglocations/search")
		Call<List<ParkingLocation>> get(@Query("lat") double latitude,
				@Query("lng") double longitude);

		@POST("/api/v1/parkinglocations/{id}/reserve/")
		Call<ParkingLocation> reserve(@Path("id") long id, @Query("minutes") int numberOfMinutes);
	}
}