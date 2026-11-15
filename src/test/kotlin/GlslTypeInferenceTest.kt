import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import glsl.GlslTypes.*
import glsl.plugin.inspections.GlslInspectionIncompatibleType
import glsl.psi.interfaces.GlslSingleDeclaration

class GlslTypeInferenceTest : BasePlatformTestCase() {

    fun testVectorArrayElement() {
        checkType("vec2 corners[4];", "corners[gl_VertexID]", VEC2)
    }

    fun testBitmaskComparisons() {
        checkType("uint Hint;", "((Hint & (1u << 31)) != 0u) && ((Hint & (1u << 30)) == 0u)", BOOL)
    }

    fun testInitializedVectorArray() {
        checkType("const vec2 corners[4] = vec2[4](vec2(0.0), vec2(1.0), vec2(2.0), vec2(3.0));",
            "corners[gl_VertexID]", VEC2)
    }

    fun testArrayOnTypeSpecifier() {
        checkType("vec2[4] corners;", "corners[0]", VEC2)
    }

    fun testMultidimensionalArray() {
        checkType("vec2[2] corners[4];", "corners[0][1]", VEC2)
    }

    fun testParenthesizedArray() {
        checkType("vec2 corners[4];", "(corners)[0]", VEC2)
    }

    fun testParenthesizedSubarray() {
        checkType("vec2 corners[2][4];", "(corners[0])[1]", VEC2)
    }

    fun testArrayElementComponent() {
        checkType("vec2 corners[4];", "corners[0][1]", FLOAT)
    }

    fun testScalarArray() {
        checkType("uint values[4];", "values[0]", UINT)
    }

    fun testCommaDeclaredArray() {
        checkType("vec2 first, corners /* array */ [4];", "corners[0]", VEC2)
    }

    fun testCommaDeclaredNonArray() {
        checkType("vec2 corners[4], vector;", "vector[0]", FLOAT)
    }

    fun testUnsizedArrayParameter() {
        checkShader("void readCorner(vec2 corners[]) { vec2 result = corners[0]; }", VEC2)
    }

    fun testArrayFunctionResult() {
        checkType("vec2[2] getCorners(int index) { return vec2[2](vec2(0.0), vec2(1.0)); }", "getCorners(0)[0]", VEC2)
    }

    fun testStructArrayMember() {
        checkType("struct Corners { vec2 values[4]; }; Corners corners;", "corners.values[0]", VEC2)
    }

    fun testArrayConstructor() {
        checkType("", "vec2[2](vec2(0.0), vec2(1.0))[0]", VEC2)
    }

    fun testVectorConstructorComponent() {
        checkType("", "vec2(0.0)[0]", FLOAT)
    }

    fun testMatrixColumn() {
        checkType("mat2x3 matrix;", "matrix[0]", VEC3)
    }

    fun testDoubleMatrixColumn() {
        checkType("dmat3x2 matrix;", "matrix[0]", DVEC2)
    }

    fun testMatrixComponent() {
        checkType("mat2x3 matrix;", "matrix[0][1]", FLOAT)
    }

    fun testMatrixArrayColumn() {
        checkType("mat2x3 matrices[4];", "matrices[0][1]", VEC3)
    }

    fun testEquality() {
        checkType("uint value;", "value == 0u", BOOL)
    }

    fun testRelationalComparison() {
        checkType("float value;", "value < 0.0", BOOL)
    }

    fun testLogicalOr() {
        checkType("uint value;", "(value == 0u) || (value != 1u)", BOOL)
    }

    fun testLogicalXor() {
        checkType("uint value;", "(value == 0u) ^^ (value != 1u)", BOOL)
    }

    fun testLogicalNegation() {
        checkType("uint value;", "!(value == 0u)", BOOL)
    }

    fun testIncompatibleTypesRemainReported() {
        myFixture.enableInspections(GlslInspectionIncompatibleType())
        val message = "Incompatible types in initialization (and no available implicit conversion)."
        myFixture.configureByText("types.vert", """
            #version 460
            vec2 corners[4];
            uint value;
            void main() {
                float component = <error descr="$message">corners[0]</error>;
                uint comparison = <error descr="$message">value == 0u</error>;
                vec2 vector = <error descr="$message">corners[0][1]</error>;
            }
        """.trimIndent())
        myFixture.checkHighlighting(false, false, false)
    }

    private fun checkType(declarations: String, expression: String, expected: IElementType) {
        checkShader("""
            #version 460
            $declarations
            void main() {
                $expected result = $expression;
            }
        """.trimIndent(), expected)
    }

    private fun checkShader(source: String, expected: IElementType) {
        myFixture.enableInspections(GlslInspectionIncompatibleType())
        myFixture.configureByText("types.vert", source)
        val declaration = PsiTreeUtil.findChildrenOfType(myFixture.file, GlslSingleDeclaration::class.java)
            .single { it.name == "result" }
        val expression = declaration.exprNoAssignmentList.single()
        assertEquals(expression.text, expected, expression.getExprType()?.typeAsToken())
        myFixture.checkHighlighting(false, false, false)
    }
}
