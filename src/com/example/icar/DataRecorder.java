/**
 * 
 */
package com.example.icar;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import android.util.Log;

import com.couchbase.lite.*;
import com.example.icar.OBDReader.executeQueryCallback;
import com.example.icar.OBDReader.pidDesc;

/**
 * Copyright (c) 2014 Tom Zhou
 * 
 * @author tomzhou
 * 
 */
public class DataRecorder extends Thread {
	private static final String TAG = "DataRecorder";
	private OBDReader reader;
	private Parameters params;
	private Database database;

	// @description recorder parameters
	public static class Parameters {
		Parameters() {
			this.sample_interval_changelog = new ArrayList<interval_changelog_t>();
		}

		// @description one shoot or cycle sample
		private volatile boolean sample_oneshot;

		// @description sample interval ms dynamic
		private volatile int sample_interval;

		// @description sample interval change log
		// @param time - ms
		// @param interval - ms
		private class interval_changelog_t {
			private final long time;
			private final int interval;

			interval_changelog_t(long time, int interval) {
				this.time = time;
				this.interval = interval;
			}

			/**
			 * @return the time
			 */
			public long getTime() {
				return time;
			}

			/**
			 * @return the interval
			 */
			public int getInterval() {
				return interval;
			}
		}

		private volatile List<interval_changelog_t> sample_interval_changelog;

		// @description sample_window: how many iterations -1 infinite
		private volatile int sample_window;

		// @description sample duty cycle: how many sample iterations in
		// sample_window
		// normally for one shoot sample duty cycle set to 1
		private volatile int sample_dutycycle;

		// @description prefix idle cycle before sample duty cycle: how many
		// idle iterations
		// normally set to 0
		private volatile int sample_precycle;

		// @description retrieve PID parameter record list
		private volatile List<data_record_t> records;

		// @description user info
		private volatile String usrinfo;

		// @description security info
		private volatile String secinfo;

		// @description geo location availability
		private volatile boolean geoenabled;
		private volatile geoInfoCallback geoinfo;

		public interface geoInfoCallback {
			// @description location as string "lon:lat:alt"
			public String getSimpleLocation();
		}

		// @description eventEmitter for async operations
		private volatile EventEmitter eventemitter;
		
		/**
		 * @return the usrinfo
		 */
		public String getUsrinfo() {
			return usrinfo;
		}

		/**
		 * @param usrinfo
		 *            the usrinfo to set
		 */
		public void setUsrinfo(String usrinfo) {
			this.usrinfo = usrinfo;
		}

		/**
		 * @return the secinfo
		 */
		public String getSecinfo() {
			return secinfo;
		}

		/**
		 * @param secinfo
		 *            the secinfo to set
		 */
		public void setSecinfo(String secinfo) {
			this.secinfo = secinfo;
		}

		/**
		 * @return the records
		 */
		public List<data_record_t> getRecords() {
			return records;
		}

		/**
		 * @param records
		 *            the records to set
		 */
		public void setRecords(List<data_record_t> records) {
			this.records = records;
		}

		/**
		 * @return the sample_oneshot
		 */
		public boolean isSample_oneshot() {
			return sample_oneshot;
		}

		/**
		 * @param sample_oneshot
		 *            the sample_oneshot to set
		 */
		public void setSample_oneshot(boolean sample_oneshot) {
			this.sample_oneshot = sample_oneshot;
		}

		/**
		 * @return the sample_interval
		 */
		public int getSample_interval() {
			return sample_interval;
		}

		/**
		 * @param sample_interval
		 *            the sample_interval to set
		 */
		public void setSample_interval(int sample_interval) {
			this.sample_interval = sample_interval;

			// record interval change log
			this.sample_interval_changelog.add(new interval_changelog_t(System
					.currentTimeMillis(), sample_interval));
		}

		/**
		 * @return the sample_window
		 */
		public int getSample_window() {
			return sample_window;
		}

		/**
		 * @param sample_window
		 *            the sample_window to set
		 */
		public void setSample_window(int sample_window) {
			this.sample_window = sample_window;
		}

		/**
		 * @return the sample_dutycycle
		 */
		public int getSample_dutycycle() {
			return sample_dutycycle;
		}

		/**
		 * @param sample_dutycycle
		 *            the sample_dutycycle to set
		 */
		public void setSample_dutycycle(int sample_dutycycle) {
			this.sample_dutycycle = sample_dutycycle;
		}

		/**
		 * @return the sample_precycle
		 */
		public int getSample_precycle() {
			return sample_precycle;
		}

		/**
		 * @param sample_precycle
		 *            the sample_precycle to set
		 */
		public void setSample_precycle(int sample_precycle) {
			this.sample_precycle = sample_precycle;
		}

		/**
		 * @return the geoenabled
		 */
		public boolean isGeoenabled() {
			return geoenabled;
		}

		/**
		 * @param geoenabled
		 *            the geoenabled to set
		 */
		public void setGeoenabled(boolean geoenabled) {
			this.geoenabled = geoenabled;
		}

		/**
		 * @return the sample_interval_changelog
		 */
		public List<interval_changelog_t> getSample_interval_changelog() {
			return sample_interval_changelog;
		}

		/**
		 * @return the geoinfo
		 */
		public geoInfoCallback getGeoinfo() {
			return geoinfo;
		}

		/**
		 * @param geoinfo
		 *            the geoinfo to set
		 */
		public void setGeoinfo(geoInfoCallback geoinfo) {
			this.geoinfo = geoinfo;
		}

		/**
		 * @return the eventemitter
		 */
		public EventEmitter getEventemitter() {
			return eventemitter;
		}

		/**
		 * @param eventemitter the eventemitter to set
		 */
		public void setEventemitter(EventEmitter eventemitter) {
			this.eventemitter = eventemitter;
		}
	}

	// @description OBD2 data record as Map for JSON
	public interface data_record_t {
		// classification
		public String getCategory();

		public String getType();

		public List<String> getTags();

		// JSON serialization and parse
		public Map<String, Object> toJsonMap(Map<String, Object> map);

		public Map<String, Object> parseJsonMap(Map<String, Object> map);

		// data details: time,geo-location,user,security,PID parameters
		public List<String> getPids();

		public boolean fillUsrinfo(String usrinfo);

		public boolean fillSecinfo(String secinfo);

		public boolean fillVin(String vin);

		public boolean fillCreatetime(long ts);

		public boolean fillGeoinfo(String geo);

		public boolean fillPidData(List<pidDesc> descs);

	}

	// real time driving data
	// for details refer to Data_Exchange-Storage_Model_Design_Spec.doc
	/*
	 * driving_data: { 
	 * // time/user/vehicle/geo-location info
	 * cts: data created timestamp
	 * uid: user information identifier 
	 * vin: vehicle identifier 
	 * geo: vehicle geo-location, optional
	 * 
	 * // driving data can be single object, or array of objects 
	 * data: { 銆�銆� 
	 *  pid: OBD2 PID to retrieve the data 
	 *  ets: Event happen timestamp means data happened time 
	 *  name: PID related data name, like speed, fuel rate, etc
	 *  type: value type like float, string, error 
	 *  value: PID related data value, like for speed 180 km/h 
	 * }
	 * 
	 * // meta-data 
	 * category: 鈥渞ealtime_driving_data鈥� // data category 
	 * secinfo: security information, optional
	 * }
	 */
	// driving data can be single object, or array of objects
	public static class data_t { 
		private String pid; 
		private Long ets; 
		private String name; 
		private Integer type;  // 0: float, 1: string, 2: error as string 
		private Object value; // number or string 
	}

	public static abstract class realtime_driving_data_t implements
			data_record_t {

		/**
		 * @return the cts
		 */
		public long getCts() {
			return cts;
		}

		/**
		 * @param cts
		 *            the cts to set
		 */
		public void setCts(long cts) {
			this.cts = cts;
		}

		/**
		 * @return the uid
		 */
		public String getUid() {
			return uid;
		}

		/**
		 * @param uid
		 *            the uid to set
		 */
		public void setUid(String uid) {
			this.uid = uid;
		}

		/**
		 * @return the vin
		 */
		public String getVin() {
			return vin;
		}

		/**
		 * @param vin
		 *            the vin to set
		 */
		public void setVin(String vin) {
			this.vin = vin;
		}

		/**
		 * @return the geo
		 */
		public String getGeo() {
			return geo;
		}

		/**
		 * @param geo
		 *            the geo to set
		 */
		public void setGeo(String geo) {
			this.geo = geo;
		}

		/**
		 * @return the secinfo
		 */
		public String getSec() {
			return sec;
		}

		/**
		 * @param secinfo
		 *            the secinfo to set
		 */
		public void setSec(String secinfo) {
			this.sec = secinfo;
		}

		private final static String category = "realtime_driving_data";

		// time/user/vehicle/geo-location info
		private Long cts;
		private String uid;
		private String vin;
		private String geo;

		// driving data as array of objects
		// /private List<data_t> datas;

		// meta-data
		private String sec; // security information, optional

		/**
		 * @return the category
		 */
		public String getCategory() {
			return category;
		}

		@Override
		public boolean fillUsrinfo(String usrinfo) {
			// TODO Auto-generated method stub
			setUid(usrinfo);
			return true;
		}

		@Override
		public boolean fillSecinfo(String secinfo) {
			// TODO Auto-generated method stub
			setSec(secinfo);
			return true;
		}

		@Override
		public boolean fillVin(String vin) {
			// TODO Auto-generated method stub
			setVin(vin);
			return true;
		}

		@Override
		public boolean fillCreatetime(long ts) {
			// TODO Auto-generated method stub
			setCts(ts);
			return true;
		}

		@Override
		public boolean fillGeoinfo(String geo) {
			// TODO Auto-generated method stub
			setGeo(geo);
			return true;
		}

		// convert record to Map
		public Map<String, Object> toJsonMap(Map<String, Object> doc) {
			Map<String, Object> docContent = (doc != null) ? doc
					: new Hashtable<String, Object>();

			// category
			docContent.put("category", getCategory());

			// cts
			docContent.put("cts", getCts());

			// uid
			docContent.put("uid", getUid());

			// vin
			docContent.put("vin", getVin());

			// geo
			docContent.put("geo", getGeo());

			// secinfo
			docContent.put("sec", getSec());

			return docContent;
		}

		// parse Map to record
		@Override
		public Map<String, Object> parseJsonMap(Map<String, Object> map) {
			if (!((map != null) && map.containsKey("category") && (map
					.get("category") == category))) {
				Log.w(TAG, "ignore wrong category: " + map.get("category"));
			}

			// cts
			if ((map != null) && (map.containsKey("cts"))) {
				setCts((Long) map.get("cts"));
			}

			// geo
			if ((map != null) && (map.containsKey("geo"))) {
				setGeo((String) map.get("geo"));
			}

			// sec
			if ((map != null) && (map.containsKey("sec"))) {
				setSec((String) map.get("sec"));
			}

			// uid
			if ((map != null) && (map.containsKey("uid"))) {
				setUid((String) map.get("uid"));
			}

			// vin
			if ((map != null) && (map.containsKey("vin"))) {
				setVin((String) map.get("vin"));
			}

			return map;
		}

	}

	// real time fuel consumption record based on EFR(engine fuel rate)
	public static class efr_realtime_fuel_consumption_record_t extends
			realtime_driving_data_t {
		private final static String type = "efr_realtime_fuel_consumption";

		// driving data as array of objects{ets, pid, value}
		// ets
		private Long vehicle_speed_ets;
		private Long engine_fuel_rate_ets;
		// pid
		private static final String vehicle_speed_pid = "010D";
		private static final String engine_fuel_rate_pid = "015E";
		// value
		private Float vehicle_speed; // Km/h
		private Float engine_fuel_rate; // engine fuel rate L/h

		/**
		 * @return the vehicle_speed_ets
		 */
		public long getVehicle_speed_ets() {
			return vehicle_speed_ets;
		}

		/**
		 * @param vehicle_speed_ets
		 *            the vehicle_speed_ets to set
		 */
		public void setVehicle_speed_ets(long vss_ets) {
			this.vehicle_speed_ets = vss_ets;
		}

		/**
		 * @return the engine_fuel_rate_ets
		 */
		public long getEngine_fuel_rate_ets() {
			return engine_fuel_rate_ets;
		}

		/**
		 * @param engine_fuel_rate_ets
		 *            the engine_fuel_rate_ets to set
		 */
		public void setEngine_fuel_rate_ets(long efr_ets) {
			this.engine_fuel_rate_ets = efr_ets;
		}

		/**
		 * @return the vehicle_speed
		 */
		public float getVehicle_speed() {
			return vehicle_speed;
		}

		/**
		 * @param vehicle_speed
		 *            the vehicle_speed to set
		 */
		public void setVehicle_speed(float vss) {
			this.vehicle_speed = vss;
		}

		/**
		 * @return the engine_fuel_rate
		 */
		public float getEngine_fuel_rate() {
			return engine_fuel_rate;
		}

		/**
		 * @param engine_fuel_rate
		 *            the engine_fuel_rate to set
		 */
		public void setEngine_fuel_rate(float efr) {
			this.engine_fuel_rate = efr;
		}

		@Override
		public List<String> getPids() {
			List<String> pids = new ArrayList<String>();

			pids.add(getVehicleSpeedPid());
			pids.add(getEngineFuelRatePid());

			return pids;
		}

		@Override
		public List<String> getTags() {
			// TODO Auto-generated method stub
			List<String> tags = new ArrayList<String>();
			tags.add("vehicle_speed");
			tags.add("engine_fuel_rate");
			tags.add("fuelcosumption");

			return tags;
		}

		@Override
		public boolean fillPidData(List<pidDesc> descs) {
			pidDesc desc = null;
			String pid = null;

			for (int i = 0; i < descs.size(); i++) {
				desc = descs.get(i);

				if (desc != null) {
					pid = String.format("%02X%02X", desc.getMode(),
							desc.getPid());

					// data - vehicle_speed
					if (pid == getVehicleSpeedPid()) {
						setVehicle_speed(desc.getResult_item_value_number()[0]);
						setVehicle_speed_ets(desc.getRts());
					} else if (pid == getEngineFuelRatePid()) {
						// data - engine_fuel_rate
						setEngine_fuel_rate(desc.getResult_item_value_number()[0]);
						setEngine_fuel_rate_ets(desc.getRts());
					} else {
						Log.w(TAG, "unknown PID desc: " + desc);
					}
				}
			}

			return true;
		}

		@Override
		public String getType() {
			// TODO Auto-generated method stub
			return type;
		}

		// convert record to Map
		public Map<String, Object> toJsonMap(Map<String, Object> doc) {
			Map<String, Object> docContent = (doc != null) ? doc
					: new Hashtable<String, Object>();

			super.toJsonMap(docContent);

			// type
			docContent.put("type", getType());

			// tags
			docContent.put("tags", getTags());

			// data - vehicle_speed
			docContent.put("vehicle_speed", getVehicle_speed());
			docContent.put("vehicle_speed_pid", getVehicleSpeedPid());
			docContent.put("vehicle_speed_ets", getVehicle_speed_ets());

			// data - engine_fuel_rate
			docContent.put("engine_fuel_rate", getEngine_fuel_rate());
			docContent.put("engine_fuel_rate_pid", getEngineFuelRatePid());
			docContent.put("engine_fuel_rate_ets", getEngine_fuel_rate_ets());

			return docContent;
		}

		// parse Map to record
		@Override
		public Map<String, Object> parseJsonMap(Map<String, Object> map) {
			super.parseJsonMap(map);

			// type
			if (!((map != null) && map.containsKey("type") && (map.get("type") == type))) {
				Log.w(TAG, "ignore wrong type: " + map.get("type"));
			}

			// tags
			
			// data - vehicle_speed
			if ((map != null) && (map.containsKey("vehicle_speed"))) {
				setVehicle_speed((Float) map.get("vehicle_speed"));
			}
			if ((map != null) && (map.containsKey("vehicle_speed_ets"))) {
				setVehicle_speed_ets((Long) map.get("vehicle_speed_ets"));
			}

			// data - engine_fuel_rate
			if ((map != null) && (map.containsKey("engine_fuel_rate"))) {
				setEngine_fuel_rate((Float) map.get("engine_fuel_rate"));
			}
			if ((map != null) && (map.containsKey("engine_fuel_rate_ets"))) {
				setEngine_fuel_rate_ets((Long) map.get("engine_fuel_rate_ets"));
			}

			return map;
			// TODO Auto-generated method stub
		}

		/**
		 * @return the vehicleSpeedPid
		 */
		public static String getVehicleSpeedPid() {
			return vehicle_speed_pid;
		}

		/**
		 * @return the engineFuelRatePid
		 */
		public static String getEngineFuelRatePid() {
			return engine_fuel_rate_pid;
		}

	}

	// real time fuel consumption record based on MAF(massa air flow)
	public static class maf_realtime_fuel_consumption_record_t extends
			realtime_driving_data_t {
		private final static String type = "maf_realtime_fuel_consumption";

		// driving data as array of objects{ets, pid, value}
		// ets
		private Long vehicle_speed_ets;
		private Long maf_ets;
		private Long fuel_type_ets;
		// pid
		private static final String vehicle_speed_pid = "010D";
		private static final String maf_pid = "0110";
		private static final String fuel_type_pid = "0151";
		// value
		private Float vehicle_speed; // Km/h
		private Float maf; // grams/g
		private Integer fuel_type; // fuel type

		/**
		 * @return the vehicle_speed_ets
		 */
		public long getVehicle_speed_ets() {
			return vehicle_speed_ets;
		}

		/**
		 * @param vehicle_speed_ets
		 *            the vehicle_speed_ets to set
		 */
		public void setVehicle_speed_ets(long vss_ets) {
			this.vehicle_speed_ets = vss_ets;
		}

		/**
		 * @return the maf_ets
		 */
		public long getMaf_ets() {
			return maf_ets;
		}

		/**
		 * @param maf_ets
		 *            the maf_ets to set
		 */
		public void setMaf_ets(long maf_ets) {
			this.maf_ets = maf_ets;
		}

		/**
		 * @return the vehicle_speed
		 */
		public float getVehicle_speed() {
			return vehicle_speed;
		}

		/**
		 * @param vehicle_speed
		 *            the vehicle_speed to set
		 */
		public void setVehicle_speed(float vss) {
			this.vehicle_speed = vss;
		}

		/**
		 * @return the maf
		 */
		public float getMaf() {
			return maf;
		}

		/**
		 * @param maf
		 *            the maf to set
		 */
		public void setMaf(float maf) {
			this.maf = maf;
		}

		@Override
		public List<String> getPids() {
			List<String> pids = new ArrayList<String>();

			pids.add(getVehicleSpeedPid());
			pids.add(getMafPid());
			pids.add(getFuelTypePid());

			return pids;
		}

		@Override
		public List<String> getTags() {
			// TODO Auto-generated method stub
			List<String> tags = new ArrayList<String>();
			tags.add("vehicle_speed");
			tags.add("maf");
			tags.add("fuel_type");
			tags.add("fuelcosumption");

			return tags;
		}

		@Override
		public boolean fillPidData(List<pidDesc> descs) {
			pidDesc desc = null;
			String pid = null;

			for (int i = 0; i < descs.size(); i++) {
				desc = descs.get(i);

				if (desc != null) {
					pid = String.format("%02X%02X", desc.getMode(),
							desc.getPid());

					// data - vehicle_speed
					if (pid == getVehicleSpeedPid()) {
						setVehicle_speed(desc.getResult_item_value_number()[0]);
						setVehicle_speed_ets(desc.getRts());
					} else if (pid == getMafPid()) {
						// data - maf
						setMaf(desc.getResult_item_value_number()[0]);
						setMaf_ets(desc.getRts());
					} else if (pid == getFuelTypePid()) {
						// data - fuel type
						setFuel_type(desc.getResult_item_value_number()[0]
								.intValue());
						setFuel_type_ets(desc.getRts());
					} else {
						Log.w(TAG, "unknown PID desc: " + desc);
					}
				}
			}

			return true;
		}

		@Override
		public String getType() {
			// TODO Auto-generated method stub
			return type;
		}

		// convert record to Map
		public Map<String, Object> toJsonMap(Map<String, Object> doc) {
			Map<String, Object> docContent = (doc != null) ? doc
					: new Hashtable<String, Object>();

			super.toJsonMap(docContent);

			// type
			docContent.put("type", getType());

			// tags
			docContent.put("tags", getTags());

			// data - vehicle_speed
			docContent.put("vehicle_speed", getVehicle_speed());
			docContent.put("vehicle_speed_pid", getVehicleSpeedPid());
			docContent.put("vehicle_speed_ets", getVehicle_speed_ets());

			// data - maf
			docContent.put("maf", getMaf());
			docContent.put("maf_pid", getMafPid());
			docContent.put("maf_ets", getMaf_ets());

			// data - fuel type
			docContent.put("fuel_type", getFuel_type());
			docContent.put("fuel_type_pid", getFuelTypePid());
			docContent.put("fuel_type_ets", getFuel_type_ets());

			return docContent;
		}

		// parse Map to record
		@Override
		public Map<String, Object> parseJsonMap(Map<String, Object> map) {
			super.parseJsonMap(map);

			// type
			if (!((map != null) && map.containsKey("type") && (map.get("type") == type))) {
				Log.w(TAG, "ignore wrong type: " + map.get("type"));
			}

			// tags
			
			// data - vehicle_speed
			if ((map != null) && (map.containsKey("vehicle_speed"))) {
				setVehicle_speed((Float) map.get("vehicle_speed"));
			}
			if ((map != null) && (map.containsKey("vehicle_speed_ets"))) {
				setVehicle_speed_ets((Long) map.get("vehicle_speed_ets"));
			}

			// data - maf
			if ((map != null) && (map.containsKey("maf"))) {
				setMaf((Float) map.get("maf"));
			}
			if ((map != null) && (map.containsKey("maf_ets"))) {
				setMaf_ets((Long) map.get("maf_ets"));
			}

			// data - fuel type
			if ((map != null) && (map.containsKey("fuel_type"))) {
				setFuel_type((Integer) map.get("fuel_type"));
			}
			if ((map != null) && (map.containsKey("fuel_type_ets"))) {
				setFuel_type_ets((Long) map.get("fuel_type_ets"));
			}

			return map;
			// TODO Auto-generated method stub
		}

		/**
		 * @return the fuel_type_ets
		 */
		public Long getFuel_type_ets() {
			return fuel_type_ets;
		}

		/**
		 * @param fuel_type_ets
		 *            the fuel_type_ets to set
		 */
		public void setFuel_type_ets(Long fyp_ets) {
			this.fuel_type_ets = fyp_ets;
		}

		/**
		 * @return the fuel_type
		 */
		public Integer getFuel_type() {
			return fuel_type;
		}

		/**
		 * @param fuel_type
		 *            the fuel_type to set
		 */
		public void setFuel_type(Integer ftp) {
			this.fuel_type = ftp;
		}

		/**
		 * @return the vehicleSpeedPid
		 */
		public static String getVehicleSpeedPid() {
			return vehicle_speed_pid;
		}

		/**
		 * @return the mafPid
		 */
		public static String getMafPid() {
			return maf_pid;
		}

		/**
		 * @return the fuelTypePid
		 */
		public static String getFuelTypePid() {
			return fuel_type_pid;
		}

	}

	// average fuel consumption record
	public static class average_fuel_consumption_record_t extends
			realtime_driving_data_t {
		private final static String type = "average_fuel_consumption";

		// driving data as array of objects{ets, pid, value}
		// ets
		private Long fuel_input_level_ets;
		private Long distance_traveled_with_malfunction_indicator_lamp_on_ets;
		private Long distance_traveled_since_codes_cleared_ets;
		// pid
		private static final String fuel_input_level_pid = "012F";
		private static final String distance_traveled_with_malfunction_indicator_lamp_on_pid = "0121";
		private static final String distance_traveled_since_codes_cleared_pid = "0131";
		// value
		private Float fuel_input_level; // fuel input level %
		private Float distance_traveled_with_malfunction_indicator_lamp_on; // Km
		private Float distance_traveled_since_codes_cleared; // Km

		@Override
		public String getType() {
			// TODO Auto-generated method stub
			return type;
		}

		@Override
		public List<String> getTags() {
			// TODO Auto-generated method stub
			List<String> tags = new ArrayList<String>();
			tags.add("fuel_input_level");
			tags.add("distance_traveled_with_malfunction_indicator_lamp_on");
			tags.add("distance_traveled_since_codes_cleared");
			tags.add("average_fuel_consumption");

			return tags;
		}

		@Override
		public List<String> getPids() {
			List<String> pids = new ArrayList<String>();

			pids.add(getDistanceTraveledSinceCodesClearedPid());
			pids.add(getDistanceTraveledWithMalfunctionIndicatorLampOnPid());
			pids.add(getFuelInputLevelPid());

			return pids;
		}

		@Override
		public boolean fillPidData(List<pidDesc> descs) {
			pidDesc desc = null;
			String pid = null;

			for (int i = 0; i < descs.size(); i++) {
				desc = descs.get(i);

				if (desc != null) {
					pid = String.format("%02X%02X", desc.getMode(),
							desc.getPid());

					// data - fuel input level
					if (pid == getFuelInputLevelPid()) {
						setFuel_input_level(desc.getResult_item_value_number()[0]);
						setFuel_input_level_ets(desc.getRts());
					} else if (pid == getDistanceTraveledSinceCodesClearedPid()) {
						// data - distance_traveled_since_codes_cleared
						setDistance_traveled_since_codes_cleared(desc
								.getResult_item_value_number()[0]);
						setDistance_traveled_since_codes_cleared_ets(desc
								.getRts());
					} else if (pid == getDistanceTraveledWithMalfunctionIndicatorLampOnPid()) {
						// data -
						// distance_traveled_with_malfunction_indicator_lamp_on
						setDistance_traveled_with_malfunction_indicator_lamp_on(desc
								.getResult_item_value_number()[0]);
						setDistance_traveled_with_malfunction_indicator_lamp_on_ets(desc
								.getRts());
					} else {
						Log.w(TAG, "unknown PID desc: " + desc);
					}
				}
			}

			return true;
		}

		// convert record to Map
		public Map<String, Object> toJsonMap(Map<String, Object> doc) {
			Map<String, Object> docContent = (doc != null) ? doc
					: new Hashtable<String, Object>();

			super.toJsonMap(docContent);

			// type
			docContent.put("type", getType());

			// tags
			docContent.put("tags", getTags());

			// data - distance_traveled_since_codes_cleared
			docContent.put("distance_traveled_since_codes_cleared",
					getDistance_traveled_since_codes_cleared());
			docContent.put("distance_traveled_since_codes_cleared_pid",
					getDistanceTraveledSinceCodesClearedPid());
			docContent.put("distance_traveled_since_codes_cleared_ets",
					getDistance_traveled_since_codes_cleared_ets());

			// data - distance_traveled_with_malfunction_indicator_lamp_on
			docContent.put(
					"distance_traveled_with_malfunction_indicator_lamp_on",
					getDistance_traveled_with_malfunction_indicator_lamp_on());
			docContent.put(
					"distance_traveled_with_malfunction_indicator_lamp_on_pid",
					getDistanceTraveledWithMalfunctionIndicatorLampOnPid());
			docContent
					.put("distance_traveled_with_malfunction_indicator_lamp_on_ets",
							getDistance_traveled_with_malfunction_indicator_lamp_on_ets());

			// data - fuel_input_level
			docContent.put("fuel_input_level", getFuel_input_level());
			docContent.put("fuel_input_level_pid", getFuelInputLevelPid());
			docContent.put("fuel_input_level_ets", getFuel_input_level_ets());

			return docContent;
		}

		// parse Map to record
		@Override
		public Map<String, Object> parseJsonMap(Map<String, Object> map) {
			super.parseJsonMap(map);

			// type
			if (!((map != null) && map.containsKey("type") && (map.get("type") == type))) {
				Log.w(TAG, "ignore wrong type: " + map.get("type"));
			}

			// tags

			// data - distance_traveled_since_codes_cleared
			if ((map != null)
					&& (map.containsKey("distance_traveled_since_codes_cleared"))) {
				setDistance_traveled_since_codes_cleared((Float) map
						.get("distance_traveled_since_codes_cleared"));
			}
			if ((map != null)
					&& (map.containsKey("distance_traveled_since_codes_cleared_ets"))) {
				setDistance_traveled_since_codes_cleared_ets((Long) map
						.get("distance_traveled_since_codes_cleared_ets"));
			}

			// data - distance_traveled_with_malfunction_indicator_lamp_on
			if ((map != null)
					&& (map.containsKey("distance_traveled_with_malfunction_indicator_lamp_on"))) {
				setDistance_traveled_with_malfunction_indicator_lamp_on((Float) map
						.get("distance_traveled_with_malfunction_indicator_lamp_on"));
			}
			if ((map != null)
					&& (map.containsKey("distance_traveled_with_malfunction_indicator_lamp_on_ets"))) {
				setDistance_traveled_with_malfunction_indicator_lamp_on_ets((Long) map
						.get("distance_traveled_with_malfunction_indicator_lamp_on_ets"));
			}

			// data - fuel_input_level
			if ((map != null) && (map.containsKey("fuel_input_level"))) {
				setFuel_input_level((Float) map.get("fuel_input_level"));
			}
			if ((map != null) && (map.containsKey("fuel_input_level_ets"))) {
				setFuel_input_level_ets((Long) map.get("fuel_input_level_ets"));
			}

			return map;
			// TODO Auto-generated method stub
		}

		/**
		 * @return the fuel_input_level_ets
		 */
		public Long getFuel_input_level_ets() {
			return fuel_input_level_ets;
		}

		/**
		 * @param fuel_input_level_ets
		 *            the fuel_input_level_ets to set
		 */
		public void setFuel_input_level_ets(Long fuel_input_level_ets) {
			this.fuel_input_level_ets = fuel_input_level_ets;
		}

		/**
		 * @return the distance_traveled_with_malfunction_indicator_lamp_on_ets
		 */
		public Long getDistance_traveled_with_malfunction_indicator_lamp_on_ets() {
			return distance_traveled_with_malfunction_indicator_lamp_on_ets;
		}

		/**
		 * @param distance_traveled_with_malfunction_indicator_lamp_on_ets
		 *            the
		 *            distance_traveled_with_malfunction_indicator_lamp_on_ets
		 *            to set
		 */
		public void setDistance_traveled_with_malfunction_indicator_lamp_on_ets(
				Long distance_traveled_with_malfunction_indicator_lamp_on_ets) {
			this.distance_traveled_with_malfunction_indicator_lamp_on_ets = distance_traveled_with_malfunction_indicator_lamp_on_ets;
		}

		/**
		 * @return the distance_traveled_since_codes_cleared_ets
		 */
		public Long getDistance_traveled_since_codes_cleared_ets() {
			return distance_traveled_since_codes_cleared_ets;
		}

		/**
		 * @param distance_traveled_since_codes_cleared_ets
		 *            the distance_traveled_since_codes_cleared_ets to set
		 */
		public void setDistance_traveled_since_codes_cleared_ets(
				Long distance_traveled_since_codes_cleared_ets) {
			this.distance_traveled_since_codes_cleared_ets = distance_traveled_since_codes_cleared_ets;
		}

		/**
		 * @return the fuel_input_level
		 */
		public Float getFuel_input_level() {
			return fuel_input_level;
		}

		/**
		 * @param fuel_input_level
		 *            the fuel_input_level to set
		 */
		public void setFuel_input_level(Float fuel_input_level) {
			this.fuel_input_level = fuel_input_level;
		}

		/**
		 * @return the distance_traveled_with_malfunction_indicator_lamp_on
		 */
		public Float getDistance_traveled_with_malfunction_indicator_lamp_on() {
			return distance_traveled_with_malfunction_indicator_lamp_on;
		}

		/**
		 * @param distance_traveled_with_malfunction_indicator_lamp_on
		 *            the distance_traveled_with_malfunction_indicator_lamp_on
		 *            to set
		 */
		public void setDistance_traveled_with_malfunction_indicator_lamp_on(
				Float distance_traveled_with_malfunction_indicator_lamp_on) {
			this.distance_traveled_with_malfunction_indicator_lamp_on = distance_traveled_with_malfunction_indicator_lamp_on;
		}

		/**
		 * @return the distance_traveled_since_codes_cleared
		 */
		public Float getDistance_traveled_since_codes_cleared() {
			return distance_traveled_since_codes_cleared;
		}

		/**
		 * @param distance_traveled_since_codes_cleared
		 *            the distance_traveled_since_codes_cleared to set
		 */
		public void setDistance_traveled_since_codes_cleared(
				Float distance_traveled_since_codes_cleared) {
			this.distance_traveled_since_codes_cleared = distance_traveled_since_codes_cleared;
		}

		/**
		 * @return the fuelInputLevelPid
		 */
		public static String getFuelInputLevelPid() {
			return fuel_input_level_pid;
		}

		/**
		 * @return the distanceTraveledWithMalfunctionIndicatorLampOnPid
		 */
		public static String getDistanceTraveledWithMalfunctionIndicatorLampOnPid() {
			return distance_traveled_with_malfunction_indicator_lamp_on_pid;
		}

		/**
		 * @return the distanceTraveledSinceCodesClearedPid
		 */
		public static String getDistanceTraveledSinceCodesClearedPid() {
			return distance_traveled_since_codes_cleared_pid;
		}

	}

	// freeze driving data
	public static abstract class freeze_driving_data_t implements data_record_t {

		private final static String category = "freeze_driving_data";

		/**
		 * @return the category
		 */
		public String getCategory() {
			return category;
		}

		// convert record to Map
		public Map<String, Object> toJsonMap(Map<String, Object> map) {
			Map<String, Object> docContent = (map != null) ? map
					: new Hashtable<String, Object>();

			// category
			docContent.put("category", getCategory());

			return docContent;
		}

		// parse Map to record
		@Override
		public Map<String, Object> parseJsonMap(Map<String, Object> map) {
			if (!((map != null) && map.containsKey("category") && (map
					.get("category") == category))) {
				Log.w(TAG, "ignore wrong category: " + map.get("category"));
			}

			return map;
		}

	}

	// DTC clear history
	public static class dtc_clear_history_t implements data_record_t {

		private final static String category = "dtc_clear_history";
		private final static String type = "dtc_clear_history";

		// time/user/vehicle/geo-location info
		private Long cts;
		private String uid;
		private String vin;
		private String geo;

		// driving data as array of objects
		// /private List<data_t> datas;

		// meta-data
		private String sec; // security information, optional

		// DTC log as array of string {ets, pid, value}
		// ets
		private Long dtc_clear_ets;
		// pid
		private static final String dtc_clear_pid = "0400"; // last 00 is padding
		// value
		private Boolean dtc_clear; // true: clear successfully, false: fail to clear

		// convert record to Map
		public Map<String, Object> toJsonMap(Map<String, Object> doc) {
			Map<String, Object> docContent = (doc != null) ? doc
					: new Hashtable<String, Object>();

			// category
			docContent.put("category", getCategory());

			// type
			docContent.put("type", getType());

			// tags
			docContent.put("tags", getTags());

			// cts
			docContent.put("cts", getCts());

			// uid
			docContent.put("uid", getUid());

			// vin
			docContent.put("vin", getVin());

			// geo
			docContent.put("geo", getGeo());

			// secinfo
			docContent.put("sec", getSec());
			
			// data - DTC clear 
			docContent.put("dtc_clear", isDtc_clear());
			docContent.put("dtc_clear_pid", getDtcClearPid());
			docContent.put("dtc_clear_ets",getDtc_clear_ets());
						
			return docContent;
		}

		// parse Map to record
		@Override
		public Map<String, Object> parseJsonMap(Map<String, Object> map) {
			if (!((map != null) && map.containsKey("category") && (map
					.get("category") == category))) {
				Log.w(TAG, "ignore wrong category: " + map.get("category"));
			}

			// cts
			if ((map != null) && (map.containsKey("cts"))) {
				setCts((Long) map.get("cts"));
			}

			// geo
			if ((map != null) && (map.containsKey("geo"))) {
				setGeo((String) map.get("geo"));
			}

			// sec
			if ((map != null) && (map.containsKey("sec"))) {
				setSec((String) map.get("sec"));
			}

			// uid
			if ((map != null) && (map.containsKey("uid"))) {
				setUid((String) map.get("uid"));
			}

			// vin
			if ((map != null) && (map.containsKey("vin"))) {
				setVin((String) map.get("vin"));
			}
			
			// type
			if (!((map != null) && map.containsKey("type") && (map.get("type") == type))) {
				Log.w(TAG, "ignore wrong type: " + map.get("type"));
			}

			// tags
			
			// data - DTC clear
			if ((map != null) && (map.containsKey("dtc_clear"))) {
				setDtc_clear((Boolean) map.get("dtc_clear"));
			}
			if ((map != null) && (map.containsKey("dtc_clear_ets"))) {
				setDtc_clear_ets((Long) map.get("dtc_clear_ets"));
			}
						
			return map;
		}

		/**
		 * @return the category
		 */
		public String getCategory() {
			return category;
		}

		@Override
		public List<String> getTags() {
			// TODO Auto-generated method stub
			List<String> tags = new ArrayList<String>();
			tags.add("dtc_log");

			return tags;
		}

		@Override
		public boolean fillPidData(List<pidDesc> descs) {
			pidDesc desc = null;
			String pid = null;

			for (int i = 0; i < descs.size(); i++) {
				desc = descs.get(i);

				if (desc != null) {
					pid = String.format("%02X%02X", desc.getMode(),
							desc.getPid());

					// data - DTC clear
					if (pid == getDtcClearPid()) {
						setDtc_clear(desc.getResult_item_value_number()[0] == 0);
						setDtc_clear_ets(desc.getRts());
					} else {
						Log.w(TAG, "unknown PID desc: " + desc);
					}
				}
			}

			return true;
		}

		@Override
		public String getType() {
			// TODO Auto-generated method stub
			return type;
		}

		@Override
		public boolean fillUsrinfo(String usrinfo) {
			// TODO Auto-generated method stub
			setUid(usrinfo);
			return true;
		}

		@Override
		public boolean fillSecinfo(String secinfo) {
			// TODO Auto-generated method stub
			setSec(secinfo);
			return true;
		}

		@Override
		public boolean fillVin(String vin) {
			// TODO Auto-generated method stub
			setVin(vin);
			return true;
		}

		@Override
		public boolean fillCreatetime(long ts) {
			// TODO Auto-generated method stub
			setCts(ts);
			return true;
		}

		@Override
		public boolean fillGeoinfo(String geo) {
			// TODO Auto-generated method stub
			setGeo(geo);
			return true;
		}

		/**
		 * @return the cts
		 */
		public long getCts() {
			return cts;
		}

		/**
		 * @param cts the cts to set
		 */
		public void setCts(long cts) {
			this.cts = cts;
		}

		/**
		 * @return the uid
		 */
		public String getUid() {
			return uid;
		}

		/**
		 * @param uid the uid to set
		 */
		public void setUid(String uid) {
			this.uid = uid;
		}

		/**
		 * @return the vin
		 */
		public String getVin() {
			return vin;
		}

		/**
		 * @param vin the vin to set
		 */
		public void setVin(String vin) {
			this.vin = vin;
		}

		/**
		 * @return the geo
		 */
		public String getGeo() {
			return geo;
		}

		/**
		 * @param geo the geo to set
		 */
		public void setGeo(String geo) {
			this.geo = geo;
		}

		/**
		 * @return the sec
		 */
		public String getSec() {
			return sec;
		}

		/**
		 * @param sec the sec to set
		 */
		public void setSec(String sec) {
			this.sec = sec;
		}

		/**
		 * @return the dtc_clear_ets
		 */
		public Long getDtc_clear_ets() {
			return dtc_clear_ets;
		}

		/**
		 * @param dtc_clear_ets the dtc_clear_ets to set
		 */
		public void setDtc_clear_ets(Long dtc_clear_ets) {
			this.dtc_clear_ets = dtc_clear_ets;
		}

		/**
		 * @return the dtc_clear
		 */
		public boolean isDtc_clear() {
			return dtc_clear;
		}

		/**
		 * @param dtc_clear the dtc_clear to set
		 */
		public void setDtc_clear(boolean dtc_clear) {
			this.dtc_clear = dtc_clear;
		}

		/**
		 * @return the dtcClearPid
		 */
		public static String getDtcClearPid() {
			return dtc_clear_pid;
		}

		@Override
		public List<String> getPids() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}

	// DTC log
	public static class dtc_log_t implements data_record_t {

		private final static String category = "dtc_log";
		private final static String type = "dtc_log";

		// time/user/vehicle/geo-location info
		private Long cts;
		private String uid;
		private String vin;
		private String geo;

		// driving data as array of objects
		// /private List<data_t> datas;

		// meta-data
		private String sec; // security information, optional

		// DTC log as array of string {ets, pid, value}
		// ets
		private Long dtc_log_ets;
		// pid
		private static final String dtc_log_pid = "0300"; // last 00 is padding
		// value
		private String[] dtc_log; // DTC logs

		// convert record to Map
		public Map<String, Object> toJsonMap(Map<String, Object> doc) {
			Map<String, Object> docContent = (doc != null) ? doc
					: new Hashtable<String, Object>();

			// category
			docContent.put("category", getCategory());

			// type
			docContent.put("type", getType());

			// tags
			docContent.put("tags", getTags());

			// cts
			docContent.put("cts", getCts());

			// uid
			docContent.put("uid", getUid());

			// vin
			docContent.put("vin", getVin());

			// geo
			docContent.put("geo", getGeo());

			// secinfo
			docContent.put("sec", getSec());
			
			// data - DTC log
			docContent.put("dtc_log", getDtc_log());
			docContent.put("dtc_log_pid", getDtcLogPid());
			docContent.put("dtc_log_ets",getDtc_log_ets());
						
			return docContent;
		}

		// parse Map to record
		@Override
		public Map<String, Object> parseJsonMap(Map<String, Object> map) {
			if (!((map != null) && map.containsKey("category") && (map
					.get("category") == category))) {
				Log.w(TAG, "ignore wrong category: " + map.get("category"));
			}

			// cts
			if ((map != null) && (map.containsKey("cts"))) {
				setCts((Long) map.get("cts"));
			}

			// geo
			if ((map != null) && (map.containsKey("geo"))) {
				setGeo((String) map.get("geo"));
			}

			// sec
			if ((map != null) && (map.containsKey("sec"))) {
				setSec((String) map.get("sec"));
			}

			// uid
			if ((map != null) && (map.containsKey("uid"))) {
				setUid((String) map.get("uid"));
			}

			// vin
			if ((map != null) && (map.containsKey("vin"))) {
				setVin((String) map.get("vin"));
			}
			
			// type
			if (!((map != null) && map.containsKey("type") && (map.get("type") == type))) {
				Log.w(TAG, "ignore wrong type: " + map.get("type"));
			}

			// tags
			
			// data - DTC log
			if ((map != null) && (map.containsKey("dtc_log"))) {
				setDtc_log((String[]) map.get("dtc_log"));
			}
			if ((map != null) && (map.containsKey("dtc_log_ets"))) {
				setDtc_log_ets((Long) map.get("dtc_log_ets"));
			}
						
			return map;
		}

		/**
		 * @return the category
		 */
		public String getCategory() {
			return category;
		}

		@Override
		public List<String> getTags() {
			// TODO Auto-generated method stub
			List<String> tags = new ArrayList<String>();
			tags.add("dtc_log");

			return tags;
		}

		@Override
		public List<String> getPids() {
			// TODO Auto-generated method stub
			List<String> pids = new ArrayList<String>();
			pids.add(getDtcLogPid());

			return pids;
		}

		@Override
		public boolean fillPidData(List<pidDesc> descs) {
			pidDesc desc = null;
			String pid = null;

			for (int i = 0; i < descs.size(); i++) {
				desc = descs.get(i);

				if (desc != null) {
					pid = String.format("%02X%02X", desc.getMode(),
							desc.getPid());

					// data - DTC log
					if (pid == getDtcLogPid()) {
						setDtc_log(desc.getResult_item_value_string()[0].split(" "));
						setDtc_log_ets(desc.getRts());
					} else {
						Log.w(TAG, "unknown PID desc: " + desc);
					}
				}
			}

			return true;
		}

		@Override
		public String getType() {
			// TODO Auto-generated method stub
			return type;
		}

		@Override
		public boolean fillUsrinfo(String usrinfo) {
			// TODO Auto-generated method stub
			setUid(usrinfo);
			return true;
		}

		@Override
		public boolean fillSecinfo(String secinfo) {
			// TODO Auto-generated method stub
			setSec(secinfo);
			return true;
		}

		@Override
		public boolean fillVin(String vin) {
			// TODO Auto-generated method stub
			setVin(vin);
			return true;
		}

		@Override
		public boolean fillCreatetime(long ts) {
			// TODO Auto-generated method stub
			setCts(ts);
			return true;
		}

		@Override
		public boolean fillGeoinfo(String geo) {
			// TODO Auto-generated method stub
			setGeo(geo);
			return true;
		}

		/**
		 * @return the cts
		 */
		public long getCts() {
			return cts;
		}

		/**
		 * @param cts the cts to set
		 */
		public void setCts(long cts) {
			this.cts = cts;
		}

		/**
		 * @return the uid
		 */
		public String getUid() {
			return uid;
		}

		/**
		 * @param uid the uid to set
		 */
		public void setUid(String uid) {
			this.uid = uid;
		}

		/**
		 * @return the vin
		 */
		public String getVin() {
			return vin;
		}

		/**
		 * @param vin the vin to set
		 */
		public void setVin(String vin) {
			this.vin = vin;
		}

		/**
		 * @return the geo
		 */
		public String getGeo() {
			return geo;
		}

		/**
		 * @param geo the geo to set
		 */
		public void setGeo(String geo) {
			this.geo = geo;
		}

		/**
		 * @return the sec
		 */
		public String getSec() {
			return sec;
		}

		/**
		 * @param sec the sec to set
		 */
		public void setSec(String sec) {
			this.sec = sec;
		}

		/**
		 * @return the dtc_log_ets
		 */
		public Long getDtc_log_ets() {
			return dtc_log_ets;
		}

		/**
		 * @param dtc_log_ets the dtc_log_ets to set
		 */
		public void setDtc_log_ets(Long dtc_log_ets) {
			this.dtc_log_ets = dtc_log_ets;
		}

		/**
		 * @return the dtc_log
		 */
		public String[] getDtc_log() {
			return dtc_log;
		}

		/**
		 * @param dtc_log the dtc_log to set
		 */
		public void setDtc_log(String[] dtc_log) {
			this.dtc_log = dtc_log;
		}

		/**
		 * @return the dtcLogPid
		 */
		public static String getDtcLogPid() {
			return dtc_log_pid;
		}
	}

	public DataRecorder(Parameters params, OBDReader reader, Database couchbase) {
		this.reader = reader;
		this.params = params;
		this.database = couchbase;
	}

	// @description start data retrieve/store thread
	public void run() {
		int steps_pre = params.getSample_precycle();
		int steps_exp = params.getSample_dutycycle() + steps_pre;
		int steps_run = 0;

		List<data_record_t> records = params.getRecords();
		while (true) {
			try {
				// waiting for prefix idle cycle
				if (steps_run < steps_pre) {
					sleep((steps_pre - steps_run) * params.getSample_interval());
					steps_run = steps_pre;
				}

				// //////////////////////////////////////////////////////////////
				// do data retrieve/store stuff every sample_interval ms
				for (int i = 0; i < records.size(); i++) {
					final data_record_t record = records.get(i);
					final List<String> pids = record.getPids();
					final List<pidDesc> descs = new ArrayList<pidDesc>();

					// 1.
					// collect requested PID data as a record
					for (int j = 0; j < pids.size(); j++) {
						String pid = pids.get(j);

						if (0 != reader.executeQuery(pid,
								new executeQueryCallback() {

									@Override
									public void cb(int error, pidDesc desc) {
										if ((error == 0) && (desc != null)) {
											descs.add(desc);
										}

										// 2.
										// check if query done
										if (descs.size() == pids.size()) {
											Log.d(TAG, "query done record:"
													+ record);

											// 3.
											// fill OBD2 data
											if (!record.fillPidData(descs)) {
												Log.w(TAG, "fillPidData fail");
											}

											// 5.
											// fill dedicated data from
											// recorder.param

											// 5.1
											// create time
											if (!record.fillCreatetime(System.currentTimeMillis())) {
												Log.w(TAG, "fillCreatetime fail");
											}

											// 5.2
											// geo location "x y"
											if (params.isGeoenabled()) {
												if (!record.fillGeoinfo(params
														.getGeoinfo()
														.getSimpleLocation())) {
													Log.w(TAG, "fillGeoinfo fail");
												}
											}

											// 5.3
											// user info
											if (!record.fillUsrinfo(params
													.getUsrinfo())) {
												Log.w(TAG, "fillUsrinfo fail");
											}

											// 5.4
											// security info
											if (!record.fillSecinfo(params
													.getSecinfo())) {
												Log.w(TAG, "fillSecinfo fail");
											}

											// 6.
											// store record in database
											{
												// create an empty document
												Document document = database
														.createDocument();

												// write the document to the
												// database
												try {
													document.putProperties(record
															.toJsonMap(null));
												} catch (CouchbaseLiteException e) {
													Log.e(TAG,
															"Cannot write document to database",
															e);
												}
											}

										} else {
											Log.d(TAG, "waiting for next query done");
										}
									}

								})) {
							Log.w(TAG, "fail to query record:" + record
									+ ":pid: " + pid);
							break;
						}
					}

				}
				// ////////////////////////////////////////////////////////////////

				// increase steps_go
				steps_run++;
				if (steps_run < steps_exp) {
					sleep(params.getSample_interval());
				} else {
					if (params.isSample_oneshot()) {
						Log.d(TAG, "oneshot record done params:\n" + params);
						break;
					} else {
						sleep((params.getSample_window() - steps_exp)
								* params.getSample_interval());
						// reset steps_run and repeat sample window
						steps_run = 0;
					}
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				// /e.printStackTrace();
			}
		}
	}

	/**
	 * @return the reader
	 */
	public OBDReader getReader() {
		return reader;
	}

	/**
	 * @return the params
	 */
	public Parameters getParams() {
		return params;
	}

	/**
	 * @return the database
	 */
	public Database getDatabase() {
		return database;
	}

}
