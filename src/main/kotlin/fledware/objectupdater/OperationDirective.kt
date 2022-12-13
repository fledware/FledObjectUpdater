package fledware.objectupdater

import fledware.objectupdater.OUU.deepCopy
import fledware.objectupdater.OUU.isAllowedValueType
import fledware.objectupdater.OUU.keyAsInt
import fledware.objectupdater.OUU.keyAsString
import fledware.objectupdater.OUU.operationRequiresJsonValue
import fledware.objectupdater.OUU.parentAsList
import fledware.objectupdater.OUU.parentAsMap
import fledware.objectupdater.OUU.parseToBaseType


interface OperationDirective : DirectiveHandler {
  fun operate(parentAt: Any, keyAt: Any, valueAt: Any?, directive: Directive): Any?
}


// ==================================================================
//
// GetOperation and friends
//
// ==================================================================

fun ObjectUpdater.getValue(target: Any, path: String): Any {
  val check = getValuesAt(target, parsePath(path))
  if (check.size != 1)
    throw IllegalArgumentException("exactly one value not found at (${check.size}): $path")
  return check[0]
}

fun ObjectUpdater.getValueOrNull(target: Any, path: String): Any? {
  val check = getValuesAt(target, parsePath(path))
  if (check.size > 1)
    throw IllegalArgumentException("more than one value found (${check.size}): $path")
  return if (check.isEmpty()) null else check[0]
}

fun ObjectUpdater.getValues(target: Any, path: String): List<Any> {
  return getValuesAt(target, parsePath(path))
}

fun ObjectUpdater.getValuesAt(target: Any, path: List<DirectivePath>): List<Any> {
  return executeResult(target, DirectiveCommand(path, null))
}


// ==================================================================
//
// SetOperation and friends
//
// ==================================================================

open class SetOperation : OperationDirective {
  override val name: String = "set"

  override fun parse(updater: ObjectUpdater, original: String, inputs: String?): Directive {
    return Directive(original, name, updater.mapper.parseToBaseType(original, inputs))
  }

  override fun operate(parentAt: Any, keyAt: Any, valueAt: Any?, directive: Directive): Any? {
    val value = directive.extra.operationRequiresJsonValue(directive)
    when (parentAt) {
      is MutableMap<*, *> -> parentAt.parentAsMap[keyAt.keyAsString] = value.deepCopy()
      is MutableList<*> -> parentAt.parentAsList[keyAt.keyAsInt] = value.deepCopy()
      else -> throw IllegalArgumentException("illegal value for container transversal: $parentAt")
    }
    return value
  }
}

fun ObjectUpdater.setValues(target: Any, path: String, value: Any): Int {
  return setValuesAt(target, parsePath(path), value)
}

fun ObjectUpdater.setValuesAt(target: Any, path: List<DirectivePath>, value: Any): Int {
  if (!value.isAllowedValueType())
    throw IllegalArgumentException("value type not allowed: $value")
  return executeCount(target, DirectiveCommand(path, Directive("set", "set", value)))
}


// ==================================================================
//
// SetIfAbsentOperation and friends
//
// ==================================================================

class SetIfAbsentOperation : SetOperation() {
  override val name: String = "setIfAbsent"

  override fun operate(parentAt: Any, keyAt: Any, valueAt: Any?, directive: Directive): Any? {
    if (valueAt != null) return null
    return super.operate(parentAt, keyAt, null, directive)
  }
}

fun ObjectUpdater.setValueIfAbsent(target: Any, path: String, value: Any): Int {
  return setValueAtIfAbsent(target, parsePath(path), value)
}

fun ObjectUpdater.setValueAtIfAbsent(target: Any, path: List<DirectivePath>, value: Any): Int {
  if (!value.isAllowedValueType())
    throw IllegalArgumentException("value type not allowed: $value")
  return executeCount(target, DirectiveCommand(path, Directive("setIfAbsent", "setIfAbsent", value)))
}


// ==================================================================
//
// UnsetOperation and friends
//
// ==================================================================

open class UnsetOperation : OperationDirective {
  override val name: String = "unset"

  override fun parse(updater: ObjectUpdater, original: String, inputs: String?): Directive {
    return Directive(original, name, updater.mapper.parseToBaseType(original, inputs))
  }

  override fun operate(parentAt: Any, keyAt: Any, valueAt: Any?, directive: Directive): Any? {
    return when (parentAt) {
      is MutableMap<*, *> -> parentAt.parentAsMap.remove(keyAt.keyAsString)
      is MutableList<*> -> parentAt.parentAsList.removeAt(keyAt.keyAsInt)
      else -> throw IllegalArgumentException("illegal value for container transversal: $parentAt")
    }
  }
}

fun ObjectUpdater.unsetValues(target: Any, path: String): Int {
  return unsetValuesAt(target, parsePath(path))
}

fun ObjectUpdater.unsetValuesAt(target: Any, path: List<DirectivePath>): Int {
  return executeCount(target, DirectiveCommand(path, Directive("unset", "unset", null)))
}


// ==================================================================
//
// AppendOperation and friends
//
// ==================================================================

open class AppendOperation : OperationDirective {
  override val name: String = "append"

  override fun parse(updater: ObjectUpdater, original: String, inputs: String?): Directive {
    return Directive(original, name, updater.mapper.parseToBaseType(original, inputs))
  }

  override fun operate(parentAt: Any, keyAt: Any, valueAt: Any?, directive: Directive): Any? {
    if (valueAt == null) return null
    val value = directive.extra.operationRequiresJsonValue(directive)
    when (valueAt) {
      is MutableList<*> -> valueAt.parentAsList += value.deepCopy()
      else -> throw IllegalArgumentException("illegal value for container transversal: $valueAt")
    }
    return value
  }
}

fun ObjectUpdater.appendCollections(target: Any, path: String, value: Any): Int {
  return appendCollectionsAt(target, parsePath(path), value)
}

fun ObjectUpdater.appendCollectionsAt(target: Any, path: List<DirectivePath>, value: Any): Int {
  if (!value.isAllowedValueType())
    throw IllegalArgumentException("value type not allowed: $value")
  return executeCount(target, DirectiveCommand(path, Directive("append", "append", value)))
}


// ==================================================================
//
// AppendIfAbsentOperation and friends
//
// ==================================================================

class AppendIfAbsentOperation : OperationDirective {
  override val name: String = "appendIfAbsent"

  override fun parse(updater: ObjectUpdater, original: String, inputs: String?): Directive {
    return Directive(original, name, updater.mapper.parseToBaseType(original, inputs))
  }

  override fun operate(parentAt: Any, keyAt: Any, valueAt: Any?, directive: Directive): Any? {
    if (valueAt == null) return null
    val value = directive.extra.operationRequiresJsonValue(directive)
    when (valueAt) {
      is MutableList<*> -> {
        val list = valueAt.parentAsList
        if (value in list) return null
        list += value
      }
      else -> throw IllegalArgumentException("illegal value for container transversal: $valueAt")
    }
    return value
  }
}

fun ObjectUpdater.appendCollectionsIfAbsent(target: Any, path: String, value: Any): Int {
  return appendCollectionsAtIfAbsent(target, parsePath(path), value)
}

fun ObjectUpdater.appendCollectionsAtIfAbsent(target: Any, path: List<DirectivePath>, value: Any): Int {
  if (!value.isAllowedValueType())
    throw IllegalArgumentException("value type not allowed: $value")
  return executeCount(target, DirectiveCommand(path, Directive("appendIfAbsent", "appendIfAbsent", value)))
}


// ==================================================================
//
// ClearOperation and friends
//
// ==================================================================

class ClearOperation : OperationDirective {
  override val name: String = "clear"

  override fun operate(parentAt: Any, keyAt: Any, valueAt: Any?, directive: Directive): Any? {
    if (valueAt == null) return null
    when (valueAt) {
      is MutableMap<*, *> -> valueAt.clear()
      is MutableList<*> -> valueAt.clear()
      else -> throw IllegalArgumentException("illegal value for container transversal: $valueAt")
    }
    return this
  }
}

fun ObjectUpdater.clearContainer(target: Any, path: String): Int {
  return clearContainerAt(target, parsePath(path))
}

fun ObjectUpdater.clearContainerAt(target: Any, path: List<DirectivePath>): Int {
  return executeCount(target, DirectiveCommand(path, Directive("clear", "clear", null)))
}


// ==================================================================
//
// TimesOperation
//
// ==================================================================

class TimesOperation : OperationDirective {
  override val name: String = "times"

  override fun parse(updater: ObjectUpdater, original: String, inputs: String?): Directive {
    inputs ?: throw IllegalArgumentException("times requires a number input")
    val timesBy = inputs.toDoubleOrNull()
        ?: throw IllegalArgumentException("invalid double format '$inputs' for $original ")
    return Directive(original, name, timesBy)
  }

  override fun operate(parentAt: Any, keyAt: Any, valueAt: Any?, directive: Directive): Any? {
    if (valueAt == null) return null
    val timesBy = directive.extra as? Number ?: throw IllegalArgumentException(
        "directive must have a number as extra: $directive")
    val newValue = when (valueAt) {
      is Long -> valueAt * timesBy.toLong()
      is Int -> valueAt * timesBy.toInt()
      is Double -> valueAt * timesBy.toDouble()
      is Float -> valueAt * timesBy.toFloat()
      else -> throw IllegalArgumentException("illegal value for multiplication : $this")
    }
    when (parentAt) {
      is MutableMap<*, *> -> parentAt.parentAsMap[keyAt.keyAsString] = newValue
      is MutableList<*> -> parentAt.parentAsList[keyAt.keyAsInt] = newValue
      else -> throw IllegalArgumentException("illegal value for container transversal: $parentAt")
    }
    return newValue
  }
}
