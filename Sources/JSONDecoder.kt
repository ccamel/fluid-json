package com.github.fluidsonic.fluid.json

import java.io.Reader
import java.io.StringReader


interface JSONDecoder<out Context : JSONCoderContext> : JSONReader {

	val context: Context


	override fun readValue() =
		readValueOfType<Any>()


	fun <Value : Any> readValueOfType(valueType: JSONCodableType<Value>): Value


	companion object {

		fun builder(): BuilderForCodecs<JSONCoderContext> =
			BuilderForCodecsImpl(context = JSONCoderContext.empty)


		fun <Context : JSONCoderContext> builder(context: Context): BuilderForCodecs<Context> =
			BuilderForCodecsImpl(context = context)


		interface BuilderForCodecs<out Context : JSONCoderContext> {

			fun codecs(
				vararg providers: JSONCodecProvider<Context>,
				base: JSONCodecProvider<JSONCoderContext>? = JSONCodecProvider.extended
			) =
				codecs(providers = providers.toList(), base = base)


			fun codecs(
				providers: Iterable<JSONCodecProvider<Context>>,
				base: JSONCodecProvider<JSONCoderContext>? = JSONCodecProvider.extended
			): BuilderForSource<Context>
		}


		private class BuilderForCodecsImpl<out Context : JSONCoderContext>(
			private val context: Context
		) : BuilderForCodecs<Context> {

			override fun codecs(
				providers: Iterable<JSONCodecProvider<Context>>,
				base: JSONCodecProvider<JSONCoderContext>?
			) =
				BuilderForSourceImpl(
					context = context,
					codecProvider = JSONCodecProvider.of(providers = providers, base = base)
				)
		}


		interface BuilderForSource<out Context : JSONCoderContext> {

			fun source(source: JSONReader): Builder<Context>


			fun source(source: Reader) =
				source(JSONReader.build(source))


			fun source(source: String) =
				source(StringReader(source))
		}


		private class BuilderForSourceImpl<out Context : JSONCoderContext>(
			private val context: Context,
			private val codecProvider: JSONCodecProvider<Context>
		) : BuilderForSource<Context> {

			override fun source(source: JSONReader) =
				BuilderImpl(
					context = context,
					codecProvider = codecProvider,
					source = source
				)
		}


		interface Builder<out Context : JSONCoderContext> {

			fun build(): JSONDecoder<Context>
		}


		private class BuilderImpl<out Context : JSONCoderContext>(
			private val context: Context,
			private val codecProvider: JSONCodecProvider<Context>,
			private val source: JSONReader
		) : Builder<Context> {

			override fun build() =
				StandardDecoder(
					context = context,
					codecProvider = codecProvider,
					source = source
				)
		}
	}
}


fun JSONDecoder<*>.readValueOrNull() =
	if (nextToken != JSONToken.nullValue) readValue() else readNull()


inline fun <reified Value : Any> JSONDecoder<*>.readValueOfType() =
	readValueOfType(jsonCodableType<Value>())


inline fun <reified Value : Any> JSONDecoder<*>.readValueOfTypeOrNull() =
	readValueOfTypeOrNull(jsonCodableType<Value>())


fun <Value : Any> JSONDecoder<*>.readValueOfTypeOrNull(valueType: JSONCodableType<Value>) =
	if (nextToken != JSONToken.nullValue) readValueOfType(valueType) else readNull()
