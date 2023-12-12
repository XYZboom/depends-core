/*
MIT License

Copyright (c) 2018-2019 Gang ZHANG

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package depends.entity;

import depends.relations.IBindingResolver;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FunctionEntity extends ContainerEntity {
	private List<GenericName> returnTypeIdentifiers = new ArrayList<>();
	ArrayList<VarEntity> parameters;
	Collection<GenericName> throwTypesIdentifiers = new ArrayList<>();
	private Collection<Entity> returnTypes = new ArrayList<>();
	private Collection<Entity> throwTypes = new ArrayList<>();
	private boolean isExtension = false;

	public boolean isExtension() {
		return isExtension;
	}

	public void setExtension(boolean extension) {
		isExtension = extension;
	}

	public boolean isExtensionOfType(TypeEntity entity) {
		if (!isExtension()) return false;
		if (getParameters() == null) return false;
		if (getParameters().isEmpty()) return false;
		VarEntity firstParam = getParameters().get(0);
		if (firstParam == null) return false;
		TypeEntity firstParamType = firstParam.getType();
		return firstParamType != null && firstParamType.isTypeParent(entity, false);
	}

	public FunctionEntity() {
		this.parameters = new ArrayList<>();
	}

	public FunctionEntity(GenericName simpleName, Entity parent, Integer id, GenericName returnType) {
		super(simpleName, parent, id);
		this.returnTypes = new ArrayList<>();
		returnTypeIdentifiers = new ArrayList<>();
		this.parameters = new ArrayList<>();
		throwTypesIdentifiers = new ArrayList<>();
		addReturnType(returnType);
	}

	public Collection<Entity> getReturnTypes() {
		return returnTypes;
	}

	@Override
	public TypeEntity getType() {
		if (returnTypes.size() > 0) {
			Object type = returnTypes.iterator().next();
			if (type instanceof TypeEntity)
				return (TypeEntity) type;
		}
		return null;
	}

	public void addReturnType(GenericName returnType) {
		if (returnType == null) return;
		this.returnTypeIdentifiers.add(returnType);
	}

	public void addReturnType(TypeEntity returnType) {
		if (returnType == null) return;
		if (!this.returnTypeIdentifiers.contains(returnType.rawName)) {
			this.returnTypeIdentifiers.add(returnType.rawName);
			this.returnTypes.add(returnType);
		}
	}

	public void addThrowTypes(List<GenericName> throwedType) {
		throwTypesIdentifiers.addAll(throwedType);
	}

	@Override
	public void inferLocalLevelEntities(IBindingResolver bindingResolver) {
		for (VarEntity param : parameters) {
			param.fillCandidateTypes(bindingResolver);
			param.inferLocalLevelEntities(bindingResolver);
		}
		if (returnTypes.size() < returnTypeIdentifiers.size()) {
			returnTypes = identifierToEntities(bindingResolver, this.returnTypeIdentifiers);
			for (GenericName returnTypeName : returnTypeIdentifiers) {
				Collection<Entity> typeEntities = typeParametersToEntities(bindingResolver, returnTypeName);
				this.appendTypeParameters(typeEntities);
			}
		}
		if (throwTypes.size() < throwTypesIdentifiers.size())
			throwTypes = identifierToEntities(bindingResolver, this.throwTypesIdentifiers);
		super.inferLocalLevelEntities(bindingResolver);
	}


	private Collection<Entity> typeParametersToEntities(IBindingResolver bindingResolver, GenericName name) {
		ArrayList<Entity> r = new ArrayList<>();
		for (GenericName typeParameter : name.getArguments()) {
			toEntityList(bindingResolver, r, typeParameter);
		}
		return r;
	}


	public ArrayList<VarEntity> getParameters() {
		return parameters;
	}

	public Collection<Entity> getThrowTypes() {
		return throwTypes;
	}

	@Override
	public Entity lookupVarInVisibleScope(GenericName varName) {
		for (VarEntity param : parameters) {
			if (varName.equals(param.getRawName())) {
				return param;
			}
		}
		return super.lookupVarInVisibleScope(varName);
	}

	public void addParameter(VarEntity var) {
		this.parameters.add(var);
	}

	@Override
	public String getDisplayName() {
		FileEntity f = (FileEntity) this.getAncestorOfType(FileEntity.class);
		return f.getRawName() + "(" + this.getQualifiedName() + ")";
	}

	@Override
	public VarEntity lookupVarLocally(GenericName varName) {
		for (VarEntity var : this.parameters) {
			if (var.getRawName().equals(varName))
				return var;
		}
		return super.lookupVarLocally(varName);
	}

	public void linkReturnToLastExpression() {
		if (expressionList() == null) return;
		for (int i = expressionList().size() - 1; i >= 0; i--) {
			Expression expr = expressionList().get(i);
			if (expr.isStatement())
				expr.addDeducedTypeFunction(this);
		}
	}

	public boolean isReturnTypeGenericTypeParameter() {
		if (returnTypeIdentifiers.isEmpty()) {
			return false;
		}
		return isGenericTypeParameter(returnTypeIdentifiers.get(0));
	}

	public @Nullable GenericName getReturnRawType() {
		return returnTypeIdentifiers == null ? null
				: returnTypeIdentifiers.isEmpty() ? null
				: returnTypeIdentifiers.get(0);
	}

	public @Nullable TypeEntity resolveFunctionCallType(Expression expression, IBindingResolver bindingResolver) {
		TypeEntity result = null;
		if (expression.isExplicitCallReferredEntity()) {
			result = expression.getType();
		}
		int parameterSize = getParameters().size();
		boolean anyParameterUnmatched = false;
		Map<GenericName, TypeEntity> genericTypeInfer = new HashMap<>();
		List<GenericName> funcGenericArgs = getRawName().getArguments();
		// The passed in generic type matches the generic parameters
		List<GenericName> callTypeArguments = expression.getCallTypeArguments();
		boolean genericArgsMatch = callTypeArguments.size() == funcGenericArgs.size();
		if (genericArgsMatch) {
			for (int i = 0; i < funcGenericArgs.size(); i++) {
				genericTypeInfer.put(funcGenericArgs.get(i),
						bindingResolver.inferTypeFromName(
								expression.getContainer(), callTypeArguments.get(i)
						));
			}
		}
		if (parameterSize == expression.getCallParameters().size()) {
			for (int i = 0; i < parameterSize; i++) {
				GenericName needTypeRawName = getParameters().get(i).getRawType();
				Expression parameterExpression = expression.getCallParameters().get(i);
				TypeEntity parameterExpressionType = parameterExpression.getType();
				if (!genericArgsMatch && isGenericTypeParameter(needTypeRawName)) {
					genericTypeInfer.put(needTypeRawName, parameterExpressionType);
				} else {
					if (parameterExpressionType == null ||
							!Objects.equals(parameterExpressionType.getRawName(), needTypeRawName)) {
						anyParameterUnmatched = true;
					}
				}
			}
		}
		if (isReturnTypeGenericTypeParameter()) {
			GenericName returnRawType = getReturnRawType();
			TypeEntity returnTypeInfer = genericTypeInfer.get(returnRawType);
			if (returnTypeInfer != null) {
				result = returnTypeInfer;
			}
		} else {
			result = getType();
		}
		if (!anyParameterUnmatched) {
			expression.setExplicitCallReferredEntity(true);
		}
		expression.setGenericTypeInfer(genericTypeInfer);
		return result;
	}
}
