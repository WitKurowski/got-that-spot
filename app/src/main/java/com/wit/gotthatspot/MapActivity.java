package com.wit.gotthatspot;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapActivity extends FragmentActivity {
	private static final float INITIAL_ZOOM = 15.0f;
	private final State state = new State();

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.setContentView(R.layout.map);
		this.setupGoogleApiClient();
	}

	@Override
	protected void onStart() {
		super.onStart();

		this.state.googleApiClient.connect();
	}

	@Override
	protected void onStop() {
		super.onStop();

		this.state.googleApiClient.disconnect();
	}

	private void setupGoogleApiClient() {
		final GoogleApiClient.Builder googleApiClientBuilder = new GoogleApiClient.Builder(this);
		final FragmentManager supportFragmentManager = this.getSupportFragmentManager();
		final ConnectionCallbacks connectionCallbacks = new ConnectionCallbacks(this,
				supportFragmentManager, this.state);

		googleApiClientBuilder.addConnectionCallbacks(connectionCallbacks);

		final OnConnectionFailedListener onConnectionFailedListener =
				new OnConnectionFailedListener();

		googleApiClientBuilder.addOnConnectionFailedListener(onConnectionFailedListener);
		googleApiClientBuilder.addApi(LocationServices.API);

		this.state.googleApiClient = googleApiClientBuilder.build();
	}

	private static final class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {
		private final Context context;
		private final FragmentManager fragmentManager;
		private final State state;

		private ConnectionCallbacks(final Context context, final FragmentManager fragmentManager,
				final State state) {
			this.context = context;
			this.fragmentManager = fragmentManager;
			this.state = state;
		}

		@Override
		public void onConnected(@Nullable final Bundle bundle) {
			final SupportMapFragment supportMapFragment =
					(SupportMapFragment) this.fragmentManager
							.findFragmentById(R.id.map);
			final OnMapReadyCallback onMapReadyCallback =
					new OnMapReadyCallback(this.context, this.state.googleApiClient);

			supportMapFragment.getMapAsync(onMapReadyCallback);
		}

		@Override
		public void onConnectionSuspended(final int i) {
		}
	}

	private static final class OnConnectionFailedListener
			implements GoogleApiClient.OnConnectionFailedListener {
		@Override
		public void onConnectionFailed(@NonNull final ConnectionResult connectionResult) {
		}
	}

	private static final class OnMapReadyCallback implements
			com.google.android.gms.maps.OnMapReadyCallback {
		private final Context context;
		private final GoogleApiClient googleApiClient;

		private OnMapReadyCallback(final Context context, final GoogleApiClient googleApiClient) {
			this.context = context;
			this.googleApiClient = googleApiClient;
		}

		@Override
		public void onMapReady(final GoogleMap googleMap) {
			final boolean locationAccessGranted = ActivityCompat
					.checkSelfPermission(this.context, Manifest.permission.ACCESS_FINE_LOCATION) ==
					PackageManager.PERMISSION_GRANTED;

			if (locationAccessGranted) {
				final Location latestLocation =
						LocationServices.FusedLocationApi.getLastLocation(this.googleApiClient);

				if (latestLocation == null) {
					final Toast toast =
							Toast.makeText(this.context,
									R.string.failed_to_retrieve_latest_location,
									Toast.LENGTH_SHORT);

					toast.show();
				} else {
					final MarkerOptions markerOptions = new MarkerOptions();
					final double latestLocationLatitude = latestLocation.getLatitude();
					final double latestLocationLongitude = latestLocation.getLongitude();
					final LatLng latestLatLng =
							new LatLng(latestLocationLatitude, latestLocationLongitude);

					markerOptions.position(latestLatLng);
					googleMap.addMarker(markerOptions);

					final CameraUpdate cameraUpdate =
							CameraUpdateFactory
									.newLatLngZoom(latestLatLng, MapActivity.INITIAL_ZOOM);

					googleMap.moveCamera(cameraUpdate);
				}
			}
		}
	}

	private static final class State {
		public GoogleApiClient googleApiClient;
	}
}