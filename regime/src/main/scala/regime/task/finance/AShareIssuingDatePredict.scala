package regime.task.finance

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.SaveMode

import regime.helper.RegimeJdbcHelper
import regime.task.{Command, Finance, RegimeTask}
import regime.task.Common.{connMarket, connBiz}

object AShareIssuingDatePredict extends RegimeTask with Finance {
  val appName: String = "AShareIssuingDatePredict"

  val query = """
  ASHAREISSUINGDATEPREDICT
  """

  def process(args: String*)(implicit spark: SparkSession): Unit = {
    //
  }

}
