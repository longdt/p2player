package com.solt.media.stream;

import java.util.HashMap;
import java.util.Map;

public class TorrentRequest {

	/**
	 * HTTP status code after processing, e.g. "200 OK", HTTP_OK
	 */
	private String status;

	/**
	 * MIME type of content, e.g. "text/html"
	 */
	private String mimeType;

	private long dataLength;

	private String hashCode;
	
	private int index;

	private long transferOffset;
	
	private String message;

	/**
	 * Headers for the HTTP response. Use addHeader() to add lines.
	 */
	private Map<String, String> headers = new HashMap<String, String>();
	/**
	 * Basic constructor.
	 */
	public TorrentRequest(String status, String mimeType, String hashCode, int index,
			long transferOffset, long dataLength) {
		this.status = status;
		this.mimeType = mimeType;
		this.hashCode = hashCode;
		this.index = index;
		this.transferOffset = transferOffset;
		this.dataLength = dataLength;
	}

	public TorrentRequest(String status, String mimeType, String message) {
		this.status = status;
		this.mimeType =  mimeType;
		this.message = message;
	}

	/**
	 * Adds given line to the header.
	 */
	public void setHeader(String name, String value) {
		headers.put(name, value);
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getMimeType() {
		return mimeType;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public String getHashCode() {
		return hashCode;
	}

	public void setHashCode(String hashCode) {
		this.hashCode = hashCode;
	}

	public long getDataLength() {
		return dataLength;
	}

	public void setDataLength(long dataLength) {
		this.dataLength = dataLength;
	}

	public long getTransferOffset() {
		return transferOffset;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}
	
	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}
	
	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	/**
	 * @return the index
	 */
	public int getIndex() {
		return index;
	}

	/**
	 * @param index the index to set
	 */
	public void setIndex(int index) {
		this.index = index;
	}
}
