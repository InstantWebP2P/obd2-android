/**
 * 
 */
package com.example.icar;

import android.util.Log;

/**
 * Copyright (c) 2014 Tom Zhou
 * @author tomzhou
 * 
 */
public class FuelConsumption {
	private static final String TAG = "FuelConsumption";

	// @description covert LPKM to LP100KM
	public static float LPKM2100KM(float lpkm) {
		return lpkm * 100f;
	}
	
	// @description convert KMPL to LPKM
	public static float KMPL2LPKM(float kmpl) {
		return 1.0f / kmpl;
	}
	
	// @description calculate realtime fuel consumption by engine fuel rate
	// @param engine_fuel_rate L/h
	// @param vehicle_speed Km/h
	// @return Km/L
	public static float realtimeKMPL(float engine_fuel_rate, float vehicle_speed) {
		return vehicle_speed / engine_fuel_rate;
	}

	// @description calculate realtime fuel consumption by MAF
	// @param fuel_type as PID0151
	// @param maf air flow rate grams/sec
	// @param vehicle_speed Km/h
	// @return Km/L
	public static float realtimeKMPL(int fuel_type, float maf, float vehicle_speed) {
		float density = VehicleInfo.fuelDensity(fuel_type);
		float afr = VehicleInfo.fuelAFR(fuel_type);
		
		return (vehicle_speed / (maf * 3600 / afr / density));
	}

	// @description calculate realtime fuel consumption by MAF
	// @param fuel_type as PID0151
	// @param maf air flow rate grams/sec
	// @param vehicle_speed Km/h
	// @return Km/g
	public static float realtimeKMPG(int fuel_type, float maf, float vehicle_speed) {
		float afr = VehicleInfo.fuelAFR(fuel_type);

		return (vehicle_speed / (maf * 3600 / afr));
	}

	// @description real time fuel consumption based on engine fuel rate records
	public static float realtimeKMPL(DataRecorder.efr_realtime_fuel_consumption_record_t efr) {
		return realtimeKMPL(efr.getEngine_fuel_rate(), efr.getVehicle_speed());
	}

	// @description real time fuel consumption based on MAF records
	public static float realtimeKMPL(DataRecorder.maf_realtime_fuel_consumption_record_t maf) {
		return realtimeKMPL(maf.getFuel_type(), maf.getMaf(), maf.getVehicle_speed());
	}
	
	// @description average fuel consumption 
	// @param vin - vehicle identifier
	// @param s1 - start value of distance_traveled_since_codes_cleared
	// @param s2 - start value of distance_traveled_with_malfunction_indicator_lamp_on
	// @param e1 - stop value of distance_traveled_since_codes_cleared
	// @param e2 - stop value of distance_traveled_with_malfunction_indicator_lamp_on
	// @param fs - start value of fuel input level
	// @param fe - stop value of fuel input level
	// @return average Km/L
	public static float averageKMPL(String vin, float s1, float s2, float e1, float e2, float fs, float fe) {
		float d1 = e1 - s1;
		float d2 = e2 - s2;
		float f = fs - fe;
		float v = VehicleInfo.fueltankVolume(vin);
		
		if ((d1 >= 0) && (d1 > d2)) {
			return d1 / (f * v);
		} else {
			return d2 / (f * v);
		}
	}
	
	public static float averageKMPL(
			DataRecorder.average_fuel_consumption_record_t from, 
			DataRecorder.average_fuel_consumption_record_t to) {
		return averageKMPL(
				from.getVin(), 
				from.getDistance_traveled_since_codes_cleared(), from.getDistance_traveled_with_malfunction_indicator_lamp_on(),
				to.getDistance_traveled_since_codes_cleared(), to.getDistance_traveled_with_malfunction_indicator_lamp_on(),
				from.getFuel_input_level(), to.getFuel_input_level());
	}
	
}
