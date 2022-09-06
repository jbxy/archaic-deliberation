package regime.task

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.SaveMode

import regime.helper._
import regime.Conn

trait RegimeTask extends RegimeSpark {

  // ===============================================================================================
  // Execute functions
  // ===============================================================================================

  /** Sync all data from one table to another.
    *
    * Make sure OOM issue when syncing a big table.
    *
    * @param from
    * @param sql
    * @param to
    * @param saveTo
    * @param spark
    */
  def syncAll(
      from: Conn,
      sql: String,
      to: Conn,
      saveTo: String
  )(implicit spark: SparkSession): Unit = {
    val df = RegimeJdbcHelper(from).readTable(sql)

    RegimeJdbcHelper(to).saveTable(df, saveTo, SaveMode.Overwrite)
  }

  /** Sync data from one table to another by upsert.
    *
    * @param from
    * @param sql
    * @param to
    * @param onConflictColumns
    * @param saveTo
    * @param spark
    */
  def syncUpsert(
      from: Conn,
      sql: String,
      to: Conn,
      onConflictColumns: Seq[String],
      saveTo: String
  )(implicit spark: SparkSession): Unit = {
    val df = RegimeJdbcHelper(from).readTable(sql)

    RegimeJdbcHelper(to).upsertTable(
      df,
      saveTo,
      None,
      false,
      onConflictColumns,
      RegimeJdbcHelper.UpsertAction.DoUpdate
    )
  }

  // ===============================================================================================
  // ExecuteOnce functions
  // ===============================================================================================

  def createPrimaryKey(
      conn: Conn,
      table: String,
      primaryKeyName: String,
      primaryColumn: Seq[String]
  )(implicit spark: SparkSession): Unit =
    RegimeJdbcHelper(conn).createPrimaryKey(table, primaryKeyName, primaryColumn)

  def createIndexes(
      conn: Conn,
      table: String,
      indexes: Seq[(String, Seq[String])]
  )(implicit spark: SparkSession): Unit = {
    val helper = RegimeJdbcHelper(conn)

    indexes.foreach(ele => helper.createIndex(table, ele._1, ele._2))
  }

  def createPrimaryKeyAndIndex(
      conn: Conn,
      table: String,
      primaryKey: (String, Seq[String]),
      indexes: Seq[(String, Seq[String])]
  )(implicit spark: SparkSession): Unit = {
    val helper = RegimeJdbcHelper(conn)

    // primary key
    helper.createPrimaryKey(table, primaryKey._1, primaryKey._2)

    // indexes
    indexes.foreach(ele => helper.createIndex(table, ele._1, ele._2))
  }

}