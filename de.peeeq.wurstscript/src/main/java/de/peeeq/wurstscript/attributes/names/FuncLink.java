package de.peeeq.wurstscript.attributes.names;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.parser.WPos;
import de.peeeq.wurstscript.types.WurstType;
import de.peeeq.wurstscript.types.WurstTypeBoundTypeParam;
import de.peeeq.wurstscript.types.WurstTypeVararg;
import de.peeeq.wurstscript.utils.Utils;
import fj.data.TreeMap;
import org.eclipse.jdt.annotation.Nullable;

import java.util.List;
import java.util.stream.Collectors;


public class FuncLink extends DefLink {
    private final Symbol<FunctionDefinition> def;
    private final WurstType returnType;
    private final List<String> parameterNames;
    private final List<WurstType> parameterTypes;

    public FuncLink(Visibility visibility, WScope definedIn, List<TypeParamDef> typeParams,
                    @Nullable WurstType receiverType, Symbol<FunctionDefinition> def, List<String> parameterNames, List<WurstType> parameterTypes, WurstType returnType) {
        super(visibility, definedIn, typeParams, receiverType);
        this.def = def;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
        this.parameterNames = parameterNames;
    }

    public static FuncLink create(FunctionDefinition func, WScope definedIn) {
        Visibility visibiliy = calcVisibility(definedIn, func);
        List<TypeParamDef> typeParams = typeParams(func).collect(Collectors.toList());
        List<String> lParameterNames = func.getParameters().stream()
                .map(WParameter::getName)
                .collect(Collectors.toList());
        List<WurstType> lParameterTypes = func.getParameters().stream()
                .map(WParameter::attrTyp)
                .collect(Collectors.toList());
        WurstType lreturnType = func.attrReturnTyp();
        WurstType lreceiverType = calcReceiverType(definedIn, func);
        return new FuncLink(visibiliy, definedIn, typeParams, lreceiverType, Symbol.fromDef(func), lParameterNames, lParameterTypes, lreturnType);
    }


    private static @Nullable WurstType calcReceiverType(WScope definedIn, NameDef nameDef) {
        if (nameDef instanceof ExtensionFuncDef) {
            ExtensionFuncDef exF = (ExtensionFuncDef) nameDef;
            return exF.getExtendedType().attrTyp().dynamic();
        } else if (nameDef instanceof FuncDef) {
            return getReceiverType(definedIn);
        }
        return null;
    }


    public String getName() {
        return def.getName();
    }


    @Override
    public FunctionDefinition getDef(WurstModel m) {
        return def.getDef(m);
    }


    public FuncLink withVisibility(Visibility newVis) {
        if (newVis == getVisibility()) {
            return this;
        }
        return new FuncLink(newVis, getDefinedIn(), getTypeParams(), getReceiverType(), def, parameterNames, parameterTypes, returnType);
    }


    public String getParameterDescription() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < getParameterTypes().size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(getParameterType(i).toString());
            if (i < getParameterNames().size()) {
                sb.append(" ");
                sb.append(getParameterName(i));
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(getVisibility());
        result.append(" ");
        if (getReceiverType() != null) {
            result.append(getReceiverType()).append(".");
        }
        result.append(getName());
        if (!typeParams.isEmpty()) {
            result.append("<");
            result.append(typeParams.stream()
                    .map(TypeParamDef::getName)
                    .collect(Collectors.joining(", ")));
            result.append(">");
        }
        result.append("(");
        result.append(getParameterDescription());
        result.append(") returns ");
        result.append(returnType);
//        WPos src = def.attrSource();
//        result.append(" (")
//                .append(src.getFile())
//                .append(":")
//                .append(src.getLine())
//                .append(")");
        return result.toString();
    }


    @Override
    public NameLinkType getType() {
        return NameLinkType.FUNCTION;
    }

    public FuncLink withTypeArgBinding(Element context, TreeMap<TypeParamDef, WurstTypeBoundTypeParam> binding) {
        if (binding.isEmpty()) {
            return this;
        }
        WurstType newReturnType = adjustType(context, getReturnType(), binding);
        boolean changed = newReturnType != returnType;
        List<WurstType> newParamTypes;
        if (getParameterTypes().isEmpty()) {
            newParamTypes = getParameterTypes();
        } else {
            newParamTypes = Lists.newArrayListWithCapacity(getParameterTypes().size());
            for (WurstType pt : getParameterTypes()) {
                WurstType newPt = adjustType(context, pt, binding);
                if (newPt != pt) {
                    changed = true;
                }
                newParamTypes.add(newPt);
            }
        }
        WurstType newReceiverType = adjustType(context, getReceiverType(), binding);
        changed = changed || newReceiverType != getReceiverType();
        if (changed) {
            // remove type parameters that are now bound:
            List<TypeParamDef> newTypeParams = getTypeParams().stream()
                    .filter(tp -> !binding.contains(tp))
                    .collect(Collectors.toList());
            return new FuncLink(getVisibility(), getDefinedIn(), newTypeParams, newReceiverType, def, parameterNames, newParamTypes, newReturnType);
        } else {
            return this;
        }
    }

    @Override
    public DefLink withGenericTypeParams(List<TypeParamDef> typeParams) {
        if (typeParams.isEmpty()) {
            return this;
        }
        ImmutableList<TypeParamDef> newTypeParams = Utils.concatLists(getTypeParams(), typeParams);
        return new FuncLink(getVisibility(), getDefinedIn(), newTypeParams, getReceiverType(), def, parameterNames, parameterTypes, returnType);
    }

    @Override
    public WurstType getTyp() {
        return getReturnType();
    }

    @Override
    public FuncLink withDef(NameDef def) {
        Symbol<FunctionDefinition> defSym = Symbol.fromDef((FunctionDefinition) def);
        return new FuncLink(getVisibility(), getDefinedIn(), getTypeParams(), getReceiverType(), defSym, getParameterNames(), getParameterTypes(), getReturnType());
    }

    private WurstType adjustType(Element context, WurstType t, TreeMap<TypeParamDef, WurstTypeBoundTypeParam> binding) {
        if (t == null) {
            return null;
        }
        return t.setTypeArgs(binding);
    }


    public WurstType getReturnType() {
        return returnType;
    }

    public List<WurstType> getParameterTypes() {
        return parameterTypes;
    }

    public WurstType getParameterType(int i) {
        List<WurstType> parameterTypes = getParameterTypes();
        if (isVarargMethod() && i >= parameterTypes.size() - 1) {
            return ((WurstTypeVararg) parameterTypes.get(parameterTypes.size() - 1)).getBaseType();
        }
        return parameterTypes.get(i);
    }

    public String getParameterName(int i) {
        if (isVarargMethod() && i >= parameterNames.size() - 1) {
            return parameterNames.get(parameterNames.size() - 1);
        }
        return parameterNames.get(i);
    }

    public boolean isVarargMethod() {
        List<WurstType> parameterTypes = getParameterTypes();
        if (parameterTypes.size() > 0) {
            return parameterTypes.get(parameterTypes.size() - 1) instanceof WurstTypeVararg;
        }
        return false;
    }

    public FuncLink withConfigDef(WurstModel model) {
        FunctionDefinition def = (FunctionDefinition) this.getDef(model).attrConfigActualNameDef();
        return new FuncLink(getVisibility(), getDefinedIn(), getTypeParams(), getReceiverType(), Symbol.fromDef(def), parameterNames, parameterTypes, returnType);
    }

    public FuncLink hidingPrivate() {
        return (FuncLink) super.hidingPrivate();
    }

    public FuncLink hidingPrivateAndProtected() {
        return (FuncLink) super.hidingPrivateAndProtected();
    }


    public List<String> getParameterNames() {
        return parameterNames;
    }

    public FuncLink adaptToReceiverType(WurstType receiverType, WurstModel m) {
        return (FuncLink) super.adaptToReceiverType(receiverType, m);
    }


    public String printFunctionTemplate() {
        StringBuilder res = new StringBuilder("function ");
        res.append(getName());
        res.append("(");
        for (int i = 0; i < parameterNames.size(); i++) {
            if (i > 0) {
                res.append(", ");
            }
            res.append(parameterTypes.get(i));
            res.append(" ");
            res.append(parameterNames.get(i));
        }
        res.append(")");
        if (!getReturnType().isVoid()) {
            res.append(" returns ");
            res.append(getReturnType());
        }
        return res.toString();
    }

    public boolean isStatic() {
        return def.attrIsStatic();
    }
}
