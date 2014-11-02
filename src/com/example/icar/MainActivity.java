package com.example.icar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Manager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;

import android.location.Location;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.app.DialogFragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
///import android.support.v4.app.DialogFragment;
///import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

/**
 * Copyright (c) 2014 Tom Zhou
 * @author tomzhou
 * 
 */
public class MainActivity extends Activity implements
GooglePlayServicesClient.ConnectionCallbacks,
GooglePlayServicesClient.OnConnectionFailedListener,
DataRecorder.Parameters.geoInfoCallback {
	private static final String TAG = "iCarMainActivity";
	private static final int REQUEST_ENABLE_BT = 0x68;
	private static final int REQUEST_ENABLE_WIFI = 0x86;
	
	// working mode: 0 - offline, 1 - bluetooth only, 2 - wifi only, 3 - both bluetooth and wifi available
    private final static int MODE_OFFLINE = 0;
    private final static int      MODE_BT = 1;
    private final static int    MODE_WIFI = 2;
    private final static int MODE_BT_WIFI = 3;
    
    private int workingMode; 

    // bluetooth device for ELM327
    private final static String mBluetoothDeviceName = "OBD";
    private final static UUID mBluetoothDeviceUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothSocket mBluetoothSocket;
    
    // wifi connection
    private WifiManager mWifiManager;
    
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    // GPS/location service
    private LocationClient mLocationClient;
    private Location mStartLocation;

    // @description get current location as string "lon:lat:alt"
    public String getSimpleLocation() {
    	if ((mLocationClient != null) && (mLocationClient.isConnected())) {
    		Location l = mLocationClient.getLastLocation();

    		String lstr = "";
    		lstr += l.getLongitude(); lstr += ":";
    		lstr +=  l.getLatitude(); lstr += ":";
    		lstr +=  l.getAltitude(); 

    		return lstr;
    	} else {
    		return null;
    	}
    }

    // event emitter for async opeartions
    private EventEmitter eventemitter;
    
    // Global constants
    /*
     * Define a request code to send to Google Play services
     * This code is returned in Activity.onActivityResult
     */
    private final static int
            CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    
    // Define a DialogFragment that displays the error dialog
    public static class ErrorDialogFragment extends DialogFragment {
        // Global field to contain the error dialog
        private Dialog mDialog;
        // Default constructor. Sets the dialog field to null
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }
        // Set the dialog to display
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }
        // Return a Dialog to the DialogFragment.
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }
    
    // @description determine if geo-location available
    private boolean isGeoAvailable() {
    	return servicesConnected() && (mLocationClient != null) && mLocationClient.isConnecting();
    }
    
    /*
     * Handle results returned to the FragmentActivity
     * by Google Play services
     */
    private boolean servicesConnected() {
        // Check that Google Play services is available
        int resultCode =
                GooglePlayServicesUtil.
                        isGooglePlayServicesAvailable(this);
        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            // In debug mode, log the status
            Log.d(TAG, "Google Play services is available.");
            // Continue
            return true;
        // Google Play services was not available for some reason
        } else {
            // Get the error dialog from Google Play services
            Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
            		resultCode,
                    this,
                    CONNECTION_FAILURE_RESOLUTION_REQUEST);

            // If Google Play services can provide an error dialog
            if (errorDialog != null) {
                // Create a new DialogFragment for the error dialog
                ErrorDialogFragment errorFragment =
                        new ErrorDialogFragment();
                // Set the dialog in the DialogFragment
                errorFragment.setDialog(errorDialog);
            }
            return false;
        }
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    
    // OBD2 reader
    private ELM327 mELM327;
    private OBDReader mOBD2Reader;
    
    // OBD2 data recorder
    private ArrayList<DataRecorder> mDataRecorders;
    
    // CouchBase database manager
    private Manager mDBManager;
    private final static String mDBNameRealtimeDrivingData = "obd2_realtime_driving_data";
    private final static String   mDBNameFreezeDrivingData = "obd2_freeze_driving_data";
    private final static String              mDBNameDTCLog = "obd2_dtc_log";
    private final static String  mDBNameDTCLogClearHistory = "obd2_dtc_log_clear_history";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);	
		
		// create eventEmitter
		if (eventemitter == null) eventemitter = new EventHandler();
		
		
		// Check that the activity is using the layout version with
        // the fragment_container FrameLayout
        if (findViewById(R.id.fragment_container) != null) {

            // However, if we're being restored from a previous state,
            // then we don't need to do anything and should return or else
            // we could end up with overlapping fragments.
            if (savedInstanceState != null) {
                return;
            }
            
            // ELM327 debug ui
            ELM327DebugFragment elm327Fragment = new ELM327DebugFragment();
            elm327Fragment.setArguments(getIntent().getExtras());
            
            getFragmentManager().beginTransaction()
            .add(R.id.fragment_container, elm327Fragment).commit();
            
            /*
            // DrivingStatusFragment
            DrivingStatusFragment drvstatusFragment = new DrivingStatusFragment();
            drvstatusFragment.setArguments(getIntent().getExtras());
            
            getFragmentManager().beginTransaction()
            .add(R.id.fragment_container, drvstatusFragment).commit();
            
            // Create a new Fragment to be placed in the activity layout
            DiagnosisReportFragment firstFragment = new DiagnosisReportFragment();
            
            // In case this activity was started with special instructions from an
            // Intent, pass the Intent's extras to the fragment as arguments
            firstFragment.setArguments(getIntent().getExtras());
            
            // Add the fragment to the 'fragment_container' FrameLayout
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, firstFragment).commit();
                    */
        }
        
		// 1.
		// restore savedState
		
		// 2.
		// create CouchBase database manager
		 try {
			 mDBManager = new Manager(getApplicationContext().getFilesDir(), Manager.DEFAULT_OPTIONS);
		 } catch (IOException e) {
		     Log.e(TAG, "Cannot create couchbase manager object");
		     return;
		 }
				
		// 3.
		// check bluetooth availability
		boolean btok = true;

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
		    // Device does not support Bluetooth
			btok = false;
		}
		Log.d(TAG, "bluetooth connection "+btok);
		
		// 5.
		// check wifi availability
		boolean wifiok = false;
		
		mWifiManager = (WifiManager) getBaseContext().getSystemService(Context.WIFI_SERVICE);
		if ((mWifiManager != null) && mWifiManager.isWifiEnabled()) {
			wifiok = true;
		}
		Log.d(TAG, "wifi connection "+wifiok);
		wifiok = false;


		// 6.
		// set connection working mode
		if (btok && wifiok) {
			// both bluetooth and wifi available
			workingMode = MODE_BT_WIFI;
		} else if (btok) {
			// bluetooth only
			workingMode = MODE_BT;
		} else if (wifiok) {
			// wifi only
			workingMode = MODE_WIFI;
		} else {
			// offline neither bluetooth nor wifi
			workingMode = MODE_OFFLINE;
		}
		
		// 7.
		// register bluetooth BroadcastReceiver 
		if (btok) {
			// Register the BroadcastReceiver
			IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
			registerReceiver(mBtReceiver, filter);
		}
		
		// 8.
		// create GPS client
		/*
         * Create a new location client, using the enclosing class to
         * handle callbacks.
         */
		if (servicesConnected()) {
			mLocationClient = new LocationClient(this, this, this);
		} else {
			mLocationClient = null;
		}

		// 9.
		// enter UI process
		enterUI();
				
	}

	/*
	 * Called when the Activity becomes visible.
	 */
	@Override
	protected void onStart() {
		super.onStart();

		// Connect the location client.
		if (mLocationClient != null) mLocationClient.connect();

		// 

		// TBD... launch after location connected 
		// start app in different connection mode
		if (workingMode == MODE_BT_WIFI) {
			// both bluetooth and wifi
			startAppWithBluetoothWifi();
		} else if (workingMode == MODE_BT) {
			// bluetooth only
			startAppWithBluetooth();
		} else if (workingMode == MODE_WIFI) {
			// wifi only
			startAppWithWifi();
		} else {
			// offline neither bluetooth nor wifi
			startAppOffline();
		}

	}

	/*
	 * Called when the Activity is no longer visible.
	 */
	@Override
	protected void onStop() {
		// disconnecting bluetooth socket
		if (mBluetoothSocket != null) {
			Log.d(TAG, "close bluetooth socket");
			try {
				mBluetoothSocket.close();
				mBluetoothSocket = null;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				///e.printStackTrace();
			}
		}

		// Disconnecting the location client invalidates it.
		Log.d(TAG, "close location client");
		if (mLocationClient != null) mLocationClient.disconnect();

		super.onStop();
	}
	
	// @description enter UI
	private boolean enterUI() {
		boolean ok = false;
		
		if (workingMode == MODE_BT_WIFI) {
			
		} else if (workingMode == MODE_BT) {
			
		} else if (workingMode == MODE_WIFI) {
			
		} else if (workingMode == MODE_OFFLINE) {
			
		}
		
		return ok;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    int itemId = item.getItemId();
		if (itemId == R.id.action_diagnosis) {
			///openSearch();
			return true;
		} else if (itemId == R.id.action_fuel_consumption) {
			///composeMessage();
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_activity_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}
	
	protected void onActivityResult (int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		// bluetooth activity
		case REQUEST_ENABLE_BT:
			if (resultCode ==  RESULT_OK) {
				if (workingMode == MODE_BT_WIFI) {
					startAppWithBluetoothWifi();
				} else if (workingMode == MODE_BT) {
					startAppWithBluetooth();
				}
				Log.d(TAG, "bluetooth enable ok");
			} else {
				Log.d(TAG, "bluetooth enable fail");
			}
			break;

	    // wifi activity
		case REQUEST_ENABLE_WIFI:
			if (resultCode ==  RESULT_OK) {
				if (workingMode == MODE_BT_WIFI) {
					startAppWithBluetoothWifi();
				} else if (workingMode == MODE_WIFI) {
					startAppWithWifi();
				}
				Log.d(TAG, "wifi enable ok");
			} else {
				Log.d(TAG, "wifi enable fail");
			}
			break;

	    // google play service
		case CONNECTION_FAILURE_RESOLUTION_REQUEST :
            /*
             * If the result code is Activity.RESULT_OK, try
             * to connect again
             */
                if (resultCode == RESULT_OK) {
                    /*
                     * Try the request again
                     */
                } else {
                	
                }
			break;
			
		default:
			break;
		}
	}

	// @description bluetooth socket connection task
	private class btConnectTask extends AsyncTask<BluetoothDevice, Void, BluetoothSocket> {
	    /** The system calls this to perform work in a worker thread and
	      * delivers it the parameters given to AsyncTask.execute() */
		protected BluetoothSocket doInBackground(BluetoothDevice... arg0) {
			BluetoothSocket bts = null;

			// Get a BluetoothSocket to connect with the given BluetoothDevice
			try {
				// MY_UUID is the app's UUID string, also used by the server code
				bts = arg0[0].createRfcommSocketToServiceRecord(mBluetoothDeviceUUID);
			} catch (IOException e) {
				Log.e(TAG, "create bluetooth socket fail: "+e);
				return null;
			}

			try {
				// Connect the device through the socket. This will block
				// until it succeeds or throws an exception
				bts.connect();
			} catch (IOException connectException) {
				// Unable to connect; close the socket and get out
				try {
					bts.close();
				} catch (IOException closeException) { 
					Log.w(TAG, "close bluetooth socket fail: "+closeException);
				}

				Log.e(TAG, "connect bluetooth socket fail: "+connectException);
				return null;
			}
			
			return bts;
		}
	    
	    /** The system calls this to perform work in the UI thread and delivers
	      * the result from doInBackground() */
		protected void onPostExecute(BluetoothSocket bts) {
			if (bts != null) {
				Log.d(TAG, "connect bluetooth socket success");

				// close last socket
				if (mBluetoothSocket != null) {
					try {
						mBluetoothSocket.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						///e.printStackTrace();
						Log.d(TAG, "close last bluetooth socket fail");
					}
					Log.d(TAG, "close last bluetooth socket");
				}
				mBluetoothSocket = bts;

				// startApp again
				if (workingMode == MODE_BT_WIFI) {
					startAppWithBluetoothWifi();
				} else if (workingMode == MODE_BT) {
					startAppWithBluetooth();
				}
			} else {
				Log.e(TAG, "connect bluetooth socket fail");
			}
		}
	}
	
	// Create a BroadcastReceiver for ACTION_FOUND
	private final BroadcastReceiver mBtReceiver = new BroadcastReceiver() {
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        // When discovery finds a device
	        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
	            // Get the BluetoothDevice object from the Intent
	            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
	            
	        	Log.d(TAG, "found discorved bt devices: " + device.getName() + "@" + device.getAddress());
	        	if (device.getName().toUpperCase().contains(mBluetoothDeviceName)) {	
	        		Log.d(TAG, "found discorved OBD device: "+device.getName() + "@" + device.getAddress());
	        		
	        		if (mBluetoothDevice != null) {
	        			return;
	        		}
	        		mBluetoothDevice = device;
	        		
	    			// Cancel discovery because it will slow down the connection
	    			mBluetoothAdapter.cancelDiscovery();
	                
	                // connect to bluetooth device
	        		new btConnectTask().execute(mBluetoothDevice);
	        	}
	        }
	    }
	};
	
	// @description working with bluetooth and wifi available
	// notes: only call from UI thread
	private void startAppWithBluetoothWifi() {
		
	}
	
	// @description working with bluetooth available
	// notes: only call from UI thread
	private void startAppWithBluetooth() {
		boolean ok = false;
		
		// 1.
		// check and enable bluetooth
		if (!mBluetoothAdapter.isEnabled()) {
		    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		} else {
			// 2.
			// try to find first "obd" device
			if (mBluetoothDevice == null) {
				// 2.1
				// try if there are paired device
				Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
				
				if ((pairedDevices != null) && (pairedDevices.size() > 0)) {
					// Loop through paired devices
					for (BluetoothDevice device : pairedDevices) {
						Log.d(TAG, "found paired bt devices: " + device.getName() + "@" + device.getAddress());
						if (device.getName().toUpperCase().contains(mBluetoothDeviceName)) {
							Log.d(TAG, "found paired OBD device: "+device.getName() + "@" + device.getAddress());
							mBluetoothDevice = device;
							break;
						}
					}
				}

				// 2.2
				// try discovery OBD device again
				if (mBluetoothDevice == null) {
                    ok = mBluetoothAdapter.startDiscovery();
                    Log.d(TAG, "start bluetooth discovery..."+(ok ? "success" : "fail"));
				} else {
					if (mBluetoothSocket == null) {
						// 3.
						// connect to bluetooth socket
		        		new btConnectTask().execute(mBluetoothDevice);
					} else {
						// 4.
						// start services
						ok = startOBD2Reader();
						Log.d(TAG, "start OBD2 reader..."+(ok ? "success" : "fail"));

						ok = startOBD2DataRecorder();
						Log.d(TAG, "start OBD2 data recorder..."+(ok ? "success" : "fail"));
					}
				}
			} else {
				if (mBluetoothSocket == null) {
					// 3.
					// connect to bluetooth socket
	        		new btConnectTask().execute(mBluetoothDevice);
				} else {
					// 4.
					// start services
					ok = startOBD2Reader();
					Log.d(TAG, "start OBD2 reader..."+(ok ? "success" : "fail"));

					ok = startOBD2DataRecorder();
					Log.d(TAG, "start OBD2 data recorder..."+(ok ? "success" : "fail"));
				}
			}
		}
	
	}
	
	// @description working with wifi available
	// notes: only call from UI thread
	private void startAppWithWifi() {
		
	}
	
	// @description working offline without bluetooth and wifi
	// notes: only call from UI thread
	private void startAppOffline() {
		
	}
	
	// @description start OBD2 reader
	private boolean startOBD2Reader() {
		boolean ok = false;
		
		if ((mBluetoothDevice != null) && 
			(mBluetoothSocket != null) &&
			(mBluetoothSocket.isConnected())) {
			// create ELM327 driver
			if (mELM327 == null) {
				// FLAG_S1 | FLAG_H1 
				try {
					mELM327 = new ELM327(
							mBluetoothSocket.getInputStream(),
							mBluetoothSocket.getOutputStream(),
							ELM327.FLAG_S1 | ELM327.FLAG_H1);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					///e.printStackTrace();
					Log.e(TAG, "create ELM327 driver fail: "+e);
					return false;
				}
			}

			// create OBDReader
			if (mOBD2Reader == null) {
				mOBD2Reader = new OBDReader(mELM327);
			}
			
			ok = true;
		}
		
		return ok;
	}

	// @description start OBD2 data recorder
	private boolean startOBD2DataRecorder() {
		boolean ok = false;
		
		if (mDataRecorders == null) {
			mDataRecorders = new ArrayList<DataRecorder>();
		}
		
		// 1.
		// create/get CouchBase database
		
		 // create a name for the database and make sure the name is legal
		 String dbname = mDBNameRealtimeDrivingData;
		 if (!Manager.isValidDatabaseName(dbname)) {
		     Log.e(TAG, "Bad database name");
		     return false;
		 }

		 // create a new database
		 Database database = null;
		 try {
		     database = mDBManager.getDatabase(dbname);
		 } catch (CouchbaseLiteException e) {
		     Log.e(TAG, "Cannot get database");
		     return false;
		 }

		
		// 2.
		// start data recorder with specific parameter
		DataRecorder.Parameters param = new DataRecorder.Parameters();
		List<DataRecorder.data_record_t> records = new ArrayList<DataRecorder.data_record_t>();
		
		// fill user specific
		param.setUsrinfo("tomzhou.icar");
		param.setSecinfo("test");
		
		// fill geo-location specific
		param.setGeoenabled(isGeoAvailable());
		if (isGeoAvailable()) {
			param.setGeoinfo(this);
		}
		
		// fill sample specific
		param.setSample_oneshot(false);
		param.setSample_interval(5000); // 5s
		param.setSample_precycle(0);
		param.setSample_window(10*60*1000); // 10mins
		param.setSample_dutycycle(param.getSample_window()); // no idle
		
		// fill eventEmitter
		param.setEventemitter(eventemitter);
		
		// 2.1
		// realtime fuel consumption related data
		// TBD... MAF or EFR selection
		// EFR 
		records.add(new DataRecorder.efr_realtime_fuel_consumption_record_t());
		
		// 2.2
		// average fuel consumption related data
		records.add(new DataRecorder.average_fuel_consumption_record_t());

		// 2.3
		// driving status
		
		// fill records
		param.setRecords(records);
		
		// 3.
		// launch recorder
		DataRecorder recorder = new DataRecorder(param, mOBD2Reader, database);
		recorder.start();
		
		// 3.1
		// manage it
		mDataRecorders.add(recorder);
		
		return ok;
	}

	// @description start local database sync
	private boolean startDataSyncer() {
		boolean ok = false;
		
		return ok;
	}

	/*
	 * Called by Location Services when the request to connect the
	 * client finishes successfully. At this point, you can
	 * request the current location or start periodic updates
	 */
	@Override
	public void onConnected(Bundle dataBundle) {
		// Display the connection status
		///Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "GPS client connected");
        
        // cache start location
        mStartLocation = mLocationClient.getLastLocation();
	}

	/*
	 * Called by Location Services if the connection to the
	 * location client drops because of an error.
	 */
	@Override
	public void onDisconnected() {
		// Display the connection status
		///Toast.makeText(this, "Disconnected. Please re-connect.",
		///        Toast.LENGTH_SHORT).show();
        Log.d(TAG, "GPS client disconnected");

	}

	/*
	 * Called by Location Services if the attempt to
	 * Location Services fails.
	 */
	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "GPS client connect failed");

		/*
		 * Google Play services can resolve some errors it detects.
		 * If the error has a resolution, try sending an Intent to
		 * start a Google Play services activity that can resolve
		 * error.
		 */
		if (connectionResult.hasResolution()) {
			try {
				// Start an Activity that tries to resolve the error
				connectionResult.startResolutionForResult(
						this,
						CONNECTION_FAILURE_RESOLUTION_REQUEST);
				/*
				 * Thrown if Google Play services canceled the original
				 * PendingIntent
				 */
			} catch (IntentSender.SendIntentException e) {
				// Log the error
				///e.printStackTrace();
			}
		} else {
			/*
			 * If no resolution is available, display a dialog to the
			 * user with the error.
			 */
			///showErrorDialog(connectionResult.getErrorCode());
		}
	}


}
