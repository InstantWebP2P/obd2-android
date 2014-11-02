package com.example.icar;

import java.util.Hashtable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * Copyright (c) 2014 Tom Zhou
 * @author tomzhou
 * 
 */
public class EventHandler implements EventEmitter {
	private final static String TAG = "EventHandler";
    private msgHdl hdl;
    private Map<String, List<Listener>> events;
	
    // @description message handler
    private class msgHdl extends Handler {
    	
    	/* (non-Javadoc)
    	 * @see android.os.Handler#handleMessage(android.os.Message)
    	 */
    	public void handleMessage (Message msg) {
    		Bundle bdl = msg.getData();
    		String event = bdl.getString("event");
    		String error = bdl.getString("error");
    		Object data = msg.obj;

    		if (events.containsKey(event)) {
    			for (Listener cb : events.get(event))
    				cb.cb(error, data);
    		} else {
    			Log.d(TAG, "unknown event "+event);
    		}
    	}
    	
    }
    
    EventHandler() {
    	this.hdl = new msgHdl();
    	this.events = new Hashtable<String, List<Listener>>();
    }
    
	@Override
	public boolean emit(final String event, final String error, final Object data) {
		Message msg = hdl.obtainMessage();
		Bundle bdl = new Bundle();
		
		// event string
		bdl.putString("event", event);
	    // error string
		if ((error != null) && (error != ""))
			bdl.putString("error", error);
		else
			bdl.putString("error", null);

		msg.setData(bdl);
		
		// data object
		if (data != null) 
			msg.obj = data;
		else 
			msg.obj = null;
		
		return hdl.sendMessage(msg);
	}

	@Override
	public boolean on(final String event, final Listener cb) {
		return addListener(event, cb);
	}

	@Override
	public boolean once(final String event, final Listener ocb) {
		return addListener(event, new Listener(){

			@Override
			public void cb(final String error, final Object data) {
				// TODO Auto-generated method stub
				ocb.cb(error, data);

				// remove listener
				removeListener(event, this);
			}

		});
	}

	@Override
	public boolean addListener(final String event, final Listener cb) {
		if (!events.containsKey(event)) {
			events.put(event, new ArrayList<Listener>());
		}
		return events.get(event).add(cb);
	}

	@Override
	public boolean removeListener(final String event, final Listener cb) {
		if (!events.containsKey(event)) {
			return true;
		} else {
			return events.get(event).remove(cb);
		}
	}

	@Override
	public boolean clearListener(final String event) {
		if (events.containsKey(event)) {
			events.get(event).clear();
		}
		
		return true;
	}

	@Override
	public boolean clear() {
		// TODO Auto-generated method stub
		events.clear();
		return true;
	}

}
