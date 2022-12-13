package fledware.objectupdater


/**
 * A complete command. It has a path to select the objects,
 * and then the operation that does work on those.
 *
 * If there is no operation, then it will assume the command
 * is for counting or aggregating.
 */
data class DirectiveCommand(val path: List<DirectivePath>,
                            val operation: Directive?)

/**
 * Selection of specific objects and paths within a command
 */
data class DirectivePath(val select: Directive, val predicate: Directive?)

/**
 * Configuration for a specific [DirectiveHandler]
 */
data class Directive(val original: String,
                     val directive: String?,
                     val extra: Any? = null)

/**
 *
 */
interface DirectiveHandler {
  val name: String
  fun parse(updater: ObjectUpdater, original: String, inputs: String?): Directive {
    if (!inputs.isNullOrBlank())
      throw IllegalArgumentException("inputs are not expected: $original")
    return Directive(original, name)
  }
}
