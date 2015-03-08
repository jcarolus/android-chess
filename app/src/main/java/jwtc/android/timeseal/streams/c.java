package jwtc.android.timeseal.streams;

import java.io.IOException;
import java.io.InterruptedIOException;

public class c
{
  private static final int jdField_new = 2048;
  private final a jdField_else;
  private final b jdField_try;
  private volatile int jdField_do = 0;
  private byte[] jdField_for;
  private final boolean jdField_byte;
  private int jdField_goto = 0;
  private int jdField_char = 0;
  private boolean jdField_int = false;
  private boolean jdField_if = false;
  private Object a = new String("Write Lock for PipedStreams");
  private Object jdField_case = new String("Read Lock for PipedStream");

  public void jdField_if(int paramInt)
  {
    synchronized (this.jdField_case)
    {
      this.jdField_do = paramInt;
    }
  }

  public int jdField_do()
  {
    return this.jdField_do;
  }

  public a jdField_if()
  {
    return this.jdField_else;
  }

  public b jdField_byte()
  {
    return this.jdField_try;
  }

  synchronized int jdField_case()
  {
    if (this.jdField_if)
      return 0;
    return jdField_int();
  }

  private final int jdField_int()
  {
    if (this.jdField_char >= this.jdField_goto)
      return this.jdField_char - this.jdField_goto;
    return this.jdField_char + this.jdField_for.length - this.jdField_goto;
  }

  private final int a()
  {
    return this.jdField_for.length - jdField_int() - 1;
  }

  private final void jdField_do(int paramInt)
  {
    int i = paramInt < this.jdField_for.length ? this.jdField_for.length : paramInt;
    byte[] arrayOfByte = new byte[this.jdField_for.length + i];
    System.arraycopy(this.jdField_for, 0, arrayOfByte, 0, this.jdField_for.length);
    this.jdField_for = arrayOfByte;
  }

  synchronized void a(int paramInt)
    throws IOException
  {
    synchronized (this.a)
    {
      if ((this.jdField_if) || (this.jdField_int))
        throw new IOException("Stream closed");
      while (a() == 0)
        if (this.jdField_byte)
          jdField_do(1);
        else
          try
          {
            wait();
          }
          catch (InterruptedException localInterruptedException)
          {
            throw new InterruptedIOException();
          }
      if ((this.jdField_if) || (this.jdField_int))
        throw new IOException("Stream closed");
      this.jdField_for[(this.jdField_char++)] = ((byte)(paramInt & 0xFF));
      if (this.jdField_char == this.jdField_for.length)
        this.jdField_char = 0;
      notifyAll();
    }
  }

  synchronized void a(byte[] paramArrayOfByte, int paramInt1, int paramInt2)
    throws IOException
  {
    synchronized (this.a)
    {
      if ((this.jdField_if) || (this.jdField_int))
        throw new IOException("Stream closed");
      if ((this.jdField_byte) && (paramInt2 > a()))
        jdField_do(paramInt2 - a());
      while (paramInt2 > 0)
      {
        while (a() == 0)
          try
          {
            wait();
          }
          catch (InterruptedException localInterruptedException)
          {
            throw new InterruptedIOException();
          }
        int i = a();
        int j = paramInt2 > i ? i : paramInt2;
        int k = this.jdField_for.length - this.jdField_char >= j ? j : this.jdField_for.length - this.jdField_char;
        int m = j - k > 0 ? j - k : 0;
        System.arraycopy(paramArrayOfByte, paramInt1, this.jdField_for, this.jdField_char, k);
        System.arraycopy(paramArrayOfByte, paramInt1 + k, this.jdField_for, 0, m);
        paramInt1 += j;
        paramInt2 -= j;
        this.jdField_char = ((this.jdField_char + j) % this.jdField_for.length);
        notifyAll();
      }
    }
  }

  synchronized int jdField_for()
    throws IOException
  {
    synchronized (this.jdField_case)
    {
      if (this.jdField_if)
        throw new IOException("Stream closed");
      long l1 = System.currentTimeMillis();
      while (jdField_case() == 0)
      {
        if (this.jdField_int)
          return -1;
        long l2 = System.currentTimeMillis();
        if ((this.jdField_do != 0) && (l2 - l1 >= this.jdField_do))
          throw new InterruptedIOException();
        try
        {
          if (this.jdField_do == 0)
            wait();
          else
            wait(this.jdField_do + l2 - l1);
        }
        catch (InterruptedException localInterruptedException)
        {
          throw new InterruptedIOException();
        }
        if (this.jdField_if)
          throw new IOException("Stream closed");
      }
      int i = this.jdField_for[(this.jdField_goto++)];
      if (this.jdField_goto == this.jdField_for.length)
        this.jdField_goto = 0;
      notifyAll();
      return i < 0 ? i + 256 : i;
    }
  }

  synchronized int jdField_if(byte[] paramArrayOfByte, int paramInt1, int paramInt2)
    throws IOException
  {
    synchronized (this.jdField_case)
    {
      if (this.jdField_if)
        throw new IOException("Stream closed");
      long l1 = System.currentTimeMillis();
      while (jdField_case() == 0)
      {
        if (this.jdField_int)
          return -1;
        long l2 = System.currentTimeMillis();
        if ((this.jdField_do != 0) && (l2 - l1 >= this.jdField_do))
          throw new InterruptedIOException();
        try
        {
          if (this.jdField_do == 0)
            wait();
          else
            wait(this.jdField_do + l2 - l1);
        }
        catch (InterruptedException localInterruptedException)
        {
          throw new InterruptedIOException();
        }
        if (this.jdField_if)
          throw new IOException("Stream closed");
      }
      int i = jdField_case();
      int j = paramInt2 > i ? i : paramInt2;
      int k = this.jdField_for.length - this.jdField_goto > j ? j : this.jdField_for.length - this.jdField_goto;
      int m = j - k > 0 ? j - k : 0;
      System.arraycopy(this.jdField_for, this.jdField_goto, paramArrayOfByte, paramInt1, k);
      System.arraycopy(this.jdField_for, 0, paramArrayOfByte, paramInt1 + k, m);
      this.jdField_goto = ((this.jdField_goto + j) % this.jdField_for.length);
      notifyAll();
      return j;
    }
  }

  synchronized void jdField_try()
  {
    if (this.jdField_int)
      throw new IllegalStateException("Already closed");
    this.jdField_int = true;
    notifyAll();
  }

  synchronized void jdField_new()
  {
    if (this.jdField_if)
      throw new IllegalStateException("Already closed");
    this.jdField_if = true;
    notifyAll();
  }

  public c()
  {
    this(2048, false);
  }

  public c(int paramInt)
  {
    this(paramInt, false);
  }

  public c(boolean paramBoolean)
  {
    this(2048, paramBoolean);
  }

  public c(int paramInt, boolean paramBoolean)
  {
    if (paramInt <= 0)
      throw new IllegalArgumentException("The buffer size must be a positive integer");
    this.jdField_else = new a(this);
    this.jdField_try = new b(this);
    this.jdField_byte = paramBoolean;
    this.jdField_for = new byte[paramInt];
  }
}