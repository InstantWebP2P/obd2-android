/**
 * 
 */
package com.example.icar;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.service.*;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copyright (c) 2014 Tom Zhou
 * @author tomzhou
 * 
 */
public class OBDReader {
	private static final String TAG = "OBDReader";
	private queryResponse query;

	// cached supported PID
	private Hashtable<String, ArrayList<Integer>> cache_supported_pids;

	// cached constant or real time parameters
	private class cache_param_t {
		/**
		 * @return the time
		 */
		public long getTime() {
			return time;
		}

		/**
		 * @return the value
		 */
		public Object getValue() {
			return value;
		}

		private long time;
		private Object value;

		cache_param_t(long time, Object value) {
			this.time = time;
			this.value = value;
		}
	}
	private Hashtable<String, cache_param_t> cache_params;
	private final static long cache_timeout = 1000; // 1000ms TBO...


	// @description OBD2 PID query/response description
	// notes: some info are determined by mode and pid, so no setter method
	public static class pidDesc {

		/**
		 * @return the result_item_number
		 */
		public final int getResult_item_number() {
			return result_item_number;
		}

		/**
		 * @return the mode
		 */
		public final int getMode() {
			return mode;
		}

		/**
		 * @return the pid
		 */
		public final int getPid() {
			return pid;
		}

		/**
		 * @return the qts
		 */
		public final long getQts() {
			return qts;
		}

		/**
		 * @return the bytes_number
		 */
		public final int getBytes_number() {
			return bytes_number;
		}

		/**
		 * @return the bytes_return
		 */
		public final byte[] getBytes_return() {
			return bytes_return;
		}

		/**
		 * @return the rts
		 */
		public final long getRts() {
			return rts;
		}

		/**
		 * @return the resCb
		 */
		public final responseCallback getResCb() {
			return resCb;
		}

		/**
		 * @return the desc
		 */
		public final String getDesc() {
			return desc;
		}

		/**
		 * @return the minVal
		 */
		public final Float[] getMinVal() {
			return minVal;
		}

		/**
		 * @return the maxVal
		 */
		public final Float[] getMaxVal() {
			return maxVal;
		}

		/**
		 * @return the units
		 */
		public final String[] getUnits() {
			return units;
		}

		/**
		 * @return the usrinfo
		 */
		public final String getUsrinfo() {
			return usrinfo;
		}

		/**
		 * @return the secinfo
		 */
		public final String getSecinfo() {
			return secinfo;
		}

		/**
		 * @return the date
		 */
		public final Date getDate() {
			return date;
		}

		/**
		 * @return the geo
		 */
		public final String getGeo() {
			return geo;
		}

		// @description construct only with mode&pid&responseCallback
		public pidDesc(final int mode, final int pid,
				final responseCallback resCb) throws Exception {
			this.mode = mode;
			this.pid = pid;
			this.resCb = resCb;

			this.bytes_number = -1;
			this.result_item_number = -1;
			// set PID info
			// refer to http://en.wikipedia.org/wiki/OBD-II_PIDs#Mode_1_PID_00
			// for details
			if (mode == 1 || mode == 2) {
				switch (pid) {
				case 0x00:
					this.desc = "PIDs supported [01 - 20]";
					this.bytes_number = 4;

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.formula[0] = "Bit encoded. [A7..D0] = [PID $01..PID $20]";
					break;

				case 0x01:
					this.desc = "Monitor status since DTCs cleared. (Includes malfunction indicator lamp (MIL) status and number of DTCs.) refer to http://en.wikipedia.org/wiki/OBD-II_PIDs#Mode_1_PID_01";
					this.bytes_number = 4;

					this.result_item_number = 26;

					this.formula = new String[this.result_item_number];
					this.formula[0] = "Bit encoded.";
					break;

				case 0x02:
					this.desc = "Freeze DTC";
					this.bytes_number = 2;

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.formula[0] = "";
					break;

				case 0x03:
					this.desc = "Fuel system status";
					this.bytes_number = 2;

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.formula[0] = "Bit encoded.";
					break;

				case 0x04:
					this.desc = "Calculated engine load value";
					this.bytes_number = 1;

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.formula[0] = "A*100/255";
					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					break;

				case 0x05:
					this.desc = "Engine coolant temperature";
					this.bytes_number = 1;

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.formula[0] = "A-40";
					this.minVal[0] = (float) -40;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					break;

				case 0x06:
					this.desc = "Short term fuel % trim°Bank 1\n"
							+ "  -100: Subtracting Fuel (Rich Condition)\n"
							+ " 99.22: Adding Fuel (Lean Condition)";
					this.bytes_number = 1;

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.formula[0] = "(A-128) * 100/128";
					this.minVal[0] = (float) -100;
					this.maxVal[0] = (float) 99.2;
					this.units[0] = "%";
					break;

				case 0x07:
					this.desc = "Long term fuel % trim°Bank 1\n"
							+ "  -100: Subtracting Fuel (Rich Condition)\n"
							+ " 99.22: Adding Fuel (Lean Condition)";
					this.bytes_number = 1;

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.formula[0] = "(A-128) * 100/128";
					this.minVal[0] = (float) -100;
					this.maxVal[0] = (float) 99.2;
					this.units[0] = "%";
					break;

				case 0x08:
					this.desc = "Short term fuel % trim°Bank 2\n"
							+ "  -100: Subtracting Fuel (Rich Condition)\n"
							+ " 99.22: Adding Fuel (Lean Condition)";
					this.bytes_number = 1;

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.formula[0] = "(A-128) * 100/128";
					this.minVal[0] = (float) -100;
					this.maxVal[0] = (float) 99.2;
					this.units[0] = "%";
					break;

				case 0x09:
					this.desc = "Long term fuel % trim°Bank 2\n"
							+ "  -100: Subtracting Fuel (Rich Condition)\n"
							+ " 99.22: Adding Fuel (Lean Condition)";
					this.bytes_number = 1;

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.formula[0] = "(A-128) * 100/128";
					this.minVal[0] = (float) -100;
					this.maxVal[0] = (float) 99.2;
					this.units[0] = "%";
					break;

				case 0x0a:
					this.desc = "Fuel pressure";
					this.bytes_number = 1;

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.formula[0] = "A*3";
					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 765;
					this.units[0] = "kPa (gauge)";
					break;

				case 0x0b:
					this.desc = "Intake manifold absolute pressure";
					this.bytes_number = 1;

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.formula[0] = "A";
					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 255;
					this.units[0] = "kPa (gauge)";
					break;

				case 0x0c:
					this.desc = "Engine RPM";
					this.bytes_number = 2;

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.formula[0] = "((A*256)+B)/4";
					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 16383.75;
					this.units[0] = "rpm";
					break;

				case 0x0d:
					this.desc = "Vehicle speed";
					this.bytes_number = 1;

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.formula[0] = "A";
					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 255;
					this.units[0] = "km/h";
					break;

				case 0x0e:
					this.bytes_number = 1;
					this.desc = "Timing advance";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -64;
					this.maxVal[0] = (float) 63.5;
					this.units[0] = "relative to #1 cylinder";
					this.formula[0] = "(A-128)/2";
					break;

				case 0x0f:
					this.bytes_number = 1;
					this.desc = "Intake air temperature";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -40;
					this.maxVal[0] = (float) 215;
					this.units[0] = "°C";
					this.formula[0] = "A-40";
					break;

				case 0x10:
					this.bytes_number = 2;
					this.desc = "MAF air flow rate";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 655.35;
					this.units[0] = "grams/sec";
					this.formula[0] = "((A*256)+B) / 100";
					break;

				case 0x11:
					this.bytes_number = 1;
					this.desc = "Throttle position";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					this.formula[0] = "A*100/255";
					break;

				case 0x12:
					this.bytes_number = 1;
					this.desc = "Commanded secondary air status";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 0;
					this.units[0] = "";
					this.formula[0] = "Bit encoded";
					break;

				case 0x13:
					this.bytes_number = 1;
					this.desc = "Oxygen sensors present";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 0;
					this.units[0] = "";
					this.formula[0] = "[A0..A3] == Bank 1, Sensors 1-4. [A4..A7] == Bank 2...";
					break;

				case 0x14:
					this.bytes_number = 2;
					this.desc = "Bank 1, Sensor 1:\n"
							+ "Oxygen sensor voltage,Short term fuel trim."
							+ " -100(lean),99.2(rich)";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 1.275;
					this.units[0] = "Volts %";
					this.formula[0] = "A/200(B-128) * 100/128 (if B==$FF, sensor is not used in trim calc)";
					break;

				case 0x15:
					this.bytes_number = 2;
					this.desc = "Bank 1, Sensor 2:\n"
							+ "Oxygen sensor voltage,Short term fuel trim."
							+ " -100(lean),99.2(rich)";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 1.275;
					this.units[0] = "Volts %";
					this.formula[0] = "A/200(B-128) * 100/128 (if B==$FF, sensor is not used in trim calc)";
					break;

				case 0x16:
					this.bytes_number = 3;
					this.desc = "Bank 1, Sensor 3:\n"
							+ "Oxygen sensor voltage,Short term fuel trim."
							+ " -100(lean),99.2(rich)";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 1.275;
					this.units[0] = "Volts %";
					this.formula[0] = "A/200(B-128) * 100/128 (if B==$FF, sensor is not used in trim calc)";
					break;

				case 0x17:
					this.bytes_number = 2;
					this.desc = "Bank 1, Sensor 4:\n"
							+ "Oxygen sensor voltage,Short term fuel trim."
							+ " -100(lean),99.2(rich)";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 1.275;
					this.units[0] = "Volts %";
					this.formula[0] = "A/200(B-128) * 100/128 (if B==$FF, sensor is not used in trim calc)";
					break;

				case 0x18:
					this.bytes_number = 2;
					this.desc = "Bank 2, Sensor 1:\n"
							+ "Oxygen sensor voltage,Short term fuel trim."
							+ " -100(lean),99.2(rich)";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 1.275;
					this.units[0] = "Volts %";
					this.formula[0] = "A/200(B-128) * 100/128 (if B==$FF, sensor is not used in trim calc)";
					break;

				case 0x19:
					this.bytes_number = 2;
					this.desc = "Bank 2, Sensor 2:\n"
							+ "Oxygen sensor voltage,Short term fuel trim."
							+ " -100(lean),99.2(rich)";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 1.275;
					this.units[0] = "Volts %";
					this.formula[0] = "A/200(B-128) * 100/128 (if B==$FF, sensor is not used in trim calc)";
					break;

				case 0x1a:
					this.bytes_number = 2;
					this.desc = "Bank 2, Sensor 3:\n"
							+ "Oxygen sensor voltage,Short term fuel trim."
							+ " -100(lean),99.2(rich)";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 1.275;
					this.units[0] = "Volts %";
					this.formula[0] = "A/200(B-128) * 100/128 (if B==$FF, sensor is not used in trim calc)";
					break;

				case 0x1b:
					this.bytes_number = 2;
					this.desc = "Bank 2, Sensor 4:\n"
							+ "Oxygen sensor voltage,Short term fuel trim."
							+ " -100(lean),99.2(rich)";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 1.275;
					this.units[0] = "Volts %";
					this.formula[0] = "A/200(B-128) * 100/128 (if B==$FF, sensor is not used in trim calc)";
					break;

				case 0x1c:
					this.bytes_number = 1;
					this.desc = "OBD standards this vehicle conforms to";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 1;
					this.maxVal[0] = (float) 255;
					this.units[0] = "";
					this.formula[0] = "Bit encoded.";
					break;

				case 0x1d:
					this.bytes_number = 1;
					this.desc = "Oxygen sensors present";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -1;
					this.maxVal[0] = (float) -1;
					this.units[0] = "";
					this.formula[0] = "Similar to PID 13, but [A0..A7] == [B1S1, B1S2, B2S1, B2S2, B3S1, B3S2, B4S1, B4S2]";
					break;

				case 0x1e:
					this.bytes_number = 1;
					this.desc = "Auxiliary input status";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -1;
					this.maxVal[0] = (float) -1;
					this.units[0] = "";
					this.formula[0] = "A0 == Power Take Off (PTO) status (1 == active) [A1..A7] not used";
					break;

				case 0x1f:
					this.bytes_number = 2;
					this.desc = "Run time since engine start";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 65535;
					this.units[0] = "seconds";
					this.formula[0] = "(A*256)+B";
					break;

				case 0x20:
					this.bytes_number = 4;
					this.desc = "PIDs supported [21 - 40]";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 65535;
					this.units[0] = "";
					this.formula[0] = "Bit encoded [A7..D0] == [PID $21..PID $40]";
					break;

				case 0x21:
					this.bytes_number = 2;
					this.desc = "Distance traveled with malfunction indicator lamp (MIL) on";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 65535;
					this.units[0] = "km";
					this.formula[0] = "(A*256)+B";
					break;

				case 0x22:
					this.bytes_number = 2;
					this.desc = "Fuel Rail Pressure (relative to manifold vacuum)";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 5177.265;
					this.units[0] = "kPa";
					this.formula[0] = "((A*256)+B) * 0.079";
					break;

				case 0x23:
					this.bytes_number = 2;
					this.desc = "Fuel Rail Pressure (diesel, or gasoline direct inject)";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 655350;
					this.units[0] = "kPa (gauge)";
					this.formula[0] = "((A*256)+B) * 10";
					break;

				case 0x24:
					this.bytes_number = 4;
					this.desc = "O2S1_WR_lambda(1): Equivalence Ratio Voltage";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) 0;
					this.maxVal[1] = (float) 8;
					this.units[0] = "N/A";
					this.units[1] = "V";
					this.formula[0] = "((A*256)+B)*2/65535";
					this.formula[1] = "((C*256)+D)*8/65535";
					break;

				case 0x25:
					this.bytes_number = 4;
					this.desc = "O2S2_WR_lambda(1): Equivalence Ratio Voltage";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) 0;
					this.maxVal[1] = (float) 8;
					this.units[0] = "N/A";
					this.units[1] = "V";
					this.formula[0] = "((A*256)+B)*2/65535";
					this.formula[1] = "((C*256)+D)*8/65535";
					break;

				case 0x26:
					this.bytes_number = 4;
					this.desc = "O2S3_WR_lambda(1): Equivalence Ratio Voltage";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) 0;
					this.maxVal[1] = (float) 8;
					this.units[0] = "N/A";
					this.units[1] = "V";
					this.formula[0] = "((A*256)+B)*2/65535";
					this.formula[1] = "((C*256)+D)*8/65535";
					break;

				case 0x27:
					this.bytes_number = 4;
					this.desc = "O2S4_WR_lambda(1): Equivalence Ratio Voltage";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) 0;
					this.maxVal[1] = (float) 8;
					this.units[0] = "N/A";
					this.units[1] = "V";
					this.formula[0] = "((A*256)+B)*2/65535";
					this.formula[1] = "((C*256)+D)*8/65535";
					break;

				case 0x28:
					this.bytes_number = 4;
					this.desc = "O2S5_WR_lambda(1): Equivalence Ratio Voltage";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) 0;
					this.maxVal[1] = (float) 8;
					this.units[0] = "N/A";
					this.units[1] = "V";
					this.formula[0] = "((A*256)+B)*2/65535";
					this.formula[1] = "((C*256)+D)*8/65535";
					break;

				case 0x29:
					this.bytes_number = 4;
					this.desc = "O2S6_WR_lambda(1): Equivalence Ratio Voltage";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) 0;
					this.maxVal[1] = (float) 8;
					this.units[0] = "N/A";
					this.units[1] = "V";
					this.formula[0] = "((A*256)+B)*2/65535";
					this.formula[1] = "((C*256)+D)*8/65535";
					break;

				case 0x2a:
					this.bytes_number = 4;

					this.desc = "O2S7_WR_lambda(1): Equivalence Ratio Voltage";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) 0;
					this.maxVal[1] = (float) 8;
					this.units[0] = "N/A";
					this.units[1] = "V";
					this.formula[0] = "((A*256)+B)*2/65535";
					this.formula[1] = "((C*256)+D)*8/65535";
					break;

				case 0x2b:
					this.bytes_number = 4;
					this.desc = "O2S8_WR_lambda(1): Equivalence Ratio Voltage";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) 0;
					this.maxVal[1] = (float) 8;
					this.units[0] = "N/A";
					this.units[1] = "V";
					this.formula[0] = "((A*256)+B)*2/65535";
					this.formula[1] = "((C*256)+D)*8/65535";
					break;

				case 0x2c:
					this.bytes_number = 1;
					this.desc = "Commanded EGR";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					this.formula[0] = "A*100/255";
					break;

				case 0x2d:
					this.bytes_number = 1;
					this.desc = "EGR Error";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -100;
					this.maxVal[0] = (float) 99.22;
					this.units[0] = "%";
					this.formula[0] = "(A-128) * 100/128";
					break;

				case 0x2e:
					this.bytes_number = 1;
					this.desc = "Commanded evaporative purge";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					this.formula[0] = "A*100/255";
					break;

				case 0x2f:
					this.bytes_number = 1;
					this.desc = "Fuel Level Input";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					this.formula[0] = "A*100/255";
					break;

				case 0x30:
					this.bytes_number = 1;
					this.desc = "# of warm-ups since codes cleared";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 255;
					this.units[0] = "N/A";
					this.formula[0] = "A";
					break;

				case 0x31:
					this.bytes_number = 2;
					this.desc = "Distance traveled since codes cleared";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 65535;
					this.units[0] = "km";
					this.formula[0] = "(A*256)+B";
					break;

				case 0x32:
					this.bytes_number = 2;
					this.desc = "Evap. System Vapor Pressure";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -8192;
					this.maxVal[0] = (float) 8192;
					this.units[0] = "Pa";
					this.formula[0] = "((A*256)+B)/4 (A and B are two's complement signed)";
					break;

				case 0x33:
					this.bytes_number = 1;
					this.desc = "Barometric pressure";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 255;
					this.units[0] = "kPa(Absolute)";
					this.formula[0] = "A";
					break;

				case 0x34:
					this.bytes_number = 4;
					this.desc = "O2S1_WR_lambda(1): Equivalence Ratio Current";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) -128;
					this.maxVal[1] = (float) 128;
					this.units[0] = "N/A";
					this.units[1] = "mA";
					this.formula[0] = "((A*256)+B)/32,768";
					this.formula[1] = "((C*256)+D)/256 - 128";
					break;

				case 0x35:
					this.bytes_number = 4;
					this.desc = "O2S2_WR_lambda(1): Equivalence Ratio Current";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) -128;
					this.maxVal[1] = (float) 128;
					this.units[0] = "N/A";
					this.units[1] = "mA";
					this.formula[0] = "((A*256)+B)/32,768";
					this.formula[1] = "((C*256)+D)/256 - 128";
					break;

				case 0x36:
					this.bytes_number = 4;
					this.desc = "O2S3_WR_lambda(1): Equivalence Ratio Current";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) -128;
					this.maxVal[1] = (float) 128;
					this.units[0] = "N/A";
					this.units[1] = "mA";
					this.formula[0] = "((A*256)+B)/32,768";
					this.formula[1] = "((C*256)+D)/256 - 128";
					break;

				case 0x37:
					this.bytes_number = 4;
					this.desc = "O2S4_WR_lambda(1): Equivalence Ratio Current";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) -128;
					this.maxVal[1] = (float) 128;
					this.units[0] = "N/A";
					this.units[1] = "mA";
					this.formula[0] = "((A*256)+B)/32,768";
					this.formula[1] = "((C*256)+D)/256 - 128";
					break;

				case 0x38:
					this.bytes_number = 4;
					this.desc = "O2S5_WR_lambda(1): Equivalence Ratio Current";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) -128;
					this.maxVal[1] = (float) 128;
					this.units[0] = "N/A";
					this.units[1] = "mA";
					this.formula[0] = "((A*256)+B)/32,768";
					this.formula[1] = "((C*256)+D)/256 - 128";
					break;

				case 0x39:
					this.bytes_number = 4;
					this.desc = "O2S6_WR_lambda(1): Equivalence Ratio Current";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) -128;
					this.maxVal[1] = (float) 128;
					this.units[0] = "N/A";
					this.units[1] = "mA";
					this.formula[0] = "((A*256)+B)/32,768";
					this.formula[1] = "((C*256)+D)/256 - 128";
					break;

				case 0x3a:
					this.bytes_number = 4;
					this.desc = "O2S7_WR_lambda(1): Equivalence Ratio Current";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) -128;
					this.maxVal[1] = (float) 128;
					this.units[0] = "N/A";
					this.units[1] = "mA";
					this.formula[0] = "((A*256)+B)/32,768";
					this.formula[1] = "((C*256)+D)/256 - 128";
					break;

				case 0x3b:
					this.bytes_number = 4;
					this.desc = "O2S8_WR_lambda(1): Equivalence Ratio Current";

					this.result_item_number = 2;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.minVal[1] = (float) -128;
					this.maxVal[1] = (float) 128;
					this.units[0] = "N/A";
					this.units[1] = "mA";
					this.formula[0] = "((A*256)+B)/32,768";
					this.formula[1] = "((C*256)+D)/256 - 128";
					break;

				case 0x3c:
					this.bytes_number = 2;
					this.desc = "Catalyst Temperature Bank 1, Sensor 1";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -40;
					this.maxVal[0] = (float) 6513.5;
					this.units[0] = "°C";
					this.formula[0] = "((A*256)+B)/10 - 40";
					break;

				case 0x3d:
					this.bytes_number = 2;
					this.desc = "Catalyst Temperature Bank 2, Sensor 1";

					this.result_item_number = 1;

					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -40;
					this.maxVal[0] = (float) 6513.5;
					this.units[0] = "°C";
					this.formula[0] = "((A*256)+B)/10 - 40";
					break;

				case 0x3e:
					this.bytes_number = 2;
					this.desc = "Catalyst Temperature Bank 1, Sensor 2";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -40;
					this.maxVal[0] = (float) 6513.5;
					this.units[0] = "°C";
					this.formula[0] = "((A*256)+B)/10 - 40";
					break;

				case 0x3f:
					this.bytes_number = 2;
					this.desc = "Catalyst Temperature Bank 2, Sensor 2";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -40;
					this.maxVal[0] = (float) 6513.5;
					this.units[0] = "°C";
					this.formula[0] = "((A*256)+B)/10 - 40";
					break;

				case 0x40:
					this.bytes_number = 4;
					this.desc = "PIDs supported [41 - 60]";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];

					this.formula[0] = "Bit encoded [A7..D0] == [PID $41..PID $60]";
					break;

				case 0x41:
					this.bytes_number = 4;
					this.desc = "Monitor status this drive cycle";

					this.result_item_number = 24;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -1;
					this.maxVal[0] = (float) -1;
					this.units[0] = "";
					this.formula[0] = "Bit encoded.";
					break;

				case 0x42:
					this.bytes_number = 2;
					this.desc = "Control module voltage";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 65.535;
					this.units[0] = "V";
					this.formula[0] = "((A*256)+B)/1000";
					break;

				case 0x43:
					this.bytes_number = 2;
					this.desc = "Absolute load value";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 25700;
					this.units[0] = "%";
					this.formula[0] = "(A*256)+B)*100/255";
					break;

				case 0x44:
					this.bytes_number = 2;
					this.desc = "Command equivalence ratio";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2;
					this.units[0] = "N/A";
					this.formula[0] = "((A*256)+B)/32768";
					break;

				case 0x45:
					this.bytes_number = 1;
					this.desc = "Relative throttle position";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					this.formula[0] = "A*100/255";
					break;

				case 0x46:
					this.bytes_number = 1;
					this.desc = "Ambient air temperature";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -40;
					this.maxVal[0] = (float) 215;
					this.units[0] = "°C";
					this.formula[0] = "A-40";
					break;

				case 0x47:
					this.bytes_number = 1;
					this.desc = "Absolute throttle position B";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					this.formula[0] = "A*100/255";
					break;

				case 0x48:
					this.bytes_number = 1;
					this.desc = "Absolute throttle position C";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					this.formula[0] = "A*100/255";
					break;

				case 0x49:
					this.bytes_number = 1;
					this.desc = "Absolute throttle position D";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					this.formula[0] = "A*100/255";
					break;

				case 0x4a:
					this.bytes_number = 1;
					this.desc = "Absolute throttle position E";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					this.formula[0] = "A*100/255";
					break;

				case 0x4b:
					this.bytes_number = 1;
					this.desc = "Absolute throttle position F";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					this.formula[0] = "A*100/255";
					break;

				case 0x4c:
					this.bytes_number = 1;
					this.desc = "Commanded throttle actuator";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					this.formula[0] = "A*100/255";
					break;

				case 0x4d:
					this.bytes_number = 2;
					this.desc = "Time run with MIL on";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 65535;
					this.units[0] = "minutes";
					this.formula[0] = "(A*256)+B";
					break;

				case 0x4e:
					this.bytes_number = 2;
					this.desc = "Time since trouble codes cleared";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 65535;
					this.units[0] = "minutes";
					this.formula[0] = "(A*256)+B";
					break;

				case 0x4f:
					this.bytes_number = 4;
					this.desc = "Maximum value for equivalence ratio, oxygen sensor voltage, oxygen sensor current, and intake manifold absolute pressure	";

					this.result_item_number = 4;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.minVal[1] = (float) 0;
					this.minVal[2] = (float) 0;
					this.minVal[3] = (float) 0;

					this.maxVal[0] = (float) 255;
					this.maxVal[1] = (float) 255;
					this.maxVal[2] = (float) 255;
					this.maxVal[3] = (float) 2550;

					this.units[0] = "N/A";
					this.units[1] = "V";
					this.units[2] = "mA";
					this.units[3] = "kPa";

					this.formula[0] = "A";
					this.formula[1] = "B";
					this.formula[2] = "C";
					this.formula[3] = "D*10";
					break;

				case 0x50:
					this.bytes_number = 4;
					this.desc = "Maximum value for air flow rate from mass air flow sensor";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 2550;
					this.units[0] = "g/s";
					this.formula[0] = "A*10, B, C, and D are reserved for future use";
					break;

				case 0x51:
					this.bytes_number = 1;
					this.desc = "Fuel Type";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];

					this.formula[0] = "From fuel type table";
					break;

				case 0x52:
					this.bytes_number = 1;
					this.desc = "Ethanol fuel %";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					this.formula[0] = "A*100/255";
					break;

				case 0x53:
					this.bytes_number = 2;
					this.desc = "Absolute Evap system Vapor Pressure";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 327.675;
					this.units[0] = "kPa";
					this.formula[0] = "((A*256)+B)/200";
					break;

				case 0x54:
					this.bytes_number = 2;
					this.desc = "Evap system vapor pressure";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -32767;
					this.maxVal[0] = (float) 32768;
					this.units[0] = "Pa";
					this.formula[0] = "((A*256)+B)-32767";
					break;

				case 0x55:
					this.bytes_number = 2;
					this.desc = "Short term secondary oxygen sensor trim bank 1 and bank 3";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -100;
					this.maxVal[0] = (float) 99.22;
					this.units[0] = "%";
					this.formula[0] = "(A-128)*100/128 or (B-128)*100/128";
					break;

				case 0x56:
					this.bytes_number = 2;
					this.desc = "Long term secondary oxygen sensor trim bank 1 and bank 3";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -100;
					this.maxVal[0] = (float) 99.22;
					this.units[0] = "%";
					this.formula[0] = "(A-128)*100/128 or (B-128)*100/128";
					break;

				case 0x57:
					this.bytes_number = 2;
					this.desc = "Short term secondary oxygen sensor trim bank 2 and bank 4";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -100;
					this.maxVal[0] = (float) 99.22;
					this.units[0] = "%";
					this.formula[0] = "(A-128)*100/128 or (B-128)*100/128";
					break;

				case 0x58:
					this.bytes_number = 2;
					this.desc = "Long term secondary oxygen sensor trim bank 2 and bank 4";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -100;
					this.maxVal[0] = (float) 99.22;
					this.units[0] = "%";
					this.formula[0] = "(A-128)*100/128 or (B-128)*100/128";
					break;

				case 0x59:
					this.bytes_number = 2;
					this.desc = "Fuel rail pressure (absolute)";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 655350;
					this.units[0] = "kPa";
					this.formula[0] = "((A*256)+B) * 10";
					break;

				case 0x5a:
					this.bytes_number = 1;
					this.desc = "Relative accelerator pedal position";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					this.formula[0] = "A*100/255";
					break;

				case 0x5b:
					this.bytes_number = 1;
					this.desc = "Hybrid battery pack remaining life";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 100;
					this.units[0] = "%";
					this.formula[0] = "A*100/255";
					break;

				case 0x5c:
					this.bytes_number = 1;
					this.desc = "Engine oil temperature";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -40;
					this.maxVal[0] = (float) 210;
					this.units[0] = "°C";
					this.formula[0] = "A-40";
					break;

				case 0x5d:
					this.bytes_number = 2;
					this.desc = "Fuel injection timing";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -210.00;
					this.maxVal[0] = (float) 301.992;
					this.units[0] = "°";
					this.formula[0] = "(((A*256)+B)-26,880)/128";
					break;

				case 0x5e:
					this.bytes_number = 2;
					this.desc = "Engine fuel rate";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 3212.75;
					this.units[0] = "L/h";
					this.formula[0] = "((A*256)+B)*0.05";
					break;

				case 0x5f:
					this.bytes_number = 1;
					this.desc = "Emission requirements to which vehicle is designed";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];

					this.formula[0] = "Bit Encoded";
					break;

				case 0x60:
					this.bytes_number = 4;
					this.desc = "PIDs supported [61 - 80]";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];

					this.formula[0] = "Bit encoded [A7..D0] == [PID $61..PID $80]";
					break;

				case 0x61:
					this.bytes_number = 1;
					this.desc = "Driver's demand engine - percent torque";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -125;
					this.maxVal[0] = (float) 125;
					this.units[0] = "%";
					this.formula[0] = "A-125";
					break;

				case 0x62:
					this.bytes_number = 1;
					this.desc = "Actual engine - percent torque";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -125;
					this.maxVal[0] = (float) 125;
					this.units[0] = "%";
					this.formula[0] = "A-125";
					break;

				case 0x63:
					this.bytes_number = 2;
					this.desc = "Engine reference torque";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) 0;
					this.maxVal[0] = (float) 65535;
					this.units[0] = "Nm";
					this.formula[0] = "A*256+B";
					break;

				case 0x64:
					this.bytes_number = 5;
					this.desc = "Engine percent torque data";

					this.result_item_number = 5;
					this.formula = new String[this.result_item_number];
					this.minVal = new Float[this.result_item_number];
					this.maxVal = new Float[this.result_item_number];
					this.units = new String[this.result_item_number];

					this.minVal[0] = (float) -125;
					this.minVal[1] = (float) -125;
					this.minVal[2] = (float) -125;
					this.minVal[3] = (float) -125;
					this.minVal[4] = (float) -125;

					this.maxVal[0] = (float) 125;
					this.maxVal[1] = (float) 125;
					this.maxVal[2] = (float) 125;
					this.maxVal[3] = (float) 125;
					this.maxVal[4] = (float) 125;

					this.units[0] = "%";
					this.units[1] = "%";
					this.units[2] = "%";
					this.units[3] = "%";
					this.units[4] = "%";

					this.formula[0] = "A-125 Idle";
					this.formula[1] = "B-125 Engine point 1";
					this.formula[2] = "C-125 Engine point 2";
					this.formula[3] = "D-125 Engine point 3";
					this.formula[4] = "E-125 Engine point 4";
					break;

				case 0x65:
					this.bytes_number = 2;
					this.desc = "Auxiliary input / output supported	";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];

					this.formula[0] = "Bit Encoded";
					break;

				// unsupported PIDs 0x66 - 0x87
				default:
					Log.w("OBDReader", "Not supported PID " + this.pid);
					throw new Exception("Not supported PID " + this.pid);
				}
			} else if (mode == 3) { // retrieve DTC no PIDs
				this.bytes_number = -1; // n*6 variable length
				this.desc = "Request trouble codes no matter PID";

				this.result_item_number = 1;
				this.formula = new String[this.result_item_number];

				this.formula[0] = "3 codes per message frame, BCD encoded. refer to http://en.wikipedia.org/wiki/OBD-II_PIDs#Mode_3_.28no_PID_required.29";
			} else if (mode == 4) { // clear DTC
				this.bytes_number = 0;
				this.desc = "Clear trouble codes / Malfunction indicator lamp (MIL) / Check engine light";

				this.result_item_number = 1;
				this.formula = new String[this.result_item_number];

				this.formula[0] = "Clears all stored trouble codes and turns the MIL off.";
			} else if (mode == 9) { // get VID
				switch (pid) {
				case 0x00:
					this.bytes_number = 4;
					this.desc = "Mode 9 supported PIDs (01 to 20)";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];

					this.formula[0] = "Bit encoded. [A7..D0] = [PID $01..PID $20]";
					break;

				case 0x01:
					this.bytes_number = 1;
					this.desc = "VIN Message Count in PID 02. Only for ISO 9141-2, ISO 14230-4 and SAE J1850.";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];

					this.formula[0] = "Usually value will be 5.";
					break;

				case 0x02:
					this.bytes_number = 17; // 17 ~ 20 with 00 pad
					this.desc = "Vehicle Identification Number (VIN)";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];

					this.formula[0] = "17-char VIN, ASCII-encoded and left-padded with null chars (0x00) if needed to.";
					break;

				case 0x03:
					this.bytes_number = 1;
					this.desc = "Calibration ID message count for PID 04. Only for ISO 9141-2, ISO 14230-4 and SAE J1850.";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];

					this.formula[0] = "It will be a multiple of 4 (4 messages are needed for each ID).";
					break;

				case 0x04:
					this.bytes_number = 16;
					this.desc = "Calibration ID";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];

					this.formula[0] = "Up to 16 ASCII chars. Data bytes not used will be reported as null bytes (0x00).";
					break;

				case 0x05:
					this.bytes_number = 1;
					this.desc = "Calibration verification numbers (CVN) message count for PID 06. Only for ISO 9141-2, ISO 14230-4 and SAE J1850.";

					break;

				case 0x06:
					this.bytes_number = 4;
					this.desc = "Calibration Verification Numbers (CVN)";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];

					this.formula[0] = "Raw data left-padded with null characters (0x00). Usually displayed as hex string.";
					break;

				case 0x07:
					this.bytes_number = 1;
					this.desc = "In-use performance tracking message count for PID 08. Only for ISO 9141-2, ISO 14230-4 and SAE J1850.";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];

					this.formula[0] = "8 if sixteen (16) values are required to be reported, 9 if eighteen (18) values are required to be reported, and 10 if twenty (20) values are required to be reported (one message is required to report two values).";
					break;

				case 0x08:
					this.bytes_number = -1; // 32, 36, 40 ???
					this.desc = "In-use performance tracking";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];

					this.formula[0] = "one message is required to report two values";
					break;

				case 0x09:
					this.bytes_number = 1;
					this.desc = "ECU name message count for PID 0A";

					break;

				case 0x0a:
					this.bytes_number = 20;
					this.desc = "ECU name";

					this.result_item_number = 1;
					this.formula = new String[this.result_item_number];

					this.formula[0] = "ASCII-coded. Right-padded with null chars (0x00).";
					break;

				default:
					Log.w(TAG, "Not supported PID " + this.pid);
					throw new Exception("Not supported PID " + this.pid);
				}
			} else {
				Log.w(TAG, "Not supported mode " + this.mode);
				throw new Exception("Not supported mode " + this.mode);
			}

			// allocate result storage
			if (this.result_item_number > 0) {
				this.result_item_name = new String[this.result_item_number];
				this.result_item_value_type = new int[this.result_item_number];
				this.result_item_value_number = new Float[this.result_item_number];
				this.result_item_value_string = new String[this.result_item_number];
			}
		}

		/**
		 * @param qts
		 *            the qts to set
		 */
		public final void setQts(long qts) {
			this.qts = qts;
		}

		/**
		 * @param bytes_return
		 *            the bytes_return to set
		 */
		public final void setBytes_return(byte[] bytes_return) {
			this.bytes_return = bytes_return;
		}

		/**
		 * @param rts
		 *            the rts to set
		 */
		public final void setRts(long rts) {
			this.rts = rts;
		}

		/**
		 * @param usrinfo
		 *            the usrinfo to set
		 */
		public final void setUsrinfo(String usrinfo) {
			this.usrinfo = usrinfo;
		}

		/**
		 * @param secinfo
		 *            the secinfo to set
		 */
		public final void setSecinfo(String secinfo) {
			this.secinfo = secinfo;
		}

		/**
		 * @param date
		 *            the date to set
		 */
		public final void setDate(Date date) {
			this.date = date;
		}

		/**
		 * @param geo
		 *            the geo to set
		 */
		public final void setGeo(String geo) {
			this.geo = geo;
		}

		// query info with tags and responseCallback
		private int mode;
		private int pid;
		private responseCallback resCb;
		private String[] tags;

		// query at time
		private long qts;

		// response info
		private int bytes_number;
		private byte[] bytes_return;

		// response at time
		private long rts;

		// timeout between query and response
		private long timeout;

		// result details
		// notes: some PID has alternate formal and multiple result items
		// type: 0 - number(as float), 1 - string, 2 - error as string on the first item
		private int result_item_number;
		private String[] result_item_name;
		private int[] result_item_value_type;

		private Float[] result_item_value_number;
		private String[] result_item_value_string;

		// description
		// notes: some PID has alternate formal and multiple result items
		private String desc;
		private Float[] minVal;
		private Float[] maxVal;
		private String[] units;
		private String[] formula;

		// meta data
		// user info
		private String usrinfo;
		// security info
		private String secinfo;
		// query/respone date
		private Date date;
		// geo location
		private String geo;

		// @description convert raw response data bytes to useful info
		// @return 0 - convert success, -1 - convert fail
		public int convertToUseful() {
			int item_idx = 0;

			if (this.mode == 1 || this.mode == 2) {
				switch (this.pid) {
				case 0x00:
					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_name[0] = "supported_pids";
						this.result_item_value_type[0] = 1;
						this.result_item_value_string[0] = "";

						// check byte A
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[0] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 1);

						// check byte B
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[1] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 9);

						// check byte C
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[2] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 17);

						// check byte D
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[3] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 25);

					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x20:
					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_name[0] = "supported_pids";
						this.result_item_value_type[0] = 1;
						this.result_item_value_string[0] = "";

						// check byte A
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[0] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x20 + 1);

						// check byte B
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[1] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x20 + 9);

						// check byte C
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[2] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x20 + 17);

						// check byte D
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[3] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x20 + 25);

					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x40:
					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_name[0] = "supported_pids";
						this.result_item_value_type[0] = 1;
						this.result_item_value_string[0] = "";

						// check byte A
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[0] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x40 + 1);

						// check byte B
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[1] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x40 + 9);

						// check byte C
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[2] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x40 + 17);

						// check byte D
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[3] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x40 + 25);

					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x60:
					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_name[0] = "supported_pids";
						this.result_item_value_type[0] = 1;
						this.result_item_value_string[0] = "";

						// check byte A
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[0] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x60 + 1);

						// check byte B
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[1] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x60 + 9);

						// check byte C
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[2] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x60 + 17);

						// check byte D
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[3] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x60 + 25);

					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x80:
					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_name[0] = "supported_pids";
						this.result_item_value_type[0] = 1;
						this.result_item_value_string[0] = "";

						// check byte A
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[0] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x80 + 1);

						// check byte B
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[1] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x80 + 9);

						// check byte C
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[2] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x80 + 17);

						// check byte D
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[3] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 0x80 + 25);

					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x01:
					// for details refer to
					// http://en.wikipedia.org/wiki/OBD-II_PIDs#Mode_1_PID_01

					if (this.bytes_return.length == this.bytes_number) {
						item_idx = -1;

						// check A7 for MIL
						this.result_item_name[++item_idx] = "mil"; // A7 MIL Off
																	// or On,
																	// indicates
																	// if the
																	// CEL/MIL
																	// is on (or
																	// should be
																	// on)
						this.result_item_value_type[item_idx] = 0; // number

						if ((this.bytes_return[0] & 0x80) != 0)
							this.result_item_value_number[item_idx] = (float) 1;
						else
							this.result_item_value_number[item_idx] = (float) 0;

						// check a6-a0 for dec_count
						this.result_item_name[++item_idx] = "dtc_cnt"; // A6-A0
																		// DTC_CNT
																		// Number
																		// of
																		// confirmed
																		// emissions-related
																		// DTCs
																		// available
																		// for
																		// display.
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) (this.bytes_return[0] & 0x7f);

						// check b7 for reserved
						this.result_item_name[++item_idx] = "reserved"; // B7
																		// RESERVED
																		// Reserved
																		// (should
																		// be 0)
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[1] & 0x80) >> 7);

						// check b3: 0 - Spark ignition monitors supported, 1 -
						// Compression ignition monitors supported
						this.result_item_name[++item_idx] = "no_name"; // B3 NO
																		// NAME
																		// 0 =
																		// Spark
																		// ignition
																		// monitors
																		// supported
																		// 1 =
																		// Compression
																		// ignition
																		// monitors
																		// supported
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[1] & 0x08) >> 3);

						// check b0
						this.result_item_name[++item_idx] = "misfire_test_available";
						this.result_item_value_type[item_idx] = 0;
						this.result_item_value_number[item_idx] = (float) (this.bytes_return[1] & 0x01);

						// check b4
						this.result_item_name[++item_idx] = "misfire_test_incomplete";
						this.result_item_value_type[item_idx] = 0;
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[1] & 0x10) >> 4);

						// check b1
						this.result_item_name[++item_idx] = "fuel_system_test_available";
						this.result_item_value_type[item_idx] = 0;
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[1] & 0x02) >> 1);

						// check b5
						this.result_item_name[++item_idx] = "fuel_system_test_incomplete";
						this.result_item_value_type[item_idx] = 0;
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[1] & 0x20) >> 5);

						// check b2
						this.result_item_name[++item_idx] = "components_test_available";
						this.result_item_value_type[item_idx] = 0;
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[1] & 0x04) >> 2);

						// check b6
						this.result_item_name[++item_idx] = "components_test_incomplete";
						this.result_item_value_type[item_idx] = 0;
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[1] & 0x40) >> 6);

						if ((this.bytes_return[1] & 0x08) != 0) {
							// Compression ignition monitors supported

							// check c0
							this.result_item_name[++item_idx] = "nmhc_catalyst_test_available";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x01) >> 0);

							// check c1
							this.result_item_name[++item_idx] = "nox_scr_monitor_test_available";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x02) >> 1);

							// check c2 N/A
							this.result_item_name[++item_idx] = "na";

							// check c3
							this.result_item_name[++item_idx] = "boost_pressure_test_available";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x08) >> 3);

							// check c4 N/A
							this.result_item_name[++item_idx] = "na";

							// check c5
							this.result_item_name[++item_idx] = "exhaust_gas_sensor_test_available";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x20) >> 5);

							// check c6
							this.result_item_name[++item_idx] = "pm_filter_monitoring_test_available";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x40) >> 6);

							// check c7
							this.result_item_name[++item_idx] = "egr_and_or_vvt_system_test_available";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x80) >> 7);

							// check d0
							this.result_item_name[++item_idx] = "nmhc_catalyst_test_incomplete";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x01) >> 0);

							// check d1
							this.result_item_name[++item_idx] = "nox_scr_monitor_test_incomplete";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x02) >> 1);

							// check d2 N/A
							this.result_item_name[++item_idx] = "na";

							// check d3
							this.result_item_name[++item_idx] = "boost_pressure_test_incomplete";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x08) >> 3);

							// check d4 N/A
							this.result_item_name[++item_idx] = "na";

							// check d5
							this.result_item_name[++item_idx] = "exhaust_gas_sensor_test_incomplete";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x20) >> 5);

							// check d6
							this.result_item_name[++item_idx] = "pm_filter_monitoring_test_incomplete";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x40) >> 6);

							// check d7
							this.result_item_name[++item_idx] = "egr_and_or_vvt_system_test_incomplete";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x80) >> 7);

						} else {
							// Spark ignition monitors supported

							// check c0
							this.result_item_name[++item_idx] = "catalyst_test_available";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x01) >> 0);

							// check c1
							this.result_item_name[++item_idx] = "heated_catalyst_test_available";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x02) >> 1);

							// check c2
							this.result_item_name[++item_idx] = "evaporative_system_test_available";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x04) >> 2);

							// check c3
							this.result_item_name[++item_idx] = "secondary_air_system_test_available";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x08) >> 3);

							// check c4
							this.result_item_name[++item_idx] = "A_C_refrigerant_test_available";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x10) >> 4);

							// check c5
							this.result_item_name[++item_idx] = "oxygen_sensor_test_available";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x20) >> 5);

							// check c6
							this.result_item_name[++item_idx] = "oxygen_sensor_heater_test_available";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x40) >> 6);

							// check c7
							this.result_item_name[++item_idx] = "egr_system_test_available";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x80) >> 7);

							// check d0
							this.result_item_name[++item_idx] = "catalyst_test_incomplete";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x01) >> 0);

							// check d1
							this.result_item_name[++item_idx] = "heated_catalyst_test_incomplete";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x02) >> 1);

							// check d2
							this.result_item_name[++item_idx] = "evaporative_system_test_incomplete";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x04) >> 2);

							// check d3
							this.result_item_name[++item_idx] = "secondary_air_system_test_incomplete";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x08) >> 3);

							// check d4
							this.result_item_name[++item_idx] = "A_C_refrigerant_test_incomplete";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x10) >> 4);

							// check d5
							this.result_item_name[++item_idx] = "oxygen_sensor_test_incomplete";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x20) >> 5);

							// check d6
							this.result_item_name[++item_idx] = "oxygen_sensor_heater_test_incomplete";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x40) >> 6);

							// check d7
							this.result_item_name[++item_idx] = "egr_system_test_incomplete";
							this.result_item_value_type[item_idx] = 0;
							this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x80) >> 7);

						}

					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x2f:
					this.result_item_name[0] = "fuel_level_input";

					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_value_type[0] = 0;

						// A*100/255
						this.result_item_value_number[0] = (float) (((char) this.bytes_return[0]) * 100.0 / 255.0);
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x41:
					// for details refer to
					// http://en.wikipedia.org/wiki/OBD-II_PIDs#Mode_1_PID_41

					if (this.bytes_return.length == this.bytes_number) {
						item_idx = -1;

						// check b0
						this.result_item_name[++item_idx] = "misfire_test_enabled";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[1] & 0x01) >> 0);

						// check b1
						this.result_item_name[++item_idx] = "fuel_system_test_enabled";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[1] & 0x02) >> 1);

						// check b2
						this.result_item_name[++item_idx] = "components_test_enabled";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[1] & 0x04) >> 2);

						// check b3
						this.result_item_name[++item_idx] = "reserved_test_enabled";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[1] & 0x08) >> 3);

						// check c0
						this.result_item_name[++item_idx] = "catalyst_test_enabled";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x01) >> 0);

						// check c1
						this.result_item_name[++item_idx] = "heated_catalyst_test_enabled";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x02) >> 1);

						// check c2
						this.result_item_name[++item_idx] = "evaporative_system_test_enabled";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x04) >> 2);

						// check c3
						this.result_item_name[++item_idx] = "secondary_air_system_test_enabled";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x08) >> 3);

						// check c4
						this.result_item_name[++item_idx] = "ac_refrigerant_test_enabled";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x10) >> 4);

						// check c5
						this.result_item_name[++item_idx] = "oxygen_sensor_test_enabled";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x20) >> 5);

						// check c6
						this.result_item_name[++item_idx] = "oxygen_sensor_heater_test_enabled";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x40) >> 6);

						// check c7
						this.result_item_name[++item_idx] = "egr_system_test_enabled";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[2] & 0x80) >> 7);

						// check b4
						this.result_item_name[++item_idx] = "misfire_test_incomplete";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[1] & 0x10) >> 4);

						// check b5
						this.result_item_name[++item_idx] = "fuel_system_test_incomplete";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[1] & 0x20) >> 5);

						// check b6
						this.result_item_name[++item_idx] = "components_test_incomplete";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[1] & 0x40) >> 6);

						// check b7
						this.result_item_name[++item_idx] = "reserved_test_incomplete";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[1] & 0x80) >> 7);

						// check d0
						this.result_item_name[++item_idx] = "catalyst_test_incomplete";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x01) >> 0);

						// check d1
						this.result_item_name[++item_idx] = "heated_catalyst_test_incomplete";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x02) >> 1);

						// check d2
						this.result_item_name[++item_idx] = "evaporative_system_test_incomplete";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x04) >> 2);

						// check d3
						this.result_item_name[++item_idx] = "secondary_air_system_test_incomplete";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x08) >> 3);

						// check d4
						this.result_item_name[++item_idx] = "ac_refrigerant_test_incomplete";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x10) >> 4);

						// check d5
						this.result_item_name[++item_idx] = "oxygen_sensor_test_incomplete";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x20) >> 5);

						// check d6
						this.result_item_name[++item_idx] = "oxygen_sensor_heater_test_incomplete";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x40) >> 6);

						// check d7
						this.result_item_name[++item_idx] = "egr_system_test_incomplete";
						this.result_item_value_type[item_idx] = 0; // number
						this.result_item_value_number[item_idx] = (float) ((this.bytes_return[3] & 0x80) >> 7);

					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x51:
					this.result_item_name[0] = "fuel_type";

					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_value_type[0] = 0;

						this.result_item_value_number[0] = (float) (this.bytes_return[0]);
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x10:
					this.result_item_name[0] = "maf_air_flow_rate";

					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_value_type[0] = 0;

						// ((A*256)+B) / 100
						this.result_item_value_number[0] = (float) (((char) (this.bytes_return[0]) * 256 + (char) (this.bytes_return[1])) / 100.0);
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x11:
					this.result_item_name[0] = "throttle_position";

					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_value_type[0] = 0;

						// A*100/255
						this.result_item_value_number[0] = (float) ((char) (this.bytes_return[0]) * 100.0 / 255.0);
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x0a:
					this.result_item_name[0] = "fuel_pressure";

					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_value_type[0] = 0;

						// A*3
						this.result_item_value_number[0] = (float) ((char) (this.bytes_return[0]) * 3);
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x0b:
					this.result_item_name[0] = "intake_manifold_absolute_pressure";

					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_value_type[0] = 0;

						// A
						this.result_item_value_number[0] = (float) ((char) (this.bytes_return[0]));
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x0c:
					this.result_item_name[0] = "engine_rpm";

					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_value_type[0] = 0;

						// ((A*256)+B)/4
						this.result_item_value_number[0] = (float) (((char) (this.bytes_return[0]) * 256 + (char) (this.bytes_return[1])) / 4.0);
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x0d:
					this.result_item_name[0] = "vehicle_speed";

					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_value_type[0] = 0;

						// A
						this.result_item_value_number[0] = (float) ((char) (this.bytes_return[0]));
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x0e:
					this.result_item_name[0] = "timing_advance";

					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_value_type[0] = 0;

						// (A-128)/2
						this.result_item_value_number[0] = (float) (((char) (this.bytes_return[0]) - 128) / 2.0);
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x0f:
					this.result_item_name[0] = "intake_air_temperature";

					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_value_type[0] = 0;

						// A-40
						this.result_item_value_number[0] = (float) ((char) (this.bytes_return[0]) - 40);
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x02:
					this.result_item_name[0] = "freeze_dtc";
					this.result_item_value_type[0] = 1;

					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_value_string[0] = convertToDTC(this.bytes_return);
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x1c:
					this.result_item_name[0] = "standards";
					this.result_item_value_type[0] = 0;

					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_value_number[0] = (float) this.bytes_return[0];
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				default:
					this.result_item_value_type[0] = 2; // error
					this.result_item_value_string[0] = "invalid response";
					return -1;
				}
			} else if (this.mode == 3) {
				// for details refer to
				// http://en.wikipedia.org/wiki/OBD-II_PIDs#Mode_3_.28no_PID_required.29

				// convert DTCs as string like "DTC1 DTC2 DTC3 "
				if ((this.bytes_return != null)
						&& (this.bytes_return.length > 0)
						&& ((this.bytes_return.length % 2) == 0)) {
					// fill actual bytes number
					this.bytes_number = this.bytes_return.length;

					this.result_item_name[0] = "dtcs";
					this.result_item_value_type[0] = 1;
					this.result_item_value_string[0] = "";

					for (int i = 0; i < this.bytes_return.length; i += 2) {
						byte[] bin = new byte[2];
						String dtc = convertToDTC(bin);

						// append space character
						this.result_item_value_string[0] += (dtc + " ");
					}
				} else {
					this.result_item_value_type[0] = 2; // error
					this.result_item_value_string[0] = "invalid response";
					return -1;
				}

			} else if (this.mode == 4) {
				// clear DTCs, MIL, etc for details refer to
				// http://en.wikipedia.org/wiki/OBD-II_PIDs#Mode_04

				// no return value
				// TBD... catch OK string from response
				this.result_item_value_type[0] = 0; // 0 - success, 1 - fail
				this.result_item_value_number[0] = (float) 0;
			} else if (this.mode == 9) {
				// retried vehicle VID, etc for details refer to
				// http://en.wikipedia.org/wiki/OBD-II_PIDs#Mode_09

				switch (this.pid) {
				case 0x00:
					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_name[0] = "supported_pids";
						this.result_item_value_type[0] = 1;
						this.result_item_value_string[0] = "";

						// check byte A
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[0] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 1);

						// check byte B
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[1] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 9);

						// check byte C
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[2] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 17);

						// check byte D
						for (int i = 0; i < 8; i++)
							if ((this.bytes_return[3] & (0x1 << (7 - i))) != 0)
								this.result_item_value_string[0] += String
										.format("%02x ", i + 25);

					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x01:
					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_name[0] = "vin_message_count";
						this.result_item_value_type[0] = 0;
						this.result_item_value_number[0] = (float) ((char) (this.bytes_return[0]));
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x02:
					if (this.bytes_return.length >= this.bytes_number) {
						this.result_item_name[0] = "vin";
						this.result_item_value_type[0] = 1;
						try {
							this.result_item_value_string[0] = new String(
									this.bytes_return, "UTF-8");
						} catch (Exception e) {
							Log.w(TAG, "VID convert error:" + e);
						}
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x03:
					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_name[0] = "calibration_id_message_count";
						this.result_item_value_type[0] = 0;
						this.result_item_value_number[0] = (float) ((char) (this.bytes_return[0]));
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x04:
					if (this.bytes_return.length <= this.bytes_number) {
						this.result_item_name[0] = "calibration_id";
						this.result_item_value_type[0] = 1;
						try {
							this.result_item_value_string[0] = new String(
									this.bytes_return, "UTF-8");
						} catch (Exception e) {
							Log.w(TAG, "Calibration ID convert error:" + e);
						}
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x05:
					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_name[0] = "cvn_message_count";
						this.result_item_value_type[0] = 0;
						this.result_item_value_number[0] = (float) ((char) (this.bytes_return[0]));
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x06:
					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_name[0] = "cvn";
						this.result_item_value_type[0] = 1;

						this.result_item_value_string[0] = String.format(
								"%02X%02X%02X%02X", this.bytes_return[0],
								this.bytes_return[1], this.bytes_return[2],
								this.bytes_return[3]);
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x07:
					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_name[0] = "in_use_performance_tracking_message_count";
						this.result_item_value_type[0] = 0;
						this.result_item_value_number[0] = (float) ((char) (this.bytes_return[0]));
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x08:
					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_name[0] = "in_use_performance_tracking";
						this.result_item_value_type[0] = 1;

						this.result_item_value_string[0] = "";
						for (int i = 0; i < this.bytes_return.length; i += 2)
							this.result_item_value_string[0] += String
									.format("%04X ",
											((char) this.bytes_return[i])
													* 256
													+ ((char) this.bytes_return[i + 1]));
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x09:
					if (this.bytes_return.length == this.bytes_number) {
						this.result_item_name[0] = "ecu_name_message_count";
						this.result_item_value_type[0] = 0;
						this.result_item_value_number[0] = (float) ((char) (this.bytes_return[0]));
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				case 0x0a:
					if (this.bytes_return.length <= this.bytes_number) {
						this.result_item_name[0] = "ecu_name";
						this.result_item_value_type[0] = 1;
						try {
							this.result_item_value_string[0] = new String(
									this.bytes_return, "UTF-8");
						} catch (Exception e) {
							Log.w(TAG, "ECU name convert error:" + e);
						}
					} else {
						this.result_item_value_type[0] = 2; // error
						this.result_item_value_string[0] = "invalid response";
						return -1;
					}
					break;

				default:
					this.result_item_value_type[0] = 2; // error
					this.result_item_value_string[0] = "invalid response";
					return -1;
				}
			} else {
				return -1;
			}

			return 0;
		}

		// convert two bytes to DTC
		private static String convertToDTC(final byte[] bytes_return) {
			String dtc = "";

			// check bytes length
			if (bytes_return.length != 2) {
				return dtc;
			}

			// check null string 0000
			if ((bytes_return[0] == 0) && (bytes_return[1] == 0)) {
				return dtc;
			}

			// check a7-a6 for first DTC character
			char val = (char) ((bytes_return[0] & 0xc0) >> 6);

			if (val == 0) {
				dtc += "P"; // Powertrain
			} else if (val == 1) {
				dtc += "C"; // Chassis
			} else if (val == 2) {
				dtc += "B"; // Body
			} else if (val == 3) {
				dtc += "U"; // Network
			}

			// check a5-a4 for second DTC character
			val = (char) ((bytes_return[0] & 0x30) >> 4);
			dtc += String.format("%01X", val);

			// check a3-a0 for third DTC character
			val = (char) ((bytes_return[0] & 0x0f) >> 0);
			dtc += String.format("%01X", val);

			// check b7-b4 for fourth character
			val = (char) ((bytes_return[1] & 0xf0) >> 4);
			dtc += String.format("%01X", val);

			// check b3-b0 for fifth character
			val = (char) ((bytes_return[1] & 0x0f) >> 0);
			dtc += String.format("%01X", val);

			return dtc;
		}

		/**
		 * @return the formula
		 */
		public final String[] getFormula() {
			return formula;
		}

		/**
		 * @return the result_item_name
		 */
		public final String[] getResult_item_name() {
			return result_item_name;
		}

		/**
		 * @return the result_item_value_type
		 */
		public final int[] getResult_item_value_type() {
			return result_item_value_type;
		}

		/**
		 * @return the result_item_value_number
		 */
		public final Float[] getResult_item_value_number() {
			return result_item_value_number;
		}

		/**
		 * @return the result_item_value_string
		 */
		public final String[] getResult_item_value_string() {
			return result_item_value_string;
		}

		/**
		 * @return the tags
		 */
		public String[] getTag() {
			return tags;
		}

		/**
		 * @param tags
		 *            the tags to set
		 */
		public void setTag(String[] tag) {
			this.tags = tag;
		}

		/**
		 * @return the timeout
		 */
		public long getTimeout() {
			return timeout;
		}

		/**
		 * @param timeout
		 *            the timeout to set
		 */
		public void setTimeout(long timeout) {
			this.timeout = timeout;
		}
	}

	// @description send OBD2 query with PID and get response
	// @param desc PID mode&pid&responseCallback
	// @return variable data bytes
	public interface queryResponse {
		public int send(final pidDesc desc);
	}

	public interface responseCallback {
		public void onResponse(int error, pidDesc desc);
	}

	// @description inject OBD2 query/response implementation
	public OBDReader(queryResponse query) {
		this.query = query;
		this.cache_supported_pids = new Hashtable<String, ArrayList<Integer>>();
		this.cache_params = new Hashtable<String, cache_param_t>();
	}

	// @description general OBD2 query/response 
	public interface executeQueryCallback {
		public void cb(int error, pidDesc desc);
	}

	public int executeQuery(String qs, final executeQueryCallback cb) {
		if ((qs != null) && qs.matches("[0-9A-Fa-f]{4}")) {
			int mode = Integer.parseInt(qs.substring(0, 2), 16);
			int pid  = Integer.parseInt(qs.substring(2, 4), 16);

			return executeQuery(mode, pid, cb);
		} else {
			Log.w(TAG, "invalid query mode:pid");
			cb.cb(-1, null);
			return -1;
		}
	}

	public int executeQuery(int mode, int pid, final executeQueryCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(mode, pid, new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, null);
						return;
					}

					// let upper application parse the result
					// item_value_type: 0 - number(float), 1 - string, 2 - error
					if (desc.result_item_value_type[0] != 2) {
						cb.cb(0, desc);
						
						// cache real time desc
						if (desc.getMode() == 0x01) {
							cache_params.put("executeQuery@"+String.format("%02X%02X", desc.getMode(), desc.getPid()),
									new cache_param_t(System.currentTimeMillis(), desc));
						}
					} else {
						cb.cb(-1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, null);
			return -1;
		}

		// check cache real time desc
		if ((mode == 0x01) && 
			cache_params.containsKey("executeQuery@"+String.format("%02X%02X", mode, pid))) {
			
			long now = System.currentTimeMillis();
			long last = cache_params.get("executeQuery@"+String.format("%02X%02X", mode, pid)).getTime();
			pidDesc cd = (pidDesc) cache_params.get("executeQuery@"+String.format("%02X%02X", mode, pid)).getValue();

			// check timeout / 3
			if ((now < (last + cache_timeout / 3)) && (now > last)) {
				Log.d(TAG, "use cached realtime pidDesc");
				cb.cb(0, cd);
				return 0;
			}
		} else {
			if (query.send(desc) != 0) {
				cb.cb(-1, null);
				return -1;
			}
		}

		return 0;
	}

	// @description get DTC
	public interface getDTCCallback {
		public void cb(int error, String[] dtcs);
	}

	public int getDTC(final getDTCCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(0x03, 0x00, new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, null);
						return;
					}

					// TODO Auto-generated method stub
					// item_value_type: 0 - number(float), 1 - string, 2 - error
					// as string
					// notes: return DTC string like dtc0 dtc1 dtc2 which
					// separate by " "
					if (desc.result_item_value_type[0] == 1) {
						cb.cb(0, desc.result_item_value_string[0].split(" "));
					} else {
						cb.cb(-1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, null);
			return -1;
		}

		return 0;
	}

	// @description clear DTC
	public interface clearDTCCallback {
		public void cb(int error);
	}

	public int clearDTC(final clearDTCCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(0x04, 0x00, new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1);
						return;
					}

					// TODO Auto-generated method stub
					// item_value_type: 0 - number(float), 1 - string, 2 - error
					// as string
					if ((desc.result_item_value_type[0] == 0)
							&& (desc.result_item_value_number[0] == 0)) {
						cb.cb(0);
					} else {
						cb.cb(-1);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1);
			return -1;
		}

		return 0;
	}

	// @description get vehicle VIN, for details refer to
	// http://en.wikipedia.org/wiki/Vehicle_Identification_Number
	// TBD... assume China follow ISO 3779: World Manufacturer Identifier, VDS,
	// VIS
	public interface getVINCallback {
		public void cb(int error, String vin, String vmi, String vds, String vis);
	}

	public int GetVIN(final getVINCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(0x09, 0x02, new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, null, null, null, null);
						return;
					}

					// TODO Auto-generated method stub
					// item_value_type: 0 - number(float), 1 - string, 2 - error
					// as string
					if (desc.result_item_value_type[0] == 1) {
						cb.cb(0, desc.result_item_value_string[0],
								desc.result_item_value_string[0].substring(0, 3),
								desc.result_item_value_string[0].substring(3, 9),
								desc.result_item_value_string[0].substring(9, 17));

						// cache it
						cache_params.put("GetVIN", 
								new cache_param_t(System.currentTimeMillis(),
										desc.result_item_value_string[0]));
					} else {
						cb.cb(-1, null, null, null, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, null, null, null, null);
			return -1;
		}

		// check cache !!!
		if (cache_params.containsKey("GetVIN")) {
			String cache_vin = (String) cache_params.get("GetVIN").getValue();
			cb.cb(0, cache_vin, 
					cache_vin.substring(0, 3), 
					cache_vin.substring(3, 9), 
					cache_vin.substring(9, 17));
		} else {
			if (query.send(desc) != 0) {
				cb.cb(-1, null, null, null, null);
				return -1;
			}
		}

		return 0;
	}

	// @description retrieve fuel input level
	// @param realtime: true - get current data, false - get freeze data
	public interface getFuelInputLevelCallback {
		public void cb(int error, float level, String desc);
	}

	public int getFuelInputLevel(final boolean realtime,
			final getFuelInputLevelCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x00,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0, desc.result_item_value_number[0],
								desc.result_item_value_number[0]
										+ desc.units[0]);

						// cache real time
						if (realtime) {
							cache_params.put("getFuelInputLevel", 
									new cache_param_t(System.currentTimeMillis(), desc.result_item_value_number[0].intValue()));
						} 
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		// check cache
		if (realtime && cache_params.containsKey("getFuelInputLevel")) {
			long now = System.currentTimeMillis();
			long last = cache_params.get("getFuelInputLevel").getTime();
			float level = (Float) cache_params.get("getFuelInputLevel").getValue();

			// check timeout
			if ((now < (last + cache_timeout)) && (now > last)) {
				Log.d(TAG, "use cached getFuelInputLevel");
				cb.cb(0, level, level+desc.units[0]);
				return 0;
			}
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Vehicle speed
	// @param realtime: true - get current data, false - get freeze data	
	public interface getVehicleSpeedCallback {
		public void cb(int error, int speed, String desc);
	}

	public int getVehicleSpeed(final boolean realtime,
			final getVehicleSpeedCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x0d,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0,
								desc.result_item_value_number[0]
										.intValue(),
										desc.result_item_value_number[0]
												.intValue() + desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Engine RPM
	// @param realtime: true - get current data, false - get freeze data
	public interface getEngineRPMCallback {
		public void cb(int error, float rpm, String desc);
	}

	public int getEngineRPM(final boolean realtime, final getEngineRPMCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x0c,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0, desc.result_item_value_number[0],
								desc.result_item_value_number[0]
										+ desc.units[0]);

						// cache real time 
						if (realtime) {
							cache_params.put("engine_rpm", 
									new cache_param_t(System.currentTimeMillis(), desc.result_item_value_number[0]));
						}
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		// check real time cache
		if (realtime && cache_params.containsKey("engine_rpm")) {
			long now = System.currentTimeMillis();
			long last = cache_params.get("engine_rpm").getTime();
			float rpm = (Float) cache_params.get("engine_rpm").getValue();

			// check timeout
			if ((now < (last + cache_timeout)) && (now > last)) {
				Log.d(TAG, "use cached engine rpm");
				cb.cb(0, rpm, rpm+desc.units[0]);
				return 0;
			}
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Fuel pressure
	// @param realtime: true - get current data, false - get freeze data
	public interface getFuelPressureCallback {
		public void cb(int error, int pressure, String desc);
	}

	public int getFuelPressure(final boolean realtime,
			final getFuelPressureCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x0a,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0,
								desc.result_item_value_number[0]
										.intValue(),
										desc.result_item_value_number[0]
												.intValue() + desc.units[0]);

						// cache it
						if (realtime) {
							cache_params.put("getFuelPressure", 
									new cache_param_t(System.currentTimeMillis(), 
											desc.result_item_value_number[0].intValue()));
						} 
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		// check cache
		if (realtime && cache_params.containsKey("getFuelPressure")) {
			long now = System.currentTimeMillis();
			long last = cache_params.get("getFuelPressure").getTime();
			int pressure = (Integer) cache_params.get("getFuelPressure").getValue();

			// check timeout
			if ((now < (last + cache_timeout)) && (now > last)) {
				Log.d(TAG, "use cached getFuelPressure");
				cb.cb(0, pressure, pressure+desc.units[0]);
				return 0;
			}
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Intake manifold absolute pressure
	// @param realtime: true - get current data, false - get freeze data
	public interface getIntakeManifoldAbsolutePressureCallback {
		public void cb(int error, int pressure, String desc);
	}

	public int getIntakeManifoldAbsolutePressure(final boolean realtime,
			final getIntakeManifoldAbsolutePressureCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x0b,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0,
								desc.result_item_value_number[0]
										.intValue(),
										desc.result_item_value_number[0]
												.intValue() + desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Throttle position
	// @param realtime: true - get current data, false - get freeze data
	public interface getThrottlePositionCallback {
		public void cb(int error, float position, String desc);
	}

	public int getThrottlePosition(final boolean realtime,
			final getThrottlePositionCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x11,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0, desc.result_item_value_number[0],
								desc.result_item_value_number[0]
										+ desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Engine coolant temperature
	// @param realtime: true - get current data, false - get freeze data
	public interface getEngineCoolantTemperatureCallback {
		public void cb(int error, int temperature, String desc);
	}

	public int getEngineCoolantTemperature(final boolean realtime,
			final getEngineCoolantTemperatureCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x05,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0,
								desc.result_item_value_number[0]
										.intValue(),
										desc.result_item_value_number[0]
												.intValue() + desc.units[0]);

						// cache it
						if (realtime) {
							cache_params.put("getEngineCoolantTemperature", 
									new cache_param_t(System.currentTimeMillis(),
											desc.result_item_value_number[0].intValue()));

						}
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});

		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		// check cache
		if (realtime && cache_params.containsKey("getEngineCoolantTemperature")) {
			long now = System.currentTimeMillis();
			long last = cache_params.get("getEngineCoolantTemperature").getTime();
			int temp = (Integer) cache_params.get("getEngineCoolantTemperature").getValue();

			// check timeout
			if ((now < (last + cache_timeout)) && (now > last)) {
				Log.d(TAG, "use cached getEngineCoolantTemperature");
				cb.cb(0, temp, temp+desc.units[0]);
				return 0;
			}
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Engine oil temperature
	// @param realtime: true - get current data, false - get freeze data
	public interface getEngineOilTemperatureCallback {
		public void cb(int error, int temperature, String desc);
	}

	public int getEngineOilTemperature(final boolean realtime,
			final getEngineOilTemperatureCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x5c,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0,
								desc.result_item_value_number[0]
										.intValue(),
										desc.result_item_value_number[0]
												.intValue() + desc.units[0]);

						// cache it
						if (realtime) {
							cache_params.put("getEngineOilTemperature", 
									new cache_param_t(System.currentTimeMillis(),
											desc.result_item_value_number[0].intValue()));

						}
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});

		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		// check cache
		if (realtime && cache_params.containsKey("getEngineOilTemperature")) {
			long now = System.currentTimeMillis();
			long last = cache_params.get("getEngineOilTemperature").getTime();
			int temp = (Integer) cache_params.get("getEngineOilTemperature").getValue();

			// check timeout
			if ((now < (last + cache_timeout)) && (now > last)) {
				Log.d(TAG, "use cached getEngineOilTemperature");
				cb.cb(0, temp, temp+desc.units[0]);
				return 0;
			}
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Engine fuel rate
	// @param realtime: true - get current data, false - get freeze data
	public interface getEngineFuelRateCallback {
		public void cb(int error, float rate, String desc);
	}

	public int getEngineFuelRate(final boolean realtime,
			final getEngineFuelRateCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x5e,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0, desc.result_item_value_number[0],
								desc.result_item_value_number[0]
										+ desc.units[0]);

						// cache it
						if (realtime) { 
							cache_params.put("getEngineFuelRate", 
									new cache_param_t(System.currentTimeMillis(),
											desc.result_item_value_number[0]));
						} 
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});

		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		// check cache
		if (realtime && cache_params.containsKey("getEngineFuelRate")) {
			long now = System.currentTimeMillis();
			long last = cache_params.get("getEngineFuelRate").getTime();
			float rate = (Float) cache_params.get("getEngineFuelRate").getValue();

			// check timeout
			if ((now < (last + cache_timeout)) && (now > last)) {
				Log.d(TAG, "use cached getEngineFuelRate");
				cb.cb(0, rate, rate+desc.units[0]);
				return 0;
			}
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Fuel injection timing
	// @param realtime: true - get current data, false - get freeze data
	public interface getFuelInjectionTimingCallback {
		public void cb(int error, float timing, String desc);
	}

	public int getFuelInjectionTiming(final boolean realtime,
			final getFuelInjectionTimingCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x5d,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0, desc.result_item_value_number[0],
								desc.result_item_value_number[0]
										+ desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Run time since engine start
	// @param realtime: true - get current data, false - get freeze data
	public interface getRuntimeSinceEnginestartCallback {
		public void cb(int error, int seconds, String desc);
	}

	public int getRuntimeSinceEnginestart(final boolean realtime,
			final getRuntimeSinceEnginestartCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x1f,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0,
								desc.result_item_value_number[0]
										.intValue(),
										desc.result_item_value_number[0]
												.intValue() + desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Freeze DTC
	public interface getFreezeDTCCallback {
		public void cb(int error, String dtc);
	}

	public int getFreezeDTC(final getFreezeDTCCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(0x2, 0x02, new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2 - error
					// as string
					if (desc.result_item_value_type[0] == 1) {
						cb.cb(0, desc.result_item_value_string[0]);
					} else {
						cb.cb(-1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Calculated engine load value
	// @param realtime: true - get current data, false - get freeze data
	public interface getCalculatedEngineLoadValueCallback {
		public void cb(int error, float load, String desc);
	}

	public int getCalculatedEngineLoadValue(final boolean realtime,
			final getCalculatedEngineLoadValueCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x04,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0, desc.result_item_value_number[0],
								desc.result_item_value_number[0]
										+ desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve supported PIDs
	// @param xmode: the different mode has own supported PIDs(1&2 have same
	// PIDs)
	public interface getSupportedPIDsCallback {
		public void cb(int error, List<Integer> pids);
	}

	private void getSupportedPids(final int mode, final int pid,
			final getSupportedPIDsCallback cb) {
		final int xmode = (mode == 2) ? 1 : mode;
		final int xpid = (pid / 0x20) * 0x20;

		final String k = String.format("%02X", mode);

		if (!this.cache_supported_pids.containsKey(k)) {
			this.cache_supported_pids.put(k, new ArrayList<Integer>());
		}

		// recursive retrieve all supported PIDs
		pidDesc desc;
		try {
			desc = new pidDesc(xmode, xpid, new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2 - error
					// as string
					if ((desc.result_item_value_type[0] == 1)) {
						// record PID
						String[] pidstr = desc.result_item_value_string[0]
								.split(" ");
						for (int i = 0; i < pidstr.length; i++)
							cache_supported_pids.get(k).add(
									Integer.parseInt(pidstr[i], 16));

						// check for next group PID recursively
						if (cache_supported_pids.get(k).contains(0x20 + xpid)) {
							getSupportedPids(xmode, xpid + 0x20, cb);
						} else {
							cb.cb(0, cache_supported_pids.get(k));
						}
					} else {
						cb.cb(-1, null);
					}
				}

			});
		} catch (Exception e) {
			cb.cb(-1, null);
			return;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, null);
			return;
		}

	}

	public int getSupportedPIDs(int mode, final getSupportedPIDsCallback cb) {
		mode = (mode == 2) ? 1 : mode;

		String k = String.format("%02X", mode);

		if (this.cache_supported_pids.containsKey(k)) {
			cb.cb(0, this.cache_supported_pids.get(k));
		} else {
			getSupportedPids(mode, 0x00, cb);
		}

		return 0;
	}

	// @description retrieve Distance traveled with malfunction indicator lamp
	// (MIL) on
	// @param realtime: true - get current data, false - get freeze data
	public interface getDistanceTraveledWithMILOnCallback {
		public void cb(int error, int distance, String desc);
	}

	public int getDistanceTraveledWithMILOn(final boolean realtime,
			final getDistanceTraveledWithMILOnCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x21,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0,
								desc.result_item_value_number[0]
										.intValue(),
										desc.result_item_value_number[0]
												.intValue() + desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Fuel Type
	public interface getFuelTypeCallback {
		public void cb(int error, int type, String desc);
	}

	public int getFuelType(final getFuelTypeCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(0x01, 0x51, new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						int type = desc.result_item_value_number[0]
								.intValue();

						cb.cb(0, type, VehicleInfo.parseFueltype(type));

						// cache it !!!
						cache_params.put("getFuelType", 
								new cache_param_t(System.currentTimeMillis(), type));
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		// check cache !!!
		if (cache_params.containsKey("getFuelType")) {
			int cache_fuel_type = (Integer) cache_params.get("getFuelType").getValue();
			cb.cb(0, cache_fuel_type, VehicleInfo.parseFueltype(cache_fuel_type));
		} else {
			if (query.send(desc) != 0) {
				cb.cb(-1, -1, null);
				return -1;
			}
		}

		return 0;
	}


	// @description retrieve Ethanol fuel
	// @param realtime: true - get current data, false - get freeze data
	public interface getEthanolFuelCallback {
		public void cb(int error, float rate, String desc);
	}

	public int getEthanolFuel(final boolean realtime, final getEthanolFuelCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x52,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0, desc.result_item_value_number[0],
								desc.result_item_value_number[0]
										+ desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Fuel rail pressure (absolute)
	// @param realtime: true - get current data, false - get freeze data
	public interface getFuelRailPressureAbsoluteCallback {
		public void cb(int error, int pressure, String desc);
	}

	public int getFuelRailPressureAbsolute(final boolean realtime,
			final getFuelRailPressureAbsoluteCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x59,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0,
								desc.result_item_value_number[0]
										.intValue(),
										desc.result_item_value_number[0]
												.intValue() + desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Number of confirmed emissions-related DTCs
	// available for display and MIL status 1:on, 0:off
	// @param realtime: true - get current data, false - get freeze data
	public interface getDTCCountMILStatusCallback {
		public void cb(int error, int dtc_count, boolean mil_on);
	}

	public int getDTCCountMILStatus(final getDTCCountMILStatusCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(0x01, 0x01, new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, false);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2 - error
					// as string
					if ((desc.result_item_name[0] == "mil")
							&& (desc.result_item_value_type[0] == 0)
							&& (desc.result_item_name[1] == "dtc_cnt")
							&& (desc.result_item_value_type[1] == 0)) {
						cb.cb(0,
								desc.result_item_value_number[1].intValue(),
								desc.result_item_value_number[0].intValue() == 1);
					} else {
						cb.cb(-1, -1, false);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, false);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, false);
			return -1;
		}

		return 0;
	}

	// @description retrieve MAF air flow rate
	// @param realtime: true - get current data, false - get freeze data
	public interface getMAFAirFlowRateCallback {
		public void cb(int error, float rate, String desc);
	}

	public int getMAFAirFlowRate(final boolean realtime,
			final getMAFAirFlowRateCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x10,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0, desc.result_item_value_number[0],
								desc.result_item_value_number[0]
										+ desc.units[0]);

						// cache it
						if (realtime) {
							cache_params.put("getMAFAirFlowRate", 
									new cache_param_t(System.currentTimeMillis(),
											desc.result_item_value_number[0]));
						}
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		// check cache
		if (realtime && cache_params.containsKey("getMAFAirFlowRate")) {
			long now = System.currentTimeMillis();
			long last = cache_params.get("getMAFAirFlowRate").getTime();
			float maf = (Float) cache_params.get("getMAFAirFlowRate").getValue();

			// check timeout
			if ((now < (last + cache_timeout)) && (now > last)) {
				Log.d(TAG, "use cached getMAFAirFlowRate");
				cb.cb(0, maf, maf+desc.units[0]);
				return 0;
			}
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Short term fuel % trim—Bank 1
	// @param realtime: true - get current data, false - get freeze data
	public interface getShortTermFuelTrimBank1Callback {
		public void cb(int error, float trim, String desc);
	}

	public int getShortTermFuelTrimBank1(final boolean realtime,
			final getShortTermFuelTrimBank1Callback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x06,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0, desc.result_item_value_number[0],
								desc.result_item_value_number[0]
										+ desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Long term fuel % trim—Bank 1
	// @param realtime: true - get current data, false - get freeze data
	public interface getLongTermFuelTrimBank1Callback {
		public void cb(int error, float trim, String desc);
	}

	public int getLongTermFuelTrimBank1(final boolean realtime,
			final getLongTermFuelTrimBank1Callback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x07,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0, desc.result_item_value_number[0],
								desc.result_item_value_number[0]
										+ desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Short term fuel % trim—Bank 2
	// @param realtime: true - get current data, false - get freeze data
	public interface getShortTermFuelTrimBank2Callback {
		public void cb(int error, float trim, String desc);
	}

	public int getShortTermFuelTrimBank2(final boolean realtime,
			final getShortTermFuelTrimBank2Callback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x08,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0, desc.result_item_value_number[0],
								desc.result_item_value_number[0]
										+ desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Long term fuel % trim—Bank 2
	// @param realtime: true - get current data, false - get freeze data
	public interface getLongTermFuelTrimBank2Callback {
		public void cb(int error, float trim, String desc);
	}

	public int getLongTermFuelTrimBank2(final boolean realtime,
			final getLongTermFuelTrimBank2Callback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x09,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0, desc.result_item_value_number[0],
								desc.result_item_value_number[0]
										+ desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Driver's demand engine - percent torque
	// @param realtime: true - get current data, false - get freeze data
	public interface getDriverDemandEnginePercentTorqueCallback {
		public void cb(int error, float torque, String desc);
	}

	public int getDriverDemandEnginePercentTorque(final boolean realtime,
			final getDriverDemandEnginePercentTorqueCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x61,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0, desc.result_item_value_number[0],
								desc.result_item_value_number[0]
										+ desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Actual engine - percent torque
	// @param realtime: true - get current data, false - get freeze data
	public interface getActualEnginePercentTorqueCallback {
		public void cb(int error, float torque, String desc);
	}

	public int getActualEnginePercentTorque(final boolean realtime,
			final getActualEnginePercentTorqueCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x62,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0, desc.result_item_value_number[0],
								desc.result_item_value_number[0]
										+ desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Engine reference torque
	// @param realtime: true - get current data, false - get freeze data
	public interface getEngineReferenceTorqueCallback {
		public void cb(int error, float torque, String desc);
	}

	public int getEngineReferenceTorque(final boolean realtime,
			final getEngineReferenceTorqueCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x63,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, -1, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						cb.cb(0, desc.result_item_value_number[0],
								desc.result_item_value_number[0]
										+ desc.units[0]);
					} else {
						cb.cb(-1, -1, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, -1, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, -1, null);
			return -1;
		}

		return 0;
	}

	// @description retrieve Engine percent torque data
	// @param realtime: true - get current data, false - get freeze data
	// @param torques: [idle,Engine point 1,Engine point2,Engine point3,Engine
	// point4]
	// @param descs: [idle,Engine point 1,Engine point2,Engine point3,Engine
	// point4]
	public interface getEnginePercentTorqueDataCallback {
		public void cb(int error, float[] torques, String[] descs);
	}

	public int getEnginePercentTorqueData(final boolean realtime,
			final getEnginePercentTorqueDataCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(realtime ? 0x01 : 0x2, 0x64,
					new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, null, null);
						return;
					}

					// item_value_type: 0 - number(float), 1 - string, 2
					// - error as string
					if (desc.result_item_value_type[0] == 0) {
						float[] torques = new float[desc.result_item_number];
						String[] descs = new String[desc.result_item_number];

						for (int i = 0; i < desc.result_item_number; i++) {
							torques[i] = desc.result_item_value_number[i];
							descs[i] = torques[i] + desc.units[i];
						}

						cb.cb(0, torques, descs);
					} else {
						cb.cb(-1, null, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, null, null);
			return -1;
		}

		if (query.send(desc) != 0) {
			cb.cb(-1, null, null);
			return -1;
		}

		return 0;
	}

	// @description get vehicle supported standards, for details refer to
	// http://en.wikipedia.org/wiki/OBD-II_PIDs#Mode_1_PID_1C
	public interface getStandardCallback {
		public void cb(int error, int standard, String desc);
	}

	public int getStandard(final getStandardCallback cb) {
		pidDesc desc;
		try {
			desc = new pidDesc(0x01, 0x1c, new responseCallback() {

				@Override
				public void onResponse(int error, pidDesc desc) {
					// convert response to useful information
					if ((error != 0) || (desc.convertToUseful() != 0)) {
						cb.cb(-1, 0, null);
						return;
					}

					// TODO Auto-generated method stub
					// item_value_type: 0 - number(float), 1 - string, 2 - error
					// as string
					if (desc.result_item_value_type[0] == 0) {
						int standard = desc.result_item_value_number[0].intValue();
						cb.cb(0, standard, VehicleInfo.parseStandard(standard));

						// cache it
						cache_params.put("getStandard", 
								new cache_param_t(System.currentTimeMillis(),
										standard));
					} else {
						cb.cb(-1, 0, null);
					}
				}

			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// /e.printStackTrace();
			cb.cb(-1, 0, null);
			return -1;
		}

		// check cache !!!
		if (cache_params.containsKey("getStandard")) {
			int cache_standard = (Integer) cache_params.get("getStandard").getValue();
			cb.cb(0, cache_standard, VehicleInfo.parseStandard(cache_standard));
		} else {
			if (query.send(desc) != 0) {
				cb.cb(-1, 0, null);
				return -1;
			}
		}

		return 0;
	}


}
