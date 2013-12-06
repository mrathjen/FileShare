package com.example.fileshare;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/*
 * 		Peer1 						Peer2
 * 		Send Library ------------->	
 * 			<----------------Send Library
 * 		Download Request --------->
 * 			<----------------<Name:::Size>
 * 			<----------------------Data
 * 			....	
 * 			<----------------------Data
 * 		
 */

public class DownloadUploadActivity extends Activity {
	private String FILE_PREFIX = "/storage/emulated/0/Music/";
	private static final String[] FILE_PREFIXES = { "/storage/emulated/0/Music/", "/mnt/sdcard/Music/" }; 
	
	private static final boolean D 			= true;
	private static final String TAG 		= "DownloadUploadActivity";
	private static final String DEVICE_NAME = "MarksApp";
	protected static final String TOAST 	= "Toast";
	
	// Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE 	= 1;
    public static final int MESSAGE_READ 			= 2;
    public static final int MESSAGE_WRITE 			= 3;
    public static final int MESSAGE_DEVICE_NAME 	= 4;
    public static final int MESSAGE_TOAST 			= 5;
	
    private int mState = WAITING_FOR_LIBRARY;
    // States for the progression of peer connection
    public static final int WAITING_FOR_LIBRARY 	= 0;
    public static final int WAITING_FOR_ACTION 		= 1;
    public static final int DOWNLOADING				= 2;
    public static final int UPLOADING				= 3;
    
    private int mDownloadState = DOWNLOADING_FILE_INFO;
    // States for downloading
    public static final int DOWNLOADING_FILE_INFO	= 0;
    public static final int DOWNLOADING_FILE_CONTENT= 1;
    
	public static final String ADDRESS = "ADDRESS";
	
	// Directory of library files
	//private static final String FILE_PREFIX = "/data/data/com.example.fileshare/files/";

	private BluetoothCommService mChatService;
	
	private String mConnectedDeviceName = "Default";
	
	private BluetoothAdapter mBtAdapter;
	
	private ArrayAdapter<String> clientFiles;
	private ArrayAdapter<String> peerFiles;
	
	private StringBuilder mStringLibrary;
	
	private P2PFile mFile;
	
	private ProgressBar mUploadBar;
	private ProgressBar mDownloadBar;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_download_upload);
		
		Intent intent = getIntent();
		
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();
		
		clientFiles = new ArrayAdapter<String>(this, R.layout.device_name);
		peerFiles = new ArrayAdapter<String>(this, R.layout.device_name);
		
		mStringLibrary = new StringBuilder();
		
		// Find and set up the ListView for newly discovered devices
        ListView clientLibrary = (ListView) findViewById(R.id.listView_clientFiles);
        clientLibrary.setAdapter(clientFiles);
        fillClientLibrary();
        
        ListView peerLibrary = (ListView) findViewById(R.id.listView_peerFiles);
        peerLibrary.setAdapter(peerFiles);
        peerLibrary.setOnItemClickListener(mDeviceClickListener);
		
		// Initialize the BluetoothChatService to perform bluetooth connections
		mChatService = BluetoothCommService.createService(this, mHandler);
		mChatService.registerHandler(mHandler);
		
		// Get the BLuetoothDevice object
        BluetoothDevice device = mBtAdapter.getRemoteDevice(intent.getStringExtra(ADDRESS));
        
        // Initialize the button to perform device disconnect
        Button disconnectButton = (Button) findViewById(R.id.button_disconnect);
        disconnectButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mChatService.stop();
                finish();
            }
        });
        
        mUploadBar = (ProgressBar) findViewById(R.id.progressBar_upload);
        mDownloadBar = (ProgressBar) findViewById(R.id.progressBar_download);
        mUploadBar.setMax(100);
        mDownloadBar.setMax(100);
		
        if (mChatService.getState() != BluetoothCommService.STATE_CONNECTED)
        	mChatService.connect(device, true);
        else {
        	// Send the library information
        	sendLibraryToPeer();
        	if (mChatService.getPeerFiles().size() > 0) {
        		for (String s: mChatService.getPeerFiles())
        			peerFiles.add(s);
        	}
        }
	}

	/**
	 * Clients files are all files in the apps dir.
	 */
	private void fillClientLibrary() {
		File f = new File(FILE_PREFIXES[0]);
		int i = 1;
		while (!f.isDirectory() && i < FILE_PREFIXES.length) {
			f = new File (FILE_PREFIXES[i]);
			FILE_PREFIX = FILE_PREFIXES[i];
			i++;
		}
		if (f.isDirectory()) {
			String[] files = f.list();
			for (i = 0; i < files.length; i++)
				clientFiles.add(files[i]);
		}
	}

	/**
	 * Send peeer a library composed of all files in the apps local dir.
	 */
	private void sendLibraryToPeer() {
		if (mStringLibrary.length() > 0)
			mStringLibrary.delete(0, mStringLibrary.length());
		mStringLibrary.append("<>");
		for (int i = 0; i < clientFiles.getCount(); i++) {
			mStringLibrary.append(clientFiles.getItem(i));
			if (i != clientFiles.getCount() - 1)
				mStringLibrary.append(":::");
		}
		mStringLibrary.append("<>");
		mChatService.write(mStringLibrary.toString().getBytes());
    	Log.d(TAG, "Sent library: " + mStringLibrary.toString());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.download_upload, menu);
		return true;
	}
	
	private void createPeerFile(String readMessage) {
		// <>Name:::Size<>
		String[] tokens = readMessage.split("<>");
		if (tokens.length > 0) {
			String[] attrs = tokens[1].split(":::");
			mFile = new P2PFile(attrs[0], Integer.parseInt(attrs[1]));
			mDownloadState = DOWNLOADING_FILE_CONTENT;
			
			if (tokens.length > 2) {
				mFile.writeToFile(tokens[1].getBytes(), 0, tokens[1].getBytes().length);
			}
		} else {
			mState = WAITING_FOR_ACTION;
		}
	}

	private void fillPeerLibrary(String readMessage) {
		String[] tokens = readMessage.split("<>");
		String[] files = tokens[1].split(":::");
		for (int i = 0; i < files.length; i++)
			peerFiles.add(files[i]);
		mChatService.setPeerFiles(files);
		String[] cFiles = new String[clientFiles.getCount()];
		for (int i = 0; i < cFiles.length; i++)
			cFiles[i] = clientFiles.getItem(i);
		mChatService.setClientFiles(cFiles);
	}
	
	// The on-click listener for all devices in the ListViews
    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
        	String fileName = ((TextView) v).getText().toString();
        	sendDownloadRequest(fileName);
        }
    };
    
    /**
     * Send a request to download peer file with fileName.
     * @param fileName
     */
    private void sendDownloadRequest(String fileName) {
		StringBuilder sb = new StringBuilder();
		sb.append("<>");
		sb.append(fileName);
		sb.append("<>");
		mChatService.write(sb.toString().getBytes());
		mState = DOWNLOADING;
	}

	// The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothCommService.STATE_CONNECTED:
                	Log.d(TAG, "STATE_CONNECTED");
                	setTitle(getString(R.string.title_connected_to, mConnectedDeviceName));
                	sendLibraryToPeer();
                    break;
                case BluetoothCommService.STATE_CONNECTING:
                	Log.d(TAG, "STATE_CONNECTING");
                    setTitle(R.string.title_connecting);
                    break;
                case BluetoothCommService.STATE_LISTEN:
                	Log.d(TAG, "STATE_LISTEN");
                case BluetoothCommService.STATE_NONE:
                	Log.d(TAG, "STATE_NONE");
                	setTitle(R.string.title_not_connected);
                	//finish();
                    break;
                }
                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage;
                //Log.d(TAG, "Recevied: " + msg.arg1 + " bytes");
                if (mState == WAITING_FOR_LIBRARY) {
                	readMessage = new String(readBuf, 0, msg.arg1);
                	mState = WAITING_FOR_ACTION;
                	fillPeerLibrary(readMessage);
                } else if (mState == WAITING_FOR_ACTION) {
                	readMessage = new String(readBuf, 0, msg.arg1);
                	mState = UPLOADING;
                	String[] toks = readMessage.split("<>");
                	P2PFile client = new P2PFile(toks[toks.length-1]);
                	client.transferFile();
                } else if (mState == DOWNLOADING) {
                	if (mDownloadState == DOWNLOADING_FILE_INFO) {
                		readMessage = new String(readBuf, 0, msg.arg1);
                		createPeerFile(readMessage);
                	} else
                		mFile.writeToFile(readBuf, 0, msg.arg1);
                }
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };
	
    public class P2PFile {
    	private String mFileName;
    	private long mFileSize;
    	private int mBytesWritten;
    	private FileOutputStream mFileOutStream;
    	private FileInputStream mFileInStream;
    	
    	private static final int BLOCK_SIZE = 4096;
    	
    	public P2PFile(String fileName) {
    		mFileName = fileName;
    		mBytesWritten = 0;
    		try {
    			File mFile = new File(FILE_PREFIX + mFileName);
    			mFileInStream = new FileInputStream(mFile);
    			mFileSize = mFile.length();
				Log.d(TAG, "Opened file "+ mFileName);
			} catch (FileNotFoundException e) {
				Log.d(TAG, "Failed to open file "+ mFileName);
			}
    		
    	}
    	
    	public P2PFile(String fileName, int fileSize) {
    		mFileName = fileName;
    		mFileSize = fileSize;
    		mBytesWritten = 0;
    		try {
    			File mFile = new File(FILE_PREFIX + mFileName);
    			mFile.createNewFile();
    			mFileOutStream = new FileOutputStream(mFile);
				if (D) Log.d(TAG, "Created file "+ mFileName);
			} catch (FileNotFoundException e) {
				Log.d(TAG, "Failed to create file "+ mFileName);
			} catch (IOException e) {
				Log.d(TAG, "Failed to create file "+ mFileName);
			}
    	}
    	
    	public synchronized void writeToFile(byte[] readBuf, int start, int len) {
    		if (mFileOutStream != null && mState == DOWNLOADING) {
				try {
					mFileOutStream.write(readBuf, 0, len);
					mBytesWritten += len;
					
					if (D) Log.d(TAG, "Wrote " + mBytesWritten + " bytes");
					
					if (mBytesWritten == mFileSize) {
						mState = WAITING_FOR_ACTION;
						mDownloadState = DOWNLOADING_FILE_INFO;
						mFileOutStream.close();
						Toast.makeText(getApplicationContext(), "Finished writing file.",
	                            Toast.LENGTH_LONG).show();
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
    	}
    	
    	public void transferFile() {
    		// Send head with file name and size
    		StringBuilder header = new StringBuilder();
    		header.append("<>"+mFileName+":::"+mFileSize+"<>");
    		mChatService.write(header.toString().getBytes());
    		
    		// Read in the requested file and send to peer
    		while (mBytesWritten != mFileSize) {
    			try {
    				byte[] b = new byte[BLOCK_SIZE];
					int read = mFileInStream.read(b, 0, BLOCK_SIZE);
					mChatService.write(b, 0, read);
					mBytesWritten += read;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		}
    		Toast.makeText(getApplicationContext(), "Finished transferring file.",
                    Toast.LENGTH_LONG).show();
    	}
    }
}
