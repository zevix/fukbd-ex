package net.z33v.fukbd.core.profile

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import kotlin.test.Test
import kotlin.test.fail

/**
 * design.md A-4: the JSON Schema (documentation and oracle) and ProfileParser agree.
 * valid/ and invalid-semantic/ pass the schema; invalid-schema/ fails it. ProfileParser accepts
 * exactly the valid ones (checked in FixtureCorpusTest), so the only intended disagreement is the
 * semantic set, which the schema cannot express.
 */
class SchemaAgreementTest {
    private val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(Fixtures.text(Fixtures.schema))
    private val mapper = ObjectMapper()

    private fun schemaErrors(text: String) = schema.validate(mapper.readTree(text.removePrefix("﻿")))

    @Test
    fun schemaVerdictMatchesFixtureDirectory() {
        val wrong = mutableListOf<String>()
        for (dir in listOf("valid", "invalid-semantic", "invalid-schema")) {
            for (f in Fixtures.dir(dir)) {
                val errors = schemaErrors(Fixtures.text(f))
                val schemaOk = errors.isEmpty()
                if (schemaOk != (dir != "invalid-schema")) wrong += "$dir/${f.name}: schema says ${if (schemaOk) "valid" else "invalid $errors"}"
            }
        }
        for (f in Fixtures.exampleFiles) {
            val errors = schemaErrors(Fixtures.text(f))
            if (errors.isNotEmpty()) wrong += "example ${f.name}: $errors"
        }
        if (wrong.isNotEmpty()) fail(wrong.joinToString("\n"))
    }

    @Test
    fun parserAndSchemaAgreeOutsideTheSemanticSet() {
        val wrong = mutableListOf<String>()
        for (dir in listOf("valid", "invalid-schema")) {
            for (f in Fixtures.dir(dir)) {
                val text = Fixtures.text(f)
                val schemaOk = schemaErrors(text).isEmpty()
                val parserOk = ProfileParser.parse(text) is ParseResult.Ok
                if (schemaOk != parserOk) wrong += "$dir/${f.name}: schema ${if (schemaOk) "accepts" else "rejects"}, parser ${if (parserOk) "accepts" else "rejects"}"
            }
        }
        if (wrong.isNotEmpty()) fail(wrong.joinToString("\n"))
    }
}
