/*
 * Copyright (C) 1999-2019 Alibaba Group Holding Limited
 */
package com.alibaba.innodb.java.reader.schema;

import com.alibaba.innodb.java.reader.CharsetMapping;
import com.alibaba.innodb.java.reader.column.ColumnType;
import com.alibaba.innodb.java.reader.util.Symbol;

import org.apache.commons.lang3.StringUtils;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import static com.alibaba.innodb.java.reader.Constants.CONST_UNSIGNED;
import static com.alibaba.innodb.java.reader.column.ColumnType.CHAR;
import static com.alibaba.innodb.java.reader.column.ColumnType.DATETIME;
import static com.alibaba.innodb.java.reader.column.ColumnType.DECIMAL;
import static com.alibaba.innodb.java.reader.column.ColumnType.TIME;
import static com.alibaba.innodb.java.reader.column.ColumnType.TIMESTAMP;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Column description.
 *
 * @author xu.zx
 */
@ToString
@EqualsAndHashCode
@Getter
public class Column {

  @EqualsAndHashCode.Exclude
  private Schema schema;

  private String name;

  /**
   * Type is in upper case.
   *
   * @see com.alibaba.innodb.java.reader.column.ColumnType
   */
  private String type;

  private boolean nullable;

  private boolean isPrimaryKey;

  /**
   * This represents display size for integer type, max variable length for varchar
   * type or fixed length for char type. This can only be set internally.
   */
  private int length;

  /**
   * For DECIMAL(M, D), D is the number of digits to the right of the decimal point
   * (the scale). It has a range of 0 to 30 and must be no larger than M.
   */
  private int scale;

  /**
   * For DECIMAL(M, D), M is the maximum number of digits (the precision). It has a range
   * of 1 to 65. It has a range of 0 to 30 and must be no larger than M.
   * <p>
   * Since MySQL 5.6, there has fractional seconds support for TIME, DATETIME, and TIMESTAMP
   * values, with up to microseconds (6 digits) precision:
   * <p>
   * To define a column that includes a fractional seconds part, use the syntax type_name(fsp),
   * where type_name is TIME, DATETIME, or TIMESTAMP, and fsp is the fractional seconds
   * precision. For example:
   * <p>
   * CREATE TABLE t1 (t TIME(3), dt DATETIME(6));
   * The fsp value, if given, must be in the range 0 to 6. A value of 0 signifies that there
   * is no fractional part. If omitted, the default precision is 0. (This differs from the
   * standard SQL default of 6, for compatibility with previous MySQL versions.)
   */
  private int precision;

  /**
   * Character data types (CHAR, VARCHAR, the TEXT types, ENUM, SET, and any synonyms) can
   * include CHARACTER SET to specify the character set for the column. CHARSET is a synonym
   * for CHARACTER SET.
   */
  private String charset;

  private String javaCharset;

  private String collation;

  /**
   * //TODO make sure this is the right way to implement
   * For example, if table charset set to utf8, then it will consume up to 3 bytes for one character.
   * if it is utf8mb4, then it must be set to 4.
   */
  @ToString.Exclude
  private int maxBytesPerChar = 1;

  /**
   * //TODO
   * For charset like utf8mb4, CHAR will be treated as VARCHAR.
   * Note this can only be set internally.
   */
  @ToString.Exclude
  private boolean isVarLenChar;

  public void setSchema(Schema schema) {
    this.schema = schema;
  }

  public Column setType(final String type) {
    checkArgument(StringUtils.isNotEmpty(type), "Column type should not be empty");
    String t = type.trim();
    String[] part = t.split(Symbol.SPACE);
    handleRawType(part[0]);
    if (part.length > 1) {
      handleUnsignedIfPresent(part[1]);
    }
    return this;
  }

  public Column setName(String name) {
    checkArgument(StringUtils.isNotEmpty(name), "Column name should not be empty");
    this.name = name
        .replace(Symbol.BACKTICK, Symbol.EMPTY)
        .replace(Symbol.DOUBLE_QUOTE, Symbol.EMPTY);
    return this;
  }

  public Column setNullable(boolean nullable) {
    this.nullable = nullable;
    return this;
  }

  public Column setPrimaryKey(boolean primaryKey) {
    this.isPrimaryKey = primaryKey;
    return this;
  }

  public Column setCharset(String charset) {
    checkArgument(StringUtils.isNotEmpty(type), "Type should be set before charset");
    this.charset = charset;
    this.javaCharset = CharsetMapping.getJavaCharsetForMysqlCharset(charset);
    this.maxBytesPerChar = CharsetMapping.getMaxByteLengthForMysqlCharset(charset);
    if (CHAR.equals(type) && maxBytesPerChar > 1) {
      isVarLenChar = true;
    }
    return this;
  }

  /**
   * Get java encoding charset for column.
   * If charset is defined in column, use it, or else use table java charset.
   */
  public String getJavaCharset() {
    if (javaCharset != null) {
      return javaCharset;
    }
    if (schema != null) {
      return schema.getJavaCharset();
    }
    return null;
  }

  /**
   * Get charset for column.
   * If charset is defined in column, use it, or else use table charset.
   */
  public String getCharset() {
    if (charset != null) {
      return charset;
    }
    if (schema != null) {
      return schema.getCharset();
    }
    return null;
  }

  public Column setCollation(String collation) {
    this.collation = collation;
    return this;
  }

  public Column setVarLenChar(boolean varLenChar) {
    isVarLenChar = varLenChar;
    return this;
  }

  public boolean isVariableLength() {
    return isVarLenChar || ColumnType.VARIABLE_LENGTH_TYPES.contains(type);
  }

  public boolean isFixedLength() {
    if (isVarLenChar) {
      return false;
    }
    return ColumnType.CHAR.equals(type) || ColumnType.BINARY.equals(type);
  }

  private void handleRawType(String type) {
    if (type.contains(Symbol.LEFT_PARENTHESES) && type.contains(Symbol.RIGHT_PARENTHESES)) {
      setTypeToUppercase(type.substring(0, type.indexOf(Symbol.LEFT_PARENTHESES)));
      String wrappedString = StringUtils.substringBetween(type, Symbol.LEFT_PARENTHESES, Symbol.RIGHT_PARENTHESES);
      checkState(StringUtils.isNotEmpty(wrappedString), "String cannot be empty between ( and ), for example int(10)");
      switch (this.type) {
        case DECIMAL:
          handleDecimal(wrappedString);
          break;
        case DATETIME:
        case TIMESTAMP:
        case TIME:
          handlePrecision(wrappedString);
          break;
        default:
          this.length = Integer.parseInt(StringUtils.substringBetween(type,
              Symbol.LEFT_PARENTHESES, Symbol.RIGHT_PARENTHESES));
      }
    } else {
      setTypeToUppercase(type);
      // Set precision to 10 if no DECIMAL precision and scale specified
      if (DECIMAL.equals(this.type)) {
        this.precision = 10;
      }
    }
  }

  private void setTypeToUppercase(String type) {
    this.type = type.toUpperCase();
  }

  private void handleDecimal(String wrappedString) {
    if (wrappedString.contains(Symbol.COMMA)) {
      String[] innerPart = wrappedString.split(Symbol.COMMA);
      this.precision = Integer.parseInt(innerPart[0]);
      this.scale = Integer.parseInt(innerPart[1]);
    } else {
      this.precision = Integer.parseInt(wrappedString);
    }
  }

  private void handlePrecision(String wrappedString) {
    this.precision = Integer.parseInt(wrappedString);
  }

  private void handleUnsignedIfPresent(String sign) {
    if (CONST_UNSIGNED.contains(sign)) {
      checkState(StringUtils.isNotEmpty(type) && StringUtils.isAllUpperCase(type));
      this.type = type + " UNSIGNED";
    }
  }

}