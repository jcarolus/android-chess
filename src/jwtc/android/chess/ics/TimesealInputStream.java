package jwtc.android.chess.ics;

import java.io.IOException;
import java.io.InputStream;

/**
 * This code was reverse engineered from the JIN project. JIN is a gpled
 * project. Its url can be found here: http://www.jinchess.com/
 */
class TimesealInputStream extends InputStream {

	private final TimesealPipe a;

	public TimesealInputStream(TimesealPipe c1) {
		a = c1;
	}

	public int available() {
		return a._mthcase();
	}

	public void close() throws IOException {
		a._mthnew();
	}

	public int read() throws IOException {
		return a._mthfor();
	}

	public int read(byte abyte0[], int i, int j) throws IOException {
		return a._mthif(abyte0, i, j);
	}
}
