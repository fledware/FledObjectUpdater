package fledware.objectupdater

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

open class ObjectUpdaterBuilder {
  companion object {
    val defaultObjectMapper = JsonMapper.builder()
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .build()
        .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
        .registerKotlinModule()
  }

  var mapper: ObjectMapper = defaultObjectMapper
  var selectDefault: SelectDirective = DefaultSelectDirective()
  val selects = mutableMapOf<String, SelectDirective>()
  val predicates = mutableMapOf<String, PredicateDirective>()
  val operations = mutableMapOf<String, OperationDirective>()

  open fun withDefaultSelectDirective(select: SelectDirective) =
      this.also { selectDefault = select }

  open fun withSelect(select: SelectDirective) =
      this.also { selects[select.name] = select }

  open fun withPredicate(predicate: PredicateDirective, canNegate: Boolean = true) =
      this.also {
        predicates[predicate.name] = predicate
        if (canNegate)
          predicates["~${predicate.name}"] = NegationPredicateDirective(predicate)
      }

  open fun withOperation(operation: OperationDirective) =
      this.also { operations[operation.name] = operation }

  open fun withMapper(mapper: ObjectMapper) =
      this.also { this.mapper = mapper }

  open fun build() = ObjectUpdater(mapper, selectDefault, selects, predicates, operations)
}
