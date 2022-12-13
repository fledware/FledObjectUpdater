package fledware.objectupdater

import com.fasterxml.jackson.databind.ObjectMapper
import fledware.objectupdater.OUU.deepCopy
import fledware.objectupdater.OUU.getType
import fledware.objectupdater.OUU.isAllowedValueType
import fledware.objectupdater.OUU.isDirective
import fledware.objectupdater.OUU.isImmutableType
import fledware.objectupdater.OUU.otherAsList
import fledware.objectupdater.OUU.otherAsMap
import fledware.objectupdater.OUU.parentAsList
import fledware.objectupdater.OUU.parentAsMap
import fledware.objectupdater.OUU.parseRawCommandWithInput
import fledware.objectupdater.OUU.parseRawPathDirective
import fledware.objectupdater.OUU.toBaseType
import fledware.objectupdater.OUU.transverse
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass


/**
 *
 */
@Suppress("MemberVisibilityCanBePrivate")
open class ObjectUpdater(
    val mapper: ObjectMapper,
    val selectDefault: SelectDirective,
    val selects: Map<String, SelectDirective>,
    val predicates: Map<String, PredicateDirective>,
    val operations: Map<String, OperationDirective>,
) {
  companion object {
    private val logger = LoggerFactory.getLogger(this::class.java)

    val allowedPath = "^[a-zA-Z_][a-zA-Z0-9_]*$".toRegex()

    fun builder() = ObjectUpdaterBuilder()

    fun default() = builder()
        .withSelect(LastSelectDirective())
        .withSelect(EachSelectDirective())
        .withSelect(EachReversedSelectDirective())
        .withSelect(MatchSelectDirective())
        .withPredicate(ContainsKeyPredicateDirective())
        .withPredicate(ContainsValuePredicateDirective())
        .withPredicate(IsEmptyPredicateDirective())
        .withPredicate(EqualsPredicateDirective())
        .withOperation(SetOperation())
        .withOperation(SetIfAbsentOperation())
        .withOperation(UnsetOperation())
        .withOperation(AppendOperation())
        .withOperation(AppendIfAbsentOperation())
        .withOperation(ClearOperation())
        .withOperation(TimesOperation())
        .build()
  }

  fun getOperation(directive: Directive): OperationDirective {
    return operations[directive.directive] ?: throw IllegalArgumentException(
        "OperationDirective not found ($directive): " +
            "are you using the same ObjectUpdater for parsing and updating?")
  }

  fun getSelect(directive: Directive): SelectDirective {
    if (directive.directive == null)
      return selectDefault
    return selects[directive.directive] ?: throw IllegalArgumentException(
        "SelectDirective not found ($directive): " +
            "are you using the same ObjectUpdater for parsing and updating?")
  }

  fun getPredicate(directive: Directive): PredicateDirective {
    return predicates[directive.directive] ?: throw IllegalArgumentException(
        "PredicateDirective not found ($directive): " +
            "are you using the same ObjectUpdater for parsing and updating?")
  }

  fun start(target: Any) = target.toBaseType()

  protected open fun select(parentAtPath: Any, directiveAt: Directive) =
      this.getSelect(directiveAt).select(parentAtPath, directiveAt)

  protected open fun acceptValue(valueAt: Any, directiveAt: Directive?) =
      directiveAt?.let { this.getPredicate(it).test(valueAt, it) } ?: true

  open fun parsePath(path: String): List<DirectivePath> {
    if (!path.startsWith("//"))
      throw IllegalArgumentException("path must start with '/': $path")
    if (path.contains('!'))
      throw IllegalArgumentException("an operation is not allowed in the path: $path")
    val results = mutableListOf<DirectivePath>()
    val pathSplit = path.substring(2).split('/')
    if (pathSplit.any { it.isBlank() })
      throw IllegalArgumentException("path must not have empty elements: $path")
    for ((index, part) in pathSplit.withIndex()) {
      val rawParsed = part.parseRawPathDirective()
      val predicate = rawParsed.predicate?.let {
        val predicateHandler = predicates[it.first] ?: throw IllegalArgumentException(
            "predicate directive handler not found at $index ($path): $it")
        predicateHandler.parse(this, part, it.second)
      }
      val rawSelect = rawParsed.select
      val select: Directive = when {
        rawSelect.first.isDirective() -> {
          val selectHandler = selects[rawSelect.first.substring(1)]
              ?: throw IllegalArgumentException(
                  "select directive handler not found at $index ($path): ${rawSelect.first}")
          selectHandler.parse(this, part, rawSelect.second)
        }
        else -> selectDefault.parse(this, rawSelect.first, rawSelect.second)
      }
      results += DirectivePath(select, predicate)
    }
    return results
  }

  open fun parseOperation(operation: String?): Directive? {
    if (operation == null) return null
    if (operation.isBlank())
      throw IllegalArgumentException("path must not have empty elements: $this")
    val rawOperation = operation.parseRawCommandWithInput()
    val operationHandler = operations[rawOperation.first] ?: throw IllegalArgumentException(
        "operation directive handler not found ($operation): ${rawOperation.first}")
    return operationHandler.parse(this, rawOperation.first, rawOperation.second)
  }

  open fun parseCommand(command: String): DirectiveCommand {
    val commandSplit = command.split('!')
    val operation: Directive? = when (commandSplit.size) {
      1 -> null
      2 -> parseOperation(commandSplit[1])
      else -> throw IllegalArgumentException(
          "invalid command. unable to split path from command: $command")
    }
    return DirectiveCommand(parsePath(commandSplit[0]), operation)
  }

  /**
   *
   */
  fun apply(target: Any, overrides: Any): Int {
    if (!overrides.isAllowedValueType())
      throw IllegalArgumentException("value type not allowed (${overrides::class}): $overrides")
    return applyRecursive(mutableListOf(), target, overrides)
  }

  private fun applyRecursive(applyPathAt: MutableList<String>,
                             thisAt: Any, thatAt: Any): Int {
    var result = 0
    when (thatAt) {
      is MutableMap<*, *> -> {
        val thisAs = thisAt.otherAsMap(applyPathAt)
        thatAt.parentAsMap.forEach { (key, thatValue) ->
          applyPathAt += key
          val thisValue = thisAs[key]
          assertStructureConflicts(applyPathAt, thisValue, thatValue)
          when {
            // if the value doesn't exist, then just copy whatever is being applied
            thisValue == null -> {
              result++
              thisAs[key] = thatValue.deepCopy()
            }
            // we want to handle numbers carefully so the type doesn't change
            thisValue is Number -> {
              result++
              thisAs[key] = when (thisValue) {
                is Byte -> (thatValue as Number).toByte()
                is Short -> (thatValue as Number).toShort()
                is Int -> (thatValue as Number).toInt()
                is Long -> (thatValue as Number).toLong()
                is Float -> (thatValue as Number).toFloat()
                is Double -> (thatValue as Number).toDouble()
                else -> throw IllegalStateException("unable to figure number type: $thisValue -> $thatValue")
              }
            }
            // if the value being applied is not a collection, set the value
            thatValue.isImmutableType() -> {
              result++
              thisAs[key] = thatValue
            }
            // recursively apply the structure
            else -> {
              result += applyRecursive(applyPathAt, thisValue, thatValue)
            }
          }
          applyPathAt.removeLast()
        }
      }
      is MutableList<*> -> {
        val thisAs = thisAt.otherAsList(applyPathAt)
        thatAt.parentAsList.otherAsList(applyPathAt).forEach {
          result++
          thisAs += it.deepCopy()
        }
      }
      else -> throw IllegalArgumentException("illegal value for container transversal: $thatAt")
    }
    return result
  }

  private fun assertStructureConflicts(applyPathAt: MutableList<String>,
                                       thisAt: Any?, thatAt: Any?) {
    if (thisAt == null || thatAt == null) return
    when (thisAt) {
      is Boolean -> thatAt as? Boolean
      is Number -> thatAt as? Number
      is String -> thatAt as? String
      is MutableList<*> -> thatAt as? MutableList<*>
      is MutableMap<*, *> -> thatAt as? MutableMap<*, *>
      else -> throw IllegalArgumentException("illegal value for container transversal: $thisAt")
    } ?: throw IllegalArgumentException(
        "structure mismatch at //${applyPathAt.joinToString("/")}: " +
            "found ${thatAt.getType()}, required ${thisAt.getType()}")
  }

  /**
   *
   */
  fun executeCount(target: Any, command: String): Int {
    return executeCount(target, parseCommand(command))
  }

  /**
   *
   */
  fun executeCount(target: Any, command: DirectiveCommand): Int {
    var count = 0
    val last = command.path.last()
    forEachParentOf(target, command.path) { rawParent ->
      select(rawParent, last.select).forEach { key ->
        val value = rawParent.transverse(key)
        if (value != null && !acceptValue(value, last.predicate))
          return@forEach
        val operation = command.operation
        if (operation == null) {
          count++
          return@forEach
        }
        if (getOperation(operation).operate(rawParent, key, value, operation) != null) {
          count++
        }
      }
    }
    return count
  }

  /**
   *
   */
  fun executeResult(target: Any, command: String): List<Any> {
    return executeResult(target, parseCommand(command))
  }

  /**
   *
   */
  fun executeResult(target: Any, command: DirectiveCommand): List<Any> {
    val results = mutableListOf<Any>()
    val last = command.path.last()
    forEachParentOf(target, command.path) { rawParent ->
      select(rawParent, last.select).forEach { key ->
        val value = rawParent.transverse(key)
        if (value != null && !acceptValue(value, last.predicate))
          return@forEach
        when (val operation = command.operation) {
          null -> value
          else -> getOperation(operation).operate(rawParent, key, value, operation)
        }?.also { results += it }
      }
    }
    return results
  }

  /**
   *
   */
  fun <T : Any> complete(target: Any, type: KClass<T>): T {
    return mapper.readValue(mapper.writeValueAsBytes(target), type.java)
  }


  // ================================================================
  //
  // pathing compiled
  //
  // ================================================================

  protected open fun forEachParentOf(target: Any, path: List<DirectivePath>,
                                     forValue: (parentAtPath: Any) -> Unit) {
    forEachValueOfRecursive(path, 0, path.lastIndex - 1, target, forValue)
  }

  protected open fun forEachValueOf(target: Any, path: List<DirectivePath>,
                                    forValue: (valueAtPath: Any) -> Unit) {
    forEachValueOfRecursive(path, 0, path.lastIndex, target, forValue)
  }

  private fun forEachValueOfRecursive(
      path: List<DirectivePath>,
      indexAt: Int,
      indexMax: Int,
      parentAt: Any?,
      forValue: (valueAt: Any) -> Unit
  ) {
    check(indexAt < path.size) { "indexAt at cannot be greater than path ($indexAt >= ${path.size})" }
    checkNotNull(parentAt) { "value null at $indexAt: $path" }
    if (indexMax == -1 && indexAt == 0) {
      // special case where we want to return the parent
      forValue(parentAt)
    }
    else {
      val directiveAt = path[indexAt]
      val isPathFinal = indexMax == indexAt
      select(parentAt, directiveAt.select).forEach { key ->
        val value = parentAt.transverse(key) ?: return@forEach
        if (acceptValue(value, directiveAt.predicate)) {
          if (isPathFinal)
            forValue(value)
          else
            forEachValueOfRecursive(path, indexAt + 1, indexMax, value, forValue)
        }
      }
    }
  }
}

inline fun <reified T : Any> ObjectUpdater.complete(target: Any): T {
  return mapper.readValue(mapper.writeValueAsBytes(target), T::class.java)
}
