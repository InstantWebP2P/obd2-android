package com.example.icar;

/**
 * Copyright (c) 2014 Tom Zhou
 * @author tomzhou
 * 
 */
public interface EventEmitter {

	// @description event handle callback
	// @param error : error string if have
	// @param data : event associated data
	public interface Listener {
		public void cb(final String error, final Object data);
	}

	// @description emit event with data or error
	public boolean emit(final String event, final String error, final Object data);

	// @description register event handler
	public boolean on(final String event, final Listener cb);

	// @description register event handler and remove it after responded
	public boolean once(final String event, final Listener cb);

	// @description add event listener
	public boolean addListener(final String event, final Listener cb);

	// @description remove event listener
	public boolean removeListener(final String event, final Listener cb);

	// @description remove all event listener
	public boolean clearListener(final String event);
	
	// @description clear all events listeners
	public boolean clear();

}
