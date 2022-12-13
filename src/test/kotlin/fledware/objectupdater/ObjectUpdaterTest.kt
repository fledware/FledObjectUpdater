package fledware.objectupdater

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class ObjectUpdaterTest {
  val original = basicTarget()
  val updater = ObjectUpdater.default()
  val target = updater.start(original)

  @Test
  fun parsesTargetCorrectly() {
    assertEquals(
        mapOf(
            "str" to "hello",
            "num" to 234,
            "dec" to 345f,
            "map" to mapOf(
                "hello" to "world",
                "goodbye" to 345,
                "another" to mapOf(
                    "haha" to "oh-no"
                )
            ),
            "lists" to listOf(345, "ok", "la"),
            "sets" to listOf(234, 567)
        ),
        target
    )
    assertEquals(original, updater.complete(target))
  }

  @Test
  fun completeAsCanSerializeDifferent() {
    assertEquals(1, updater.unsetValues(target, "//map"))
    assertEquals(1, updater.unsetValues(target, "//lists"))
    assertEquals(1, updater.unsetValues(target, "//sets"))
    assertEquals(
        UpdateMePlease2("hello", 234, 345f),
        updater.complete(target)
    )
  }

  @Test
  fun canGetValuesAtRoot() {
    assertEquals(listOf("hello"), updater.getValues(target, "//str"))
    assertEquals(listOf(234), updater.getValues(target, "//num"))
  }

  @Test
  fun canGetValuesInMap() {
    assertEquals(listOf("world"), updater.getValues(target, "//map/hello"))
    assertEquals("world", updater.getValue(target, "//map/hello"))
    assertEquals(listOf(345), updater.getValues(target, "//map/goodbye"))
    assertEquals(listOf("oh-no"), updater.getValues(target, "//map/another/:each"))
    assertEquals(listOf(mapOf("haha" to "oh-no")), updater.getValues(target, "//map/another"))
  }

  @Test
  fun throwsOnIllegalTransversal() {
    assertEquals(1, updater.unsetValues(target, "//map/goodbye"))
    val exception = assertThrows<IllegalArgumentException> {
      updater.getValue(target, "//map/:each/haha")
    }
    assertEquals("illegal value for parent transversal: world", exception.message)
  }

  @Test
  fun canGetValuesInList() {
    assertEquals(listOf(345, "ok", "la"), updater.getValues(target, "//lists/:each"))
    assertEquals(listOf("la", "ok", 345), updater.getValues(target, "//lists/:eachr"))
    assertEquals(listOf(listOf(345, "ok", "la")), updater.getValues(target, "//lists"))
    assertEquals(listOf(345, "ok", "la"), updater.getValue(target, "//lists"))
    assertEquals(listOf(345), updater.getValues(target, "//lists/0"))
    assertEquals(listOf("ok"), updater.getValues(target, "//lists/1"))
    assertEquals(listOf("la"), updater.getValues(target, "//lists/2"))
    assertEquals(listOf("la"), updater.getValues(target, "//lists/:last"))
  }

  @Test
  fun getValueWithRegex() {
    assertEquals(
        setOf("hello", listOf(345, "ok", "la")),
        updater.getValues(target, "//:match(.*st.*)").toSet()
    )
  }

  @Test
  fun applyWorksOnRoot() {
    assertEquals(1, updater.apply(target, mapOf("num" to 345)))
    assertEquals(345, updater.getValue(target, "//num"))
  }

  @Test
  fun applyWorksOnMap() {
    val applying = mapOf("map" to mapOf(
        "hello" to "world 2",
        "lala" to 345
      )
    )
    assertEquals(2, updater.apply(target, applying))
    val newMap = mapOf(
      "hello" to "world 2",
      "goodbye" to 345,
      "lala" to 345,
      "another" to mapOf(
          "haha" to "oh-no"
      )
    )
    assertEquals(newMap, updater.getValue(target, "//map"))
  }

  @Test
  fun applyWorksOnDeepMap() {
    val applying = mapOf("map" to mapOf(
        "another" to mapOf("haha" to "oh-no-no", "lala" to 456)
    ))
    assertEquals(2, updater.apply(target, applying))
    assertEquals(mapOf("haha" to "oh-no-no", "lala" to 456),
                 updater.getValue(target, "//map/another"))
  }

  @Test
  fun applyWorksOnList() {
    val applying = mapOf("lists" to listOf("new-value!"))
    assertEquals(1, updater.apply(target, applying))
    assertEquals(listOf(345, "ok", "la", "new-value!"),
                 updater.getValue(target, "//lists"))
  }

  @Test
  fun applyBreaksOnStructureChange() {
    val applying = mapOf("lists" to mapOf("haha" to "oh-no-no", "lala" to 456))
    val exception = assertFailsWith<IllegalArgumentException> {
      updater.apply(target, applying)
    }
    assertEquals("structure mismatch at //lists: found Map, required List", exception.message)
  }
}