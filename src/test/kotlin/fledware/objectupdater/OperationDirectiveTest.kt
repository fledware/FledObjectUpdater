package fledware.objectupdater

import fledware.objectupdater.OUU.isImmutableType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


// ==================================================================
//
//
//
// ==================================================================

abstract class MutationTest {
  companion object {
    @JvmStatic
    fun getData() = listOf(
        Arguments.of(::mutateWithExtension),
        Arguments.of(::mutateWithoutExtension),
    )

    private fun mutateWithExtension(updater: ObjectUpdater, target: Any,
                                    path: String, command: String, value: Any?): Int {
      return when (command) {
        "set" -> updater.setValues(target, path, value!!)
        "setIfAbsent" -> updater.setValueIfAbsent(target, path, value!!)
        "unset" -> updater.unsetValues(target, path)
        "append" -> updater.appendCollections(target, path, value!!)
        "appendIfAbsent" -> updater.appendCollectionsIfAbsent(target, path, value!!)
        "clear" -> updater.clearContainer(target, path)
        else -> throw IllegalArgumentException("mutateWithExtension: $command")
      }
    }

    private fun mutateWithoutExtension(updater: ObjectUpdater, target: Any,
                                       path: String, command: String, value: Any?): Int {
      val serializedValue: String = when {
        value == null -> ""
        value.isImmutableType() -> value.toString()
        else -> updater.mapper.writeValueAsString(value)
      }
      val fullCommand = "$path!$command($serializedValue)"
      println(fullCommand)
      return updater.executeCount(target, fullCommand)
    }
  }

  val original = basicTarget()
  val updater = ObjectUpdater.default()
  val target = updater.start(original)
}

typealias MutationLambda = (updater: ObjectUpdater, target: Any,
                            path: String, command: String, value: Any?) -> Int


// ==================================================================
//
//
//
// ==================================================================

class SetOperationTest : MutationTest() {
  @ParameterizedTest
  @MethodSource("getData")
  fun canSetValueAtRoot(mutation: MutationLambda) {
    assertEquals("hello", updater.getValueOrNull(target, "//str"))
    assertEquals(1, mutation(updater, target, "//str", "set", "goodbye"))
    assertEquals("goodbye", updater.getValueOrNull(target, "//str"))
  }

  @ParameterizedTest
  @MethodSource("getData")
  fun canSetValueOnMapWithExtension(mutation: MutationLambda) {
    assertEquals(1, mutation(updater, target, "//map/hello", "set", "goodbye"))
    val expected = original.copy(
        map = LinkedHashMap(original.map).also { it["hello"] = "goodbye" }
    )
    assertEquals(expected, updater.complete(target, original::class))
  }

  @ParameterizedTest
  @MethodSource("getData")
  fun canSetValuesOnDeepMap(mutation: MutationLambda) {
    assertEquals(1, mutation(updater, target, "//map/another/lala", "set", "poo"))
    val expected = original.copy(
        map = LinkedHashMap(original.map).also {
          it["another"] = mapOf("haha" to "oh-no", "lala" to "poo")
        }
    )
    assertEquals(expected, updater.complete(target))
  }

  @ParameterizedTest
  @MethodSource("getData")
  fun canSetAllValuesOnMap(mutation: MutationLambda) {
    assertEquals(3, mutation(updater, target, "//map/:each", "set", "oops"))
    val expected = original.copy(
        map = mapOf("hello" to "oops", "goodbye" to "oops", "another" to "oops")
    )
    assertEquals(expected, updater.complete(target))
  }

  @ParameterizedTest
  @MethodSource("getData")
  fun canSetListToEmpty(mutation: MutationLambda) {
    assertEquals(listOf(345, "ok", "la"), updater.getValueOrNull(target, "//lists"))
    assertEquals(1, mutation(updater, target, "//lists", "set", mutableListOf<Any>()))
    assertEquals(listOf<Any>(), updater.getValueOrNull(target, "//lists"))
  }

  @ParameterizedTest
  @MethodSource("getData")
  fun canSetAMapToAList(mutation: MutationLambda) {
    assertEquals(listOf(345, "ok", "la"), updater.getValueOrNull(target, "//lists"))
    assertEquals(1, mutation(updater, target, "//lists/:last", "set", mapOf("hello" to "world")))
    assertEquals(listOf(345, "ok", mapOf("hello" to "world")),
                 updater.getValueOrNull(target, "//lists"))
  }
}


// ==================================================================
//
//
//
// ==================================================================

class SetIfAbsentOperationTest : MutationTest() {
  @ParameterizedTest
  @MethodSource("getData")
  fun setValueIfAbsentDoesntSetValue(mutation: MutationLambda) {
    assertEquals(mapOf("haha" to "oh-no"), updater.getValue(target, "//map/another"))
    assertEquals(0, mutation(updater, target, "//map/another", "setIfAbsent", "hello"))
    assertEquals(mapOf("haha" to "oh-no"), updater.getValue(target, "//map/another"))
  }

  @ParameterizedTest
  @MethodSource("getData")
  fun setValueIfAbsentDoesSetValue(mutation: MutationLambda) {
    assertEquals(null, updater.getValueOrNull(target, "//map/another2"))
    assertEquals(1, mutation(updater, target, "//map/another2", "setIfAbsent", "hello"))
    assertEquals("hello", updater.getValue(target, "//map/another2"))
  }
}


// ==================================================================
//
//
//
// ==================================================================

class UnsetOperationTest : MutationTest() {
  @ParameterizedTest
  @MethodSource("getData")
  fun canUnsetRootValue(mutation: MutationLambda) {
    assertEquals(234, updater.getValueOrNull(target, "//num"))
    assertEquals(1, mutation(updater, target, "//num", "unset", null))
    assertEquals(null, updater.getValueOrNull(target, "//num"))
  }

  @ParameterizedTest
  @MethodSource("getData")
  fun canClearValueInMap(mutation: MutationLambda) {
    assertEquals(mapOf("haha" to "oh-no"), updater.getValueOrNull(target, "//map/another"))
    assertEquals(1, mutation(updater, target, "//map/another", "unset", null))
    assertEquals(null, updater.getValueOrNull(target, "//map/another"))
  }

  @ParameterizedTest
  @MethodSource("getData")
  fun canClearAllValueInDeepMap(mutation: MutationLambda) {
    assertEquals(mapOf("haha" to "oh-no"), updater.getValueOrNull(target, "//map/another"))
    assertEquals(1, mutation(updater, target, "//map/another/:each", "unset", null))
    assertEquals(mapOf<String, Any>(), updater.getValueOrNull(target, "//map/another"))
  }

  @ParameterizedTest
  @MethodSource("getData")
  fun canClearAllValuesInList(mutation: MutationLambda) {
    assertEquals(listOf(345, "ok", "la"), updater.getValueOrNull(target, "//lists"))
    assertEquals(3, mutation(updater, target, "//lists/:eachr", "unset", null))
    assertEquals(listOf<Any>(), updater.getValueOrNull(target, "//lists"))
  }
}


// ==================================================================
//
//
//
// ==================================================================

class AppendOperationTest : MutationTest() {
  @ParameterizedTest
  @MethodSource("getData")
  fun canAppendValueInList(mutation: MutationLambda) {
    assertEquals(listOf(345, "ok", "la"), updater.getValue(target, "//lists"))
    assertEquals(1, mutation(updater, target, "//lists", "append", "lala"))
    assertEquals(listOf(345, "ok", "la", "lala"), updater.getValue(target, "//lists"))
  }

  @ParameterizedTest
  @MethodSource("getData")
  fun canAppendValueInListIfDuplicate(mutation: MutationLambda) {
    assertEquals(listOf(345, "ok", "la"), updater.getValue(target, "//lists"))
    assertEquals(1, mutation(updater, target, "//lists", "append", "ok"))
    assertEquals(listOf(345, "ok", "la", "ok"), updater.getValue(target, "//lists"))
  }

  @ParameterizedTest
  @MethodSource("getData")
  fun canAppendMultipleCollections(mutation: MutationLambda) {
    assertEquals(listOf(listOf(345, "ok", "la"), listOf(234, 567)),
                 updater.getValues(target, "//:match(.*ts)"))
    assertEquals(2, mutation(updater, target, "//:match(.*ts)", "append", "lala"))
    assertEquals(listOf(listOf(345, "ok", "la", "lala"), listOf(234, 567, "lala")),
                 updater.getValues(target, "//:match(.*ts)"))
  }
}


// ==================================================================
//
//
//
// ==================================================================

class AppendIfAbsentOperationTest : MutationTest() {
  @ParameterizedTest
  @MethodSource("getData")
  fun canAppendValueInList(mutation: MutationLambda) {
    assertEquals(listOf(345, "ok", "la"), updater.getValue(target, "//lists"))
    assertEquals(1, mutation(updater, target, "//lists", "appendIfAbsent", "lala"))
    assertEquals(listOf(345, "ok", "la", "lala"), updater.getValue(target, "//lists"))
  }

  @ParameterizedTest
  @MethodSource("getData")
  fun doesntAppendValueIfExists(mutation: MutationLambda) {
    assertEquals(listOf(345, "ok", "la"), updater.getValue(target, "//lists"))
    assertEquals(0, mutation(updater, target, "//lists", "appendIfAbsent", "la"))
    assertEquals(listOf(345, "ok", "la"), updater.getValue(target, "//lists"))
    assertEquals(0, mutation(updater, target, "//lists", "appendIfAbsent", 345))
    assertEquals(listOf(345, "ok", "la"), updater.getValue(target, "//lists"))
  }
}


// ==================================================================
//
//
//
// ==================================================================

class ClearOperationTest : MutationTest() {
  @ParameterizedTest
  @MethodSource("getData")
  fun canClearDeepMap(mutation: MutationLambda) {
    assertEquals(listOf(mapOf("haha" to "oh-no")), updater.executeResult(target, "//map/another"))
    assertEquals(1, mutation(updater, target, "//map/another", "clear", null))
    assertEquals(listOf(mapOf<String, Any>()), updater.executeResult(target, "//map/another"))
  }

  @ParameterizedTest
  @MethodSource("getData")
  fun canClearList(mutation: MutationLambda) {
    assertEquals(listOf(345, "ok", "la"), updater.getValue(target, "//lists"))
    assertEquals(1, mutation(updater, target, "//lists", "clear", null))
    assertEquals(listOf<Any>(), updater.getValue(target, "//lists"))
  }

  @ParameterizedTest
  @MethodSource("getData")
  fun canClearWithMatch(mutation: MutationLambda) {
    assertEquals(listOf(listOf(345, "ok", "la"), listOf(234, 567)),
                 updater.getValues(target, "//:match(.*ts)"))
    assertEquals(2, mutation(updater, target, "//:match(.*ts)", "clear", null))
    assertEquals(listOf(listOf<Any>(), listOf()),
                 updater.getValues(target, "//:match(.*ts)"))
  }

  @Test
  fun errorsWithExtensionWhenDirectiveIncluded() {
    val exception = assertFailsWith<IllegalArgumentException> {
      updater.clearContainer(target, "//:match(.*ts)!clear")
    }
    assertEquals("an operation is not allowed in the path: //:match(.*ts)!clear", exception.message)
  }
}
