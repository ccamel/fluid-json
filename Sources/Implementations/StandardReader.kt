package com.github.fluidsonic.fluid.json

import java.io.IOException


internal class StandardReader(private val source: TextInput) : JSONReader {

	private val buffer = StringBuilder()
	private var peekedToken: JSONToken? = null
	private var peekedTokenIndex = -1
	private var state = State.initial
	private val stateStack = mutableListOf<State>()


	private fun assertNotClosed() {
		if (state == State.closed) {
			throw IOException("Cannot operate on a closed reader")
		}
	}


	override fun close() {
		if (state == State.closed) {
			return
		}

		state = State.closed

		source.close()
	}


	private fun finishValue() {
		val nextCharacter = source.peekCharacter()
		if (!Character.isValueBoundary(nextCharacter)) {
			throw unexpectedCharacter(
				nextCharacter,
				expected = "end of value",
				characterIndex = source.sourceIndex
			)
		}
	}


	override val nextToken: JSONToken?
		get() {
			assertNotClosed()

			if (peekedTokenIndex >= 0) {
				return peekedToken
			}

			val peekedToken = peekToken()
			this.peekedToken = peekedToken
			this.peekedTokenIndex = source.sourceIndex

			return peekedToken
		}


	private fun peekToken(): JSONToken? {
		val source = source

		while (true) {
			source.skipWhitespaceCharacters()

			val character = source.peekCharacter()

			@Suppress("NON_EXHAUSTIVE_WHEN")
			when (state) {
				State.afterListElement ->
					when (character) {
						Character.Symbol.comma -> {
							state = State.afterListElementSeparator
							source.readCharacter()
						}

						Character.Symbol.rightSquareBracket -> {
							restoreState()
							return JSONToken.listEnd
						}

						else -> throw unexpectedCharacter(character, expected = "',' or ']'")
					}

				State.afterListElementSeparator -> {
					state = State.afterListElement
					return peekValueToken(expected = "a value")
				}

				State.afterListStart ->
					when (character) {
						Character.Symbol.rightSquareBracket -> {
							restoreState()
							return JSONToken.listEnd
						}
						else -> {
							state = State.afterListElement
							return peekValueToken(expected = "a value or ']'")
						}
					}

				State.afterMapElement ->
					when (character) {
						Character.Symbol.comma -> {
							state = State.afterMapElementSeparator
							source.readCharacter()
						}

						Character.Symbol.rightCurlyBracket -> {
							restoreState()
							return JSONToken.mapEnd
						}

						else -> throw unexpectedCharacter(character, expected = "',' or '}'")
					}

				State.afterMapElementSeparator ->
					when (character) {
						Character.Symbol.quotationMark -> {
							state = State.afterMapKey
							return JSONToken.mapKey
						}

						else -> throw unexpectedCharacter(character, expected = "'\"'")
					}

				State.afterMapKey ->
					when (character) {
						Character.Symbol.colon -> {
							state = State.afterMapKeySeparator
							source.readCharacter()
						}

						else -> throw unexpectedCharacter(character, expected = "':'")
					}

				State.afterMapKeySeparator -> {
					state = State.afterMapElement
					return peekValueToken(expected = "a value")
				}

				State.afterMapStart ->
					when (character) {
						Character.Symbol.quotationMark -> {
							state = State.afterMapKey
							return JSONToken.mapKey
						}

						Character.Symbol.rightCurlyBracket -> {
							restoreState()
							return JSONToken.mapEnd
						}

						else -> throw unexpectedCharacter(character, expected = "'\"' or '}'")
					}

				State.end ->
					when (character) {
						Character.end ->
							return null

						else -> throw unexpectedCharacter(character, expected = "end of input")
					}

				State.initial -> {
					state = State.end
					return peekValueToken(expected = "a value")
				}
			}
		}
	}


	private fun peekValueToken(expected: String): JSONToken? {
		val character = source.peekCharacter()
		return when (character) {
			Character.Symbol.quotationMark ->
				JSONToken.stringValue

			Character.Symbol.hyphenMinus,
			Character.Digit.zero,
			Character.Digit.one,
			Character.Digit.two,
			Character.Digit.three,
			Character.Digit.four,
			Character.Digit.five,
			Character.Digit.six,
			Character.Digit.seven,
			Character.Digit.eight,
			Character.Digit.nine ->
				JSONToken.numberValue

			Character.Letter.f,
			Character.Letter.t ->
				JSONToken.booleanValue

			Character.Letter.n ->
				JSONToken.nullValue

			Character.Symbol.leftCurlyBracket -> {
				saveState()
				state = State.afterMapStart

				JSONToken.mapStart
			}

			Character.Symbol.leftSquareBracket -> {
				saveState()
				state = State.afterListStart

				JSONToken.listStart
			}

			else -> throw unexpectedCharacter(character, expected = expected)
		}
	}


	override fun readBoolean(): Boolean {
		readToken(JSONToken.booleanValue)

		val source = source
		if (source.peekCharacter() == Character.Letter.t) {
			source.readCharacter(Character.Letter.t)
			source.readCharacter(Character.Letter.r)
			source.readCharacter(Character.Letter.u)
			source.readCharacter(Character.Letter.e)
			finishValue()

			return true
		}
		else {
			source.readCharacter(Character.Letter.f)
			source.readCharacter(Character.Letter.a)
			source.readCharacter(Character.Letter.l)
			source.readCharacter(Character.Letter.s)
			source.readCharacter(Character.Letter.e)
			finishValue()

			return false
		}
	}


	override fun readDouble(): Double {
		readNumberIntoBuffer()

		return buffer.toString().toDouble()
	}


	override fun readFloat(): Float {
		readNumberIntoBuffer()

		return buffer.toString().toFloat()
	}


	override fun readListEnd() {
		readToken(JSONToken.listEnd)

		source.readCharacter(Character.Symbol.rightSquareBracket)
	}


	override fun readListStart() {
		readToken(JSONToken.listStart)

		source.readCharacter(Character.Symbol.leftSquareBracket)
	}


	override fun readLong(): Long {
		readToken(JSONToken.numberValue)

		return source.locked {
			readLongLocked()
		}
	}


	private fun readLongLocked(): Long {
		val startIndex = source.index

		val isNegative: Boolean
		val negativeLimit: Long

		val source = source
		var character = source.readCharacter()
		if (character == Character.Symbol.hyphenMinus) {
			isNegative = true
			character = source.readCharacter(required = Character::isDigit) { "a digit" }
			negativeLimit = Long.MIN_VALUE
		}
		else {
			isNegative = false
			negativeLimit = -Long.MAX_VALUE
		}

		val minimumBeforeMultiplication = negativeLimit / 10
		var value = 0L

		if (character == Character.Digit.zero) {
			character = source.readCharacter(required = { !Character.isDigit(it) }) {
				Character.toString(
					Character.Symbol.fullStop,
					Character.Letter.e,
					Character.Letter.E
				) + " or end of number after a leading '0'"
			}
		}
		else {
			do {
				val digit = character - Character.Digit.zero
				if (value < minimumBeforeMultiplication) {
					value = negativeLimit

					do character = source.readCharacter()
					while (Character.isDigit(character))
					break
				}

				value *= 10

				if (value < negativeLimit + digit) {
					value = negativeLimit

					do character = source.readCharacter()
					while (Character.isDigit(character))
					break
				}

				value -= digit
				character = source.readCharacter()
			}
			while (Character.isDigit(character))

			if (!isNegative) {
				value *= -1
			}
		}

		if (character == Character.Symbol.fullStop) { // truncate decimal value
			source.readCharacter(required = Character::isDigit) { "a digit in decimal value of number" }

			do character = source.readCharacter()
			while (Character.isDigit(character))
		}

		if (character == Character.Letter.e || character == Character.Letter.E) { // oh no, an exponent!
			source.seekTo(startIndex)
			unreadToken(JSONToken.numberValue)

			return readDouble().toLong()
		}

		source.seekBackOneCharacter()
		finishValue()

		return value
	}


	override fun readMapEnd() {
		readToken(JSONToken.mapEnd)

		source.readCharacter(Character.Symbol.rightCurlyBracket)
	}


	override fun readMapStart() {
		readToken(JSONToken.mapStart)

		source.readCharacter(Character.Symbol.leftCurlyBracket)
	}


	override fun readNull(): Nothing? {
		readToken(JSONToken.nullValue)

		val source = source
		source.readCharacter(Character.Letter.n)
		source.readCharacter(Character.Letter.u)
		source.readCharacter(Character.Letter.l)
		source.readCharacter(Character.Letter.l)
		finishValue()

		return null
	}


	override fun readNumber(): Number {
		val shouldParseAsFloatingPoint = readNumberIntoBuffer()

		val stringValue = buffer.toString()
		if (!shouldParseAsFloatingPoint) {
			val value = stringValue.toLongOrNull()
			if (value != null) {
				return if (value in Int.MIN_VALUE .. Int.MAX_VALUE) value.toInt() else value
			}
		}

		return stringValue.toDouble()
	}


	private fun readNumberIntoBuffer(): Boolean {
		readToken(JSONToken.numberValue)

		val buffer = buffer
		buffer.setLength(0)

		var shouldParseAsFloatingPoint = false
		val source = source
		var character = source.readCharacter()

		// consume optional minus sign
		if (character == Character.Symbol.hyphenMinus) {
			buffer.append('-')
			character = source.readCharacter()
		}

		// consume integer value
		when (character) {
			Character.Digit.zero -> {
				buffer.append('0')
				character = source.readCharacter(required = { !Character.isDigit(it) }) {
					Character.toString(
						Character.Symbol.fullStop,
						Character.Letter.e,
						Character.Letter.E
					) + " or end of number after a leading '0'"
				}
			}

			Character.Digit.one,
			Character.Digit.two,
			Character.Digit.three,
			Character.Digit.four,
			Character.Digit.five,
			Character.Digit.six,
			Character.Digit.seven,
			Character.Digit.eight,
			Character.Digit.nine ->
				do {
					buffer.append(character.toChar())
					character = source.readCharacter()
				}
				while (Character.isDigit(character))

			else ->
				throw unexpectedCharacter(
					character,
					expected = "a digit in integer value of number",
					characterIndex = source.sourceIndex - 1
				)
		}

		// consume optional decimal separator and value
		if (character == Character.Symbol.fullStop) {
			shouldParseAsFloatingPoint = true

			buffer.append('.')
			character = source.readCharacter(required = Character::isDigit) { "a digit in decimal value of number" }

			do {
				buffer.append(character.toChar())
				character = source.readCharacter()
			}
			while (Character.isDigit(character))
		}

		// consume optional exponent separator and value
		if (character == Character.Letter.e || character == Character.Letter.E) {
			shouldParseAsFloatingPoint = true

			buffer.append(character.toChar())

			character = source.peekCharacter()
			if (character == Character.Symbol.plusSign || character == Character.Symbol.hyphenMinus) {
				buffer.append(character.toChar())
				source.readCharacter()
			}

			character = source.readCharacter(required = Character::isDigit) { "a digit in exponent value of number" }

			do {
				buffer.append(character.toChar())
				character = source.readCharacter()
			}
			while (Character.isDigit(character))
		}

		source.seekBackOneCharacter()
		finishValue()

		return shouldParseAsFloatingPoint
	}


	override fun readString(): String {
		readToken(JSONToken.stringValue, JSONToken.mapKey)

		return source.locked { readStringLocked() }
	}


	private fun readStringLocked(): String {
		val source = source
		source.readCharacter(Character.Symbol.quotationMark)

		val buffer = buffer
		buffer.setLength(0)

		var startIndex = source.index

		do {
			var character = source.readCharacter()
			when (character) {
				Character.Symbol.reverseSolidus -> {
					val endIndex = source.index - 1
					if (endIndex > startIndex) {
						buffer.append(source.buffer, startIndex, endIndex - startIndex)
					}

					character = source.readCharacter()
					when (character) {
						Character.Symbol.reverseSolidus,
						Character.Symbol.solidus ->
							buffer.append(character.toChar())

						Character.Symbol.quotationMark -> {
							buffer.append(character.toChar())
							character = 0
						}

						Character.Letter.b -> buffer.append('\b')
						Character.Letter.f -> buffer.append('\u000C')
						Character.Letter.n -> buffer.append('\n')
						Character.Letter.r -> buffer.append('\r')
						Character.Letter.t -> buffer.append('\t')
						Character.Letter.u -> {
							val digit1 = source.readCharacter(required = Character::isHexDigit) { "0-9, a-f or A-F" }
							val digit2 = source.readCharacter(required = Character::isHexDigit) { "0-9, a-f or A-F" }
							val digit3 = source.readCharacter(required = Character::isHexDigit) { "0-9, a-f or A-F" }
							val digit4 = source.readCharacter(required = Character::isHexDigit) { "0-9, a-f or A-F" }

							val decodedCharacter = (Character.parseHexDigit(digit1) shl 12) or
								(Character.parseHexDigit(digit2) shl 8) or
								(Character.parseHexDigit(digit3) shl 4) or
								Character.parseHexDigit(digit4)

							buffer.append(decodedCharacter.toChar())
						}
						else -> throw unexpectedCharacter(character, "an escape sequence starting with '\\', '/', '\"', 'b', 'f', 'n', 'r', 't' or 'u'")
					}

					startIndex = source.index
				}

				0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
				0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F ->
					throw unexpectedCharacter(character, "an escape sequence")

				Character.Symbol.quotationMark ->
					Unit

				Character.end ->
					throw JSONException("unterminated string value")
			}
		}
		while (character != Character.Symbol.quotationMark)

		val endIndex = source.index - 1
		if (endIndex > startIndex) {
			buffer.append(source.buffer, startIndex, endIndex - startIndex)
		}

		return buffer.toString()
	}


	private fun readToken(required: JSONToken) {
		val token = nextToken
		if (token != required) {
			throw JSONException.unexpectedToken(
				token,
				expected = "'$required'",
				characterIndex = peekedTokenIndex
			)
		}

		peekedToken = null
		peekedTokenIndex = -1
	}


	private fun readToken(required: JSONToken, alternative: JSONToken) {
		val token = nextToken
		if (token != required && token != alternative) {
			throw JSONException.unexpectedToken(
				token,
				expected = "'$required' or '$alternative'",
				characterIndex = peekedTokenIndex
			)
		}

		peekedToken = null
		peekedTokenIndex = -1
	}


	private fun restoreState() {
		state = stateStack.removeAt(stateStack.size - 1)
	}


	private fun saveState() {
		stateStack.add(state)
	}


	private fun unexpectedCharacter(character: Int, expected: String, characterIndex: Int = source.sourceIndex) =
		JSONException.unexpectedCharacter(character, expected = expected, characterIndex = characterIndex)


	private fun unreadToken(token: JSONToken) {
		peekedToken = token
		peekedTokenIndex = source.sourceIndex
	}

	
	private enum class State {

		afterListElementSeparator,
		afterListElement,
		afterListStart,
		afterMapElement,
		afterMapElementSeparator,
		afterMapKey,
		afterMapKeySeparator,
		afterMapStart,
		closed,
		end,
		initial,
	}
}
