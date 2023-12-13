package depends.entity.intf;

import depends.entity.FunctionEntity;
import depends.entity.GenericName;
import depends.entity.TypeEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IExtensionContainer {
    @NotNull Integer getId();
    @Nullable FunctionEntity lookupExtensionFunctionInVisibleScope(
            @NotNull TypeEntity type, @NotNull GenericName genericName,
            boolean searchPackage
    );
}
