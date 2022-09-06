package regime.task

import regime.task.information._
import regime.task.timeseries._
import regime.task.finance._

sealed trait TaskCategory {
  val name: String
}

trait Information {
  val appName: String
}

trait TimeSeries {
  val appName: String
}

trait Finance {
  val appName: String
}

trait Consensus {
  val appName: String
}

object TaskCategory {
  def unapply(str: String): Option[TaskCategory] = str match {
    case Information.name => Some(Information)
    case TimeSeries.name  => Some(TimeSeries)
    case Finance.name     => Some(Finance)
    case _                => None
  }
}

object Information extends TaskCategory {
  val name = "Information"

  def unapply(str: String): Option[Information] = str match {
    case AShareCalendar.appName          => Some(AShareCalendar)
    case AShareInformationCitics.appName => Some(AShareInformationCitics)
    case AShareInformationWind.appName   => Some(AShareInformationWind)
    // TODO
    case AIndexInformation.appName => Some(AIndexInformation)
    // TODO
    case AIndexInformationCitics.appName => Some(AIndexInformationCitics)
    // TODO
    case AIndexInformationWind.appName => Some(AIndexInformationWind)
    case _                             => None
  }
}

object TimeSeries extends TaskCategory {
  val name = "TimeSeries"

  def unapply(str: String): Option[TimeSeries] = str match {
    case AShareEODPrices.appName => Some(AShareEODPrices)
    // TODO
    case AShareEODDerivativeIndicator.appName => Some(AShareEODDerivativeIndicator)
    // TODO
    case AShareYield.appName => Some(AShareYield)
    // TODO
    case AShareL2Indicators.appName      => Some(AShareL2Indicators)
    case AShareEXRightDividend.appName   => Some(AShareEXRightDividend)
    case AShareTradingSuspension.appName => Some(AShareTradingSuspension)
    // TODO
    case AIndexEODPrices.appName => Some(AIndexEODPrices)
    // TODO
    case AIndexCiticsEODPrices.appName => Some(AIndexCiticsEODPrices)
    // TODO
    case AIndexWindEODPrices.appName => Some(AIndexWindEODPrices)
    // TODO
    case AIndexValuation.appName => Some(AIndexValuation)
    // TODO
    case AIndexFinancialDerivative.appName => Some(AIndexFinancialDerivative)
    case _                                 => None
  }
}

object Finance extends TaskCategory {
  val name = "Finance"

  def unapply(str: String): Option[Finance] = str match {
    case AShareBalanceSheet.appName => Some(AShareBalanceSheet)
    case AShareCashFlow.appName     => Some(AShareCashFlow)
    case AShareIncome.appName       => Some(AShareIncome)
    // TODO
    case AShareIssuingDatePredict.appName => Some(AShareIssuingDatePredict)
    // TODO
    case AShareFinancialExpense.appName => Some(AShareFinancialExpense)
    // TODO
    case AShareProfitExpress.appName => Some(AShareProfitExpress)
    // TODO
    case AShareProfitNotice.appName => Some(AShareProfitNotice)
    // TODO
    case AShareSalesSegmentMapping.appName => Some(AShareSalesSegmentMapping)
    // TODO
    case AShareSalesSegment.appName => Some(AShareSalesSegment)
    case _                          => None
  }
}

object Consensus extends TaskCategory {
  val name: String = "Consensus"

  def unapply(str: String): Option[Consensus] = str match {
    case _ => None
  }
}
