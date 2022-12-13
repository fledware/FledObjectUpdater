package fledware.objectupdater

import fledware.objectupdater.OUU.keyAsInt
import fledware.objectupdater.OUU.keyAsString


interface SelectDirective : DirectiveHandler {
  fun select(valueAt: Any, directive: Directive): List<Any>
}

/**
 * This directive is used to unify how the transverse algorithm
 * works within the [ObjectUpdater].
 *
 * Added benefit is that it allows default indexing to be overridden.
 */
class DefaultSelectDirective : SelectDirective {
  override val name: String = "default"

  override fun parse(updater: ObjectUpdater, original: String, inputs: String?): Directive {
    val extra = original.toIntOrNull() ?: original
    val valid = when (extra) {
      is String -> ObjectUpdater.allowedPath.matches(extra)
      is Int -> 0 <= extra
      else -> false
    }
    if (!valid)
      throw IllegalArgumentException("unable to parse directive, invalid part: $original")
    return Directive(original, null, extra)
  }

  override fun select(valueAt: Any, directive: Directive): List<Any> {
    val key = when (valueAt) {
      is List<*> -> directive.extra.keyAsInt
      is Map<*, *> -> directive.extra.keyAsString
      else -> throw IllegalArgumentException("illegal value for parent transversal: $valueAt")
    }
    return listOf(key)
  }
}

/**
 * Selects the last index of valueAt.
 */
class LastSelectDirective : SelectDirective {
  override val name: String = "last"

  override fun select(valueAt: Any, directive: Directive): List<Any> {
    return when (valueAt) {
      is List<*> -> listOf(valueAt.lastIndex)
      else -> throw IllegalArgumentException("last directive can only work on collections")
    }
  }
}

/**
 * Selects all keys of the given object.
 */
class EachSelectDirective : SelectDirective {
  override val name: String = "each"

  override fun select(valueAt: Any, directive: Directive): List<Any> {
    @Suppress("UNCHECKED_CAST")
    return when (valueAt) {
      is List<*> -> valueAt.indices.toList()
      is Map<*, *> -> (valueAt.keys as Set<Any>).toList()
      else -> throw IllegalArgumentException("last directive can only work on collections")
    }
  }
}

/**
 * Selects all keys of the given object, but reverses them for lists.
 */
class EachReversedSelectDirective : SelectDirective {
  override val name: String = "eachr"

  override fun select(valueAt: Any, directive: Directive): List<Any> {
    @Suppress("UNCHECKED_CAST")
    return when (valueAt) {
      is List<*> -> valueAt.indices.reversed().toList()
      is Map<*, *> -> (valueAt.keys as Set<Any>).toList()
      else -> throw IllegalArgumentException("last directive can only work on collections")
    }
  }
}

/**
 * Selects keys based on a regex.
 */
class MatchSelectDirective : SelectDirective {
  override val name: String = "match"

  override fun parse(updater: ObjectUpdater, original: String, inputs: String?): Directive {
    inputs ?: throw IllegalArgumentException("match directive requires a regex in inputs: $original")
    return Directive(original, name, inputs.toRegex())
  }

  override fun select(valueAt: Any, directive: Directive): List<Any> {
    val regex = directive.extra as? Regex
        ?: throw IllegalArgumentException("regex not found: $directive")
    @Suppress("UNCHECKED_CAST")
    return when (valueAt) {
      is Map<*, *> -> {
        valueAt as Map<String, Any>
        valueAt.keys.filter { regex.matches(it) }
      }
      else -> throw IllegalArgumentException("match directive can only work on maps")
    }
  }
}
