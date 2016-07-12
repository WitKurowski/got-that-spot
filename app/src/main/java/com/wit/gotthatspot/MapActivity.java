package com.wit.gotthatspot;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.wit.gotthatspot.exception.ServerException;
import com.wit.gotthatspot.model.ParkingLocation;
import com.wit.gotthatspot.service.ParkingLocationManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MapActivity extends FragmentActivity {
	private static final float INITIAL_ZOOM = 15.0f;
	private final State state = new State();
	private final ViewHolder viewHolder = new ViewHolder();
	private final ViewUpdater viewUpdater = new ViewUpdater(this, this.state, this.viewHolder);

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.setContentView(R.layout.map);

		ButterKnife.bind(this.viewHolder, this);

		this.viewUpdater.prepare();

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
				supportFragmentManager, this.state, this.viewHolder, this.viewUpdater);

		googleApiClientBuilder.addConnectionCallbacks(connectionCallbacks);

		final OnConnectionFailedListener onConnectionFailedListener =
				new OnConnectionFailedListener();

		googleApiClientBuilder.addOnConnectionFailedListener(onConnectionFailedListener);
		googleApiClientBuilder.addApi(LocationServices.API);

		this.state.googleApiClient = googleApiClientBuilder.build();
	}

	private static final class CancelAsyncTask extends AsyncTask<Void, Void, Void> {
		private final Context context;
		private final ParkingLocation parkingLocation;
		private final State state;
		private final ViewUpdater viewUpdater;
		private String errorMessage;

		private CancelAsyncTask(final Context context, final ParkingLocation parkingLocation,
				final State state, final ViewUpdater viewUpdater) {
			this.context = context;
			this.parkingLocation = parkingLocation;
			this.state = state;
			this.viewUpdater = viewUpdater;
		}

		@Override
		protected Void doInBackground(final Void... params) {
			final ParkingLocationManager parkingLocationManager =
					ParkingLocationManager.getInstance();

			try {
				parkingLocationManager.cancel(this.parkingLocation);
			} catch (final IOException ioException) {
				ioException.printStackTrace();

				final String name = this.parkingLocation.getName();

				this.errorMessage = this.context
						.getString(R.string.unable_to_cancel_parking_location_x,
								name);
			} catch (final ServerException serverException) {
				serverException.printStackTrace();

				final long id = this.parkingLocation.getId();

				this.errorMessage =
						this.context.getString(R.string.failed_to_cancel_parking_location,
								id);
			}

			return null;
		}

		@Override
		protected void onPostExecute(final Void result) {
			super.onPostExecute(result);

			if (this.errorMessage == null) {
				this.state.myParkingLocationReservations.remove(this.parkingLocation);
				this.viewUpdater.update();

				final Toast toast =
						Toast.makeText(this.context,
								R.string.parking_location_successfully_canceled,
								Toast.LENGTH_SHORT);

				toast.show();
			} else {
				final Toast toast =
						Toast.makeText(this.context, this.errorMessage, Toast.LENGTH_SHORT);

				toast.show();
			}
		}
	}

	private static final class CancelOnClickListener implements View.OnClickListener {
		private final ParkingLocation parkingLocation;
		private final State state;
		private final ViewUpdater viewUpdater;

		private CancelOnClickListener(final ParkingLocation parkingLocation,
				final State state, final ViewUpdater viewUpdater) {
			this.parkingLocation = parkingLocation;
			this.state = state;
			this.viewUpdater = viewUpdater;
		}

		@Override
		public void onClick(final View view) {
			final Context context = view.getContext();
			final CancelAsyncTask cancelAsyncTask =
					new CancelAsyncTask(context, this.parkingLocation, this.state,
							this.viewUpdater);

			cancelAsyncTask.execute();
		}
	}

	private static final class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {
		private final Context context;
		private final FragmentManager fragmentManager;
		private final State state;
		private final ViewHolder viewHolder;
		private final ViewUpdater viewUpdater;

		private ConnectionCallbacks(final Context context, final FragmentManager fragmentManager,
				final State state, final ViewHolder viewHolder,
				final ViewUpdater viewUpdater) {
			this.context = context;
			this.fragmentManager = fragmentManager;
			this.state = state;
			this.viewHolder = viewHolder;
			this.viewUpdater = viewUpdater;
		}

		@Override
		public void onConnected(@Nullable final Bundle bundle) {
			final SupportMapFragment supportMapFragment =
					(SupportMapFragment) this.fragmentManager
							.findFragmentById(R.id.map);
			final OnMapReadyCallback onMapReadyCallback =
					new OnMapReadyCallback(this.context, this.state, this.viewUpdater);

			supportMapFragment.getMapAsync(onMapReadyCallback);
		}

		@Override
		public void onConnectionSuspended(final int i) {
		}
	}

	private static final class OnCameraChangeListener implements GoogleMap.OnCameraChangeListener {
		private final Context context;
		private final State state;
		private final ViewUpdater viewUpdater;

		private OnCameraChangeListener(Context context, State state, ViewUpdater viewUpdater) {
			this.context = context;
			this.state = state;
			this.viewUpdater = viewUpdater;
		}

		@Override
		public void onCameraChange(final CameraPosition cameraPosition) {
			final Location cameraCenterLocation = new Location(LocationManager.GPS_PROVIDER);

			cameraCenterLocation.setLatitude(cameraPosition.target.latitude);
			cameraCenterLocation.setLongitude(cameraPosition.target.longitude);

			final ShowNearestParkingLocationsAsyncTask
					showNearestParkingLocationsAsyncTask =
					new ShowNearestParkingLocationsAsyncTask(this.context, cameraCenterLocation,
							this.state, this.viewUpdater);

			showNearestParkingLocationsAsyncTask.execute();
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
		private final ViewUpdater viewUpdater;

		private OnMapReadyCallback(final Context context, final State state,
				final ViewUpdater viewUpdater) {
			this.context = context;
			this.state = state;
			this.viewUpdater = viewUpdater;
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
							new ShowNearestParkingLocationsAsyncTask(this.context, latestLocation,
									this.state, this.viewUpdater);

					showNearestParkingLocationsAsyncTask.execute();

					final GoogleMap.OnCameraChangeListener onCameraChangeListener =
							new OnCameraChangeListener(context, state, viewUpdater);

					googleMap.setOnCameraChangeListener(onCameraChangeListener);
				}
			}
		}
	}

	private static final class OnMarkerClickListener implements GoogleMap.OnMarkerClickListener {
		private final State state;
		private final ViewUpdater viewUpdater;

		private OnMarkerClickListener(final State state, final ViewUpdater viewUpdater) {
			this.state = state;
			this.viewUpdater = viewUpdater;
		}

		@Override
		public boolean onMarkerClick(final Marker marker) {
			this.state.selectedParkingLocation = this.state.markerParkingLocations.get(marker);

			this.viewUpdater.update();

			return true;
		}
	}

	private static final class OnSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {
		private final ParkingLocation parkingLocation;
		private final ViewHolder viewHolder;

		private OnSeekBarChangeListener(final ParkingLocation parkingLocation,
				final ViewHolder viewHolder) {
			this.parkingLocation = parkingLocation;
			this.viewHolder = viewHolder;
		}

		@Override
		public void onProgressChanged(final SeekBar seekBar, final int progress,
				final boolean fromUser) {
			if (fromUser) {
				final String currentlyInputReservationLength =
						this.viewHolder.reservationLengthEditText.getText().toString();
				final int minReservationTimeInMinutes =
						this.parkingLocation.getMinReservationTimeInMinutes();
				final int reservationLength = progress + minReservationTimeInMinutes;

				if (currentlyInputReservationLength.length() == 0 ||
						Integer.parseInt(currentlyInputReservationLength) != reservationLength) {
					this.viewHolder.reservationLengthEditText
							.setText(String.valueOf(reservationLength));

					final int numberOfReservationLengthDigits =
							String.valueOf(reservationLength).length();

					this.viewHolder.reservationLengthEditText
							.setSelection(numberOfReservationLengthDigits);
				}
			}
		}

		@Override
		public void onStartTrackingTouch(final SeekBar seekBar) {
		}

		@Override
		public void onStopTrackingTouch(final SeekBar seekBar) {
		}
	}

	private static final class ReservationLengthInputTextWatcher implements TextWatcher {
		private final State state;
		private final ViewHolder viewHolder;

		private ReservationLengthInputTextWatcher(final State state,
				final ViewHolder viewHolder) {
			this.state = state;
			this.viewHolder = viewHolder;
		}

		@Override
		public void beforeTextChanged(final CharSequence s, final int start, final int count,
				final int after) {
		}

		@Override
		public void onTextChanged(final CharSequence s, final int start, final int before,
				final int count) {
		}

		@Override
		public void afterTextChanged(final Editable s) {
			final String reservationLengthString = s.toString();

			if (reservationLengthString.length() == 0) {
				this.viewHolder.reservationLengthSeekBar.setProgress(0);
			} else {
				if (this.state.selectedParkingLocation != null) {
					final int maxReservationTimeInMinutes =
							this.state.selectedParkingLocation.getMaxReservationTimeInMinutes();
					final int reservationLength = Integer.parseInt(reservationLengthString);
					final int minReservationTimeInMinutes =
							this.state.selectedParkingLocation.getMinReservationTimeInMinutes();
					final int newReservationLength = Math.max(minReservationTimeInMinutes,
							Math.min(maxReservationTimeInMinutes, reservationLength));
					final int newProgress = newReservationLength - minReservationTimeInMinutes;
					final int currentProgress =
							this.viewHolder.reservationLengthSeekBar.getProgress();

					if (currentProgress != newProgress) {
						this.viewHolder.reservationLengthSeekBar.setProgress(newProgress);
					}
				}
			}
		}
	}

	private static final class ReserveAsyncTask extends AsyncTask<Void, Void, Void> {
		private final Context context;
		private final ParkingLocation parkingLocation;
		private final State state;
		private final ViewHolder viewHolder;
		private final ViewUpdater viewUpdater;
		private String errorMessage;
		private int reservationLength;

		private ReserveAsyncTask(final Context context, final ParkingLocation parkingLocation,
				final State state, final ViewHolder viewHolder,
				final ViewUpdater viewUpdater) {
			this.context = context;
			this.parkingLocation = parkingLocation;
			this.state = state;
			this.viewHolder = viewHolder;
			this.viewUpdater = viewUpdater;
		}

		@Override
		protected Void doInBackground(final Void... params) {
			final ParkingLocationManager parkingLocationManager =
					ParkingLocationManager.getInstance();

			try {
				final ParkingLocation updatedParkingLocation =
						parkingLocationManager
								.reserve(this.parkingLocation, this.reservationLength);
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
		protected void onPreExecute() {
			super.onPreExecute();

			this.reservationLength = Integer.parseInt(
					this.viewHolder.reservationLengthEditText.getText().toString());
		}

		@Override
		protected void onPostExecute(final Void result) {
			super.onPostExecute(result);

			if (this.errorMessage == null) {
				this.state.myParkingLocationReservations.add(this.parkingLocation);

				this.viewUpdater.update();

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

	private static final class ReserveOnClickListener implements View.OnClickListener {
		private final ParkingLocation parkingLocation;
		private final State state;
		private final ViewHolder viewHolder;
		private final ViewUpdater viewUpdater;

		private ReserveOnClickListener(final ParkingLocation parkingLocation,
				final State state, final ViewHolder viewHolder,
				final ViewUpdater viewUpdater) {
			this.parkingLocation = parkingLocation;
			this.state = state;
			this.viewHolder = viewHolder;
			this.viewUpdater = viewUpdater;
		}

		@Override
		public void onClick(final View view) {
			final String reservationLengthString =
					this.viewHolder.reservationLengthEditText.getText().toString();
			final Context context = view.getContext();

			if (reservationLengthString.length() == 0) {
				final Toast toast =
						Toast.makeText(context, R.string.no_reservation_length_specified,
								Toast.LENGTH_SHORT);

				toast.show();
			} else {
				final int reservationLength = Integer.parseInt(reservationLengthString);
				final int minReservationTimeInMinutes =
						this.parkingLocation.getMinReservationTimeInMinutes();
				final int maxReservationTimeInMinutes =
						this.parkingLocation.getMaxReservationTimeInMinutes();

				if (reservationLength >= minReservationTimeInMinutes &&
						reservationLength <= maxReservationTimeInMinutes) {
					final ReserveAsyncTask reserveAsyncTask =
							new ReserveAsyncTask(context, this.parkingLocation, this.state,
									this.viewHolder, this.viewUpdater);

					reserveAsyncTask.execute();
				} else {
					final String message =
							context.getString(R.string.reservation_length_must_be_between_x_and_y,
									minReservationTimeInMinutes, maxReservationTimeInMinutes);
					final Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);

					toast.show();
				}
			}
		}
	}

	private static final class ShowNearestParkingLocationsAsyncTask
			extends AsyncTask<Void, Void, List<ParkingLocation>> {
		private final Context context;
		private final Location location;
		private final State state;
		private final ViewUpdater viewUpdater;
		private String errorMessage;

		private ShowNearestParkingLocationsAsyncTask(final Context context, final Location location,
				final State state, final ViewUpdater viewUpdater) {
			this.context = context;
			this.location = location;
			this.state = state;
			this.viewUpdater = viewUpdater;
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
					new OnMarkerClickListener(this.state, this.viewUpdater);

			this.state.googleMap.setOnMarkerClickListener(
					onMarkerClickListener);
		}
	}

	private static final class State {
		public GoogleApiClient googleApiClient;
		public GoogleMap googleMap;
		public Map<Marker, ParkingLocation> markerParkingLocations = new HashMap<>();
		public List<ParkingLocation> myParkingLocationReservations = new ArrayList<>();
		public ParkingLocation selectedParkingLocation;
	}

	public static final class ViewHolder {
		@BindView(R.id.already_reserved)
		public View alreadyReservedView;

		@BindView(R.id.cancel_reservation)
		public Button cancelReservation;

		@BindView(R.id.minutes_label)
		public View minutesLabelView;

		@BindView(R.id.name)
		public TextView parkingLocationNameTextView;

		@BindView(R.id.cost_per_minute)
		public TextView parkingLocationCostPerMinuteTextView;

		@BindView(R.id.reservation_length)
		public EditText reservationLengthEditText;

		@BindView(R.id.reservation_length_slider)
		public SeekBar reservationLengthSeekBar;

		@BindView(R.id.reserve)
		public Button reserveButton;

		@BindView(R.id.select_nearby_parking_location)
		public View selectNearbyParkingLocationView;

		@BindView(R.id.successfully_reserved)
		public View successfullyReservedView;
	}

	private static final class ViewUpdater {
		private final Context context;
		private final State state;
		private final ViewHolder viewHolder;

		private ViewUpdater(final Context context, final State state, final ViewHolder viewHolder) {
			this.context = context;
			this.state = state;
			this.viewHolder = viewHolder;
		}

		public void prepare() {
			this.viewHolder.alreadyReservedView.setVisibility(View.GONE);
			this.viewHolder.cancelReservation.setVisibility(View.INVISIBLE);
			this.viewHolder.minutesLabelView.setVisibility(View.INVISIBLE);
			this.viewHolder.parkingLocationNameTextView.setText(null);
			this.viewHolder.parkingLocationCostPerMinuteTextView.setText(null);
			this.viewHolder.reservationLengthEditText.setVisibility(View.INVISIBLE);
			this.viewHolder.reservationLengthSeekBar.setVisibility(View.INVISIBLE);
			this.viewHolder.reserveButton.setVisibility(View.INVISIBLE);
			this.viewHolder.selectNearbyParkingLocationView.setVisibility(View.VISIBLE);
			this.viewHolder.successfullyReservedView.setVisibility(View.INVISIBLE);

			final ReservationLengthInputTextWatcher reservationLengthInputTextWatcher =
					new ReservationLengthInputTextWatcher(this.state, this.viewHolder);

			this.viewHolder.reservationLengthEditText
					.addTextChangedListener(reservationLengthInputTextWatcher);
		}

		public void update() {
			if (this.state.selectedParkingLocation == null) {
				this.prepare();
			} else {
				if (this.state.myParkingLocationReservations
						.contains(this.state.selectedParkingLocation)) {
					this.viewHolder.alreadyReservedView.setVisibility(View.GONE);

					final CancelOnClickListener cancelOnClickListener = new CancelOnClickListener(
							this.state.selectedParkingLocation, this.state, this);

					this.viewHolder.cancelReservation.setOnClickListener(cancelOnClickListener);

					this.viewHolder.cancelReservation.setVisibility(View.VISIBLE);
					this.viewHolder.minutesLabelView.setVisibility(View.INVISIBLE);
					this.viewHolder.reserveButton.setVisibility(View.INVISIBLE);
					this.viewHolder.reservationLengthEditText.setVisibility(View.INVISIBLE);
					this.viewHolder.reservationLengthSeekBar.setVisibility(View.INVISIBLE);
					this.viewHolder.successfullyReservedView.setVisibility(View.VISIBLE);
				} else {
					this.viewHolder.cancelReservation.setVisibility(View.INVISIBLE);

					final String name = this.state.selectedParkingLocation.getName();

					this.viewHolder.parkingLocationNameTextView.setText(name);

					final BigDecimal costPerMinute =
							this.state.selectedParkingLocation.getCostPerMinute();
					final String costPerMinuteString = this.context
							.getString(R.string.cost_per_minute, costPerMinute.toString());

					this.viewHolder.parkingLocationCostPerMinuteTextView
							.setText(costPerMinuteString);

					final boolean reserved = this.state.selectedParkingLocation.getReserved();

					if (reserved) {
						this.viewHolder.alreadyReservedView.setVisibility(View.VISIBLE);
						this.viewHolder.minutesLabelView.setVisibility(View.INVISIBLE);
						this.viewHolder.reservationLengthEditText.setVisibility(View.INVISIBLE);
						this.viewHolder.reservationLengthSeekBar.setVisibility(View.INVISIBLE);
						this.viewHolder.reserveButton.setVisibility(View.INVISIBLE);
					} else {
						this.viewHolder.alreadyReservedView.setVisibility(View.INVISIBLE);
						this.viewHolder.minutesLabelView.setVisibility(View.VISIBLE);
						this.viewHolder.reservationLengthEditText.setVisibility(View.VISIBLE);

						final int minReservationTimeInMinutes =
								this.state.selectedParkingLocation.getMinReservationTimeInMinutes();

						if (this.viewHolder.reservationLengthEditText.getText().length() == 0) {
							this.viewHolder.reservationLengthEditText
									.setText(String.valueOf(minReservationTimeInMinutes));
						}

						final int maxReservationTimeInMinutes =
								this.state.selectedParkingLocation.getMaxReservationTimeInMinutes();
						final int maxNumberOfReservationTimeDigits =
								String.valueOf(maxReservationTimeInMinutes).length();
						final InputFilter[] inputFilters = new InputFilter[1];

						inputFilters[0] =
								new InputFilter.LengthFilter(maxNumberOfReservationTimeDigits);

						this.viewHolder.reservationLengthEditText.setFilters(inputFilters);
						this.viewHolder.reservationLengthSeekBar.setVisibility(View.VISIBLE);

						final int seekBarMax =
								maxReservationTimeInMinutes - minReservationTimeInMinutes;

						this.viewHolder.reservationLengthSeekBar.setMax(seekBarMax);

						final OnSeekBarChangeListener onSeekBarChangeListener =
								new OnSeekBarChangeListener(this.state.selectedParkingLocation,
										this.viewHolder);

						this.viewHolder.reservationLengthSeekBar
								.setOnSeekBarChangeListener(onSeekBarChangeListener);
						this.viewHolder.reserveButton.setVisibility(View.VISIBLE);

						final ReserveOnClickListener reserveOnClickListener =
								new ReserveOnClickListener(this.state.selectedParkingLocation,
										this.state, this.viewHolder,
										this);

						this.viewHolder.reserveButton.setOnClickListener(reserveOnClickListener);
					}

					this.viewHolder.selectNearbyParkingLocationView.setVisibility(View.GONE);
					this.viewHolder.successfullyReservedView.setVisibility(View.GONE);
				}
			}
		}
	}
}