package edu.purdue.cs.googleplaycrawler;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.jmx.LoggerConfigAdmin;

public class SqliteManager {

	private String dbPath;
	private Connection connection;
	private Statement statement;

	private static final Logger logger = LogManager.getLogger(SqliteManager.class);

	SqliteManager(String home) {
		File fHome = new File(home);
		File fDb = new File(fHome, "AllApps.sqlite3.db");
		dbPath = fDb.getAbsolutePath();
		fHome = null;
		fDb = null;
	}

	public static SqliteManager getInstance(String home) {
		SqliteManager mgr = new SqliteManager(home);
		return mgr;
	}

	/**
	 * Do check duplication before [INSERT].
	 * 
	 * @param pkg
	 * @param title
	 * @param versionCode
	 * @param category
	 * @param downloads
	 */
	public synchronized void insert(String pkg, String versionCode,
			String category, String downloads) {
		try {
			String sql = String.format(
					"insert into APPS values ('%s', '%s', '%s', '%s')", pkg,
					versionCode, category, downloads);
			statement.executeUpdate(sql);
			logger.info("INSERT into DB succeeds for: " + pkg + "-"
					+ versionCode);
		} catch (SQLException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public synchronized boolean exists(String pkg, String versionCode) {
		boolean exists = false;
		String sql = String.format(
				"select * from APPS where package='%s' AND versionCode='%s'",
				pkg, versionCode);
		try {
			ResultSet rs = statement.executeQuery(sql);
			if (rs.next()) {
				exists = true;
				logger.warn("Existing [" + pkg + "-" + versionCode + "] in DB.");
			}
		} catch (SQLException e) {
			logger.error(e.getMessage(), e);
		}
		return exists;
	}

	public List<SimplePair<String, String>> dumpUnusedItems(Main main) {
		List<SimplePair<String, String>> unused = new LinkedList<SimplePair<String, String>>();
		try {
			ResultSet rs = statement.executeQuery("select * from APPS;");
			while (rs.next()) {
				String pkg = rs.getString("package");
				String vCode = rs.getString("versionCode");
				SimplePair<String, String> p = new SimplePair<String, String>(
						pkg, vCode);
				if (!main.checkFile(pkg, vCode)) {
					unused.add(p);
				}
			}
			rs.close();
		} catch (SQLException e) {
			logger.error(e.getMessage(), e);
		}
		logger.info("Totally {} unused items found.", unused.size());
		return unused;
	}

	/**
	 * Delete UNUSED items defined by the parameter.
	 * 
	 * @param unused
	 *            - it is cleared after this method call.
	 */
	public void delete(List<SimplePair<String, String>> unused) {
		logger.info("Start deleting {} items from DB.", unused.size());
		int s = 0;
		while (!unused.isEmpty()) {
			SimplePair<String, String> p = unused.remove(0);
			String pkg = p.first;
			String vcode = p.second;
			String sql = String.format(
					"delete from APPS where package='%s' AND versionCode='%s'",
					pkg, vcode);
			try {
				statement.executeUpdate(sql);
				s++;
			} catch (SQLException e) {

			}
		}
		logger.info("End deleting {} items from DB.", s);
	}

	/**
	 * No matter it returns true (success) or false (fail), remember to call
	 * [end] before program ends.
	 * 
	 * @return true: success; false: fail
	 */
	public boolean begin() {
		boolean success = true;
		try {
			Class.forName("org.sqlite.JDBC");
			String DB = String.format("jdbc:sqlite:%s", dbPath);
			connection = DriverManager.getConnection(DB);
			statement = connection.createStatement();
			statement.setQueryTimeout(30); // set timeout to 30 sec.
			statement.executeUpdate("create table if not exists APPS "
					+ "(package string NOT NULL, versionCode string, "
					+ "category string, downloads string, "
					+ " PRIMARY KEY (package, versionCode))");
			logger.info("SqliteManager INITIALIZATION succeeds.");
		} catch (ClassNotFoundException e) {
			logger.error(e.getMessage(), e);
			success = false;
		} catch (SQLException e) {
			logger.error(e.getMessage(), e);
			success = false;
		}

		return success;
	}

	public void end() {
		try {
			if (statement != null)
				statement.close();
		} catch (SQLException e) {
			logger.error(e.getMessage(), e);
		}
		try {
			if (connection != null)
				connection.close();
			logger.info("SqliteManager FINALIZATION succeeds.");
		} catch (SQLException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public static void main(String[] args) throws ClassNotFoundException {
		SqliteManager mgr = SqliteManager.getInstance("D:\\");
		mgr.begin();
		mgr.insert("a.b.c", "125", "Free", "1,000,000");
		if (mgr.exists("a.b.c", "125")) {
			logger.warn("Exists");
		}
		mgr.end();
	}
}
