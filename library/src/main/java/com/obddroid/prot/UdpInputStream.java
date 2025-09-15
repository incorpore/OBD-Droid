package com.obddroid.prot;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.logging.Logger;

/**
 * Wrapper work class to create InputStream from a Datagram Socket
 *
 
 */
class UdpInputStream extends PipedInputStream
{

	/** receive buffer */
	private final char[] buffer = new char[16384];
	/** receiving datagram packet */
	private DatagramPacket packet;
	/** Datagram socket object to be listened to */
	private DatagramSocket socket = null;
	/** the pipe which connects socket output to buffer input */
	private PipedOutputStream out = null;
	/** the worker thread to read the socket */
	private Thread readerThread = null;
	/** Logging Object */
	private static final Logger log = Logger.getLogger("STREAM");
	private long charsReceived = 0;
	private long startTime = 0;

	/**
	 * default constructor
	 */
	private UdpInputStream()
	{
		super();
	}

	/**
	 * construct with existing DatagramSocket
	 */
	public UdpInputStream(DatagramSocket newSocket)
	{
		this();
		setSocket(newSocket);
	}

	/**
	 * get current datagram socket
	 */
	public DatagramSocket getSocket()
	{
		return (socket);
	}

	private void closeItAll()
		throws IOException
	{
		// terminate and destroy current reader thread (if available)
		if (readerThread != null)
		{
			readerThread.interrupt();
	/*
	try
    {
    readerThread.join();
    }
    catch(InterruptedException e)
    {
    log.severe(e);
    }
     */
		}
		if (socket != null)
		{
			socket.close();
		}
		if (out != null)
		{
			out.close();
		}
	}

	/**
	 * close the UdpInputStream and release all associated resources
	 */
	public void close()
		throws IOException
	{
		log.info("Closing UdpInputStream");
		closeItAll();
		super.close();
	}

	/**
	 * set datagram socket for data reading
	 */
	private void setSocket(DatagramSocket newSocket)
	{
		// only do something if the socket really changes
		if (newSocket != socket)
		{
			try
			{
				closeItAll();
				log.info("Opening UdpInputStream");
				out = new PipedOutputStream(this);
				// set socket to the new one
				socket = newSocket;
				// create a new reader thread ...
				readerThread = new Thread()
				{

					public void run()
					{
						log.info("UdpReader started");
						try
						{
							while (!isInterrupted())
							{
								packet.setLength(buffer.length);
								socket.receive(packet);
								charsReceived += packet.getLength();
								out.write(packet.getData(), 0, packet.getLength());
								log.fine("RX:" + new String(packet.getData()));
							}
						} catch (IOException e)
						{
							log.severe(e.toString());
						}
						log.info("UdpReader finished");
					}
				};
				// mark socket staring time
				startTime = System.currentTimeMillis();
				// set number of chars received to zero
				charsReceived = 0;
				// and start listening ...
				readerThread.start();
			} catch (IOException e)
			{
				log.severe(e.toString());
			}
		}
	}
	/**
	 * show receive statistics on selectedPort close
	 * this is activated for testing only
	 *
	 */
  /*
  public void close()
  {
  try
  {
  long duration = (System.currentTimeMillis()-startTime)/1000;
  System.out.println( "RX: "+String.valueOf(charsReceived)
  +" Time: "+String.valueOf(duration)
  +" Thpt: "+String.valueOf(charsReceived/duration));
  super.close();
  }
  catch(IOException e)
  {
  log.severe(e);
  }
  }
   */
}
