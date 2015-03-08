package jwtc.android.timeseal.streams;

import java.io.IOException;
import java.io.InputStream;

class a extends InputStream
{
  private final c a;

  public int available()
  {
    //return this.a.jdMethod_case();
	return this.a.jdField_case();
  }

  public int read()
    throws IOException
  {
    //return this.a.jdMethod_for();
	  return this.a.jdField_for();
  }

  public int read(byte[] paramArrayOfByte, int paramInt1, int paramInt2)
    throws IOException
  {
    //return this.a.jdMethod_if(paramArrayOfByte, paramInt1, paramInt2);
	  return this.a.jdField_if(paramArrayOfByte, paramInt1, paramInt2);
  }

  public void close()
    throws IOException
  {
    //this.a.jdMethod_new();
	  this.a.jdField_new();
  }

  public a(c paramc)
  {
    this.a = paramc;
  }
}