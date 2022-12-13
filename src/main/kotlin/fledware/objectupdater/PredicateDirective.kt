package fledware.objectupdater

import fledware.objectupdater.OUU.isImmutableType
import fledware.objectupdater.OUU.parentAsList
import fledware.objectupdater.OUU.parentAsMap


interface PredicateDirective : DirectiveHandler {
  fun test(valueAt: Any, directive: Directive): Boolean
}

class ContainsKeyPredicateDirective : PredicateDirective {
  override val name: String = "containsKey"

  override fun parse(updater: ObjectUpdater, original: String, inputs: String?): Directive {
    inputs ?: throw IllegalArgumentException("containsKey requires an input: $original")
    return Directive(original, name, inputs)
  }

  override fun test(valueAt: Any, directive: Directive): Boolean {
    val key = directive.extra as String
    return when (valueAt) {
      is Map<*, *> -> valueAt.containsKey(key)
      else -> throw IllegalArgumentException("containsKey directive can only works on maps")
    }
  }
}

class ContainsValuePredicateDirective : PredicateDirective {
  override val name: String = "containsValue"

  override fun parse(updater: ObjectUpdater, original: String, inputs: String?): Directive {
    inputs ?: throw IllegalArgumentException("containsValue requires an input: $original")
    return Directive(original, name, inputs)
  }

  override fun test(valueAt: Any, directive: Directive): Boolean {
    val checkValue = directive.extra as String
    fun check(value: Any): Boolean {
      if (!value.isImmutableType())
        throw IllegalArgumentException(
            "containsValue can only compare immutable types (String, Boolean, Number): $value")
      return checkValue == value.toString()
    }
    return when (valueAt) {
      is Map<*, *> -> valueAt.parentAsMap.values.any(::check)
      is List<*> -> valueAt.parentAsList.any(::check)
      else -> throw IllegalArgumentException("containsValue directive can only works on containers")
    }
  }
}

class IsEmptyPredicateDirective : PredicateDirective {
  override val name: String = "isEmpty"

  override fun test(valueAt: Any, directive: Directive): Boolean {
    return when (valueAt) {
      is Map<*, *> -> valueAt.parentAsMap.isEmpty()
      is List<*> -> valueAt.parentAsList.isEmpty()
      else -> throw IllegalArgumentException("isEmpty directive can only works on containers")
    }
  }
}

class EqualsPredicateDirective : PredicateDirective {
  data class EqualsExtra(val key: String, val value: String)

  override val name: String = "eq"

  override fun parse(updater: ObjectUpdater, original: String, inputs: String?): Directive {
    inputs ?: throw IllegalArgumentException("$name requires an input: $original")
    val inputsSplit = inputs.split(',', limit = 2)
    if (inputsSplit.size != 2)
      throw IllegalArgumentException("input requires two values. eq({key}, {value}): $original")
    return Directive(original, name, EqualsExtra(inputsSplit[0].trim(), inputsSplit[1].trim()))
  }

  override fun test(valueAt: Any, directive: Directive): Boolean {
    val extra = directive.extra as EqualsExtra
    return when (valueAt) {
      is Map<*, *> -> {
        val value = valueAt[extra.key]
        if (!value.isImmutableType())
          throw IllegalArgumentException(
              "$name can only compare immutable types (String, Boolean, Number): $value")
        extra.value == value.toString()
      }
      else -> throw IllegalArgumentException("$name directive can only works on maps")
    }
  }
}

class NegationPredicateDirective(val wrapping: PredicateDirective) : PredicateDirective {
  override val name: String = "~${wrapping.name}"

  override fun parse(updater: ObjectUpdater, original: String, inputs: String?): Directive {
    return wrapping.parse(updater, original, inputs).copy(directive = name)
  }

  override fun test(valueAt: Any, directive: Directive): Boolean {
    return !wrapping.test(valueAt, directive)
  }
}
