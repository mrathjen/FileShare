package com.example.fileshare;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


public class SetupActivity extends Activity {
	
	private static final String TAG = "SetupActivity";
	
	private static final boolean D = true;
	
	// Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE 	= 1;
    public static final int MESSAGE_READ 			= 2;
    public static final int MESSAGE_WRITE 			= 3;
    public static final int MESSAGE_DEVICE_NAME 	= 4;
    public static final int MESSAGE_TOAST 			= 5;
    
    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME 	= "device_name";
    public static final String TOAST 		= "toast";

	private static final int REQUEST_CONNECT_DEVICE_SECURE 	= 0;
	private static final int REQUEST_ENABLE_BT 				= 1;
	private static final int REQUEST_DISCOVERABLE 			= 2;
	
	// 0 as input to discovery makes it always discoverable
	private static final int DISCOVERY_DURATION = 15;
	
	private BluetoothAdapter mBtAdapter;

	private BluetoothCommService mChatService;
	
	private ArrayAdapter<String> mNewDevicesArrayAdapter;
	
	private String mConnectedDeviceName;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);
		
        // Get local Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = BluetoothCommService.createService(this, mHandler);
        mChatService.registerHandler(mHandler);
        //mChatService.start();
        
        // Initialize the button to perform device discovery
        Button scanButton = (Button) findViewById(R.id.button_discover);
        scanButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                doDiscovery();
                v.setVisibility(View.INVISIBLE);
            }
        });

        // Initialize array adapters. One for newly discovered devices
        mNewDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);

        // Find and set up the ListView for newly discovered devices
        ListView newDevicesListView = (ListView) findViewById(R.id.listView_newDevices);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);
        
        for (BluetoothDevice device: mBtAdapter.getBondedDevices()) 
        	mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        // If the adapter is null, then Bluetooth is not supported
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        if (!mBtAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
        	enableDiscovery();
        }
	}

	@Override
    protected void onDestroy() {
        super.onDestroy();

        // Make sure we're not doing discovery anymore
        if (mBtAdapter != null) {
            mBtAdapter.cancelDiscovery();
        }

        // Unregister broadcast listeners
        this.unregisterReceiver(mReceiver);
    }
	
	/**
     * Start device discover with the BluetoothAdapter
     */
    private void doDiscovery() {
        if (D) Log.d(TAG, "doDiscovery()");

        // Indicate scanning in the title
        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.scanning);

        // If we're already discovering, stop it
        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }

        // Request discover from BluetoothAdapter
        mBtAdapter.startDiscovery();
    }
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_DISCOVERABLE:
//            if (resultCode == Activity.RESULT_OK) {
//            	Intent listIntent = new Intent(this, DeviceListActivity.class);
//                startActivityForResult(listIntent, REQUEST_CONNECT_DEVICE_SECURE);
//            } else {
//            	Log.d(TAG, "Discovery not enabled");
//                Toast.makeText(this, R.string.discovery_not_enabled_leaving, Toast.LENGTH_SHORT).show();
//                finish();
//            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so discover devices
            	enableDiscovery();
            } else {
                // User did not enable Bluetooth or an error occured
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
		
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	// The on-click listener for all devices in the ListViews
    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // Cancel discovery because it's costly and we're about to connect
            mBtAdapter.cancelDiscovery();

            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);
            
            connectDevice(address, true);
        }
    };
	
	private void enableDiscovery() {
		Intent discoverableIntent = new
				Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERY_DURATION);
		startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE);
	}
	
	private void connectDevice(String address, boolean secure) {
        // Go to the connection activity
        Intent connectIntent = new Intent(this, DownloadUploadActivity.class);
        connectIntent.putExtra(DownloadUploadActivity.ADDRESS, address);
        startActivity(connectIntent);
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.login, menu);
		return true;
	}
	
	// The BroadcastReceiver that listens for discovered devices and
    // changes the title when discovery is finished
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed already
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                setTitle(R.string.select_device);
                if (mNewDevicesArrayAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.none_found).toString();
                    mNewDevicesArrayAdapter.add(noDevices);
                }
            }
        }
    };

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
		@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothCommService.STATE_CONNECTED:
                	setTitle(getString(R.string.title_connected_to, mConnectedDeviceName));
                	connectDevice(mConnectedDeviceName, true);
                    break;
                case BluetoothCommService.STATE_CONNECTING:
                    setTitle(R.string.title_connecting);
                    break;
                case BluetoothCommService.STATE_LISTEN:
                case BluetoothCommService.STATE_NONE:
                	setTitle(R.string.title_not_connected);
                    break;
                }
                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage;
                readMessage = new String(readBuf, 0, msg.arg1);
                String[] tokens = readMessage.split("<>");
        		String[] files = tokens[1].split(":::");
        		mChatService.setPeerFiles(files);
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                connectDevice(mConnectedDeviceName, true);
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };
}
