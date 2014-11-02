/**
 * @description implement eml327 device obd2 read command
 */
package com.example.icar;

import java.io.*;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.example.icar.OBDReader.pidDesc;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

/**
 * Copyright (c) 2014 Tom Zhou
 * @author tomzhou
 * 
 */
public class ELM327 implements OBDReader.queryResponse {
	private static final String TAG = "ELM327";
	private InputStream sockin;
	private OutputStream sockout;
	private BufferedReader in;
	private BufferedWriter out;
	private Hashtable<String, LinkedList<OBDReader.pidDesc>> descCnt;
	private rxThread rxTask;
	private Handler timeoutMonitor;
	private LinkedList<cmdPidDesc> sendQueue;
	private txThread txTask;
	private static final long txIPG = 150; // 150ms or 50ms TBO...
	private static final long txMaxScaler = 6000 / txIPG; // 6s TBO...
	private volatile long txScaler; // tx IPG scaler
	private volatile boolean txSyncFlag[] = {true};

	// flag to save ELM327 work sate
	private volatile int FLAGS;
	public static int FLAG_L1 = 0x1; // extra line feed after carriage return
	public static int FLAG_S1 = 0x2; // spaces in output
	public static int FLAG_H1 = 0x4; // headers and checksum to be sent
	public static int FLAG_E1 = 0x8; // echo

	public ELM327(InputStream in, OutputStream out, int flags) throws IOException {
		this.sockin = in;
		this.sockout = out;
		this.descCnt = new Hashtable<String, LinkedList<OBDReader.pidDesc>>();
		this.sendQueue = new LinkedList<cmdPidDesc>();
		this.timeoutMonitor = new Handler();
		this.txScaler = 1;
		this.txTask = new txThread();
		this.rxTask = new rxThread();

		this.FLAGS = flags;

		// create buffered I/O
		this.in = new BufferedReader(new InputStreamReader(in, "UTF-8"));
		this.out = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));

		// Queue initial AT command with \r

		// make sure '>' prompt show out
		this.sendQueue.add(new cmdPidDesc("\r", null));

		// reset
		this.sendQueue.add(new cmdPidDesc("ATZ\r", null));

		// Turns off extra line feed after carriage return
		if ((this.FLAGS & FLAG_L1) != 0) {
			this.sendQueue.add(new cmdPidDesc("ATL1\r", null));
		} else {
			this.sendQueue.add(new cmdPidDesc("ATL0\r", null));
		}

		// This disables spaces in in output, which is faster!
		if ((this.FLAGS & FLAG_S1) != 0) {
			this.sendQueue.add(new cmdPidDesc("ATS1\r", null));
		} else {
			this.sendQueue.add(new cmdPidDesc("ATS0\r", null));
		}

		// Turns on/off(H1/H0) headers and checksum to be sent.
		if ((this.FLAGS & FLAG_H1) != 0) {
			this.sendQueue.add(new cmdPidDesc("ATH1\r", null));
		} else {
			this.sendQueue.add(new cmdPidDesc("ATH0\r", null));
		}


		// Setting the Header / ID Bit
		// TBD... tuning
		///this.sendQueue.add(new cmdPidDesc("ATSH7E0\r", null));

		// Receive Filtering - the CRA command
		// TBD... tuning
		///this.sendQueue.add(new cmdPidDesc("ATCRA7E8\r", null));


		// Turns off echo
		if ((this.FLAGS & FLAG_E1) != 0) {
			this.sendQueue.add(new cmdPidDesc("ATE1\r", null));
		} else {
			this.sendQueue.add(new cmdPidDesc("ATE0\r", null));
		}

		// Turn adaptive timing to 2. This is an aggressive learn curve for
		// adjusting the timeout. Will make huge difference on slow systems.
		this.sendQueue.add(new cmdPidDesc("ATAT2\r", null));

		// Set timeout to 10 * 4 = 40msec, allows +20 queries per second.
		// This is the maximum wait-time. ATAT will decide if it should wait
		// shorter or not.
		///this.sendQueue.add(new cmdPidDesc("ATST0A\r", null));

		// Set the protocol to automatic.
		this.sendQueue.add(new cmdPidDesc("ATSP0\r", null));

		// make sure '>' prompt show out
		this.sendQueue.add(new cmdPidDesc("\r", null));

		// start both tx and rx thread
		start();
	}

	// @description start both recv and send thread
	private void start() {
		Log.d(TAG, "start both rx and tx thread");
		rxTask.start();
		txTask.start();
	}
		
	// @description response retrieve thread
	private class rxThread extends Thread {
		
		public void run() {
    		Log.d(TAG, "rx thread started");
			
			final List<String> lines = new ArrayList<String>();
			String line;

			try {
				while ((line = in.readLine()) != null) {
					Log.d(TAG, "PID line:" + line);

					// 1.
					// make sure \r in line
					if (!line.matches("[\\r\\n]$")) {
						line += "\r";
					}

					// 2.
					// store line
					lines.add(line);

					// 3.
					// parse lines to PIDDesc
					List<OBDReader.pidDesc> descs = parsePidDescs(lines);
					if (descs != null && descs.size() > 0) {
						for (int idx = 0; idx < descs.size(); idx++) {
							// 4.
							// time stamp on response
							descs.get(idx).setRts(System.currentTimeMillis());

							// 5.
							// execute response callback
							if (descs.get(idx).getResCb() != null) {
								descs.get(idx).getResCb()
										.onResponse(0, descs.get(idx));
							} else {
								Log.d(TAG, "Dummy PID response:" + descs.get(idx));
							}
						}
					}
				}
			} catch (Exception e) {
				Log.w(TAG, "Retrieve PID response exception:" + e);

				try {
					in.close();
					sockin.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					///e1.printStackTrace();
					Log.e(TAG, "clost input stream execption:" + e1);
				}

				// clear pending query
				while (descCnt.elements().hasMoreElements()) {
					LinkedList<OBDReader.pidDesc> descl = descCnt.elements()
							.nextElement();

					OBDReader.pidDesc desc;
					while ((desc = descl.poll()) != null) {
						if (desc.getResCb() != null) {
							desc.getResCb().onResponse(-1, desc);
						}
					}
				}
			}
		}
		
	}
	
	// @description query command with pidDesc bean
	private class cmdPidDesc {
		private final String cmd;
		private final OBDReader.pidDesc desc;
		
		cmdPidDesc(String cmd, OBDReader.pidDesc desc) {
			this.cmd = cmd;
			this.desc = desc;
		}
	}
	
	// @description query send thread
    private class txThread extends Thread {
   	
    	// check command queue and schedule query in next IPG
    	public void run() {
    		Log.d(TAG, "tx thread started");
    		
    		while (true) {
                // check 
    			final cmdPidDesc cmd = sendQueue.poll();

                if (cmd != null) {
                	// down scaler
                	txScaler = (txScaler / 2) | 1;
    				final OBDReader.pidDesc desc = cmd.desc;

    				// send command
                	try {
						out.write(cmd.cmd);
	        			out.flush();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						///e.printStackTrace();
						try {
							out.close();
							sockout.close();
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							///e1.printStackTrace();
							Log.e(TAG, "clost output stream execption:" + e1);
						}
						
						// check PID descriptor
						if ((desc != null) && tstDesc(desc)) {
							delDesc(desc);
							desc.getResCb().onResponse(-1, desc);
							Log.e(TAG, "query fail on PID" + desc.getMode()
									+ "-" + desc.getPid() + ":" + desc + " " + e);
						}
					}
                	
            		// start timeout monitor in case timeout > 0 ms
            		// TBD... hard-code on 600ms
            		if ((desc != null) && tstDesc(desc) &&
            			(desc.getResCb() != null) 
            			/* && desc.getTimeout() > 0 */) {
            			timeoutMonitor.postDelayed(new Runnable() {

            				@Override
            				public void run() {
            					// TODO Auto-generated method stub
            					if (tstDesc(desc)) {
            						delDesc(desc);
            						desc.getResCb().onResponse(-2, desc);
            						Log.e(TAG, "query timeout on PID" + desc.getMode()
            								+ "-" + desc.getPid() + ":" + desc);
            					}
            				}

            			}, 600);
            			// /desc.getTimeout());
            		}
                } else {
                	// up scaler
                	txScaler *= 2;

                	// schedule after next IPG or tx request come
                	synchronized(txSyncFlag) {
                		if (txSyncFlag[0]) txSyncFlag[0] = false;

                		try {
							txSyncFlag.wait(txIPG * (txScaler % txMaxScaler));
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
                	}
                }
    		}
    	}
    	
    }

	// parse PID and clear parsed lines
	private List<OBDReader.pidDesc> parsePidDescs(List<String> lines) {
		List<OBDReader.pidDesc> descsReturn = new ArrayList<OBDReader.pidDesc>();

		// 1.
		// parse command array
		String cmdsStr = "";
		for (int idx = 0; idx < lines.size(); idx++) {
			cmdsStr += lines.get(idx);
		}
		Log.d(TAG, "Cmd strs: " + cmdsStr);

		String[] cmdsArray = cmdsStr.split(">");

		// 1.1
		// check cmdsArray
		if ((cmdsArray == null) || (cmdsArray.length == 0)) {
			Log.d(TAG, "No valid cmd: " + cmdsStr);
			return null;
		}

		// 1.2
		// nothing to do why ??? refer to
		if (cmdsArray.length < 2) {
			Log.d(TAG, "AT cmd: " + cmdsArray[0]);
			return null;
		}

		// 2.
		// parse PID desc one by one
		for (int idx = 0; idx < cmdsArray.length; idx++) {
			if (cmdsArray[idx] == "")
				continue;

			String[] resStrs = cmdsArray[idx].split("[\\n\\r]+");
			Log.d(TAG, "parse "
					+ ((resStrs.length > 1) ? "multiple line respones"
							: "signle line response"));

			ArrayList<String> mixLines = new ArrayList<String>();
			// 2.1
			// check for No data or OK is the response
			for (int i = 0; i < resStrs.length; i++) {
				if ((resStrs[i].indexOf("NO DATA") > 0) ||
					(resStrs[i].indexOf("OK") > 0) ||
					(resStrs[i].indexOf("?") > 0) ||
					(resStrs[i] == "")) {
					continue;
				} else {
					mixLines.add(resStrs[i]);
				}
			}

			// extract only one ECU's response in case multiple ECU response
			ArrayList<String> resLines = new ArrayList<String>();

			// prefer first line of ECU's response
			String ecu = mixLines.get(0).substring(0, 3);

			for (int i = 0; i < mixLines.size(); i++)
				if (ecu == mixLines.get(i).substring(0, 3))
					resLines.add(mixLines.get(i));

			Log.d(TAG, "parse response from ECU " + ecu);

			// 3.
			// single line response per query
			if (resLines.size() == 1) {
				Log.d(TAG, "valid single response:" + resLines.get(0));

				// 3.1
				// parse mode&pid like below (for detail refer to
				// http://elmelectronics.com/DSheets/ELM329DS.pdf)
				// >01 00
				// 7E8 06 41 00 BE 3F B8 13 00
				String[] valStrs;
				if ((this.FLAGS & FLAG_S1) != 0) {
					valStrs = resLines.get(0).split(" ");
				} else {
					String rlstr = resLines.get(0);
					int rlcnt = 1 + (rlstr.length() - 3) / 2;
					valStrs = new String[rlcnt];

					valStrs[0] = rlstr.substring(0, 3);
					for (int i = 1; i < rlcnt; i++) {
						valStrs[i] = rlstr
								.substring(3 + (i - 1) * 2, 3 + i * 2);
					}
				}

				// parse CAN message ID
				int msg_id, msg_len, obd_mode, obd_pid;
				try {
					msg_id = Integer.parseInt(valStrs[0], 16);
					msg_len = Integer.parseInt(valStrs[1], 16);

					// check message length
					if (valStrs.length < (2 + msg_len)) {
						Log.w(TAG, "corrupted CAN message:" + resLines.get(0));
						continue;
					}

					obd_mode = Integer.parseInt(valStrs[2], 16);
					obd_pid = Integer.parseInt(valStrs[3], 16);
				} catch (Exception e) {
					Log.w(TAG, "parse CAN message exception:" + e);
					continue;
				}

				Log.d(TAG,
						"parsed CAN message: "
								+ String.format("%03x %02x %02x %02x", msg_id,
										msg_len, obd_mode, obd_pid));

				// response's mode should be 0x4x
				if ((obd_mode & 0xc0) == 0x40) {
					// 3.2
					// dequeue pidDesc
					OBDReader.pidDesc qdesc = this.getDesc(obd_mode & 0x3f,
							obd_pid);

					if (qdesc != null) {
						// parse result and fill in pidDesc like
						// >01 00
						// 7E8 06 41 00 BE 3F B8 13 00

						// check bytes number
						if (qdesc.getBytes_number() > 0) {
							if (qdesc.getBytes_number() != (msg_len - 2)) {
								Log.w(TAG,
										"invalid response length:"
												+ resLines.get(0));
								continue;
							}
						}

						// push byte in result
						byte[] resBytes = new byte[msg_len - 2];
						for (int i = 4; i < (msg_len + 2); i++) {
							resBytes[i - 4] = (byte) Integer.parseInt(
									valStrs[i], 16);
						}
						qdesc.setBytes_return(resBytes);

						// 3.3
						// add pidDesc in returned list
						descsReturn.add(qdesc);
					}
				} else {
					Log.w(TAG, "invalid OBD response mode:" + obd_mode
							+ ",pid:" + obd_pid);
				}
			} else {
				// 4.
				// multiple line response per query, like VID, follow ISO
				// 15765-4 Message Types
				Log.d(TAG, "valid multiple response:" + resLines);

				// 4.1
				// reorder lines according to ISO 15765-4,
				// http://en.wikipedia.org/wiki/ISO_15765-2
				// >09 04
				// 7E8 10 13 49 04 01 35 36 30
				// 7E8 21 32 38 39 35 34 41 43
				// 7E8 22 00 00 00 00 00 00 00
				ArrayList<String> orderLines = new ArrayList<String>();

				for (int i = 0; i < resLines.size(); i++) {
					int seq_exp = ((i == 0) ? 0x10 : (0x20 + i));

					for (int j = 0; j < resLines.size(); j++) {
						int seq_cur = Integer.parseInt(resLines.get(j)
								.substring(4, 6), 16);

						if (seq_exp == seq_cur)
							orderLines.add(resLines.get(j));
					}
				}

				// 4.2
				// parse mode&pid
				String[] valStrs;
				if ((this.FLAGS & FLAG_S1) != 0) {
					valStrs = orderLines.get(0).split(" ");
				} else {
					String rlstr = orderLines.get(0);
					int rlcnt = 1 + (rlstr.length() - 3) / 2;
					valStrs = new String[rlcnt];

					valStrs[0] = rlstr.substring(0, 3);
					for (int c = 1; c < rlcnt; c++) {
						valStrs[c] = rlstr
								.substring(3 + (c - 1) * 2, 3 + c * 2);
					}
				}

				// parse CAN message ID
				int msg_id, msg_len, obd_mode, obd_pid;
				try {
					msg_id = Integer.parseInt(valStrs[0], 16);
					msg_len = Integer.parseInt(valStrs[2], 16)
							+ (Integer.parseInt(valStrs[1], 16) & 0x0f) * 256;

					// check message length
					if ((6 + (resLines.size() - 1) * 7) < msg_len) {
						Log.w(TAG, "corrupted CAN message:" + resLines);
						continue;
					}

					obd_mode = Integer.parseInt(valStrs[3], 16);
					obd_pid = Integer.parseInt(valStrs[4], 16);
				} catch (Exception e) {
					Log.w(TAG, "parse CAN message exception:" + e);
					continue;
				}

				Log.i(TAG,
						"parsed CAN message: "
								+ String.format("%03x %02x %02x %02x", msg_id,
										msg_len, obd_mode, obd_pid));

				// response's mode should be 0x4x
				if ((obd_mode & 0xc0) == 0x40) {
					// 2.3
					// dequeue pidDesc
					OBDReader.pidDesc qdesc = this.getDesc(obd_mode & 0x3f,
							obd_pid);

					if (qdesc != null) {
						// 2.4
						// parse result and fill multiple line response in
						// pidDesc like
						// >09 04
						// 7E8 10 13 49 04 01 35 36 30
						// 7E8 21 32 38 39 35 34 41 43
						// 7E8 22 00 00 00 00 00 00 00

						// check bytes number
						if (qdesc.getBytes_number() > 0) {
							if (qdesc.getBytes_number() != (msg_len - 3)) {
								Log.w(TAG, "invalid response length:"
										+ orderLines.get(0));
								continue;
							}
						}

						// push byte in result
						byte[] resBytes = new byte[msg_len - 3];

						// first line
						int idxb = 0;
						if ((this.FLAGS & FLAG_S1) != 0) {
							valStrs = orderLines.get(0).split(" ");
						} else {
							String rlstr = orderLines.get(0);
							int rlcnt = 1 + (rlstr.length() - 3) / 2;
							valStrs = new String[rlcnt];

							valStrs[0] = rlstr.substring(0, 3);
							for (int c = 1; c < rlcnt; c++) {
								valStrs[c] = rlstr.substring(3 + (c - 1) * 2,
										3 + c * 2);
							}
						}

						resBytes[idxb++] = (byte) Integer.parseInt(valStrs[6],
								16);
						resBytes[idxb++] = (byte) Integer.parseInt(valStrs[7],
								16);
						resBytes[idxb++] = (byte) Integer.parseInt(valStrs[8],
								16);

						// sequence line
						for (int i = 1; i < orderLines.size(); i++) {
							String rlstr = orderLines.get(i);

							if ((this.FLAGS & FLAG_S1) != 0) {
								valStrs = rlstr.split(" ");
							} else {
								int rlcnt = 1 + (rlstr.length() - 3) / 2;
								valStrs = new String[rlcnt];

								valStrs[0] = rlstr.substring(0, 3);
								for (int c = 1; c < rlcnt; c++) {
									valStrs[c] = rlstr.substring(
											3 + (c - 1) * 2, 3 + c * 2);
								}
							}

							for (int j = 2; j < valStrs.length; j++) {
								resBytes[idxb++] = (byte) Integer.parseInt(
										valStrs[j], 16);
								if (idxb >= (msg_len - 3))
									break;
							}

							if (idxb >= (msg_len - 3))
								break;
						}
						qdesc.setBytes_return(resBytes);

						// 2.5
						// add pidDesc in returned list
						descsReturn.add(qdesc);
					}
				} else {
					Log.w(TAG, "invalid OBD response mode:" + obd_mode
							+ ",pid:" + obd_pid);
				}
			}
		}

		// 3.
		// clear lines container
		lines.clear();

		return descsReturn;
	}

	@Override
	// TBD... timeout mechanism
	public int send(final OBDReader.pidDesc desc) {
		// TODO Auto-generated method stub

		if (desc == null) {
			return -1;
		}

		// 0.
		// down tx scaler
		txScaler = 1;
		
		// 1.
		// time stamp on query
		desc.setQts(System.currentTimeMillis());
		
		// 2.
		// queue pidDesc with response callback 
		if (putDesc(desc) != 0) {
			return -1;
		}

		// 3.
		// query command 
		String cmd = String.format("%02X", desc.getMode());
		if ((desc.getMode() == 0x01) || // current data
			(desc.getMode() == 0x02) || // freeze data
			(desc.getMode() == 0x09)) { // VIN data
			cmd += String.format("%02X", desc.getPid());

			// append response line count in case each response line have 4 data bytes
			if (desc.getBytes_number() > 0) {
				cmd += String.format("%X", (desc.getBytes_number() + 4) / 4);
			}
		}
		cmd += "\r";
		
		if (sendQueue.add(new cmdPidDesc(cmd, desc))) {
			// notify tx task
			synchronized(txSyncFlag) {
				if (!txSyncFlag[0]) {
					txSyncFlag[0] = true;
					txSyncFlag.notifyAll();
				}
			}
			
			return 0;
		} else {
			Log.e(TAG, "queue query command fail");
			return -1;
		}
	}

	// @description queue pidDesc
	private int putDesc(OBDReader.pidDesc desc) {
		String k = String.format("pid%02x%02x", desc.getMode(), desc.getPid());

		if (!descCnt.containsKey(k)) {
			descCnt.put(k, new LinkedList<OBDReader.pidDesc>());
		}
		if (descCnt.get(k).add(desc)) {
			return 0;
		} else {
			return -1;
		}
	}

	// @description dequeue pidDesc
	private OBDReader.pidDesc getDesc(int mode, int pid) {
		String k = String.format("pid%02x%02x", mode, pid);

		if (descCnt.containsKey(k)) {
			// pop first pidDesc
			return descCnt.get(k).poll();
		} else {
			return null;
		}
	}

	// @description test if pidDesc in queue
	private boolean tstDesc(OBDReader.pidDesc desc) {
		String k = String.format("pid%02x%02x", desc.getMode(), desc.getPid());

		if (descCnt.containsKey(k)) {
			return descCnt.get(k).contains(desc);
		} else {
			return false;
		}
	}

	// @description delete if pidDesc in queue
	private void delDesc(OBDReader.pidDesc desc) {
		String k = String.format("pid%02x%02x", desc.getMode(), desc.getPid());

		if (descCnt.containsKey(k) && descCnt.get(k).contains(desc)) {
			descCnt.get(k).remove(desc);
		}
	}

}
