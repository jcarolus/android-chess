package jwtc.android.chess.ics;

import java.io.IOException;
import java.io.InterruptedIOException;

/**
 * This code was reverse engineered from the JIN project. JIN is a gpled
 * project. Its url can be found here: http://www.jinchess.com/
 */
public class TimesealPipe {

	private int _fldbyte;

	private int _fldchar;

	private volatile int _flddo;

	private final byte buffer[];

	private boolean closedFlag;

	private boolean closedFlag2;

	private Object readLock;

	private final TimesealInputStream timesealInputStream;

	private final TimesealOutputStream timesealOutputStream;

	private Object writeLock;

	public TimesealPipe() {
		this(2048);
	}

	public TimesealPipe(int i) {
		_flddo = 0;
		_fldchar = 0;
		_fldbyte = 0;
		closedFlag = false;
		closedFlag2 = false;
		writeLock = "Write Lock for PipedStreams";
		readLock = "Read Lock for PipedStream";
		timesealInputStream = new TimesealInputStream(this);
		timesealOutputStream = new TimesealOutputStream(this);
		buffer = new byte[i];
	}

	public int _mthdo() {
		return _flddo;
	}

	public void _mthif(int i) {
		synchronized (readLock) {
			_flddo = i;
		}
	}

	public TimesealInputStream getTimesealInputStream() {
		return timesealInputStream;
	}

	public TimesealOutputStream getTimesealOutputStream() {
		return timesealOutputStream;
	}

	synchronized int _mthcase() {
		if (closedFlag2) {
			return 0;
		} else {
			return _mthint();
		}
	}

	synchronized int _mthfor() throws IOException {
		synchronized (readLock) {
			if (closedFlag2) {
				throw new IOException("Stream closed");
			}
			long l = System.currentTimeMillis();
			while (_mthcase() == 0) {
				if (closedFlag) {
					byte byte0 = -1;
					return byte0;
				}
				long currentTime = System.currentTimeMillis();
				if (_flddo != 0 && currentTime - l >= _flddo) {
					throw new InterruptedIOException();
				}
				try {
					if (_flddo == 0) {
						wait();
					} else {
						wait(_flddo + currentTime - l);
					}
				} catch (InterruptedException _ex) {
					throw new InterruptedIOException();
				}
				if (closedFlag2) {
					throw new IOException("Stream closed");
				}
			}
			byte byte1 = buffer[_fldchar++];
			if (_fldchar == buffer.length) {
				_fldchar = 0;
			}
			notifyAll();
			int i = byte1 >= 0 ? (int) byte1 : byte1 + 256;
			return i;
		}
	}

	synchronized int _mthif(byte abyte0[], int i, int j) throws IOException {
		synchronized (readLock) {
			if (closedFlag2) {
				throw new IOException("Stream closed");
			}
			long currentTimeMillis = System.currentTimeMillis();
			while (_mthcase() == 0) {
				if (closedFlag) {
					byte byte0 = -1;
					return byte0;
				}
				long l1 = System.currentTimeMillis();
				if (_flddo != 0 && l1 - currentTimeMillis >= _flddo) {
					throw new InterruptedIOException();
				}
				try {
					if (_flddo == 0) {
						wait();
					} else {
						wait(_flddo + l1 - currentTimeMillis);
					}
				} catch (InterruptedException _ex) {
					throw new InterruptedIOException();
				}
				if (closedFlag2) {
					throw new IOException("Stream closed");
				}
			}
			int i1 = _mthcase();
			int j1 = j <= i1 ? j : i1;
			int k1 = buffer.length - _fldchar <= j1 ? buffer.length - _fldchar
					: j1;
			int i2 = j1 - k1 <= 0 ? 0 : j1 - k1;
			System.arraycopy(buffer, _fldchar, abyte0, i, k1);
			System.arraycopy(buffer, 0, abyte0, i + k1, i2);
			_fldchar = (_fldchar + j1) % buffer.length;
			notifyAll();
			int k = j1;
			return k;
		}
	}

	synchronized void _mthnew() {
		if (closedFlag2) {
			throw new IllegalStateException("Already closed");
		} else {
			closedFlag2 = true;
			notifyAll();
			return;
		}
	}

	synchronized void _mthtry() {
		if (closedFlag) {
			throw new IllegalStateException("Already closed");
		} else {
			closedFlag = true;
			notifyAll();
			return;
		}
	}

	synchronized void a(int i) throws IOException {
		synchronized (writeLock) {
			if (closedFlag2 || closedFlag) {
				throw new IOException("Stream closed");
			}
			while (duno() == 0) {
				try {
					wait();
				} catch (InterruptedException _ex) {
					throw new InterruptedIOException();
				}
			}
			if (closedFlag2 || closedFlag) {
				throw new IOException("Stream closed");
			}
			buffer[_fldbyte++] = (byte) (i & 0xff);
			if (_fldbyte == buffer.length) {
				_fldbyte = 0;
			}
			notifyAll();
		}
	}

	synchronized void write(byte bytes[], int i, int j) throws IOException {
		synchronized (writeLock) {
			if (closedFlag2 || closedFlag) {
				throw new IOException("Stream closed");
			}
			while (j > 0) {
				while (duno() == 0) {
					try {
						wait();
					} catch (InterruptedException _ex) {
						throw new InterruptedIOException();
					}
				}
				int k = duno();
				int l = j <= k ? j : k;
				int i1 = buffer.length - _fldbyte < l ? buffer.length
						- _fldbyte : l;
				int j1 = l - i1 <= 0 ? 0 : l - i1;
				System.arraycopy(bytes, i, buffer, _fldbyte, i1);
				System.arraycopy(bytes, i + i1, buffer, 0, j1);
				i += l;
				j -= l;
				_fldbyte = (_fldbyte + l) % buffer.length;
				notifyAll();
			}
		}
	}

	private int _mthint() {
		if (_fldbyte >= _fldchar) {
			return _fldbyte - _fldchar;
		} else {
			return _fldbyte + buffer.length - _fldchar;
		}
	}

	private int duno() {
		return buffer.length - _mthint() - 1;
	}
}
