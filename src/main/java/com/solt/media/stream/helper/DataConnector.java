package com.solt.media.stream.helper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.log4j.Logger;

public class DataConnector {
	private static final Logger logger = Logger.getLogger(DataConnector.class);
	private static final byte INIT_STREAM = 1;
	private static final byte GET_DATA = 2;
	private static final byte STATUS_OK = 0;
	private static final byte STATUS_GET_DATA = 4;
	private Socket socket;
	private String host;
	private int port;
	private DataInputStream in;
	private DataOutputStream out;
	private long fileId;
	private byte fileNo;
	private String fileName;
	private int pieceSize;
	private long fileOffset;
	private volatile boolean closed;

	public DataConnector(String host, int port) throws UnknownHostException,
			IOException {
		socket = new Socket(host, port);
		in = new DataInputStream(new BufferedInputStream(
				socket.getInputStream()));
		out = new DataOutputStream(new BufferedOutputStream(
				socket.getOutputStream()));
		this.host = host;
		this.port = port;
	}

	public synchronized boolean reconnect(boolean initData) {
		try {
			if (closed) {
				return false;
			} else if (socket != null) {
				socket.close();
			}
			socket = new Socket(host, port);
			in = new DataInputStream(new BufferedInputStream(
					socket.getInputStream()));
			out = new DataOutputStream(new BufferedOutputStream(
					socket.getOutputStream()));
			if (initData) {
				return init();
			}
			return true;
		} catch (IOException e) {
			logger.error("cant reconnect", e);
		}
		return false;
	}

	private boolean init() throws IOException {
		byte[] file = fileName.getBytes("UTF-8");
		out.write(INIT_STREAM);
		// write data length
		out.writeInt(21 + file.length);
		out.writeInt(pieceSize);
		out.writeLong(fileOffset);
		out.writeLong(fileId);
		out.writeByte(fileNo);
		out.write(file);
		out.flush();
		byte status = in.readByte();
		return status == STATUS_OK;
	}

	public synchronized boolean initData(String fileName, long fileId, byte fileNo,
			int pieceSize, long fileOffset) throws IOException {
		this.fileName = fileName;
		this.fileId = fileId;
		this.fileNo = fileNo;
		this.pieceSize = pieceSize;
		this.fileOffset = fileOffset;
		return init();
	}

	public synchronized boolean getData(int pieceIdx, int pieceOffset, int length, byte[] data)
			throws IOException {
		out.write(GET_DATA);
		out.writeInt(pieceIdx);
		out.writeInt(pieceOffset);
		out.writeInt(length);
		out.flush();
		byte status = in.readByte();
		if (status == STATUS_GET_DATA) {
			length = in.readInt();
			in.readFully(data, pieceOffset, length);
			return true;
		}
		return false;
	}

	public void close() {
		closed = true;
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
