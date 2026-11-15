import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import glsl.psi.interfaces.GlslFunctionDeclarator
import glsl.psi.interfaces.GlslSingleDeclaration

class GlslUpstreamRegressionTest : BasePlatformTestCase() {

    fun testNestedIncludeResolvesRelativeToEachIncludedFile() {
        myFixture.addFileToProject("common/value.glsl", "float sharedValue;")
        myFixture.addFileToProject("lib/wrapper.glsl", "#include \"../common/value.glsl\"\n")
        configureProjectFile("shaders/main.glsl", "#include \"../lib/wrapper.glsl\"\nvoid main() { float x = shared<caret>Value; }")

        val resolved = myFixture.getReferenceAtCaretPosition()?.resolve()
        assertTrue("nested include must resolve the declaration", resolved is GlslSingleDeclaration)
        assertTrue(resolved!!.containingFile.virtualFile.path.endsWith("/common/value.glsl"))
    }

    fun testDiamondIncludesDoNotStopLookingInTheSecondBranch() {
        myFixture.addFileToProject("leaf.glsl", "float leaf;")
        myFixture.addFileToProject("common.glsl", "#include \"leaf.glsl\"\nfloat commonValue;")
        myFixture.addFileToProject("left.glsl", "#include \"common.glsl\"\n")
        myFixture.addFileToProject("right.glsl", "#include \"common.glsl\"\nfloat rightValue;")
        myFixture.addFileToProject("bundle.glsl", "#include \"left.glsl\"\n#include \"right.glsl\"\n")
        configureProjectFile("main.glsl", "#include \"bundle.glsl\"\nvoid main() { float x = right<caret>Value; }")

        val resolved = myFixture.getReferenceAtCaretPosition()?.resolve() as? GlslSingleDeclaration
        assertNotNull("a repeated include must not abort sibling lookup", resolved)
        assertEquals("rightValue", resolved!!.name)
    }

    fun testStructMembersCompleteThroughNestedIncludes() {
        myFixture.addFileToProject("common/types.glsl", "struct Shared { vec3 direction; float depth; };")
        myFixture.addFileToProject("lib/types.glsl", "#include \"../common/types.glsl\"\n")
        configureProjectFile("shaders/main.glsl", "#include \"../lib/types.glsl\"\nvoid main() { Shared s; s.d<caret>; }")

        val candidates = complete()
        assertTrue("cross-file struct must offer direction: $candidates", candidates.contains("direction"))
        assertTrue("cross-file struct must offer depth: $candidates", candidates.contains("depth"))
    }

    fun testIncludeCompletionFiltersTheFilenamePrefix() {
        addIncludeCandidates()
        configureProjectFile("shaders/main.glsl", "#include \"../lib/ta<caret>\"\n")

        assertSameElements(complete(), listOf("../lib/target.glsl", "../lib/table.glsl"))
    }

    fun testIncludeCompletionInsertsOnePathAndPreservesQuotes() {
        addIncludeCandidates()
        configureProjectFile("shaders/main.glsl", "#include \"../lib/ta<caret>\"\n")
        myFixture.complete(CompletionType.BASIC)
        val target = myFixture.lookupElements!!.first { it.lookupString == "../lib/target.glsl" }
        myFixture.lookup.currentItem = target
        myFixture.finishLookup('\n')

        myFixture.checkResult("#include \"../lib/target.glsl\"\n")
        assertEquals("caret must remain inside the path", myFixture.editor.document.text.indexOf('"', 10), myFixture.editor.caretModel.offset)
    }

    fun testIncludeCompletionUsesOnlyThePrefixBeforeTheCaret() {
        addIncludeCandidates()
        configureProjectFile("shaders/main.glsl", "#include \"../lib/ta<caret>rget.glsl\"\n")

        assertSameElements(complete(), listOf("../lib/target.glsl", "../lib/table.glsl"))
    }

    fun testDotInIncludeCompletionDoesNotThrow() {
        myFixture.addFileToProject("lib/target.glsl", "float target;")
        configureProjectFile("main.glsl", "#include \".<caret>\"\n")
        assertNoThrowable { myFixture.complete(CompletionType.BASIC) }
    }

    fun testVersionNumberCompletion() {
        myFixture.configureByText("version.glsl", "#version 4<caret>\n")
        val candidates = complete()
        assertTrue("version completion must include 450: $candidates", candidates.contains("450"))
        assertTrue("version completion must include 460: $candidates", candidates.contains("460"))
    }

    fun testVersionProfileCompletion() {
        myFixture.configureByText("version.glsl", "#version 450 <caret>\n")
        assertSameElements(complete(), listOf("core", "compatibility", "es"))
    }

    fun testCustomFunctionCompletionInUnfinishedStatement() {
        myFixture.configureByText("functions.glsl", "float customValue() { return 1.0; }\nfloat customOther() { return 2.0; }\nvoid main() { float x = custom<caret>; }")
        val candidates = complete()
        assertTrue("customValue must be offered: $candidates", candidates.any { it.startsWith("customValue(") })
        assertTrue("customOther must be offered: $candidates", candidates.any { it.startsWith("customOther(") })
    }

    fun testParameterlessFunctionReferenceResolves() {
        myFixture.configureByText("functions.glsl", "float source() { return 1.0; }\nvoid main() { float x = sour<caret>ce(); }")
        val resolved = myFixture.getReferenceAtCaretPosition()?.resolve() as? GlslFunctionDeclarator
        assertNotNull("parameterless call must resolve to its declaration", resolved)
        assertEquals("source", resolved!!.name)
    }

    fun testVectorStructMemberIndexHasScalarType() {
        myFixture.configureByText("types.glsl", "struct S { vec3 v; };\nvoid main() { S s; float result = s.v[0]; }")
        val result = PsiTreeUtil.findChildrenOfType(myFixture.file, GlslSingleDeclaration::class.java).first { it.name == "result" }
        assertEquals("float", result.exprNoAssignmentList.single().getExprType()?.text)
    }

    private fun addIncludeCandidates() {
        for (name in listOf("target", "table", "other")) {
            myFixture.addFileToProject("lib/$name.glsl", "float $name;")
        }
    }

    private fun configureProjectFile(path: String, text: String) {
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject(path, text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        if (caret >= 0) myFixture.editor.caretModel.moveToOffset(caret)
    }

    private fun complete(): List<String> {
        myFixture.complete(CompletionType.BASIC)
        return myFixture.lookupElementStrings.orEmpty()
    }
}
