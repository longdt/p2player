package com.solt.media.stream;

public interface HttpStatus {
	/**
	 * Some HTTP response status codes
	 */
	public static final String HTTP_OK = "200 OK";
	
	public static final String HTTP_PARTIALCONTENT = "206 Partial Content";
	
	public static final String HTTP_RANGE_NOT_SATISFIABLE = "416 Requested Range Not Satisfiable";
	
	public static final String HTTP_REDIRECT = "301 Moved Permanently";
	
	public static final String HTTP_NOTMODIFIED = "304 Not Modified";
	
	public static final String HTTP_FORBIDDEN = "403 Forbidden";
	
	public static final String HTTP_NOTFOUND = "404 Not Found";
	
	public static final String HTTP_BADREQUEST = "400 Bad Request";
	
	public static final String HTTP_INTERNALERROR = "500 Internal Server Error";
	
	public static final String HTTP_NOTIMPLEMENTED = "501 Not Implemented";

}
