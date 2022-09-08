package regime.market.information

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.SaveMode

import regime.helper.RegimeJdbcHelper
import regime.market.{Command, Information, RegimeTask}
import regime.market.Common.{connMarket, connBiz}

object AShareCalendar extends RegimeTask with Information {
  val appName = "AShareCalendar"

  val query = """
  SELECT
    OBJECT_ID AS object_id,
    TRADE_DAYS AS trade_days,
    S_INFO_EXCHMARKET AS exchange
  FROM
    ASHARECALENDAR
  """

  val saveTo     = "ashare_calendar"
  val primaryKey = ("PK_ashare_calendar", Seq("object_id"))
  val index      = ("IDX_ashare_calendar", Seq("trade_days"))

  def process(args: String*)(implicit spark: SparkSession): Unit = {
    args.toList match {
      case Command.SyncAll :: _ =>
        syncAll(connMarket, query, connBiz, saveTo)
      case Command.ExecuteOnce :: _ =>
        createPrimaryKeyAndIndex(
          connBiz,
          saveTo,
          primaryKey,
          Seq(index)
        )
      case _ =>
        throw new Exception("Invalid command")
    }
  }
}