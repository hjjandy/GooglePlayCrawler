/**
 * Part of this file is copied for the CLI of googleplaycrawler-0.3.jar.
 */
package edu.purdue.cs.googleplaycrawler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.akdeniz.googleplaycrawler.GooglePlay.AppDetails;
import com.akdeniz.googleplaycrawler.GooglePlay.BrowseLink;
import com.akdeniz.googleplaycrawler.GooglePlay.BrowseResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.DetailsResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.DocV2;
import com.akdeniz.googleplaycrawler.GooglePlay.ListResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.Offer;
import com.akdeniz.googleplaycrawler.GooglePlay.SearchResponse;
import com.akdeniz.googleplaycrawler.GooglePlayAPI;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.FeatureControl;
import net.sourceforge.argparse4j.inf.Namespace;

public class Main {
	private ArgumentParser parser;
	private GooglePlayAPI service;
	private Namespace namespace;
	private String home;
	private String appsDir;
	private String conf;
	private boolean listOnly = false;
	private String categoryFile;
	private String keywordFile;
	private boolean cleanDB = false;
	private boolean restoreDB = false;
	private SqliteManager sqliteManager;

	private static final Logger logger = LogManager.getLogger(Main.class);

	public Main() {
		parser = ArgumentParsers.newArgumentParser("GooglePlayCrawler")
				.description(
						"Download Android Apps from Google Play :) "
								+ "For other operations, (e.g. CheckIn), "
								+ "please use the original googleplaycrawler.");
		parser.addArgument("-H", "--home")
				.nargs("?")
				.help("Assign the home directory for downloaded apps.")
				.setDefault(FeatureControl.SUPPRESS);
		parser.addArgument("-A", "--appsDir")
				.nargs("?")
				.help("Specific directory to store apps (optional). Default value is '<HOME>/apps'.")
				.setDefault(FeatureControl.SUPPRESS);
		parser.addArgument("-f", "--conf")
				.nargs("?")
				.help("Configuration file to used for login! See sample.conf for example. "
						+ "It is searched at current dir, dir 'dat/' and dir [HOME] in order.")
				.setDefault(FeatureControl.SUPPRESS);
		parser.addArgument("-l", "--listOnly")
				.nargs("?")
				.setConst("true")
				.help("Only download apps using [LIST] command. "
						+ "Do not [SEARCH] apps. The option value is not requried (and not used).")
				.setDefault(FeatureControl.SUPPRESS);
		parser.addArgument("-y", "--category")
				.nargs("?")
				.help("Provide an external category file for downloading.")
				.setDefault(FeatureControl.SUPPRESS);
		parser.addArgument("-k", "--keyword")
				.nargs("?")
				.help("Provide a keyword file for searching and downloading.")
				.setDefault(FeatureControl.SUPPRESS);
		parser.addArgument("--clean")
				.nargs("?")
				.setConst("true")
				.help("Clean up the DB; remove items whose corresponding apps do not exist. "
						+ "All other options except [home] and [appsDir] are ignored.")
				.setDefault(FeatureControl.SUPPRESS);
		parser.addArgument("-r", "--restore")
				.nargs("?")
				.setConst("true")
				.help("Only used when accidentally good items in DB is deleted. "
						+ "Then use this command to restore DB based on existing apk files. "
						+ "Suppose version code does not change (no new download).")
				.setDefault(FeatureControl.SUPPRESS);
	}

	private void start(String[] args) {
		Map<String, Object> attrs = null;
		try {
			namespace = parser.parseArgs(args);
			attrs = namespace.getAttrs();
			if (attrs == null || attrs.isEmpty())
				throw new ArgumentParserException("No options provided.",
						parser);
			if (attrs.containsKey("home"))
				home = namespace.getString("home");
			if (attrs.containsKey("appsDir")) {
				appsDir = namespace.getString("appsDir");
			}
			if (attrs.containsKey("conf"))
				conf = namespace.getString("conf");
			if (attrs.containsKey("listOnly"))
				listOnly = true;
			if (attrs.containsKey("category")) {
				categoryFile = namespace.getString("category");
			}
			if (attrs.containsKey("keyword")) {
				keywordFile = namespace.getString("keyword");
			}
			if (attrs.containsKey("restore")) {
				restoreDB = true;
			}

			if (home == null) {
				throw new ArgumentParserException("[HOME] option is required.",
						parser);
			}
			File fHome = new File(home);
			if (!fHome.exists() && !fHome.mkdir()) {
				throw new ArgumentParserException(
						"Creating DIR for [HOME] fails. Pls do it manually.",
						parser);
			}
			if (!fHome.isDirectory()) {
				throw new ArgumentParserException(
						"[HOME] must point to a directory.", parser);
			}
			home = fHome.getAbsolutePath();

			if (appsDir != null) {
				File fADir = new File(appsDir);
				if (!fADir.exists() && !fADir.mkdir()) {
					throw new ArgumentParserException(
							"Creating DIR for [AppsDir] fails. Pls do it manually.",
							parser);
				}
				if (!fADir.isDirectory()) {
					throw new ArgumentParserException(
							"[AppsDir] must point to a directory.", parser);
				}
				appsDir = fADir.getAbsolutePath();
			} else {
				File fADir = new File(fHome, "apps");
				if (!fADir.exists() && !fADir.mkdir()) {
					throw new ArgumentParserException(
							"Creating DIR for [AppsDir] at "
									+ fADir.getAbsolutePath()
									+ " fails. Pls do it manually.", parser);
				}
				if (!fADir.isDirectory()) {
					throw new ArgumentParserException("[AppsDir] at "
							+ fADir.getAbsolutePath()
							+ " must point to a directory.", parser);
				}
				appsDir = fADir.getAbsolutePath();
			}

			if (attrs.containsKey("clean")) {
				cleanDB = true;
				throw new NoMoreActionException();
			}

			if (conf == null) {
				throw new ArgumentParserException("[CONF] option is required.",
						parser);
			}
			File fConf = new File(conf);
			File fConf1 = new File("dat", conf);
			File fConf2 = new File(fHome, conf);
			if (!fConf.exists() || !fConf.isFile() || !fConf.canRead()) {
				if (fConf1.exists() && fConf1.isFile() && fConf1.canRead()) {
					fConf = fConf1;
				} else if (fConf2.exists() && fConf2.isFile()
						&& fConf2.canRead()) {
					fConf = fConf2;
				} else {
					String msg = String.format(
							"The given [CONF] file cannot be accessed at '%s', 'dat/%s' or '%s/%s'.",
							conf, conf, home, conf);
					throw new ArgumentParserException(msg, parser);
				}
			}
			conf = fConf.getAbsolutePath();

			if (restoreDB || listOnly || null != categoryFile
					|| null != keywordFile) {
				doLogin();
			}
		} catch (ArgumentParserException e) {
			logger.error(e.getMessage(), e);
			parser.printHelp();
			System.exit(-1);
		} catch (NoMoreActionException e) {
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			System.exit(-1);
		}

		if (cleanDB) {
			doCleanDB();
			return;
		}

		if (restoreDB) {
			doRestoreDB();
			return;
		}

		if (listOnly) {
			if (categoryFile != null || keywordFile != null) {
				logger.warn("Options [category] and [keyword] are ignored whne [listOnly] exists.");
			}
			listCategores();
		} else if (null != categoryFile) {
			if (keywordFile != null) {
				logger.warn("Option [keyword] is ignored whne [category] exists.");
			}
			processExternalCategories();
		} else if (null != keywordFile) {
			processAssignedKeywords();
		} else {
			logger.warn("Please specify one of the following options: [listOnly], [category] and [keyword].");
			parser.printHelp();
		}
	}

	private void doCleanDB() {
		sqliteManager = SqliteManager.getInstance(home);
		sqliteManager.begin();
		List<SimplePair<String, String>> unused = sqliteManager.dumpUnusedItems(this);
		if (!unused.isEmpty()) {
			sqliteManager.delete(unused);
		}
		sqliteManager.end();
	}

	private void doRestoreDB() {
		File apps = new File(appsDir);
		File[] apks = apps.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File arg0, String arg1) {
				return arg1 != null && arg1.endsWith(".apk");
			}
		});
		sqliteManager = SqliteManager.getInstance(home);
		sqliteManager.begin();
		for (File f : apks) {
			String name = f.getName();
			int dashIdx = name.lastIndexOf('-');
			String pkg = name.substring(0, dashIdx);
			String vcode = name.substring(dashIdx + 1, name.length() - 4);
			if (!sqliteManager.exists(pkg, vcode)) {
				try {
					DetailsResponse details = service.details(pkg);

					AppDetails appDetails = details.getDocV2()
							.getDetails()
							.getAppDetails();
					String downloads = appDetails.getNumDownloads();
					int nCategories = appDetails.getAppCategoryCount();
					StringBuilder builder = new StringBuilder(
							appDetails.getAppCategory(0));
					int idx = 1;
					while (idx < nCategories) {
						builder.append(',');
						builder.append(appDetails.getAppCategory(idx));
						idx++;
					}
					String category = builder.toString();
					sqliteManager.insert(pkg, vcode, category, downloads);
					logger.info("Restored in DB: {}-{}", pkg, vcode);
				} catch (IOException e) {
				}
			}
		}
		sqliteManager.end();
	}

	private void doLogin() throws Exception {
		Properties properties = new Properties();
		properties.load(new FileInputStream(conf));

		String androidid = properties.getProperty("androidid");
		String email = properties.getProperty("email");
		String password = properties.getProperty("password");
		String localization = properties.getProperty("localization");

		if (androidid == null || email == null || password == null) {
			throw new Exception(
					"[androidid], [email] and [password] must NOT be empty. "
							+ "Valid values are required for login.");
		}

		service = new GooglePlayAPI(email, password, androidid);
		service.setLocalization(localization);
		service.login();
	}

	private void listCategores() {
		logger.info("List all categories.");
		List<String> categories = new LinkedList<String>();
		BrowseResponse browseResponse;
		try {
			browseResponse = service.browse();
			// System.out.println(CATEGORIES_HEADER);
			for (BrowseLink browseLink : browseResponse.getCategoryList()) {
				String[] splitedStrs = browseLink.getDataUrl().split("&cat=");
				// System.out.println(splitedStrs[splitedStrs.length - 1] + "\t"
				// + browseLink.getName());
				categories.add(splitedStrs[splitedStrs.length - 1]);
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		logger.info("Totally {} categories listed.", categories.size());
		for (String cat : categories) {
			listCategory(cat);
		}
	}

	private void processExternalCategories() {
		Iterator<SimplePair<String, String>> iter = ExternalListUtil.getExternalCategories(categoryFile);
		while (iter.hasNext()) {
			SimplePair<String, String> p = iter.next();
			String categoryId = p.first;
			String subCategoryId = p.second;
			if (subCategoryId == null) {
				listCategory(categoryId);
			} else {
				downloadSubCategory(categoryId, subCategoryId);
			}
		}
	}

	private void processAssignedKeywords() {
		Iterator<String> iter = ExternalListUtil.getExternalKeywords(keywordFile);
		while (iter.hasNext()) {
			String kw = iter.next();
			search(kw);
		}
	}

	private void listCategory(String cat) {
		logger.info("Start processing category <<{}>>.", cat);
		try {
			ListResponse listResponse = service.list(cat);
			for (DocV2 child : listResponse.getDocList()) {
				String subCateId = child.getDocid();
				if (!subCateId.contains("paid") && !subCateId.contains("pay")) {
					downloadSubCategory(cat, subCateId);
				}
			}
		} catch (IOException e) {
			logger.error("Category Failing: {}.", cat);
		}
		logger.info("End processing category <<{}>>.", cat);
	}

	/**
	 * NOTICE: only in a DetailsResponse can one get the detail information,
	 * e.g. AppCategory, DescriptionHtml. AppDetails from ListResponse does not
	 * provide such information.
	 * 
	 * @param categoryId
	 * @param subcategoryId
	 */
	private void downloadSubCategory(String categoryId, String subcategoryId) {
		logger.info("Start downloading Category <<{}>>:<{}>", categoryId,
				subcategoryId);
		List<String> packages = new LinkedList<String>();
		try {
			ListResponse listResponse = service.list(categoryId, subcategoryId,
					0, 100);
			for (DocV2 child : listResponse.getDoc(0).getChildList()) {
				AppDetails appDetails = child.getDetails().getAppDetails();
				packages.add(appDetails.getPackageName());
			}
			// System.out.println(categoryId + " : " + subcategoryId + " : " +
			// listResponse.getDocCount());
		} catch (IOException e) {
			logger.error("Subcategory Failing: {}::{}.", categoryId,
					subcategoryId);
		}
		if (!packages.isEmpty()) {
			download(packages);
		}
		packages = null;
		logger.info("End downloading Category <<{}>>:<{}>", categoryId,
				subcategoryId);
		// System.exit(0);
	}

	private void search(String keyword) {
		List<String> packages = new LinkedList<String>();
		int offset = 0;
		int numberOfResult = 100;

		try {
			while (true) {
				logger.info("Search {} at offset {}.", keyword, offset);
				SearchResponse sr = service.search(keyword, offset,
						numberOfResult);
				int n = 0;
				for (DocV2 child : sr.getDoc(0).getChildList()) {
					AppDetails appDetails = child.getDetails().getAppDetails();
					packages.add(appDetails.getPackageName());
					n++;
				}
				logger.info("Searched {} results.", n);
				if (!packages.isEmpty()) {
					download(packages);
					packages.clear();
				}
				if (n < numberOfResult) {
					break;
				}
				offset += n;
			}
		} catch (IOException e) {
			logger.error("Search Failing: {}", e.getMessage());
		}
		packages = null;
	}

	private void download(List<String> packages) {
		try {
			service.login();
		} catch (Exception e1) {
			logger.error("LOGIN before download fails.");
			return;
		}
		sqliteManager = SqliteManager.getInstance(home);
		sqliteManager.begin();
		while (!packages.isEmpty()) {
			// multi-thread task increase the chance of re-authentication
			// (captcha response).
			doDownload(packages.remove(0));
		}
		// new DownloadTask(packages).run();
		/*
		 * Thread[] threads = new Thread[5]; for (int i = 0; i < threads.length;
		 * i++) { threads[i] = new DownloadTask(packages); threads[i].start(); }
		 * for (int i = 0; i < threads.length; i++) { try { threads[i].join(); }
		 * catch (InterruptedException e) { logger.warn(e.getMessage(), e); } }
		 */
		sqliteManager.end();
		try {
			Thread.sleep(1000 * 60 * 5); // 5 mins
		} catch (InterruptedException e) {
			logger.error(e.getMessage());
		}
	}

	private void doDownload(String pkg) {

		try {
			logger.info("Try to download {}", pkg);
			DetailsResponse details = service.details(pkg);
			Offer offer = details.getDocV2().getOffer(0);
			if (offer.getCheckoutFlowRequired()) {
				logger.warn("Paid app: {}", pkg);
				return;
			}
			int offerType = offer.getOfferType();
			AppDetails appDetails = details.getDocV2()
					.getDetails()
					.getAppDetails();
			int versionCode = appDetails.getVersionCode();
			if (sqliteManager.exists(pkg, String.valueOf(versionCode))) {
				return;
			}
			String downloads = appDetails.getNumDownloads();
			int nCategories = appDetails.getAppCategoryCount();
			if (nCategories < 1) {
				logger.warn("Ignore no category app: {}-{}", pkg, versionCode);
				return;
			}
			logger.info("Downloading {}-{}", pkg, versionCode);
			StringBuilder builder = new StringBuilder(
					appDetails.getAppCategory(0));
			int idx = 1;
			while (idx < nCategories) {
				builder.append(',');
				builder.append(appDetails.getAppCategory(idx));
				idx++;
			}
			String category = builder.toString();

			InputStream dlStream = service.download(pkg, versionCode, offerType);
			File dstApk = new File(appsDir, pkg + "-" + versionCode + ".apk");
			FileOutputStream fos = new FileOutputStream(
					dstApk.getAbsoluteFile());
			byte buffer[] = new byte[1024];
			for (int k = 0; (k = dlStream.read(buffer)) != -1;) {
				fos.write(buffer, 0, k);
			}
			fos.close();
			dlStream.close();
			// download html and insert DB after downloading apk in case
			// it fails.
			String description = details.getDocV2().getDescriptionHtml();
			File desc = new File(appsDir, pkg + "-" + versionCode + ".html");
			BufferedWriter writer = new BufferedWriter(new FileWriter(desc));
			writer.write(description);
			writer.close();

			sqliteManager.insert(pkg, String.valueOf(versionCode), category,
					downloads);

			logger.info("Finish downloading {}-{}", pkg, versionCode);
		} catch (IOException e) {
			logger.warn("Fail to download: #{}#", pkg);
			logger.error(e.getMessage(), e);
		}

	}

	public boolean checkFile(String pkg, String versionCode) {
		String name = String.format("%s-%s", pkg, versionCode);
		File file = new File(appsDir, name + ".apk");
		if (file.exists()) {
			return true;
		}
		file = new File(appsDir, name + ".html");
		if (file.exists()) {
			file.delete();
		}
		return false;
	}

	public static void main(String[] args) {
		Main main = new Main();
		logger.info("Job start......");
		main.start(args);
		logger.info("Job is done. Going to exit.");
		System.exit(0);
	}

	class DownloadTask extends Thread {
		private List<String> packages;

		public DownloadTask(List<String> pkgs) {
			packages = pkgs;
		}

		public void run() {
			while (true) {
				String pkg = null;
				synchronized (packages) {
					if (!packages.isEmpty())
						pkg = packages.remove(0);
				}
				if (pkg == null) {
					break;
				}
				try {
					logger.info("[{}] Try to download {}", getId(), pkg);
					DetailsResponse details = service.details(pkg);
					Offer offer = details.getDocV2().getOffer(0);
					if (offer.getCheckoutFlowRequired()) {
						logger.warn("Paid app: {}", pkg);
						continue;
					}
					int offerType = offer.getOfferType();
					AppDetails appDetails = details.getDocV2()
							.getDetails()
							.getAppDetails();
					int versionCode = appDetails.getVersionCode();
					if (sqliteManager.exists(pkg, String.valueOf(versionCode))) {
						continue;
					}
					String downloads = appDetails.getNumDownloads();
					int nCategories = appDetails.getAppCategoryCount();
					if (nCategories < 1) {
						logger.warn("Ignore no category app: {}-{}", pkg,
								versionCode);
						continue;
					}
					logger.info("Downloading {}-{}", pkg, versionCode);
					StringBuilder builder = new StringBuilder(
							appDetails.getAppCategory(0));
					int idx = 1;
					while (idx < nCategories) {
						builder.append(',');
						builder.append(appDetails.getAppCategory(idx));
						idx++;
					}
					String category = builder.toString();

					InputStream dlStream = service.download(pkg, versionCode,
							offerType);
					File dstApk = new File(appsDir, pkg + "-" + versionCode
							+ ".apk");
					FileOutputStream fos = new FileOutputStream(
							dstApk.getAbsoluteFile());
					byte buffer[] = new byte[1024];
					for (int k = 0; (k = dlStream.read(buffer)) != -1;) {
						fos.write(buffer, 0, k);
					}
					fos.close();
					dlStream.close();
					// download html and insert DB after downloading apk in case
					// it fails.
					String description = details.getDocV2()
							.getDescriptionHtml();
					File desc = new File(appsDir, pkg + "-" + versionCode
							+ ".html");
					BufferedWriter writer = new BufferedWriter(new FileWriter(
							desc));
					writer.write(description);
					writer.close();

					sqliteManager.insert(pkg, String.valueOf(versionCode),
							category, downloads);

					logger.info("Finish downloading {}-{}", pkg, versionCode);
				} catch (IOException e) {
					logger.warn("Fail to download: #{}#", pkg);
					logger.error(e.getMessage(), e);
				}
			}
		}
	}

	class NoMoreActionException extends Exception {

	}
}
