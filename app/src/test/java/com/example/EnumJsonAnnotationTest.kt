package com.example

import com.example.data.model.ActiveWorkout
import com.example.data.model.AppSettings
import com.example.data.model.CompletedWorkout
import com.example.data.model.Routine
import com.example.data.model.TrainingPlan
import com.example.domain.RepDayBackupPayload
import com.squareup.moshi.JsonClass
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * Release builds are shrunk by R8. Moshi reads enum fields by name, so every enum it saves must be
 * annotated with @JsonClass(generateAdapter = false), or R8 renames the fields and the app crashes
 * on launch. Debug builds aren't shrunk, so this only shows up in the Play Store build.
 *
 * This walks every class Moshi saves (starting from the top-level saved types) and checks each
 * enum it finds, so a new enum added to a saved model is caught too.
 */
class EnumJsonAnnotationTest {

    private val savedRoots: List<Class<*>> = listOf(
        AppSettings::class.java,
        ActiveWorkout::class.java,
        Routine::class.java,
        TrainingPlan::class.java,
        CompletedWorkout::class.java,
        RepDayBackupPayload::class.java
    )

    private fun collect(type: Type, seen: MutableSet<Class<*>>, enums: MutableSet<Class<*>>) {
        when (type) {
            is ParameterizedType -> {
                collect(type.rawType, seen, enums)
                type.actualTypeArguments.forEach { collect(it, seen, enums) }
            }
            is WildcardType -> type.upperBounds.forEach { collect(it, seen, enums) }
            is Class<*> -> {
                if (!seen.add(type)) return
                if (type.isEnum) {
                    enums.add(type)
                } else if (type.isAnnotationPresent(JsonClass::class.java)) {
                    type.declaredFields
                        .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
                        .forEach { collect(it.genericType, seen, enums) }
                }
            }
        }
    }

    @Test
    fun everyEnumSavedByMoshi_isAnnotatedSoReleaseBuildsDontCrash() {
        val seen = HashSet<Class<*>>()
        val enums = LinkedHashSet<Class<*>>()
        savedRoots.forEach { collect(it, seen, enums) }

        // Sanity check that the walk actually reached the models.
        assertTrue("Expected to find saved enums, found none", enums.isNotEmpty())

        val missing = enums.filter { enumClass ->
            val annotation = enumClass.getAnnotation(JsonClass::class.java)
            annotation == null || annotation.generateAdapter
        }
        if (missing.isNotEmpty()) {
            fail(
                "Add @JsonClass(generateAdapter = false) to: " +
                    missing.joinToString { it.simpleName } +
                    ". Without it the release (Play Store) build crashes on launch."
            )
        }
    }
}
