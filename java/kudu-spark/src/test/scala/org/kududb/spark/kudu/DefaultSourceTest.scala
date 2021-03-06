/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kududb.spark.kudu

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.TimeZone

import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.functions._
import org.junit.Assert._
import org.junit.runner.RunWith
import org.kududb.client.CreateTableOptions
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfter, FunSuite}

import scala.collection.JavaConverters._
import scala.collection.immutable.IndexedSeq

@RunWith(classOf[JUnitRunner])
class DefaultSourceTest extends FunSuite with TestContext with BeforeAndAfter {

  test("timestamp conversion") {
    val epoch = new Timestamp(0)
    assertEquals(0, KuduRelation.timestampToMicros(epoch))
    assertEquals(epoch, KuduRelation.microsToTimestamp(0))

    val t1 = new Timestamp(0)
    t1.setNanos(123456000)
    assertEquals(123456, KuduRelation.timestampToMicros(t1))
    assertEquals(t1, KuduRelation.microsToTimestamp(123456))

    val iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
    iso8601.setTimeZone(TimeZone.getTimeZone("UTC"))

    val t3 = new Timestamp(iso8601.parse("1923-12-01T00:44:36.876").getTime)
    t3.setNanos(876544000)
    assertEquals(-1454368523123456L, KuduRelation.timestampToMicros(t3))
    assertEquals(t3, KuduRelation.microsToTimestamp(-1454368523123456L))
  }

  val rowCount = 10
  var sqlContext : SQLContext = _
  var rows : IndexedSeq[(Int, Int, String)] = _
  var kuduOptions : Map[String, String] = _

  before {
    val rowCount = 10
    rows = insertRows(rowCount)

    sqlContext = new SQLContext(sc)

    kuduOptions = Map(
      "kudu.table" -> tableName,
      "kudu.master" -> miniCluster.getMasterAddresses)

    sqlContext.read.options(kuduOptions).kudu.registerTempTable(tableName)
  }

  test("table creation") {
    if(kuduContext.tableExists("testcreatetable")) {
      kuduContext.deleteTable("testcreatetable")
    }

    val df = sqlContext.read.options(kuduOptions).kudu

    kuduContext.createTable("testcreatetable", df.schema, Seq("key"),
                            new CreateTableOptions().setRangePartitionColumns(List("key").asJava)
                                                    .setNumReplicas(1))

    // now use new options to refer to the new table name
    val newOptions: Map[String, String] = Map(
      "kudu.table" -> "testcreatetable",
      "kudu.master" -> miniCluster.getMasterAddresses)

    df.write.options(newOptions).mode("append").kudu

    val checkDf = sqlContext.read.options(newOptions).kudu

    assert(checkDf.schema === df.schema)

    assertTrue(kuduContext.tableExists("testcreatetable"))
    assert(checkDf.count == 10)
    kuduContext.deleteTable("testcreatetable")

    assertFalse(kuduContext.tableExists("testcreatetable"))
  }

  test("insertion") {
    val df = sqlContext.read.options(      kuduOptions).kudu
    val changedDF = df.limit(1).withColumn("key", df("key").plus(100)).withColumn("c2_s", lit("abc"))
    changedDF.show
    changedDF.write.options(kuduOptions).mode("append").kudu

    val newDF = sqlContext.read.options(kuduOptions).kudu
    newDF.show
    val collected = newDF.filter("key = 100").collect()
    assertEquals("abc", collected(0).getAs[String]("c2_s"))

    deleteRow(100)
  }

  test("insertion multiple") {
    val df = sqlContext.read.options(kuduOptions).kudu
    val changedDF = df.limit(2).withColumn("key", df("key").plus(100)).withColumn("c2_s", lit("abc"))
    changedDF.show
    changedDF.write.options(kuduOptions).mode("append").kudu

    val newDF = sqlContext.read.options(kuduOptions).kudu
    newDF.show
    val collected = newDF.filter("key = 100").collect()
    assertEquals("abc", collected(0).getAs[String]("c2_s"))

    val collectedTwo = newDF.filter("key = 101").collect()
    assertEquals("abc", collectedTwo(0).getAs[String]("c2_s"))

    deleteRow(100)
    deleteRow(101)
  }

  test("update row") {
    val df = sqlContext.read.options(kuduOptions).kudu
    val baseDF = df.limit(1) // filter down to just the first row
    baseDF.show
    // change the c2 string to abc and update
    val changedDF = baseDF.withColumn("c2_s", lit("abc"))
    changedDF.show
    changedDF.write.options(kuduOptions).mode("overwrite").kudu

    //read the data back
    val newDF = sqlContext.read.options(kuduOptions).kudu
    newDF.show
    val collected = newDF.filter("key = 0").collect()
    assertEquals("abc", collected(0).getAs[String]("c2_s"))

    //rewrite the original value
    baseDF.withColumn("c2_s", lit("0")).write.options(kuduOptions)
      .mode("overwrite").kudu
  }

  test("out of order selection") {
    val df = sqlContext.read.options(kuduOptions).kudu.select( "c2_s", "c1_i", "key")
    val collected = df.collect()
    assert(collected(0).getString(0).equals("0"))

  }

  test("table scan") {
    val results = sqlContext.sql(s"SELECT * FROM $tableName").collectAsList()
    assert(results.size() == rowCount)

    assert(!results.get(0).isNullAt(2))
    assert(results.get(1).isNullAt(2))
  }

  test("table scan with projection") {
    assertEquals(10, sqlContext.sql(s"""SELECT key FROM $tableName""").count())
  }

  test("table scan with projection and predicate double") {
    assertEquals(rows.count { case (key, i, s) => i != null && i > 5 },
                 sqlContext.sql(s"""SELECT key, c3_double FROM $tableName where c3_double > "5.0"""").count())
  }

  test("table scan with projection and predicate long") {
    assertEquals(rows.count { case (key, i, s) => i != null && i > 5 },
                 sqlContext.sql(s"""SELECT key, c4_long FROM $tableName where c4_long > "5"""").count())

  }
  test("table scan with projection and predicate bool") {
    assertEquals(rows.count { case (key, i, s) => i != null && i%2==0 },
                 sqlContext.sql(s"""SELECT key, c5_bool FROM $tableName where c5_bool = true""").count())

  }
  test("table scan with projection and predicate short") {
    assertEquals(rows.count { case (key, i, s) => i != null && i > 5},
                 sqlContext.sql(s"""SELECT key, c6_short FROM $tableName where c6_short > 5""").count())

  }
  test("table scan with projection and predicate float") {
    assertEquals(rows.count { case (key, i, s) => i != null && i > 5},
                 sqlContext.sql(s"""SELECT key, c7_float FROM $tableName where c7_float > 5""").count())

  }

  test("table scan with projection and predicate ") {
    assertEquals(rows.count { case (key, i, s) => s != null && s > "5" },
      sqlContext.sql(s"""SELECT key FROM $tableName where c2_s > "5"""").count())

    assertEquals(rows.count { case (key, i, s) => s != null },
      sqlContext.sql(s"""SELECT key, c2_s FROM $tableName where c2_s IS NOT NULL""").count())
  }


  test("Test basic SparkSQL") {
    val results = sqlContext.sql("SELECT * FROM " + tableName).collectAsList()
    assert(results.size() == rowCount)

    assert(results.get(1).isNullAt(2))
    assert(!results.get(0).isNullAt(2))
  }

  test("Test basic SparkSQL projection") {
    val results = sqlContext.sql("SELECT key FROM " + tableName).collectAsList()
    assert(results.size() == rowCount)
    assert(results.get(0).size.equals(1))
    assert(results.get(0).getInt(0).equals(0))
  }

  test("Test basic SparkSQL with predicate") {
    val results = sqlContext.sql("SELECT key FROM " + tableName + " where key=1").collectAsList()
    assert(results.size() == 1)
    assert(results.get(0).size.equals(1))
    assert(results.get(0).getInt(0).equals(1))

  }

  test("Test basic SparkSQL with two predicates") {
    val results = sqlContext.sql("SELECT key FROM " + tableName + " where key=2 and c2_s='2'").collectAsList()
    assert(results.size() == 1)
    assert(results.get(0).size.equals(1))
    assert(results.get(0).getInt(0).equals(2))
  }

  test("Test basic SparkSQL with two predicates negative") {
    val results = sqlContext.sql("SELECT key FROM " + tableName + " where key=1 and c2_s='2'").collectAsList()
    assert(results.size() == 0)
  }

  test("Test basic SparkSQL with two predicates including string") {
    val results = sqlContext.sql("SELECT key FROM " + tableName + " where c2_s='2'").collectAsList()
    assert(results.size() == 1)
    assert(results.get(0).size.equals(1))
    assert(results.get(0).getInt(0).equals(2))
  }

  test("Test basic SparkSQL with two predicates and projection") {
    val results = sqlContext.sql("SELECT key, c2_s FROM " + tableName + " where c2_s='2'").collectAsList()
    assert(results.size() == 1)
    assert(results.get(0).size.equals(2))
    assert(results.get(0).getInt(0).equals(2))
    assert(results.get(0).getString(1).equals("2"))
  }

  test("Test basic SparkSQL with two predicates greater than") {
    val results = sqlContext.sql("SELECT key, c2_s FROM " + tableName + " where c2_s>='2'").collectAsList()
    assert(results.size() == 4)
    assert(results.get(0).size.equals(2))
    assert(results.get(0).getInt(0).equals(2))
    assert(results.get(0).getString(1).equals("2"))
  }
}
