package com.example.icar;

import java.util.Hashtable;
import android.util.Log;

public class VehicleInfo {
	private final static String TAG = "VehicleInfo";

	// @description fuel tank volume
	public static float fueltankVolume(String vin) {
		float v = 0.0f;
		String vds = vin;

		if (vds.indexOf("cn") > 0) {
			v = 32.0f;
		}

		return v;
	}

	// @description fuel Air/Fuel Ratios
	// @param fuel_type as PID0151
	// @return air/fuel ratio
	/*
	 * Value Description 0 Not available 1 Gasoline - 14.7 2 Methanol - 6.4 3
	 * Ethanol - 9 4 Diesel - 14.6 5 LPG - 17.2 ?? 6 CNG - 7 Propane - 15.5 8
	 * Electric 9 Bifuel running Gasoline 10 Bifuel running Methanol 11 Bifuel
	 * running Ethanol 12 Bifuel running LPG 13 Bifuel running CNG 14 Bifuel
	 * running Propane 15 Bifuel running Electricity 16 Bifuel running electric
	 * and combustion engine 17 Hybrid gasoline 18 Hybrid Ethanol 19 Hybrid
	 * Diesel 20 Hybrid Electric 21 Hybrid running electric and combustion
	 * engine 22 Hybrid Regenerative 23 Bifuel running diesel
	 */
	public static float fuelAFR(int fuel_type) {
		float afr = 14.7f;

		switch (fuel_type) {
		case 1:
			afr = 14.7f;
			break;

		case 2:
			afr = 6.4f;
			break;

		case 3:
			afr = 9.0f;
			break;

		case 4:
			afr = 14.6f;
			break;

		case 5:
			afr = 17.2f;
			break;

		case 7:
			afr = 15.5f;
			break;

		case 0:
		default:
			Log.w(TAG, "unknown fuel type " + fuel_type);
			break;
		}

		return afr;
	}

	// @description fuel density
	// @param fuel_type as PID0151
	// @return g/L
	public static float fuelDensity(int fuel_type) {
		float density = 725.0f;

		switch (fuel_type) {
		case 1:
			density = 725.0f;
			break;

		case 2:
			// /density = ?f;
			break;

		case 3:
			// /density = ?f;
			break;

		case 4:
			density = 870.0f;
			break;

		case 5:
			// /density = ?f;
			break;

		case 7:
			// /density = ?f;
			break;

		case 0:
		default:
			Log.w(TAG, "unknown fuel type " + fuel_type);
			break;
		}

		return density;
	}

	// @description DTC details
	// @description for details refer to
	// http://www.datsc.com/Technical-Resources
	// http://www.totalcardiagnostics.com/support/Knowledgebase/Article/View/21/0/genericmanufacturer-obd2-codes-and-their-meanings
	// http://www.engine-codes.com/
	// http://www.obdch.com/pages/info-OBD/
	public static class dtcInfo_t {
		/**
		 * @return the desc_cn
		 */
		public String getDesc_cn() {
			return desc_cn;
		}
		/**
		 * @param desc_cn the desc_cn to set
		 */
		public void setDesc_cn(String desc_cn) {
			this.desc_cn = desc_cn;
		}
		/**
		 * @return the dtc
		 */
		public String getDtc() {
			return dtc;
		}
		/**
		 * @param dtc the dtc to set
		 */
		public void setDtc(String dtc) {
			this.dtc = dtc;
		}
		/**
		 * @return the desc
		 */
		public String getDesc() {
			return desc;
		}
		/**
		 * @param desc the desc to set
		 */
		public void setDesc(String desc) {
			this.desc = desc;
		}
		private String dtc;
		private String desc; // English description
		private String desc_cn; // Chinese description
		private float score; // score for the DTC
		
		dtcInfo_t(String dtc, String desc, String desc_cn, float score) {
			this.dtc = dtc;
			this.desc = desc;
			this.desc_cn = desc_cn;
			this.score = score;
		}
		/**
		 * @return the score
		 */
		public float getScore() {
			return score;
		}
		/**
		 * @param score the score to set
		 */
		public void setScore(float score) {
			this.score = score;
		}
	}
	
	public final static Hashtable<String, dtcInfo_t> dtcDetails;
	static {
		// diagnosis trouble code details
		dtcDetails = new Hashtable<String, dtcInfo_t>();

		// Powertrain
		dtcDetails.put("P0000", new dtcInfo_t("P0000", "no fault on powertrain system", "动力系统没有故障", -3));
		dtcDetails.put("P0100", new dtcInfo_t("P0100", "MAF Sensor Circuit Insufficient Activity", "空气流量计线路不良", -3));
		dtcDetails.put("P0101", new dtcInfo_t("P0100", "Mass Air Flow (MAF) Sensor Performance", "空气流量计线路不良", -3));

		// Chassis
		dtcDetails.put("C0000", new dtcInfo_t("C0000", "Vehicle Speed Information Circuit Malfunction", "车速信息电路故障", -1));

		// Body
		dtcDetails.put("B0001", new dtcInfo_t("B0001", "PCM Discrete Input Speed Signal Error", "PCM离散输入速度信号错误", -1));

		// Network
		dtcDetails.put("U0001", new dtcInfo_t("U0001", "High Speed CAN Communication Bus", "高速CAN总线通信", -2));


		// vehicle maker details
		vmiDetails = new Hashtable<String, vmiInfo_t>();


		// vehicle parameters details
		vdsDetails = new Hashtable<String, vdsInfo_t>();


	}

	// @description Vehicle information details
	// @description - vehicle maker information
	// @param key - vin.vmi
	public static class vmiInfo_t {
		/**
		 * @return the country
		 */
		public String getCountry() {
			return country;
		}

		/**
		 * @param country
		 *            the country to set
		 */
		public void setCountry(String country) {
			this.country = country;
		}

		/**
		 * @return the remarker
		 */
		public String getRemarker() {
			return remarker;
		}

		/**
		 * @param remarker
		 *            the remarker to set
		 */
		public void setRemarker(String remarker) {
			this.remarker = remarker;
		}

		private String vmi;

		// vehicle information details
		private String name;
		private String country;
		private String remarker;

		vmiInfo_t(String vmi) {
			this.setVmi(vmi);
		}

		/**
		 * @return the vmi
		 */
		public String getVmi() {
			return vmi;
		}

		/**
		 * @param vmi
		 *            the vmi to set
		 */
		public void setVmi(String vmi) {
			this.vmi = vmi;
		}

		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}

		/**
		 * @param name
		 *            the name to set
		 */
		public void setName(String name) {
			this.name = name;
		}
	}

	public final static Hashtable<String, vmiInfo_t> vmiDetails;

	// @description vehicle class information
	// @param key - vin.vmi + vin.vds
	public static class vdsInfo_t {
		private String vmi;
		private String vds;

		vdsInfo_t(String vmi, String vds) {
			this.setVmi(vmi);
			this.setVds(vds);
		}

		/**
		 * @return the vmi
		 */
		public String getVmi() {
			return vmi;
		}

		/**
		 * @param vmi
		 *            the vmi to set
		 */
		public void setVmi(String vmi) {
			this.vmi = vmi;
		}

		/**
		 * @return the vds
		 */
		public String getVds() {
			return vds;
		}

		/**
		 * @param vds
		 *            the vds to set
		 */
		public void setVds(String vds) {
			this.vds = vds;
		}
	}

	public final static Hashtable<String, vdsInfo_t> vdsDetails;


	// @description parse fuel type string
	public static String parseFueltype(int type) {
		// Value Description
		// 0 Not available
		// 1 Gasoline
		// 2 Methanol
		// 3 Ethanol
		// 4 Diesel
		// 5 LPG
		// 6 CNG
		// 7 Propane
		// 8 Electric
		// 9 Bifuel running Gasoline
		// 10 Bifuel running Methanol
		// 11 Bifuel running Ethanol
		// 12 Bifuel running LPG
		// 13 Bifuel running CNG
		// 14 Bifuel running Propane
		// 15 Bifuel running Electricity
		// 16 Bifuel running electric and combustion
		// engine
		// 17 Hybrid gasoline
		// 18 Hybrid Ethanol
		// 19 Hybrid Diesel
		// 20 Hybrid Electric
		// 21 Hybrid running electric and combustion
		// engine
		// 22 Hybrid Regenerative
		// 23 Bifuel running diesel

		String detail = "unknown";
		switch (type) {
		case 0:
			detail = "Not available";
			break;

		case 1:
			detail = "Gasoline";
			break;

		case 2:
			detail = "Methanol";
			break;

		case 3:
			detail = "Ethanol";
			break;

		case 4:
			detail = "Diesel";
			break;

		case 5:
			detail = "LPG";
			break;

		case 6:
			detail = "CNG";
			break;

		case 7:
			detail = "Propane";
			break;

		case 8:
			detail = "Electric";
			break;

		case 9:
			detail = "Bifuel running Gasoline";
			break;

		case 10:
			detail = "Bifuel running Methanol";
			break;

		case 11:
			detail = "Bifuel running Ethanol";
			break;

		case 12:
			detail = "Bifuel running LPG";
			break;

		case 13:
			detail = "Bifuel running CNG";
			break;

		case 14:
			detail = "Bifuel running Propane";
			break;

		case 15:
			detail = "Bifuel running Electricity";
			break;

		case 16:
			detail = "Bifuel running electric and combustion engine";
			break;

		case 17:
			detail = "Hybrid gasoline";
			break;

		case 18:
			detail = "Hybrid Ethanol";
			break;

		case 19:
			detail = "Hybrid Diesel";
			break;

		case 20:
			detail = "Hybrid Electric";
			break;

		case 21:
			detail = "Hybrid running electric and combustion engine";
			break;

		case 22:
			detail = "Hybrid Regenerative";
			break;

		case 23:
			detail = "Bifuel running diesel";
			break;

		default:
			detail = "Unknown";
			break;
		}

		return detail;
	}
	
	// @description parse OBD2 standard 
	/*
Value	Description
1	OBD-II as defined by the CARB
2	OBD as defined by the EPA
3	OBD and OBD-II
4	OBD-I
5	Not OBD compliant
6	EOBD (Europe)
7	EOBD and OBD-II
8	EOBD and OBD
9	EOBD, OBD and OBD II
10	JOBD (Japan)
11	JOBD and OBD II
12	JOBD and EOBD
13	JOBD, EOBD, and OBD II
14	Reserved
15	Reserved
16	Reserved
17	Engine Manufacturer Diagnostics (EMD)
18	Engine Manufacturer Diagnostics Enhanced (EMD+)
19	Heavy Duty On-Board Diagnostics (Child/Partial) (HD OBD-C)
20	Heavy Duty On-Board Diagnostics (HD OBD)
21	World Wide Harmonized OBD (WWH OBD)
22	Reserved
23	Heavy Duty Euro OBD Stage I without NOx control (HD EOBD-I)
24	Heavy Duty Euro OBD Stage I with NOx control (HD EOBD-I N)
25	Heavy Duty Euro OBD Stage II without NOx control (HD EOBD-II)
26	Heavy Duty Euro OBD Stage II with NOx control (HD EOBD-II N)
27	Reserved
28	Brazil OBD Phase 1 (OBDBr-1)
29	Brazil OBD Phase 2 (OBDBr-2)
30	Korean OBD (KOBD)
31	India OBD I (IOBD I)
32	India OBD II (IOBD II)
33	Heavy Duty Euro OBD Stage VI (HD EOBD-IV)
34-250	Reserved
251-255	Not available for assignment (SAE J1939 special meaning)
	 * */
	public static String parseStandard(int standard) {
		String desc = "unknown";

		switch (standard) {
		case 1:
			desc = "OBD-II as defined by the CARB";
			break;

		case 2:
			desc = "OBD as defined by the EPA";
			break;

		case 3:
			desc = "OBD and OBD-II";
			break;

		case 4:
			desc = "OBD-I";
			break;

		case 5:
			desc = "Not OBD compliant";
			break;

		case 6:
			desc = "EOBD (Europe)";
			break;

		case 7:
			desc = "EOBD and OBD-II";
			break;

		case 8:
			desc = "EOBD and OBD";
			break;

		case 9:
			desc = "EOBD, OBD and OBD II";
			break;

		case 10:
			desc = "JOBD (Japan)";
			break;

		case 11:
			desc = "JOBD and OBD II";
			break;

		case 12:
			desc = "JOBD and EOBD";
			break;

		case 13:
			desc = "JOBD, EOBD, and OBD II";

		case 14:
		case 15:
		case 16:
			desc = "Reserved";
			break;

		case 17:
			desc = "Engine Manufacturer Diagnostics (EMD)";
			break;

		case 18:
			desc = "Engine Manufacturer Diagnostics Enhanced (EMD+)";
			break;

		case 19:
			desc = "Heavy Duty On-Board Diagnostics (Child/Partial) (HD OBD-C)";
			break;

		case 20:
			desc = "Heavy Duty On-Board Diagnostics (HD OBD)";
			break;

		case 21:
			desc = "World Wide Harmonized OBD (WWH OBD)";
			break;

		case 22:
			desc = "Reserved";
			break;

		case 23:
			desc = "Heavy Duty Euro OBD Stage I without NOx control (HD EOBD-I)";
			break;

		case 24:
			desc = "Heavy Duty Euro OBD Stage I with NOx control (HD EOBD-I N)";
			break;

		case 25:
			desc = "Heavy Duty Euro OBD Stage II without NOx control (HD EOBD-II)";
			break;

		case 26:
			desc = "Heavy Duty Euro OBD Stage II with NOx control (HD EOBD-II N)";
			break;

		case 27:
			desc = "Reserved";
			break;

		case 28:
			desc = "Brazil OBD Phase 1 (OBDBr-1)";
			break;

		case 29:
			desc = "Brazil OBD Phase 2 (OBDBr-2)";
			break;

		case 30:
			desc = "Korean OBD (KOBD)";
			break;

		case 31:
			desc = "India OBD I (IOBD I)";
			break;

		case 32:
			desc = "India OBD II (IOBD II)";
			break;

		case 33:
			desc = "Heavy Duty Euro OBD Stage VI (HD EOBD-IV)";
			break;

			///34-250	Reserved
			///251-255	Not available for assignment (SAE J1939 special meaning)	

		case 0:
		default:
			Log.d(TAG, "invalid standard: "+standard);
			break;
		}

		return desc;
	}

	
}
