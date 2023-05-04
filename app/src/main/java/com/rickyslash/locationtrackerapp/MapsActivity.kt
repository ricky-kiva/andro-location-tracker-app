package com.rickyslash.locationtrackerapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.rickyslash.locationtrackerapp.databinding.ActivityMapsBinding
import java.util.concurrent.TimeUnit

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest

    private var isTracking = false
    private lateinit var locationCallback: LocationCallback

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false -> {
                // precise location access granted
                getMyLastLocation()
            }
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false -> {
                // only approximate location access granted
                getMyLastLocation()
            }
            else -> {
                // No location access granted
            }
        }
    }

    // launcher to check if the failure is resolvable (can be fixed by user) (ex: set GPS permission)
    private val resolutionLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        when (result.resultCode) {
            RESULT_OK -> {
                Log.i(TAG, "onActivityResult: All location settings are satisfied.")
            }
            RESULT_CANCELED -> {
                Toast.makeText(this@MapsActivity, "Please activate GPS", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)

        // instantiate FusedLocationProvider
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    // function to call when map is ready
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        /* Not used because we use live location
        getMyLastLocation()*/

        // build LocationRequest
        createLocationRequest()

        // callback for when there is location update
        createLocationCallback()



        binding.btnStart.setOnClickListener {
            if (!isTracking) {
                updateTrackingStatus(true)
                startLocationUpdates()
            } else {
                updateTrackingStatus(false)
                stopLocationUpdates()
            }
        }
    }

    // function to start location updates
    private fun startLocationUpdates() {
        try {
            // do location updates
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Error: ${e.message}")
        }
    }

    // function to stop location updates
    private fun stopLocationUpdates() {
        // remove location update for the callback
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                for (location in p0.locations) {
                    Log.d(TAG, "onLocationResult: ${location.latitude}, ${location.longitude}")
                }
            }
        }
    }

    private fun createLocationRequest() {
        // set parameter for LocationRequest
        locationRequest = LocationRequest.create().apply {
            interval = TimeUnit.SECONDS.toMillis(1)
            maxWaitTime = TimeUnit.SECONDS.toMillis(1)
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // make new instance of LocationRequest using parameter from `locationRequest` var
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        // check whether the `device location settings` are configured appropriately for app's needs
        // example: if app require high-accuracy, request checks if device location settings are set to high-accuracy also
        val client = LocationServices.getSettingsClient(this)
        client.checkLocationSettings(builder.build())
            .addOnSuccessListener {
                getMyLastLocation()
            }
            .addOnFailureListener { e ->
                // checks if the failure is resolvable (can be fixed by user) (ex: set GPS permission)
                if (e is ResolvableApiException) {
                    try {
                        resolutionLauncher.launch(IntentSenderRequest.Builder(e.resolution).build())
                    } catch (sendEx: IntentSender.SendIntentException) {
                        Toast.makeText(this@MapsActivity, sendEx.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    // get the latest map
    private fun getMyLastLocation() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION) && checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    showStartMarker(loc)
                } else {
                    Toast.makeText(this@MapsActivity, "Location is not found. Try again.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // show marker on start
    private fun showStartMarker(loc: Location) {
        val startLocation = LatLng(loc.latitude, loc.longitude)
        mMap.addMarker(
            MarkerOptions()
                .position(startLocation)
                .title(getString(R.string.start_point))
        )
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 17f))
    }

    private fun updateTrackingStatus(newStatus: Boolean) {
        isTracking = newStatus
        if (isTracking) {
            binding.btnStart.text = getString(R.string.stop_running)
        } else {
            binding.btnStart.text = getString(R.string.start_running)
        }
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        if (isTracking) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    companion object {
        private const val TAG = "MapsActivity"
    }

}

// 2 ways of using Android's location sensor:
// - precise: (ACCESS_FINE_LOCATION) get precise 4-50 meter position (good for object & address tracking)
// - approximate: (ACCESS_COARSE_LOCATION) get approx 1 mil position (good for weather & social media application)

// Fused Location Provider: to adjust how sensor is accessed after getting permission
// - it will get location data based on accuracy & battery efficiency
// - the data is either from GPS Satellite, BTS internet tower, or WiFi

// Location Manager: former fused location that need to manage location data source from it's own

// 3 Scenario available from Fused Location Provider:
// - Last Known Location: get last known location from device (most battery-efficient, not really accurate)
// - Current Location: get current location (battery-confusing, accurate)
// - Location Updates: get to know new location in some interval (good for cases like live location update)

// LocationRequest object configuration:
// - Update interval: set interval getting new data in millisecond (adjusted by battery efficiency)
// - Fastest Update Interval: set fastest interval in getting new location data
// - Priority: set how important the request:
// --- PRIORITY_HIGH_ACCURACY: Get most accurate location
// --- PRIORITY_BALANCED_POWER_ACCURACY: Get location within 100 meter
// --- PRIORITY_LOWER_ACCURACY: Get location within 10 km accuracy
// --- PRIORITY_NO_POWER: Get new location from another app