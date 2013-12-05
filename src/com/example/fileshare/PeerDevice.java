package com.example.fileshare;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class PeerDevice {
	private String mDeviceName;
	private List<String> mPeerFiles;
	private List<String> mClientFiles;
	
	public PeerDevice() {
		mDeviceName = "";
		mPeerFiles = new LinkedList<String>();
		mClientFiles = new LinkedList<String>();
	}
	
	public void setDeviceName(String name) {
		mDeviceName = name;
	}
	public void setClientFiles(String[] files) {
		for (String s: files)
			mClientFiles.add(s);
	}
	public void setPeerFiles(String[] files) {
		for (String s: files)
			mPeerFiles.add(s);
	}
	public String getDeviceName() {
		return mDeviceName;
	}
	public List<String> getClientFiles() {
		return Collections.unmodifiableList(mClientFiles);
	}
	public List<String> getPeerFiles() {
		return Collections.unmodifiableList(mPeerFiles);
	}
}
