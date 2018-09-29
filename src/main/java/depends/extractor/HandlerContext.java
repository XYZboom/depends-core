package depends.extractor;

import java.util.Stack;

import depends.entity.ContainerEntity;
import depends.entity.Entity;
import depends.entity.repo.EntityRepo;
import depends.entity.types.FileEntity;
import depends.entity.types.FunctionEntity;
import depends.entity.types.PackageEntity;
import depends.entity.types.TypeEntity;
import depends.entity.types.VarEntity;

public class HandlerContext{
	private EntityRepo entityRepo;
	private FileEntity currentFileEntity;
	private String currentPackageName = "";
	Stack<Entity> entityStack = new Stack<Entity>();
	
	public HandlerContext(EntityRepo entityRepo) {
		this.entityRepo = entityRepo;
		entityStack = new Stack<Entity>();
	}
	public FileEntity newFileEntity(String fileName) {
		currentFileEntity = new FileEntity(fileName,entityRepo.generateId());
        entityStack.push(currentFileEntity);
		return currentFileEntity;
	}

	public Entity newPackageEntity(String packageName) {
		this.currentPackageName = packageName;
		Entity pkgEntity = entityRepo.getEntity(packageName);
		if (pkgEntity ==null) {
			pkgEntity = new PackageEntity(packageName,-1,
				entityRepo.generateId());
		}
		currentFileEntity.setParentId(pkgEntity.getId());
		pkgEntity.addChildId(currentFileEntity.getId());
		return pkgEntity;
	}
	
	private String resolveTypeNameDefinition(String name) {
		if (name.isEmpty()) return "";
		String prefix = "";
		for (int i=entityStack.size()-1;i>=0;i--) {
			Entity t = entityStack.get(i);
			if (t instanceof FileEntity) continue; //file name should be bypass. use package name instead 
			if(! t.getFullName().isEmpty() &&
					!(t.getFullName().startsWith("<Anony>"))) {
				prefix = t.getFullName();
				break;
			}
		}
		if (prefix.isEmpty()) {
			if (currentPackageName.length()>0)
				return currentPackageName + "." + name;
			return name;
		}else {
			return  prefix + "." + name;
		}
	}
	
	public Entity newClassInterface(String classOrInterfaceName) {
		Entity currentTypeEntity = new TypeEntity(resolveTypeNameDefinition(classOrInterfaceName),
				currentFileEntity.getId(),
				entityRepo.generateId());
        entityRepo.add(currentTypeEntity);
        entityStack.push(currentTypeEntity);
		return currentTypeEntity;
	}
	
	public void popEntity() {
		entityStack.pop();
	}
	
	public void newImport(String importedTypeOrPackage) {
		currentFileEntity.addImport(importedTypeOrPackage);
	}
	
	public String resolveTypeNameRef(String typeName) {
		//if it is a full name like "java.io.Exception"
		if (typeName.indexOf('.')>0) return typeName;
		
		//if it is a singleName like "JavaHandler"
		// TODO: we still cannot handle on demand import like 
		// import package.name.*;
		if (currentFile().getImport(typeName)!=null)
			typeName = currentFile().getImport(typeName);
		else
			typeName = currentPackageName + (currentPackageName.isEmpty()? "":".") + typeName;
		return typeName;
	}
	public Entity newFunctionEntity(String methodName, String resultType) {
		//TODO: should process parameter types to distinguish the overload functions for short name;
		FunctionEntity currentFunctionEntity = new FunctionEntity(resolveTypeNameDefinition(methodName),
				currentType().getId(),
				entityRepo.generateId(),resultType,methodName);
		currentType().addFunction(currentFunctionEntity);
        entityStack.push(currentFunctionEntity);
		return currentFunctionEntity;
	}
	public TypeEntity currentType() {
		for (int i=entityStack.size()-1;i>=0;i--) {
			Entity t = entityStack.get(i);
			if (t instanceof TypeEntity)
				return (TypeEntity)t;
		}
		return null;
	}
	public Entity currentFunction() {
		for (int i=entityStack.size()-1;i>=0;i--) {
			Entity t = entityStack.get(i);
			if (t instanceof FunctionEntity)
				return t;
		}
		return null;
	}
	
	public FileEntity currentFile() {
		return currentFileEntity;
	}
	
	public Entity latestValidContainer() {
		for (int i=entityStack.size()-1;i>=0;i--) {
			Entity t = entityStack.get(i);
			if (t instanceof FunctionEntity)
				return t;
			if (t instanceof TypeEntity)
				return t;
			if (t instanceof FileEntity)
				return t;
		}
		return null;
	}
	
	public void addVar(String type, String varName) {
		VarEntity varEntity = new VarEntity(varName, type, lastContainer().getId(), entityRepo.generateId());
		lastContainer().addVar(varEntity);
	}
	public ContainerEntity lastContainer() {
		for (int i=entityStack.size()-1;i>=0;i--) {
			Entity t = entityStack.get(i);
			if (t instanceof ContainerEntity)
				return (ContainerEntity)t;
		}
		return null;
	}
	
	public String inferVarType(String varName) {
		for (int i=entityStack.size()-1;i>=0;i--) {
			Entity t = entityStack.get(i);
			if (t instanceof ContainerEntity) {
				for (VarEntity var:((ContainerEntity)t).getVars()) {
					if (var.getFullName().equals(varName)){
						return var.getType();
					}
				}
			}
		}
		return null;
	}
	
	public String inferVarType(String fromType, String varName) {
		Entity type = entityRepo.getEntity(fromType);
		if (type==null) return null;
		if (!(type instanceof TypeEntity)) return null;
		if (type instanceof ContainerEntity) {
			for (VarEntity var:((ContainerEntity)type).getVars()) {
				if (var.getFullName().equals(varName))
					return var.getType();
			}
		}
		return null;
	}

	public String inferFunctionType(String fromType, String varName) {
		Entity type = entityRepo.getEntity(fromType);
		if (type==null) return null;
		if (!(type instanceof TypeEntity)) return null;
		TypeEntity typeEntity = (TypeEntity)type;
		
		for (FunctionEntity var:typeEntity.getFunctions()) {
			if (var.getShortName().equals(varName))
				return var.getReturnType();
		}
		return null;
	}
}