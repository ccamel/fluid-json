package com.github.fluidsonic.fluid.json


object FloatArrayJSONCodec : AbstractJSONEncoderCodec<FloatArray, JSONCoderContext>() {

	override fun encode(value: FloatArray, encoder: JSONEncoder<JSONCoderContext>) =
		encoder.writeList(value)
}
