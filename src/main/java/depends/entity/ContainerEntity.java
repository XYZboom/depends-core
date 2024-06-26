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

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import depends.entity.intf.IExtensionContainer;
import depends.entity.repo.EntityRepo;
import depends.relations.IBindingResolver;
import depends.relations.Relation;
import depends.utils.GraphUtils;
import multilang.depends.util.file.TemporaryFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Consumer;

/**
 * ContainerEntity for example file, class, method, etc. they could contain
 * vars, functions, ecpressions, type parameters, etc.
 */
public abstract class ContainerEntity extends DecoratedEntity implements IExtensionContainer {
	private static final Logger logger = LoggerFactory.getLogger(ContainerEntity.class);

	private ArrayList<VarEntity> vars;
	private ArrayList<FunctionEntity> functions;
	WeakReference<HashMap<Object, Expression>> expressionWeakReference;
	private ArrayList<Expression> expressionList;
	private int expressionCount = 0;
	private Collection<GenericName> mixins;
	private Collection<ContainerEntity> resolvedMixins;

	private ArrayList<VarEntity> vars() {
		if (vars == null)
			vars = new ArrayList<>();
		return this.vars;
	}

	private Collection<GenericName> mixins() {
		if (mixins == null)
			mixins = new ArrayList<>();
		return this.mixins;
	}

	private ArrayList<FunctionEntity> functions() {
		if (functions == null)
			functions = new ArrayList<>();
		return this.functions;
	}

	public ContainerEntity() {
	}

	public ContainerEntity(GenericName rawName, Entity parent, Integer id) {
		super(rawName, parent, id);
	}

	public void addVar(VarEntity var) {
		if (logger.isDebugEnabled()) {
			logger.debug("var found: " + var.getRawName() + ":" + var.getRawType());
		}
		this.vars().add(var);
	}

	public ArrayList<VarEntity> getVars() {
		if (vars == null)
			return new ArrayList<>();
		return this.vars();
	}

	public void addFunction(FunctionEntity functionEntity) {
		this.functions().add(functionEntity);
	}

	public ArrayList<FunctionEntity> getFunctions() {
		if (functions == null)
			return new ArrayList<>();
		return this.functions;
	}

	public HashMap<Object, Expression> expressions() {
		if (expressionWeakReference == null)
			expressionWeakReference = new WeakReference<>(new HashMap<>());
		HashMap<Object, Expression> r = expressionWeakReference.get();
		if (r == null) return new HashMap<>();
		return r;
	}

	public void addExpression(Object key, Expression expression) {
		expressions().put(key, expression);
		expressionList().add(expression);
		expression.setContainer(this);
		expressionCount = expressionList.size();
	}

	public boolean containsExpression(Object key) {
		return expressions().containsKey(key);
	}

	/**
	 * For all data in the class, infer their types. Should be override in
	 * sub-classes
	 */
	public void inferLocalLevelEntities(IBindingResolver bindingResolver) {
		super.inferLocalLevelEntities(bindingResolver);
		for (VarEntity var : this.vars()) {
			if (var.getParent() != this) {
				var.inferLocalLevelEntities(bindingResolver);
			}
		}
		for (FunctionEntity func : this.getFunctions()) {
			if (func.getParent() != this) {
				func.inferLocalLevelEntities(bindingResolver);
			}
		}
		if (bindingResolver.isEagerExpressionResolve()) {
			reloadExpression(bindingResolver.getRepo());
			resolveExpressions(bindingResolver);
			cacheExpressions();
		}
		resolvedMixins = identiferToContainerEntity(bindingResolver, getMixins());
	}

	private Collection<GenericName> getMixins() {
		if (mixins == null)
			return new ArrayList<>();
		return mixins;
	}

	private Collection<ContainerEntity> identiferToContainerEntity(IBindingResolver bindingResolver, Collection<GenericName> identifiers) {
		if (identifiers.isEmpty()) return null;
		ArrayList<ContainerEntity> r = new ArrayList<>();
		for (GenericName identifier : identifiers) {
			Entity entity = bindingResolver.resolveName(this, identifier, true);
			if (entity == null) {
				continue;
			}
			if (entity instanceof ContainerEntity)
				r.add((ContainerEntity) entity);
		}
		return r;
	}

	/**
	 * Resolve all expression's type
	 */
	public void resolveExpressions(IBindingResolver bindingResolver) {
		if (this instanceof FunctionEntity) {
			((FunctionEntity) this).linkReturnToLastExpression();
		}

		if (expressionList == null) return;
		if (expressionList.size() > 10000) return;

		MutableGraph<Expression> expressionGraph =
				GraphBuilder.directed()
						.expectedNodeCount(expressionCount)
						.build();

		for (Expression expression : expressionList) {
			expressionGraph.addNode(expression);
			for (Expression resolveFirst : expression.getResolveFirstList()) {
				if (expression != resolveFirst)
					expressionGraph.putEdge(resolveFirst, expression);
			}
		}
		GraphUtils.topologyTraverse(expressionGraph,
				expression -> expression.resolve(bindingResolver),
				expression -> {
					logger.warn("expression: '{}' has cycle dependency when resolving!", expression);
					expression.resolve(bindingResolver);
				},
				// When there is no dependency relationship in the expression,
				// the default should be to resolve the expression that was added first
				// 在表达式没有依赖关系时，默认应当是先加入的表达式先解析
				Comparator.comparing(expression -> expressionList.indexOf(expression)
				));
	}

	public void cacheChildExpressions() {
		cacheExpressions();
		for (Entity child : getChildren()) {
			if (child instanceof ContainerEntity) {
				((ContainerEntity) child).cacheChildExpressions();
			}
		}
	}


	public void cacheExpressions() {
		if (expressionWeakReference == null) return;
		if (expressionList == null) return;
		this.expressions().clear();
		this.expressionWeakReference.clear();
		cacheExpressionListToFile();
		this.expressionList.clear();
		this.expressionList = null;
		this.expressionList = new ArrayList<>();
	}

	public void clearExpressions() {
		if (expressionWeakReference == null) return;
		if (expressionList == null) return;
		this.expressions().clear();
		this.expressionWeakReference.clear();
		this.expressionList.clear();
		this.expressionList = null;
		this.expressionList = new ArrayList<>();
		this.expressionUseList = null;
	}

	private void cacheExpressionListToFile() {
		if (expressionCount == 0) return;
		try {
			FileOutputStream fileOut = new FileOutputStream(TemporaryFile.getInstance().exprPath(this.id));
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(this.expressionList);
			out.close();
			fileOut.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public void reloadExpression(EntityRepo repo) {
		if (expressionCount == 0) return;
		try {
			FileInputStream fileIn = new FileInputStream(TemporaryFile.getInstance().exprPath(this.id));
			ObjectInputStream in = new ObjectInputStream(fileIn);
			expressionList = (ArrayList<Expression>) in.readObject();
			if (expressionList == null) expressionList = new ArrayList<>();
			for (Expression expr : expressionList) {
				expr.reload(repo, expressionList);
			}
			in.close();
			fileIn.close();
		} catch (IOException | ClassNotFoundException i) {
			i.printStackTrace();
		}
	}


	public List<Expression> expressionList() {
		if (expressionList == null)
			expressionList = new ArrayList<>();
		return expressionList;
	}

	public boolean containsExpression() {
		return !expressions().isEmpty();
	}

	/**
	 * The entry point of lookup functions. It will treat multi-declare entities and
	 * normal entity differently. - for multiDeclare entity, it means to lookup all
	 * entities - for normal entity, it means to lookup entities from current scope
	 * still root
	 *
	 * @param functionName
	 * @return
	 */
	public @NotNull List<Entity> lookupFunctionInVisibleScope(GenericName functionName) {
		List<Entity> functions = new ArrayList<>();
		if (this.getMutliDeclare() != null) {
			for (Entity fromEntity : this.getMutliDeclare().getEntities()) {
				Entity f = lookupFunctionBottomUpTillTopContainer(functionName, fromEntity);
				if (f != null) {
					functions.add(f);
					return functions;
				}
			}
		} else {
			ContainerEntity fromEntity = this;
			Entity f = lookupFunctionBottomUpTillTopContainer(functionName, fromEntity);
			if (f != null) {
				functions.add(f);
				return functions;
			}
		}
		return functions;
	}

	public static void processVisibleEntitiesThatNameOf(
			@NotNull Entity fromEntity,
			@NotNull GenericName functionName,
			boolean searchPackage,
			@NotNull Consumer<Entity> consumer
	) {
		processVisibleEntitiesThatNameOf(fromEntity, functionName, searchPackage, consumer, new HashSet<>());
	}

	public static void processVisibleEntitiesThatNameOf(
			@NotNull Entity fromEntity,
			@NotNull GenericName functionName,
			boolean searchPackage,
			@NotNull Consumer<Entity> consumer,
			Set<Entity> searched) {
		if (searched.contains(fromEntity)) {
			return;
		}
		while (fromEntity != null) {
			if (searchPackage && fromEntity instanceof PackageEntity packageEntity) {
				for (Entity entity : packageEntity.getChildren()) {
					processVisibleEntitiesThatNameOf(entity, functionName, searchPackage, consumer, searched);
					searched.add(entity);
				}
			}
			if (fromEntity instanceof ContainerEntity container) {
				for (FunctionEntity function : container.getFunctions()) {
					if (Objects.equals(function.rawName, functionName)) {
						consumer.accept(function);
					}
				}
				for (VarEntity varEntity : container.getVars()) {
					if (Objects.equals(varEntity.rawName, functionName)) {
						consumer.accept(varEntity);
					}
				}
			}
			for (Entity child : fromEntity.getChildren()) {
				if (child instanceof AliasEntity) {
					if (child.getRawName().equals(functionName))
						consumer.accept(child.getActualReferTo());
				}
			}
			searched.add(fromEntity);
			fromEntity = fromEntity.getParent();
		}
	}

	@Nullable
	@Override
	public FunctionEntity lookupExtensionFunctionInVisibleScope(
			@NotNull TypeEntity type, @NotNull GenericName genericName,
			boolean searchPackage
	) {
		ArrayList<FunctionEntity> currentTypeFunc = new ArrayList<>();
		ArrayList<FunctionEntity> nonTypeFunc = new ArrayList<>();
		Entity ancestorOfType = getAncestorOfType(FileEntity.class);
		Consumer<Entity> consumer = (entity) -> {
			if (!(entity instanceof FunctionEntity function)) return;
			if (!function.isExtension()) return;
			ArrayList<VarEntity> parameters = function.getParameters();
			if (parameters == null || parameters.isEmpty()) return;
			VarEntity firstParameter = parameters.get(0);
			if (firstParameter == null) return;
			TypeEntity parameterType = firstParameter.getType();
			if (parameterType == null) return;
			if (parameterType.equals(type)) {
				if (!currentTypeFunc.contains(function)) {
					currentTypeFunc.add(function);
				}
			} else if (!nonTypeFunc.contains(function) && parameterType.isTypeParent(type, true)) {
				nonTypeFunc.add(function);
			}
		};
		if (ancestorOfType instanceof FileEntity fileEntity) {
			Collection<Entity> importedTypes = fileEntity.getImportedTypes();
			for (Entity entity : importedTypes) {
				consumer.accept(entity);
			}
		}
		processVisibleEntitiesThatNameOf(this, genericName, true, consumer);
		if (!currentTypeFunc.isEmpty()) {
			return getNearest(currentTypeFunc);
		}
		if (!nonTypeFunc.isEmpty()) {
			return getNearest(nonTypeFunc);
		}
		return null;
	}

	/**
	 * lookup function bottom up till the most outside container
	 *
	 * @param functionName
	 * @param fromEntity
	 * @return
	 */
	private Entity lookupFunctionBottomUpTillTopContainer(GenericName functionName, Entity fromEntity) {
		while (fromEntity != null) {
			if (fromEntity instanceof ContainerEntity) {
				FunctionEntity func = ((ContainerEntity) fromEntity).lookupFunctionLocally(functionName);
				if (func != null)
					return func;
			}
			for (Entity child : this.getChildren()) {
				if (child instanceof AliasEntity) {
					if (child.getRawName().equals(functionName))
						return child;
				}
			}
			fromEntity = this.getAncestorOfType(ContainerEntity.class);
		}
		return null;
	}

	/**
	 * lookup function in local entity. It could be override such as the type entity
	 * (it should also lookup the inherit/implemented types
	 *
	 * @param functionName
	 * @return
	 */
	public FunctionEntity lookupFunctionLocally(GenericName functionName) {
		for (FunctionEntity func : getFunctions()) {
			if (func.getRawName().equals(functionName))
				return func;
		}
		return null;
	}

	/**
	 * The entry point of lookup var. It will treat multi-declare entities and
	 * normal entity differently. - for multiDeclare entity, it means to lookup all
	 * entities - for normal entity, it means to lookup entities from current scope
	 * still root
	 *
	 * @param varName
	 * @return
	 */
	public Entity lookupVarInVisibleScope(GenericName varName) {
		ContainerEntity fromEntity = this;
		return lookupVarBottomUpTillTopContainer(varName, fromEntity);
	}

	/**
	 * To found the var.
	 *
	 * @param fromEntity
	 * @param varName
	 * @return
	 */
	private Entity lookupVarBottomUpTillTopContainer(GenericName varName, ContainerEntity fromEntity) {
		while (fromEntity != null) {
			if (fromEntity instanceof ContainerEntity) {
				VarEntity var = fromEntity.lookupVarLocally(varName);
				if (var != null)
					return var;
			}
			for (Entity child : this.getChildren()) {
				if (child instanceof AliasEntity) {
					if (child.getRawName().equals(varName))
						return child;
				}
			}
			fromEntity = (ContainerEntity) this.getAncestorOfType(ContainerEntity.class);
		}
		return null;
	}

	public VarEntity lookupVarLocally(GenericName varName) {
		for (VarEntity var : getVars()) {
			if (var.getRawName().equals(varName))
				return var;
		}
		return null;
	}

	public VarEntity lookupVarLocally(String varName) {
		return this.lookupVarLocally(GenericName.build(varName));
	}

	public void addMixin(GenericName moduleName) {
		mixins().add(moduleName);
	}

	public Collection<ContainerEntity> getResolvedMixins() {
		if (resolvedMixins == null) return new ArrayList<>();
		return resolvedMixins;
	}

	HashMap<String, Set<Expression>> expressionUseList = null;

	public void addRelation(Expression expression, Relation relation) {
		String key = relation.getEntity().qualifiedName + relation.getType();
		if (this.expressionUseList == null)
			expressionUseList = new HashMap<>();
		if (expressionUseList.containsKey(key)) {
			Set<Expression> expressions = expressionUseList.get(key);
			for (Expression expr : expressions) {
				if (linkedExpr(expr, expression)) return;
			}
		} else {
			expressionUseList.put(key, new HashSet<>());
		}

		expressionUseList.get(key).add(expression);
		super.addRelation(relation);
	}

	private boolean linkedExpr(Expression a, Expression b) {
		Expression parent = a.getParent();
		while (parent != null) {
			if (parent == b) return true;
			parent = parent.getParent();
		}
		parent = b.getParent();
		while (parent != null) {
			if (parent == a) return true;
			parent = parent.getParent();
		}
		return false;
	}
}
