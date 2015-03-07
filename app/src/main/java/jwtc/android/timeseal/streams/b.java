package jwtc.android.timeseal.streams;

import java.io.IOException;
import java.io.OutputStream;

public class b extends OutputStream
{
  private final c a;

  public void write(int paramInt)
    throws IOException
  {
    this.a.a(paramInt);
  }

  public void write(byte[] paramArrayOfByte, int paramInt1, int paramInt2)
    throws IOException
  {
    this.a.a(paramArrayOfByte, paramInt1, paramInt2);
  }

  public void close()
    throws IOException
  {
    //this.a.jdMethod_try();
	this.a.jdField_try();
    
  }

  public b(c paramc)
  {
    this.a = paramc;
  }
}