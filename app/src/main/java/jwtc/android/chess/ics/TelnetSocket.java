package jwtc.android.chess.ics;

import java.net.*;
import java.io.*;

import android.util.Log;

//public class TelnetSocket extends Socket    
public class TelnetSocket extends jwtc.android.timeseal.TimesealingSocket
{
	protected static byte[] _inBytes;
	protected static byte[] _outBytes;

	TelnetSocket (String host, int port) throws UnknownHostException, IOException{
		//super (host, port);
		super(InetAddress.getByName(host), port);
		//super (host, port, "TIMESTAMP|openseal|Running on Android|");
		
		_inBytes = new byte[2048];
		_outBytes = new byte[128];
		
		/// own openseal
		//String hello = "TIMESTAMP|openseal|Running on Android|";
		//sendString(hello);
	}
	
	public String readString(){
		String data = null;
		try	{
			InputStream is = getInputStream();
			if(is != null){
				int num = is.read (_inBytes);
				if(num > 0){
					data = new String(_inBytes, 0, num);
				}
			}
		} catch (Exception e)	{
			Log.e("TelnetSocket", "readString " + e.toString());
			return null;
		}
		return data;
	}
	
	public boolean sendString (String data){

		for (int i = 0; i < data.length(); i++)
		{
			_outBytes[i] = (byte)data.charAt(i);
		}
		try	
		{
			getOutputStream().write(_outBytes, 0, data.length());
			getOutputStream().flush();
			//Log.i("TelnetSocket", "sendString: " + data);
			return true;
		}
		catch (Exception e) 
		{
			Log.e("TelnetSocket", "sendString: " + e.toString());
			return false;
		}
	}
	/*
	//////
	protected StringBuffer key = new StringBuffer("Timestamp (FICS) v1.0 - programmed by Henrik Gram.");
	
	protected void SC(StringBuffer sb, int A, int B){
		//#define SC(A,B) s[B]^=s[A]^=s[B],s[A]^=s[B]
		// s[A] ^= S[B]
		sb.setCharAt(A, (char)(sb.charAt(A) ^ sb.charAt(B)));
		// s[B] ^= S[A]
		sb.setCharAt(B, (char)(sb.charAt(B) ^ sb.charAt(A)));
		// s[A]^=s[B]
		sb.setCharAt(A, (char)(sb.charAt(A) ^ sb.charAt(B)));
	}
	
	protected void crypt(StringBuffer sb){
		int n;
		sb.append((byte)0x18).append(System.currentTimeMillis()).append((byte)0x19);
		int l = sb.length();
		
		for(;(l%12 > 0);l++){
			sb.append('1');
		}
		
		//for(n=0;n<l;n+=12)
     	//	SC(n,n+11), SC(n+2,n+9), SC(n+4,n+7);
		for(n=0;n<l;n+=12){
			SC(sb, n,n+11); 
			SC(sb, n+2,n+9); 
			SC(sb, n+4,n+7);
		}
		for(n=0;n<l;n++){
			//for(n=0;n<l;n++)
			//s[n]=((s[n]|0x80)^key[n%50])-32;

			sb.setCharAt(n, (char)(((sb.charAt(n)|0x80)^key.charAt(n%50))-32));
		}
		
		sb.append((byte)0x80).append((byte)0x0A);

		Log.i("crypt", sb.toString());
	}
	
	// will be sendString
	boolean sendString(String data)
	{
		StringBuffer buff = new StringBuffer(data);
		crypt(buff);
		for (int i = 0; i < buff.length(); i++)
		{
			_outBytes[i] = (byte)buff.charAt(i);
		}
		Log.i("sendString", data);
		try	
		{
			getOutputStream().write(_outBytes, 0, buff.length());
			getOutputStream().flush();
			//Log.i("TelnetSocket", "sendString: " + data);
			return true;
		}
		catch (Exception e) 
		{
			Log.e("TelnetSocket", "sendString: " + e.toString());
			return false;
		}
	}

	// will be readString
	public String readString()
	{
		String data = null;
		try	{
			InputStream is = getInputStream();
			if(is != null){
				int num = is.read (_inBytes);
				
				if(num > 0){
					if(num < 5){
						data = new String(_inBytes, 0, num);
						int extraNum;
						while(num < 5){
							extraNum = is.read (_inBytes);
							if(extraNum > 0){
								data += new String(_inBytes, 0, extraNum);
								num += extraNum;
							} else {
								return data;
							}
						}
					} else {
						data = new String(_inBytes, 0, num);
					}
					
					Log.i("readString", "data " + data);
					
					// data contains at least 5 chars
					if(data.startsWith("[G]\n\r")){
						Log.i("readString", "matched [G]!");
						sendString("\0029\n");
						
						return data.substring(5);
						
					} 
					return data;
				}
			}
		} catch (Exception e)	{
			Log.e("TelnetSocket", "readString " + e.toString());
			return null;
		}

		return null;
	}
	*/
}

/*
#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>
#include <time.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <string.h>
#include <sys/select.h>

#define BSIZE 1024

char *key="Timestamp (FICS) v1.0 - programmed by Henrik Gram.";
//char hello[100]="TIMESTAMP|javaboard|Blackdown Java-Linux Team 1.3.1 Linux|";
char hello[100]="TIMESTAMP|openseal|Running on an operating system|";

int crypt(char *s,int l)
{
	int n;
	struct timeval tv;
	s[l++]='\x18';
	gettimeofday(&tv,NULL);
	l+=sprintf(&s[l],"%ld",(tv.tv_sec%10000)*1000+tv.tv_usec/1000);
	s[l++]='\x19';
	for(;l%12;l++)
		s[l]='1';
#define SC(A,B) s[B]^=s[A]^=s[B],s[A]^=s[B]
	for(n=0;n<l;n+=12)
		SC(n,n+11), SC(n+2,n+9), SC(n+4,n+7);
	for(n=0;n<l;n++)
		s[n]=((s[n]|0x80)^key[n%50])-32;
	s[l++]='\x80';
	s[l++]='\x0a';
	return l;
}

int makeconn(char *hostname,int port)
{
	int sockfd;
	struct hostent* host_info;
	struct sockaddr_in address;
	long host_address;
	sockfd=socket(AF_INET,SOCK_STREAM,IPPROTO_TCP);
	if(sockfd==-1) {
		perror(NULL);
		exit(1);
	}
	host_info=gethostbyname(hostname);
	if(host_info==NULL) {
		perror(NULL);
		exit(1);
	}
	memcpy(&host_address,host_info->h_addr,host_info->h_length);
	address.sin_addr.s_addr=host_address;
	address.sin_port=htons(port);
	address.sin_family=AF_INET;
	if(-1==connect(sockfd,(struct sockaddr*)&address,sizeof(address)));
	return sockfd;
}

void mywrite(int fd,char *buff,int n)
{
	if(write(fd,buff,n)==-1) {
		perror(NULL);
		exit(1);
	}
}

void sendtofics(int fd, char *buff, int *rd)
{
	static int c=0;
	for(;c<*rd;c++)
		if(buff[c]=='\n') {
			char ffub[BSIZE+20];
			int k;
			memcpy(ffub,buff,c);
			k=crypt(ffub,c);
			mywrite(fd,ffub,k);
			for(c++,k=0;c<*rd;c++,k++)
				buff[k]=buff[c];
			*rd=k;
			c=-1;
		}
}

void getfromfics(int fd, char *buff, int *rd)
{
	static int c=0;
	int n,m;
	while(*rd>0) {
		if(!strncmp(buff,"[G]\n\r",*rd<5?*rd:5))
			if(*rd<5) break;
			else {
				char reply[20]="\x2""9";
				n=crypt(reply,2);
				mywrite(fd,reply,n);
				for(n=5;n<*rd;n++)
					buff[n-5]=buff[n];
				*rd-=5;
				continue;
			}
		for(n=0;n<*rd && buff[n]!='\r';n++);
		if(n<*rd) n++;
		mywrite(1,buff,n);
		for(m=n;m<*rd;m++)
			buff[m-n]=buff[m];
		*rd-=n;
	}
}

int main(int argc, char **argv)
{
	char *hostname;
	int port,fd,n;
	if(argc==3) {
		hostname=argv[1];
		port=atoi(argv[2]);
	} else if(argc==2) {
		hostname=argv[1];
		port=5000;
	} else {
		fprintf(stderr,"Usage:\n    %s ICS-host [ICS-port]\n",argv[0]);
		return 1;
	}
	fd=makeconn(hostname,port);
	n=crypt(hello,strlen(hello));
	mywrite(fd,hello,n);
	for(;;) {
		fd_set fds;
		FD_ZERO(&fds);
		FD_SET(0,&fds);
		FD_SET(fd,&fds);
		select(fd+1,&fds,NULL,NULL,NULL);
		if(FD_ISSET(0,&fds)) {
			static int rd=0;
			static char buff[BSIZE];
			rd+=n=read(0,buff+rd,BSIZE-rd);
			if(!n) {
				fprintf(stderr,"Gasp!\n");
				exit(0);
			}
			if(n==-1) {
				perror(NULL);
				exit(1);
			}
			sendtofics(fd,buff,&rd);
			if(rd==BSIZE) {
				fprintf(stderr,"Line tooooo long! I die!\n");
				exit(1);
			}
		}
		if(FD_ISSET(fd,&fds)) {
			static int rd=0;
			static char buff[BSIZE];
			rd+=n=read(fd,buff,BSIZE-rd);
			if(!n) {
				fprintf(stderr,"Connection closed by ICS\n");
				exit(0);
			}
			if(n==-1) {
				perror(NULL);
				exit(1);
			}
			getfromfics(fd,buff,&rd);
			if(rd==BSIZE) {
				fprintf(stderr,"Receive buffer full! Your ICS killed me!\n");
				exit(1);
			}
		}
	}
}
*/


