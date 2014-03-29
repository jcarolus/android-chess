package jwtc.android.chess.ics;

import java.net.*;
import java.io.*;

public class TimesealingSocket extends Socket implements Runnable {
	protected class CryptOutputStream extends OutputStream {

		private byte buffer[];

		private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

		private final OutputStream outputStreamToDecorate;
		private final byte timesealKey[] = "Timestamp (FICS) v1.0 - programmed by Henrik Gram."
				.getBytes();

		public CryptOutputStream(OutputStream outputstream) {
			buffer = new byte[10000];
			outputStreamToDecorate = outputstream;
		}

		@Override
		public void write(int i) throws IOException {
			if (i == 10) {
				synchronized (TimesealingSocket.this) {
					int resultLength = crypt(byteArrayOutputStream
							.toByteArray(), System.currentTimeMillis()
							- initialTime);
					outputStreamToDecorate.write(buffer, 0, resultLength);
					outputStreamToDecorate.flush();
					byteArrayOutputStream.reset();
				}
			} else {
				byteArrayOutputStream.write(i);
			}
		}

		private int crypt(byte stringToWriteBytes[], long timestamp) {
			int bytesInLength = stringToWriteBytes.length;
			System.arraycopy(stringToWriteBytes, 0, buffer, 0,
					stringToWriteBytes.length);
			buffer[bytesInLength++] = 24;
			byte abyte1[] = Long.toString(timestamp).getBytes();
			System.arraycopy(abyte1, 0, buffer, bytesInLength, abyte1.length);
			bytesInLength += abyte1.length;
			buffer[bytesInLength++] = 25;
			int j = bytesInLength;
			for (bytesInLength += 12 - bytesInLength % 12; j < bytesInLength;) {
				buffer[j++] = 49;
			}

			for (int k = 0; k < bytesInLength; k++) {
				buffer[k] = (byte) (buffer[k] | 0x80);
			}

			for (int i1 = 0; i1 < bytesInLength; i1 += 12) {
				byte byte0 = buffer[i1 + 11];
				buffer[i1 + 11] = buffer[i1];
				buffer[i1] = byte0;
				byte0 = buffer[i1 + 9];
				buffer[i1 + 9] = buffer[i1 + 2];
				buffer[i1 + 2] = byte0;
				byte0 = buffer[i1 + 7];
				buffer[i1 + 7] = buffer[i1 + 4];
				buffer[i1 + 4] = byte0;
			}

			int l1 = 0;
			for (int j1 = 0; j1 < bytesInLength; j1++) {
				buffer[j1] = (byte) (buffer[j1] ^ timesealKey[l1]);
				l1 = (l1 + 1) % timesealKey.length;
			}

			for (int k1 = 0; k1 < bytesInLength; k1++) {
				buffer[k1] = (byte) (buffer[k1] - 32);
			}

			buffer[bytesInLength++] = -128;
			buffer[bytesInLength++] = 10;
			return bytesInLength;
		}
	}


	protected CryptOutputStream cryptedOutputStream;

	private volatile long initialTime;

	private String initialTimesealString = null;

	private volatile Thread thread;

	private final TimesealPipe timesealPipe;

	public TimesealingSocket(InetAddress inetaddress, int i, String intialTimestampString) throws IOException {
		super(inetaddress, i);
		initialTimesealString = intialTimestampString;
		timesealPipe = new TimesealPipe(10000);
		init();
	}

	public TimesealingSocket(String s, int i, String intialTimestampString)
			throws IOException {
		super(s, i);
		timesealPipe = new TimesealPipe(10000);
		initialTimesealString = intialTimestampString;
		init();
	}

	@Override
	public void close() throws IOException {
		super.close();
		thread = null;
	}

	@Override
	public InputStream getInputStream() {
		return timesealPipe.getTimesealInputStream();
	}

	@Override
	public CryptOutputStream getOutputStream() throws IOException {
		return cryptedOutputStream;
	}

	public void run() {
		try {
			BufferedInputStream bufferedinputstream = new BufferedInputStream(
					super.getInputStream());
			TimesealOutputStream timesealOutputStream = timesealPipe.getTimesealOutputStream();
			//Timeseal 1
			//String timesealRequest = "\n\r[G]\n\r";
			//Timeseal 2
			String timesealRequest = "[G]\0";
			byte timesealRequestBytes[] = new byte[timesealRequest.length()];
			int i = 0;
			int j = 0;
			while (thread != null) {
				int k;
				if (i != 0) {
					k = timesealRequestBytes[0];
					if (k < 0) {
						k += 256;
					}
					for (int l = 0; l < i; l++) {
						timesealRequestBytes[l] = timesealRequestBytes[l + 1];
					}

					i--;
				} else {
					k = bufferedinputstream.read();
				}
				if (timesealRequest.charAt(j) == k) {
					if (++j == timesealRequest.length()) {
						j = 0;
						i = 0;
						synchronized (this) {
							getOutputStream().write("\0029\n".getBytes());
						}
					}
				} else if (j != 0) {
					timesealOutputStream
							.write((byte) timesealRequest.charAt(0));
					for (int i1 = 0; i1 < j - 1; i1++) {
						timesealRequestBytes[i1] = (byte) timesealRequest
								.charAt(i1 + 1);
						i++;
					}

					timesealRequestBytes[i++] = (byte) k;
					j = 0;
				} else {
					if (k < 0) {
						timesealOutputStream.close();
						return;
					}
					timesealOutputStream.write(k);
				}
			}
		} catch (Exception _ex) {
			try {
				cryptedOutputStream.close();
			} catch (IOException ioexception) {
				//LOG.debug("Failed to close PipedStream");
				ioexception.printStackTrace();
			}
		} finally {
			try {
				TimesealOutputStream timesealOutputStream = timesealPipe.getTimesealOutputStream();
				timesealOutputStream.close();
			} catch (Exception _ex) {
			}
		}
	}

	private void init() throws IOException {
		initialTime = System.currentTimeMillis();
		cryptedOutputStream = new CryptOutputStream(super.getOutputStream());
		writeInitialTimesealString();
		thread = new Thread(this, "Timeseal thread");
		thread.start();
	}

	private void writeInitialTimesealString() throws IOException {

		// BICS can't handle speedy connections so this slows it down a bit.
		try {
			Thread.sleep(100);
		} catch (InterruptedException ie) {
		}

		OutputStream outputstream = getOutputStream();
		synchronized (outputstream) {
			outputstream.write(initialTimesealString.getBytes());
			outputstream.write(10);
		}
	}

}
