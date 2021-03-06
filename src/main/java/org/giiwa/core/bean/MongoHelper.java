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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.giiwa.core.json.JSON;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

/**
 * The {@code Bean} Class is base class for all class that database access, it
 * almost includes all methods that need for database <br>
 * all data access MUST be inherited from it
 * 
 */
public class MongoHelper extends Helper {

  /** The mongo. */
  private static Map<String, MongoDatabase> mongo = new HashMap<String, MongoDatabase>();

  public static boolean isConfigured() {
    getDB();
    return mongo.size() > 0;
  }

  /**
   * Delete the data in mongo by the query
   * 
   * @param collection
   *          the collection
   * @param query
   *          the query
   * @return the long
   */
  public static long delete(String collection, W q, String db) {
    try {
      MongoCollection<Document> db1 = MongoHelper.getCollection(db, collection);
      if (db != null) {
        DeleteResult r = db1.deleteMany(q.query());
        // db.remove(query);
        return r.getDeletedCount();
      }
      return -1;
    } catch (Exception e) {
      if (log.isErrorEnabled())
        log.error(e.getMessage(), e);
    }
    return -1;
  }

  /**
   * get Mongo DB connection
   * 
   * @return DB
   */
  public static MongoDatabase getDB() {
    return getDB(DEFAULT);
  }

  /**
   * get Mongo DB connection <br>
   * the configuration including:
   * 
   * <pre>
   * mongo[database].url=
   * mongo[database].db=
   * mongo[database].conns=(50)
   * mongo[database].user=(null)
   * mongo[database].password=(null)
   * </pre>
   * 
   * @param database
   *          the name of database, if "" or null, then "prod"
   * @return DB
   */
  @SuppressWarnings("resource")
  public static MongoDatabase getDB(String database) {
    MongoDatabase g = null;
    if (X.isEmpty(database)) {
      database = "prod";
    }
    database = database.trim();

    synchronized (mongo) {
      g = mongo.get(database);
      if (g == null && conf != null) {
        String url = conf.getString("mongo[" + database + "].url", X.EMPTY);
        String dbname = conf.getString("mongo[" + database + "].db", X.EMPTY);

        if (!X.isEmpty(url) && !X.isEmpty(dbname)) {
          url = url.trim();
          dbname = dbname.trim();

          if (!url.startsWith("mongodb://")) {
            url = "mongodb://" + url;
          }

          int conns = conf.getInt("mongo[" + database + "].conns", 50);
          MongoClientOptions.Builder opts = new MongoClientOptions.Builder().socketTimeout(5000)
              .serverSelectionTimeout(1000).connectionsPerHost(conns);
          MongoClient client = new MongoClient(new MongoClientURI(url, opts));
          g = client.getDatabase(dbname);

          mongo.put(database, g);
        }
      }
    }

    return g;
  }

  /**
   * Gets the collection using the database connection
   * 
   * <br>
   * the configuration including:
   * 
   * <pre>
   * mongo[database].url=
   * mongo[database].db=
   * mongo[database].conns=(50)
   * mongo[database].user=(null)
   * mongo[database].password=(null)
   * </pre>
   * 
   * @param database
   *          the database
   * @param collection
   *          the collection
   * @return DBCollection
   */
  public static MongoCollection<Document> getCollection(String database, String collection) {
    MongoDatabase g = getDB(database);

    MongoCollection<Document> d = null;

    if (g != null) {
      d = g.getCollection(collection);
    }

    if (d == null) {
      if (log.isErrorEnabled())
        log.error(database + " was miss configured, please access http://[host:port]/setup to configure");
    }
    return d;
  }

  /**
   * Gets the collection using "prod", if the same thread required twice, then
   * return same connection but reference "+1" <br>
   * the configuration including:
   * 
   * <pre>
   * mongo[prod].url=
   * mongo[prod].db=
   * mongo[prod].conns=(50)
   * mongo[prod].user=(null)
   * mongo[prod].password=(null)
   * </pre>
   * 
   * @deprecated
   * @param name
   *          the name of the collection
   * @return DBCollection
   */
  public static MongoCollection<Document> getCollection(String name) {
    return getCollection("prod", name);
  }

  /**
   * Load the data by the query
   * 
   * @param <T>
   *          the generic type
   * @param collection
   *          the collection
   * @param query
   *          the query
   * @param clazz
   *          the clazz
   * @return the Bean
   */
  public static <T extends Bean> T load(String collection, Bson query, Class<T> clazz) {
    try {
      return load(collection, query, clazz.newInstance());
    } catch (Exception e) {
      if (log.isErrorEnabled())
        log.error(e.getMessage(), e);
    }

    return null;
  }

  /**
   * load the data by the query
   * 
   * @param <T>
   *          the subclass of Bean
   * @param collection
   *          the collection name
   * @param query
   *          the query
   * @param b
   *          the Bean
   * @return the Bean
   */
  public static <T extends Bean> T load(String collection, Bson query, T b) {
    try {
      MongoCollection<Document> db = MongoHelper.getCollection(collection);
      if (db != null) {
        FindIterable<Document> d = db.find(query);
        if (d != null) {
          Document d1 = d.first();
          if (d1 != null) {
            b.load(d1);
            return b;
          }
        }
      }
    } catch (Exception e) {
      if (log.isErrorEnabled())
        log.error(e.getMessage(), e);
    }

    return null;
  }

  /**
   * load the data by the query
   * 
   * @param <T>
   *          the subclass of Bean
   * @param collection
   *          the collection name
   * @param query
   *          the query
   * @param order
   *          the order
   * @param b
   *          the Bean
   * @return the Bean
   */
  public static <T extends Bean> T load(String collection, Bson query, Bson order, T b, String db) {
    TimeStamp t = TimeStamp.create();
    try {
      MongoCollection<Document> db1 = MongoHelper.getCollection(db, collection);
      if (db1 != null) {

        FindIterable<Document> d = db1.find(query);
        if (order == null) {
          d.sort(order);
        }

        if (d != null) {
          if (log.isDebugEnabled())
            log.debug(
                "load - cost=" + t.past() + "ms, collection=" + collection + ", query=" + query + ", order=" + order);

          Document d1 = d.first();
          if (d1 != null) {
            b.load(d1);
            return b;
          }

          // MongoCursor<Document> it = d.iterator();
          // if (it.hasNext()) {
          // b.load(it.next());
          // return b;
          // }
        } else {
          if (log.isDebugEnabled())
            log.debug("load - cost=" + t.past() + "ms, collection=" + collection + ", query=" + query + ", order="
                + order + ", result=" + null);
        }
      }
    } catch (Exception e) {
      if (log.isErrorEnabled())
        log.error("query=" + query + ", order=" + order, e);
    }

    return null;
  }

  /**
   * get the data from the collection.
   * 
   * @param <T>
   *          the generic Bean Class
   * @param query
   *          the query
   * @param order
   *          the order query
   * @param obj
   *          the Bean Class
   * @return T
   */
  public static <T extends Bean> T load(Bson query, Bson order, T obj) {
    String collection = getCollection(obj.getClass());
    if (collection != null) {
      return load(collection, query, order, obj, DEFAULT);
    }
    return null;

  }

  /**
   * load the data list.
   * 
   * @param <T>
   *          the generic Bean Class
   * @param collection
   *          the collection name
   * @param query
   *          the query
   * @param orderBy
   *          the order by query
   * @param offset
   *          the offset
   * @param limit
   *          the limit
   * @param clazz
   *          the Bean Class
   * @return Beans
   */
  public static <T extends Bean> Beans<T> load(String collection, Bson query, Bson orderBy, int offset, int limit,
      final Class<T> clazz, String db) {

    TimeStamp t = TimeStamp.create();
    MongoCollection<Document> db1 = null;
    FindIterable<Document> cur = null;
    try {
      db1 = MongoHelper.getCollection(db, collection);
      if (db1 != null) {
        cur = db1.find(query);

        if (orderBy != null) {
          cur.sort(orderBy);
        }

        final Beans<T> bs = new Beans<T>();

        // TODO, ignore this as big performance
        // bs.total = (int) db.count(query);
        // log.debug("cost=" + t.past() + "ms, count=" + bs.total);

        cur = cur.skip(offset);
        // log.debug("skip=" + t.past() +"ms, count=" + bs.total);

        if (limit < 0) {
          limit = 1000;
        }
        cur = cur.limit(limit);

        bs.list = new ArrayList<T>();

        MongoCursor<Document> it = cur.iterator();
        while (it.hasNext() && limit > 0) {
          // log.debug("hasnext=" + t.past() + "ms, count=" + bs.total);
          Document d = it.next();
          // log.debug("next=" + t.past() +"ms, count=" + bs.total);
          if (d != null) {
            T b = clazz.newInstance();
            b.load(d);
            bs.list.add(b);
            limit--;
          }
        }

        if (log.isDebugEnabled())
          log.debug("load - cost=" + t.past() + "ms, collection=" + collection + ", query=" + query + ", order="
              + orderBy + ", offset=" + offset + ", limit=" + limit + ", result="
              + (bs == null || bs.getList() == null ? "null" : bs.getList().size()));

        if (t.past() > 10000) {
          log.warn("load - cost=" + t.past() + "ms, collection=" + collection + ", query=" + query + ", order="
              + orderBy + ", result=" + (bs == null || bs.getList() == null ? "null" : bs.getList().size()));
        }
        return bs;
      }
    } catch (Exception e) {
      log.error("query=" + query + ", order=" + orderBy, e);

      // sort
      if (query != null && db != null) {
        db1.createIndex(query);
      }

      if (orderBy != null && db != null) {
        db1.createIndex(orderBy);
      }

    }

    return null;
  }

  /**
   * load the data full into the t.
   * 
   * @param <T>
   *          the generic Bean Class
   * @param query
   *          the query
   * @param t
   *          the Bean Class
   * @return Bean if failed, return null
   */
  public static <T extends Bean> T load(Bson query, T t) {
    String collection = getCollection(t.getClass());
    if (collection != null) {
      try {
        return load(query, null, t);
      } catch (Exception e) {
        if (log.isErrorEnabled())
          log.error(e.getMessage(), e);
      }
    }
    return null;
  }

  /**
   * load the data by the query
   * 
   * @param <T>
   *          the generic Bean Class
   * @param query
   *          the query
   * @param t
   *          the Bean Class
   * @return Bean the instance of the Class
   */
  public static <T extends Bean> T load(Bson query, Class<T> t) {

    String collection = getCollection(t);
    if (collection != null) {
      try {
        T obj = t.newInstance();
        return load(query, null, obj);
      } catch (Exception e) {
        if (log.isErrorEnabled())
          log.error(e.getMessage(), e);
      }
    }
    return null;
  }

  /**
   * load the Bean by the query, and order
   * 
   * @param <T>
   *          the generic Bean Class
   * @param query
   *          the query
   * @param order
   *          the orderby
   * @param t
   *          the Class Bean
   * @return Bean
   */
  public static <T extends Bean> T load(Bson query, Bson order, Class<T> t) {
    String collection = getCollection(t);
    if (collection != null) {
      try {
        T obj = t.newInstance();
        return load(query, order, obj);
      } catch (Exception e) {
        if (log.isErrorEnabled())
          log.error(e.getMessage(), e);
      }
    }
    return null;
  }

  public static <T extends Bean> T load(String collection, W q, Class<T> t, String db) {
    try {
      T obj = t.newInstance();
      return load(collection, q.query(), q.order(), obj, db);
    } catch (Exception e) {
      if (log.isErrorEnabled())
        log.error(e.getMessage(), e);
    }
    return null;
  }

  /**
   * Load the data, and return the DBObject
   * 
   * @param collection
   *          the collection
   * @param query
   *          the query
   * @return the DB object
   */
  public static Document load(String db, String collection, Bson query) {
    /**
     * create the sql statement
     */
    try {
      MongoCollection<Document> c = MongoHelper.getCollection(db, collection);
      if (c != null) {

        return c.find(query).first();
      }
    } catch (Exception e) {
      if (log.isErrorEnabled())
        log.error(query, e);
    }
    return null;
  }

  /**
   * get the collection name that associated with the Bean.
   * 
   * @param clazz
   *          the Bean Class
   * @return String
   */
  final static public String getCollection(Class<? extends Bean> clazz) {
    /**
     * get the require annotation onGet
     */
    Table mapping = (Table) clazz.getAnnotation(Table.class);
    if (mapping == null) {
      if (log.isErrorEnabled())
        log.error("mapping missed in [" + clazz + "] declaretion");
      return null;
    } else {
      return mapping.name();
    }
  }

  /**
   * insert into the collection
   * 
   * @param collection
   *          the collection name
   * @param v
   *          the values
   * @return int
   */
  final public static int insertCollection(String collection, V v, String db) {

    MongoCollection<Document> c = getCollection(db, collection);
    if (c != null) {
      Document d = new Document();

      for (String name : v.names()) {
        Object v1 = v.value(name);
        d.append(name, v1);
      }

      try {

        c.insertOne(d);

        if (log.isDebugEnabled())
          log.debug("inserted collection=" + collection + ", d=" + d);
        return 1;
      } catch (Exception e) {
        if (log.isErrorEnabled())
          log.error(e.getMessage(), e);
      }
    }
    return 0;
  }

  /**
   * update mongo collection.
   * 
   * @param collection
   *          the collection name
   * @param q
   *          the update query
   * @param v
   *          the value
   * @return int of updated
   */
  final public static long updateCollection(String collection, W q, V v, String db) {

    Document d = new Document();

    // int len = v.size();
    for (String name : v.names()) {
      Object v1 = v.value(name);
      d.append(name, v1);
    }

    try {
      log.debug("data=" + d);
      MongoCollection<Document> c = MongoHelper.getCollection(db, collection);
      UpdateResult r = c.updateMany(q.query(), new Document("$set", d));

      if (log.isDebugEnabled())
        log.debug("updated collection=" + collection + ", query=" + q + ", d=" + d + ", n=" + r.getModifiedCount()
            + ",result=" + r);

      // r.getN();
      // r.getField("nModified");
      return r.getModifiedCount();
    } catch (Exception e) {
      if (log.isErrorEnabled())
        log.error(e.getMessage(), e);
    }
    return 0;
  }

  /**
   * test the data exists ?
   * 
   * @param collection
   *          the collection name
   * @param query
   *          the query
   * @return true: if exists, false: not exists
   * @throws SQLException
   *           throw Exception if occur error
   */
  public static boolean exists(String collection, W q, String db) throws SQLException {
    TimeStamp t1 = TimeStamp.create();
    boolean b = false;
    try {
      b = MongoHelper.load(db, collection, q.query()) != null;
    } finally {
      if (log.isDebugEnabled())
        log.debug("exists cost=" + t1.past() + "ms,  collection=" + collection + ", query=" + q + ", result=" + b);
    }
    return b;
  }

  /**
   * run the command of Mongo.
   *
   * @param cmd
   *          the command
   * @return boolean, return true if "ok"
   */
  public static Document run(Bson cmd) {
    MongoDatabase d = MongoHelper.getDB();
    if (d != null) {
      return d.runCommand(cmd);
    }
    return null;
  }

  /**
   * get all collections
   * 
   * @return Set
   */
  public static Set<String> getCollections() {
    MongoDatabase d = MongoHelper.getDB();
    if (d != null) {

      MongoIterable<String> it = d.listCollectionNames();
      MongoCursor<String> ii = it.iterator();
      Set<String> r = new TreeSet<String>();
      while (ii.hasNext()) {
        r.add(ii.next());
      }
      return r;
    }
    return null;
  }

  /**
   * remove all the data from the collection.
   *
   * @param collection
   *          the collection
   */
  public static void clear(String collection) {
    try {
      MongoCollection<Document> c = MongoHelper.getCollection(collection);
      if (c != null) {
        c.deleteMany(new BasicDBObject());
      }
    } catch (Exception e) {
      if (log.isErrorEnabled())
        log.error(e.getMessage(), e);
    }
  }

  /**
   * get distinct value for key by the query.
   *
   * @param <T>
   *          the base object
   * @param collection
   *          the collection name
   * @param key
   *          the key that contain the value
   * @param q
   *          the query
   * @param t
   *          the class
   * @return List of the value
   */
  public static <T> List<T> distinct(String collection, String key, W q, Class<T> t, String db) {

    TimeStamp t1 = TimeStamp.create();
    try {

      MongoCollection<Document> c = MongoHelper.getCollection(db, collection);
      if (c != null) {
        Iterator<T> it = c.distinct(key, q.query(), t).iterator();
        List<T> list = new ArrayList<T>();
        while (it.hasNext()) {
          list.add(it.next());
        }

        return (List<T>) list;
      }
    } catch (Exception e) {
      if (log.isErrorEnabled())
        log.error(e.getMessage(), e);
    } finally {
      if (log.isDebugEnabled())
        log.debug("disinct[" + key + "] cost=" + t1.past() + "ms,  collection=" + collection + ", query=" + q);
    }
    return null;
  }

  /**
   * count the data, this may cause big issue if the data is huge
   * 
   * @param q
   *          the query
   * @param t
   *          the Class of Bean
   * @return the number of data
   */
  public static long count(W q, Class<? extends Bean> t) {
    String collection = MongoHelper.getCollection(t);
    if (!X.isEmpty(collection)) {
      return count(collection, q, DEFAULT);
    }
    return 0;

  }

  /**
   * count the number by the query.
   * 
   * @param collection
   *          the collection name
   * @param q
   *          the query and order
   * @return long
   */
  public static long count(String collection, W q, String db) {
    TimeStamp t1 = TimeStamp.create();
    try {

      MongoCollection<Document> c = MongoHelper.getCollection(db, collection);
      if (c != null) {
        return c.count(q.query());
      }

    } finally {
      if (log.isDebugEnabled())
        log.debug("count, cost=" + t1.past() + "ms,  collection=" + collection + ", query=" + q);
    }
    return 0;
  }

  /**
   * backup the whole data from file
   * 
   * @param filename
   *          the file name
   */
  public static void backup(String filename) {
    File f = new File(filename);
    f.getParentFile().mkdirs();

    try {
      ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(f));
      zip.putNextEntry(new ZipEntry("db"));
      PrintStream out = new PrintStream(zip);

      Set<String> c1 = getCollections();
      log.debug("collections=" + c1);
      for (String table : c1) {
        _backup(out, table);
      }

      zip.closeEntry();
      zip.close();

    } catch (Exception e) {
      log.debug(e.getMessage(), e);
    }
  }

  private static void _backup(PrintStream out, String tablename) {
    log.debug("backuping " + tablename);
    MongoCollection<Document> d1 = getCollection(tablename);
    MongoCursor<Document> c1 = d1.find().iterator();
    int rows = 0;
    while (c1.hasNext()) {
      rows++;

      Document d2 = c1.next();
      JSON jo = new JSON();
      jo.put("_table", tablename);
      for (String name : d2.keySet()) {
        jo.put(name, d2.get(name));
      }
      out.println(jo.toString());
      if (rows % 1000 == 0)
        log.debug("backup " + tablename + ", rows=" + rows);
    }
  }

  /**
   * recover the database from the file, the old data will be erased, index will
   * be keep
   * 
   * @param file
   *          the mongo.dmp file
   */
  public static void recover(File file) {

    try {
      ZipInputStream zip = new ZipInputStream(new FileInputStream(file));
      zip.getNextEntry();
      BufferedReader in = new BufferedReader(new InputStreamReader(zip));

      Set<String> c1 = getCollections();
      log.debug("collections=" + c1);
      for (String table : c1) {
        MongoCollection<Document> c2 = getCollection(table);
        try {
          c2.drop();
        } catch (Exception e) {
          log.error("table=" + table, e);
        }
      }

      String line = in.readLine();
      while (line != null) {
        _recover(line);
        line = in.readLine();
      }
      zip.closeEntry();
      in.close();
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }

  }

  private static void _recover(String json) {
    try {
      JSON jo = JSON.fromObject(json);
      V v = V.create().copy(jo);
      String tablename = jo.getString("_table");
      v.remove("_table");
      insertCollection(tablename, v, DEFAULT);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
  }

}
