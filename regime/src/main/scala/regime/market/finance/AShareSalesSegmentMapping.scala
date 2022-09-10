package regime.market.finance

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.SaveMode

import regime.helper.RegimeJdbcHelper
import regime.market.{Command, Finance, RegimeTask}
import regime.market.Common.{connMarket, connBiz}

object AShareSalesSegmentMapping extends RegimeTask with Finance {
  val query = """
  ASHARESALESSEGMENTMAPPING
  """

  def process(args: String*)(implicit spark: SparkSession): Unit = {
    //
  }

}
