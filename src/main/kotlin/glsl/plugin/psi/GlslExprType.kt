package glsl.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import glsl.GlslTypes.*
import glsl.plugin.psi.named.GlslNamedElement
import glsl.plugin.psi.named.GlslNamedType
import glsl.plugin.psi.named.types.builtins.GlslMatrix
import glsl.plugin.utils.GlslUtils.createScalarTypeElement
import glsl.plugin.utils.GlslUtils.createVectorTypeElement
import glsl.plugin.utils.GlslUtils.getType
import glsl.psi.interfaces.*

interface GlslExprType : PsiElement {
    fun getExprType(): GlslNamedType?
}

abstract class GlslExprTypeImpl(node: ASTNode) : ASTWrapperPsiElement(node), GlslExprType {

    override fun getExprType(): GlslNamedType? {
        return getExpressionType()?.type
    }

    private data class ExpressionType(val type: GlslNamedType, val arrayDimensions: Int = 0)

    private fun getExpressionType(expr: GlslExprType?): ExpressionType? {
        return (expr as? GlslExprTypeImpl)?.getExpressionType()
    }

    private fun getExpressionType(): ExpressionType? {
        val expr = node.psi ?: return null
        return when (expr) {
            is GlslUnaryExpr -> getPostfixType(expr.postfixExpr)
            is GlslConditionalExpr -> getExpressionType(expr.exprNoAssignmentList.getOrNull(1))
            is GlslRelationalExpr,
            is GlslEqualityExpr,
            is GlslLogicalAndExpr,
            is GlslLogicalXorExpr,
            is GlslLogicalOrExpr -> ExpressionType(createScalarTypeElement(BOOL, "bool"))
            is GlslAddExpr,
            is GlslMulExpr,
            is GlslAndExpr,
            is GlslShiftExpr,
            is GlslExclusiveOrExpr,
            is GlslInclusiveOrExpr -> getBinaryExprType(expr)?.let { ExpressionType(it) }
            else -> null
        }
    }

    /**
     *
     */
    private fun getPostfixType(postfixExpr: GlslPostfixExpr?): ExpressionType? {
        return when (postfixExpr) {
            is GlslPrimaryExpr -> getPrimaryExprType(postfixExpr)
            is GlslFunctionCall -> getDeclaredType(postfixExpr.variableIdentifier?.resolveReference())
            is GlslConstructorCall -> getType(postfixExpr.typeSpecifier)?.let {
                ExpressionType(it, getArrayDimensions(postfixExpr.typeSpecifier.arraySpecifier))
            }
            is GlslPostfixArrayIndex -> getArrayIndexType(postfixExpr)
            is GlslPostfixInc -> getPostfixType(postfixExpr.postfixExpr)
            is GlslPostfixFieldSelection -> getPostfixSelectionType(postfixExpr)
            else -> null
        }
    }

    /**
     *
     */
    private fun getArrayIndexType(arrayIndex: GlslPostfixArrayIndex): ExpressionType? {
        var result = getPostfixType(arrayIndex.postfixExpr) ?: return null
        repeat(arrayIndex.exprList.size) {
            result = getIndexedType(result) ?: return null
        }
        return result
    }

    private fun getIndexedType(expressionType: ExpressionType): ExpressionType? {
        if (expressionType.arrayDimensions > 0) {
            return expressionType.copy(arrayDimensions = expressionType.arrayDimensions - 1)
        }
        val type = expressionType.type
        if (type is GlslMatrix) {
            val isDouble = when {
                type.name?.startsWith("dmat") == true -> true
                type.name?.startsWith("mat") == true -> false
                else -> return null
            }
            val token = when (type.getDimension()) {
                2 -> if (isDouble) DVEC2 else VEC2
                3 -> if (isDouble) DVEC3 else VEC3
                4 -> if (isDouble) DVEC4 else VEC4
                else -> return null
            }
            return ExpressionType(createVectorTypeElement(token, token.toString()))
        }
        return type.getScalarType()?.let { ExpressionType(it) }
    }

    private fun getArrayDimensions(arraySpecifier: GlslArraySpecifier?): Int {
        return arraySpecifier?.node?.getChildren(TokenSet.create(LEFT_BRACKET))?.size ?: 0
    }

    private fun getDeclaredType(declaration: GlslNamedElement?): ExpressionType? {
        val type = declaration?.getAssociatedType() ?: return null
        val typeSpecifier = when (declaration) {
            is GlslSingleDeclaration -> declaration.typeSpecifier
            is GlslParameterDeclarator -> declaration.typeSpecifier
            is GlslFunctionDeclarator -> declaration.typeSpecifier
            is GlslStructDeclarator -> (declaration.parent as? GlslStructDeclaration)?.typeSpecifier
            is GlslInitDeclaratorVariable -> (declaration.parent as? GlslDeclaration)?.singleDeclaration?.typeSpecifier
            else -> null
        }
        val arraySpecifier = when (declaration) {
            is GlslInitDeclaratorVariable -> PsiTreeUtil.skipWhitespacesAndCommentsForward(declaration) as? GlslArraySpecifier
            else -> PsiTreeUtil.getChildOfType(declaration, GlslArraySpecifier::class.java)
        }
        return ExpressionType(type, getArrayDimensions(typeSpecifier?.arraySpecifier) + getArrayDimensions(arraySpecifier))
    }

    /**
     *
     */
    private fun getPrimaryExprType(primaryExpr: GlslPrimaryExpr): ExpressionType? {
        if (primaryExpr.variableIdentifier != null) {
            return getDeclaredType(primaryExpr.variableIdentifier?.resolveReference())
        }

        val expr = primaryExpr.expr
        if (expr != null) {
            return getExpressionType(expr)
        }
        if (primaryExpr.intconstant != null) {
            return ExpressionType(createScalarTypeElement(INT, "int"))
        } else if (primaryExpr.uintconstant != null) {
            return ExpressionType(createScalarTypeElement(UINT, "uint"))
        } else if (primaryExpr.boolconstant != null) {
            return ExpressionType(createScalarTypeElement(BOOL, "bool"))
        } else if (primaryExpr.floatconstant != null) {
            return ExpressionType(createScalarTypeElement(FLOAT, "float"))
        } else {
            return null
        }
    }


    /**
     *
     */
    private fun getBinaryExprType(expr: PsiElement): GlslNamedType? {
        val exprList = PsiTreeUtil.getChildrenOfTypeAsList(expr, GlslExpr::class.java)
        val leftExpr = exprList.first().getExprType() ?: return null
        val rightExpr = exprList.last().getExprType() ?: return null
        val operation = expr.firstChild.nextSibling.nextSibling.text ?: ""
        return leftExpr.getBinaryType(rightExpr, operation)
    }

    /**
     *
     */
    private fun getPostfixSelectionType(postfixExpr: GlslPostfixFieldSelection): ExpressionType? {
        val member = postfixExpr.postfixStructMemberList.lastOrNull() ?: return null
        val identifier = member.variableIdentifier ?: member.functionCall?.variableIdentifier
        val result = getDeclaredType(identifier?.resolveReference()) ?: return null
        return if (member.expr != null) getIndexedType(result) else result
    }
}
