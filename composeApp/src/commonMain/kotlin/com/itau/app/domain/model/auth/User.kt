package com.itau.app.domain.model.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder

/**
 * Serializer that accepts both String and Int for ID field
 */
object FlexibleIdSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        return when (val jsonDecoder = decoder as? JsonDecoder) {
            null -> decoder.decodeString()
            else -> {
                val element = jsonDecoder.decodeJsonElement()
                when (element) {
                    is JsonPrimitive -> element.intOrNull?.toString() ?: element.content
                    else -> element.toString()
                }
            }
        }
    }
}

/**
 * Authenticated user information.
 * Matches the backend's response format.
 */
@Serializable
data class User(
    @SerialName("id")
    @Serializable(with = FlexibleIdSerializer::class)
    val id: String,

    @SerialName("email")
    val email: String? = null,

    @SerialName("name")
    val name: String,

    @SerialName("document")
    val document: String? = null,

    @SerialName("phone")
    val phone: String? = null,

    @SerialName("mfaEnabled")
    val mfaEnabled: Boolean = false,

    @SerialName("biometricEnabled")
    val biometricEnabled: Boolean = false
)
