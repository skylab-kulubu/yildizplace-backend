package com.weblab.rplace.weblab.rplace.core.utilities.results;

public class DataResult<T> extends Result {
	
	private T data;
	
	public T getData() {
		return data;
	}

	public DataResult(T data, boolean success, String message) {
		super(success, message);
		this.data = data;
		
	}
	
	public DataResult(T data, boolean success) {
		super(success);
		this.data = data;
	}

}
