package com.weblab.rplace.weblab.rplace.core.utilities.results;

public class Result {
	private boolean success;
	private String message;
	
	public boolean isSuccess() {
		return success;
	}
	
	public String getMessage() {
		return message;
	}
	
	public Result(boolean success, String message) {
		this.success = success;
		this.message = message;
	}
	
	public Result(boolean success) {
		this.success = success;
	}
}
