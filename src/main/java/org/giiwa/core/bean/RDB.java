/*
 * Copyright 2015 JIHU, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package org.giiwa.core.bean;

import java.io.File;
import java.sql.*;
import java.util.*;

import javax.sql.DataSource;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.giiwa.core.base.Http;
import org.giiwa.core.conf.Config;
import org.giiwa.framework.web.Model;

/**
 * The {@code RDB} Class used to for RDS database layer operation.
 * 
 * @author joe
 *
 */
public class RDB {

  final private static Log log = LogFactory.getLog(RDB.class);

  /**
   * test is configured
   * 
   * @return boolean, true if configured
   */
  public static boolean isConfigured() {
    return ds != null;
  }

  /**
   * drop all tables for the "db", the "db" name was configured in
   * "giiwa.properties", such as: db[ttt].url=....
   *
   * @param db
   *          the db
   * @return int of how many table was dropped
   */
  public static int dropAll(String db) {
    Connection c = null;
    PreparedStatement stat = null;
    ResultSet r = null;

    try {
      if (X.isEmpty(db)) {
        c = getConnection();
      } else {
        c = getConnection(db);
      }
      DatabaseMetaData dm = c.getMetaData();
      r = dm.getTables(null, null, "%", new String[] { "TABLE" });
      List<String> tables = new ArrayList<String>();
      while (r.next()) {
        tables.add(r.getString(3));
      }
      r.close();
      r = null;
      for (String t : tables) {
        stat = c.prepareStatement("drop table " + t);
        stat.executeUpdate();
        stat.close();
        stat = null;
      }

      return tables.size();
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
    return 0;
  }

  /** The max active number. */
  private static int                          MAX_ACTIVE_NUMBER = 10;

  /** The max wait time. */
  private static int                          MAX_WAIT_TIME     = 10 * 1000;

  /** The url. */
  private static String                       URL               = "jdbc:mysql://localhost:3306/lud?user=root&password=123456";

  /** The driver. */
  private static String                       DRIVER            = "com.mysql.jdbc.Driver";

  /** The user. */
  private static String                       USER;

  /** The passwd. */
  private static String                       PASSWD;

  /** The validation sql. */
  private static String                       VALIDATION_SQL    = "SELECT 1 FROM DUAL";

  /** The ds. */
  private static BasicDataSource              ds;

  /** The dss. */
  private static Map<String, BasicDataSource> dss               = new TreeMap<String, BasicDataSource>();

  /** The conf. */
  private static Configuration                conf;

  /**
   * initialize the DB object from the "giiwa.properties"
   */
  public static synchronized void init() {
    conf = Config.getConfig();

    if (ds == null && !X.isEmpty(conf.getString("db.url", null))) {
      DRIVER = conf.getString("db.driver", DRIVER);
      URL = conf.getString("db.url", null);

      USER = conf.getString("db.user", null);

      PASSWD = conf.getString("db.passwd", null);

      MAX_ACTIVE_NUMBER = conf.getInt("db.number", MAX_ACTIVE_NUMBER);

      VALIDATION_SQL = conf.getString("db.validation.sql", VALIDATION_SQL);

      ds = new BasicDataSource();
      ds.setDriverClassName(DRIVER);
      ds.setUrl(URL);

      if (!X.isEmpty(USER))
        ds.setUsername(USER);

      if (!X.isEmpty(PASSWD))
        ds.setPassword(PASSWD);

      ds.setMaxActive(MAX_ACTIVE_NUMBER);

      // ds.setDefaultAutoCommit(true);
      ds.setMaxIdle(MAX_ACTIVE_NUMBER);
      ds.setMaxWait(MAX_WAIT_TIME);
      ds.setDefaultReadOnly(false);
      // ds.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      ds.setValidationQuery(null);// VALIDATION_SQL);
      ds.setPoolPreparedStatements(true);

    }

  }

  /**
   * Gets the driver.
   * 
   * Derby gives "Apache Derby" <br>
   * Microsoft SQL Server gives "Microsoft SQL Server" <br>
   * Oracle gives "Oracle" <br>
   * PostgreSQL gives "PostgreSQL"<br>
   * MySQL gives "MySQL"<br>
   * HSQLDB gives "HSQL Database Engine" <br>
   * DB2 gives "DB2/....". In my situation it gives "DB2/LINUXX8664" <br>
   * H2 gives "H2" <br>
   * 
   * @return the driver
   */
  public static String getDriver() {
    Connection c = null;
    try {
      c = getConnection();
      if (c != null) {
        String s = c.getMetaData().getDatabaseProductName().toLowerCase();
        String[] ss = X.split(s, "[ /]");

        return ss[0];
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    } finally {
      RDSHelper.close(c);
    }
    return null;
  }

  /**
   * Gets the driver by the db name.
   *
   * @param name
   *          the name
   * @return the driver
   */
  public static String getDriver(String name) {
    Connection c = null;
    try {
      c = getConnection(name);
      if (c != null) {
        String s = c.getMetaData().getDatabaseProductName().toLowerCase();
        String[] ss = X.split(s, "[ /]");
        return ss[0];
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    } finally {
      RDSHelper.close(c);
    }
    return null;
  }

  /**
   * initialize the DB object by the configuration.
   *
   * @param conf
   *          the conf
   */
  public static void init(Properties conf) {
    // if (ds == null) {
    if (conf.containsKey("db.driver")) {
      DRIVER = conf.getProperty("db.driver");
    }

    if (conf.containsKey("db.url")) {
      URL = conf.getProperty("db.url");
    }

    if (conf.containsKey("db.number")) {
      MAX_ACTIVE_NUMBER = Integer.parseInt(conf.getProperty("db.number"));
    }

    if (conf.containsKey("db.validation.sql")) {
      VALIDATION_SQL = conf.getProperty("db.validation.sql");
    }

    ds = new BasicDataSource();
    ds.setDriverClassName(DRIVER);
    ds.setUrl(URL);

    if (USER != null)
      ds.setUsername(USER);

    if (PASSWD != null)
      ds.setPassword(PASSWD);

    ds.setMaxActive(MAX_ACTIVE_NUMBER);
    ds.setMaxIdle(MAX_ACTIVE_NUMBER);
    ds.setMaxWait(MAX_WAIT_TIME);
    ds.setDefaultAutoCommit(true);
    ds.setDefaultReadOnly(false);
    // ds.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    ds.setValidationQuery(null);// VALIDATION_SQL);
    ds.setPoolPreparedStatements(true);
    // }

  }

  /**
   * Gets the connection.
   * 
   * @return the connection
   * @throws SQLException
   *           the sQL exception
   */
  public static Connection getConnection() throws SQLException {
    if (ds != null) {
      Connection c = ds.getConnection();
      if (c != null) {
        c.setAutoCommit(true);
      }
      return c;
    }

    return null;
  }

  /**
   * Gets the source.
   * 
   * @return the source
   */
  public static DataSource getSource() {
    return ds;
  }

  /**
   * Gets the connection by url.
   *
   * @param url
   *          the url
   * @param username
   *          the user name
   * @param passwd
   *          the password
   * @return the connection by url
   * @throws SQLException
   *           the SQL exception
   */
  public static Connection getConnectionByUrl(String url, String username, String passwd) throws SQLException {
    BasicDataSource external = dss.get(url);
    String jar = null;
    if (external == null) {

      // String D = conf.getString("db[" + name + "].driver", DRIVER);
      // String EXTERNAL_URL = conf.getString("db[" + name + "].url",
      // URL);
      // int N = conf.getInt("db[" + name + "].conns", MAX_ACTIVE_NUMBER);

      String D = null;
      String[] ss = url.split(":");
      if (ss.length > 2) {
        String type = ss[1];
        if (X.isSame(type, "mysql")) {
          D = "com.mysql.jdbc.Driver";
        } else if (X.isSame(type, "postgresql")) {
          D = "org.postgresql.Driver";
        } else if (X.isSame(type, "hsqldb")) {
          D = "org.hsqldb.jdbc.JDBCDriver";
          jar = "hsqldb-2.3.4.jar";
        } else if (X.isSame(type, "derby")) {
          D = "org.apache.derby.jdbc.EmbeddedDriver";
          jar = "derby-10.13.1.1.jar";
          // jdbc:derby:sampledb;create=true
        } else if (X.isSame(type, "oracle")) {
          D = "oracle.jdbc.driver.OracleDriver";
          jar = "ojdbc-14.jar";
        } else if (X.isSame(type, "db2")) {
          D = "com.ibm.db2.jcc.DB2Driver";
          jar = "db2jcc4-10.1.jar";
        } else if (X.isSame(type, "sqlserver")) {
          // 2005
          D = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
          jar = "mssql-jdbc-6.1.0.jre8.jar";
        } else if (X.isSame(type, "microsoft")) {
          // 2000
          D = "com.microsoft.jdbc.sqlserver.SQLServerDriver";
        }
      }

      log.debug("driver=" + D + ", url=" + url + ", user=" + username + ", password=" + passwd);

      if (!X.isEmpty(D)) {
        external = new BasicDataSource();
        external.setDriverClassName(D);

        external.setUrl(url);

        if (!X.isEmpty(username)) {
          external.setUsername(username);
        }
        if (!X.isEmpty(passwd)) {
          external.setPassword(passwd);
        }

        external.setMaxActive(10);
        // external.setDefaultAutoCommit(true);
        external.setMaxIdle(10);

        external.setMaxWait(MAX_WAIT_TIME);
        // external.setDefaultAutoCommit(true);
        // external.setDefaultReadOnly(false);
        // external.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        external.setValidationQuery(null);// VALIDATION_SQL);
        external.setPoolPreparedStatements(true);

        dss.put(url, external);

      } else {
        throw new SQLException("unknown URL");
      }
    }

    try {

      Connection c = (external == null ? ds.getConnection() : external.getConnection());
      // c.setAutoCommit(true);
      return c;

    } catch (SQLException e) {

      log.error(url, e);
      String error = e.getMessage();
      log.error("error=" + error + ", jar=" + jar);

      if (!X.isEmpty(error) && error.indexOf("Cannot load JDBC driver") >= 0) {
        if (!X.isEmpty(jar)) {
          // download the driver from giiwa
          int len = Http.download("http://www.giiwa.org/archive/" + jar,
              new File(Model.GIIWA_HOME + "/giiwa/WEB-INF/lib/" + jar));
          if (len > 0) {
            // restart the giiwa
            System.exit(0);
          }
        } else {
          throw new SQLException(e.getMessage() + ", please download the jdbc driver, copy to {giiwa}/WEB-INF/lib");
        }
      } else {
        throw e;
      }
    }

    throw new SQLException("unknown driver, please download the jdbc driver, copy to {giiwa}/WEB-INF/lib");
  }

  /**
   * Gets the connection.
   * 
   * @param name
   *          the name
   * @return the connection
   * @throws SQLException
   *           the SQL exception
   */
  public static Connection getConnection(String name) throws SQLException {
    name = name.trim();
    BasicDataSource external = dss.get(name);
    if (external == null) {

      String D = conf.getString("db[" + name + "].driver", DRIVER);
      String EXTERNAL_URL = conf.getString("db[" + name + "].url", URL);

      String username = null;
      String password = null;
      String[] ss = EXTERNAL_URL.split(":");
      if (ss.length > 2) {
        if (ss[1].equalsIgnoreCase("mysql")) {
          D = X.isEmpty(D) ? "com.mysql.jdbc.Driver" : D;
        } else if (ss[1].equalsIgnoreCase("postgresql")) {
          D = X.isEmpty(D) ? "org.postgresql.Driver" : D;
        } else if (ss[1].equalsIgnoreCase("oracle")) {
          D = X.isEmpty(D) ? "oracle.jdbc.driver.OracleDriver" : D;
        } else if (ss[1].equalsIgnoreCase("sqlserver") || ss[1].equalsIgnoreCase("microsoft")) {
          D = X.isEmpty(D) ? "com.microsoft.sqlserver.jdbc.SQLServerDriver" : D;

          int i = EXTERNAL_URL.indexOf("user=");
          if (i > 0) {
            int j = EXTERNAL_URL.indexOf("&", i + 1);
            if (j < 0) {
              j = EXTERNAL_URL.length();
            }
            String[] ss1 = EXTERNAL_URL.substring(i, j).split("=");
            if (ss1.length == 2) {
              username = ss1[1];
            }

            String url1 = EXTERNAL_URL.substring(0, i - 1);
            if (j < EXTERNAL_URL.length()) {
              EXTERNAL_URL = url1 + EXTERNAL_URL.substring(j);
            } else {
              EXTERNAL_URL = url1;
            }
          }

          i = EXTERNAL_URL.indexOf("password=");
          if (i > 0) {
            int j = EXTERNAL_URL.indexOf("&", i + 1);
            if (j < 0) {
              j = EXTERNAL_URL.length();
            }
            String[] ss1 = EXTERNAL_URL.substring(i, j).split("=");
            if (ss1.length == 2) {
              password = ss1[1];
            }
            String url1 = EXTERNAL_URL.substring(0, i - 1);
            if (j < EXTERNAL_URL.length()) {
              EXTERNAL_URL = url1 + EXTERNAL_URL.substring(j + 1);
            } else {
              EXTERNAL_URL = url1;
            }
          }

        }
      }

      int N = conf.getInt("db[" + name + "].conns", MAX_ACTIVE_NUMBER);

      external = new BasicDataSource();
      external.setDriverClassName(D.trim());

      if (!X.isEmpty(username)) {
        external.setUsername(username.trim());
      }
      if (!X.isEmpty(password)) {
        external.setPassword(password.trim());
      }

      external.setUrl(EXTERNAL_URL.trim());
      if (conf.containsKey("db[" + name + "].user")) {
        external.setUsername(conf.getString("db[" + name + "].user").trim());
      }
      if (conf.containsKey("db[" + name + "].passwd")) {
        external.setUsername(conf.getString("db[" + name + "].passwd").trim());
      }

      external.setMaxActive(N);
      external.setDefaultAutoCommit(true);
      external.setMaxIdle(N);
      external.setMaxWait(MAX_WAIT_TIME);
      external.setDefaultAutoCommit(true);
      external.setDefaultReadOnly(false);
      external.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      external.setValidationQuery(null);// VALIDATION_SQL);
      external.setPoolPreparedStatements(true);

      dss.put(name, external);
    }

    Connection c = (external == null ? ds.getConnection() : external.getConnection());
    c.setAutoCommit(true);
    return c;
  }

}
