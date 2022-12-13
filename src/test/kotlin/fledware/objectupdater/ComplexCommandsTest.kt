package fledware.objectupdater

import kotlin.test.Test
import kotlin.test.assertEquals

class ComplexCommandsTest {
  val original = basicTarget()
  val updater = ObjectUpdater.default()
  val target = updater.start(original)

  @Test
  fun testContainsKeyThenMultiply() {
    assertEquals(345, updater.getValue(target, "//map/goodbye"))
    assertEquals(1, updater.executeCount(target, "//map#containsKey(hello)/goodbye!times(2)"))
    assertEquals(690, updater.getValue(target, "//map/goodbye"))
  }

  @Test
  fun testNotContainsKeyThenMultiply() {
    assertEquals(345, updater.getValue(target, "//map/goodbye"))
    assertEquals(0, updater.executeCount(target, "//map#~containsKey(hello)/goodbye!times(2)"))
    assertEquals(345, updater.getValue(target, "//map/goodbye"))
  }

  @Test
  fun testContainsValueThenMultiply() {
    assertEquals(345, updater.getValue(target, "//map/goodbye"))
    assertEquals(1, updater.executeCount(target, "//map#containsValue(world)/goodbye!times(2)"))
    assertEquals(690, updater.getValue(target, "//map/goodbye"))
  }

  @Test
  fun testSetsValueIfDoesntExist() {
    assertEquals(0, updater.executeCount(target, "//map#isEmpty/lala!set(200)"))
    assertEquals(1, updater.executeCount(target, "//map!clear"))
    assertEquals(emptyMap<String, Any>(), updater.getValue(target, "//map"))
    assertEquals(1, updater.executeCount(target, "//map#isEmpty/lala!set(200)"))
    assertEquals(200, updater.getValue(target, "//map/lala"))
  }


  private fun makeListObjects() {
    fun createElement(index: Int): String {
      return updater.mapper.writeValueAsString(mapOf(
          "name" to "world ${index % 3}",
          "value" to index
      ))
    }
    assertEquals(1, updater.executeCount(target, "//lists!clear"))
    repeat(10) {
      assertEquals(1, updater.executeCount(target, "//lists!append(${createElement(it)})"))
    }
  }

  @Test
  fun canRemoveValuesBasedOnMapValuesInList() {
    makeListObjects()
    assertEquals(1, updater.executeCount(target, "//lists/:each#eq(value, 9)"))
    assertEquals(3, updater.executeCount(target, "//lists/:each#eq(name, world 2)/value!set(9)"))
    assertEquals(4, updater.executeCount(target, "//lists/:each#eq(value, 9)"))
  }
}