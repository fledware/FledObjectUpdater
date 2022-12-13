package fledware.objectupdater

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

@Suppress("UNCHECKED_CAST")
object OUU {
  fun Any.toBaseType(): Any {
    return when (this) {
      is Boolean -> this
      is Number -> this
      is String -> this
      is Enum<*> -> this.name
      is Collection<*> -> {
        val result = mutableListOf<Any>()
        this.forEach { result += it!!.toBaseType() }
        result
      }
      is Map<*, *> -> {
        val result = mutableMapOf<String, Any>()
        this.forEach { key, value -> result[key as String] = value!!.toBaseType() }
        result
      }
      else -> {
        // we assume this is a poko
        // TODO: maybe the memberProperties should be cached somewhere?
        val result = mutableMapOf<String, Any>()
        this::class.memberProperties.forEach { property ->
          property as KProperty1<Any, Any?>
          val rawValue = property.get(this) ?: return@forEach
          result[property.name] = rawValue.toBaseType()
        }
        result
      }
    }
  }

  fun Any.deepCopy(): Any {
    return when (this) {
      is Boolean -> this
      is Number -> this
      is String -> this
      is MutableList<*> -> {
        val result = mutableListOf<Any>()
        this.forEach { result += it!!.deepCopy() }
        result
      }
      is MutableMap<*, *> -> {
        val result = mutableMapOf<String, Any>()
        this.forEach { key, value -> result[key as String] = value!!.deepCopy() }
        result
      }
      else -> throw IllegalArgumentException("illegal copy: $this")
    }
  }

  fun ObjectMapper.parseToBaseType(original: String, raw: String?): Any {
    if (raw == null)
      throw IllegalArgumentException("this operation requires a json value as input: $original")
    return when {
      raw.startsWith('[') -> this.readValue<List<Any>>(raw)
      raw.startsWith('{') -> this.readValue<Map<String, Any>>(raw)
      raw.startsWith('"') -> raw.substring(1, raw.lastIndex)
      else -> raw.toBooleanStrictOrNull() ?: raw.toIntOrNull() ?: raw
    }
  }

  fun Any?.operationRequiresJsonValue(directive: Directive) =
      this ?: throw IllegalArgumentException(
          "This operation requires json value ($directive): " +
              "Are you using the same ObjectUpdaterConfig for parsing and updating?")

  fun Any?.isAllowedValueType(): Boolean {
    return when (this) {
      is Boolean -> true
      is Number -> true
      is String -> true
      is MutableList<*> -> this.all { it.isAllowedValueType() }
      is MutableMap<*, *> -> this.all { it.key.isAllowedMapKey() && it.value.isAllowedValueType() }
      else -> false
    }
  }

  fun Any?.isAllowedMapKey(): Boolean {
    return when (this) {
      is String -> isNotBlank() //ObjectUpdater.allowedPath.matches(this)
      else -> false
    }
  }

  fun Any.getType(): String {
    return when (this) {
      is Boolean -> "Boolean"
      is Number -> "Number"
      is String -> "String"
      is MutableList<*> -> "List"
      is MutableMap<*, *> -> "Map"
      else -> throw IllegalArgumentException("illegal value for container transversal: $this")
    }
  }

  fun Any?.isImmutableType(): Boolean {
    return when (this) {
      is Boolean -> true
      is Number -> true
      is String -> true
      is MutableList<*> -> false
      is MutableMap<*, *> -> false
      else -> throw IllegalArgumentException("illegal type for object updater: $this")
    }
  }

  fun Any.transverse(key: Any): Any? {
    return when (this) {
      is MutableMap<*, *> -> this.parentAsMap[key.keyAsString]
      is MutableList<*> -> this.parentAsList[key.keyAsInt]
      else -> throw IllegalArgumentException("illegal value for container transversal: $this")
    }
  }

  fun String.isDirective(): Boolean = this.startsWith(':')

  data class SplitPathDirective(val select: Pair<String, String?>,
                                val predicate: Pair<String, String?>?)

  fun String.parseRawPathDirective(): SplitPathDirective {
    val predicateIndex = this.indexOf('#')
    return if (predicateIndex == -1) {
      SplitPathDirective(this.parseRawCommandWithInput(), null)
    }
    else {
      SplitPathDirective(
          this.substring(0, predicateIndex).parseRawCommandWithInput(),
          this.substring(predicateIndex + 1).parseRawCommandWithInput()
      )
    }
  }

  fun String.parseRawCommandWithInput(): Pair<String, String?> {
    val first = this.indexOf('(')
    if (first == -1) return this to null
    if (this.last() != ')')
      throw IllegalArgumentException("directive input must be between '(' and ')': $this")
    return substring(0, first) to substring(first + 1, lastIndex)
  }

  fun List<Any>.requireStringKeys(): List<String> {
    forEach { it.keyAsString }
    return this as List<String>
  }

  fun List<Any>.requireIntKeys(): List<Int> {
    forEach { it.keyAsInt }
    return this as List<Int>
  }

  val Any?.keyAsInt: Int
    get() = this as? Int
        ?: throw IllegalArgumentException("key must be a Int: $this")

  val Any?.keyAsString: String
    get() = this as? String
        ?: throw IllegalArgumentException("key must be a String: $this")

  val Any.parentAsMap: MutableMap<String, Any>
    get() = this as? MutableMap<String, Any>
        ?: throw IllegalArgumentException("this operation requires a map: $this")

  val Any.parentAsList: MutableList<Any>
    get() = this as? MutableList<Any>
        ?: throw IllegalArgumentException("this operation requires a list: $this")

  fun Any.otherAsMap(pathAt: List<String>) = this as? MutableMap<String, Any>
      ?: throw IllegalArgumentException("structure mismatch. Map required at //${pathAt.joinToString("/")}: $this")

  fun Any.otherAsList(pathAt: List<String>) = this as? MutableList<Any>
      ?: throw IllegalArgumentException("structure mismatch. List required at //${pathAt.joinToString("/")}: $this")
}