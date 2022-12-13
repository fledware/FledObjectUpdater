package fledware.objectupdater


data class UpdateMePlease(val str: String,
                          val num: Int,
                          val dec: Float,
                          val map: Map<String, Any>,
                          val lists: List<Any>,
                          val sets: Set<Any>)

data class UpdateMePlease2(val str: String,
                           val num: Int,
                           val dec: Float)

fun basicTarget() = UpdateMePlease(
    str = "hello",
    num = 234,
    dec = 345f,
    map = mapOf(
        "hello" to "world",
        "goodbye" to 345,
        "another" to mapOf(
            "haha" to "oh-no"
        )
    ),
    lists = listOf(345, "ok", "la"),
    sets = setOf(234, 567)
)