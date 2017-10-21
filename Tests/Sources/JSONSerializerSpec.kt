package tests

import com.github.fluidsonic.fluid.json.JSONCoderContext
import com.github.fluidsonic.fluid.json.JSONSerializer
import com.github.fluidsonic.fluid.json.serialize
import com.winterbe.expekt.should
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import java.io.StringWriter
import java.io.Writer


internal object JSONSerializerSpec : Spek({

	describe("JSONSerializer") {

		it(".default() returns a default serializer") {
			plainData.testEncoding(JSONSerializer.default()::serialize)
		}

		it(".serialize() shortcuts pass correct values") {
			var expectedContext = JSONCoderContext.empty

			val parser = object : JSONSerializer<JSONCoderContext> {
				override fun serialize(value: Any?, destination: Writer, context: JSONCoderContext) {
					context.should.equal(expectedContext)
				}
			}

			parser.serialize("")
			parser.serialize("", destination = StringWriter())

			expectedContext = TestCoderContext()
			parser.serialize("", context = expectedContext)
		}
	}
})