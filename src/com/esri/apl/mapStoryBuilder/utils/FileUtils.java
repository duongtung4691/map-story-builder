package com.esri.apl.mapStoryBuilder.utils;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import android.content.Context;
import android.util.Log;

public class FileUtils {
	private static final String TAG = "MapStoryBuilder.FileUtils";

	/** Creates a new zip file, overwriting the old one and any entries in it **/
	public static void addDirectoryToZip(File directory, File zipfile) throws IOException {
		addDirectoryToZip(directory, zipfile, null);
	}
	/** Creates a new zip file, overwriting the old one and any entries in it **/
	public static void addDirectoryToZip(File directory, File zipFile, FileFilter filter) throws IOException {
		OutputStream out = new FileOutputStream(zipFile);
		ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(out));
		
		addDirectoryToZip(directory, zout, filter);
		
		zout.close();
	}
	/** Adds to an already open zip output stream **/
	public static void addDirectoryToZip(File directory, ZipOutputStream zout, FileFilter filter) throws IOException {
		addDirectoryToZip(directory, zout, filter, false);
	}
	/** Adds to an already open zip output stream **/
	public static void addDirectoryToZip(File topDirectory, ZipOutputStream zout, FileFilter filter, boolean createTopDirectoryEntry) throws IOException {
		File directory;
		URI base = topDirectory.toURI();
		LinkedList<File> queue = new LinkedList<File>();
		queue.addFirst(topDirectory);
//		if (createTopDirectoryEntry) {
//			zout.putNextEntry(new ZipEntry(topDirectory.getName()));
//			zout.closeEntry();
//		}
		while (!queue.isEmpty()) {
			directory = queue.remove();
			
			for (File kid : directory.listFiles(filter)) {
				String name = base.relativize(kid.toURI()).getPath();
				if (createTopDirectoryEntry)
					name = topDirectory.getName() + File.separator + name;
				
				zout.putNextEntry(new ZipEntry(name));
				if (kid.isDirectory()) {
					queue.addFirst(kid);
				} else {
					copy(kid, zout);
				}
				zout.closeEntry();
			}
		}
	}

	public static void addSingleFileToZipRootDir(File inFile, File zipOutFile) throws IOException {
		OutputStream out = new FileOutputStream(zipOutFile);
		ZipOutputStream zout = new ZipOutputStream(out);
		zout.close();
	}
	
	public static void addSingleFileToZipRootDir(File inFile, ZipOutputStream zout) throws IOException {
		String name = inFile.getName();
		
		ZipEntry ze = new ZipEntry(name);
		zout.putNextEntry(ze);
		copy(inFile, zout);
		zout.closeEntry();
//		zout.flush();
	}
	
	private static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		while (true) {
			int readCount = in.read(buffer);
			if (readCount < 0) {
				break;
			}
			out.write(buffer, 0, readCount);
		}
	}

	private static void copy(File file, OutputStream out) throws IOException {
		InputStream in = new FileInputStream(file);
		try {
			copy(in, out);
		} finally {
			in.close();
		}
	}

	private static void copy(InputStream in, File file) throws IOException {
		OutputStream out = new FileOutputStream(file, true);
		try {
			copy(in, out);
		} finally {
			out.close();
		}
	}
	
	public static boolean copyRawResource(Context context, int resId, File outFile) {
		InputStream is = context.getResources().openRawResource(resId);
		OutputStream os = null;
		try {
			os = new FileOutputStream(outFile);

			// Transfer bytes from in to out
			byte[] buf = new byte[1024];
			int len;
			while ((len = is.read(buf)) > 0) {
				os.write(buf, 0, len);
			}
			return true;
		} catch(IOException e) {
			Log.e(TAG, e.getMessage());
			return false;
		} finally {
			try {
				is.close();
				os.close();
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
				e.printStackTrace();
			}
		}
	}
	
	public static void copyEntireZipArchive(ZipInputStream zin, ZipOutputStream zout) throws IOException {
		for(ZipEntry ze = zin.getNextEntry(); ze != null; ze = zin.getNextEntry()) {
			String name = ze.getName();
			ZipEntry newze = new ZipEntry(name);
			zout.putNextEntry(newze);
			if (!ze.isDirectory()) {
				copy(zin, zout);
			}
			zout.closeEntry();
		}
	}
	
	public static String readFileAsString(File file) throws IOException {
		FileInputStream fis = new FileInputStream(file);
		byte[] bytes = new byte[(int) file.length()];

		fis.read(bytes);
		fis.close();
		return new String(bytes);
	}
	
	public static void saveStringAsFile(String string, File file) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(file, false));
		bw.write(string);
		bw.flush();
		bw.close();
	}
}
