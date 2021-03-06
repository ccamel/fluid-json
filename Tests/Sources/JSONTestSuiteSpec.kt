package tests

import com.github.fluidsonic.fluid.json.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import java.io.File
import java.io.FileReader


internal object JSONTestSuiteSpec : Spek({

	describe("JSON Test Suite (https://github.com/nst/JSONTestSuite)") {
		val nonRecursiveOnlyFileNames = setOf(
			"n_structure_100000_opening_arrays.json",
			"n_structure_open_array_object.json"
		)

		listOf(
			TestEnvironment("Default (non-recursive) Parser", JSONParser.nonRecursive, parserIsRecursive = false),
			TestEnvironment("Recursive Parser", JSONParser.default, parserIsRecursive = true)
		)
			.forEach { environment ->
				describe(environment.name) {
					File("Tests/Libraries/JSONTestSuite/test_parsing")
						.listFiles()
						.filter { it.name.endsWith(".json") }
						.forEach testCase@ { file ->
							if (environment.parserIsRecursive && nonRecursiveOnlyFileNames.contains(file.name)) {
								return@testCase
							}

							val expectedBehavior = when (file.name.substringBefore('_')) {
								"i" -> null
								"n" -> Behavior.rejected
								"y" -> Behavior.accepted
								else -> return@testCase
							}

							it(file.name) {
								val result = try {
									environment.parser.parseValueOrNull(FileReader(file))
								}
								catch (e: JSONException) {
									e
								}
								catch (e: StackOverflowError) {
									throw AssertionError("Stack overflow in '${file.name}'")
								}

								val actualBehavior = if (result is JSONException) Behavior.rejected else Behavior.accepted

								if (expectedBehavior != null && actualBehavior != expectedBehavior) {
									var message = "Expected '${file.name}' to be $expectedBehavior but was $actualBehavior"
									if (result !is Exception) {
										message = "$message: $result"
									}

									throw RuntimeException(message, result as? Exception)
								}
							}
						}
				}
			}
	}
}) {

	private enum class Behavior {

		accepted,
		rejected
	}


	private class TestEnvironment(
		val name: String,
		val parser: JSONParser,
		val parserIsRecursive: Boolean
	)
}
