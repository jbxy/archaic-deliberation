package regime.helper

import java.sql.Connection
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.execution.datasources.jdbc.JdbcOptionsInWrite
import org.apache.spark.sql.execution.datasources.jdbc.JdbcUtils
import org.apache.spark.sql.jdbc.JdbcDialects
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.execution.datasources.jdbc.JDBCOptions
import org.apache.spark.sql.connector.catalog.TableChange
import org.apache.spark.sql.connector.catalog.index.TableIndex
import org.apache.spark.sql.SaveMode
import org.apache.spark.sql.SparkSession

import regime.{Conn, ConnTableColumn, DriverType, Global}

class RegimeJdbcHelper(conn: Conn) {
  import RegimeSqlHelper._
  import RegimeJdbcHelper._

  // ===============================================================================================
  // private helper functions
  // ===============================================================================================

  /** Create a JdbcOptions
    *
    * @param params
    * @return
    */
  private def genOptions(params: (String, String)*): JdbcOptionsInWrite = {
    val op = params.foldLeft(conn.options) { (acc, ele) =>
      acc + (ele._1 -> ele._2)
    }

    new JdbcOptionsInWrite(op)
  }

  private def jdbcOptionsAddTable(table: String): JdbcOptionsInWrite =
    genOptions(("dbtable", table))

  private def jdbcOptionsAddTruncateTable(
      table: String,
      cascade: Boolean = false
  ): JdbcOptionsInWrite =
    genOptions(
      ("dbtable", table),
      ("cascadeTruncate", cascade.toString())
    )

  private def jdbcOptionsAddSaveTable(
      table: String,
      batchSize: Option[Integer] = None,
      isolationLevel: Option[String] = None,
      numPartition: Option[Integer] = None
  ): JdbcOptionsInWrite = {
    val op = conn.options +
      ("dbtable" -> table) ++
      batchSize.map(v => ("batchsize" -> v.toString())) ++
      isolationLevel.map(v => ("isolationLevel" -> v)) ++
      numPartition.map(v => ("numPartitions" -> v.toString()))

    new JdbcOptionsInWrite(op)
  }

  private def jdbcOptionsAddCreateTable(
      createTableColumnTypes: Option[String],
      createTableOptions: Option[String],
      tableComment: Option[String]
  ): JdbcOptionsInWrite = {
    val op = conn.options ++
      createTableColumnTypes.map(("createTableColumnTypes" -> _)) ++
      createTableOptions.map(("createTableOptions" -> _)) ++
      tableComment.map(("tableComment" -> _))

    new JdbcOptionsInWrite(op)
  }

  /** Create a connection by extra parameters
    *
    * @param params
    * @return
    */
  private def genConn(params: (String, String)*): Connection = {
    val jdbcOptions = genOptions(params: _*)

    JdbcDialects
      .get(conn.url)
      .createConnectionFactory(jdbcOptions)(-1)
  }

  /** Create a connection by options
    *
    * @param options
    * @return
    */
  private def genConn(options: JdbcOptionsInWrite): Connection = {
    JdbcDialects
      .get(conn.url)
      .createConnectionFactory(options)(-1)
  }

  /** Create a connectionOptions with extra parameters
    *
    * @param params
    * @return
    */
  private def genConnOpt(params: (String, String)*): ConnectionOptions = {
    val jdbcOptions = genOptions(params: _*)

    val connection = JdbcDialects
      .get(conn.url)
      .createConnectionFactory(jdbcOptions)(-1)

    ConnectionOptions(connection, jdbcOptions)
  }

  /** Create a connectionOptions
    *
    * @param options
    * @return
    */
  private def genConnOpt(options: JdbcOptionsInWrite): ConnectionOptions = {
    val connection = JdbcDialects
      .get(conn.url)
      .createConnectionFactory(options)(-1)

    ConnectionOptions(connection, options)
  }

  /** Execute a raw SQL statement
    *
    * @param conn
    * @param options
    * @param sql
    * @param f
    */
  private def executeUpdate(
      conn: Connection,
      options: JDBCOptions,
      sql: String
  )(f: Int => Unit): Unit = {
    val statement = conn.createStatement
    try {
      statement.setQueryTimeout(options.queryTimeout)
      val eff = statement.executeUpdate(sql)
      f(eff)
    } finally {
      statement.close()
    }
  }

  // ===============================================================================================
  // general functions
  // 1. runSaveStatement
  // 1. readTable
  // 1. saveTable
  // 1. upsertTable
  // 1. deleteByConditions
  // 1. tableExists
  // 1. dropTable
  // 1. truncateTable
  // 1. getTableSchema
  // 1. createTable
  // 1. renameTable
  // 1. alterTable
  // 1. createSchema
  // 1. schemaExists
  // 1. listSchemas
  // 1. alterSchemaComment
  // 1. removeSchemaComment
  // 1. dropSchema
  // 1. createPrimaryKey
  // 1. dropPrimaryKey
  // 1. createUniqueConstraint
  // 1. dropUniqueConstraint
  // 1. createIndex
  // 1. indexExists
  // 1. dropIndex
  // 1. listIndexes
  // 1. createForeignKey
  // 1. dropForeignKey
  // ===============================================================================================

  /** Raw statement for saving a DataFrame
    *
    * @param df
    * @param statement
    * @param options
    */
  def runSaveStatement(
      df: DataFrame,
      statement: String,
      options: JdbcOptionsInWrite
  ): Unit = {
    // IMPORTANT!
    // These variables are required under this context scope, because of `.rdd.foreachPartition` broadcasting.
    val url            = options.url
    val dialect        = JdbcDialects.get(url)
    val table          = options.table
    val updateSchema   = df.schema
    val batchSize      = options.batchSize
    val isolationLevel = options.isolationLevel

    val repartitionedDF = options.numPartitions match {
      case Some(n) if n < df.rdd.getNumPartitions => df.coalesce(n)
      case _                                      => df
    }

    repartitionedDF.rdd.foreachPartition { iterator =>
      JdbcUtils.savePartition(
        table,
        iterator,
        updateSchema,
        statement,
        batchSize,
        dialect,
        isolationLevel,
        options
      )
    }
  }

  /** Load a DataFrame by a SQL query
    *
    * `.option("dbtable", s"($sql) as subq")` for partitioning read (TODO)
    *
    * @param sparkå
    * @param sql
    * @return
    */
  def readTable(spark: SparkSession, sql: String): DataFrame =
    spark.read
      .format("jdbc")
      .options(conn.options)
      // .option("encoding", "UTF-8")
      .option("query", sql)
      .load()

  def readTable(sql: String)(implicit spark: SparkSession): DataFrame =
    readTable(spark, sql)

  /** Save a DataFrame to the database in a single transaction
    *
    * @param df
    * @param table
    * @param isCaseSensitive
    * @param batchSize
    * @param isolationLevel
    * @param numPartition
    */
  def saveTable(
      df: DataFrame,
      table: String,
      isCaseSensitive: Boolean,
      batchSize: Option[Integer] = None,
      isolationLevel: Option[String] = None,
      numPartition: Option[Integer] = None
  ): Unit =
    JdbcUtils.saveTable(
      df,
      None,
      isCaseSensitive,
      jdbcOptionsAddSaveTable(
        table,
        batchSize,
        isolationLevel,
        numPartition
      )
    )

  /** Save a DataFrame to the database with mode option
    *
    * @param df
    * @param table
    * @param mode
    */
  def saveTable(
      df: DataFrame,
      table: String,
      mode: SaveMode
  ): Unit = {
    df.write
      .format("jdbc")
      .options(conn.options)
      .option("dbtable", table)
      // .option("encoding", "UTF-8")
      .mode(mode)
      .save()
  }

  def saveTable(
      df: DataFrame,
      table: String,
      mode: String
  ): Unit = {
    df.write
      .format("jdbc")
      .options(conn.options)
      .option("dbtable", table)
      // .option("encoding", "UTF-8")
      .mode(mode)
      .save()
  }

  /** Upsert table.
    *
    * Make sure the input DataFrame has the same schema as the database table. Also, unique
    * constraint must be set before calling this method.
    *
    * @param df
    * @param table
    * @param conditions
    * @param isCaseSensitive
    * @param conflictColumns
    * @param conflictAction
    */
  def upsertTable(
      df: DataFrame,
      table: String,
      conditions: Option[String],
      isCaseSensitive: Boolean,
      conflictColumns: Seq[String],
      conflictAction: UpsertAction.Value
  ): Unit = {
    val upsertStmt = generateUpsertStatement(
      conn,
      table,
      df.schema,
      conditions,
      isCaseSensitive,
      conflictColumns,
      conflictAction
    )

    runSaveStatement(
      df,
      upsertStmt,
      jdbcOptionsAddTable(table)
    )
  }

  /** Upsert table.
    *
    * Make sure the input DataFrame has the same schema as the database table.
    *
    * @param df
    * @param table
    * @param conditions
    * @param isCaseSensitive
    * @param conflictColumns
    * @param conflictAction
    */
  def upsertTable(
      df: DataFrame,
      table: String,
      conditions: Option[String],
      isCaseSensitive: Boolean,
      conflictColumns: Seq[String],
      conflictAction: String
  ): Unit = {
    val ca = conflictAction match {
      case "doNothing" => UpsertAction.DoNothing
      case "doUpdate"  => UpsertAction.DoUpdate
      case _           => throw new Exception("No upsert action matched: doNothing/doUpdate")
    }

    upsertTable(
      df,
      table,
      conditions,
      isCaseSensitive,
      conflictColumns,
      ca
    )
  }

  /** Delete data from a table
    *
    * @param table
    * @param conditions
    */
  def deleteByConditions(table: String, conditions: String, resp: Int => Unit): Unit = {
    val query = s"""
    DELETE FROM ${table} WHERE $conditions
    """
    val co = genConnOpt(jdbcOptionsAddTable(table))

    executeUpdate(co.conn, co.opt, query)(resp)
  }

  def deleteByConditions(table: String, conditions: String): Unit =
    deleteByConditions(table, conditions, _ => {})

  /** Delete null data based on selected columns
    *
    * @param table
    * @param columns
    */
  def deleteNullValues(
      table: String,
      columns: Seq[String],
      conjunction: Conjunction.Value,
      respFn: Int => Unit
  ): Unit = {
    val cs         = columns.map(c => s"$c IS NULL").toList
    val conditions = Global.listIntersperse(cs, Conjunction.generateString(conjunction))
    deleteByConditions(table, conditions.mkString(" "))
  }

  def deleteNullValues(
      table: String,
      columns: Seq[String],
      conjunction: Conjunction.Value
  ): Unit = deleteNullValues(table, columns, conjunction, _ => {})

  /** Check if a table exists
    *
    * @param table
    * @return
    */
  def tableExists(table: String): Boolean = {
    val co = genConnOpt(jdbcOptionsAddTable(table))

    JdbcUtils.tableExists(co.conn, co.opt)
  }

  /** Drop a table
    *
    * @param table
    */
  def dropTable(table: String): Unit = {
    val co = genConnOpt(jdbcOptionsAddTable(table))

    JdbcUtils.dropTable(co.conn, table, co.opt)
  }

  /** Truncate a table
    *
    * @param table
    * @param cascadeTruncate
    */
  def truncateTable(table: String, cascadeTruncate: Boolean = false): Unit = {
    val co = genConnOpt(jdbcOptionsAddTruncateTable(table, cascadeTruncate))

    JdbcUtils.truncateTable(co.conn, co.opt)
  }

  /** Get a table's schema if it exists
    *
    * @param table
    * @return
    */
  def getTableSchema(table: String): Option[StructType] = {
    val co = genConnOpt(jdbcOptionsAddTable(table))

    JdbcUtils.getSchemaOption(co.conn, co.opt)
  }

  /** Create a table with a given schema
    *
    * @param table
    * @param schema
    * @param isCaseSensitive
    * @param createTableColumnTypes
    * @param createTableOptions
    * @param tableComment
    */
  def createTable(
      table: String,
      schema: StructType,
      isCaseSensitive: Boolean,
      createTableColumnTypes: Option[String],
      createTableOptions: Option[String],
      tableComment: Option[String]
  ): Unit = {
    val co = genConnOpt(
      jdbcOptionsAddCreateTable(
        createTableColumnTypes,
        createTableOptions,
        tableComment
      )
    )
    JdbcUtils.createTable(
      co.conn,
      table,
      schema,
      isCaseSensitive,
      co.opt
    )
  }

  /** Rename a table from the JDBC database
    *
    * @param oldTable
    * @param newTable
    */
  def renameTable(oldTable: String, newTable: String): Unit = {
    val co = genConnOpt()

    JdbcUtils.renameTable(co.conn, oldTable, newTable, co.opt)
  }

  /** Update a table from the JDBC database
    *
    * @param table
    * @param changes
    */
  def alterTable(table: String, changes: Seq[TableChange]): Unit = {
    val co = genConnOpt(jdbcOptionsAddTable(table))

    JdbcUtils.alterTable(co.conn, table, changes, co.opt)
  }

  /** Create a schema
    *
    * @param schema
    * @param comment
    */
  def createSchema(schema: String, comment: String): Unit = {
    val co = genConnOpt()

    JdbcUtils.createSchema(co.conn, co.opt, schema, comment)
  }

  /** Check a schema if exists
    *
    * @param schema
    * @return
    */
  def schemaExists(schema: String): Boolean = {
    val co = genConnOpt()

    JdbcUtils.schemaExists(co.conn, co.opt, schema)
  }

  /** List all schema
    *
    * @return
    */
  def listSchemas(): Array[Array[String]] = {
    val co = genConnOpt()

    JdbcUtils.listSchemas(co.conn, co.opt)
  }

  /** Alter a schema's comment
    *
    * @param schema
    * @param comment
    */
  def alterSchemaComment(schema: String, comment: String): Unit = {
    val co = genConnOpt()

    JdbcUtils.alterSchemaComment(co.conn, co.opt, schema, comment)
  }

  /** Remove a schema's comment
    *
    * @param schema
    */
  def removeSchemaComment(schema: String): Unit = {
    val co = genConnOpt()

    JdbcUtils.removeSchemaComment(co.conn, co.opt, schema)
  }

  /** Drop a schema
    *
    * @param schema
    * @param cascade
    */
  def dropSchema(schema: String, cascade: Boolean): Unit = {
    val co = genConnOpt()

    JdbcUtils.dropSchema(co.conn, co.opt, schema, cascade)
  }

  /** Create a primary key
    *
    * @param tableName
    * @param primaryKeyName
    * @param columns
    */
  def createPrimaryKey(
      tableName: String,
      primaryKeyName: String,
      columns: Seq[String]
  ): Unit = {
    val query = generateCreatePrimaryKeyStatement(conn, tableName, primaryKeyName, columns)
    val co    = genConnOpt(jdbcOptionsAddTable(tableName))

    executeUpdate(co.conn, co.opt, query)(_ => {})
  }

  /** Drop a primary key
    *
    * @param tableName
    * @param primaryKeyName
    */
  def dropPrimaryKey(tableName: String, primaryKeyName: String): Unit = {
    val query = generateDropPrimaryKeyStatement(conn, tableName, primaryKeyName)
    val co    = genConnOpt(jdbcOptionsAddTable(tableName))

    executeUpdate(co.conn, co.opt, query)(_ => {})
  }

  /** Create a unique constraint for a table
    *
    * @param table
    * @param name
    * @param tableColumnNames
    */
  def createUniqueConstraint(
      table: String,
      name: String,
      tableColumnNames: Seq[String]
  ): Unit = {
    val query = s"""
    ALTER TABLE $table ADD CONSTRAINT $name
    UNIQUE (${tableColumnNames.mkString(",")})
    """
    val co = genConnOpt(jdbcOptionsAddTable(table))

    executeUpdate(co.conn, co.opt, query)(_ => {})
  }

  /** Drop a unique constraint from a table
    *
    * @param table
    * @param name
    */
  def dropUniqueConstraint(table: String, name: String): Unit = {
    val query = s"""
    ALTER TABLE $table DROP CONSTRAINT $name
    """
    val co = genConnOpt(jdbcOptionsAddTable(table))

    executeUpdate(co.conn, co.opt, query)(_ => {})
  }

  /** Create an index
    *
    * @param table
    * @param index
    * @param columns
    * @param columnsProperties
    * @param properties
    */
  def createIndex(
      table: String,
      index: String,
      columns: Seq[String]
  ): Unit = {
    val co    = genConnOpt(jdbcOptionsAddTable(table))
    val query = generateCreateIndexStatement(conn, table, index, columns)

    executeUpdate(co.conn, co.opt, query)(_ => {})
  }

  /** Check an index if exists
    *
    * @param table
    * @param index
    * @return
    */
  def indexExists(table: String, index: String): Boolean = {
    throw new UnsupportedOperationException("indexExists not supported currently")
  }

  /** Drop an index
    *
    * @param table
    * @param index
    */
  def dropIndex(table: String, index: String): Unit = {
    val query = generateDropIndexStatement(conn, table, index)
    val co    = genConnOpt(jdbcOptionsAddTable(table))

    executeUpdate(co.conn, co.opt, query)(_ => {})
  }

  /** List indices from a table
    *
    * @param table
    * @return
    */
  def listIndexes(table: String): Array[TableIndex] = {
    throw new UnsupportedOperationException("listIndexes not supported currently")
  }

  /** Create a foreign key
    *
    * @param fromTable
    * @param fromTableColumn
    * @param foreignKeyName
    * @param toTable
    * @param toTableColumn
    * @param onDelete
    * @param onUpdate
    */
  def createForeignKey(
      fromTable: String,
      fromTableColumn: String,
      foreignKeyName: String,
      toTable: String,
      toTableColumn: String,
      onDelete: Option[ForeignKeyModifyAction.Value],
      onUpdate: Option[ForeignKeyModifyAction.Value]
  ): Unit = {
    val query = generateCreateForeignKey(
      conn,
      fromTable,
      fromTableColumn,
      foreignKeyName,
      toTable,
      toTableColumn,
      onDelete,
      onUpdate
    )
    val co = genConnOpt()

    executeUpdate(co.conn, co.opt, query)(_ => {})
  }

  /** Drop a foreign key
    *
    * @param table
    * @param name
    */
  def dropForeignKey(table: String, name: String): Unit = {
    val query = generateDropForeignKey(conn, table, name)
    val co    = genConnOpt()

    executeUpdate(co.conn, co.opt, query)(_ => {})
  }
}

object RegimeJdbcHelper {

  case class ConnectionOptions(conn: Connection, opt: JdbcOptionsInWrite)

  def apply(conn: Conn): RegimeJdbcHelper = new RegimeJdbcHelper(conn)
}
