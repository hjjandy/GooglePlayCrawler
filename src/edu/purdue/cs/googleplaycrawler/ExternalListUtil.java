package edu.purdue.cs.googleplaycrawler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExternalListUtil {

	private static final Logger logger = LogManager.getLogger(ExternalListUtil.class);

	/**
	 * Extract externally defined CATEGORY's for downloading. It happens if the
	 * LIST downloading fails for certain categories. The file should have the
	 * following format: CategoryId [sub-CategoryID:optional]
	 * 
	 * @param cname
	 * @return
	 */
	public static Iterator<SimplePair<String, String>> getExternalCategories(
			String cname) {
		File f = new File(cname);
		List<SimplePair<String, String>> all = new LinkedList<SimplePair<String, String>>();
		if (!f.exists() || !f.canRead()) {
			logger.warn("Assigned category file <{}> not exist.", cname);
			return all.iterator();
		}
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(f));
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty())
					continue;
				String[] parts = line.split(" ");
				SimplePair<String, String> p;
				if (parts.length == 1) {
					p = new SimplePair<String, String>(parts[0]);
					all.add(p);
				} else if (parts.length > 1) {
					p = new SimplePair<String, String>(parts[0], parts[1]);
					all.add(p);
				} else {
					logger.warn("Unrecognized <{}> in file '{}'.", line, cname);
				}
			}
		} catch (IOException e) {
			logger.error(e.getMessage());
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {

				}
			}
		}
		logger.info("Totally {} category items obtained.", all.size());
		return all.iterator();
	}

	/**
	 * Obtain externally defined keywords for search. One keyword (phrase) each
	 * line, e.g. "hello" or "hello world".
	 * 
	 * @param kname
	 *            - the path of keyword file.
	 * @return
	 */
	public static Iterator<String> getExternalKeywords(String kname) {
		File f = new File(kname);
		List<String> kw = new LinkedList<String>();
		if (!f.exists() || !f.canRead()) {
			logger.warn("Assigned keyword file <{}> not exist.", kname);
			return kw.iterator();
		}
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(f));
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty())
					continue;
				kw.add(line);
			}
		} catch (IOException e) {
			logger.error(e.getMessage());
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
				}
			}
		}
		logger.info("Totally {} keywords found.", kw.size());
		return kw.iterator();
	}
}
