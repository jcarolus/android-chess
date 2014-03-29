package jwtc.android.timeseal;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

import jwtc.android.timeseal.streams.b;
import jwtc.android.timeseal.streams.c;

import android.util.Log;

public class TimesealingSocket extends Socket
  implements Runnable
{
  private static final String jdField_int = "TIMESTAMP|FICS timeseal implementation by Alexander Maryanovsky|" + System.getProperty("java.vendor") + " " + System.getProperty("java.version") + ", " + System.getProperty("os.name") + " " + System.getProperty("os.version") + "|";
  private volatile long jdField_for;
  private final c jdField_do = new c(10000);
  private OutputStream jdField_if;
  private volatile Thread a;

  private final void jdField_if()
    throws IOException
  {
    this.jdField_for = System.currentTimeMillis();
    this.jdField_if = new a(super.getOutputStream());
    a();
    this.a = new Thread(this, "Timeseal thread");
    this.a.start();
  }

  private final void a()
    throws IOException
  {
    OutputStream localOutputStream = getOutputStream();
    synchronized (localOutputStream)
    {
      localOutputStream.write(jdField_int.getBytes());
      localOutputStream.write(10);
    }
  }

  public void run()
  {
    try
    {
      BufferedInputStream localBufferedInputStream = new BufferedInputStream(super.getInputStream());
      //b localb2 = this.jdField_do.jdMethod_byte();
      b localb2 = this.jdField_do.jdField_byte();
      String str = "\n\r[G]\n\r";
      byte[] arrayOfByte = new byte[str.length()];
      int i = 0;
      int j = 0;
      while (this.a != null)
      {
        int k;
        if (i != 0)
        {
          k = arrayOfByte[0];
          if (k < 0)
            k += 256;
          for (int m = 0; m < i; m++)
            arrayOfByte[m] = arrayOfByte[(m + 1)];
          i--;
        }
        else
        {
          k = localBufferedInputStream.read();
        }
        if (str.charAt(j) == k)
        {
          j++;
          if (j == str.length())
          {
            j = 0;
            i = 0;
            synchronized (this)
            {
              getOutputStream().write("\0029\n".getBytes());
            }
          }
        }
        else if (j != 0)
        {
          localb2.write((byte)str.charAt(0));
          for (int n = 0; n < j - 1; n++)
          {
            arrayOfByte[n] = ((byte)str.charAt(n + 1));
            i++;
          }
          arrayOfByte[(i++)] = ((byte)k);
          j = 0;
        }
        else
        {
          if (k < 0)
            return;
          localb2.write(k);
        }
      }
    }
    catch (IOException localIOException2)
    {
      try
      {
        this.jdField_if.close();
      }
      catch (IOException localIOException3)
      {
        System.err.println("Failed to close PipedStream");
        localIOException3.printStackTrace();
      }
    }
    finally
    {
      try
      {
        //b localb1 = this.jdField_do.jdMethod_byte();
    	b localb1 = this.jdField_do.jdField_byte();
        localb1.close();
      }
      catch (IOException localIOException1)
      {
      }
    }
  }

  public OutputStream getOutputStream()
    throws IOException
  {
    return this.jdField_if;
  }

  public InputStream getInputStream()
  {
    return this.jdField_do.jdField_if();
  }

  public void close()
    throws IOException
  {
    super.close();
    this.a = null;
  }

  public TimesealingSocket(InetAddress paramInetAddress, int paramInt)
    throws IOException
  {
    super(paramInetAddress, paramInt);
    jdField_if();
    
    Log.i("TimesealingSocket", "initialized");
  }

  public TimesealingSocket(String paramString, int paramInt)
    throws IOException
  {
    super(paramString, paramInt);
    jdField_if();
  }

  private class a extends OutputStream
  {
    private final byte[] jdField_for = "Timestamp (FICS) v1.0 - programmed by Henrik Gram.".getBytes();
    private byte[] a = new byte[10000];
    private final OutputStream jdField_do;
    private final ByteArrayOutputStream jdField_if = new ByteArrayOutputStream();
    private long jdField_int = 0L;

    public void write(int paramInt)
      throws IOException
    {
      synchronized (TimesealingSocket.this)
      {
        if (paramInt == 10)
        {
          byte[] arrayOfByte = this.jdField_if.toByteArray();
          long l = System.currentTimeMillis() - TimesealingSocket.this.jdField_for;
          if (l <= this.jdField_int)
            l = this.jdField_int + 1L;
          int i = a(arrayOfByte, l);
          this.jdField_do.write(this.a, 0, i);
          this.jdField_do.flush();
          this.jdField_if.reset();
          this.jdField_int = l;
        }
        else
        {
          this.jdField_if.write(paramInt);
        }
      }
    }

    private final int a(byte[] paramArrayOfByte, long paramLong)
    {
      int i = paramArrayOfByte.length;
      System.arraycopy(paramArrayOfByte, 0, this.a, 0, paramArrayOfByte.length);
      this.a[(i++)] = 24;
      byte[] arrayOfByte = Long.toString(paramLong).getBytes();
      System.arraycopy(arrayOfByte, 0, this.a, i, arrayOfByte.length);
      i += arrayOfByte.length;
      this.a[(i++)] = 25;
      int j = i;
      i += 12 - i % 12;
      while (j < i)
        this.a[(j++)] = 49;
      for (j = 0; j < i; j++)
        this.a[j] = ((byte)(this.a[j] | 0x80));
      for (j = 0; j < i; j += 12)
      {
    	// k = this.a[(j + 11)];
        byte k = this.a[(j + 11)];
        
        this.a[(j + 11)] = this.a[j];
        this.a[j] = k;
        k = this.a[(j + 9)];
        this.a[(j + 9)] = this.a[(j + 2)];
        this.a[(j + 2)] = k;
        k = this.a[(j + 7)];
        this.a[(j + 7)] = this.a[(j + 4)];
        this.a[(j + 4)] = k;
      }
      int k = 0;
      for (j = 0; j < i; j++)
      {
        this.a[j] = ((byte)(this.a[j] ^ this.jdField_for[k]));
        k = (k + 1) % this.jdField_for.length;
      }
      for (j = 0; j < i; j++)
        this.a[j] = ((byte)(this.a[j] - 32));
      this.a[(i++)] = -128;
      this.a[(i++)] = 10;
      return i;
    }

    public a(OutputStream arg2)
    {
      //Object localObject;
      //this.jdField_do = localObject;
      this.jdField_do = arg2;
    }
  }
}