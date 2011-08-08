package com.redhat.ceylon.compiler.codegen;

import static com.sun.tools.javac.code.Flags.FINAL;

import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AttributeDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Expression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ForIterator;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.KeyValueIterator;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ValueIterator;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Variable;
import com.redhat.ceylon.compiler.util.Util;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

/**
 * This transformer deals with statements only
 */
public class StatementTransformer extends AbstractTransformer {

    // Used to hold the name of the variable associated with the fail-block if the innermost for-loop
    // Is null if we're currently in a while-loop or not in any loop at all
    private Name currentForFailVariable = null;
    
    public static StatementTransformer getInstance(Context context) {
        StatementTransformer trans = context.get(StatementTransformer.class);
        if (trans == null) {
            trans = new StatementTransformer(context);
            context.put(StatementTransformer.class, trans);
        }
        return trans;
    }

    private StatementTransformer(Context context) {
        super(context);
    }

    public JCBlock transform(Tree.Block block) {
        return block == null ? null : at(block).Block(0, transformStmts(block.getStatements()));
    }

    List<JCStatement> transformStmts(java.util.List<Tree.Statement> list) {
        CeylonVisitor v = new CeylonVisitor(gen());

        for (Tree.Statement stmt : list)
            stmt.visit(v);

        return (List<JCStatement>) v.getResult().toList();
    }

    List<JCStatement> transform(Tree.IfStatement stmt) {
        Tree.Block thenPart = stmt.getIfClause().getBlock();
        Tree.Block elsePart = stmt.getElseClause() != null ? stmt.getElseClause().getBlock() : null;
        return transformCondition(stmt.getIfClause().getCondition(), JCTree.IF, thenPart, elsePart);
    }

    List<JCStatement> transform(Tree.WhileStatement stmt) {
        Name tempForFailVariable = currentForFailVariable;
        currentForFailVariable = null;
        
        Tree.Block thenPart = stmt.getWhileClause().getBlock();
        List<JCStatement> res = transformCondition(stmt.getWhileClause().getCondition(), JCTree.WHILELOOP, thenPart, null);
        
        currentForFailVariable = tempForFailVariable;
        
        return res;
    }

// FIXME Seems this really has gone the way of the Dodo, maybe remove it forgood?
//    List<JCStatement> transform(Tree.DoWhileStatement stmt) {
//        Name tempForFailVariable = currentForFailVariable;
//        currentForFailVariable = null;
//        
//        Tree.Block thenPart = stmt.getDoClause().getBlock();
//        List<JCStatement> res = transformCondition(stmt.getDoClause().getCondition(), JCTree.DOLOOP, thenPart, null);
//        
//        currentForFailVariable = tempForFailVariable;
//        
//        return res;
//    }

    private List<JCStatement> transformCondition(Tree.Condition cond, int tag, Tree.Block thenPart, Tree.Block elsePart) {
        JCExpression test;
        JCVariableDecl decl = null;
        JCBlock thenBlock = null;
        JCBlock elseBlock = null;
        if (cond instanceof Tree.ExistsCondition) {
            Tree.ExistsCondition exists = (Tree.ExistsCondition) cond;

            JCExpression expr = expressionGen().transformExpression(exists.getVariable().getSpecifierExpression().getExpression());

            test = at(cond).Binary(JCTree.NE, expr, make().Literal(TypeTags.BOT, null));
        } else if ((cond instanceof Tree.IsCondition) || (cond instanceof Tree.NonemptyCondition)) {
            String name;
            ProducedType toType;
            Expression specifierExpr;
            if (cond instanceof Tree.IsCondition) {
                Tree.IsCondition isdecl = (Tree.IsCondition) cond;
                name = isdecl.getVariable().getIdentifier().getText();
                toType = isdecl.getType().getTypeModel();
                specifierExpr = isdecl.getVariable().getSpecifierExpression().getExpression();
            } else {
                Tree.NonemptyCondition nonempty = (Tree.NonemptyCondition) cond;
                name = nonempty.getVariable().getIdentifier().getText();
                toType = nonempty.getVariable().getType().getTypeModel();
                specifierExpr = nonempty.getVariable().getSpecifierExpression().getExpression();
            }
            
            JCExpression toTypeExpr = makeJavaType(toType);

            // Want raw type for instanceof since it can't be used with generic types
            JCExpression rawToTypeExpr = makeJavaType(toType, CeylonTransformer.WANT_RAW_TYPE);

            Name tmpVarName = names().fromString(aliasName(name));
            Name origVarName = names().fromString(name);
            Name substVarName = names().fromString(aliasName(name));

            JCExpression expr = expressionGen().transformExpression(specifierExpr);
            ProducedType tmpVarType = specifierExpr.getTypeModel();

            // Temporary variable holding the result of the expression/variable to test
            decl = at(cond).VarDef(make().Modifiers(FINAL), tmpVarName, makeJavaType(tmpVarType), expr);
            // Substitute variable with the correct type to use in the rest of the code block
            JCVariableDecl decl2 = at(cond).VarDef(make().Modifiers(FINAL), substVarName, toTypeExpr, at(cond).TypeCast(toTypeExpr, at(cond).Ident(tmpVarName)));
            
            // Prepare for variable substitution in the following code block
            String prevSubst = addVariableSubst(origVarName.toString(), substVarName.toString());
            
            thenBlock = transform(thenPart);
            thenBlock = at(cond).Block(0, (List.<JCStatement> of(decl2)).appendList(thenBlock.getStatements()));
            
            // Deactivate the above variable substitution
            removeVariableSubst(origVarName.toString(), prevSubst);
            
            test = at(cond).TypeTest(make().Ident(decl.name), rawToTypeExpr);
        } else if (cond instanceof Tree.BooleanCondition) {
            Tree.BooleanCondition booleanCondition = (Tree.BooleanCondition) cond;
            test = makeBooleanTest(expressionGen().transformExpression(booleanCondition.getExpression()), true);
        } else {
            throw new RuntimeException("Not implemented: " + cond.getNodeType());
        }
        
        at(cond);
        // Convert the code blocks (if not already done so above)
        if (thenPart != null && thenBlock == null) {
            thenBlock = transform(thenPart);
        }
        if (elsePart != null && elseBlock == null) {
            elseBlock = transform(elsePart);
        }
        
        JCStatement cond1;
        switch (tag) {
        case JCTree.IF:
            cond1 = make().If(test, thenBlock, elseBlock);
            break;
        case JCTree.WHILELOOP:
            cond1 = make().WhileLoop(test, thenBlock);
            break;
        case JCTree.DOLOOP:
            cond1 = make().DoLoop(thenBlock, test);
            break;
        default:
            throw new RuntimeException();
        }
        
        if (decl != null) {
            return List.<JCStatement> of(decl, cond1);
        } else {
            return List.<JCStatement> of(cond1);
        }
    }

    List<JCStatement> transform(Tree.ForStatement stmt) {
        Name tempForFailVariable = currentForFailVariable;
        
        at(stmt);
        List<JCStatement> outer = List.<JCStatement> nil();
        if (stmt.getElseClause() != null) {
            // boolean $doforelse$X = true;
            JCVariableDecl failtest_decl = make().VarDef(make().Modifiers(0), names().fromString(aliasName("doforelse")), make().TypeIdent(TypeTags.BOOLEAN), make().Literal(TypeTags.BOOLEAN, 1));
            outer = outer.append(failtest_decl);
            
            currentForFailVariable = failtest_decl.getName();
        } else {
            currentForFailVariable = null;
        }

        ForIterator iterDecl = stmt.getForClause().getForIterator();
        Variable variable;
        Variable variable2;
        if (iterDecl instanceof ValueIterator) {
            variable = ((ValueIterator) iterDecl).getVariable();
            variable2 = null;
        } else if (iterDecl instanceof KeyValueIterator) {
            variable = ((KeyValueIterator) iterDecl).getKeyVariable();
            variable2 = ((KeyValueIterator) iterDecl).getValueVariable();
        } else {
            throw new RuntimeException("Unknown ForIterator");
        }
        
        String loop_var_name = variable.getIdentifier().getText();
        ProducedType sequenceElementType = typeFact().getIteratedType(iterDecl.getSpecifierExpression().getExpression().getTypeModel());
        ProducedType item_type = typeFact().makeIteratorType(sequenceElementType );
        JCExpression iter_type_expr = makeJavaType(item_type , CeylonTransformer.TYPE_PARAM);
        JCExpression item_type_expr = makeJavaType(actualType(variable));
        List<JCAnnotation> annots = makeJavaTypeAnnotations(variable.getDeclarationModel(), actualType(variable));

        // ceylon.language.Iterator<T> $V$iter$X = ITERABLE.iterator();
        JCExpression containment = expressionGen().transformExpression(iterDecl.getSpecifierExpression().getExpression());
        JCVariableDecl iter_decl = at(stmt).VarDef(make().Modifiers(0), names().fromString(aliasName(loop_var_name + "$iter")), iter_type_expr, at(stmt).Apply(null, makeSelect(containment, "iterator"), List.<JCExpression> nil()));
        JCIdent iter_id = at(stmt).Ident(iter_decl.getName());
        
        // final U n = $V$iter$X.getHead();
        // or
        // final U n = $V$iter$X.getHead().getKey();
        JCExpression iter_head = at(stmt).Apply(null, makeSelect(iter_id, Util.getGetterName("head")), List.<JCExpression> nil());
        JCExpression loop_var_init;
        if (variable2 == null) {
            loop_var_init = iter_head;
        } else {
            loop_var_init = at(stmt).Apply(null, makeSelect(iter_head, Util.getGetterName("key")), List.<JCExpression> nil());
        }
        JCVariableDecl item_decl = at(stmt).VarDef(make().Modifiers(FINAL, annots), names().fromString(loop_var_name), item_type_expr, loop_var_init );
        List<JCStatement> for_loop = List.<JCStatement> of(item_decl);

        if (variable2 != null) {
            // final V n = $V$iter$X.getHead().getElement();
            JCExpression loop_var_init2 = at(stmt).Apply(null, makeSelect(at(stmt).Apply(null, makeSelect(iter_id, Util.getGetterName("head")), List.<JCExpression> nil()), Util.getGetterName("element")), List.<JCExpression> nil());
            String loop_var_name2 = variable2.getIdentifier().getText();
            JCExpression item_type_expr2 = makeJavaType(actualType(variable2));
            JCVariableDecl item_decl2 = at(stmt).VarDef(make().Modifiers(FINAL, annots), names().fromString(loop_var_name2), item_type_expr2, loop_var_init2);
            for_loop = for_loop.append(item_decl2);
        }

        // The user-supplied contents of the loop
        for_loop = for_loop.appendList(transformStmts(stmt.getForClause().getBlock().getStatements()));

        // $V$iter$X = $V$iter$X.getTail();
        JCExpression step = at(stmt).Assign(iter_id, at(stmt).Apply(null, makeSelect(iter_id, Util.getGetterName("tail")), List.<JCExpression> nil()));
        
        // $i$iter$1.getHead() != null;
        JCExpression cond = at(stmt).Binary(JCTree.NE, iter_head, make().Literal(TypeTags.BOT, null));
        
        // for (.ceylon.language.Iterator<T> $V$iter$X = ITERABLE.iterator(); $V$iter$X.getHead() != null; $V$iter$X = $V$iter$X.getTail()) {
        outer = outer.append(at(stmt).ForLoop(
            List.<JCStatement>of(iter_decl), 
	        cond, 
	        List.<JCExpressionStatement>of(at(stmt).Exec(step)), 
	        at(stmt).Block(0, for_loop)));

        if (stmt.getElseClause() != null) {
            // The user-supplied contents of fail block
            List<JCStatement> failblock = transformStmts(stmt.getElseClause().getBlock().getStatements());
            
            // if ($doforelse$X) ...
            JCIdent failtest_id = at(stmt).Ident(currentForFailVariable);
            outer = outer.append(at(stmt).If(failtest_id, at(stmt).Block(0, failblock), null));
        }
        currentForFailVariable = tempForFailVariable;

        return outer;
    }

    // FIXME There is a similar implementation in ClassGen!
    public JCStatement transform(AttributeDeclaration decl) {
        Name atrrName = names().fromString(decl.getIdentifier().getText());
        
        JCExpression initialValue = null;
        if (decl.getSpecifierOrInitializerExpression() != null) {
            initialValue = expressionGen().transformExpression(decl.getSpecifierOrInitializerExpression().getExpression());
        }

        ProducedType t = actualType(decl);
        JCExpression type = makeJavaType(t);
        List<JCAnnotation> annots = makeJavaTypeAnnotations(decl.getDeclarationModel(), t);

        int modifiers = transformLocalFieldDeclFlags(decl);
        return at(decl).VarDef(at(decl).Modifiers(modifiers, annots), atrrName, type, initialValue);
    }
    
    List<JCStatement> transform(Tree.Break stmt) {
        // break;
        JCStatement brk = at(stmt).Break(null);
        
        if (currentForFailVariable != null) {
            JCIdent failtest_id = at(stmt).Ident(currentForFailVariable);
            List<JCStatement> list = List.<JCStatement> of(at(stmt).Exec(at(stmt).Assign(failtest_id, make().Literal(TypeTags.BOOLEAN, 0))));
            list = list.append(brk);
            return list;
        } else {
            return List.<JCStatement> of(brk);
        }
    }

    JCStatement transform(Tree.Return ret) {
        Tree.Expression expr = ret.getExpression();
        JCExpression returnExpr = expr != null ? expressionGen().transformExpression(expr) : null;
        return at(ret).Return(returnExpr);
    }

    private JCIdent transform(Tree.Identifier identifier) {
        return at(identifier).Ident(names().fromString(substitute(identifier.getText())));
    }

    JCStatement transform(Tree.SpecifierStatement op) {
        return at(op).Exec(expressionGen().transformAssignment(op, op.getBaseMemberExpression(), op.getSpecifierExpression().getExpression()));
    }

    private int transformLocalFieldDeclFlags(Tree.AttributeDeclaration cdecl) {
        int result = 0;

        result |= cdecl.getDeclarationModel().isVariable() ? 0 : FINAL;

        return result;
    }
}
