package com.solt.media.stream;

import java.util.HashMap;
import java.util.Map;

public class HttpRequest {
	public static final int METHOD_GET = 1;
	public static final int METHOD_HEAD = 2;
	
	private String uri;
	private int method;
	private Map<String, String> params;
	private Map<String, String> headers;
	
	public HttpRequest() {
		this(null, 0);
	}
	
	public HttpRequest(String uri, int method) {
		this(uri, method, new HashMap<String, String>(), new HashMap<String, String>());
	}
	
	public HttpRequest(String uri, int method, Map<String, String> params, Map<String, String> headers) {
		this.uri = uri;
		this.method = method;
		this.params = params;
		this.headers = headers;
	}
	
	public String getParam(String key) {
		return params.get(key);
	}
	
	public void setParam(String key, String value) {
		params.put(key, value);
	}
	
	public String getHeader(String key) {
		return headers.get(key);
	}
	
	public void setHeader(String key, String value) {
		headers.put(key, value);
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public int getMethod() {
		return method;
	}

	public void setMethod(int method) {
		this.method = method;
	}

	public Map<String, String> getParams() {
		return params;
	}

	public void setParams(Map<String, String> params) {
		this.params = params;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}
}
