package glsl.plugin.utils

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil.findChildOfType
import com.intellij.psi.util.PsiTreeUtil.findChildrenOfType
import com.intellij.psi.util.findParentOfType
import glsl.data.ShaderType
import glsl.data.ShaderType.*
import glsl.plugin.language.GlslFile
import glsl.plugin.language.GlslFileType
import glsl.plugin.psi.named.GlslNamedElement
import glsl.plugin.psi.named.GlslNamedVariable
import glsl.plugin.psi.named.types.user.GlslNamedStructSpecifier
import glsl.plugin.utils.GlslUtils.getResourceFileAsString
import glsl.psi.interfaces.GlslDeclaration
import glsl.psi.interfaces.GlslFunctionDeclarator
import glsl.psi.interfaces.GlslSingleDeclaration
import glsl.psi.interfaces.GlslStructSpecifier
import java.util.*

object GlslBuiltinUtils {

    private var cacheProject: Project? = null
    private var vecStructs: Map<String, Map<String, GlslNamedVariable>>? = null
    private var builtinConstants: Map<String, GlslNamedVariable>? = null
    private var defaultShaderVariables: Map<String, GlslNamedVariable>? = null
    private var shaderVariables: EnumMap<ShaderType, Map<String, GlslNamedVariable>>? = null
    private var builtinFuncs: Map<String, List<GlslFunctionDeclarator>>? = null

    private fun currentProject(): Project {
        return ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }
            ?: ProjectManager.getInstance().defaultProject
    }

    private fun invalidateCachesIfNeeded() {
        val project = currentProject()
        if (cacheProject === project && !project.isDisposed) return
        cacheProject = project
        vecStructs = null
        builtinConstants = null
        defaultShaderVariables = null
        shaderVariables = null
        builtinFuncs = null
    }

    /**
     * Creates a map of the GLSL builtin functions with their name as a key and a list of their AST
     * as a value. Due to overloading, most functions have different signatures with the same name.
     * Therefore, we want to create a list of them and show all possible signatures to the user.
     */
    fun getBuiltinFuncs(): Map<String, List<GlslFunctionDeclarator>> {
        invalidateCachesIfNeeded()
        builtinFuncs?.let {
            return it
        }
        val funcs = mutableMapOf<String, MutableList<GlslFunctionDeclarator>>()
        val builtinFile = getBuiltinFile("glsl-builtin-functions")
        val declarations = findChildrenOfType(builtinFile, GlslDeclaration::class.java)
        for (declaration in declarations) {
            val funcName = findChildOfType(declaration, GlslFunctionDeclarator::class.java)?.name ?: continue
            val functionDeclarator = declaration.functionDeclarator ?: continue
            if (funcs.containsKey(funcName)) {
                funcs[funcName]?.add(functionDeclarator)
            } else {
                funcs[funcName] = mutableListOf(functionDeclarator)
            }
        }
        builtinFuncs = funcs
        return funcs
    }

    /**
     *
     */
    fun getVecStructs(): Map<String, Map<String, GlslNamedVariable>> {
        invalidateCachesIfNeeded()
        vecStructs?.let {
            return it
        }
        val builtinFile = getBuiltinFile("glsl-vector-structs")
        val structSpecifiers = findChildrenOfType(builtinFile, GlslStructSpecifier::class.java).toList()
        val lengthFunc = findChildOfType(builtinFile, GlslFunctionDeclarator::class.java)
        val vecStructsTemp = hashMapOf<String, MutableMap<String, GlslNamedVariable>>()
        for (structSpecifier in structSpecifiers) {
            val vecName = structSpecifier.name?.lowercase() ?: continue
            for (structDeclaration in structSpecifier.structDeclarationList) {
                val structDeclarator = structDeclaration.structDeclaratorList.first()
                val structDeclaratorName = structDeclarator.name ?: continue
                vecStructsTemp.putIfAbsent(vecName, hashMapOf())
                vecStructsTemp[vecName]!![structDeclaratorName] = structDeclarator
            }
            if (lengthFunc != null && lengthFunc.name == "length") {
                vecStructsTemp[vecName]?.set("length", lengthFunc)
            }
        }
        this.vecStructs = vecStructsTemp
        return vecStructsTemp
    }

    /**
     *
     */
    fun getBuiltinConstants(): Map<String, GlslNamedVariable> {
        invalidateCachesIfNeeded()
        builtinConstants?.let {
            return it
        }
        val builtinFile = getBuiltinFile("glsl-builtin-constants")
        val singleDeclarations = findChildrenOfType(builtinFile, GlslSingleDeclaration::class.java).toList()
        val constants = hashMapOf<String, GlslNamedVariable>()
        for (child in singleDeclarations) {
            val childName = child.name
            if (childName != null) {
                constants[childName] = child
            }
        }
        builtinConstants = constants
        return constants
    }

    fun getShaderVariables(fileExtension: String? = null): Map<String, GlslNamedVariable> {
        invalidateCachesIfNeeded()
        if (defaultShaderVariables == null || shaderVariables == null) {
            setShaderVariables()
        }
        val shaderType = getShaderType(fileExtension)
        if (shaderType == GLSL) {
            return defaultShaderVariables ?: emptyMap()
        }
        return shaderVariables?.get(shaderType) ?: emptyMap()
    }

    /**
     *
     */
    private fun setShaderVariables() {
        val shaderVariablesFile = getBuiltinFile("glsl-shader-variables")
        val structSpecifiers = findChildrenOfType(shaderVariablesFile, GlslStructSpecifier::class.java).filter { it.findParentOfType<GlslStructSpecifier>() == null }.toList()
        // Initializes map with ShaderType enum
        val shaderVariablesMap = EnumMap<ShaderType, Map<String, GlslNamedVariable>>(ShaderType::class.java)
        val allShaderVariables = hashMapOf<String, GlslNamedVariable>()
        for (structSpecifier in structSpecifiers) {
            val namedStruct = structSpecifier as GlslNamedStructSpecifier
            val structDeclarators = hashMapOf<String, GlslNamedVariable>()
            for (structMember in structSpecifier.getStructMembers()) {
                val memberName = structMember.name ?: continue
                structDeclarators[memberName] = structMember
                allShaderVariables[memberName] = structMember
            }
            val shaderType = getShaderType(namedStruct.name)
            shaderVariablesMap[shaderType] = structDeclarators
        }
        shaderVariables = shaderVariablesMap
        defaultShaderVariables = allShaderVariables
    }

    /**
     *
     */
    fun isBuiltin(name: String?, fileExtension: String? = null): Boolean {
        if (name == null) return false
        return isBuiltinFunction(name) || isBuiltinShaderVariable(name, fileExtension) || isBuiltinConstant(name)
    }

    /**
     *
     */
    fun isBuiltinFunction(name: String?): Boolean {
        if (name == null) return false
        return name in getBuiltinFuncs().keys
    }

    /**
     *
     */
    fun isBuiltinConstant(name: String): Boolean {
        return name in getBuiltinConstants().keys
    }

    /**
     *
     */
    fun isBuiltinShaderVariable(variable: String, fileExtension: String?): Boolean {
        if (fileExtension == null) return false
        invalidateCachesIfNeeded()
        if (defaultShaderVariables == null || shaderVariables == null) {
            setShaderVariables()
        }
        fun isAinB(a: String, b: Map<String, GlslNamedElement>?): Boolean = if (b != null) a in b.keys else false
        return when (val shaderType = getShaderType(fileExtension)) {
            VERT -> isAinB(variable, shaderVariables?.get(shaderType))
            GEOM -> isAinB(variable, shaderVariables?.get(shaderType))
            FRAG -> isAinB(variable, shaderVariables?.get(shaderType))
            TESC -> isAinB(variable, shaderVariables?.get(shaderType))
            TESE -> isAinB(variable, shaderVariables?.get(shaderType))
            COMP -> isAinB(variable, shaderVariables?.get(shaderType))
            GLSL -> isAinB(variable, defaultShaderVariables)
        }
    }

    /**
     *
     */
    private fun getBuiltinFile(fileName: String): GlslFile? {
        val project = currentProject()
        val funcsString = getResourceFileAsString("builtin-objects/$fileName.glsl") ?: return null
        val fileFactory = PsiFileFactory.getInstance(project)
        val glslFile = fileFactory.createFileFromText(fileName, GlslFileType(), funcsString) as? GlslFile
        glslFile?.viewProvider?.virtualFile?.isWritable = false
        glslFile?.viewProvider
        return glslFile
    }

    /**
     *
     */
    private fun getShaderType(fileExtension: String?): ShaderType {
        if (fileExtension == null) return GLSL
        return try {
            valueOf(fileExtension.lowercase())
        } catch (_: IllegalArgumentException) {
            GLSL
        }
    }
}
