package fi.evident.apina.spring

import fi.evident.apina.java.model.JavaAnnotatedElement
import fi.evident.apina.java.model.JavaModel
import fi.evident.apina.java.model.type.JavaType
import fi.evident.apina.java.model.type.TypeEnvironment
import fi.evident.apina.model.ApiDefinition
import fi.evident.apina.model.settings.TranslationSettings
import fi.evident.apina.model.type.ApiType
import fi.evident.apina.model.type.ApiTypeName
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Translates Java types to model types.
 */
internal class TypeTranslator(
    private val settings: TranslationSettings,
    private val classes: JavaModel,
    private val api: ApiDefinition
) {

    private val jacksonTypeTranslator = JacksonTypeTranslator(this, classes, api)
    private val kotlinSerializationTypeTranslator = KotlinSerializationTypeTranslator(this, classes, api)

    /**
     * Maps translated simple names back to their original types.
     * Needed to make sure that our mapping remains unique.
     */
    private val translatedNames = HashMap<String, JavaType.Basic>()

    fun translateType(javaType: JavaType, element: JavaAnnotatedElement, env: TypeEnvironment): ApiType {
        val type = translateType(javaType, env)
        return if (element.hasNullableAnnotation) type.nullable() else type
    }

    fun translateType(type: JavaType, env: TypeEnvironment): ApiType = when (type) {
        is JavaType.Basic ->
            translateBasicType(type, env)
        is JavaType.Parameterized ->
            translateParameterizedType(type, env)
        is JavaType.Array ->
            ApiType.Array(translateType(type.elementType, env))
        is JavaType.Variable ->
            env.lookup(type)?.let {
                check(it != type) { "Looking up type returned itself: $type in $env" }
                translateType(it, env)
            } ?: ApiType.Primitive.ANY
        is JavaType.Wildcard ->
            type.lowerBound?.let { translateType(it, env) } ?: ApiType.Primitive.ANY
        is JavaType.InnerClass ->
            throw UnsupportedOperationException("translating inner class types is not supported: $type")
    }

    private fun translateBasicType(type: JavaType.Basic, env: TypeEnvironment): ApiType = when {
        classes.isInstanceOf<Collection<*>>(type) ->
            ApiType.Array(ApiType.Primitive.ANY)

        classes.isInstanceOf<Map<*, *>>(type) ->
            ApiType.Dictionary(ApiType.Primitive.ANY)

        type == JavaType.Basic(String::class.java) ->
            ApiType.Primitive.STRING

        classes.isIntegral(type) ->
            ApiType.Primitive.INTEGER

        classes.isNumber(type) ->
            ApiType.Primitive.FLOAT

        type == JavaType.Basic.BOOLEAN || type == JavaType.Basic(Boolean::class.javaObjectType) ->
            ApiType.Primitive.BOOLEAN

        type in OPTIONAL_INTEGRAL_TYPES ->
            ApiType.Nullable(ApiType.Primitive.INTEGER)

        type == OPTIONAL_DOUBLE ->
            ApiType.Nullable(ApiType.Primitive.FLOAT)

        type == JavaType.Basic(Any::class.java) ->
            ApiType.Primitive.ANY

        type.isVoid ->
            ApiType.Primitive.VOID

        else ->
            translateClassType(type, env)
    }

    private fun translateClassType(type: JavaType.Basic, env: TypeEnvironment): ApiType {
        val typeName = classNameForType(type)

        if (settings.isImported(typeName))
            return ApiType.BlackBox(typeName)

        if (settings.isBlackBoxClass(type.name)) {
            log.debug("Translating {} as black box", type.name)

            api.addBlackBox(typeName)
            return ApiType.BlackBox(typeName)
        }

        val javaClass = classes.findClass(type.name) ?:
            return ApiType.Class(typeName)

        if (kotlinSerializationTypeTranslator.supports(javaClass))
            return kotlinSerializationTypeTranslator.translateClass(javaClass, typeName, env)
        else
            return jacksonTypeTranslator.translateClass(javaClass, typeName, env)
    }

    private fun translateParameterizedType(type: JavaType.Parameterized, env: TypeEnvironment): ApiType {
        val baseType = type.baseType
        val arguments = type.arguments.map { translateType(it, env) }

        return when {
            classes.isInstanceOf<Collection<*>>(baseType) && arguments.size == 1 ->
                ApiType.Array(arguments[0])
            classes.isInstanceOf<Map<*, *>>(baseType) && arguments.size == 2 && arguments[0] == ApiType.Primitive.STRING ->
                ApiType.Dictionary(arguments[1])
            classes.isInstanceOf<Optional<*>>(baseType) && arguments.size == 1 ->
                ApiType.Nullable(arguments[0])
            else ->
                translateType(baseType, env)
        }
    }

    fun classNameForType(type: JavaType.Basic): ApiTypeName {
        val translatedName = settings.nameTranslator.translateClassName(type.name)

        val existingType = translatedNames.putIfAbsent(translatedName, type)
        if (existingType != null && type != existingType)
            throw DuplicateClassNameException(type.name, existingType.name)

        return ApiTypeName(translatedName)
    }

    companion object {

        private val log = LoggerFactory.getLogger(TypeTranslator::class.java)

        private val OPTIONAL_INTEGRAL_TYPES = listOf(JavaType.basic<OptionalInt>(), JavaType.basic<OptionalLong>())
        private val OPTIONAL_DOUBLE = JavaType.basic<OptionalDouble>()
    }
}
