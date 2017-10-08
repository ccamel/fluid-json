package com.github.fluidsonic.fluid.json

import org.apiguardian.api.API


@API(status = API.Status.EXPERIMENTAL)
class JSONException constructor(message: String, cause: Throwable? = null)
	: RuntimeException(message, cause)
