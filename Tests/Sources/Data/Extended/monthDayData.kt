package tests

import java.time.MonthDay


internal val monthDayData: TestData<MonthDay> = TestData(
	symmetric = mapOf(
		MonthDay.parse("--12-03") to "\"--12-03\""
	)
)
