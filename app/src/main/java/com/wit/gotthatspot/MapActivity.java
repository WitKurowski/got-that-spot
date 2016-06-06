package com.wit.gotthatspot;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.wit.gotthatspot.exception.ServerException;
import com.wit.gotthatspot.model.ParkingLocation;
import com.wit.gotthatspot.service.ParkingLocationManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapActivity extends FragmentActivity {
	private static final float INITIAL_ZOOM = 15.0f;
	private final State state = new State();
	private final ViewHolder viewHolder = new ViewHolder();

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.setContentView(R.layout.map);
		this.setupViewHolder();

		this.viewHolder.parkingLocationNameTextView.setText(null);
		this.viewHolder.parkingLocationCostPerMinuteTextView.setText(null);
		this.viewHolder.alreadyReservedView.setVisibility(View.INVISIBLE);
		this.viewHolder.reserveButton.setVisibility(View.INVISIBLE);

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
				supportFragmentManager, this.state, this.viewHolder);

		googleApiClientBuilder.addConnectionCallbacks(connectionCallbacks);

		final OnConnectionFailedListener onConnectionFailedListener =
				new OnConnectionFailedListener();

		googleApiClientBuilder.addOnConnectionFailedListener(onConnectionFailedListener);
		googleApiClientBuilder.addApi(LocationServices.API);

		this.state.googleApiClient = googleApiClientBuilder.build();
	}

	private void setupViewHolder() {
		this.viewHolder.parkingLocationNameTextView = (TextView) this.findViewById(R.id.name);
		this.viewHolder.parkingLocationCostPerMinuteTextView =
				(TextView) this.findViewById(R.id.cost_per_minute);
		this.viewHolder.reserveButton = (Button) this.findViewById(R.id.reserve);
		this.viewHolder.alreadyReservedView = this.findViewById(R.id.already_reserved);
	}

	private static final class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {
		private final Context context;
		private final FragmentManager fragmentManager;
		private final State state;
		private final ViewHolder viewHolder;

		private ConnectionCallbacks(final Context context, final FragmentManager fragmentManager,
				final State state, final ViewHolder viewHolder) {
			this.context = context;
			this.fragmentManager = fragmentManager;
			this.state = state;
			this.viewHolder = viewHolder;
		}

		@Override
		public void onConnected(@Nullable final Bundle bundle) {
			final SupportMapFragment supportMapFragment =
					(SupportMapFragment) this.fragmentManager
							.findFragmentById(R.id.map);
			final OnMapReadyCallback onMapReadyCallback =
					new OnMapReadyCallback(this.context, this.state, this.viewHolder);

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
		private final State state;
		private final ViewHolder viewHolder;

		private OnMapReadyCallback(final Context context, final State state,
				final ViewHolder viewHolder) {
			this.context = context;
			this.state = state;
			this.viewHolder = viewHolder;
		}

		@Override
		public void onMapReady(final GoogleMap googleMap) {
			this.state.googleMap = googleMap;

			final boolean locationAccessGranted = ActivityCompat
					.checkSelfPermission(this.context, Manifest.permission.ACCESS_FINE_LOCATION) ==
					PackageManager.PERMISSION_GRANTED;

			if (locationAccessGranted) {
				googleMap.setMyLocationEnabled(true);

				final Location latestLocation =
						LocationServices.FusedLocationApi
								.getLastLocation(this.state.googleApiClient);

				if (latestLocation == null) {
					final Toast toast =
							Toast.makeText(this.context,
									R.string.failed_to_retrieve_latest_location,
									Toast.LENGTH_SHORT);

					toast.show();
				} else {
					final double latestLocationLatitude = latestLocation.getLatitude();
					final double latestLocationLongitude = latestLocation.getLongitude();
					final LatLng latestLatLng =
							new LatLng(latestLocationLatitude, latestLocationLongitude);
					final CameraUpdate cameraUpdate =
							CameraUpdateFactory
									.newLatLngZoom(latestLatLng, MapActivity.INITIAL_ZOOM);

					googleMap.animateCamera(cameraUpdate);

					final ShowNearestParkingLocationsAsyncTask
							showNearestParkingLocationsAsyncTask =
							new ShowNearestParkingLocationsAsyncTask(context, latestLocation,
									this.state, viewHolder);

					showNearestParkingLocationsAsyncTask.execute();
				}
			}
		}
	}

	private static final class ShowNearestParkingLocationsAsyncTask
			extends AsyncTask<Void, Void, List<ParkingLocation>> {
		private final Context context;
		private final Location location;
		private final State state;
		private final ViewHolder viewHolder;
		private String errorMessage;

		private ShowNearestParkingLocationsAsyncTask(final Context context, final Location location,
				final State state, final ViewHolder viewHolder) {
			this.context = context;
			this.location = location;
			this.state = state;
			this.viewHolder = viewHolder;
		}

		@Override
		protected List<ParkingLocation> doInBackground(final Void... params) {
			final ParkingLocationManager parkingLocationManager =
					ParkingLocationManager.getInstance();
			List<ParkingLocation> parkingLocations = null;

			try {
				parkingLocations = parkingLocationManager.get(this.location);
			} catch (final IOException ioException) {
				ioException.printStackTrace();

				this.errorMessage = this.context
						.getString(R.string.unable_to_retrieve_nearest_parking_locations);
			} catch (final ServerException serverException) {
				serverException.printStackTrace();

				this.errorMessage =
						this.context.getString(R.string.no_parking_locations_found_nearby);
			}

			return parkingLocations;
		}

		@Override
		protected void onPostExecute(final List<ParkingLocation> parkingLocations) {
			super.onPostExecute(parkingLocations);

			if (this.errorMessage == null) {
				this.state.googleMap.clear();
				this.state.markerParkingLocations.clear();

				for (final ParkingLocation parkingLocation : parkingLocations) {
					final MarkerOptions markerOptions = new MarkerOptions();
					final double latitude = parkingLocation.getLatitude();
					final double longitude = parkingLocation.getLongitude();
					final LatLng latLng = new LatLng(latitude, longitude);

					markerOptions.position(latLng);

					final Marker marker = this.state.googleMap.addMarker(markerOptions);

					this.state.markerParkingLocations.put(marker, parkingLocation);
				}
			} else {
				final Toast toast =
						Toast.makeText(this.context, this.errorMessage, Toast.LENGTH_SHORT);

				toast.show();
			}

			final OnMarkerClickListener onMarkerClickListener =
					new OnMarkerClickListener(this.context, this.state, this.viewHolder);

			this.state.googleMap.setOnMarkerClickListener(
					onMarkerClickListener);
		}
	}

	private static final class OnMarkerClickListener implements GoogleMap.OnMarkerClickListener {
		private final Context context;
		private final State state;
		private final ViewHolder viewHolder;

		private OnMarkerClickListener(final Context context, final State state,
				final ViewHolder viewHolder) {
			this.context = context;
			this.state = state;
			this.viewHolder = viewHolder;
		}

		@Override
		public boolean onMarkerClick(final Marker marker) {
			final ParkingLocation parkingLocation = this.state.markerParkingLocations.get(marker);
			final String name = parkingLocation.getName();

			this.viewHolder.parkingLocationNameTextView.setText(name);

			final BigDecimal costPerMinute = parkingLocation.getCostPerMinute();
			final String costPerMinuteString = this.context
					.getString(R.string.cost_per_minute, costPerMinute.toString());

			this.viewHolder.parkingLocationCostPerMinuteTextView.setText(costPerMinuteString);

			final boolean reserved = parkingLocation.getReserved();

			if (reserved) {
				this.viewHolder.reserveButton.setVisibility(View.INVISIBLE);
				this.viewHolder.alreadyReservedView.setVisibility(View.VISIBLE);
			} else {
				this.viewHolder.reserveButton.setVisibility(View.VISIBLE);
				this.viewHolder.alreadyReservedView.setVisibility(View.INVISIBLE);

				final ReserveOnClickListener reserveOnClickListener =
						new ReserveOnClickListener(parkingLocation, this.viewHolder);

				this.viewHolder.reserveButton.setOnClickListener(reserveOnClickListener);
			}

			return false;
		}
	}

	private static final class ReserveOnClickListener implements View.OnClickListener {
		private final ParkingLocation parkingLocation;
		private final ViewHolder viewHolder;

		private ReserveOnClickListener(final ParkingLocation parkingLocation,
				final ViewHolder viewHolder) {
			this.parkingLocation = parkingLocation;
			this.viewHolder = viewHolder;
		}

		@Override
		public void onClick(final View view) {
			final Context context = view.getContext();
			final ReserveAsyncTask reserveAsyncTask =
					new ReserveAsyncTask(context, this.parkingLocation, this.viewHolder);

			reserveAsyncTask.execute();
		}
	}

	private static final class ReserveAsyncTask extends AsyncTask<Void, Void, Void> {
		private final Context context;
		private final ParkingLocation parkingLocation;
		private final ViewHolder viewHolder;
		private String errorMessage;

		private ReserveAsyncTask(final Context context, final ParkingLocation parkingLocation,
				final ViewHolder viewHolder) {
			this.context = context;
			this.parkingLocation = parkingLocation;
			this.viewHolder = viewHolder;
		}

		@Override
		protected Void doInBackground(final Void... params) {
			final ParkingLocationManager parkingLocationManager =
					ParkingLocationManager.getInstance();

			try {
				final int numberOfMinutes = this.parkingLocation.getMinReservationTimeInMinutes();
				final ParkingLocation updatedParkingLocation =
						parkingLocationManager.reserve(this.parkingLocation, numberOfMinutes);
				final boolean reserved = updatedParkingLocation.getReserved();

				this.parkingLocation.setReserved(reserved);

				final Date reservedUntil = updatedParkingLocation.getReservedUntil();

				this.parkingLocation.setReservedUntil(reservedUntil);
			} catch (final IOException ioException) {
				ioException.printStackTrace();

				final String name = this.parkingLocation.getName();

				this.errorMessage =
						this.context.getString(R.string.unable_to_reserve_parking_location_x, name);
			} catch (final ServerException serverException) {
				serverException.printStackTrace();

				final long id = this.parkingLocation.getId();

				this.errorMessage =
						this.context.getString(R.string.failed_to_reserve_parking_location, id);
			}

			return null;
		}

		@Override
		protected void onPostExecute(final Void result) {
			super.onPostExecute(result);

			if (this.errorMessage == null) {
				this.viewHolder.reserveButton.setVisibility(View.INVISIBLE);
				this.viewHolder.alreadyReservedView.setVisibility(View.VISIBLE);

				final Toast toast =
						Toast.makeText(this.context,
								R.string.parking_location_successfully_reserved,
								Toast.LENGTH_SHORT);

				toast.show();
			} else {
				final Toast toast =
						Toast.makeText(this.context, this.errorMessage, Toast.LENGTH_SHORT);

				toast.show();
			}
		}
	}

	private static final class State {
		public GoogleApiClient googleApiClient;
		public GoogleMap googleMap;
		public Map<Marker, ParkingLocation> markerParkingLocations = new HashMap<>();
	}

	private static final class ViewHolder {
		public TextView parkingLocationNameTextView;
		public TextView parkingLocationCostPerMinuteTextView;
		public Button reserveButton;
		public View alreadyReservedView;
	}
}