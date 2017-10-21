package com.github.fluidsonic.fluid.json


interface JSONEncoderCodec<in Value : Any, in Context : JSONCoderContext> : JSONCodecProvider<Context> {

	override val decoderCodecs: List<JSONDecoderCodec<*, Context>>
		get() = emptyList()

	override val encoderCodecs: List<JSONEncoderCodec<*, Context>>
		get() = listOf(this)

	val valueClass: Class<in Value>


	fun encode(value: Value, encoder: JSONEncoder<Context>)


	companion object
}