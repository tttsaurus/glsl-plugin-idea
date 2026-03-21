import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil

class GlslMacroPlaceholderTest : BasePlatformTestCase() {

    fun testMacroPlaceholders() {
        val code = """
            #ifndef VEC3F_RECORD
                #define VEC3F_RECORD

                #ifndef VEC3F_RECORD_BINDING
                    #define VEC3F_RECORD_BINDING {vec3f_record_binding}
                #endif

                layout(std430, binding = VEC3F_RECORD_BINDING) buffer Vec3fRecordsBuffer
                {
                    vec4 vec3fRecords[];
                };
            #endif
        """.trimIndent()

        myFixture.configureByText("macro-placeholders.glsl", code)

        assertNotNull(myFixture.file.node)
        assertNull(PsiTreeUtil.findChildOfType(myFixture.file, PsiErrorElement::class.java))
    }
}
