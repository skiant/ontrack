package net.nemerosa.ontrack.ui.graphql.dsl.support

import com.fasterxml.jackson.databind.ObjectMapper
import graphql.schema.DataFetchingEnvironment
import net.nemerosa.ontrack.json.ObjectMapperFactory
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

val environmentMapper: ObjectMapper = ObjectMapperFactory.create()

inline fun <reified T> DataFetchingEnvironment.get(property: KProperty<T>): T =
        getArgument<T>(property.name)

inline operator fun <reified T> Map<String, Any>.get(property: KProperty<T>): T =
        get(property.name) as T

fun DataFetchingEnvironment.input(property: String): Map<String, Any> =
        getArgument(property)

inline operator fun <reified T> DataFetchingEnvironment.get(name: String): T {
    val map: Map<String, Any> = getArgument(name)
    return environmentMapper.convertValue(map, T::class.java)
}

inline fun <reified T> DataFetchingEnvironment.parse(): T = environmentMapper.convertValue(arguments, T::class.java)

fun <T : Any> DataFetchingEnvironment.get(name: String, type: KClass<T>): T {
    val map: Map<String, Any> = getArgument(name)
    return environmentMapper.convertValue(map, type.java)
}
