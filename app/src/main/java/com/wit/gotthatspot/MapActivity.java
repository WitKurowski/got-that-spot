package com.wit.gotthatspot;

import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback {
	private GoogleMap googleMap;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.setContentView(R.layout.map);

		// Obtain the SupportMapFragment and get notified when the map is ready to be used.
		final FragmentManager supportFragmentManager = getSupportFragmentManager();
		final SupportMapFragment supportMapFragment =
				(SupportMapFragment) supportFragmentManager
						.findFragmentById(R.id.map);

		supportMapFragment.getMapAsync(this);
	}

	/**
	 * Manipulates the map once available. This callback is triggered when the map is ready to be
	 * used. This is where we can add markers or lines, add listeners or move the camera. In this
	 * case, we just add a marker near Sydney, Australia. If Google Play services is not installed
	 * on the device, the user will be prompted to install it inside the SupportMapFragment. This
	 * method will only be triggered once the user has installed Google Play services and returned
	 * to the app.
	 */
	@Override
	public void onMapReady(final GoogleMap googleMap) {
		this.googleMap = googleMap;

		// Add a marker in Sydney and move the camera
		final LatLng sydney = new LatLng(-34, 151);
		final MarkerOptions markerOptions = new MarkerOptions();

		markerOptions.position(sydney);
		markerOptions.title("Marker in Sydney");

		this.googleMap.addMarker(markerOptions);
		this.googleMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
	}
}