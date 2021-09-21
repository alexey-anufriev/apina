@file:Suppress("PLUGIN_IS_NOT_ENABLED")

package fi.evident.apina.spring

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.annotation.JsonUnwrapped
import fi.evident.apina.java.model.JavaClass
import fi.evident.apina.java.model.JavaModel
import fi.evident.apina.java.model.type.JavaType
import fi.evident.apina.java.model.type.TypeEnvironment
import fi.evident.apina.java.model.type.TypeSchema
import fi.evident.apina.java.reader.TestClassMetadataLoader
import fi.evident.apina.model.ApiDefinition
import fi.evident.apina.model.ClassDefinition
import fi.evident.apina.model.EnumDefinition
import fi.evident.apina.model.ModelMatchers.hasProperties
import fi.evident.apina.model.ModelMatchers.property
import fi.evident.apina.model.settings.OptionalTypeMode
import fi.evident.apina.model.settings.TranslationSettings
import fi.evident.apina.model.type.ApiType
import fi.evident.apina.model.type.ApiTypeName
import fi.evident.apina.spring.testclasses.*
import kotlinx.serialization.SerialName
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Collections.emptyList
import java.util.Collections.singletonMap
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlin.test.fail

class TypeTranslatorTest {

    private val settings = TranslationSettings()

    @Test
    fun translatingClassWithFieldProperties() {
        val classDefinition = translateClass<ClassWithFieldProperties>()

        assertEquals(ApiTypeName(ClassWithFieldProperties::class.java.simpleName), classDefinition.type)
        assertThat(classDefinition.properties, hasProperties(
                property("intField", ApiType.Primitive.INTEGER),
                property("floatField", ApiType.Primitive.FLOAT),
                property("doubleField", ApiType.Primitive.FLOAT),
                property("integerField", ApiType.Primitive.INTEGER),
                property("stringField", ApiType.Primitive.STRING),
                property("booleanField", ApiType.Primitive.BOOLEAN),
                property("booleanNonPrimitiveField", ApiType.Primitive.BOOLEAN),
                property("intArrayField", ApiType.Array(ApiType.Primitive.INTEGER)),
                property("rawCollectionField", ApiType.Array(ApiType.Primitive.ANY)),
                property("wildcardMapField", ApiType.Dictionary(ApiType.Primitive.ANY)),
                property("rawMapField", ApiType.Dictionary(ApiType.Primitive.ANY)),
                property("stringIntegerMapField", ApiType.Dictionary(ApiType.Primitive.INTEGER)),
                property("objectField", ApiType.Primitive.ANY),
                property("stringCollectionField", ApiType.Array(ApiType.Primitive.STRING))))
    }

    @Test
    fun translatingClassWithGetterProperties() {
        val classDefinition = translateClass<ClassWithGetters>()

        assertEquals(ApiTypeName(ClassWithGetters::class.java.simpleName), classDefinition.type)
        assertThat(classDefinition.properties, hasProperties(
                property("int", ApiType.Primitive.INTEGER),
                property("integer", ApiType.Primitive.INTEGER),
                property("string", ApiType.Primitive.STRING),
                property("boolean", ApiType.Primitive.BOOLEAN),
                property("booleanNonPrimitive", ApiType.Primitive.BOOLEAN)))
    }

    @Test
    fun translatingClassWithOverlappingFieldAndGetter() {
        val classDefinition = translateClass<TypeWithOverlappingFieldAndGetter>()

        assertThat(classDefinition.properties, hasProperties(
                property("foo", ApiType.Primitive.STRING)))
    }

    @Test
    fun translateVoidType() {
        assertEquals(ApiType.Primitive.VOID, translateType(JavaType.Basic.VOID))
    }

    @Test
    fun translateBlackBoxType() {
        settings.blackBoxClasses.addPattern("foo\\..+")

        assertEquals(ApiType.BlackBox(ApiTypeName("Baz")), translateType(JavaType.Basic("foo.bar.Baz")))
    }

    @Test
    fun translatingOptionalTypes() {
        val classDefinition = translateClass<ClassWithOptionalTypes>()

        assertEquals(ApiTypeName(ClassWithOptionalTypes::class.java.simpleName), classDefinition.type)
        assertThat(classDefinition.properties, hasProperties(
                property("optionalString", ApiType.Nullable(ApiType.Primitive.STRING)),
                property("optionalInt", ApiType.Nullable(ApiType.Primitive.INTEGER)),
                property("optionalLong", ApiType.Nullable(ApiType.Primitive.INTEGER)),
                property("optionalDouble", ApiType.Nullable(ApiType.Primitive.FLOAT))))
    }

    @Test
    fun `types with @JsonValue should be translated as aliased types`() {
        val api = ApiDefinition()
        val apiType = translateClass<ClassWithJsonValue>(api)

        assertEquals(ApiType.BlackBox(ApiTypeName("ClassWithJsonValue")), apiType)
        assertEquals(singletonMap(ApiTypeName("ClassWithJsonValue"), ApiType.Primitive.STRING), api.typeAliases)
    }

    @Test
    fun duplicateClassNames() {
        val class1 = JavaClass(JavaType.Basic("foo.MyClass"), JavaType.Basic("java.lang.Object"), emptyList(), 0, TypeSchema())
        val class2 = JavaClass(JavaType.Basic("bar.MyClass"), JavaType.Basic("java.lang.Object"), emptyList(), 0, TypeSchema())

        val loader = TestClassMetadataLoader()
        loader.addClass(class1)
        loader.addClass(class2)
        val classes = JavaModel(loader)
        val translator = TypeTranslator(settings, classes, ApiDefinition())
        val env = TypeEnvironment.empty()

        translator.translateType(class1.type, class1, env)

        assertFailsWith<DuplicateClassNameException> {
            translator.translateType(class2.type, class2, env)
        }
    }

    @Nested
    inner class `handling @JsonIgnore` {

        @Test
        fun `class hierarchy with ignores`() {
            val classDefinition = translateClass<TypeWithIgnoresAndSuperClass>()

            assertEquals(setOf("bar", "baz"), classDefinition.propertyNames)
        }

        @Test
        fun `ignores can be overridden in sub classes`() {
            val classDefinition = translateClass<TypeWithOverridingIgnore>()

            assertEquals(setOf("foo"), classDefinition.propertyNames)
        }
    }

    @Test
    fun `ignore properties annotated with java beans Transient`() {
        val classDefinition = translateClass<ClassWithTransientIgnore>()

        assertEquals(setOf("bar"), classDefinition.propertyNames)
    }

    @Test
    fun `ignore properties annotated with Spring Data Transient`() {
        val classDefinition = translateClass<ClassWithSpringDataTransient>()

        assertEquals(setOf("foo"), classDefinition.propertyNames)
    }

    @Test
    fun `ignore transient fields`() {
        val classDefinition = translateClass<ClassWithTransientFields>()

        assertEquals(setOf("foo", "baz"), classDefinition.propertyNames)
    }

    @Test
    fun interfaceWithProperties() {
        val classDefinition = translateClass<TypeWithPropertiesFromInterface>()

        assertThat(classDefinition.properties, hasProperties(
                property("foo", ApiType.Primitive.STRING),
                property("bar", ApiType.Primitive.STRING)))
    }

    @Test
    fun enumTranslation() {
        val enumDefinition = translateEnum<TestEnum>()

        assertEquals(listOf("FOO", "BAR", "BAZ"), enumDefinition.constants)
    }

    @Test
    fun genericTypeWithoutKnownParameters() {
        val classDefinition = translateClass<GenericType<*>>()

        assertThat(classDefinition.properties, hasProperties(
                property("genericField", ApiType.Primitive.ANY)))
    }

    @Test
    fun genericTypeInheritedByFixingParameters() {
        val classDefinition = translateClass<SubTypeOfGenericType>()

        assertThat(classDefinition.properties, hasProperties(
                property("genericField", ApiType.Primitive.STRING)))
    }

    @Test
    fun genericTypeInheritedThroughMiddleType() {
        val classDefinition = translateClass<SecondOrderSubTypeOfGenericType>()

        assertThat(classDefinition.properties, hasProperties(
                property("genericField", ApiType.Primitive.STRING)))
    }

    @Test
    fun genericTypeInheritedThroughMiddleTypeThatParameterizesWithVariable() {
        val classDefinition = translateClass<SubTypeOfGenericTypeParameterizedWithVariable>()

        assertThat(classDefinition.properties, hasProperties(
                property("genericField", ApiType.Array(ApiType.Primitive.STRING))))
    }

    @Test
    fun `generic type parameters are translated for unknown generic types`() {
        class Foo
        class Bar
        @Suppress("unused") class GenericType<T, S>

        class Root {
            @Suppress("unused")
            lateinit var foo: GenericType<Foo, Bar>
        }

        val model = JavaModel(TestClassMetadataLoader().apply {
            loadClassesFromInheritanceTree<Root>()
            loadClassesFromInheritanceTree<GenericType<*, *>>()
            loadClassesFromInheritanceTree<Foo>()
            loadClassesFromInheritanceTree<Bar>()
        })
        val api = ApiDefinition()
        val translator = TypeTranslator(settings, model, api)
        translator.translateType(JavaType.basic<Root>(), MockAnnotatedElement(), TypeEnvironment.empty())

        assertTrue(api.classDefinitions.any { it.type.name == "Foo" }, "Class definition for Foo is created")
        assertTrue(api.classDefinitions.any { it.type.name == "Bar" }, "Class definition for Bar is created")
    }

    @Test
    fun unboundTypeVariable() {
        @Suppress("unused")
        abstract class Bar<A>
        class Foo<B> : Bar<B>()

        assertEquals("Foo", translateClass<Foo<*>>().type.name)
    }

    interface GenericSuperType<out T> {
        @Suppress("unused")
        fun get(): T
    }

    interface GenericSubType<out T> : GenericSuperType<T>

    @Test
    fun shadowedTypesShouldNotPreventTranslation() {
        translateClass<GenericSubType<String>>()
    }

    @Nested
    inner class `discriminated unions` {


        @Test
        fun `translating discriminated unions`() {
            val model = JavaModel(TestClassMetadataLoader().apply {
                loadClassesFromInheritanceTree<Vehicle>()
                loadClassesFromInheritanceTree<Vehicle.Car>()
                loadClassesFromInheritanceTree<Vehicle.Truck>()
            })

            val api = ApiDefinition()
            val translator = TypeTranslator(settings, model, api)
            translator.translateType(JavaType.basic<Vehicle>(), MockAnnotatedElement(), TypeEnvironment.empty())

            assertEquals(1, api.discriminatedUnionDefinitions.size)
            val definition = api.discriminatedUnionDefinitions.first()
            assertEquals("Vehicle", definition.type.name)
            assertEquals("type", definition.discriminator)

            val types = definition.types
            assertEquals(2, types.size)
            assertEquals(setOf("car", "truck"), types.keys)
            assertEquals("Car", types["car"]?.type?.name)
            assertEquals("Truck", types["truck"]?.type?.name)

            // ensure that subclasses themselves are not translated
            assertEquals(0, api.classDefinitions.size)
        }

        @Test
        fun `without @JsonSubtypes`() {
            val model = JavaModel(TestClassMetadataLoader().apply {
                loadClassesFromInheritanceTree<Vehicle2>()
                loadClassesFromInheritanceTree<Vehicle2.Car>()
                loadClassesFromInheritanceTree<Vehicle2.Truck>()
            })

            val api = ApiDefinition()
            val translator = TypeTranslator(settings, model, api)
            translator.translateType(JavaType.basic<Vehicle2>(), MockAnnotatedElement(), TypeEnvironment.empty())

            assertEquals(1, api.discriminatedUnionDefinitions.size)
            val definition = api.discriminatedUnionDefinitions.first()
            assertEquals("Vehicle2", definition.type.name)
            assertEquals("type", definition.discriminator)

            val types = definition.types
            assertEquals(2, types.size)
            assertEquals(setOf("car", "truck"), types.keys)
            assertEquals("Car", types["car"]?.type?.name)
            assertEquals("Truck", types["truck"]?.type?.name)

            // ensure that subclasses themselves are not translated
            assertEquals(0, api.classDefinitions.size)
        }
    }

    @Test
    fun `translating unwrapped properties`() {

        @Suppress("unused")
        class Name(val first: String, val last: String)
        @Suppress("unused")
        class Person(@get:JsonUnwrapped val name: Name, val age: Int)

        val model = JavaModel(TestClassMetadataLoader().apply {
            loadClassesFromInheritanceTree<Name>()
            loadClassesFromInheritanceTree<Person>()
        })

        val api = ApiDefinition()
        val translator = TypeTranslator(settings, model, api)
        translator.translateType(JavaType.basic<Person>(), MockAnnotatedElement(), TypeEnvironment.empty())

        assertEquals(1, api.classDefinitionCount)
        val person = api.classDefinitions.find { it.type.name == "Person" } ?: error("no Person found")
        assertEquals(setOf("age", "first", "last"), person.properties.map { it.name }.toSet())
    }

    @Test
    fun `translating unwrapped with prefixes and suffixes`() {

        @Suppress("unused")
        class Name(val first: String, val last: String)
        @Suppress("unused")
        class Person(@get:JsonUnwrapped(suffix = "Name") val name: Name,
                     @get:JsonUnwrapped(prefix = "foo", suffix = "bar") val name2: Name,
                     val age: Int)

        val model = JavaModel(TestClassMetadataLoader().apply {
            loadClassesFromInheritanceTree<Name>()
            loadClassesFromInheritanceTree<Person>()
        })

        val api = ApiDefinition()
        val translator = TypeTranslator(settings, model, api)
        translator.translateType(JavaType.basic<Person>(), MockAnnotatedElement(), TypeEnvironment.empty())

        assertEquals(1, api.classDefinitionCount)
        val person = api.classDefinitions.find { it.type.name == "Person" } ?: error("no Person found")
        assertEquals(setOf("age", "firstName", "lastName", "foofirstbar", "foolastbar"), person.properties.map { it.name }.toSet())
    }

    @Test
    fun `override translated class name`() {

        @Suppress("unused")
        class Foo(val foo: String)

        settings.nameTranslator.registerClassName(Foo::class.java.name, "MyOverriddenFoo")

        assertEquals("MyOverriddenFoo", translateClass<Foo>().type.name)
    }

    @Nested
    inner class `kotlin serialization` {

        @Test
        fun `basic class definition`() {

            @Suppress("unused")
            @kotlinx.serialization.Serializable
            class Example(
                val normalProperty: String,
                @kotlinx.serialization.Transient val ignoredProperty: String = "",
                @kotlinx.serialization.SerialName("overriddenName") val propertyWithOverriddenName: String,
                val fieldWithDefaultWillBeNullable: Int = 42,
                @kotlinx.serialization.Required val requiredFieldWithDefaultWillNotBeNullable: Int = 42
            )

            val classDefinition = translateClass<Example>()

            assertThat(classDefinition.properties, hasProperties(
                property("normalProperty", ApiType.Primitive.STRING),
                property("overriddenName", ApiType.Primitive.STRING),
                property("fieldWithDefaultWillBeNullable", ApiType.Primitive.INTEGER.nullable()),
                property("requiredFieldWithDefaultWillNotBeNullable", ApiType.Primitive.INTEGER)))
        }

        @Test
        fun `discriminated unions`() {
            val model = JavaModel(TestClassMetadataLoader().apply {
                loadClassesFromInheritanceTree<KotlinSerializationDiscriminatedUnion>()
                loadClassesFromInheritanceTree<KotlinSerializationDiscriminatedUnion.SubClassWithCustomDiscriminator>()
                loadClassesFromInheritanceTree<KotlinSerializationDiscriminatedUnion.SubClassWithDefaultDiscriminator>()
            })

            val api = ApiDefinition()
            val translator = TypeTranslator(settings, model, api)
            translator.translateType(JavaType.basic<KotlinSerializationDiscriminatedUnion>(), MockAnnotatedElement(), TypeEnvironment.empty())

            val definition = api.discriminatedUnionDefinitions.find { it.type.name == KotlinSerializationDiscriminatedUnion::class.simpleName }
                ?: fail("could not find union")

            assertEquals("type", definition.discriminator)

            assertEquals(
                setOf("CustomDiscriminator", KotlinSerializationDiscriminatedUnion.SubClassWithDefaultDiscriminator::class.qualifiedName),
                definition.types.keys)
        }

        @Suppress("unused")
        @Test
        fun `inherited fields`() {
            @kotlinx.serialization.Serializable
            open class ParentClass(val parentParameter: Int) {
                var parentProperty = "string"

                @kotlinx.serialization.Required
                var requiredParentProperty = "string"

                val propertyWithoutBackingField: String
                    get() = "no-included"
            }

            @kotlinx.serialization.Serializable
            class ChildClass(val ownParameter: Int) : ParentClass(42) {
                var ownProperty = "string"

                @kotlinx.serialization.Required
                var requiredOwnProperty = "string"

                @kotlinx.serialization.Transient
                private var transientPrivateProperty = "42"

                @SerialName("renamedPrivatePropertyNewName")
                private var renamedPrivateProperty = "42"

                private var privateProperty = "42"

                @kotlinx.serialization.Required
                private var requiredPrivateProperty = "42"

                @kotlinx.serialization.Required
                private var isProperty = false
            }

            val model = JavaModel(TestClassMetadataLoader().apply {
                loadClassesFromInheritanceTree<ParentClass>()
                loadClassesFromInheritanceTree<ChildClass>()
            })

            val api = ApiDefinition()
            val translator = TypeTranslator(settings, model, api)
            translator.translateType(JavaType.basic<ChildClass>(), MockAnnotatedElement(), TypeEnvironment.empty())

            val classDefinition = api.classDefinitions.find { it.type.name == ChildClass::class.simpleName }
                ?: fail("could not find class")

            println(classDefinition.properties)

            assertThat(classDefinition.properties, hasProperties(
                property("ownParameter", ApiType.Primitive.INTEGER),
                property("ownProperty", ApiType.Primitive.STRING.nullable()),
                property("requiredOwnProperty", ApiType.Primitive.STRING),
                property("privateProperty", ApiType.Primitive.STRING.nullable()),
                property("renamedPrivatePropertyNewName", ApiType.Primitive.STRING.nullable()),
                property("requiredPrivateProperty", ApiType.Primitive.STRING),
                property("isProperty", ApiType.Primitive.BOOLEAN),
                property("parentParameter", ApiType.Primitive.INTEGER),
                property("parentProperty", ApiType.Primitive.STRING.nullable()),
                property("requiredParentProperty", ApiType.Primitive.STRING)))
        }
    }

    @kotlinx.serialization.Serializable
    sealed class KotlinSerializationDiscriminatedUnion {

        @kotlinx.serialization.Serializable
        class SubClassWithDefaultDiscriminator(val x: Int) : KotlinSerializationDiscriminatedUnion()

        @kotlinx.serialization.Serializable
        @kotlinx.serialization.SerialName("CustomDiscriminator")
        class SubClassWithCustomDiscriminator(val y: Int) : KotlinSerializationDiscriminatedUnion()
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = Vehicle.Car::class, name = "car"),
        JsonSubTypes.Type(value = Vehicle.Truck::class, name = "truck")
    )
    abstract class Vehicle {
        class Car : Vehicle()
        class Truck : Vehicle()
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    abstract class Vehicle2 {
        @JsonTypeName("car")
        class Car : Vehicle2()
        @JsonTypeName("truck")
        class Truck : Vehicle2()
    }

    private fun translateType(type: JavaType): ApiType {
        val classes = JavaModel(TestClassMetadataLoader())
        val api = ApiDefinition()
        val translator = TypeTranslator(settings, classes, api)

        return translator.translateType(type, MockAnnotatedElement(), TypeEnvironment.empty()) // TODO: create environment from type
    }

    private inline fun <reified T : Any> translateClass(): ClassDefinition {
        val api = ApiDefinition()
        val apiType = translateClass<T>(api)

        return api.classDefinitions.find { d -> apiType.toTypeScript(OptionalTypeMode.NULL).startsWith(d.type.toString()) }
            ?: throw AssertionError("could not find definition for $apiType")
    }

    private inline fun <reified T : Enum<T>> translateEnum(): EnumDefinition {
        val api = ApiDefinition()
        val apiType = translateClass<T>(api)

        return api.enumDefinitions.find { d -> d.type.toString() == apiType.toTypeScript(OptionalTypeMode.NULL) }
            ?: throw AssertionError("could not find definition for $apiType")
    }

    private inline fun <reified T : Any> translateClass(api: ApiDefinition): ApiType {
        val model = JavaModel(TestClassMetadataLoader().apply {
            loadClassesFromInheritanceTree<T>()
        })
        val translator = TypeTranslator(settings, model, api)

        return translator.translateType(JavaType.basic<T>(), MockAnnotatedElement(), TypeEnvironment.empty())
    }

    companion object {

        private val ClassDefinition.propertyNames: Set<String>
            get() = properties.map { it.name }.toSet()
    }
}