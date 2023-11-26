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

import depends.entity.repo.EntityRepo;
import depends.relations.IBindingResolver;
import org.apache.commons.codec.binary.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Expression
 */
public class Expression implements Serializable {
	private static final long serialVersionUID = 3L;

	public Integer id;
	private String text;                // not only for debug purpose but also for kotlin expression call deduce
	private GenericName rawType;        // the raw type name
	private GenericName identifier;     // the varName, or method name, etc.
	private boolean isSet = false;       // is a set relation from right to leftHand
	private boolean isDot = false;       // is a dot expression, will decuce variable tfype left to right
	private boolean isCall = false;
	private boolean isLogic = false;
	private boolean isCreate = false;
	private boolean isCast = false;
	private boolean isThrow = false;
	private boolean isStatement = false; //statement is only used for return type calcuation in some langs such as ruby
	//they will not be treat as real expressions in case of relation calculation
	protected boolean deriveTypeFromChild = true;

	protected Integer deduceTypeBasedId; //by default, parent expression type determined by most left child

	private Integer parentId = -1;
	private transient Expression parent;

	private transient List<VarEntity> deducedTypeVars = new ArrayList<>();
	private List<Integer> deducedTypeVarsId = new ArrayList<>();

	private transient List<FunctionEntity> deducedTypeFunctions = new ArrayList<>();
	private List<Integer> deducedTypeFunctionsId = new ArrayList<>();

	private Integer referredEntityId;
	private transient Entity referredEntity;

	private transient TypeEntity type; // the type we care - for relation calculation. 
	private Location location = new Location();
	//for leaf, it equals to referredEntity.getType. otherwise, depends on child's type strategy

	private transient ContainerEntity container;
	private Integer containerId;

	/**
	 * The context type of this expression. If it is null, it defaults to the type entity of container
	 * where this expression is located.
	 * <br>
	 * 本表达式的上下文类型。如果是null，则默认为本表达式所在的容器所在的类型实体.
	 * <br>
	 * Used by processing scope functions in languages such as kotlin.
	 * <br>
	 * 用于处理kotlin等语言的作用域函数.
	 */
	private transient TypeEntity contextEntity;

	private Integer contextEntityId;

	public void resolve(IBindingResolver bindingResolver) {
		// 1. if expression's type existed, break;
		if (getType() != null)
			return;
		if (isDot()) { // wait for previous
			return;
		}
		if (getRawType() == null && getIdentifier() == null)
			return;

		// 2. if expression's rawType existed, directly infer type by rawType
		// if expression's rawType does not existed, infer type based on identifiers
		if (getRawType() != null) {
			setType(bindingResolver.inferTypeFromName(getContainer(), getRawType()), null, bindingResolver);
			if (getType() != null) {
				return;
			}
		}
		if (getIdentifier() != null) {
			Entity entity = bindingResolver.resolveName(getContainer(), getIdentifier(), true);
			String composedName = getIdentifier().toString();
			Expression theExpr = this;
			if (entity == null) {
				while (theExpr.getParent() != null && theExpr.getParent().isDot()) {
					theExpr = theExpr.getParent();
					if (theExpr.getIdentifier() == null) break;
					composedName = composedName + "." + theExpr.getIdentifier().toString();
					entity = bindingResolver.resolveName(getContainer(), GenericName.build(composedName), true);
					if (entity != null)
						break;
				}
			}
			if (entity != null) {
				TypeEntity entityType = entity.getType();
				if (bindingResolver.isDelayHandleCreateExpression() &&
						entityType != null && isCall()
						&& StringUtils.equals(entityType.rawName.getName(), composedName)) {
					// 检测到延迟处理则在此处进行Create类型设置
					setCreate(true);
					setCall(false);
				}
				setType(entityType, entity, bindingResolver);
				return;
			}
			TypeEntity context = getContext();
			if (context != null) {
				// search in context, does not search import in context
				Entity contextResult = bindingResolver.resolveName(context, getIdentifier(), false);
				if (contextResult != null && contextResult.getType() != null) {
					setType(context.getType(), contextResult, bindingResolver);
					return;
				}
			}
			if (isCall()) {
				List<Entity> contextFuncs = new ArrayList<>();
				if (context != null) {
					contextFuncs.addAll(context.lookupFunctionInVisibleScope(getIdentifier()));
				}
				List<Entity> funcs = getContainer().lookupFunctionInVisibleScope(getIdentifier());
				if (!contextFuncs.isEmpty()) {
					for (Entity func : contextFuncs) {
						setType(func.getType(), func, bindingResolver);
					}
				} else {
					for (Entity func : funcs) {
						setType(func.getType(), func, bindingResolver);
					}
				}
			} else {
				Entity varEntity = null;
				if (context != null) {
					varEntity = context.lookupVarInVisibleScope(getIdentifier());
				}
				if (varEntity != null) {
					setType(varEntity.getType(), varEntity, bindingResolver);
				} else {
					varEntity = getContainer().lookupVarInVisibleScope(getIdentifier());
					if (varEntity != null) {
						setType(varEntity.getType(), varEntity, bindingResolver);
					}
				}
			}
		}
	}

	public ContainerEntity getContainer() {
		return container;
	}

	public void setContainer(ContainerEntity container) {
		this.container = container;
		containerId = container.getId();
	}

	/**
	 * Unlike {@link #getContextEntity}, this function looks up the context stored in the expression's father
	 * to determine the context of the expression
	 * 与{@link #getContextEntity}不同，本函数查找存储在表达式父亲的上下文以判断表达式的上下文
	 */
	public @Nullable TypeEntity getContext() {
		Expression now = this;
		while (now != null) {
			if (now.getContextEntity() != null) {
				return now.getContextEntity();
			}
			now = now.parent;
		}
		Entity ancestorOfType = getContainer().getAncestorOfType(TypeEntity.class);
		if (ancestorOfType == null) {
			return null;
		}
		return ancestorOfType.getType();
	}

	public @Nullable TypeEntity getContextEntity() {
		return contextEntity;
	}

	public void setContextEntity(TypeEntity contextEntity) {
		this.contextEntity = contextEntity;
		this.contextEntityId = contextEntity.getId();
	}
	/*
	 * */

	public Expression() {
		deducedTypeVars = new ArrayList<>();
		deducedTypeFunctions = new ArrayList<>();
	}

	public Expression(Integer id) {
		this.id = id;
		deducedTypeVars = new ArrayList<>();
		deducedTypeFunctions = new ArrayList<>();
	}

	public void reload(EntityRepo repo, ArrayList<Expression> expressionList) {
		this.deducedTypeFunctions = new ArrayList<>();
		this.deducedTypeVars = new ArrayList<>();

		//recover parent relation
		if (parentId != -1) {
			for (Expression expr : expressionList) {
				if (Objects.equals(expr.id, parentId)) {
					parent = expr;
					break;
				}
			}
		}

		//recover deducedTypeFunctionsId
		if (deducedTypeFunctionsId != null) {
			for (Integer funcId : this.deducedTypeFunctionsId) {
				this.deducedTypeFunctions.add((FunctionEntity) repo.getEntity(funcId));
			}
		}

		//recover deducedTypeVars
		if (deducedTypeVarsId != null) {
			for (Integer varId : this.deducedTypeVarsId) {
				this.deducedTypeVars.add((VarEntity) repo.getEntity(varId));
			}
		}

		//referer referredEntity -- TODO:maybe not require
		if (this.referredEntityId != null && this.referredEntity == null) {
			this.referredEntity = repo.getEntity(this.referredEntityId);
			if (this.referredEntity == null) {
				System.err.println("unexpected: referred Entity is null" + this.referredEntityId + this.text + this.id);
			}
		}

		if (containerId != null) {
			Entity mayBeContainer = repo.getEntity(containerId);
			if (mayBeContainer instanceof ContainerEntity containerEntity) {
				container = containerEntity;
			}
		}
		if (contextEntityId != null) {
			Entity mayBeType = repo.getEntity(contextEntityId);
			if (mayBeType instanceof TypeEntity typeEntity) {
				contextEntity = typeEntity;
			}
		}
	}

	/**
	 * Set type of the expression
	 * if it is already has type, it will skip
	 * if it is already referered entity, it will skip
	 * if the type changed, parent expression will be re-caculated
	 * For dynamic type language, return type or parameters, variables may depends on the expression type,
	 * so once we get the type of expression, we will assign type to them.
	 *
	 * @param type            the type of the expression
	 * @param referredEntity  the entity of the expression point to, which is used to calculate dependency relation
	 * @param bindingResolver a parameter which will be passed to deduced parent type
	 */
	public void setType(TypeEntity type, Entity referredEntity, IBindingResolver bindingResolver) {
		if (this.getReferredEntity() == null && referredEntity != null) {
			this.setReferredEntity(referredEntity);
		}

		boolean changedType = false;
		if (this.type == null && type != null) {
			this.type = type;
			changedType = true;
			for (VarEntity var : deducedTypeVars) {
				if (var != null) {
					var.setType(this.type);
				}
			}
			for (FunctionEntity func : deducedTypeFunctions) {
				if (func != null) {
					func.addReturnType(this.type);
				}
			}
		}
		if (this.referredEntity == null)
			this.setReferredEntity(this.type);

		if (changedType)
			deduceTheParentType(bindingResolver);
	}


	/**
	 * deduce type of parent based on child's type.
	 * <p>For programming languages like kotlin or C #,
	 * there is a syntax feature that allows for adding
	 * syntactically equivalent member functions without
	 * modifying the class itself. Therefore, for type
	 * inference of the parent expression of an expression,
	 * it is necessary to search for these extension functions
	 * (or extension properties)</p>
	 *
	 * @param bindingResolver
	 */
	protected void deduceTheParentType(IBindingResolver bindingResolver) {
		if (this.type == null) return;
		if (this.parent == null) return;
		Expression parent = this.parent;
		if (parent.type != null) return;
		if (!parent.deriveTypeFromChild) return;
		//parent's type depends on first child's type
		if (!Objects.equals(parent.deduceTypeBasedId, this.id)) return;

		//if child is a built-in/external type, then parent must also a built-in/external type
		if (this.type.equals(TypeEntity.buildInType)) {
			parent.setType(TypeEntity.buildInType, TypeEntity.buildInType, bindingResolver);
			return;
		}

		/* if it is a logic expression, the return type/type is boolean. */
		if (parent.isLogic) {
			parent.setType(TypeEntity.buildInType, null, bindingResolver);
		}
		/* if it is a.b, and we already get a's type, b's type could be identified easily  */
		else if (parent.isDot) {
			if (parent.isCall()) {
				List<Entity> funcs = this.getType().lookupFunctionInVisibleScope(parent.identifier);
				if (getContainer() != null) {
					FunctionEntity functionEntity = getContainer().lookupExtensionFunctionInVisibleScope(
							getType(), parent.identifier, true);
					if (functionEntity != null) {
						funcs.add(functionEntity);
					}
				}
				parent.setReferredFunctions(bindingResolver, funcs);
			} else {
				Entity var = this.getType().lookupVarInVisibleScope(parent.identifier);
				if (var != null) {
					parent.setType(var.getType(), var, bindingResolver);
					parent.setReferredEntity(var);
				} else {
					List<Entity> funcs = this.getType().lookupFunctionInVisibleScope(parent.identifier);
					parent.setReferredFunctions(bindingResolver, funcs);
				}
			}
			if (parent.getType() == null) {
				parent.setType(bindingResolver.inferTypeFromName(this.getType(), parent.identifier), null, bindingResolver);
			}
		}
		/* if other situation, simple make the parent and child type same */
		else {
			parent.setType(type, null, bindingResolver);
		}
		if (parent.getReferredEntity() == null)
			parent.setReferredEntity(parent.type);
	}

	/**
	 * set expr's referred entity to functions
	 * why do not use 'setReferredEntity' directly?
	 * in case of multiple functions, we should first construct a multi-declare entities object,
	 * than set the type to multi-declare entity, for future resolver,
	 * for example in duck typing case:
	 * conn.send().foo, if conn is mutiple type (A, B), send should be search in both A and B
	 *
	 * @param bindingResolver
	 * @param funcs
	 */
	protected void setReferredFunctions(IBindingResolver bindingResolver, List<Entity> funcs) {
		if (funcs == null || funcs.size() == 0) return;
		Entity func = funcs.get(0);
		if (funcs.size() == 1) {
			setType(func.getType(), func, bindingResolver);
			setReferredEntity(func);
			return;
		}
		MultiDeclareEntities m = new MultiDeclareEntities(func, bindingResolver.getRepo().generateId());
		bindingResolver.getRepo().add(m);
		for (int i = 1; i < funcs.size(); i++) {
			m.add(funcs.get(i));
		}
		setType(func.getType(), m, bindingResolver);
		setReferredEntity(m);
	}

	protected void setReferredEntity(Entity referredEntity) {
		this.referredEntity = referredEntity;
		if (this.referredEntity != null) {
			this.referredEntityId = referredEntity.getId();
		}
	}

	/**
	 * remember the vars depends on the expression type
	 *
	 * @param var
	 */
	public void addDeducedTypeVar(VarEntity var) {
		this.deducedTypeVars.add(var);
		this.deducedTypeVarsId.add(var.getId());
	}

	/**
	 * remember the functions depends on the expression type
	 */
	public void addDeducedTypeFunction(FunctionEntity function) {
		this.deducedTypeFunctions.add(function);
		this.deducedTypeFunctionsId.add(function.id);
	}

	public void setParent(Expression parent) {
		this.parent = parent;
		if (parent != null)
			this.parentId = parent.id;
		if (parent != null) {
			if (parent.deduceTypeBasedId == null)
				parent.deduceTypeBasedId = id;
			if (parent.isSet) {
				parent.deduceTypeBasedId = id;
			}
		}
	}


	public GenericName getIdentifier() {
		return this.identifier;
	}

	public GenericName getRawType() {
		return this.rawType;
	}

	public void setIdentifier(String name) {
		if (!validName(name)) {
			return;
		}
		this.identifier = GenericName.build(name);
	}

	/**
	 * if want set identifier to null, use {@link Expression#setIdentifierToNull()}
	 */
	public void setIdentifier(GenericName name) {
		if (name == null) return;
		if (!validName(name.getName())) {
			return;
		}
		this.identifier = name;
	}

	public void setIdentifierToNull() {
		this.identifier = null;
	}

	public void setRawType(GenericName name) {
		if (name == null) return;
		if (!validName(name.getName())) {
			return;
		}
		this.rawType = name;

	}

	public void setRawType(String name) {
		if (name == null) return;
		if (!validName(name)) {
			return;
		}
		this.rawType = GenericName.build(name);
	}

	public Expression getParent() {
		return this.parent;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}

	public boolean isCall() {
		return isCall;
	}

	public boolean isSet() {
		return isSet;
	}

	public void setSet(boolean isSet) {
		this.isSet = isSet;
	}

	public boolean isDot() {
		return isDot;
	}

	public void setDot(boolean isDot) {
		this.isDot = isDot;
	}

	public boolean isLogic() {
		return isLogic;
	}

	public void setLogic(boolean isLogic) {
		this.isLogic = isLogic;
	}

	public boolean isCreate() {
		return isCreate;
	}

	public void setCreate(boolean isCreate) {
		this.isCreate = isCreate;
	}

	public boolean isCast() {
		return isCast;
	}

	public void setCast(boolean isCast) {
		this.isCast = isCast;
	}

	public boolean isThrow() {
		return isThrow;
	}

	public void setThrow(boolean isThrow) {
		this.isThrow = isThrow;
	}

	public boolean isStatement() {
		return isStatement;
	}

	public void setStatement(boolean isStatement) {
		this.isStatement = isStatement;
	}

	public void setCall(boolean isCall) {
		this.isCall = isCall;
	}

	public void disableDriveTypeFromChild() {
		deriveTypeFromChild = false;
	}

	public Entity getReferredEntity() {
		return referredEntity;
	}

	public TypeEntity getType() {
		return type;
	}


	private boolean validName(String name) {
		if (name == null) return false;
		if (name.toLowerCase().equals("<literal>")) return true;
		if (name.toLowerCase().equals("<built-in>")) return true;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append("[").append(text).append("]").append("|")
				.append("rawType:").append(rawType).append("|")
				.append("identifier:").append(identifier).append("|")
				.append("prop:").append(isDot ? "[dot]" : "")
				.append(isSet ? "[set]" : "")
				.append(isLogic ? "[bool]" : "")
				.append(isCall ? "[call]" : "")
				.append(isCreate ? "[new]" : "")
				.append(isThrow ? "[throw]" : "").append("|")
				.append("parent:").append(parent == null ? "nil" : parent.text).append("|")
				.append("type:").append(type).append("|");
		return s.toString();
	}

	public void setLine(int lineNumber) {
		this.location.setLine(lineNumber);
	}

	public void setStart(int start) {
		this.location.setStartIndex(start);
	}

	public void setStop(int stop) {
		this.location.setStopIndex(stop);
	}

	public Location getLocation() {
		return location;
	}
}