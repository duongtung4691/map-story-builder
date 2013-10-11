package com.esri.apl.mapStoryBuilder.utils;

public class FileException {
	public String file;
	public Exception exception;

	public FileException(String file, Exception exception) {
		this.exception = exception;
		this.file = file;
	}

}
