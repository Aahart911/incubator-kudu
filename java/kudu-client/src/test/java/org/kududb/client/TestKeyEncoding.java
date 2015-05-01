// Copyright (c) 2014, Cloudera, inc.
// Confidential Cloudera Information: Covered by NDA.
package org.kududb.client;

import static org.junit.Assert.*;

import org.kududb.ColumnSchema;
import org.kududb.Schema;
import org.kududb.Type;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TestKeyEncoding {

  @Test
  public void testBoolKey() {
    List<ColumnSchema> cols1 = new ArrayList<ColumnSchema>();
    cols1.add(new ColumnSchema.ColumnSchemaBuilder("key", Type.BOOL)
        .key(true)
        .build());
    Schema schema = new Schema(cols1);
    KuduTable table = new KuduTable(null, "one", schema);
    Insert oneKeyInsert = new Insert(table);
    oneKeyInsert.addBoolean("key", true);
    assertTrue(Bytes.pretty(oneKeyInsert.key()) + " isn't foo",
            Bytes.equals(new byte[] {1}, oneKeyInsert.key()));

    oneKeyInsert = new Insert(table);
    oneKeyInsert.addBoolean("key", false);
    assertTrue(Bytes.pretty(oneKeyInsert.key()) + " isn't foo",
            Bytes.equals(new byte[] {0}, oneKeyInsert.key()));

  }

  @Test
  public void test() {
    List<ColumnSchema> cols1 = new ArrayList<ColumnSchema>();
    cols1.add(new ColumnSchema.ColumnSchemaBuilder("key", Type.STRING)
        .key(true)
        .build());
    Schema schemaOneString = new Schema(cols1);
    KuduTable table = new KuduTable(null, "one", schemaOneString);
    Insert oneKeyInsert = new Insert(table);
    oneKeyInsert.addString("key", "foo");
    assertTrue(Bytes.pretty(oneKeyInsert.key()) + " isn't foo", Bytes.equals(new byte[] {'f', 'o',
        'o'}, oneKeyInsert.key()));

    List<ColumnSchema> cols2 = new ArrayList<ColumnSchema>();
    cols2.add(new ColumnSchema.ColumnSchemaBuilder("key", Type.STRING)
        .key(true)
        .build());
    cols2.add(new ColumnSchema.ColumnSchemaBuilder("key2", Type.STRING)
        .key(true)
        .build());
    Schema schemaTwoString = new Schema(cols2);
    KuduTable table2 = new KuduTable(null, "two", schemaTwoString);
    Insert twoKeyInsert = new Insert(table2);
    twoKeyInsert.addString("key", "foo");
    twoKeyInsert.addString("key2", "bar");
    assertTrue(Bytes.pretty(twoKeyInsert.key()) + " isn't foo0x000x00bar",
        Bytes.equals(new byte[] {'f',
        'o', 'o', 0x00, 0x00, 'b', 'a', 'r'}, twoKeyInsert.key()));

    Insert twoKeyInsertWithNull = new Insert(table2);
    twoKeyInsertWithNull.addString("key", "xxx\0yyy");
    twoKeyInsertWithNull.addString("key2", "bar");
    assertTrue(Bytes.pretty(twoKeyInsertWithNull.key()) + " isn't " +
        "xxx0x000x01yyy0x000x00bar",
        Bytes.equals(new byte[] {'x', 'x', 'x', 0x00, 0x01, 'y', 'y', 'y', 0x00, 0x00, 'b', 'a',
            'r'},
            twoKeyInsertWithNull.key()));

    // test that we get the correct memcmp result, the bytes are in big-endian order in a key
    List<ColumnSchema> cols3 = new ArrayList<ColumnSchema>();
    cols3.add(new ColumnSchema.ColumnSchemaBuilder("key", Type.INT32)
        .key(true)
        .build());
    cols3.add(new ColumnSchema.ColumnSchemaBuilder("key2", Type.STRING)
        .key(true)
        .build());
    Schema schemaIntString = new Schema(cols3);
    KuduTable table3 = new KuduTable(null, "three", schemaIntString);
    Insert small = new Insert(table3);
    small.addInt("key", 20);
    small.addString("key2", "data");
    assertEquals(0, Bytes.memcmp(small.key(), small.key()));

    Insert big = new Insert(table3);
    big.addInt("key", 10000);
    big.addString("key2", "data");
    assertTrue(Bytes.memcmp(small.key(), big.key()) < 0);
    assertTrue(Bytes.memcmp(big.key(), small.key()) > 0);

    // The following tests test our assumptions on unsigned data types sorting from KeyEncoder
    byte four = 4;
    byte onHundredTwentyFour = -4;
    four = Bytes.xorLeftMostBit(four);
    onHundredTwentyFour = Bytes.xorLeftMostBit(onHundredTwentyFour);
    assertTrue(four < onHundredTwentyFour);

    byte[] threeHundred = Bytes.fromInt(300);
    byte[] reallyBigNumber = Bytes.fromInt(-300);
    threeHundred[0] = Bytes.xorLeftMostBit(threeHundred[0]);
    reallyBigNumber[3] = Bytes.xorLeftMostBit(reallyBigNumber[3]);
    assertTrue(Bytes.memcmp(threeHundred, reallyBigNumber) < 0);
  }
}