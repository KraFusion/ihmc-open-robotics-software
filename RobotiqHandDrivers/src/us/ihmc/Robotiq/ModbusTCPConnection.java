package us.ihmc.Robotiq;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;

public class ModbusTCPConnection
{
	private Socket connection;
	private OutputStream outStream;
	private FilterInputStream inStream;
	private byte[] outBuffer = new byte[32];
	private byte[] inBuffer = new byte[32];
	private int packetCounter;
	
	public ModbusTCPConnection(String ip_address, int port) throws UnknownHostException, IOException
	{
		connection = new Socket(ip_address,port);
		outStream = connection.getOutputStream();
		inStream = new BufferedInputStream(connection.getInputStream());
		connection.setTcpNoDelay(true);
		packetCounter = 0;
	}
	
	public ModbusTCPConnection(String ip_address) throws UnknownHostException, IOException
	{
		this(ip_address, 502);
	}
	
	public void transcieve(int unitID, int functionCode, byte[] data) throws IOException
	{
		transcieve((byte)unitID, (byte)functionCode, data);
	}
	
	public byte[] transcieve(byte unitID, byte functionCode, byte[] data) throws IOException
	{		
		/*
		 *Header:
		 *Transaction ID: 2 bytes
		 *Protocol Identifier: 2 bytes
		 *Packet Length: 2 bytes
		 *
		 *Data:
		 *Unit ID: 1 byte
		 *Function Code: 1 byte
		 *Application Data: Up to 1452 Bytes
		*/
		
		packetCounter++;
		if(packetCounter > 0xFFFF)
			packetCounter = 0;
		
		byte[] packetLength = new byte[2];
		packetLength[0] = (byte)((2 + data.length) >> 8);	//bit shifting to align integer to byte stream
		packetLength[1] = (byte)(2 + data.length);
		
		byte[] transactionID = new byte[2];
		transactionID[0] = (byte)(packetCounter >> 8);		//bit shifting to align integer to byte stream
		transactionID[1] = (byte)packetCounter;
		
		outBuffer[0] = transactionID[0];
		outBuffer[1] = transactionID[1];
		outBuffer[2] = 0x00;
		outBuffer[3] = 0x00;				//Defining protocol as Modbus (0x0000)
		outBuffer[4] = packetLength[0];
		outBuffer[5] = packetLength[1];
		outBuffer[6] = unitID;
		outBuffer[7] = functionCode;
		
		System.out.println(data.length);
		for(int counter = 0; counter < data.length; counter++)
		{
			outBuffer[counter+8] = data[counter];
		}
		
		int outBytes = 8 + data.length; 
		outStream.write(outBuffer, 0, outBytes); //request
		outStream.flush();
		
		int inBytes = inStream.read(inBuffer, 0, 32); //reply
		
		if(inBytes < 9)
		{
			if(inBytes == 0)
			{
				//unexpected close of connection
				System.err.println("connection unexpectly closed");
				System.exit(-1);
			}
			else
			{
				//response too short
				System.err.println("response too short");
				System.exit(-1);
			}
		}
		
		//TODO:more error testing should be handled here
		//TODO:return proper length array of response here
		return Arrays.copyOfRange(inBuffer, 7, inBuffer.length);
		
	}
	
	public byte[] sendLiteral(byte[] data, int length) throws IOException
	{
		outStream.write(data, 0, length); //request
		outStream.flush();
		inStream.read(inBuffer, 0, 32); //reply
		return inBuffer;
	}
	
	public void close() throws IOException
	{
		connection.close();
	}
	
	
}
