/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package com.redhat.ceylon.compiler.java.codegen;

import static com.sun.tools.javac.code.Flags.FINAL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.antlr.runtime.Token;

import com.redhat.ceylon.ceylondoc.Util;
import com.redhat.ceylon.compiler.java.codegen.Naming.DeclNameFlag;
import com.redhat.ceylon.compiler.java.loader.CeylonModelLoader;
import com.redhat.ceylon.compiler.java.loader.TypeFactory;
import com.redhat.ceylon.compiler.java.tools.CeylonLog;
import com.redhat.ceylon.compiler.loader.AbstractModelLoader;
import com.redhat.ceylon.compiler.loader.ModelLoader.DeclarationType;
import com.redhat.ceylon.compiler.typechecker.model.Annotation;
import com.redhat.ceylon.compiler.typechecker.model.BottomType;
import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.ControlBlock;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.FunctionalParameter;
import com.redhat.ceylon.compiler.typechecker.model.Interface;
import com.redhat.ceylon.compiler.typechecker.model.IntersectionType;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.ModuleImport;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ProducedReference;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.ProducedTypedReference;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.Setter;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.UnionType;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Expression;
import com.sun.tools.javac.code.BoundKind;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.Factory;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.LetExpr;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Position;
import com.sun.tools.javac.util.Position.LineMap;

/**
 * Base class for all delegating transformers
 */
public abstract class AbstractTransformer implements Transformation {
    
    /**
     * M1 and M2 are 0.0 since they were not tagged at the time
     * M3 is 1.0 as the first version with binary version information
     * M3.1 is 2.0
     * M4 is 3.0
     */
    public static final int BINARY_MAJOR_VERSION = 3;
    public static final int BINARY_MINOR_VERSION = 0;
    
    private Context context;
    private TreeMaker make;
    private Names names;
    private Symtab syms;
    private AbstractModelLoader loader;
    private TypeFactory typeFact;
    protected Log log;
    final Naming naming;

    public AbstractTransformer(Context context) {
        this.context = context;
        make = TreeMaker.instance(context);
        names = Names.instance(context);
        syms = Symtab.instance(context);
        loader = CeylonModelLoader.instance(context);
        typeFact = TypeFactory.instance(context);
        log = CeylonLog.instance(context);
        naming = Naming.instance(context);
    }

    Context getContext() {
        return context;
    }

    @Override
    public TreeMaker make() {
        return make;
    }

    private static JavaPositionsRetriever javaPositionsRetriever = null;
    public static void trackNodePositions(JavaPositionsRetriever positionsRetriever) {
        javaPositionsRetriever = positionsRetriever;
    }
    
    @Override
    public Factory at(Node node) {
        if (node == null) {
            make.at(Position.NOPOS);
            
        }
        else {
            Token token = node.getToken();
            if (token != null) {
                int tokenStartPosition = getMap().getStartPosition(token.getLine()) + token.getCharPositionInLine();
                make().at(tokenStartPosition);
                if (javaPositionsRetriever != null) {
                    javaPositionsRetriever.addCeylonNode(tokenStartPosition, node);
                }
            }
        }
        return make();
    }

    @Override
    public Symtab syms() {
        return syms;
    }

    @Override
    public Names names() {
        return names;
    }

    @Override
    public AbstractModelLoader loader() {
        return loader;
    }
    
    @Override
    public TypeFactory typeFact() {
        return typeFact;
    }

    void setMap(LineMap map) {
        gen().setMap(map);
    }

    LineMap getMap() {
        return gen().getMap();
    }

    @Override
    public CeylonTransformer gen() {
        return CeylonTransformer.getInstance(context);
    }
    
    @Override
    public ExpressionTransformer expressionGen() {
        return ExpressionTransformer.getInstance(context);
    }
    
    @Override
    public StatementTransformer statementGen() {
        return StatementTransformer.getInstance(context);
    }
    
    @Override
    public ClassTransformer classGen() {
        return ClassTransformer.getInstance(context);
    }
    
    /** 
     * Makes an <strong>unquoted</strong> simple identifier
     * @param ident The identifier
     * @return The ident
     */
    JCExpression makeUnquotedIdent(String ident) {
        return naming.makeUnquotedIdent(ident);
    }

    /** 
     * Makes an <strong>quoted</strong> simple identifier
     * @param ident The identifier
     * @return The ident
     */
    JCIdent makeQuotedIdent(String ident) {
        // TODO Only 3 callers
        return naming.makeQuotedIdent(ident);
    }
    
    /** 
     * Makes a <strong>quoted</strong> qualified (compound) identifier from 
     * the given qualified name. Each part of the name will be 
     * quoted if it is a Java keyword.
     * @param qualifiedName The qualified name 
     */
    JCExpression makeQuotedQualIdentFromString(String qualifiedName) {
        return naming.makeQuotedQualIdentFromString(qualifiedName);
    }

    /** 
     * Makes an <strong>unquoted</strong> qualified (compound) identifier 
     * from the given qualified name components
     * @param expr A starting expression (may be null)
     * @param names The components of the name (may be null)
     * @see #makeQuotedQualIdentFromString(String)
     */
    JCExpression makeQualIdent(JCExpression expr, String name) {
        return naming.makeQualIdent(expr, name);
    }
    
    JCExpression makeQuotedQualIdent(JCExpression expr, String... names) {
        // TODO Remove this method: Only 1 caller 
        return naming.makeQuotedQualIdent(expr, names);
    }

    JCExpression makeQuotedFQIdent(String qualifiedName) {
        // TODO Remove this method??: Only 2 callers
        return naming.makeQuotedFQIdent(qualifiedName);
    }

    JCExpression makeIdent(Type type) {
        return naming.makeIdent(type);
    }

    /**
     * Makes a <strong>unquoted</strong> field access
     * @param s1 The base expression
     * @param s2 The field to access
     * @return The field access
     */
    JCFieldAccess makeSelect(JCExpression s1, String s2) {
        return naming.makeSelect(s1, s2);
    }

    /**
     * Makes a <strong>unquoted</strong> field access
     * @param s1 The base expression
     * @param s2 The field to access
     * @return The field access
     */
    JCFieldAccess makeSelect(String s1, String s2) {
        return naming.makeSelect(s1, s2);
    }

    JCLiteral makeNull() {
        return make().Literal(TypeTags.BOT, null);
    }
    
    JCExpression makeInteger(int i) {
        return make().Literal(Integer.valueOf(i));
    }
    
    JCExpression makeLong(long i) {
        return make().Literal(Long.valueOf(i));
    }
    
    JCExpression makeBoolean(boolean b) {
        JCExpression expr;
        if (b) {
            expr = make().Literal(TypeTags.BOOLEAN, Integer.valueOf(1));
        } else {
            expr = make().Literal(TypeTags.BOOLEAN, Integer.valueOf(0));
        }
        return expr;
    }
    
    // Creates a "foo foo = new foo();"
    JCTree.JCVariableDecl makeLocalIdentityInstance(String varName, String className, boolean isShared) {
        JCExpression name = makeQuotedIdent(className);
        
        JCExpression initValue = makeNewClass(className, false);
        List<JCAnnotation> annots = List.<JCAnnotation>nil();

        int modifiers = isShared ? 0 : FINAL;
        JCTree.JCVariableDecl var = make().VarDef(
                make().Modifiers(modifiers, annots), 
                names().fromString(varName), 
                name, 
                initValue);
        
        return var;
    }
    
    // Creates a "new foo();"
    JCTree.JCNewClass makeNewClass(String className, boolean fullyQualified) {
        JCExpression name = fullyQualified ? naming.makeQuotedFQIdent(className) : makeQuotedQualIdentFromString(className);
        return makeNewClass(name, List.<JCTree.JCExpression>nil());
    }
    
    /** Creates a "new foo();" */
    JCTree.JCNewClass makeSyntheticInstance(Declaration decl) {
        JCExpression clazz = naming.makeSyntheticClassname(decl);
        return makeNewClass(clazz, List.<JCTree.JCExpression>nil());
    }
    
    JCTree.JCNewClass makeNewClass(JCExpression clazz) {
        return makeNewClass(clazz, null);
    }
    
    // Creates a "new foo(arg1, arg2, ...);"
    JCTree.JCNewClass makeNewClass(JCExpression clazz, List<JCTree.JCExpression> args) {
        if (args == null) {
            args = List.<JCTree.JCExpression>nil();
        }
        return make().NewClass(null, null, clazz, args, null);
    }

    JCVariableDecl makeVar(String varName, JCExpression typeExpr, JCExpression valueExpr) {
        return make().VarDef(make().Modifiers(0), names().fromString(varName), typeExpr, valueExpr);
    }
    JCVariableDecl makeVar(Naming.SyntheticName varName, JCExpression typeExpr, JCExpression valueExpr) {
        return make().VarDef(make().Modifiers(0), varName.asName(), typeExpr, valueExpr);
    }
    
    // Creates a "( let var1=expr1,var2=expr2,...,varN=exprN in varN; )"
    // or a "( let var1=expr1,var2=expr2,...,varN=exprN,exprO in exprO; )"
    JCExpression makeLetExpr(JCExpression... args) {
        return makeLetExpr(naming.temp(), null, args);
    }

    // Creates a "( let var1=expr1,var2=expr2,...,varN=exprN in statements; varN; )"
    // or a "( let var1=expr1,var2=expr2,...,varN=exprN in statements; exprO; )"
    JCExpression makeLetExpr(Naming.SyntheticName varBaseName, List<JCStatement> statements, JCExpression... args) {
        return makeLetExpr(varBaseName.getName(), statements, args);
    }
    private JCExpression makeLetExpr(String varBaseName, List<JCStatement> statements, JCExpression... args) {
        String varName = null;
        ListBuffer<JCStatement> decls = ListBuffer.lb();
        int i;
        for (i = 0; (i + 1) < args.length; i += 2) {
            JCExpression typeExpr = args[i];
            JCExpression valueExpr = args[i+1];
            varName = varBaseName + ((args.length > 3) ? "$" + i : "");
            JCVariableDecl varDecl = makeVar(varName, typeExpr, valueExpr);
            decls.append(varDecl);
        }
        
        JCExpression result;
        if (i == args.length) {
            result = makeUnquotedIdent(varName);
        } else {
            result = args[i];
        }
        if (statements != null) {
            decls.appendList(statements);
        } 
        return make().LetExpr(decls.toList(), result);   
    }
    
    /*
     * Type handling
     */

    boolean isBooleanTrue(Declaration decl) {
        return decl == loader().getDeclaration("ceylon.language.true", DeclarationType.VALUE);
    }
    
    boolean isBooleanFalse(Declaration decl) {
        return decl == loader().getDeclaration("ceylon.language.false", DeclarationType.VALUE);
    }
    
    /**
     * Determines whether the given type is optional.
     */
    boolean isOptional(ProducedType type) {
        // Note we don't use typeFact().isOptionalType(type) because
        // that implements a stricter test used in the type checker.
        return typeFact().getNullDeclaration().getType().isSubtypeOf(type);
    }
    
    boolean isNothing(ProducedType type) {
        return type.getSupertype(typeFact.getNothingDeclaration()) != null;
    }
    
    public boolean isVoid(ProducedType type) {
        return typeFact.getVoidDeclaration().getType().isExactly(type);
    }

    private boolean isObject(ProducedType type) {
        return typeFact.getObjectDeclaration().getType().isExactly(type);
    }

    ProducedType simplifyType(ProducedType orgType) {
        ProducedType type = orgType;
        if (isOptional(type)) {
            // For an optional type T?:
            //  - The Ceylon type T? results in the Java type T
            type = typeFact().getDefiniteType(type);
            if (type.getUnderlyingType() != null) {
                // A definite type should not have its underlyingType set so we make a copy
                type = type.withoutUnderlyingType();
            }
        }
        
        TypeDeclaration tdecl = type.getDeclaration();
        if (tdecl instanceof UnionType && tdecl.getCaseTypes().size() == 1) {
            // Special case when the Union contains only a single CaseType
            // FIXME This is not correct! We might lose information about type arguments!
            type = tdecl.getCaseTypes().get(0);
        } else if (tdecl instanceof IntersectionType) {
            java.util.List<ProducedType> satisfiedTypes = tdecl.getSatisfiedTypes();
            if (satisfiedTypes.size() == 1) {
                // Special case when the Intersection contains only a single SatisfiedType
                // FIXME This is not correct! We might lose information about type arguments!
                type = satisfiedTypes.get(0);
            } else if (satisfiedTypes.size() == 2) {
                // special case for T? simplified as T&Object
                if (isTypeParameter(satisfiedTypes.get(0)) && isObject(satisfiedTypes.get(1))) {
                    type = satisfiedTypes.get(0);
                }
            }
        }
        
        return type;
    }

    ProducedTypedReference getTypedReference(TypedDeclaration decl){
        if(decl.getContainer() instanceof TypeDeclaration){
            TypeDeclaration containerDecl = (TypeDeclaration) decl.getContainer();
            return containerDecl.getType().getTypedMember(decl, Collections.<ProducedType>emptyList());
        }
        return decl.getProducedTypedReference(null, Collections.<ProducedType>emptyList());
    }
    
    ProducedTypedReference nonWideningTypeDecl(ProducedTypedReference typedReference) {
        ProducedTypedReference refinedTypedReference = getRefinedDeclaration(typedReference);
        if(refinedTypedReference != null){
            /*
             * We are widening if the type:
             * - is not object
             * - is erased to object
             * - refines a declaration that is not erased to object
             */
            ProducedType declType = typedReference.getType();
            ProducedType refinedDeclType = refinedTypedReference.getType();
            boolean isWidening = isWidening(declType, refinedDeclType);
            
            if(!isWidening){
                // make sure we get the instantiated refined decl
                if(refinedDeclType.getDeclaration() instanceof TypeParameter
                        && !(declType.getDeclaration() instanceof TypeParameter))
                    refinedDeclType = nonWideningType(typedReference, refinedTypedReference);
                isWidening = isWideningTypeArguments(declType, refinedDeclType, true);
            }
            
            if(isWidening)
                return refinedTypedReference;
        }
        return typedReference;
    }

    /*
     * We have several special cases here to find the best non-widening refinement in case of multiple inheritace:
     * 
     * - The first special case is for some decls like None.first, which inherits from ContainerWithFirstElement
     * twice: once with Nothing (erased to j.l.Object) and once with Element (a type param). Now, in order to not widen the
     * return type it can't be Nothing (j.l.Object), it must be Element (a type param that is not instantiated), because in Java
     * a type param refines j.l.Object but not the other way around.
     * - The second special case is when implementing an interface first with a non-erased type, then with an erased type. In this
     * case we want the refined decl to be the one with the non-erased type.
     * - The third special case is when we implement a declaration via multiple super types, without having any refining
     * declarations in those supertypes, simply by instantiating a common super type with different type parameters
     */
    private ProducedTypedReference getRefinedDeclaration(ProducedTypedReference typedReference) {
        TypedDeclaration decl = typedReference.getDeclaration();
        TypedDeclaration modelRefinedDecl = (TypedDeclaration)decl.getRefinedDeclaration();
        // quick exit
        if(decl == modelRefinedDecl)
            return null;
        if(decl.getContainer() instanceof ClassOrInterface){
            // only try to find better if we're erasing to Object and we're not returning a type param
            if(willEraseToObject(typedReference.getType())
                    && !isTypeParameter(typedReference.getType())){
                ClassOrInterface declaringType = (ClassOrInterface) decl.getContainer();
                Set<TypedDeclaration> refinedMembers = getRefinedMembers(declaringType, decl.getName(), null);
                // now we must select a different refined declaration if we refine it more than once
                if(refinedMembers.size() > 1){
                    // first case
                    for(TypedDeclaration refinedDecl : refinedMembers){
                        // get the type reference to see if any eventual type param is instantiated in our inheritance of this type/method
                        ProducedTypedReference refinedTypedReference = getRefinedTypedReference(typedReference, refinedDecl);
                        // if it is not instantiated, that's the one we're looking for
                        if(isTypeParameter(refinedTypedReference.getType()))
                            return refinedTypedReference;
                    }
                    // second case
                    for(TypedDeclaration refinedDecl : refinedMembers){
                        // get the type reference to see if any eventual type param is instantiated in our inheritance of this type/method
                        ProducedTypedReference refinedTypedReference = getRefinedTypedReference(typedReference, refinedDecl);
                        // if we're not erasing this one to Object let's select it
                        if(!willEraseToObject(refinedTypedReference.getType()))
                            return refinedTypedReference;
                    }
                    // third case
                    if(isTypeParameter(modelRefinedDecl.getType())){
                        // it can happen that we have inherited a method twice from a single refined declaration 
                        // via different supertype instantiations, without having ever refined them in supertypes
                        // so we try each super type to see if we already have a matching typed reference
                        // first super type
                        ProducedType extendedType = declaringType.getExtendedType();
                        if(extendedType != null){
                            ProducedTypedReference refinedTypedReference = getRefinedTypedReference(extendedType, modelRefinedDecl);
                            ProducedType refinedType = refinedTypedReference.getType();
                            if(!isTypeParameter(refinedType)
                                    && !willEraseToObject(refinedType))
                                return refinedTypedReference;
                        }
                        // then satisfied interfaces
                        for(ProducedType satisfiedType : declaringType.getSatisfiedTypes()){
                            ProducedTypedReference refinedTypedReference = getRefinedTypedReference(satisfiedType, modelRefinedDecl);
                            ProducedType refinedType = refinedTypedReference.getType();
                            if(!isTypeParameter(refinedType)
                                    && !willEraseToObject(refinedType))
                                return refinedTypedReference;
                        }
                    }
                }
            }
        }
        return getRefinedTypedReference(typedReference, modelRefinedDecl);
    }

    public Set<TypedDeclaration> getRefinedMembers(TypeDeclaration decl,
            String name, 
            java.util.List<ProducedType> signature) {
        Set<TypedDeclaration> ret = new HashSet<TypedDeclaration>();
        collectRefinedMembers(decl, name, signature, 
                new HashSet<TypeDeclaration>(), ret);
        return ret;
    }

    private void collectRefinedMembers(TypeDeclaration decl, String name, 
            java.util.List<ProducedType> signature, java.util.Set<TypeDeclaration> visited, Set<TypedDeclaration> ret) {
        if (visited.contains(decl)) {
            return;
        }
        else {
            visited.add(decl);
            TypeDeclaration et = decl.getExtendedTypeDeclaration();
            if (et!=null) {
                collectRefinedMembers(et, name, signature, visited, ret);
            }
            for (TypeDeclaration st: decl.getSatisfiedTypeDeclarations()) {
                collectRefinedMembers(st, name, signature, visited, ret);
            }
            Declaration found = decl.getDirectMember(name, signature);
            if(found != null)
                ret.add((TypedDeclaration) found);
        }
    }

    private ProducedTypedReference getRefinedTypedReference(ProducedTypedReference typedReference, 
            TypedDeclaration refinedDeclaration) {
        return getRefinedTypedReference(typedReference.getQualifyingType(), refinedDeclaration);
    }

    private ProducedTypedReference getRefinedTypedReference(ProducedType qualifyingType, 
                                                            TypedDeclaration refinedDeclaration) {
        TypeDeclaration refinedContainer = (TypeDeclaration)refinedDeclaration.getContainer();

        ProducedType refinedContainerType = qualifyingType.getSupertype(refinedContainer);
        return refinedDeclaration.getProducedTypedReference(refinedContainerType, Collections.<ProducedType>emptyList());
    }

    public boolean isWidening(ProducedType declType, ProducedType refinedDeclType) {
        return !sameType(syms().ceylonObjectType, declType)
                && willEraseToObject(declType)
                && !willEraseToObject(refinedDeclType);
    }

    private boolean isWideningTypeArguments(ProducedType declType, ProducedType refinedDeclType, boolean allowSubtypes) {
        if(declType.getDeclaration() instanceof TypeParameter
                && refinedDeclType.getDeclaration() instanceof TypeParameter){
            // consider them equivalent if they have the same bounds
            TypeParameter tp = (TypeParameter) declType.getDeclaration();
            TypeParameter refinedTP = (TypeParameter) refinedDeclType.getDeclaration();
            
            return !haveSameBounds(tp, refinedTP);
        }
        if(allowSubtypes){
            // if we don't erase to object and we refine something that does, we can't possibly be widening
            if(willEraseToObject(refinedDeclType)
                    && !willEraseToObject(declType))
                return false;

            // if we have exactly the same type don't bother finding a common ancestor
            if(!declType.isExactly(refinedDeclType)){
                // check if we can form an informed decision
                if(refinedDeclType.getDeclaration() == null)
                    return true;
                // find the instantiation of the refined decl type in the decl type
                // special case for optional types: let's find the definite type since
                // in java they are equivalent
                ProducedType definiteType = typeFact().getDefiniteType(refinedDeclType);
                if(definiteType != null)
                    refinedDeclType = definiteType;
                declType = declType.getSupertype(refinedDeclType.getDeclaration());
                // could not find common type, we must be widening somehow
                if(declType == null)
                    return true;
            }
        }
        Map<TypeParameter, ProducedType> typeArguments = declType.getTypeArguments();
        Map<TypeParameter, ProducedType> refinedTypeArguments = refinedDeclType.getTypeArguments();
        for(Entry<TypeParameter, ProducedType> typeArgument : typeArguments.entrySet()){
            ProducedType refinedTypeArgument = refinedTypeArguments.get(typeArgument.getKey());
            if(refinedTypeArgument == null)
                return true; // something fishy here
            // check if the type arg is widening due to erasure
            if(isWidening(typeArgument.getValue(), refinedTypeArgument))
                return true;
            // check if the type arg is a subtype, or if its type args are widening
            if(isWideningTypeArguments(typeArgument.getValue(), refinedTypeArgument, false))
                return true;
        }
        // so far so good
        return false;
    }

    public boolean haveSameBounds(TypeParameter tp, TypeParameter refinedTP) {
        java.util.List<ProducedType> satTP = tp.getSatisfiedTypes();
        java.util.List<ProducedType> satRefinedTP = new LinkedList<ProducedType>();
        satRefinedTP.addAll(refinedTP.getSatisfiedTypes());
        // same number of bounds
        if(satTP.size() != satRefinedTP.size())
            return false;
        // make sure all the bounds are the same
        OUT:
        for(ProducedType satisfiedType : satTP){
            for(ProducedType refinedSatisfiedType : satRefinedTP){
                // if we found it, remove it from the second list to not match it again
                if(satisfiedType.isExactly(refinedSatisfiedType)){
                    satRefinedTP.remove(satRefinedTP);
                    continue OUT;
                }
            }
            // not found
            return false;
        }
        // all bounds are equal
        return true;
    }

    ProducedType nonWideningType(ProducedTypedReference declaration, ProducedTypedReference refinedDeclaration){
        if(declaration == refinedDeclaration)
            return declaration.getType();
        ProducedType refinedType = refinedDeclaration.getType();
        // if the refined type is a method TypeParam, use the original decl that will be more correct
        if(refinedType.getDeclaration() instanceof TypeParameter
                && refinedType.getDeclaration().getContainer() instanceof Method){
            return declaration.getType();
        }
        return refinedType;
    }
    
    private ProducedType toPType(com.sun.tools.javac.code.Type t) {
        return loader().getType(t.tsym.packge().getQualifiedName().toString(), t.tsym.getQualifiedName().toString(), null);
    }
    
    private boolean sameType(Type t1, ProducedType t2) {
        return toPType(t1).isExactly(t2);
    }
    
    // Determines if a type will be erased to java.lang.Object once converted to Java
    boolean willEraseToObject(ProducedType type) {
        type = simplifyType(type);
        return (sameType(syms().ceylonVoidType, type) || sameType(syms().ceylonObjectType, type)
                || sameType(syms().ceylonNothingType, type)
                || sameType(syms().ceylonIdentifiableObjectType, type)
                || sameType(syms().ceylonIdentifiableType, type)
                || type.getDeclaration() instanceof BottomType
                || typeFact().isUnion(type)|| typeFact().isIntersection(type));
    }
    
    boolean willEraseToException(ProducedType type) {
        type = simplifyType(type);
        return (sameType(syms().ceylonExceptionType, type));
    }
    
    boolean willEraseToIterable(ProducedType type) {
        return typeFact().getNonemptyIterableType(typeFact().getDefiniteType(type)) != null;
    }

    boolean isCeylonString(ProducedType type) {
        return (sameType(syms().ceylonStringType, type));
    }
    
    boolean isCeylonBoolean(ProducedType type) {
        return type.isSubtypeOf(typeFact.getBooleanDeclaration().getType())
                && !(type.getDeclaration() instanceof BottomType);
    }
    
    boolean isCeylonInteger(ProducedType type) {
        return (sameType(syms().ceylonIntegerType, type));
    }
    
    boolean isCeylonFloat(ProducedType type) {
        return (sameType(syms().ceylonFloatType, type));
    }
    
    boolean isCeylonCharacter(ProducedType type) {
        return (sameType(syms().ceylonCharacterType, type));
    }

    boolean isCeylonArray(ProducedType type) {
        return type.getSupertype(typeFact.getArrayDeclaration()) != null;
    }
    
    boolean isCeylonObject(ProducedType type) {
        return sameType(syms().ceylonObjectType, type);
    }
    
    boolean isCeylonBasicType(ProducedType type) {
        return (isCeylonString(type) || isCeylonBoolean(type) || isCeylonInteger(type) || isCeylonFloat(type) || isCeylonCharacter(type));
    }
    
    boolean isCeylonCallable(ProducedType type) {
        return type.isCallable();
    }

    /*
     * Java Type creation
     */
    
    /** For use in {@code implements} clauses. */
    static final int JT_SATISFIES = 1 << 0;
    /** For use in {@code extends} clauses. */
    static final int JT_EXTENDS = 1 << 1;
    
    /** For use when a primitive type won't do. */
    static final int JT_NO_PRIMITIVES = 1 << 2;
    
    /** For generating a type without type arguments. */
    static final int JT_RAW = 1 << 3;
    /** For use in {@code catch} statements. */
    static final int JT_CATCH = 1 << 4;
    /** 
     * Generate a 'small' primitive type (if the type is primitive and has a 
     * small variant). 
     */
    static final int JT_SMALL = 1 << 5;
    /** For use in {@code new} expressions. */
    static final int JT_CLASS_NEW = 1 << 6;
    /** Generates the Java type of the companion class of the given type */
    static final int JT_COMPANION = 1 << 7;
    static final int JT_NON_QUALIFIED = 1 << 8;
    
    private static final int __JT_RAW_TP_BOUND = 1 << 9;
    /** 
     * If the type is a type parameter, return the Java type for its upper bound. 
     * Implies {@link #JT_RAW}   
     */
    static final int JT_RAW_TP_BOUND = JT_RAW | __JT_RAW_TP_BOUND;
    
    private static final int __JT_TYPE_ARGUMENT = 1 << 10;
    /** For use when generating a type argument. Implies {@code JT_NO_PRIMITIVES} */
    static final int JT_TYPE_ARGUMENT = JT_NO_PRIMITIVES | __JT_TYPE_ARGUMENT;

    /**
     * This function is used solely for method return types and parameters 
     */
    JCExpression makeJavaType(TypedDeclaration typeDecl, ProducedType type, int flags) {
        if (typeDecl instanceof FunctionalParameter) {
            FunctionalParameter p = (FunctionalParameter)typeDecl;
            ProducedType pt = type;
            for (int ii = 1; ii < p.getParameterLists().size(); ii++) {
                pt = typeFact().getCallableType(pt);
            }
            return makeJavaType(typeFact().getCallableType(pt), flags);
        } else {
            boolean usePrimitives = CodegenUtil.isUnBoxed(typeDecl);
            return makeJavaType(type, flags | (usePrimitives ? 0 : AbstractTransformer.JT_NO_PRIMITIVES));
        }
    }

    JCExpression makeJavaType(ProducedType producedType) {
        return makeJavaType(producedType, 0);
    }

    JCExpression makeJavaType(ProducedType type, final int flags) {
        if(type == null)
            return make().Erroneous();
        
        if ((flags & __JT_RAW_TP_BOUND) != 0
                && type.getDeclaration() instanceof TypeParameter) {
            type = ((TypeParameter)type.getDeclaration()).getExtendedType();    
        }
        
        // ERASURE
        if (willEraseToObject(type)) {
            // For an erased type:
            // - Any of the Ceylon types Void, Object, Nothing,
            //   IdentifiableObject, and Bottom result in the Java type Object
            // For any other union type U|V (U nor V is Optional):
            // - The Ceylon type U|V results in the Java type Object
            ProducedType iterType = typeFact().getNonemptyIterableType(typeFact().getDefiniteType(type));
            if (iterType != null) {
                // We special case the erasure of X[] and X[]?
                type = iterType;
            } else {
                if ((flags & JT_SATISFIES) != 0) {
                    return null;
                } else {
                    return make().Type(syms().objectType);
                }
            }
        } else if (willEraseToException(type)) {
            if ((flags & JT_CLASS_NEW) != 0
                    || (flags & JT_EXTENDS) != 0) {
                return make().Type(syms().ceylonExceptionType);
            } else if ((flags & JT_CATCH) != 0) {
                return make().Type(syms().exceptionType);
            } else {
                return make().Type(syms().throwableType);
            }
        } else if ((flags & (JT_SATISFIES | JT_EXTENDS | JT_NO_PRIMITIVES | JT_CLASS_NEW)) == 0 
                && (!isOptional(type) || isJavaString(type))) {
            if (isCeylonString(type) || isJavaString(type)) {
                return make().Type(syms().stringType);
            } else if (isCeylonBoolean(type)) {
                return make().TypeIdent(TypeTags.BOOLEAN);
            } else if (isCeylonInteger(type)) {
                if ("byte".equals(type.getUnderlyingType())) {
                    return make().TypeIdent(TypeTags.BYTE);
                } else if ("short".equals(type.getUnderlyingType())) {
                    return make().TypeIdent(TypeTags.SHORT);
                } else if ((flags & JT_SMALL) != 0 || "int".equals(type.getUnderlyingType())) {
                    return make().TypeIdent(TypeTags.INT);
                } else {
                    return make().TypeIdent(TypeTags.LONG);
                }
            } else if (isCeylonFloat(type)) {
                if ((flags & JT_SMALL) != 0 || "float".equals(type.getUnderlyingType())) {
                    return make().TypeIdent(TypeTags.FLOAT);
                } else {
                    return make().TypeIdent(TypeTags.DOUBLE);
                }
            } else if (isCeylonCharacter(type)) {
                if ("char".equals(type.getUnderlyingType())) {
                    return make().TypeIdent(TypeTags.CHAR);
                } else {
                    return make().TypeIdent(TypeTags.INT);
                }
            }
        } else if (isCeylonBoolean(type)
                && !isTypeParameter(type)) {
                //&& (flags & TYPE_ARGUMENT) == 0){
            // special case to get rid of $true and $false types
            type = typeFact.getBooleanDeclaration().getType();
        }
        
        JCExpression jt = makeErroneous();
        
        ProducedType simpleType = simplifyType(type);
        
        java.util.List<ProducedType> qualifyingTypes = new java.util.ArrayList<ProducedType>();
        ProducedType qType = simpleType;
        boolean hasTypeParameters = false;
        while (qType != null) {
            hasTypeParameters |= !qType.getTypeArguments().isEmpty();
            qualifyingTypes.add(qType);
            qType = qType.getQualifyingType();
        }
        int firstQualifyingTypeWithTypeParameters = qualifyingTypes.size() - 1;
        // find the first static one, from the right to the left
        for(ProducedType pt : qualifyingTypes){
            TypeDeclaration declaration = pt.getDeclaration();
            if(Decl.isStatic(declaration)){
                break;
            }
            firstQualifyingTypeWithTypeParameters--;
        }
        if(firstQualifyingTypeWithTypeParameters < 0)
            firstQualifyingTypeWithTypeParameters = 0;
        // put them in outer->inner order
        Collections.reverse(qualifyingTypes);
        
        if (((flags & JT_RAW) == 0) && hasTypeParameters) {
            // special case for interfaces because we pull them into toplevel types
            if(Decl.isCeylon(simpleType.getDeclaration())
                    && qualifyingTypes.size() > 1
                    && simpleType.getDeclaration() instanceof Interface){
                JCExpression baseType = makeErroneous();
                TypeDeclaration tdecl = simpleType.getDeclaration();
                // collect all the qualifying type args we'd normally have
                java.util.List<TypeParameter> qualifyingTypeParameters = new java.util.ArrayList<TypeParameter>();
                java.util.Map<TypeParameter, ProducedType> qualifyingTypeArguments = new java.util.HashMap<TypeParameter, ProducedType>();
                for (ProducedType qualifiedType : qualifyingTypes) {
                    Map<TypeParameter, ProducedType> tas = qualifiedType.getTypeArguments();
                    java.util.List<TypeParameter> tps = qualifiedType.getDeclaration().getTypeParameters();
                    if (tps != null) {
                        qualifyingTypeParameters.addAll(tps);
                        qualifyingTypeArguments.putAll(tas);
                    }
                }
                ListBuffer<JCExpression> typeArgs = makeTypeArgs(isCeylonCallable(simpleType), 
                        flags, 
                        qualifyingTypeArguments, qualifyingTypeParameters);
                if (isCeylonCallable(type) && 
                        (flags & JT_CLASS_NEW) != 0) {
                    baseType = makeIdent(syms().ceylonAbstractCallableType);
                } else {
                    baseType = naming.makeDeclarationName(tdecl, DeclNameFlag.QUALIFIED);
                }

                if (typeArgs != null && typeArgs.size() > 0) {
                    jt = make().TypeApply(baseType, typeArgs.toList());
                } else {
                    jt = baseType;
                }
            }else if((flags & JT_NON_QUALIFIED) == 0){
                int index = 0;
                for (ProducedType qualifyingType : qualifyingTypes) {
                    jt = makeParameterisedType(qualifyingType, type, flags, jt, qualifyingTypes, firstQualifyingTypeWithTypeParameters, index);
                    index++;
                }
            }else{
                jt = makeParameterisedType(type, type, flags, jt, qualifyingTypes, 0, 0);
            }
        } else {
            TypeDeclaration tdecl = simpleType.getDeclaration();            
            // For an ordinary class or interface type T:
            // - The Ceylon type T results in the Java type T
            if(tdecl instanceof TypeParameter)
                jt = makeQuotedIdent(tdecl.getName());
            // don't use underlying type if we want no primitives
            else if((flags & (JT_SATISFIES | JT_NO_PRIMITIVES)) != 0 || simpleType.getUnderlyingType() == null){
                jt = naming.makeDeclarationName(tdecl, jtFlagsToDeclNameOpts(flags));
            }else
                jt = makeQuotedFQIdent(simpleType.getUnderlyingType());
        }
        
        return jt;
    }

    public JCExpression makeParameterisedType(ProducedType type, ProducedType generalType, final int flags, 
            JCExpression qualifyingExpression, java.util.List<ProducedType> qualifyingTypes, 
            int firstQualifyingTypeWithTypeParameters, int index) {
        JCExpression baseType;
        TypeDeclaration tdecl = type.getDeclaration();
        ListBuffer<JCExpression> typeArgs = null;
        if(index >= firstQualifyingTypeWithTypeParameters)
            typeArgs = makeTypeArgs( 
                    type, 
                    //tdecl, 
                    flags);
        if (isCeylonCallable(generalType) && 
                (flags & JT_CLASS_NEW) != 0) {
            baseType = makeIdent(syms().ceylonAbstractCallableType);
        } else if (index == 0) {
            // in Ceylon we'd move the nested decl to a companion class
            // but in Java we just don't have type params to the qualifying type if the
            // qualified type is static
            if (tdecl instanceof Interface
                    && qualifyingTypes.size() > 1
                    && firstQualifyingTypeWithTypeParameters == 0) {
                baseType = naming.makeCompanionClassName(tdecl);
            } else {
                baseType = naming.makeDeclarationName(tdecl, jtFlagsToDeclNameOpts(flags));
            }
            
        } else {
            baseType = naming.makeDeclName(qualifyingExpression, tdecl, 
                    jtFlagsToDeclNameOpts(flags 
                            | JT_NON_QUALIFIED 
                            | (type.getDeclaration() instanceof Interface ? JT_COMPANION : 0)));
        }

        if (typeArgs != null && typeArgs.size() > 0) {
            qualifyingExpression = make().TypeApply(baseType, typeArgs.toList());
        } else {
            qualifyingExpression = baseType;
        }
        return qualifyingExpression;
    }

    private ListBuffer<JCExpression> makeTypeArgs(
            ProducedType simpleType, 
            int flags) {
        Map<TypeParameter, ProducedType> tas = simpleType.getTypeArguments();
        java.util.List<TypeParameter> tps = simpleType.getDeclaration().getTypeParameters();
        

        return makeTypeArgs(isCeylonCallable(simpleType), flags, tas, tps);
    }

    private ListBuffer<JCExpression> makeTypeArgs(boolean isCeylonCallable,
            int flags, Map<TypeParameter, ProducedType> tas,
            java.util.List<TypeParameter> tps) {
        ListBuffer<JCExpression> typeArgs = new ListBuffer<JCExpression>();
        int idx = 0;
        for (TypeParameter tp : tps) {
            ProducedType ta = tas.get(tp);
            if (idx > 0 &&
                    isCeylonCallable) {
                // In the runtime Callable only has a single type param
                break;
            }
            if (isOptional(ta)) {
                // For an optional type T?:
                // - The Ceylon type Foo<T?> results in the Java type Foo<T>.
                ta = getNonNullType(ta);
            }
            if (typeFact().isUnion(ta) || typeFact().isIntersection(ta)) {
                // For any other union type U|V (U nor V is Optional):
                // - The Ceylon type Foo<U|V> results in the raw Java type Foo.
                // For any other intersection type U|V:
                // - The Ceylon type Foo<U&V> results in the raw Java type Foo.
                ProducedType iterType = typeFact().getNonemptyIterableType(typeFact().getDefiniteType(ta));
                // don't break if the union type is erased to something better than Object
                if(iterType == null){
                    // use raw types if:
                    // - we're calling a constructor
                    // - we're not in a type argument (when used as type arguments raw types have more constraint than at the toplevel)
                    //   or not in an extends or satisfies
                    if((flags & JT_CLASS_NEW) != 0 || (flags & (__JT_TYPE_ARGUMENT | JT_EXTENDS | JT_SATISFIES)) == 0){
                        // A bit ugly, but we need to escape from the loop and create a raw type, no generics
                        typeArgs = null;
                        break;
                    }
                    // otherwise just go on
                }else
                    ta = iterType;
            }
            if (isCeylonBoolean(ta)
                    && !isTypeParameter(ta)) {
                ta = typeFact.getBooleanDeclaration().getType();
            } 
            JCExpression jta;
            
            if (sameType(syms().ceylonVoidType, ta)) {
                // For the root type Void:
                if ((flags & (JT_SATISFIES | JT_EXTENDS)) != 0) {
                    // - The Ceylon type Foo<Void> appearing in an extends or satisfies
                    //   clause results in the Java raw type Foo<Object>
                    jta = make().Type(syms().objectType);
                } else {
                    // - The Ceylon type Foo<Void> appearing anywhere else results in the Java type
                    // - Foo<Object> if Foo<T> is invariant in T
                    // - Foo<?> if Foo<T> is covariant in T, or
                    // - Foo<Object> if Foo<T> is contravariant in T
                    if (tp.isContravariant()) {
                        jta = make().Type(syms().objectType);
                    } else if (tp.isCovariant()) {
                        jta = make().Wildcard(make().TypeBoundKind(BoundKind.UNBOUND), makeJavaType(ta, flags | JT_TYPE_ARGUMENT));
                    } else {
                        jta = make().Type(syms().objectType);
                    }
                }
            } else if (ta.getDeclaration() instanceof BottomType
                    // if we're in a type argument, extends or satisfies already, union and intersection types should 
                    // use the same erasure rules as bottom: prefer wildcards
                    || ((flags & (__JT_TYPE_ARGUMENT | JT_EXTENDS | JT_SATISFIES)) != 0
                        && (typeFact().isUnion(ta) || typeFact().isIntersection(ta)))) {
                // For the bottom type Bottom:
                if ((flags & (JT_CLASS_NEW)) != 0) {
                    // - The Ceylon type Foo<Bottom> or Foo<erased_type> appearing in an instantiation
                    //   clause results in the Java raw type Foo
                    // A bit ugly, but we need to escape from the loop and create a raw type, no generics
                    typeArgs = null;
                    break;
                } else {
                    // - The Ceylon type Foo<Bottom> appearing in an extends or satisfies location results in the Java type
                    //   Foo<Object> (see https://github.com/ceylon/ceylon-compiler/issues/633 for why)
                    if((flags & (JT_SATISFIES | JT_EXTENDS)) != 0){
                        if (ta.getDeclaration() instanceof BottomType) {
                            jta = make().Type(syms().objectType);
                        } else {
                            if (!tp.getSatisfiedTypes().isEmpty()) {
                                // union or intersection: Use the common upper bound of the types
                                jta = makeJavaType(tp.getSatisfiedTypes().get(0), JT_TYPE_ARGUMENT);
                            } else {
                                jta = make().Type(syms().objectType);
                            }
                        }
                    }else if (ta.getDeclaration() instanceof BottomType){
                        // - The Ceylon type Foo<Bottom> appearing anywhere else results in the Java type
                        // - Foo<?> always
                        jta = make().Wildcard(make().TypeBoundKind(BoundKind.UNBOUND), null);
                    }else{
                        // - The Ceylon type Foo<T> appearing anywhere else results in the Java type
                        // - Foo<T> if Foo is invariant in T,
                        // - Foo<? extends T> if Foo is covariant in T, or
                        // - Foo<? super T> if Foo is contravariant in T
                        if (((flags & JT_CLASS_NEW) == 0) && tp.isContravariant()) {
                            jta = make().Wildcard(make().TypeBoundKind(BoundKind.SUPER), makeJavaType(ta, JT_TYPE_ARGUMENT));
                        } else if (((flags & JT_CLASS_NEW) == 0) && tp.isCovariant()) {
                            jta = make().Wildcard(make().TypeBoundKind(BoundKind.EXTENDS), makeJavaType(ta, JT_TYPE_ARGUMENT));
                        } else {
                            jta = makeJavaType(ta, JT_TYPE_ARGUMENT);
                        }
                    }
                }
            } else {
                // For an ordinary class or interface type T:
                if ((flags & (JT_SATISFIES | JT_EXTENDS)) != 0) {
                    // - The Ceylon type Foo<T> appearing in an extends or satisfies clause
                    //   results in the Java type Foo<T>
                    jta = makeJavaType(ta, JT_TYPE_ARGUMENT);
                } else {
                    // - The Ceylon type Foo<T> appearing anywhere else results in the Java type
                    // - Foo<T> if Foo is invariant in T,
                    // - Foo<? extends T> if Foo is covariant in T, or
                    // - Foo<? super T> if Foo is contravariant in T
                    if (((flags & JT_CLASS_NEW) == 0) && tp.isContravariant()) {
                        jta = make().Wildcard(make().TypeBoundKind(BoundKind.SUPER), makeJavaType(ta, JT_TYPE_ARGUMENT));
                    } else if (((flags & JT_CLASS_NEW) == 0) && tp.isCovariant()) {
                        jta = make().Wildcard(make().TypeBoundKind(BoundKind.EXTENDS), makeJavaType(ta, JT_TYPE_ARGUMENT));
                    } else {
                        jta = makeJavaType(ta, JT_TYPE_ARGUMENT);
                    }
                }
            }
            typeArgs.add(jta);
            idx++;
        }
        return typeArgs;
    }

    private ProducedType getNonNullType(ProducedType pt) {
        // typeFact().getDefiniteType() intersects with Object, which isn't 
        // always right for working with the java type system.
        if (typeFact().getVoidDeclaration().equals(pt.getDeclaration())) {
            pt = typeFact().getObjectDeclaration().getType();
        }
        else {
            pt = pt.minus(typeFact().getNothingDeclaration());
        }
        return pt;
    }
    
    private boolean isJavaString(ProducedType type) {
        return "java.lang.String".equals(type.getUnderlyingType());
    }
    
    private ClassDefinitionBuilder ccdb;
    
    ClassDefinitionBuilder current() {
        return ((AbstractTransformer)gen()).ccdb; 
    }
    
    ClassDefinitionBuilder replace(ClassDefinitionBuilder ccdb) {
        ClassDefinitionBuilder result = ((AbstractTransformer)gen()).ccdb;
        ((AbstractTransformer)gen()).ccdb = ccdb;
        return result;
    }

    private DeclNameFlag[] jtFlagsToDeclNameOpts(int flags) {
        java.util.List<DeclNameFlag> args = new LinkedList<DeclNameFlag>();
        if ((flags & JT_COMPANION) != 0) {
            args.add(DeclNameFlag.COMPANION);
        }
        if ((flags & JT_NON_QUALIFIED) == 0) {
            args.add(DeclNameFlag.QUALIFIED);
        }
        DeclNameFlag[] opts = args.toArray(new DeclNameFlag[args.size()]);
        return opts;
    }
    
    /**
     * Gets the first type parameter from the type model representing a 
     * {@code ceylon.language.Callable<Result, ParameterTypes...>}.
     * @param typeModel A {@code ceylon.language.Callable<Result, ParameterTypes...>}.
     * @return The result type of the {@code Callable}.
     */
    ProducedType getReturnTypeOfCallable(ProducedType typeModel) {
        if (!isCeylonCallable(typeModel)) {
            throw new RuntimeException("Not a Callable<>: " + typeModel);
        }
        return typeModel.getTypeArgumentList().get(0);
    }
    
    /**
     * <p>Gets the type of the given functional
     * (ignoring parameter types) according to 
     * the functionals parameter lists. The result is always a 
     * {@code Callable} of some kind (because Functionals always have 
     * at least one parameter list).</p> 
     * 
     * <p>For example:</p>
     * <table>
     * <tbody>
     * <tr><th>functional</th><th>functionalType(functional)</th></tr>
     * <tr><td><code>String m()</code></td><td><code>Callable&lt;String&gt;</code></td></tr>
     * <tr><td><code>String m()()</code></td><td><code>Callable&lt;Callable&lt;String&gt;&gt;</code></td></tr>
     * </tbody>
     * </table>
     */
    ProducedType functionalType(Functional model) {
        return typeFact().getCallableType(functionalReturnType(model));
    }
    
    /**
     * <p>Gets the return type of the given functional (ignoring parameter 
     * types) according to the functionals parameter lists. If the functional 
     * has multiple parameter lists the return type will be a 
     * {@code Callable}.</p>
     * 
     * <p>For example:</p>
     * <table>
     * <tbody>
     * <tr><th>functional</th><th>functionalReturnType(functional)</th></tr>
     * <tr><td><code>String m()</code></td><td><code>String</code></td></tr>
     * <tr><td><code>String m()()</code></td><td><code>Callable&lt;String&gt;</code></td></tr>
     * </tbody>
     * </table>
     */
    ProducedType functionalReturnType(Functional model) {
        ProducedType callableType = model.getType();
        for (int ii = 1; ii < model.getParameterLists().size(); ii++) {
            callableType = typeFact().getCallableType(callableType);
        }
        return callableType;
    }
    
    /** 
     * Return the upper bound of any type parameter, instead of the type 
     * parameter itself 
     */
    static final int TP_TO_BOUND = 1<<0;
    /** 
     * Return the type of the sequenced parameter (T[]) rather than its element type (T) 
     */
    static final int TP_SEQUENCED_TYPE = 1<<1;
    
    ProducedType getTypeForParameter(Parameter parameter, ProducedReference producedReference, int flags) {
        if (parameter instanceof FunctionalParameter) {
            return getTypeForFunctionalParameter((FunctionalParameter)parameter);
        }
        if (producedReference == null) {
            return parameter.getType();
        }
        final ProducedTypedReference producedTypedReference = producedReference.getTypedParameter(parameter);
        final ProducedType type = producedTypedReference.getType();
        final TypedDeclaration producedParameterDecl = producedTypedReference.getDeclaration();
        final ProducedType declType = producedParameterDecl.getType();
        final TypeDeclaration declTypeDecl = declType.getDeclaration();
        if(isJavaVariadic(parameter) && (flags & TP_SEQUENCED_TYPE) == 0){
            // type of param must be Iterable<T>
            ProducedType elementType = typeFact.getIteratedType(type);
            if(elementType == null){
                log.error("ceylon", "Invalid type for Java variadic parameter: "+type.getProducedTypeQualifiedName());
                return type;
            }
            return elementType;
        }
        if (type.getDeclaration() instanceof ClassOrInterface) {
            // Explicit type parameter
            return producedTypedReference.getType();
        } else if (declTypeDecl instanceof ClassOrInterface) {
            return declType;
        } else if ((declTypeDecl instanceof TypeParameter)
                && (flags & TP_TO_BOUND) != 0) {
            if (!declTypeDecl.getSatisfiedTypes().isEmpty()) {
                // use upper bound
                return declTypeDecl.getSatisfiedTypes().get(0);
            }
         }
        return type;
    }

    private boolean isJavaVariadic(Parameter parameter) {
        return parameter.isSequenced()
                && parameter.getContainer() instanceof Method
                && isJavaMethod((Method) parameter.getContainer());
    }

    boolean isJavaMethod(Method method) {
        ClassOrInterface container = Decl.getClassOrInterfaceContainer(method);
        return container != null && !Decl.isCeylon(container);
    }
    
    boolean isJavaCtor(Class cls) {
        return !Decl.isCeylon(cls);
    }

    private ProducedType getTypeForFunctionalParameter(FunctionalParameter fp) {
        java.util.List<ProducedType> typeArgs = new ArrayList<ProducedType>(fp.getTypeParameters().size());
        typeArgs.add(fp.getType());
        for (Parameter parameter : fp.getParameterLists().get(0).getParameters()) {
            typeArgs.add(parameter.getType());
        }
        return typeFact().getCallableType(typeArgs);
    }
    
    /*
     * Annotation generation
     */
    
    List<JCAnnotation> makeAtOverride() {
        return List.<JCAnnotation> of(make().Annotation(makeIdent(syms().overrideType), List.<JCExpression> nil()));
    }

    boolean checkCompilerAnnotations(Tree.Declaration decl){
        boolean old = gen().disableModelAnnotations;
        if(CodegenUtil.hasCompilerAnnotation(decl, "nomodel"))
            gen().disableModelAnnotations  = true;
        return old;
    }

    void resetCompilerAnnotations(boolean value){
        gen().disableModelAnnotations = value;
    }

    private List<JCAnnotation> makeModelAnnotation(Type annotationType, List<JCExpression> annotationArgs) {
        if (gen().disableModelAnnotations)
            return List.nil();
        return List.of(make().Annotation(makeIdent(annotationType), annotationArgs));
    }

    private List<JCAnnotation> makeModelAnnotation(Type annotationType) {
        return makeModelAnnotation(annotationType, List.<JCExpression>nil());
    }

    List<JCAnnotation> makeAtCeylon() {
        JCExpression majorAttribute = make().Assign(naming.makeUnquotedIdent("major"), make().Literal(BINARY_MAJOR_VERSION));
        List<JCExpression> annotationArgs;
        if(BINARY_MINOR_VERSION != 0){
            JCExpression minorAttribute = make().Assign(naming.makeUnquotedIdent("minor"), make().Literal(BINARY_MINOR_VERSION));
            annotationArgs = List.<JCExpression>of(majorAttribute, minorAttribute);
        }else{
            // keep the minor implicit value of 0 to reduce bytecode size
            annotationArgs = List.<JCExpression>of(majorAttribute);
        }
        return makeModelAnnotation(syms().ceylonAtCeylonType, annotationArgs);
    }

    /** Returns a ListBuffer with assignment expressions for the doc, license and by arguments, as well as name,
     * to be used in an annotation which requires them (such as Module and Package) */
    ListBuffer<JCExpression> getLicenseAuthorsDocAnnotationArguments(String name, java.util.List<Annotation> anns) {
        ListBuffer<JCExpression> authors = new ListBuffer<JCTree.JCExpression>();
        ListBuffer<JCExpression> res = new ListBuffer<JCExpression>();
        res.add(make().Assign(naming.makeUnquotedIdent("name"), make().Literal(name)));
        for (Annotation a : anns) {
            if (a.getPositionalArguments() != null && !a.getPositionalArguments().isEmpty()) {
                if (a.getName().equals("doc")) {
                    res.add(make().Assign(naming.makeUnquotedIdent("doc"),
                            make().Literal(Util.unquote(a.getPositionalArguments().get(0)))));
                } else if (a.getName().equals("license")) {
                    res.add(make().Assign(naming.makeUnquotedIdent("license"),
                            make().Literal(Util.unquote(a.getPositionalArguments().get(0)))));
                } else if (a.getName().equals("by")) {
                    for (String author : a.getPositionalArguments()) {
                        authors.add(make().Literal(Util.unquote(author)));
                    }
                }
            }
        }
        if (!authors.isEmpty()) {
            res.add(make().Assign(naming.makeUnquotedIdent("by"), make().NewArray(null, null, authors.toList())));
        }
        return res;
    }

    List<JCAnnotation> makeAtModule(Module module) {
        ListBuffer<JCExpression> imports = new ListBuffer<JCTree.JCExpression>();
        for(ModuleImport dependency : module.getImports()){
            Module dependencyModule = dependency.getModule();
            // do not include the implicit java module as a dependency
            if(dependencyModule.getNameAsString().equals(AbstractModelLoader.JDK_MODULE)
                    // nor ceylon.language
                    || dependencyModule.getNameAsString().equals("ceylon.language"))
                continue;
            JCExpression dependencyName = make().Assign(naming.makeUnquotedIdent("name"),
                    make().Literal(dependencyModule.getNameAsString()));
            JCExpression dependencyVersion = null;
            if(dependencyModule.getVersion() != null)
                dependencyVersion = make().Assign(naming.makeUnquotedIdent("version"),
                        make().Literal(dependencyModule.getVersion()));
            List<JCExpression> spec;
            if(dependencyVersion != null)
                spec = List.<JCExpression>of(dependencyName, dependencyVersion);
            else
                spec = List.<JCExpression>of(dependencyName);
            JCAnnotation atImport = make().Annotation(makeIdent(syms().ceylonAtImportType), spec);
            // TODO : add the export & optional annotations also ?
            imports.add(atImport);
        }

        ListBuffer<JCExpression> annotationArgs = getLicenseAuthorsDocAnnotationArguments(
                module.getNameAsString(), module.getAnnotations());
        annotationArgs.add(make().Assign(naming.makeUnquotedIdent("version"), make().Literal(module.getVersion())));
        annotationArgs.add(make().Assign(naming.makeUnquotedIdent("dependencies"),
                make().NewArray(null, null, imports.toList())));
        return makeModelAnnotation(syms().ceylonAtModuleType, annotationArgs.toList());
    }

    List<JCAnnotation> makeAtPackage(Package pkg) {
        ListBuffer<JCExpression> annotationArgs = getLicenseAuthorsDocAnnotationArguments(
                pkg.getNameAsString(), pkg.getAnnotations());
        annotationArgs.add(make().Assign(naming.makeUnquotedIdent("shared"), makeBoolean(pkg.isShared())));
        return makeModelAnnotation(syms().ceylonAtPackageType, annotationArgs.toList());
    }

    List<JCAnnotation> makeAtName(String name) {
        return makeModelAnnotation(syms().ceylonAtNameType, List.<JCExpression>of(make().Literal(name)));
    }

    List<JCAnnotation> makeAtType(String name) {
        return makeModelAnnotation(syms().ceylonAtTypeInfoType, List.<JCExpression>of(make().Literal(name)));
    }

    List<JCAnnotation> makeAtAlias(ProducedType type) {
        String name = serialiseTypeSignature(type);
        return makeModelAnnotation(syms().ceylonAtAliasType, List.<JCExpression>of(make().Literal(name)));
    }

    final JCAnnotation makeAtTypeParameter(String name, java.util.List<ProducedType> satisfiedTypes, boolean covariant, boolean contravariant) {
        JCExpression nameAttribute = make().Assign(naming.makeUnquotedIdent("value"), make().Literal(name));
        // variance
        String variance = "NONE";
        if(covariant)
            variance = "OUT";
        else if(contravariant)
            variance = "IN";
        JCExpression varianceAttribute = make().Assign(naming.makeUnquotedIdent("variance"), 
                make().Select(makeIdent(syms().ceylonVarianceType), names().fromString(variance)));
        // upper bounds
        ListBuffer<JCExpression> upperBounds = new ListBuffer<JCTree.JCExpression>();
        for(ProducedType satisfiedType : satisfiedTypes){
            String type = serialiseTypeSignature(satisfiedType);
            upperBounds.append(make().Literal(type));
        }
        JCExpression satisfiesAttribute = make().Assign(naming.makeUnquotedIdent("satisfies"), 
                make().NewArray(null, null, upperBounds.toList()));
        // all done
        return make().Annotation(makeIdent(syms().ceylonAtTypeParameter), 
                List.<JCExpression>of(nameAttribute, varianceAttribute, satisfiesAttribute));
    }

    List<JCAnnotation> makeAtTypeParameters(List<JCExpression> typeParameters) {
        JCExpression value = make().NewArray(null, null, typeParameters);
        return makeModelAnnotation(syms().ceylonAtTypeParameters, List.of(value));
    }

    List<JCAnnotation> makeAtSequenced() {
        return makeModelAnnotation(syms().ceylonAtSequencedType);
    }

    List<JCAnnotation> makeAtDefaulted() {
        return makeModelAnnotation(syms().ceylonAtDefaultedType);
    }

    List<JCAnnotation> makeAtAttribute() {
        return makeModelAnnotation(syms().ceylonAtAttributeType);
    }

    List<JCAnnotation> makeAtMethod() {
        return makeModelAnnotation(syms().ceylonAtMethodType);
    }

    List<JCAnnotation> makeAtObject() {
        return makeModelAnnotation(syms().ceylonAtObjectType);
    }

    List<JCAnnotation> makeAtClass(ProducedType extendedType) {
        List<JCExpression> attributes = List.nil();
        if(!extendedType.isExactly(typeFact.getIdentifiableObjectDeclaration().getType())){
            JCExpression extendsAttribute = make().Assign(naming.makeUnquotedIdent("extendsType"), 
                    make().Literal(serialiseTypeSignature(extendedType)));
            attributes = attributes.prepend(extendsAttribute);
        }
        return makeModelAnnotation(syms().ceylonAtClassType, attributes);
    }

    List<JCAnnotation> makeAtSatisfiedTypes(java.util.List<ProducedType> satisfiedTypes) {
        return makeTypesListAnnotation(syms().ceylonAtSatisfiedTypes, satisfiedTypes);
    }

    List<JCAnnotation> makeAtCaseTypes(java.util.List<ProducedType> caseTypes) {
        return makeTypesListAnnotation(syms().ceylonAtCaseTypes, caseTypes);
    }

    private List<JCAnnotation> makeTypesListAnnotation(Type annotationType, java.util.List<ProducedType> types) {
        if(types.isEmpty())
            return List.nil();
        ListBuffer<JCExpression> upperBounds = new ListBuffer<JCTree.JCExpression>();
        for(ProducedType type : types){
            String typeSig = serialiseTypeSignature(type);
            upperBounds.append(make().Literal(typeSig));
        }
        JCExpression caseAttribute = make().Assign(naming.makeUnquotedIdent("value"), 
                make().NewArray(null, null, upperBounds.toList()));
        
        return makeModelAnnotation(annotationType, List.of(caseAttribute));
    }

    List<JCAnnotation> makeAtCaseTypes(ProducedType ofType) {
        if(ofType == null)
            return List.nil();
        String typeSig = serialiseTypeSignature(ofType);
        JCExpression caseAttribute = make().Assign(naming.makeUnquotedIdent("of"), 
                make().Literal(typeSig));
        
        return makeModelAnnotation(syms().ceylonAtCaseTypes, List.of(caseAttribute));
    }

    List<JCAnnotation> makeAtIgnore() {
        return makeModelAnnotation(syms().ceylonAtIgnore);
    }

    List<JCAnnotation> makeAtAnnotations(java.util.List<Annotation> annotations) {
        if(annotations == null || annotations.isEmpty())
            return List.nil();
        ListBuffer<JCExpression> array = new ListBuffer<JCTree.JCExpression>();
        for(Annotation annotation : annotations){
            array.append(makeAtAnnotation(annotation));
        }
        JCExpression annotationsAttribute = make().Assign(naming.makeUnquotedIdent("value"), 
                make().NewArray(null, null, array.toList()));
        
        return makeModelAnnotation(syms().ceylonAtAnnotationsType, List.of(annotationsAttribute));
    }

    private JCExpression makeAtAnnotation(Annotation annotation) {
        JCExpression valueAttribute = make().Assign(naming.makeUnquotedIdent("value"), 
                                                    make().Literal(annotation.getName()));
        List<JCExpression> attributes;
        if(!annotation.getPositionalArguments().isEmpty()){
            java.util.List<String> positionalArguments = annotation.getPositionalArguments();
            ListBuffer<JCExpression> array = new ListBuffer<JCTree.JCExpression>();
            for(String val : positionalArguments)
                array.add(make().Literal(val));
            JCExpression argumentsAttribute = make().Assign(naming.makeUnquotedIdent("arguments"), 
                                                            make().NewArray(null, null, array.toList()));
            attributes = List.of(valueAttribute, argumentsAttribute);
        }else if(!annotation.getNamedArguments().isEmpty()){
            Map<String, String> namedArguments = annotation.getNamedArguments();
            ListBuffer<JCExpression> array = new ListBuffer<JCTree.JCExpression>();
            for(Entry<String, String> entry : namedArguments.entrySet()){
                JCExpression argNameAttribute = make().Assign(naming.makeUnquotedIdent("name"), 
                        make().Literal(entry.getKey()));
                JCExpression argValueAttribute = make().Assign(naming.makeUnquotedIdent("value"), 
                        make().Literal(entry.getValue()));

                JCAnnotation namedArg = make().Annotation(makeIdent(syms().ceylonAtNamedArgumentType), 
                                                          List.of(argNameAttribute, argValueAttribute));
                array.add(namedArg);
            }
            JCExpression argumentsAttribute = make().Assign(naming.makeUnquotedIdent("namedArguments"), 
                    make().NewArray(null, null, array.toList()));
            attributes = List.of(valueAttribute, argumentsAttribute);
        }else
            attributes = List.of(valueAttribute);

        return make().Annotation(makeIdent(syms().ceylonAtAnnotationType), attributes);
    }

    /** Determine whether the given declaration requires a 
     * {@code @TypeInfo} annotation 
     */
    private boolean needsJavaTypeAnnotations(Declaration decl) {
        Declaration reqdecl = decl;
        if (reqdecl instanceof Parameter) {
            Parameter p = (Parameter)reqdecl;
            reqdecl = p.getDeclaration();
        }
        if (reqdecl instanceof TypeDeclaration) {
            return true;
        } else { // TypedDeclaration
            return !Decl.isLocal(reqdecl);
        }
    }

    List<JCTree.JCAnnotation> makeJavaTypeAnnotations(TypedDeclaration decl) {
        if(decl == null || decl.getType() == null)
            return List.nil();
        ProducedType type;
        if (decl instanceof FunctionalParameter) {
            type = getTypeForFunctionalParameter((FunctionalParameter)decl);
        } else {
            type = decl.getType();
        }
        return makeJavaTypeAnnotations(type, needsJavaTypeAnnotations(decl));
    }

    private List<JCTree.JCAnnotation> makeJavaTypeAnnotations(ProducedType type, boolean required) {
        if (!required)
            return List.nil();
        // Add the original type to the annotations
        return makeAtType(serialiseTypeSignature(type));
    }
    
    private String serialiseTypeSignature(ProducedType type){
        return type.getProducedTypeQualifiedName();

    }
    
    /*
     * Boxing
     */
    public enum BoxingStrategy {
        UNBOXED, BOXED, INDIFFERENT;
    }

    public boolean canUnbox(ProducedType type){
        // all the rest is boxed
        return isCeylonBasicType(type) || isJavaString(type);
    }
    
    JCExpression boxUnboxIfNecessary(JCExpression javaExpr, Tree.Term expr,
            ProducedType exprType,
            BoxingStrategy boxingStrategy) {
        boolean exprBoxed = !CodegenUtil.isUnBoxed(expr);
        return boxUnboxIfNecessary(javaExpr, exprBoxed, exprType, boxingStrategy);
    }
    
    JCExpression boxUnboxIfNecessary(JCExpression javaExpr, boolean exprBoxed,
            ProducedType exprType,
            BoxingStrategy boxingStrategy) {
        if(boxingStrategy == BoxingStrategy.INDIFFERENT)
            return javaExpr;
        boolean targetBoxed = boxingStrategy == BoxingStrategy.BOXED;
        // only box if the two differ
        if(targetBoxed == exprBoxed)
            return javaExpr;
        if (targetBoxed) {
            // box
            javaExpr = boxType(javaExpr, exprType);
        } else {
            // unbox
            javaExpr = unboxType(javaExpr, exprType);
        }
        return javaExpr;
    }
    
    boolean isTypeParameter(ProducedType type) {
        if (typeFact().isOptionalType(type)) {
            type = type.minus(typeFact().getNothingDeclaration());
        } 
        return type.getDeclaration() instanceof TypeParameter;
    }
    
    JCExpression unboxType(JCExpression expr, ProducedType targetType) {
        if (isCeylonInteger(targetType)) {
            expr = unboxInteger(expr);
        } else if (isCeylonFloat(targetType)) {
            expr = unboxFloat(expr);
        } else if (isCeylonString(targetType)) {
            expr = unboxString(expr);
        } else if (isCeylonCharacter(targetType)) {
            boolean isJavaCharacter = targetType.getUnderlyingType() != null;
            expr = unboxCharacter(expr, isJavaCharacter);
        } else if (isCeylonBoolean(targetType)) {
            expr = unboxBoolean(expr);
        } else if (isCeylonArray(targetType)) {
            expr = unboxArray(expr);
        } else if (isOptional(targetType)) {
            targetType = typeFact().getDefiniteType(targetType);
            if (isCeylonString(targetType)){
                expr = unboxOptionalString(expr);
            }
        }
        return expr;
    }

    JCExpression boxType(JCExpression expr, ProducedType exprType) {
        if (isCeylonInteger(exprType)) {
            expr = boxInteger(expr);
        } else if (isCeylonFloat(exprType)) {
            expr = boxFloat(expr);
        } else if (isCeylonString(exprType)) {
            expr = boxString(expr);
        } else if (isCeylonCharacter(exprType)) {
            expr = boxCharacter(expr);
        } else if (isCeylonBoolean(exprType)) {
            expr = boxBoolean(expr);
        } else if (isCeylonArray(exprType)) {
            expr = boxArray(expr, typeFact.getArrayElementType(exprType));
        } else if (isVoid(exprType)) {
            expr = make().LetExpr(List.<JCStatement>of(make().Exec(expr)), makeNull());
        }
        return expr;
    }
    
    private JCTree.JCMethodInvocation boxInteger(JCExpression value) {
        return makeBoxType(value, syms().ceylonIntegerType);
    }
    
    private JCTree.JCMethodInvocation boxFloat(JCExpression value) {
        return makeBoxType(value, syms().ceylonFloatType);
    }
    
    private JCTree.JCMethodInvocation boxString(JCExpression value) {
        return makeBoxType(value, syms().ceylonStringType);
    }
    
    private JCTree.JCMethodInvocation boxCharacter(JCExpression value) {
        return makeBoxType(value, syms().ceylonCharacterType);
    }
    
    private JCTree.JCMethodInvocation boxBoolean(JCExpression value) {
        return makeBoxType(value, syms().ceylonBooleanType);
    }
    
    private JCTree.JCMethodInvocation boxArray(JCExpression value, ProducedType type) {
        JCExpression typeExpr = makeJavaType(type, JT_TYPE_ARGUMENT);
        return make().Apply(List.<JCExpression>of(typeExpr), makeSelect(makeIdent(syms().ceylonArrayType), "instance"), List.<JCExpression>of(value));
    }
    
    private JCTree.JCMethodInvocation makeBoxType(JCExpression value, Type type) {
        return make().Apply(null, makeSelect(makeIdent(type), "instance"), List.<JCExpression>of(value));
    }
    
    private JCTree.JCMethodInvocation unboxInteger(JCExpression value) {
        return makeUnboxType(value, "longValue");
    }
    
    private JCTree.JCMethodInvocation unboxFloat(JCExpression value) {
        return makeUnboxType(value, "doubleValue");
    }
    
    private JCExpression unboxString(JCExpression value) {
        if (isStringLiteral(value)) {
            // If it's already a String literal, why call .toString on it?
            return value;
        }
        return makeUnboxType(value, "toString");
    }

    private boolean isStringLiteral(JCExpression value) {
        return value instanceof JCLiteral
                && ((JCLiteral)value).value instanceof String;
    }
    
    private JCExpression unboxOptionalString(JCExpression value){
        if (isStringLiteral(value)) {
            // If it's already a String literal, why call .toString on it?
            return value;
        }
        Naming.SyntheticName name = naming.temp();
        JCExpression type = makeJavaType(typeFact().getStringDeclaration().getType(), JT_NO_PRIMITIVES);
        JCExpression expr = make().Conditional(make().Binary(JCTree.NE, name.makeIdent(), makeNull()), 
                unboxString(name.makeIdent()),
                makeNull());
        return makeLetExpr(name, null, type, value, expr);
    }
    
    private JCTree.JCMethodInvocation unboxCharacter(JCExpression value, boolean isJava) {
        return makeUnboxType(value, isJava ? "charValue" : "intValue");
    }
    
    private JCTree.JCMethodInvocation unboxBoolean(JCExpression value) {
        return makeUnboxType(value, "booleanValue");
    }
    
    private JCTree.JCMethodInvocation unboxArray(JCExpression value) {
        return makeUnboxType(value, "toArray");
    }
    
    private JCTree.JCMethodInvocation makeUnboxType(JCExpression value, String unboxMethodName) {
        return make().Apply(null, makeSelect(value, unboxMethodName), List.<JCExpression>nil());
    }

    /*
     * Sequences
     */
    
    /**
     * Returns a JCExpression along the lines of 
     * {@code new ArraySequence<seqElemType>(list...)}
     * @param elems The elements in the sequence
     * @param seqElemType The sequence type parameter
     * @param makeJavaTypeOpts The option flags to pass to makeJavaType().
     * @return a JCExpression
     * @see #makeSequenceRaw(java.util.List)
     */
    JCExpression makeSequence(List<JCExpression> elems, ProducedType seqElemType, int makeJavaTypeOpts) {
        ProducedType seqType = typeFact().getDefaultSequenceType(seqElemType);
        JCExpression typeExpr = makeJavaType(seqType, makeJavaTypeOpts);
        return makeNewClass(typeExpr, elems);
    }
    
    /**
     * Returns a JCExpression along the lines of 
     * {@code new ArraySequence<seqElemType>(list...)}
     * @param list The elements in the sequence
     * @param seqElemType The sequence type parameter
     * @return a JCExpression
     * @see #makeSequenceRaw(java.util.List)
     */
    JCExpression makeSequence(java.util.List<Expression> list, ProducedType seqElemType) {
        ListBuffer<JCExpression> elems = new ListBuffer<JCExpression>();
        if (list.size() != 1 || !isNothing(list.get(0).getTypeModel())) {
            for (Expression expr : list) {
                // no need for erasure casts here
                elems.append(expressionGen().transformExpression(expr));
            }
        } else {
            // Resolve the ambiguous situation of being passed a single "null" argument
            elems.append(make().TypeCast(syms().objectType, expressionGen().transformExpression(list.get(0))));
        }
        return makeSequence(elems.toList(), seqElemType, CeylonTransformer.JT_CLASS_NEW);
    }
    
    /**
     * Returns a JCExpression along the lines of 
     * {@code new ArraySequence(list...)}
     * @param list The elements in the sequence
     * @return a JCExpression
     * @see #makeSequence(java.util.List, ProducedType)
     */
    JCExpression makeSequenceRaw(java.util.List<Expression> list) {
        ListBuffer<JCExpression> elems = new ListBuffer<JCExpression>();
        for (Expression expr : list) {
            // no need for erasure casts here
            elems.append(expressionGen().transformExpression(expr));
        }
        return makeSequenceRaw(elems.toList());
    }
    
    JCExpression makeSequenceRaw(List<JCExpression> elems) {
        return makeSequence(elems, typeFact().getObjectDeclaration().getType(), CeylonTransformer.JT_RAW);
    }
    
    JCExpression makeEmptyAsIterable(boolean needsCast){
        if(needsCast)
            return make().TypeCast(makeJavaType(typeFact().getIterableDeclaration().getType(), JT_RAW), makeEmpty());
        return makeEmpty();
    }
    
    JCExpression makeEmpty() {
        return make().Apply(
                List.<JCTree.JCExpression>nil(),
                naming.makeLanguageValue("empty"),
                List.<JCTree.JCExpression>nil());
    }
    
    JCExpression makeFinished() {
        return make().Apply(
                List.<JCTree.JCExpression>nil(),
                naming.makeLanguageValue("exhausted"),
                List.<JCTree.JCExpression>nil());
    }

    /**
     * Turns a sequence into a Java array
     * @param expr the sequence
     * @param sequenceType the sequence type
     * @param boxingStrategy the boxing strategy for expr
     * @param exprType 
     */
    JCExpression sequenceToJavaArray(JCExpression expr, ProducedType sequenceType, BoxingStrategy boxingStrategy, ProducedType exprType) {
        String methodName = null;
        // find the sequence element type
        ProducedType type = typeFact().getIteratedType(sequenceType);
        if(boxingStrategy == BoxingStrategy.UNBOXED){
            if(isCeylonInteger(type)){
                if("byte".equals(type.getUnderlyingType()))
                    methodName = "toByteArray";
                else if("short".equals(type.getUnderlyingType()))
                    methodName = "toShortArray";
                else if("int".equals(type.getUnderlyingType()))
                    methodName = "toIntArray";
                else
                    methodName = "toLongArray";
            }else if(isCeylonFloat(type)){
                if("float".equals(type.getUnderlyingType()))
                    methodName = "toFloatArray";
                else
                    methodName = "toDoubleArray";
            } else if (isCeylonCharacter(type)) {
                if ("char".equals(type.getUnderlyingType()))
                    methodName = "toCharArray";
                // else it must be boxed, right?
            } else if (isCeylonBoolean(type)) {
                methodName = "toBooleanArray";
            } else if (isJavaString(type)) {
                methodName = "toJavaStringArray";
            } else if (isCeylonString(type)) {
                return objectVariadicToJavaArray(type, sequenceType, expr, exprType);
            }
            if(methodName == null){
                log.error("ceylon", "Don't know how to convert sequences of type "+type+" to Java array (This is a compiler bug)");
                return expr;
            }
            return makeUtilInvocation(methodName, List.of(expr), null);
        }else{
            return objectVariadicToJavaArray(type, sequenceType, expr, exprType);
        }
    }

    private JCExpression objectVariadicToJavaArray(ProducedType type,
            ProducedType sequenceType, JCExpression expr, ProducedType exprType) {
        if(typeFact().getFixedSizedType(exprType) != null){
            return objectFixedSizedToJavaArray(type, expr);
        }
        return objectIterableToJavaArray(type, typeFact().getIterableType(sequenceType), expr);
    }

    private JCExpression objectIterableToJavaArray(ProducedType type,
            ProducedType iterableType, JCExpression expr) {
        JCExpression klass = makeJavaType(type, AbstractTransformer.JT_CLASS_NEW | AbstractTransformer.JT_NO_PRIMITIVES);
        JCExpression klassLiteral = make().Select(klass, names().fromString("class"));
        return makeUtilInvocation("toArray", List.of(expr, klassLiteral), null);
    }
    
    private JCExpression objectFixedSizedToJavaArray(ProducedType type, JCExpression expr) {
        JCExpression klass1 = makeJavaType(type, AbstractTransformer.JT_CLASS_NEW | AbstractTransformer.JT_NO_PRIMITIVES);
        JCExpression klass2 = makeJavaType(type, AbstractTransformer.JT_CLASS_NEW | AbstractTransformer.JT_NO_PRIMITIVES);
        Naming.SyntheticName seqName = naming.temp().suffixedBy("$0");

        ProducedType fixedSizedType = typeFact().getFixedSizedDeclaration().getProducedType(null, Arrays.asList(type));
        JCExpression seqTypeExpr1 = makeJavaType(fixedSizedType);
        JCExpression seqTypeExpr2 = makeJavaType(fixedSizedType);

        JCExpression sizeExpr = make().Apply(List.<JCExpression>nil(), 
                make().Select(seqName.makeIdent(), names().fromString("getSize")),
                List.<JCExpression>nil());
        sizeExpr = make().TypeCast(syms().intType, sizeExpr);

        JCExpression newArrayExpr = make().NewArray(klass1, List.of(sizeExpr), null);
        JCExpression sequenceToArrayExpr = makeUtilInvocation("toArray", 
                List.of(seqName.makeIdent(), 
                        newArrayExpr),
                List.of(klass2));
        
        // since T[] is erased to Iterable<T> we probably need a cast to FixedSized<T>
        JCExpression castedExpr = make().TypeCast(seqTypeExpr2, expr);
        
        return makeLetExpr(seqName, List.<JCStatement>nil(), seqTypeExpr1, castedExpr, sequenceToArrayExpr);
    }

    // Creates comparisons of expressions against types
    JCExpression makeTypeTest(JCExpression firstTimeExpr, Naming.SyntheticName varName, ProducedType type) {
        JCExpression result = null;
        if (typeFact().isUnion(type)) {
            UnionType union = (UnionType)type.getDeclaration();
            for (ProducedType pt : union.getCaseTypes()) {
                JCExpression partExpr = makeTypeTest(firstTimeExpr, varName, pt);
                firstTimeExpr = null;
                if (result == null) {
                    result = partExpr;
                } else {
                    result = make().Binary(JCTree.OR, result, partExpr);
                }
            }
        } else if (typeFact().isIntersection(type)) {
            IntersectionType union = (IntersectionType)type.getDeclaration();
            for (ProducedType pt : union.getSatisfiedTypes()) {
                JCExpression partExpr = makeTypeTest(firstTimeExpr, varName, pt);
                firstTimeExpr = null;
                if (result == null) {
                    result = partExpr;
                } else {
                    result = make().Binary(JCTree.AND, result, partExpr);
                }
            }
        } else {
            JCExpression varExpr = firstTimeExpr != null ? firstTimeExpr : varName.makeIdent();
            if (isVoid(type)){
                // everything is Void, it's the root of the hierarchy
                return makeIgnoredEvalAndReturn(varExpr, makeBoolean(true));
            } else if (type.isExactly(typeFact().getNothingDeclaration().getType())){
                // is Nothing => is null
                return make().Binary(JCTree.EQ, varExpr, makeNull());
            } else if (type.isExactly(typeFact().getObjectDeclaration().getType())){
                // is Object => is not null
                return make().Binary(JCTree.NE, varExpr, makeNull());
            } else if (type.isExactly(typeFact().getIdentifiableDeclaration().getType())){
                // it's erased
                return makeUtilInvocation("isIdentifiable", List.of(varExpr), null);
            } else if (type.isExactly(typeFact().getIdentifiableObjectDeclaration().getType())){
                // it's erased
                return makeUtilInvocation("isIdentifiableObject", List.of(varExpr), null);
            } else if (type.getDeclaration() instanceof BottomType){
                // nothing is Bottom
                return makeIgnoredEvalAndReturn(varExpr, makeBoolean(false));
            } else {
                JCExpression rawTypeExpr = makeJavaType(type, JT_NO_PRIMITIVES | JT_RAW);
                result = make().TypeTest(varExpr, rawTypeExpr);
            }
        }
        return result;
    }

    JCExpression makeNonEmptyTest(JCExpression firstTimeExpr, Naming.SyntheticName varName) {
        Interface fixedSize = typeFact().getFixedSizedDeclaration();
        JCExpression test = makeTypeTest(firstTimeExpr, varName, fixedSize.getType());
        JCExpression fixedSizeType = makeJavaType(fixedSize.getType(), JT_NO_PRIMITIVES | JT_RAW);
        JCExpression nonEmpty = makeNonEmptyTest(make().TypeCast(fixedSizeType, varName.makeIdent()));
        return make().Binary(JCTree.AND, test, nonEmpty);
    }

    JCExpression makeNonEmptyTest(JCExpression expr){
        JCExpression getEmptyCall = make().Select(expr, names().fromString("getEmpty"));
        JCExpression empty = make().Apply(List.<JCExpression>nil(), getEmptyCall, List.<JCExpression>nil());
        return make().Unary(JCTree.NOT, empty);
    }
    
    /**
     * Invokes a static method of the Util helper class
     * @param methodName name of the method
     * @param params parameters
     * @return the invocation AST
     */
    public JCExpression makeUtilInvocation(String methodName, List<JCExpression> params, List<JCExpression> typeParams) {
        return make().Apply(typeParams, 
                            make().Select(make().QualIdent(syms().ceylonUtilType.tsym), 
                                          names().fromString(methodName)), 
                            params);
    }

    private LetExpr makeIgnoredEvalAndReturn(JCExpression toEval, JCExpression toReturn){
        // define a variable of type j.l.Object to hold the result of the evaluation
        JCVariableDecl def = makeVar(naming.temp(), make().Type(syms().objectType), toEval);
        // then ignore this result and return something else
        return make().LetExpr(def, toReturn);

    }
    
    JCExpression makeErroneous() {
        return makeErroneous(null);
    }
    
    /**
     * Makes an 'erroneous' AST node with no message
     */
    JCExpression makeErroneous(Node node) {
        return makeErroneous(node, null, List.<JCTree>nil());
    }
    
    /**
     * Makes an 'erroneous' AST node with a message to be logged as an error
     */
    JCExpression makeErroneous(Node node, String message) {
        return makeErroneous(node, message, List.<JCTree>nil());
    }
    
    /**
     * Makes an 'erroneous' AST node with a message to be logged as an error
     */
    JCExpression makeErroneous(Node node, String message, List<? extends JCTree> errs) {
        if (node != null) {
            at(node);
        }
        if (message != null) {
            if (node != null) {
                log.error(getPosition(node), "ceylon", message);
            } else {
                log.error("ceylon", message);
            }
        }
        return make().Erroneous(errs);
    }

    private JCTypeParameter makeTypeParameter(String name, java.util.List<ProducedType> satisfiedTypes, boolean covariant, boolean contravariant) {
        ListBuffer<JCExpression> bounds = new ListBuffer<JCExpression>();
        for (ProducedType t : satisfiedTypes) {
            if (!willEraseToObject(t)) {
                bounds.append(makeJavaType(t, AbstractTransformer.JT_NO_PRIMITIVES));
            }
        }
        return make().TypeParameter(names().fromString(name), bounds.toList());
    }

    JCTypeParameter makeTypeParameter(TypeParameter declarationModel) {
        TypeParameter typeParameterForBounds = declarationModel;
        // special case for method refinenement where Java doesn't let us refine the parameter bounds
        if(declarationModel.getContainer() instanceof Method){
            Method method = (Method) declarationModel.getContainer();
            Method refinedMethod = (Method) method.getRefinedDeclaration();
            if(method != refinedMethod){
                // find the param index
                int index = method.getTypeParameters().indexOf(declarationModel);
                if(index == -1){
                    log.error("Failed to find type parameter index: "+declarationModel.getName());
                }else if(refinedMethod.getTypeParameters().size() > index){
                    // ignore smaller index than size since the typechecker would have found the error
                    TypeParameter refinedTP = refinedMethod.getTypeParameters().get(index);
                    if(!haveSameBounds(declarationModel, refinedTP)){
                        typeParameterForBounds = refinedTP;
                    }
                }
            }
        }
        return makeTypeParameter(declarationModel.getName(), 
                typeParameterForBounds.getSatisfiedTypes(),
                typeParameterForBounds.isCovariant(),
                typeParameterForBounds.isContravariant());
    }
    
    JCTypeParameter makeTypeParameter(Tree.TypeParameterDeclaration param) {
        at(param);
        return makeTypeParameter(param.getDeclarationModel());
    }

    JCAnnotation makeAtTypeParameter(TypeParameter declarationModel) {
        return makeAtTypeParameter(declarationModel.getName(), 
                declarationModel.getSatisfiedTypes(),
                declarationModel.isCovariant(),
                declarationModel.isContravariant());
    }
    
    JCAnnotation makeAtTypeParameter(Tree.TypeParameterDeclaration param) {
        at(param);
        return makeAtTypeParameter(param.getDeclarationModel());
    }
    
    final List<JCExpression> typeArguments(Tree.AnyMethod method) {
        return typeArguments(method.getDeclarationModel());
    }
    
    final List<JCExpression> typeArguments(Tree.AnyClass type) {
        return typeArguments(type);
    }
    
    final List<JCExpression> typeArguments(Functional method) {
        return typeArguments(method.getTypeParameters(), method.getType().getTypeArguments());
    }
    
    final List<JCExpression> typeArguments(Tree.ClassOrInterface type) {
        return typeArguments(type.getDeclarationModel().getTypeParameters(), type.getDeclarationModel().getType().getTypeArguments());
    }
    
    final List<JCExpression> typeArguments(java.util.List<TypeParameter> typeParameters, Map<TypeParameter, ProducedType> typeArguments) {
        ListBuffer<JCExpression> typeArgs = ListBuffer.<JCExpression>lb();
        for (TypeParameter tp : typeParameters) {
            ProducedType type = typeArguments.get(tp);
            if (type != null) {
                typeArgs.append(makeJavaType(type, JT_TYPE_ARGUMENT));
            } else {
                typeArgs.append(makeJavaType(tp.getType(), JT_TYPE_ARGUMENT));
            }
        }
        return typeArgs.toList();
    }
    /**
     * Returns the name of the field in classes which holds the companion 
     * instance.
     */
    final String getCompanionFieldName(Interface def) {
        return naming.getCompanionFieldName(def);
    }
    /** 
     * Returns the name of the method in interfaces and classes used to get 
     * the companion instance.
     */
    final String getCompanionAccessorName(Interface def) {
        return naming.getCompanionAccessorName(def);
    }
    
    private int getPosition(Node node) {
        int pos = getMap().getStartPosition(node.getToken().getLine())
                + node.getToken().getCharPositionInLine();
                log.useSource(gen().getFileObject());
        return pos;
    }

}
